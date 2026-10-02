package main

import (
	"bytes"
	"context"
	"crypto/rand"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"io/fs"
	"log"
	"mime"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"time"
)

func sendJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(value)
}
func fail(w http.ResponseWriter, status int, err error) {
	sendJSON(w, status, map[string]any{"error": err.Error()})
}
func decode(w http.ResponseWriter, r *http.Request, target any) error {
	media, _, _ := mime.ParseMediaType(r.Header.Get("Content-Type"))
	if media != "application/json" {
		return fmt.Errorf("Content-Type must be application/json")
	}
	r.Body = http.MaxBytesReader(w, r.Body, 16<<20)
	d := json.NewDecoder(r.Body)
	d.DisallowUnknownFields()
	if err := d.Decode(target); err != nil {
		return err
	}
	if err := d.Decode(new(any)); err != io.EOF {
		return fmt.Errorf("expected one JSON value")
	}
	return nil
}

// Creation and validation accept either one object or a non-empty list.
func normalizeImport(kind string, raw json.RawMessage, s *Store) ([]json.RawMessage, bool, error) {
	raw = bytes.TrimSpace(raw)
	batch := len(raw) > 0 && raw[0] == '['
	items := []json.RawMessage{raw}
	if batch {
		if err := json.Unmarshal(raw, &items); err != nil {
			return nil, true, err
		}
	}
	if len(items) == 0 || len(items) > 1000 {
		return nil, batch, fmt.Errorf("provide between 1 and 1000 entries")
	}
	for i, item := range items {
		normalized, err := normalize(kind, item, s.schemas)
		if err != nil {
			return nil, batch, fmt.Errorf("entry %d: %w", i+1, err)
		}
		items[i] = normalized
	}
	return items, batch, nil
}

func api(s *Store, key string, peerOptions ...peerConfig) http.Handler {
	peers := peerConfig{}
	if len(peerOptions) > 0 {
		peers = peerOptions[0]
	}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /api/v1/peer-catalog", peerCatalogHandler(s, peers))
	web, _ := fs.Sub(assets, "web")
	mux.Handle("GET /", http.FileServer(http.FS(web)))
	for _, kind := range []string{"food", "recipe"} {
		for _, folder := range []string{"schemas", "examples"} {
			raw, _ := assets.ReadFile(folder + "/" + kind + ".json")
			mux.HandleFunc("GET /api/v1/"+folder+"/"+kind, func(w http.ResponseWriter, r *http.Request) {
				w.Header().Set("Content-Type", "application/json")
				w.Write(raw)
			})
		}
	}
	mux.HandleFunc("GET /api/v1/catalog", func(w http.ResponseWriter, r *http.Request) {
		c, err := s.snapshot()
		if err != nil {
			fail(w, 500, err)
			return
		}
		sendJSON(w, 200, c)
	})
	result := func(w http.ResponseWriter, req SyncRequest) {
		c, err := s.synchronize(req)
		if err != nil {
			var conflict *Conflicts
			if errors.As(err, &conflict) {
				sendJSON(w, 409, map[string]any{"error": err.Error(), "conflicts": conflict.Records, "serverId": s.serverID})
				return
			}
			if errors.Is(err, errWrongServer) {
				fail(w, 409, err)
				return
			}
			fail(w, 400, err)
			return
		}
		sendJSON(w, 200, c)
	}
	mux.HandleFunc("POST /api/v1/sync", func(w http.ResponseWriter, r *http.Request) {
		var req SyncRequest
		if err := decode(w, r, &req); err != nil {
			fail(w, 400, err)
			return
		}
		result(w, req)
	})
	for _, kind := range []string{"food", "recipe"} {
		plural := kind + "s"
		mux.HandleFunc("POST /api/v1/validate/"+kind, func(w http.ResponseWriter, r *http.Request) {
			var raw json.RawMessage
			if err := decode(w, r, &raw); err != nil {
				fail(w, 400, err)
				return
			}
			items, batch, err := normalizeImport(kind, raw, s)
			if err != nil {
				fail(w, 400, err)
				return
			}
			if batch {
				sendJSON(w, 200, items)
			} else {
				sendJSON(w, 200, items[0])
			}
		})
		mux.HandleFunc("POST /api/v1/"+plural, func(w http.ResponseWriter, r *http.Request) {
			var raw json.RawMessage
			if err := decode(w, r, &raw); err != nil {
				fail(w, 400, err)
				return
			}
			id := r.Header.Get("Idempotency-Key")
			if id == "" {
				id = newID()
			}
			items, batch, err := normalizeImport(kind, raw, s)
			if err != nil {
				fail(w, 400, err)
				return
			}
			if !validID.MatchString(id) || len(id) > 180 {
				fail(w, 400, fmt.Errorf("invalid Idempotency-Key (maximum 180 letters, digits, colons, underscores or hyphens)"))
				return
			}
			changes := make([]Mutation, 0, len(items))
			for i, item := range items {
				entryID := id
				if batch {
					entryID = fmt.Sprintf("%s:%d", id, i)
				}
				changes = append(changes, Mutation{Kind: kind, ID: entryID, Data: item})
			}
			result(w, SyncRequest{SchemaVersion: 1, Changes: changes})
		})
		for _, method := range []string{"PUT", "DELETE"} {
			mux.HandleFunc(method+" /api/v1/"+plural+"/{id}", func(w http.ResponseWriter, r *http.Request) {
				rev, err := strconv.ParseInt(strings.Trim(r.Header.Get("If-Match"), `"`), 10, 64)
				if err != nil || rev <= 0 {
					fail(w, 428, fmt.Errorf("If-Match must contain the current entry revision"))
					return
				}
				m := Mutation{Kind: kind, ID: r.PathValue("id"), BaseRevision: rev, Deleted: method == "DELETE"}
				if !m.Deleted {
					if err := decode(w, r, &m.Data); err != nil {
						fail(w, 400, err)
						return
					}
				}
				result(w, SyncRequest{SchemaVersion: 1, Changes: []Mutation{m}})
			})
		}
	}
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("Cache-Control", "no-store")
		w.Header().Set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; frame-ancestors 'none'; base-uri 'none'")
		host, _, err := net.SplitHostPort(r.RemoteAddr)
		ip := net.ParseIP(host)
		if err != nil || ip == nil || !(ip.IsLoopback() || ip.IsPrivate() || ip.IsLinkLocalUnicast()) {
			fail(w, 403, fmt.Errorf("local network access only"))
			return
		}
		if origin := r.Header.Get("Origin"); origin != "" {
			u, err := url.Parse(origin)
			if err != nil || u.Host != r.Host || (u.Scheme != "http" && u.Scheme != "https") {
				fail(w, 403, fmt.Errorf("cross-origin requests are not allowed"))
				return
			}
		}
		public := r.Method == "GET" && (!strings.HasPrefix(r.URL.Path, "/api/") || strings.HasPrefix(r.URL.Path, "/api/v1/schemas/") || strings.HasPrefix(r.URL.Path, "/api/v1/examples/") || r.URL.Path == "/api/v1/peer-catalog")
		if !public && subtle.ConstantTimeCompare([]byte(r.Header.Get("Authorization")), []byte("Bearer "+key)) != 1 {
			fail(w, 401, fmt.Errorf("enter the server access key"))
			return
		}
		mux.ServeHTTP(w, r)
	})
}

func readKey(path string) (string, error) {
	raw, err := os.ReadFile(path)
	if err == nil {
		key := strings.TrimSpace(string(raw))
		if len(key) < 24 {
			return "", fmt.Errorf("access key must be at least 24 characters")
		}
		return key, nil
	}
	if !os.IsNotExist(err) {
		return "", err
	}
	var b [32]byte
	if _, err = rand.Read(b[:]); err != nil {
		return "", err
	}
	key := hex.EncodeToString(b[:])
	f, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
	if err != nil {
		return "", err
	}
	_, err = f.WriteString(key + "\n")
	closeErr := f.Close()
	if err == nil {
		err = closeErr
	}
	return key, err
}
func main() {
	if err := run(); err != nil {
		log.Fatal(err)
	}
}

func run() error {
	listen := flag.String("listen", "0.0.0.0:8765", "LAN address and port")
	dataDir := flag.String("data", "data", "database and access-key directory")
	discovery := flag.Bool("discovery", true, "advertise this catalogue for the app's Find server button")
	peerSync := flag.Bool("peer-sync", true, "automatically exchange missing entries with other CarboMon servers on this trusted LAN")
	peerKeyFile := flag.String("peer-key-file", "", "optional file containing a shared peer key (same file contents on all participating servers)")
	flag.Parse()
	peers := peerConfig{Enabled: *peerSync}
	if *peerKeyFile != "" {
		raw, err := os.ReadFile(*peerKeyFile)
		if err != nil {
			return err
		}
		peers.Key = strings.TrimSpace(string(raw))
		if len(peers.Key) < 24 {
			return fmt.Errorf("shared peer key must be at least 24 characters")
		}
	}
	if err := os.MkdirAll(*dataDir, 0700); err != nil {
		return err
	}
	keyPath := filepath.Join(*dataDir, "access-key.txt")
	key, err := readKey(keyPath)
	if err != nil {
		return err
	}
	s, err := openStore(filepath.Join(*dataDir, "catalog.db"))
	if err != nil {
		return err
	}
	defer s.db.Close()
	listener, err := net.Listen("tcp", *listen)
	if err != nil {
		return err
	}
	defer listener.Close()
	fmt.Printf("CarboMon catalogue: http://%s\nAccess key: %s\nUse Find server in the Android app, or enter this computer's LAN address.\n", listener.Addr(), keyPath)
	if *discovery {
		announcement, err := advertiseCatalog(listener.Addr().(*net.TCPAddr), s.serverID, peers)
		if err != nil {
			log.Printf("Automatic discovery unavailable: %v. Manual server addresses still work.", err)
		} else {
			defer announcement.Shutdown()
			log.Print("Automatic discovery is available on the local network.")
		}
	}
	srv := &http.Server{Addr: *listen, Handler: api(s, key, peers), ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 30 * time.Second, WriteTimeout: 60 * time.Second, IdleTimeout: 60 * time.Second, MaxHeaderBytes: 8192}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	if peers.Enabled {
		if peers.Key == "" {
			log.Print("Automatic peer sharing enabled: other devices on this trusted LAN can read the shared catalogue.")
		}
		peerDone := make(chan struct{})
		go func() { defer close(peerDone); syncPeers(ctx, s, peers) }()
		defer func() { stop(); <-peerDone }()
	}
	done := make(chan error, 1)
	go func() { done <- srv.Serve(listener) }()
	select {
	case err := <-done:
		return err
	case <-ctx.Done():
		shutdown, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := srv.Shutdown(shutdown); err != nil {
			srv.Close()
			return err
		}
		return nil
	}
}
