package agents_test

import (
	"os"
	"path/filepath"
	"runtime"
	"testing"

	"sh.simple/simpled/internal/agents"
)

func TestParseFxStatusJSON(t *testing.T) {
	_, file, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("caller")
	}
	path := filepath.Join(filepath.Dir(file), "testdata", "fx_status.json")
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if got := agents.ParseFxStatusJSON(data); got != "auto" {
		t.Fatalf("mode %q", got)
	}
}

func TestFxModelsResult(t *testing.T) {
	r := agents.FxModelsResult()
	if len(r.Models) == 0 || r.Models[0].ID != "default" {
		t.Fatalf("models %#v", r.Models)
	}
	if len(r.Modes) != 0 {
		t.Fatalf("modes %#v", r.Modes)
	}
}

func TestInstalledKindsShell(t *testing.T) {
	r := agents.InstalledKinds()
	if !r.Kinds["shell"] {
		t.Fatal("shell")
	}
}
