package main

import (
	"crypto/rand"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"sync"

	"github.com/santhosh-tekuri/jsonschema/v6"
	_ "modernc.org/sqlite"
)

func newID() string {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		panic(err)
	}
	return "catalog-" + hex.EncodeToString(b[:])
}

type Store struct {
	mu       sync.Mutex
	db       *sql.DB
	serverID string
	schemas  map[string]*jsonschema.Schema
}

type Conflicts struct{ Records []Record }

func (e *Conflicts) Error() string {
	return "Some entries changed on another device. Choose which version to keep, then sync again."
}

var errWrongServer = errors.New("This is a different catalogue server. Use the original server, or clear the connection in app settings before connecting to a new one.")

func openStore(path string) (*Store, error) {
	schemas, err := loadSchemas()
	if err != nil {
		return nil, err
	}
	db, err := sql.Open("sqlite", path)
	if err != nil {
		return nil, err
	}
	db.SetMaxOpenConns(1)
	failed := true
	defer func() {
		if failed {
			db.Close()
		}
	}()
	_, err = db.Exec(`PRAGMA busy_timeout=5000;
 CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);
 CREATE TABLE IF NOT EXISTS records (kind TEXT NOT NULL, id TEXT NOT NULL, revision INTEGER NOT NULL, deleted INTEGER NOT NULL, data TEXT NOT NULL, PRIMARY KEY(kind,id));
 INSERT OR IGNORE INTO metadata(key,value) VALUES ('revision','0');`)
	if err != nil {
		return nil, err
	}
	_, err = db.Exec("INSERT OR IGNORE INTO metadata(key,value) VALUES ('serverId',?)", newID())
	if err != nil {
		return nil, err
	}
	s := &Store{db: db, schemas: schemas}
	if err = db.QueryRow("SELECT value FROM metadata WHERE key='serverId'").Scan(&s.serverID); err != nil {
		return nil, err
	}
	failed = false
	return s, nil
}

type queryer interface {
	Query(string, ...any) (*sql.Rows, error)
	QueryRow(string, ...any) *sql.Row
}

func (s *Store) catalog(q queryer) (Catalog, error) {
	c := Catalog{SchemaVersion: 1, ServerID: s.serverID, Records: []Record{}}
	if err := q.QueryRow("SELECT value FROM metadata WHERE key='revision'").Scan(&c.Revision); err != nil {
		return c, err
	}
	rows, err := q.Query("SELECT kind,id,revision,deleted,data FROM records ORDER BY kind,id")
	if err != nil {
		return c, err
	}
	defer rows.Close()
	for rows.Next() {
		var r Record
		var data string
		if err = rows.Scan(&r.Kind, &r.ID, &r.Revision, &r.Deleted, &data); err != nil {
			return c, err
		}
		r.Data = json.RawMessage(data)
		c.Records = append(c.Records, r)
	}
	return c, rows.Err()
}
func (s *Store) snapshot() (Catalog, error) { s.mu.Lock(); defer s.mu.Unlock(); return s.catalog(s.db) }

func (s *Store) synchronize(req SyncRequest) (Catalog, error) {
	if req.SchemaVersion != 1 {
		return Catalog{}, fmt.Errorf("unsupported schemaVersion (expected 1)")
	}
	if req.ServerID != "" && req.ServerID != s.serverID {
		return Catalog{}, errWrongServer
	}
	if len(req.Changes) > 10000 {
		return Catalog{}, fmt.Errorf("maximum 10000 changes per sync")
	}
	seen := map[string]bool{}
	for i := range req.Changes {
		m := &req.Changes[i]
		key := m.Kind + ":" + m.ID
		if (m.Kind != "food" && m.Kind != "recipe") || !validID.MatchString(m.ID) || m.BaseRevision < 0 || seen[key] {
			return Catalog{}, fmt.Errorf("invalid or duplicate change: %s", key)
		}
		seen[key] = true
		if m.Deleted {
			if m.BaseRevision == 0 {
				return Catalog{}, fmt.Errorf("cannot delete an entry without a base revision")
			}
			m.Data = json.RawMessage("null")
		} else {
			data, err := normalize(m.Kind, m.Data, s.schemas)
			if err != nil {
				return Catalog{}, fmt.Errorf("%s: %w", key, err)
			}
			m.Data = data
		}
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	tx, err := s.db.Begin()
	if err != nil {
		return Catalog{}, err
	}
	defer tx.Rollback()
	c, err := s.catalog(tx)
	if err != nil {
		return c, err
	}
	current := map[string]Record{}
	for _, r := range c.Records {
		current[r.Kind+":"+r.ID] = r
	}
	conflicts := []Record{}
	updates := []Mutation{}
	for _, m := range req.Changes {
		old, exists := current[m.Kind+":"+m.ID]
		// Idempotent retry after a committed response was lost.
		if exists && old.Deleted == m.Deleted && equalData(old.Data, m.Data) {
			continue
		}
		if (!exists && m.BaseRevision != 0) || (exists && old.Revision != m.BaseRevision) {
			if !exists {
				old = Record{Kind: m.Kind, ID: m.ID, Deleted: true, Data: json.RawMessage("null")}
			}
			conflicts = append(conflicts, old)
			continue
		}
		updates = append(updates, m)
	}
	if len(conflicts) > 0 {
		return Catalog{}, &Conflicts{conflicts}
	}
	for _, m := range updates {
		c.Revision++
		_, err = tx.Exec(`INSERT INTO records(kind,id,revision,deleted,data) VALUES (?,?,?,?,?)
   ON CONFLICT(kind,id) DO UPDATE SET revision=excluded.revision,deleted=excluded.deleted,data=excluded.data`, m.Kind, m.ID, c.Revision, m.Deleted, string(m.Data))
		if err != nil {
			return Catalog{}, err
		}
	}
	if _, err = tx.Exec("UPDATE metadata SET value=? WHERE key='revision'", c.Revision); err != nil {
		return Catalog{}, err
	}
	c, err = s.catalog(tx)
	if err != nil {
		return c, err
	}
	if err = tx.Commit(); err != nil {
		return Catalog{}, err
	}
	return c, nil
}
