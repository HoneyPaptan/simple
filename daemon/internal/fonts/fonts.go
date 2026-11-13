package fonts

import (
	"encoding/binary"
	"io"
	"os"
	"path/filepath"
	"strings"

	"sh.simple/simpled/internal/wire"
)

var userRoots = []string{
	".local/share/fonts",
	".fonts",
}

var systemRoot = filepath.Join(string(os.PathSeparator), "usr", "share", "fonts")

func List(home string) ([]wire.FontEntry, error) {
	seen := map[string]struct{}{}
	var out []wire.FontEntry
	roots := make([]string, 0, len(userRoots)+1)
	for _, rel := range userRoots {
		roots = append(roots, filepath.Join(home, rel))
	}
	roots = append(roots, systemRoot)
	for _, dir := range roots {
		_ = filepath.WalkDir(dir, func(path string, d os.DirEntry, err error) error {
			if err != nil || d.IsDir() || !isFontFile(d.Name()) {
				return nil
			}
			if _, ok := seen[path]; ok {
				return nil
			}
			seen[path] = struct{}{}
			if !FixedPitch(path) {
				return nil
			}
			family := strings.TrimSuffix(d.Name(), filepath.Ext(d.Name()))
			out = append(out, wire.FontEntry{Path: path, Family: family})
			return nil
		})
	}
	return out, nil
}

func isFontFile(name string) bool {
	low := strings.ToLower(name)
	return strings.HasSuffix(low, ".ttf") || strings.HasSuffix(low, ".otf")
}

func FixedPitch(path string) bool {
	f, err := os.Open(path)
	if err != nil {
		return false
	}
	defer f.Close()
	head := make([]byte, 12)
	if _, err := io.ReadFull(f, head); err != nil {
		return false
	}
	count := int(binary.BigEndian.Uint16(head[4:6]))
	if count == 0 || count > 512 {
		return false
	}
	records := make([]byte, 16*count)
	if _, err := io.ReadFull(f, records); err != nil {
		return false
	}
	for i := 0; i < count; i++ {
		rec := records[i*16 : i*16+16]
		if string(rec[0:4]) != "post" {
			continue
		}
		offset := int64(binary.BigEndian.Uint32(rec[8:12]))
		field := make([]byte, 4)
		if _, err := f.ReadAt(field, offset+12); err != nil {
			return false
		}
		return binary.BigEndian.Uint32(field) != 0
	}
	return false
}

func Allowed(paths []wire.FontEntry, path string) bool {
	for _, e := range paths {
		if e.Path == path {
			return true
		}
	}
	return false
}
