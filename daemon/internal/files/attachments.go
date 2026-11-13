package files

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

const maxAttachmentBytes = 20 * 1024 * 1024

var allowedAttachmentExt = map[string]bool{
	".jpg":  true,
	".jpeg": true,
	".png":  true,
	".webp": true,
	".gif":  true,
}

var allowedAttachmentMIME = map[string]bool{
	"image/jpeg": true,
	"image/png":  true,
	"image/webp": true,
	"image/gif":  true,
}

func AttachmentDir(rawWorkspace string) (string, error) {
	ws, err := ResolvePath(rawWorkspace)
	if err != nil {
		return "", err
	}
	st, err := os.Stat(ws)
	if err != nil {
		if os.IsNotExist(err) {
			return "", errNotFound
		}
		if os.IsPermission(err) {
			return "", errDenied
		}
		return "", errInternal
	}
	if !st.IsDir() {
		return "", errBadRequest
	}
	return filepath.Join(ws, ".simple", "attachments"), nil
}

func ReserveAttachmentPath(rawWorkspace, ext string) (string, error) {
	ext = strings.ToLower(strings.TrimSpace(ext))
	if !strings.HasPrefix(ext, ".") {
		ext = "." + ext
	}
	if !allowedAttachmentExt[ext] {
		return "", errBadRequest
	}
	dir, err := AttachmentDir(rawWorkspace)
	if err != nil {
		return "", err
	}
	if err := os.MkdirAll(dir, 0o755); err != nil {
		if os.IsPermission(err) {
			return "", errDenied
		}
		return "", errInternal
	}
	ws, err := ResolvePath(rawWorkspace)
	if err != nil {
		return "", err
	}
	if err := ensureSimpleGitExclude(ws); err != nil {
		return "", err
	}
	var rnd [16]byte
	if _, err := rand.Read(rnd[:]); err != nil {
		return "", errInternal
	}
	name := hex.EncodeToString(rnd[:]) + ext
	path := filepath.Join(dir, name)
	clean, err := ResolvePath(path)
	if err != nil {
		return "", err
	}
	if !pathUnderDir(clean, dir) {
		return "", errDenied
	}
	return clean, nil
}

func PutAttachment(rawPath, contentType string, body io.Reader) error {
	path, err := ResolvePath(rawPath)
	if err != nil {
		return err
	}
	dir := filepath.Dir(path)
	wsRoot, ok := attachmentWorkspaceRoot(dir)
	if !ok {
		return errDenied
	}
	_ = wsRoot
	if !pathUnderDir(path, dir) {
		return errDenied
	}
	ext := strings.ToLower(filepath.Ext(path))
	if !allowedAttachmentExt[ext] {
		return errBadRequest
	}
	ct := strings.ToLower(strings.TrimSpace(contentType))
	if ct != "" && !allowedAttachmentMIME[ct] {
		return errBadRequest
	}
	if err := os.MkdirAll(dir, 0o755); err != nil {
		if os.IsPermission(err) {
			return errDenied
		}
		return errInternal
	}
	tmp, err := os.CreateTemp(dir, ".simple-upload-*")
	if err != nil {
		if os.IsPermission(err) {
			return errDenied
		}
		return errInternal
	}
	tmpPath := tmp.Name()
	okWrite := false
	defer func() {
		if !okWrite {
			os.Remove(tmpPath)
		}
	}()
	n, err := io.Copy(tmp, io.LimitReader(body, maxAttachmentBytes+1))
	if err != nil {
		tmp.Close()
		return errInternal
	}
	if n > maxAttachmentBytes {
		tmp.Close()
		return errTooLarge
	}
	if err := tmp.Close(); err != nil {
		return errInternal
	}
	if err := os.Rename(tmpPath, path); err != nil {
		if os.IsPermission(err) {
			return errDenied
		}
		return errInternal
	}
	okWrite = true
	return nil
}

func AttachmentWorkspaceRoot(attachmentDir string) (string, bool) {
	return attachmentWorkspaceRoot(attachmentDir)
}

func attachmentWorkspaceRoot(attachmentDir string) (string, bool) {
	clean := filepath.Clean(attachmentDir)
	simpleDir := filepath.Dir(clean)
	if filepath.Base(clean) != "attachments" || filepath.Base(simpleDir) != ".simple" {
		return "", false
	}
	ws := filepath.Dir(simpleDir)
	st, err := os.Stat(ws)
	if err != nil || !st.IsDir() {
		return "", false
	}
	return ws, true
}

func pathUnderDir(path, dir string) bool {
	rel, err := filepath.Rel(dir, path)
	if err != nil {
		return false
	}
	return rel != ".." && !strings.HasPrefix(rel, ".."+string(filepath.Separator))
}

func ensureSimpleGitExclude(workspace string) error {
	gitDir := filepath.Join(workspace, ".git")
	if st, err := os.Stat(gitDir); err != nil || !st.IsDir() {
		return nil
	}
	excludePath := filepath.Join(gitDir, "info", "exclude")
	if err := os.MkdirAll(filepath.Dir(excludePath), 0o755); err != nil {
		return errInternal
	}
	const line = ".simple/"
	b, err := os.ReadFile(excludePath)
	if err != nil && !os.IsNotExist(err) {
		if os.IsPermission(err) {
			return errDenied
		}
		return errInternal
	}
	if os.IsNotExist(err) {
		b = nil
	}
	for _, ln := range strings.Split(string(b), "\n") {
		if strings.TrimSpace(ln) == line {
			return nil
		}
	}
	out := string(b)
	if len(out) > 0 && !strings.HasSuffix(out, "\n") {
		out += "\n"
	}
	out += line + "\n"
	if err := os.WriteFile(excludePath, []byte(out), 0o644); err != nil {
		if os.IsPermission(err) {
			return errDenied
		}
		return errInternal
	}
	return nil
}

func ExtFromMIME(mime string) (string, error) {
	switch strings.ToLower(strings.TrimSpace(mime)) {
	case "image/jpeg":
		return ".jpg", nil
	case "image/png":
		return ".png", nil
	case "image/webp":
		return ".webp", nil
	case "image/gif":
		return ".gif", nil
	default:
		return "", fmt.Errorf("bad mime")
	}
}
