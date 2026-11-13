package opencode

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"testing"
	"time"
)

func TestPromptPostsTextToSession(t *testing.T) {
	var gotPath, gotUser string
	var gotBody map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotPath = r.URL.Path
		gotUser, _, _ = r.BasicAuth()
		_ = json.NewDecoder(r.Body).Decode(&gotBody)
		w.Write([]byte(`{"data":{}}`))
	}))
	defer srv.Close()
	state := filepath.Join(t.TempDir(), "service.json")
	raw, _ := json.Marshal(map[string]string{"url": srv.URL, "password": "pw"})
	if err := os.WriteFile(state, raw, 0o600); err != nil {
		t.Fatal(err)
	}
	c := &Client{statePath: state, http: srv.Client()}
	if err := c.Prompt(context.Background(), "ses_1", "fix the test"); err != nil {
		t.Fatal(err)
	}
	if gotPath != "/api/session/ses_1/prompt" || gotUser != "opencode" || gotBody["text"] != "fix the test" || len(gotBody) != 1 {
		t.Fatalf("path %q user %q body %v", gotPath, gotUser, gotBody)
	}
}

func TestNewSessionIDMatchesOpenCodeFormat(t *testing.T) {
	at := time.UnixMilli(1793736587768)
	id := newSessionIDAt(at)
	if !regexp.MustCompile(`^ses_[0-9a-f]{12}[0-9A-Za-z]{14}$`).MatchString(id) {
		t.Fatalf("id %q does not match the OpenCode session format", id)
	}
	stamp, err := strconv.ParseUint(id[4:16], 16, 64)
	if err != nil {
		t.Fatal(err)
	}
	if got := (^stamp & 0xffffffffffff) >> 12; got != uint64(at.UnixMilli())&0xfffffffff {
		t.Fatalf("decoded stamp %d does not match %d", got, at.UnixMilli())
	}
	if later := newSessionIDAt(at.Add(time.Second)); later[4:16] >= id[4:16] {
		t.Fatalf("later id %q must sort before %q", later, id)
	}
}
