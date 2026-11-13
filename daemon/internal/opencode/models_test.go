package opencode_test

import (
	"testing"

	"sh.simple/simpled/internal/opencode"
	"sh.simple/simpled/internal/wire"
)

func TestWireModelsForProvidersKeepsZenAndConnected(t *testing.T) {
	models := []opencode.Model{
		{ID: "big-pickle", ProviderID: "opencode", Name: "Big Pickle", Enabled: true},
		{ID: "x", ProviderID: "openrouter", Name: "X", Enabled: true},
		{ID: "y", ProviderID: "anthropic", Name: "Y", Enabled: true},
	}
	got := opencode.WireModelsForProviders(models, []string{"anthropic"})
	if len(got) != 2 {
		t.Fatalf("len=%d", len(got))
	}
	if got[0].ID != "opencode/big-pickle" || got[1].ID != "anthropic/y" {
		t.Fatalf("%+v", got)
	}
	_ = wire.ModelInfo{}
}
