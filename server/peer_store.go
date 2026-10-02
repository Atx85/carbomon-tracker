package main

import (
	"database/sql"
	"encoding/json"
	"fmt"
)

type peerImportResult struct {
	Added      int
	Tombstones int
	Different  int
}

// Peer exchange fills gaps. It never replaces a local edit or deletion with a
// remote version whose revision belongs to a different server's revision clock.
func (s *Store) importMissing(c Catalog) (peerImportResult, error) {
	result := peerImportResult{}
	if c.SchemaVersion != 1 || !validID.MatchString(c.ServerID) || c.Revision < 0 || len(c.Records) > 10000 {
		return result, fmt.Errorf("invalid peer catalogue")
	}
	seen := make(map[string]bool)
	for i := range c.Records {
		r := &c.Records[i]
		key := r.Kind + ":" + r.ID
		if (r.Kind != "food" && r.Kind != "recipe") || !validID.MatchString(r.ID) || r.Revision <= 0 || r.Revision > c.Revision || seen[key] {
			return result, fmt.Errorf("invalid or duplicate peer record: %s", key)
		}
		seen[key] = true
		if r.Deleted {
			r.Data = json.RawMessage("null")
		} else {
			data, err := normalize(r.Kind, r.Data, s.schemas)
			if err != nil {
				return result, fmt.Errorf("invalid peer record %s: %w", key, err)
			}
			r.Data = data
		}
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	tx, err := s.db.Begin()
	if err != nil {
		return result, err
	}
	defer tx.Rollback()
	var revision int64
	if err = tx.QueryRow("SELECT value FROM metadata WHERE key='revision'").Scan(&revision); err != nil {
		return result, err
	}
	for _, r := range c.Records {
		var oldData string
		var oldDeleted bool
		err = tx.QueryRow("SELECT deleted,data FROM records WHERE kind=? AND id=?", r.Kind, r.ID).Scan(&oldDeleted, &oldData)
		if err == nil {
			if oldDeleted != r.Deleted || !equalData(json.RawMessage(oldData), r.Data) {
				result.Different++
			}
			continue
		}
		if err != sql.ErrNoRows {
			return peerImportResult{}, err
		}
		revision++
		if _, err = tx.Exec("INSERT INTO records(kind,id,revision,deleted,data) VALUES (?,?,?,?,?)", r.Kind, r.ID, revision, r.Deleted, string(r.Data)); err != nil {
			return peerImportResult{}, err
		}
		if r.Deleted {
			result.Tombstones++
		} else {
			result.Added++
		}
	}
	if _, err = tx.Exec("UPDATE metadata SET value=? WHERE key='revision'", revision); err != nil {
		return peerImportResult{}, err
	}
	if err = tx.Commit(); err != nil {
		return peerImportResult{}, err
	}
	return result, nil
}
