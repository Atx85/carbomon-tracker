package main

import (
	"bytes"
	"encoding/json"
	"net/http/httptest"
	"testing"
)

func TestKeyFreeLANAccessAndOptionalProtection(t *testing.T) {
	for _, key := range []string{"", "optional-key"} {
		t.Run("key="+key, func(t *testing.T) {
			s := testStore(t)
			h := api(s, key)
			call := func(method, path, remote, token, origin string, body []byte) *httptest.ResponseRecorder {
				r := httptest.NewRequest(method, "http://localhost"+path, bytes.NewReader(body))
				r.RemoteAddr = remote
				r.Header.Set("Content-Type", "application/json")
				if token != "" {
					r.Header.Set("Authorization", "Bearer "+token)
				}
				if origin != "" {
					r.Header.Set("Origin", origin)
				}
				w := httptest.NewRecorder()
				h.ServeHTTP(w, r)
				return w
			}
			info := call("GET", "/api/v1/server-info", "192.168.0.2:1234", "", "", nil)
			var config map[string]bool
			if err := json.Unmarshal(info.Body.Bytes(), &config); err != nil || info.Code != 200 || config["requiresAccessKey"] != (key != "") || len(config) != 1 {
				t.Fatal("incorrect public connection information", info.Code, info.Body.String())
			}
			for _, token := range []string{"", "stale-key", key} {
				want := 200
				if key != "" && token != key {
					want = 401
				}
				for _, request := range []struct {
					method, path string
					body         []byte
				}{
					{"GET", "/api/v1/catalog", nil},
					{"POST", "/api/v1/validate/food", food(t)},
					{"POST", "/api/v1/foods", food(t)},
					{"POST", "/api/v1/sync", []byte(`{"schemaVersion":1,"serverId":"","changes":[]}`)},
				} {
					if w := call(request.method, request.path, "192.168.0.2:1234", token, "", request.body); w.Code != want {
						t.Fatal(request.path, w.Code, w.Body.String())
					}
				}
			}
			if w := call("POST", "/api/v1/foods", "8.8.8.8:1234", key, "", food(t)); w.Code != 403 {
				t.Fatal("public peer accepted", w.Code)
			}
			if w := call("POST", "/api/v1/foods", "192.168.0.2:1234", key, "http://another-site.example", food(t)); w.Code != 403 {
				t.Fatal("cross-origin edit accepted", w.Code)
			}
		})
	}
}
