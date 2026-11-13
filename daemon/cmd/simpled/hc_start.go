package main

import (
	"context"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"sync/atomic"
	"time"
)

const (
	hcInstallSh  = "https://releases.herdr.dev/core/install.sh"
	hcInstallPs1 = "https://releases.herdr.dev/core/install.ps1"
	hcInstallMax = 5 * time.Minute
)

var (
	errHCInstalling = errors.New("herdr-core is being installed")
	hcInstallState  atomic.Int32
)

func startHCServer(ctx context.Context) error {
	path, err := findHC(exec.LookPath, fileExists, runtime.GOOS, os.Getenv)
	if err != nil {
		if hcInstallState.CompareAndSwap(0, 1) {
			go installHC()
		}
		if hcInstallState.Load() == 1 {
			return errHCInstalling
		}
		return err
	}
	cmd := exec.CommandContext(ctx, path, "server", "start")
	hideWindow(cmd)
	return cmd.Run()
}

func installHC() {
	defer hcInstallState.Store(2)
	ctx, cancel := context.WithTimeout(context.Background(), hcInstallMax)
	defer cancel()
	name, args := hcInstallCommand(runtime.GOOS)
	cmd := exec.CommandContext(ctx, name, args...)
	hideWindow(cmd)
	out, err := cmd.CombinedOutput()
	if err != nil {
		fmt.Fprintf(os.Stderr, "simpled: installing herdr-core failed: %v\n%s", err, out)
		return
	}
	fmt.Fprintln(os.Stderr, "simpled: installed herdr-core")
}

func hcInstallCommand(goos string) (string, []string) {
	if goos == "windows" {
		return "powershell.exe", []string{"-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", "irm " + hcInstallPs1 + " | iex"}
	}
	return "sh", []string{"-c", "curl -fsSL " + hcInstallSh + " | sh"}
}

func findHC(lookPath func(string) (string, error), exists func(string) bool, goos string, getenv func(string) string) (string, error) {
	if path, err := lookPath("hc"); err == nil {
		return path, nil
	}
	for _, path := range hcCandidates(goos, getenv) {
		if exists(path) {
			return path, nil
		}
	}
	return "", errors.New("herdr-core (hc) is not installed")
}

func hcCandidates(goos string, getenv func(string) string) []string {
	if goos == "windows" {
		if dir := getenv("LOCALAPPDATA"); dir != "" {
			return []string{filepath.Join(dir, "Programs", "herdr-core", "hc.exe")}
		}
		return nil
	}
	var out []string
	if dir := getenv("HC_INSTALL_DIR"); dir != "" {
		out = append(out, filepath.Join(dir, "hc"))
	}
	if home := getenv("HOME"); home != "" {
		out = append(out, filepath.Join(home, ".local", "bin", "hc"))
	}
	return out
}

func fileExists(path string) bool {
	st, err := os.Stat(path)
	return err == nil && !st.IsDir()
}
