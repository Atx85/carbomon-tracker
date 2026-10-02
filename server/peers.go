package main

import (
	"context"
	"crypto/subtle"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/libp2p/zeroconf/v2"
)

type peerConfig struct {
	Enabled bool
	Key     string
}
type catalogPeer struct {
	ID      string
	Address string
}

func peerCatalogHandler(s *Store, config peerConfig) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if !config.Enabled {
			fail(w, 404, fmt.Errorf("peer sharing is disabled"))
			return
		}
		if config.Key != "" && subtle.ConstantTimeCompare([]byte(r.Header.Get("X-Carbomon-Peer-Key")), []byte(config.Key)) != 1 {
			fail(w, 401, fmt.Errorf("peer key required"))
			return
		}
		catalog, err := s.snapshot()
		if err != nil {
			fail(w, 500, err)
			return
		}
		etag := fmt.Sprintf(`"%s-%d"`, catalog.ServerID, catalog.Revision)
		w.Header().Set("ETag", etag)
		if r.Header.Get("If-None-Match") == etag {
			w.WriteHeader(http.StatusNotModified)
			return
		}
		sendJSON(w, 200, catalog)
	}
}

func peerFromService(entry *zeroconf.ServiceEntry, ownID string, keyed bool) []catalogPeer {
	attributes := map[string]string{}
	for _, txt := range entry.Text {
		key, value, ok := strings.Cut(txt, "=")
		if ok {
			attributes[key] = value
		}
	}
	if attributes["version"] != "1" || attributes["peers"] != "1" || !validID.MatchString(attributes["id"]) ||
		attributes["id"] == ownID || (attributes["peer-key"] == "1") != keyed || entry.Port < 1 || entry.Port > 65535 {
		return nil
	}
	var peers []catalogPeer
	for _, ip := range entry.AddrIPv4 {
		if ip.To4() != nil && !ip.IsLoopback() && (ip.IsPrivate() || ip.IsLinkLocalUnicast()) {
			peers = append(peers, catalogPeer{ID: attributes["id"], Address: "http://" + net.JoinHostPort(ip.String(), strconv.Itoa(entry.Port))})
		}
	}
	return peers
}

// Literal LAN addresses, no proxy and no redirects: never send a shared key to
// another destination supplied by a peer's HTTP response.
func peerHTTPClient() *http.Client {
	return &http.Client{Timeout: 10 * time.Second,
		Transport:     &http.Transport{Proxy: nil, DialContext: (&net.Dialer{Timeout: 5 * time.Second}).DialContext},
		CheckRedirect: func(_ *http.Request, _ []*http.Request) error { return http.ErrUseLastResponse },
	}
}

func pullPeer(ctx context.Context, client *http.Client, s *Store, peer catalogPeer, key, etag string) (peerImportResult, string, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, peer.Address+"/api/v1/peer-catalog", nil)
	if err != nil {
		return peerImportResult{}, "", err
	}
	if key != "" {
		req.Header.Set("X-Carbomon-Peer-Key", key)
	}
	if etag != "" {
		req.Header.Set("If-None-Match", etag)
	}
	response, err := client.Do(req)
	if err != nil {
		return peerImportResult{}, "", err
	}
	defer response.Body.Close()
	if response.StatusCode == http.StatusNotModified {
		return peerImportResult{}, etag, nil
	}
	if response.StatusCode != http.StatusOK {
		return peerImportResult{}, "", fmt.Errorf("peer returned HTTP %d", response.StatusCode)
	}
	const maxSnapshot = 32 << 20
	raw, err := io.ReadAll(io.LimitReader(response.Body, maxSnapshot+1))
	if err != nil {
		return peerImportResult{}, "", err
	}
	if len(raw) > maxSnapshot {
		return peerImportResult{}, "", fmt.Errorf("peer catalogue exceeds 32 MiB")
	}
	var catalog Catalog
	if err = json.Unmarshal(raw, &catalog); err != nil {
		return peerImportResult{}, "", err
	}
	if catalog.ServerID != peer.ID || catalog.ServerID == s.serverID {
		return peerImportResult{}, "", fmt.Errorf("peer identity changed")
	}
	result, err := s.importMissing(catalog)
	return result, response.Header.Get("ETag"), err
}

func discoverPeers(ctx context.Context, ownID string, keyed bool) ([]catalogPeer, error) {
	entries := make(chan *zeroconf.ServiceEntry, 32)
	done := make(chan error, 1)
	// This library's Browse blocks for the lifetime of the scan. Consume results
	// concurrently, including buffered announcements delivered just before timeout.
	go func() {
		done <- zeroconf.Browse(ctx, catalogServiceType, "local.", entries, zeroconf.SelectIPTraffic(zeroconf.IPv4))
	}()
	peers := map[string]catalogPeer{}
	collected := func() []catalogPeer {
		result := make([]catalogPeer, 0, len(peers))
		for _, peer := range peers {
			result = append(result, peer)
		}
		return result
	}
	for {
		select {
		case err := <-done:
			return collected(), err
		case entry, ok := <-entries:
			if !ok {
				return collected(), nil
			}
			for _, peer := range peerFromService(entry, ownID, keyed) {
				if len(peers) < 32 {
					peers[peer.ID+"@"+peer.Address] = peer
				}
			}
		}
	}
}

func syncPeers(ctx context.Context, s *Store, config peerConfig) {
	client := peerHTTPClient()
	defer client.CloseIdleConnections()
	etags := map[string]string{}
	for ctx.Err() == nil {
		scan, cancel := context.WithTimeout(ctx, 5*time.Second)
		peers, err := discoverPeers(scan, s.serverID, config.Key != "")
		cancel()
		if ctx.Err() != nil {
			return
		}
		if err != nil {
			log.Printf("Peer discovery: %v", err)
		}
		active := map[string]bool{}
		transfer, stopTransfer := context.WithTimeout(ctx, 20*time.Second)
		for _, peer := range peers {
			if transfer.Err() != nil {
				break
			}
			cacheKey := peer.ID + "@" + peer.Address
			active[cacheKey] = true
			result, etag, err := pullPeer(transfer, client, s, peer, config.Key, etags[cacheKey])
			if ctx.Err() != nil {
				stopTransfer()
				return
			}
			if err != nil {
				log.Printf("Peer %s unavailable: %v", peer.Address, err)
				continue
			}
			etags[cacheKey] = etag
			if result.Added > 0 || result.Tombstones > 0 || result.Different > 0 {
				log.Printf("Peer %s: added %d entries and %d deletion records; kept %d differing local versions.", peer.Address, result.Added, result.Tombstones, result.Different)
			}
		}
		stopTransfer()
		for key := range etags {
			if !active[key] {
				delete(etags, key)
			}
		}
		timer := time.NewTimer(25 * time.Second)
		select {
		case <-ctx.Done():
			timer.Stop()
			return
		case <-timer.C:
		}
	}
}
