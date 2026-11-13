package servers

import (
	"bufio"
	"bytes"
	"encoding/hex"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

type Entry struct {
	Port    int
	PID     int
	Command string
	Cwd     string
}

type ListOptions struct {
	Root           string
	OwnUID         int
	ExcludePorts   map[int]struct{}
	ExcludeComm    map[string]struct{}
	PIDForInode    func(root string, inode uint64, uid int) (int, bool)
	ProcessCommand func(root string, pid int) string
	ProcessCwd     func(root string, pid int) string
}

func List(opts ListOptions) ([]Entry, error) {
	if opts.Root == "" {
		opts.Root = "/proc"
	}
	if opts.OwnUID == 0 {
		opts.OwnUID = os.Getuid()
	}
	if opts.ExcludePorts == nil {
		opts.ExcludePorts = map[int]struct{}{}
	}
	if opts.ExcludeComm == nil {
		opts.ExcludeComm = map[string]struct{}{
			"simpled":     {},
			"herdr-core":  {},
			"hc":          {},
		}
	}
	if opts.PIDForInode == nil {
		opts.PIDForInode = pidForSocketInode
	}
	if opts.ProcessCommand == nil {
		opts.ProcessCommand = processCommand
	}
	if opts.ProcessCwd == nil {
		opts.ProcessCwd = processCwd
	}
	var out []Entry
	seen := map[string]struct{}{}
	appendRows := func(rows []tcpRow) {
		for _, row := range rows {
			if row.uid != opts.OwnUID || row.state != 0x0A {
				continue
			}
			if _, skip := opts.ExcludePorts[row.port]; skip {
				continue
			}
			if !listenAddrOK(row.ip) {
				continue
			}
			pid, ok := opts.PIDForInode(opts.Root, row.inode, row.uid)
			if !ok || pid <= 0 {
				continue
			}
			comm := opts.ProcessCommand(opts.Root, pid)
			if baseComm(comm) != "" {
				if _, skip := opts.ExcludeComm[baseComm(comm)]; skip {
					continue
				}
			}
			key := fmt.Sprintf("%d:%d", row.port, pid)
			if _, dup := seen[key]; dup {
				continue
			}
			seen[key] = struct{}{}
			out = append(out, Entry{
				Port:    row.port,
				PID:     pid,
				Command: comm,
				Cwd:     opts.ProcessCwd(opts.Root, pid),
			})
		}
	}
	v4, err := parseTCPFile(filepath.Join(opts.Root, "net/tcp"))
	if err != nil && !os.IsNotExist(err) {
		return nil, err
	}
	appendRows(v4)
	v6, err := parseTCPFile(filepath.Join(opts.Root, "net/tcp6"))
	if err != nil && !os.IsNotExist(err) {
		return nil, err
	}
	appendRows(v6)
	return out, nil
}

type tcpRow struct {
	ip    string
	port  int
	state int
	uid   int
	inode uint64
}

func parseTCPFile(path string) ([]tcpRow, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var rows []tcpRow
	sc := bufio.NewScanner(bytes.NewReader(b))
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		if line == "" || strings.HasPrefix(line, "sl") {
			continue
		}
		fields := strings.Fields(line)
		if len(fields) < 10 {
			continue
		}
		ip, port, err := parseProcAddr(fields[1])
		if err != nil {
			continue
		}
		state, _ := strconv.ParseInt(fields[3], 16, 32)
		uid, _ := strconv.Atoi(fields[7])
		inode, _ := strconv.ParseUint(fields[9], 10, 64)
		rows = append(rows, tcpRow{ip: ip, port: port, state: int(state), uid: uid, inode: inode})
	}
	return rows, sc.Err()
}

func parseProcAddr(hexAddr string) (string, int, error) {
	parts := strings.Split(hexAddr, ":")
	if len(parts) != 2 {
		return "", 0, fmt.Errorf("addr")
	}
	port64, err := strconv.ParseUint(parts[1], 16, 16)
	if err != nil {
		return "", 0, err
	}
	ipHex := parts[0]
	if len(ipHex) == 8 {
		b, err := hex.DecodeString(ipHex)
		if err != nil || len(b) != 4 {
			return "", 0, fmt.Errorf("v4")
		}
		ip := fmt.Sprintf("%d.%d.%d.%d", b[3], b[2], b[1], b[0])
		return ip, int(port64), nil
	}
	if len(ipHex) == 32 {
		b, err := hex.DecodeString(ipHex)
		if err != nil || len(b) != 16 {
			return "", 0, fmt.Errorf("v6")
		}
		groups := make([]string, 8)
		for i := 0; i < 8; i++ {
			groups[i] = fmt.Sprintf("%02x%02x", b[i*2], b[i*2+1])
		}
		ip := strings.Join(groups, ":")
		if ip == "0000:0000:0000:0000:0000:0000:0000:0000" {
			ip = ":" + ":"
		}
		return ip, int(port64), nil
	}
	return "", 0, fmt.Errorf("len")
}

func listenAddrOK(string) bool {
	return true
}

func pidForSocketInode(root string, inode uint64, uid int) (int, bool) {
	_ = uid
	entries, err := os.ReadDir(root)
	if err != nil {
		return 0, false
	}
	needle := fmt.Sprintf("socket:[%d]", inode)
	for _, e := range entries {
		if !e.IsDir() || !isDigits(e.Name()) {
			continue
		}
		pid, err := strconv.Atoi(e.Name())
		if err != nil {
			continue
		}
		fdDir := filepath.Join(root, e.Name(), "fd")
		fds, err := os.ReadDir(fdDir)
		if err != nil {
			continue
		}
		for _, fd := range fds {
			target, err := os.Readlink(filepath.Join(fdDir, fd.Name()))
			if err != nil {
				continue
			}
			if target == needle {
				return pid, true
			}
		}
	}
	return 0, false
}

func processCommand(root string, pid int) string {
	b, err := os.ReadFile(filepath.Join(root, strconv.Itoa(pid), "cmdline"))
	if err == nil && len(b) > 0 {
		parts := bytes.Split(b, []byte{0})
		var args []string
		for _, p := range parts {
			if len(p) == 0 {
				continue
			}
			args = append(args, string(p))
		}
		if len(args) > 0 {
			return filepath.Base(args[0])
		}
	}
	comm, err := os.ReadFile(filepath.Join(root, strconv.Itoa(pid), "comm"))
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(comm))
}

func processCwd(root string, pid int) string {
	target, err := os.Readlink(filepath.Join(root, strconv.Itoa(pid), "cwd"))
	if err != nil {
		return ""
	}
	return target
}

func baseComm(name string) string {
	name = strings.TrimSpace(name)
	if name == "" {
		return ""
	}
	if i := strings.IndexByte(name, ' '); i >= 0 {
		name = name[:i]
	}
	return strings.TrimPrefix(filepath.Base(name), "(")
}

func isDigits(s string) bool {
	if s == "" {
		return false
	}
	for i := 0; i < len(s); i++ {
		if s[i] < '0' || s[i] > '9' {
			return false
		}
	}
	return true
}
