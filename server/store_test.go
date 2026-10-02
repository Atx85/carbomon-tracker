package main

import (
	"bytes"
	"encoding/json"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
)

func testStore(t *testing.T) *Store {
	t.Helper()
	s, e := openStore(filepath.Join(t.TempDir(), "test.db"))
	if e != nil {
		t.Fatal(e)
	}
	t.Cleanup(func() { s.db.Close() })
	return s
}
func food(t *testing.T) json.RawMessage {
	t.Helper()
	b, e := assets.ReadFile("examples/food.json")
	if e != nil {
		t.Fatal(e)
	}
	return b
}
func change(id string, base int64, data json.RawMessage) Mutation {
	return Mutation{Kind: "food", ID: id, BaseRevision: base, Data: data}
}
func syncChanges(s *Store, m ...Mutation) (Catalog, error) {
	return s.synchronize(SyncRequest{SchemaVersion: 1, Changes: m})
}
func TestSyncRetryConflictAndDelete(t *testing.T) {
	s := testStore(t)
	m := change("catalog-test", 0, food(t))
	c, e := syncChanges(s, m)
	if e != nil {
		t.Fatal(e)
	}
	if c.Revision != 1 || len(c.Records) != 1 {
		t.Fatal(c)
	}
	retry, e := syncChanges(s, m)
	if e != nil || retry.Revision != 1 {
		t.Fatalf("retry: %+v %v", retry, e)
	}
	m.BaseRevision = 1
	m.Data = bytes.ReplaceAll(food(t), []byte("rolled oats"), []byte("oat flakes"))
	c, e = syncChanges(s, m)
	if e != nil || c.Revision != 2 {
		t.Fatal(c, e)
	}
	stale := change(m.ID, 1, food(t))
	_, e = syncChanges(s, change("new", 0, food(t)), stale)
	if _, ok := e.(*Conflicts); !ok {
		t.Fatalf("expected conflict: %v", e)
	}
	c, _ = s.snapshot()
	if c.Revision != 2 || len(c.Records) != 1 {
		t.Fatal("partial conflict commit", c)
	}
	_, e = syncChanges(s, Mutation{Kind: "food", ID: m.ID, BaseRevision: 1, Deleted: true})
	if e == nil {
		t.Fatal("stale delete accepted")
	}
	c, e = syncChanges(s, Mutation{Kind: "food", ID: m.ID, BaseRevision: 2, Deleted: true})
	if e != nil || !c.Records[0].Deleted {
		t.Fatal(c, e)
	}
	_, e = syncChanges(s, stale)
	if e == nil {
		t.Fatal("stale client resurrected deletion")
	}
	c, e = syncChanges(s, Mutation{Kind: "food", ID: m.ID, BaseRevision: 2, Deleted: true})
	if e != nil || c.Revision != 3 {
		t.Fatal("delete retry", c, e)
	}
}
func TestValidationIsAtomicAndSchemasMatch(t *testing.T) {
	s := testStore(t)
	bad := bytes.ReplaceAll(food(t), []byte(`"caloriesPer100g": 370`), []byte(`"caloriesPer100g": -1`))
	_, e := syncChanges(s, change("valid", 0, food(t)), change("bad", 0, bad))
	if e == nil {
		t.Fatal("invalid data accepted")
	}
	c, _ := s.snapshot()
	if len(c.Records) != 0 {
		t.Fatal("partial write")
	}
	for _, kind := range []string{"food", "recipe"} {
		b, _ := assets.ReadFile("examples/" + kind + ".json")
		if _, e := normalize(kind, b, s.schemas); e != nil {
			t.Fatal(e)
		}
	}
	recipe, _ := assets.ReadFile("examples/recipe.json")
	bad = bytes.ReplaceAll(recipe, []byte(`"finishedWeightGrams": 250`), []byte(`"finishedWeightGrams": 0`))
	if _, e := normalize("recipe", bad, s.schemas); e == nil {
		t.Fatal("zero finished weight accepted")
	}
	_, e = s.synchronize(SyncRequest{SchemaVersion: 1, ServerID: "wrong"})
	if e != errWrongServer {
		t.Fatal(e)
	}
}
func TestPersistence(t *testing.T) {
	path := filepath.Join(t.TempDir(), "test.db")
	s, e := openStore(path)
	if e != nil {
		t.Fatal(e)
	}
	c, e := syncChanges(s, change("keep", 0, food(t)))
	if e != nil {
		t.Fatal(e)
	}
	s.db.Close()
	s, e = openStore(path)
	if e != nil {
		t.Fatal(e)
	}
	defer s.db.Close()
	got, e := s.snapshot()
	if e != nil || got.ServerID != c.ServerID || got.Revision != 1 || len(got.Records) != 1 {
		t.Fatal(got, e)
	}
}
func TestHTTPAuthOriginCRUDAndLimits(t *testing.T) {
	s := testStore(t)
	h := api(s, "test-key")
	call := func(method, path, body, token, origin, rev string) *httptest.ResponseRecorder {
		r := httptest.NewRequest(method, "http://localhost:8765"+path, strings.NewReader(body))
		r.RemoteAddr = "127.0.0.1:1234"
		r.Header.Set("Content-Type", "application/json")
		r.Header.Set("Authorization", token)
		r.Header.Set("Origin", origin)
		r.Header.Set("If-Match", rev)
		r.Header.Set("Idempotency-Key", "browser-entry")
		w := httptest.NewRecorder()
		h.ServeHTTP(w, r)
		return w
	}
	if w := call("GET", "/api/v1/catalog", "", "", "", ""); w.Code != 401 {
		t.Fatal(w.Code)
	}
	if w := call("GET", "/api/v1/schemas/food", "", "", "", ""); w.Code != 200 {
		t.Fatal(w.Code)
	}
	if w := call("POST", "/api/v1/foods", string(food(t)), "Bearer test-key", "http://evil.local", ""); w.Code != 403 {
		t.Fatal(w.Code)
	}
	for range 2 {
		if w := call("POST", "/api/v1/foods", string(food(t)), "Bearer test-key", "http://localhost:8765", ""); w.Code != 200 {
			t.Fatal(w.Code, w.Body.String())
		}
	}
	c, _ := s.snapshot()
	if c.Revision != 1 {
		t.Fatal("duplicate browser retry")
	}
	if w := call("PUT", "/api/v1/foods/browser-entry", string(food(t)), "Bearer test-key", "", ""); w.Code != 428 {
		t.Fatal(w.Code)
	}
	if w := call("DELETE", "/api/v1/foods/browser-entry", "", "Bearer test-key", "", "1"); w.Code != 200 {
		t.Fatal(w.Code, w.Body.String())
	}
	if w := call("POST", "/api/v1/foods", string(food(t))+" {}", "Bearer test-key", "", ""); w.Code != 400 {
		t.Fatal("trailing JSON accepted")
	}
	if w := call("POST", "/api/v1/foods", `{"description":"`+strings.Repeat("x", 17<<20)+`"}`, "Bearer test-key", "", ""); w.Code != 400 {
		t.Fatal("oversized body accepted", w.Code)
	}
}

func TestBulkImportsAreAtomicAndRetrySafe(t *testing.T) {
	s := testStore(t)
	h := api(s, "test-key")
	call := func(path string, body []byte) *httptest.ResponseRecorder {
		r := httptest.NewRequest("POST", "http://localhost"+path, bytes.NewReader(body))
		r.RemoteAddr = "127.0.0.1:1234"
		r.Header.Set("Authorization", "Bearer test-key")
		r.Header.Set("Content-Type", "application/json")
		r.Header.Set("Idempotency-Key", "batch-1")
		w := httptest.NewRecorder()
		h.ServeHTTP(w, r)
		return w
	}
	for _, kind := range []string{"food", "recipe"} {
		raw, _ := assets.ReadFile("examples/" + kind + ".json")
		batch, _ := json.Marshal([]json.RawMessage{raw, raw})
		if w := call("/api/v1/validate/"+kind, batch); w.Code != 200 || !strings.HasPrefix(w.Body.String(), "[") {
			t.Fatal(w.Code, w.Body.String())
		}
		for range 2 {
			if w := call("/api/v1/"+kind+"s", batch); w.Code != 200 {
				t.Fatal(w.Code, w.Body.String())
			}
		}
		invalid, _ := json.Marshal([]json.RawMessage{raw, json.RawMessage(`{}`)})
		if w := call("/api/v1/"+kind+"s", invalid); w.Code != 400 || !strings.Contains(w.Body.String(), "entry 2") {
			t.Fatal(w.Code, w.Body.String())
		}
		if w := call("/api/v1/"+kind+"s", []byte(`[]`)); w.Code != 400 {
			t.Fatal(w.Code)
		}
	}
	c, _ := s.snapshot()
	if c.Revision != 4 || len(c.Records) != 4 {
		t.Fatal("bulk duplicate or partial save", c)
	}
}
