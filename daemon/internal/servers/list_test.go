package servers_test

import (
	"fmt"
	"os"
	"path/filepath"
	"testing"

	"sh.simple/simpled/internal/servers"
)

func TestListFromProcFixture(t *testing.T) {
	root := t.TempDir()
	uid := os.Getuid()
	tcp := fmt.Sprintf("  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode\n   0: 0100007F:1F90 00000000:0000 0A 00000000:00000000 00:00000000 00000000  %8d        0 4242 1\n", uid)
	if err := os.MkdirAll(filepath.Join(root, "net"), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "net/tcp"), []byte(tcp), 0o644); err != nil {
		t.Fatal(err)
	}
	entries, err := servers.List(servers.ListOptions{
		Root:         root,
		OwnUID:       uid,
		ExcludePorts: map[int]struct{}{47420: {}},
		PIDForInode: func(root string, inode uint64, uid int) (int, bool) {
			if inode == 4242 {
				return 4242, true
			}
			return 0, false
		},
		ProcessCommand: func(root string, pid int) string {
			if pid == 4242 {
				return "python3"
			}
			return ""
		},
		ProcessCwd: func(root string, pid int) string {
			if pid == 4242 {
				return "/tmp/workspace"
			}
			return ""
		},
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(entries) != 1 {
		t.Fatalf("entries %+v", entries)
	}
	if entries[0].Port != 8080 || entries[0].PID != 4242 || entries[0].Command != "python3" {
		t.Fatalf("%+v", entries[0])
	}
}
