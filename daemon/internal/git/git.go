package git

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"sh.simple/simpled/internal/files"
	"sh.simple/simpled/internal/wire"
)

var (
	errBadRequest = errors.New("bad_request")
	errNotFound   = errors.New("not_found")
	errInternal   = errors.New("internal")
)

const (
	runTimeout  = 30 * time.Second
	pushTimeout = 60 * time.Second
)

type PushFailedError struct {
	Commit string
	Msg    string
}

func (e *PushFailedError) Error() string {
	return e.Msg
}

func ErrKind(err error) string {
	var pf *PushFailedError
	switch {
	case errors.As(err, &pf):
		return "push_failed"
	case errors.Is(err, errNotFound):
		return "not_found"
	case errors.Is(err, errBadRequest):
		return "bad_request"
	default:
		return "internal"
	}
}

func CommitResult(err error) (string, bool) {
	var pf *PushFailedError
	if errors.As(err, &pf) {
		return pf.Commit, true
	}
	return "", false
}

func Status(rawCwd string) (wire.GitStatusResult, error) {
	cwd, err := files.ResolvePath(rawCwd)
	if err != nil {
		return wire.GitStatusResult{}, errBadRequest
	}
	if !isRepo(cwd) {
		return wire.GitStatusResult{}, errNotFound
	}
	out, err := runGit(cwd, runTimeout, "status", "--porcelain=v2", "--branch")
	if err != nil {
		return wire.GitStatusResult{}, errInternal
	}
	branch, ahead, behind, upstream := parseBranch(out)
	filesOut, err := runGit(cwd, runTimeout, "diff", "--numstat")
	if err != nil {
		return wire.GitStatusResult{}, errInternal
	}
	stats := parseNumstat(filesOut)
	entries := parsePorcelainV2(cwd, out, stats)
	return wire.GitStatusResult{
		Branch:   branch,
		Ahead:    ahead,
		Behind:   behind,
		Upstream: upstream,
		Files:    entries,
	}, nil
}

func Diff(rawCwd, path string) (wire.GitDiffResult, error) {
	cwd, err := files.ResolvePath(rawCwd)
	if err != nil {
		return wire.GitDiffResult{}, errBadRequest
	}
	if !isRepo(cwd) {
		return wire.GitDiffResult{}, errNotFound
	}
	var b strings.Builder
	if path != "" {
		p, err := files.ResolvePath(filepath.Join(cwd, path))
		if err != nil {
			return wire.GitDiffResult{}, errBadRequest
		}
		if !strings.HasPrefix(p, cwd+string(filepath.Separator)) && p != cwd {
			return wire.GitDiffResult{}, errBadRequest
		}
		rel, err := filepath.Rel(cwd, p)
		if err != nil {
			return wire.GitDiffResult{}, errBadRequest
		}
		tracked, err := runGit(cwd, runTimeout, "ls-files", "--error-unmatch", rel)
		if err == nil && len(tracked) > 0 {
			diff, err := runGit(cwd, runTimeout, "diff", "--unified=3", "HEAD", "--", rel)
			if err != nil {
				return wire.GitDiffResult{}, errInternal
			}
			b.WriteString(diff)
		}
		if st, err := os.Stat(p); err == nil && !st.IsDir() {
			if len(tracked) == 0 {
				untracked, err := runGit(cwd, runTimeout, "diff", "--no-index", "/dev/null", rel)
				if err != nil {
					if out := extractDiffOutput(err); out != "" {
						b.WriteString(out)
					}
				} else {
					b.WriteString(untracked)
				}
			}
		}
	} else {
		diff, err := runGit(cwd, runTimeout, "diff", "--unified=3", "HEAD")
		if err != nil {
			return wire.GitDiffResult{}, errInternal
		}
		b.WriteString(diff)
		untracked, _ := runGit(cwd, runTimeout, "ls-files", "--others", "--exclude-standard")
		for _, rel := range splitLines(untracked) {
			if rel == "" {
				continue
			}
			part, err := runGit(cwd, runTimeout, "diff", "--no-index", "/dev/null", rel)
			if err != nil {
				if out := extractDiffOutput(err); out != "" {
					b.WriteString(out)
				}
			} else {
				b.WriteString(part)
			}
		}
	}
	return wire.GitDiffResult{Diff: b.String()}, nil
}

func Commit(rawCwd, message string, push bool) (string, error) {
	cwd, err := files.ResolvePath(rawCwd)
	if err != nil {
		return "", errBadRequest
	}
	if !isRepo(cwd) {
		return "", errNotFound
	}
	if strings.TrimSpace(message) == "" {
		return "", errBadRequest
	}
	if _, err := runGit(cwd, runTimeout, "add", "-A"); err != nil {
		return "", errInternal
	}
	if _, err := runGit(cwd, runTimeout, "commit", "-m", message); err != nil {
		return "", errInternal
	}
	hash, err := runGit(cwd, runTimeout, "rev-parse", "--short", "HEAD")
	if err != nil {
		return "", errInternal
	}
	hash = strings.TrimSpace(hash)
	if push {
		if _, err := runGit(cwd, pushTimeout, "push"); err != nil {
			line := lastLine(err)
			return hash, &PushFailedError{Commit: hash, Msg: line}
		}
	}
	return hash, nil
}

func isRepo(cwd string) bool {
	out, err := runGit(cwd, runTimeout, "rev-parse", "--git-dir")
	return err == nil && strings.TrimSpace(out) != ""
}

func runGit(cwd string, timeout time.Duration, args ...string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	argv := append([]string{"-C", cwd, "--no-pager"}, args...)
	cmd := exec.CommandContext(ctx, "git", argv...)
	cmd.Env = append(os.Environ(), "GIT_TERMINAL_PROMPT=0")
	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	if err := cmd.Run(); err != nil {
		out := stdout.String()
		if out != "" && isDiffSuccessExit(args, err) {
			return out, nil
		}
		if ctx.Err() != nil {
			return "", fmt.Errorf("%w: timeout", errInternal)
		}
		msg := strings.TrimSpace(stderr.String())
		if msg == "" {
			msg = err.Error()
		}
		return out, fmt.Errorf("%s", msg)
	}
	return stdout.String(), nil
}

func isDiffSuccessExit(args []string, err error) bool {
	var exit *exec.ExitError
	if !errors.As(err, &exit) || exit.ExitCode() != 1 {
		return false
	}
	for i, a := range args {
		if a == "diff" && i+1 < len(args) && args[i+1] == "--no-index" {
			return true
		}
	}
	return false
}

func extractDiffOutput(err error) string {
	if err == nil {
		return ""
	}
	s := err.Error()
	if idx := strings.Index(s, ":\n"); idx >= 0 {
		return s[idx+2:]
	}
	return ""
}

func lastLine(err error) string {
	if err == nil {
		return ""
	}
	lines := strings.Split(strings.TrimSpace(err.Error()), "\n")
	return lines[len(lines)-1]
}

func parseBranch(out string) (string, int, int, bool) {
	branch := ""
	ahead, behind := 0, 0
	upstream := false
	for _, line := range strings.Split(out, "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "# branch.head") {
			branch = strings.TrimSpace(strings.TrimPrefix(line, "# branch.head"))
		}
		if strings.HasPrefix(line, "# branch.upstream ") {
			upstream = true
		}
		if strings.HasPrefix(line, "# branch.ab ") {
			upstream = true
			parts := strings.Fields(strings.TrimPrefix(line, "# branch.ab "))
			if len(parts) == 2 {
				ahead, _ = strconv.Atoi(parts[0])
				behind, _ = strconv.Atoi(parts[1])
			}
		}
	}
	if branch == "" {
		branch = "HEAD"
	}
	return branch, ahead, behind, upstream
}

func parseNumstat(out string) map[string][2]int {
	m := map[string][2]int{}
	for _, line := range strings.Split(out, "\n") {
		if line == "" {
			continue
		}
		parts := strings.Fields(line)
		if len(parts) < 3 {
			continue
		}
		add, _ := strconv.Atoi(parts[0])
		del, _ := strconv.Atoi(parts[1])
		path := parts[len(parts)-1]
		m[path] = [2]int{add, del}
	}
	return m
}

func parsePorcelainV2(cwd, out string, stats map[string][2]int) []wire.GitFileStatus {
	var entries []wire.GitFileStatus
	seen := map[string]bool{}
	for _, line := range strings.Split(out, "\n") {
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		if line[0] != '1' && line[0] != '2' && line[0] != '?' {
			continue
		}
		fields := strings.Fields(line)
		if len(fields) < 2 {
			continue
		}
		status := fields[1]
		path := ""
		add, del := 0, 0
		if line[0] == '?' {
			status = "?"
			if len(fields) >= 2 {
				path = fields[1]
			}
			if path != "" {
				add = countFileLines(filepath.Join(cwd, path))
			}
		} else if len(fields) >= 9 {
			path = fields[8]
		}
		if path == "" {
			continue
		}
		letter := statusLetter(status)
		if st, ok := stats[path]; ok {
			add, del = st[0], st[1]
		}
		entries = append(entries, wire.GitFileStatus{
			Path:   path,
			Status: letter,
			Add:    add,
			Del:    del,
		})
		seen[path] = true
	}
	for path, st := range stats {
		if seen[path] {
			continue
		}
		entries = append(entries, wire.GitFileStatus{
			Path:   path,
			Status: "M",
			Add:    st[0],
			Del:    st[1],
		})
	}
	return entries
}

func statusLetter(code string) string {
	if code == "?" {
		return "?"
	}
	if len(code) < 2 {
		return code
	}
	x := code[0]
	y := code[1]
	if x == '.' && y != '.' {
		return string(y)
	}
	if y == '.' && x != '.' {
		return string(x)
	}
	if x != '.' {
		return string(x)
	}
	return string(y)
}

func splitLines(s string) []string {
	if s == "" {
		return nil
	}
	return strings.Split(strings.TrimSuffix(s, "\n"), "\n")
}

func countFileLines(path string) int {
	data, err := os.ReadFile(path)
	if err != nil || len(data) == 0 {
		if err == nil {
			return 0
		}
		return 0
	}
	n := bytes.Count(data, []byte{'\n'})
	if data[len(data)-1] != '\n' {
		n++
	}
	return n
}
