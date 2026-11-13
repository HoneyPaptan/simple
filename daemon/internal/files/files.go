package files

import (
	"bufio"
	"bytes"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"unicode/utf8"

	"sh.simple/simpled/internal/wire"
)

const maxReadBytes = 8 * 1024 * 1024

func List(rawPath string) (wire.FilesListResult, error) {
	path, err := ResolvePath(rawPath)
	if err != nil {
		return wire.FilesListResult{}, err
	}
	ent, err := os.Stat(path)
	if err != nil {
		if os.IsNotExist(err) {
			return wire.FilesListResult{}, errNotFound
		}
		if os.IsPermission(err) {
			return wire.FilesListResult{}, errDenied
		}
		return wire.FilesListResult{}, errInternal
	}
	if !ent.IsDir() {
		return wire.FilesListResult{}, errBadRequest
	}
	names, err := os.ReadDir(path)
	if err != nil {
		if os.IsPermission(err) {
			return wire.FilesListResult{}, errDenied
		}
		return wire.FilesListResult{}, errInternal
	}
	entries := make([]wire.FileEntry, 0, len(names))
	for _, de := range names {
		name := de.Name()
		info, err := de.Info()
		if err != nil {
			continue
		}
		if info.Mode()&os.ModeSymlink != 0 {
			if target, err := os.Stat(filepath.Join(path, name)); err == nil {
				info = target
			}
		}
		var size int64
		if !info.IsDir() {
			size = info.Size()
		}
		entries = append(entries, wire.FileEntry{
			Name:    name,
			Dir:     info.IsDir(),
			Size:    size,
			MtimeMs: info.ModTime().UnixMilli(),
		})
	}
	sort.Slice(entries, func(i, j int) bool {
		if entries[i].Dir != entries[j].Dir {
			return entries[i].Dir
		}
		return strings.ToLower(entries[i].Name) < strings.ToLower(entries[j].Name)
	})
	return wire.FilesListResult{Entries: entries}, nil
}

func Read(rawPath string, offset, limit *int) (wire.FilesReadResult, error) {
	path, err := ResolvePath(rawPath)
	if err != nil {
		return wire.FilesReadResult{}, err
	}
	ent, err := os.Stat(path)
	if err != nil {
		if os.IsNotExist(err) {
			return wire.FilesReadResult{}, errNotFound
		}
		if os.IsPermission(err) {
			return wire.FilesReadResult{}, errDenied
		}
		return wire.FilesReadResult{}, errInternal
	}
	if ent.IsDir() {
		return wire.FilesReadResult{}, errBadRequest
	}
	if ent.Size() > maxReadBytes {
		return wire.FilesReadResult{}, errTooLarge
	}
	f, err := os.Open(path)
	if err != nil {
		if os.IsPermission(err) {
			return wire.FilesReadResult{}, errDenied
		}
		return wire.FilesReadResult{}, errInternal
	}
	defer f.Close()
	data, err := io.ReadAll(f)
	if err != nil {
		return wire.FilesReadResult{}, errInternal
	}
	if bytes.IndexByte(data, 0) >= 0 || !utf8.Valid(data) {
		return wire.FilesReadResult{}, errBinary
	}
	text := string(data)
	lines := splitLines(text)
	total := len(lines)
	off := 0
	if offset != nil && *offset > 0 {
		off = *offset
	}
	if off > total {
		off = total
	}
	end := total
	truncated := false
	if limit != nil && *limit >= 0 {
		end = off + *limit
		if end < total {
			truncated = true
		}
		if end > total {
			end = total
		}
	}
	chunk := text
	if off > 0 || end < total {
		chunk = strings.Join(lines[off:end], "\n")
		if end > off && end < total {
			chunk += "\n"
		}
	}
	return wire.FilesReadResult{
		Text:      chunk,
		Lines:     end - off,
		Truncated: truncated,
	}, nil
}

func Write(rawPath, text string) error {
	path, err := ResolvePath(rawPath)
	if err != nil {
		return err
	}
	dir := filepath.Dir(path)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		if os.IsPermission(err) {
			return errDenied
		}
		return errInternal
	}
	mode := os.FileMode(0o644)
	if st, err := os.Stat(path); err == nil {
		if st.IsDir() {
			return errBadRequest
		}
		mode = st.Mode() & os.ModePerm
	} else if !os.IsNotExist(err) {
		if os.IsPermission(err) {
			return errDenied
		}
		return errInternal
	}
	tmp, err := os.CreateTemp(dir, ".simpled-write-*")
	if err != nil {
		if os.IsPermission(err) {
			return errDenied
		}
		return errInternal
	}
	tmpPath := tmp.Name()
	ok := false
	defer func() {
		if !ok {
			os.Remove(tmpPath)
		}
	}()
	if _, err := io.Copy(tmp, strings.NewReader(text)); err != nil {
		tmp.Close()
		return errInternal
	}
	if err := tmp.Chmod(mode); err != nil {
		tmp.Close()
		return errInternal
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
	ok = true
	return nil
}

func splitLines(s string) []string {
	if s == "" {
		return []string{}
	}
	sc := bufio.NewScanner(bytes.NewReader([]byte(s)))
	sc.Split(bufio.ScanLines)
	var out []string
	for sc.Scan() {
		out = append(out, sc.Text())
	}
	if len(out) == 0 {
		return []string{""}
	}
	return out
}
