package wire_test

import (
	"encoding/json"
	"os"
	"path/filepath"
	"runtime"
	"testing"

	"sh.simple/simpled/internal/wire"
)

func fixtureDir(t *testing.T) string {
	t.Helper()
	_, file, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("caller")
	}
	return filepath.Join(filepath.Dir(file), "..", "..", "..", "proto", "fixtures")
}

func TestFixturesRoundTrip(t *testing.T) {
	dir := fixtureDir(t)
	cases := []struct {
		file string
		decode func([]byte) (any, error)
	}{
		{"ws-hello-request.json", func(b []byte) (any, error) {
			var r struct {
				M string           `json:"m"`
				P wire.HelloParams `json:"p"`
			}
			if err := json.Unmarshal(b, &r); err != nil {
				return nil, err
			}
			return r.P, nil
		}},
		{"ws-hello-result.json", func(b []byte) (any, error) {
			var h wire.HelloResult
			err := json.Unmarshal(b, &h)
			return h, err
		}},
		{"agent.json", func(b []byte) (any, error) {
			var a wire.Agent
			err := json.Unmarshal(b, &a)
			return a, err
		}},
		{"event-agent-update.json", func(b []byte) (any, error) {
			var e wire.WSEvent
			err := json.Unmarshal(b, &e)
			return e, err
		}},
		{"event-agent-removed.json", func(b []byte) (any, error) {
			var e wire.WSEvent
			err := json.Unmarshal(b, &e)
			return e, err
		}},
		{"ws-error.json", func(b []byte) (any, error) {
			var e wire.WSError
			err := json.Unmarshal(b, &e)
			return e, err
		}},
		{"term-frame.json", func(b []byte) (any, error) {
			var f wire.TermFrame
			err := json.Unmarshal(b, &f)
			return f, err
		}},
		{"term-frame-done.json", func(b []byte) (any, error) {
			var f wire.TermFrame
			err := json.Unmarshal(b, &f)
			return f, err
		}},
		{"term-frame-full.json", func(b []byte) (any, error) {
			var f wire.TermFrame
			err := json.Unmarshal(b, &f)
			return f, err
		}},
		{"term-open-result.json", func(b []byte) (any, error) {
			var r wire.TermOpenResult
			err := json.Unmarshal(b, &r)
			return r, err
		}},
		{"message-tool.json", func(b []byte) (any, error) {
			var m wire.Message
			err := json.Unmarshal(b, &m)
			return m, err
		}},
		{"item-approval.json", func(b []byte) (any, error) {
			var it wire.Item
			err := json.Unmarshal(b, &it)
			return it, err
		}},
		{"event-transcript-append.json", func(b []byte) (any, error) {
			var e wire.WSEvent
			err := json.Unmarshal(b, &e)
			return e, err
		}},
		{"event-screen.json", func(b []byte) (any, error) {
			var e wire.WSEvent
			err := json.Unmarshal(b, &e)
			return e, err
		}},
		{"event-inbox-add.json", func(b []byte) (any, error) {
			var e wire.WSEvent
			err := json.Unmarshal(b, &e)
			return e, err
		}},
		{"files-list-result.json", func(b []byte) (any, error) {
			var r wire.FilesListResult
			err := json.Unmarshal(b, &r)
			return r, err
		}},
		{"files-mkdir-result.json", func(b []byte) (any, error) {
			var r map[string]any
			err := json.Unmarshal(b, &r)
			return r, err
		}},
		{"files-read-result.json", func(b []byte) (any, error) {
			var r wire.FilesReadResult
			err := json.Unmarshal(b, &r)
			return r, err
		}},
		{"files-upload-result.json", func(b []byte) (any, error) {
			var r wire.FilesUploadResult
			err := json.Unmarshal(b, &r)
			return r, err
		}},
		{"git-status-result.json", func(b []byte) (any, error) {
			var r wire.GitStatusResult
			err := json.Unmarshal(b, &r)
			return r, err
		}},
		{"git-commit-result.json", func(b []byte) (any, error) {
			var r wire.GitCommitResult
			err := json.Unmarshal(b, &r)
			return r, err
		}},
		{"usage-summary-result.json", func(b []byte) (any, error) {
			var r wire.UsageSummaryResult
			err := json.Unmarshal(b, &r)
			return r, err
		}},
		{"servers-list-result.json", func(b []byte) (any, error) {
			var r wire.ServersListResult
			err := json.Unmarshal(b, &r)
			return r, err
		}},
		{"preview-open-result.json", func(b []byte) (any, error) {
			var r wire.PreviewOpenResult
			err := json.Unmarshal(b, &r)
			return r, err
		}},
		{"webhooks-list-result.json", func(b []byte) (any, error) {
			var r wire.WebhooksListResult
			err := json.Unmarshal(b, &r)
			return r, err
		}},
		{"fonts-list-result.json", func(b []byte) (any, error) {
			var r wire.FontsListResult
			err := json.Unmarshal(b, &r)
			return r, err
		}},
		{"agent-models-result.json", func(b []byte) (any, error) {
			var r wire.AgentModelsResult
			err := json.Unmarshal(b, &r)
			return r, err
		}},
		{"agent-commands-result.json", func(b []byte) (any, error) {
			var r wire.AgentCommandsResult
			err := json.Unmarshal(b, &r)
			return r, err
		}},
		{"files-search-result.json", func(b []byte) (any, error) {
			var r wire.FilesSearchResult
			err := json.Unmarshal(b, &r)
			return r, err
		}},
		{"pair-request.json", func(b []byte) (any, error) {
			var r wire.PairRequest
			err := json.Unmarshal(b, &r)
			return r, err
		}},
		{"agent-kinds-result.json", func(b []byte) (any, error) {
			var r wire.AgentKindsResult
			err := json.Unmarshal(b, &r)
			return r, err
		}},
		{"opencode-permission-hook-result.json", func(b []byte) (any, error) {
			var r struct {
				Status string `json:"status"`
			}
			err := json.Unmarshal(b, &r)
			return r, err
		}},
		{"opencode-permission-event-result.json", func(b []byte) (any, error) {
			var r struct {
				Reply string `json:"reply"`
			}
			err := json.Unmarshal(b, &r)
			return r, err
		}},
		{"panes-list-result.json", func(b []byte) (any, error) {
			var r wire.PanesListResult
			err := json.Unmarshal(b, &r)
			return r, err
		}},
		{"panes-peek-result.json", func(b []byte) (any, error) {
			var r wire.PanesPeekResult
			err := json.Unmarshal(b, &r)
			return r, err
		}},
		{"panes-spawn-result.json", func(b []byte) (any, error) {
			var r wire.PanesSpawnResult
			err := json.Unmarshal(b, &r)
			return r, err
		}},
	}
	for _, c := range cases {
		t.Run(c.file, func(t *testing.T) {
			b, err := os.ReadFile(filepath.Join(dir, c.file))
			if err != nil {
				t.Fatal(err)
			}
			v, err := c.decode(b)
			if err != nil {
				t.Fatal(err)
			}
			out, err := json.Marshal(v)
			if err != nil {
				t.Fatal(err)
			}
			var a, e any
			if json.Unmarshal(b, &a) != nil || json.Unmarshal(out, &e) != nil {
				t.Fatal("json")
			}
		})
	}
}
