package main

import (
	"bytes"
	"context"
	"encoding/json"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"sync"
	"testing"
	"time"

	"github.com/libp2p/zeroconf/v2"
)

func TestPeersExchangeMissingEntriesWithoutOverwritingOrResurrecting(t *testing.T) {
	a, b := testStore(t), testStore(t)
	ac, err := syncChanges(a, change("food-a", 0, food(t)))
	if err != nil {
		t.Fatal(err)
	}
	recipe, _ := assets.ReadFile("examples/recipe.json")
	bc, err := syncChanges(b, Mutation{Kind: "recipe", ID: "recipe-b", Data: recipe})
	if err != nil {
		t.Fatal(err)
	}
	if result, err := a.importMissing(bc); err != nil || result.Added != 1 {
		t.Fatal(result, err)
	}
	if result, err := b.importMissing(ac); err != nil || result.Added != 1 {
		t.Fatal(result, err)
	}
	before, _ := a.snapshot()
	bc, _ = b.snapshot()
	if result, err := a.importMissing(bc); err != nil || result.Added != 0 {
		t.Fatal(result, err)
	}
	after, _ := a.snapshot()
	if after.Revision != before.Revision || len(after.Records) != 2 {
		t.Fatal("retry changed catalogue")
	}
	// Editing one copy does not silently replace the other server's copy.
	edit := bytes.ReplaceAll(food(t), []byte("rolled oats"), []byte("local oats"))
	if _, err := syncChanges(a, change("food-a", 1, edit)); err != nil {
		t.Fatal(err)
	}
	if result, err := a.importMissing(bc); err != nil || result.Different != 1 {
		t.Fatal(result, err)
	}
	after, _ = a.snapshot()
	if !bytes.Contains(after.Records[0].Data, []byte("local oats")) {
		t.Fatal("local edit overwritten")
	}
	if _, err := syncChanges(a, Mutation{Kind: "food", ID: "food-a", BaseRevision: after.Records[0].Revision, Deleted: true}); err != nil {
		t.Fatal(err)
	}
	if _, err := a.importMissing(bc); err != nil {
		t.Fatal(err)
	}
	after, _ = a.snapshot()
	if !after.Records[0].Deleted {
		t.Fatal("peer resurrected local deletion")
	}
	// A new server receives deletion records too, preventing a stale peer from restoring them later.
	c := testStore(t)
	if result, err := c.importMissing(after); err != nil || result.Tombstones != 1 {
		t.Fatal(result, err)
	}
	if _, err := c.importMissing(bc); err != nil {
		t.Fatal(err)
	}
	cc, _ := c.snapshot()
	if !cc.Records[0].Deleted {
		t.Fatal("tombstone was not preserved")
	}
}

func TestInvalidPeerSnapshotIsAtomic(t *testing.T) {
	s := testStore(t)
	for _, records := range [][]Record{
		{{Kind: "food", ID: "good", Revision: 1, Data: food(t)}, {Kind: "food", ID: "bad", Revision: 2, Data: json.RawMessage(`{}`)}},
		{{Kind: "food", ID: "duplicate", Revision: 1, Data: food(t)}, {Kind: "food", ID: "duplicate", Revision: 2, Data: food(t)}},
	} {
		if _, err := s.importMissing(Catalog{SchemaVersion: 1, ServerID: "peer", Revision: 2, Records: records}); err == nil {
			t.Fatal("accepted invalid snapshot")
		}
		c, _ := s.snapshot()
		if c.Revision != 0 || len(c.Records) != 0 {
			t.Fatal("partial peer import")
		}
	}
}

func TestPeerHTTPKeysCachingAndIdentity(t *testing.T) {
	a, b := testStore(t), testStore(t)
	if _, err := syncChanges(a, change("food-a", 0, food(t))); err != nil {
		t.Fatal(err)
	}
	server := httptest.NewServer(api(a, "browser-key", peerConfig{Enabled: true, Key: "shared-key"}))
	defer server.Close()
	peer := catalogPeer{ID: a.serverID, Address: server.URL}
	client := peerHTTPClient()
	defer client.CloseIdleConnections()
	if _, _, err := pullPeer(context.Background(), client, b, peer, "wrong-key", ""); err == nil {
		t.Fatal("wrong peer key accepted")
	}
	result, etag, err := pullPeer(context.Background(), client, b, peer, "shared-key", "")
	if err != nil || result.Added != 1 || etag == "" {
		t.Fatal(result, etag, err)
	}
	if result, next, err := pullPeer(context.Background(), client, b, peer, "shared-key", etag); err != nil || result.Added != 0 || next != etag {
		t.Fatal(result, next, err)
	}
	peer.ID = "wrong-id"
	if _, _, err := pullPeer(context.Background(), client, b, peer, "shared-key", ""); err == nil {
		t.Fatal("changed identity accepted")
	}
	redirect := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { http.Redirect(w, r, server.URL, 302) }))
	defer redirect.Close()
	if _, _, err := pullPeer(context.Background(), client, b, catalogPeer{ID: a.serverID, Address: redirect.URL}, "shared-key", ""); err == nil {
		t.Fatal("redirect followed")
	}
}

func TestPeerEndpointRemainsLANOnlyAndBrowserWritesStillRequireKey(t *testing.T) {
	s := testStore(t)
	for _, test := range []struct {
		enabled              bool
		remote, path, method string
		want                 int
	}{
		{true, "192.168.0.2:1234", "/api/v1/peer-catalog", "GET", 200},
		{false, "192.168.0.2:1234", "/api/v1/peer-catalog", "GET", 404},
		{true, "8.8.8.8:1234", "/api/v1/peer-catalog", "GET", 403},
		{true, "192.168.0.2:1234", "/api/v1/foods", "POST", 401},
		{true, "192.168.0.2:1234", "/api/v1/catalog", "GET", 401},
	} {
		r := httptest.NewRequest(test.method, "http://localhost"+test.path, nil)
		r.RemoteAddr = test.remote
		w := httptest.NewRecorder()
		api(s, "browser-key", peerConfig{Enabled: test.enabled}).ServeHTTP(w, r)
		if w.Code != test.want {
			t.Fatalf("%+v: HTTP %d", test, w.Code)
		}
	}
}

func TestDiscoveryIgnoresUnrelatedPeersAndPublicAddresses(t *testing.T) {
	entry := &zeroconf.ServiceEntry{Port: 8765, Text: []string{"version=1", "peers=1", "id=peer"},
		AddrIPv4: []net.IP{net.ParseIP("192.168.0.181"), net.ParseIP("8.8.8.8"), net.ParseIP("127.0.0.1")}}
	if peers := peerFromService(entry, "self", false); len(peers) != 1 || peers[0].Address != "http://192.168.0.181:8765" {
		t.Fatal(peers)
	}
	if len(peerFromService(entry, "peer", false)) != 0 || len(peerFromService(entry, "self", true)) != 0 {
		t.Fatal("own server or incompatible key mode accepted")
	}
	entry.Text = []string{"version=2", "peers=1", "id=peer"}
	if len(peerFromService(entry, "self", false)) != 0 {
		t.Fatal("incompatible protocol accepted")
	}
}

// Explicit opt-in: exercises real multicast discovery and HTTP on this machine's LAN.
// A random peer key keeps test data isolated from real catalogues on that network.
func TestLiveLANDiscoveryAndPeerExchange(t *testing.T) {
	if os.Getenv("CARBOMON_MDNS_TEST") != "1" {
		t.Skip("set CARBOMON_MDNS_TEST=1 on a multicast-capable LAN")
	}
	a, b := testStore(t), testStore(t)
	config := peerConfig{Enabled: true, Key: newID()}
	for _, s := range []*Store{a, b} {
		listener, err := net.Listen("tcp4", "0.0.0.0:0")
		if err != nil {
			t.Fatal(err)
		}
		server := httptest.NewUnstartedServer(api(s, "test-browser-key", config))
		server.Listener.Close()
		server.Listener = listener
		server.Start()
		defer server.Close()
		announcement, err := advertiseCatalog(listener.Addr().(*net.TCPAddr), s.serverID, config)
		if err != nil {
			t.Fatal(err)
		}
		defer announcement.Shutdown()
	}
	if _, err := syncChanges(a, change("from-a", 0, food(t))); err != nil {
		t.Fatal(err)
	}
	if _, err := syncChanges(b, change("from-b", 0, food(t))); err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	var workers sync.WaitGroup
	for _, s := range []*Store{a, b} {
		workers.Add(1)
		go func(s *Store) { defer workers.Done(); syncPeers(ctx, s, config) }(s)
	}
	defer func() { cancel(); workers.Wait() }()
	deadline := time.Now().Add(18 * time.Second)
	for time.Now().Before(deadline) {
		ac, _ := a.snapshot()
		bc, _ := b.snapshot()
		if len(ac.Records) == 2 && len(bc.Records) == 2 {
			return
		}
		time.Sleep(100 * time.Millisecond)
	}
	t.Fatal("servers did not automatically discover each other and exchange missing entries")
}
