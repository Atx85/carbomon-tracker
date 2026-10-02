package main

import (
	"bytes"
	"embed"
	"encoding/json"
	"fmt"
	"regexp"

	"github.com/santhosh-tekuri/jsonschema/v6"
)

//go:embed schemas/*.json examples/*.json web/*
var assets embed.FS

var nutrientKeys = []string{"caloriesPer100g", "proteinPer100g", "carbsPer100g", "fatPer100g", "fiberPer100g", "sugarPer100g", "sodiumMgPer100g", "potassiumMgPer100g", "calciumMgPer100g", "ironMgPer100g"}
var validID = regexp.MustCompile(`^[A-Za-z0-9:_-]{1,200}$`)

type Record struct {
	Kind     string          `json:"kind"`
	ID       string          `json:"id"`
	Revision int64           `json:"revision"`
	Deleted  bool            `json:"deleted"`
	Data     json.RawMessage `json:"data"`
}
type Mutation struct {
	Kind         string          `json:"kind"`
	ID           string          `json:"id"`
	BaseRevision int64           `json:"baseRevision"`
	Deleted      bool            `json:"deleted"`
	Data         json.RawMessage `json:"data"`
}
type Catalog struct {
	SchemaVersion int      `json:"schemaVersion"`
	ServerID      string   `json:"serverId"`
	Revision      int64    `json:"revision"`
	Records       []Record `json:"records"`
}
type SyncRequest struct {
	SchemaVersion int        `json:"schemaVersion"`
	ServerID      string     `json:"serverId"`
	Changes       []Mutation `json:"changes"`
}

func loadSchemas() (map[string]*jsonschema.Schema, error) {
	c := jsonschema.NewCompiler()
	out := map[string]*jsonschema.Schema{}
	for _, kind := range []string{"food", "recipe"} {
		raw, err := assets.ReadFile("schemas/" + kind + ".json")
		if err != nil {
			return nil, err
		}
		var doc any
		if err = json.Unmarshal(raw, &doc); err != nil {
			return nil, err
		}
		name := "https://carbomon.local/schemas/" + kind + ".json"
		if err = c.AddResource(name, doc); err != nil {
			return nil, err
		}
		s, err := c.Compile(name)
		if err != nil {
			return nil, err
		}
		out[kind] = s
	}
	return out, nil
}

func normalize(kind string, raw json.RawMessage, schemas map[string]*jsonschema.Schema) (json.RawMessage, error) {
	schema, ok := schemas[kind]
	if !ok {
		return nil, fmt.Errorf("kind must be food or recipe")
	}
	var value any
	if err := json.Unmarshal(raw, &value); err != nil {
		return nil, fmt.Errorf("invalid JSON: %w", err)
	}
	if err := schema.Validate(value); err != nil {
		return nil, fmt.Errorf("%s: %w", kind, err)
	}
	obj := value.(map[string]any)
	defaults := func(m map[string]any) {
		for _, key := range nutrientKeys {
			if _, ok := m[key]; !ok {
				m[key] = float64(0)
			}
		}
		if _, ok := m["cholesterolMgPer100g"]; !ok {
			m["cholesterolMgPer100g"] = nil
		}
	}
	if kind == "food" {
		defaults(obj)
		for _, key := range []string{"brand", "nutritionSource"} {
			if _, ok := obj[key]; !ok {
				obj[key] = ""
			}
		}
	} else {
		if _, ok := obj["notes"]; !ok {
			obj["notes"] = ""
		}
		if _, ok := obj["createdAtEpochMs"]; !ok {
			obj["createdAtEpochMs"] = 0
		}
		if _, ok := obj["finishedWeightGrams"]; !ok {
			obj["finishedWeightGrams"] = nil
		}
		for _, item := range obj["ingredients"].([]any) {
			ing := item.(map[string]any)
			defaults(ing)
			if _, ok := ing["foodId"]; !ok {
				ing["foodId"] = ""
			}
			if _, ok := ing["foodSource"]; !ok {
				ing["foodSource"] = "MANUAL"
			}
		}
	}
	return json.Marshal(obj)
}

func equalData(a, b json.RawMessage) bool { return bytes.Equal(a, b) }
