package files_test

import (
	"bytes"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"sh.simple/simpled/internal/files"
)

func TestReserveAttachmentPathUnderWorkspace(t *testing.T) {
	root := t.TempDir()
	ws := filepath.Join(root, "proj")
	if err := os.MkdirAll(ws, 0o755); err != nil {
		t.Fatal(err)
	}
	p, err := files.ReserveAttachmentPath(ws, ".png")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(p, ws+string(filepath.Separator)) {
		t.Fatalf("path %q not under workspace", p)
	}
	if !strings.Contains(p, string(filepath.Separator)+".simple"+string(filepath.Separator)+"attachments"+string(filepath.Separator)) {
		t.Fatalf("path %q missing attachments dir", p)
	}
	exclude := filepath.Join(ws, ".git", "info", "exclude")
	if err := os.MkdirAll(filepath.Dir(exclude), 0o755); err != nil {
		t.Fatal(err)
	}
	if _, err := files.ReserveAttachmentPath(ws, ".jpg"); err != nil {
		t.Fatal(err)
	}
	raw, err := os.ReadFile(exclude)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(raw), ".simple/") {
		t.Fatalf("exclude missing .simple/: %q", string(raw))
	}
}

func TestPutAttachmentRejectsOutsideWorkspace(t *testing.T) {
	root := t.TempDir()
	ws := filepath.Join(root, "proj")
	if err := os.MkdirAll(ws, 0o755); err != nil {
		t.Fatal(err)
	}
	good, err := files.ReserveAttachmentPath(ws, ".png")
	if err != nil {
		t.Fatal(err)
	}
	body := bytes.Repeat([]byte{1, 2, 3}, 10)
	if err := files.PutAttachment(good, "image/png", bytes.NewReader(body)); err != nil {
		t.Fatal(err)
	}
	bad := filepath.Join(root, "outside.png")
	putErr := files.PutAttachment(bad, "image/png", bytes.NewReader(body))
	if putErr == nil {
		t.Fatal("expected deny outside attachments dir")
	}
	if files.ErrKind(putErr) != "denied" {
		t.Fatalf("kind %q err %v", files.ErrKind(putErr), putErr)
	}
}

func TestPutAttachmentSizeAndType(t *testing.T) {
	root := t.TempDir()
	ws := filepath.Join(root, "proj")
	if err := os.MkdirAll(ws, 0o755); err != nil {
		t.Fatal(err)
	}
	p, err := files.ReserveAttachmentPath(ws, ".png")
	if err != nil {
		t.Fatal(err)
	}
	big := bytes.Repeat([]byte{0}, 20*1024*1024+1)
	putErr := files.PutAttachment(p, "image/png", bytes.NewReader(big))
	if putErr == nil {
		t.Fatal("expected too large")
	}
	if files.ErrKind(putErr) != "too_large" {
		t.Fatalf("kind %q err %v", files.ErrKind(putErr), putErr)
	}
	if err := files.PutAttachment(p, "application/pdf", io.NopCloser(bytes.NewReader([]byte{1}))); err == nil {
		t.Fatal("expected bad mime")
	}
}
