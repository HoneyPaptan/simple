package opencode

import (
	"sort"
	"strings"

	"sh.simple/simpled/internal/wire"
)

type Model struct {
	ID         string    `json:"id"`
	ProviderID string    `json:"providerID"`
	Name       string    `json:"name"`
	Enabled    bool      `json:"enabled"`
	Status     string    `json:"status"`
	Variants   []Variant `json:"variants"`
	Limit      struct {
		Context int `json:"context"`
	} `json:"limit"`
}

type Variant struct {
	ID string `json:"id"`
}

type ModelRef struct {
	ID         string `json:"id"`
	ProviderID string `json:"providerID"`
	Variant    string `json:"variant,omitempty"`
}

func ModelKey(m Model) string {
	return m.ProviderID + "/" + m.ID
}

func ParseModelKey(key, variant string) (ModelRef, bool) {
	provider, id, ok := strings.Cut(key, "/")
	if !ok || provider == "" || id == "" {
		return ModelRef{}, false
	}
	return ModelRef{ID: id, ProviderID: provider, Variant: variant}, true
}

func WireModels(models []Model) []wire.ModelInfo {
	return WireModelsForProviders(models, nil)
}

func WireModelsForProviders(models []Model, connected []string) []wire.ModelInfo {
	allow := map[string]bool{"opencode": true}
	for _, id := range connected {
		if id != "" {
			allow[id] = true
		}
	}
	out := make([]wire.ModelInfo, 0, len(models))
	for _, m := range models {
		if !m.Enabled || m.Status == "deprecated" {
			continue
		}
		if len(connected) > 0 && !allow[m.ProviderID] {
			continue
		}
		efforts := make([]string, 0, len(m.Variants))
		for _, v := range m.Variants {
			efforts = append(efforts, v.ID)
		}
		label := m.Name
		if label == "" {
			label = m.ID
		}
		out = append(out, wire.ModelInfo{ID: ModelKey(m), Label: label, Efforts: efforts})
	}
	sort.SliceStable(out, func(i, j int) bool { return out[i].Label < out[j].Label })
	return out
}
