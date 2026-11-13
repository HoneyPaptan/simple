package files

import (
	"bufio"
	"context"
	"errors"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"sh.simple/simpled/internal/wire"
)

var errWalkCap = errors.New("walk_cap")

const (
	maxSearchFiles   = 20000
	defaultSearchLimit = 30
	searchGitTimeout   = 30 * time.Second
)

func Search(rawCwd, query string, limit *int) (wire.FilesSearchResult, error) {
	cwd, err := ResolvePathUnderHome(rawCwd)
	if err != nil {
		return wire.FilesSearchResult{}, err
	}
	st, err := os.Stat(cwd)
	if err != nil {
		if os.IsNotExist(err) {
			return wire.FilesSearchResult{}, errNotFound
		}
		if os.IsPermission(err) {
			return wire.FilesSearchResult{}, errDenied
		}
		return wire.FilesSearchResult{}, errInternal
	}
	if !st.IsDir() {
		return wire.FilesSearchResult{}, errBadRequest
	}
	lim := defaultSearchLimit
	if limit != nil && *limit > 0 {
		lim = *limit
	}
	if lim > maxSearchFiles {
		lim = maxSearchFiles
	}
	paths, err := listSearchPaths(cwd)
	if err != nil {
		return wire.FilesSearchResult{}, err
	}
	q := strings.ToLower(strings.TrimSpace(query))
	if q == "" {
		if len(paths) > lim {
			paths = paths[:lim]
		}
		return wire.FilesSearchResult{Paths: paths}, nil
	}
	ranked := rankPaths(paths, q)
	if len(ranked) > lim {
		ranked = ranked[:lim]
	}
	return wire.FilesSearchResult{Paths: ranked}, nil
}

func listSearchPaths(cwd string) ([]string, error) {
	if isGitRepo(cwd) {
		return gitFiles(cwd)
	}
	return walkFiles(cwd)
}

func isGitRepo(cwd string) bool {
	out, err := exec.Command("git", "-C", cwd, "rev-parse", "--git-dir").CombinedOutput()
	return err == nil && len(strings.TrimSpace(string(out))) > 0
}

func gitFiles(cwd string) ([]string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), searchGitTimeout)
	defer cancel()
	cmd := exec.CommandContext(ctx, "git", "-C", cwd, "ls-files", "--cached", "--others", "--exclude-standard")
	out, err := cmd.Output()
	if err != nil {
		if ctx.Err() != nil {
			return nil, errInternal
		}
		return nil, errInternal
	}
	var paths []string
	sc := bufio.NewScanner(bufio.NewReader(strings.NewReader(string(out))))
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		if line == "" {
			continue
		}
		paths = append(paths, line)
		if len(paths) >= maxSearchFiles {
			break
		}
	}
	return paths, nil
}

func walkFiles(cwd string) ([]string, error) {
	var paths []string
	err := filepath.WalkDir(cwd, func(path string, d os.DirEntry, err error) error {
		if err != nil {
			return nil
		}
		if d.IsDir() {
			name := d.Name()
			if path != cwd && (strings.HasPrefix(name, ".") || skipWalkDir(name)) {
				return filepath.SkipDir
			}
			return nil
		}
		rel, err := filepath.Rel(cwd, path)
		if err != nil || rel == "." {
			return nil
		}
		paths = append(paths, filepath.ToSlash(rel))
		if len(paths) >= maxSearchFiles {
			return errWalkCap
		}
		return nil
	})
	if err != nil && !errors.Is(err, errWalkCap) {
		return nil, errInternal
	}
	sort.Strings(paths)
	return paths, nil
}

func skipWalkDir(name string) bool {
	switch name {
	case "node_modules", "build", "target":
		return true
	default:
		return false
	}
}

type rankedPath struct {
	path  string
	score int
	len   int
}

func rankPaths(paths []string, q string) []string {
	var ranked []rankedPath
	for _, p := range paths {
		base := strings.ToLower(filepath.Base(p))
		low := strings.ToLower(p)
		score := 0
		switch {
		case strings.HasPrefix(base, q):
			score = 3
		case strings.Contains(base, q):
			score = 2
		case strings.Contains(low, q):
			score = 1
		default:
			continue
		}
		ranked = append(ranked, rankedPath{path: p, score: score, len: len(p)})
	}
	sort.Slice(ranked, func(i, j int) bool {
		if ranked[i].score != ranked[j].score {
			return ranked[i].score > ranked[j].score
		}
		if ranked[i].len != ranked[j].len {
			return ranked[i].len < ranked[j].len
		}
		return ranked[i].path < ranked[j].path
	})
	out := make([]string, len(ranked))
	for i, r := range ranked {
		out[i] = r.path
	}
	return out
}
