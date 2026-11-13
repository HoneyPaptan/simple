package main

import (
	"fmt"
	"os"
	"runtime"

	"sh.simple/simpled/internal/config"
	"sh.simple/simpled/internal/control"
	"sh.simple/simpled/internal/install"
)

func printHelp(w *os.File) {
	fmt.Fprintln(w, "Usage: simpled <command>")
	fmt.Fprintln(w)
	fmt.Fprintln(w, "Commands:")
	fmt.Fprintln(w, "  serve                Run the daemon (the installed service does this)")
	fmt.Fprintln(w, "  pair                 Show a pairing QR code and link")
	fmt.Fprintln(w, "  devices              List paired phones")
	fmt.Fprintln(w, "  devices rm <id>      Revoke a paired phone")
	fmt.Fprintln(w, "  install              Install the user service and agent hooks")
	fmt.Fprintln(w, "  uninstall            Remove the user service and agent hooks")
	fmt.Fprintln(w, "  status               Show whether the user service is running")
	fmt.Fprintln(w, "  hooks install        Install agent hook scripts")
	fmt.Fprintln(w, "  hooks uninstall      Remove agent hook scripts")
	fmt.Fprintln(w, "  hook                 Agent hook entry (internal)")
	fmt.Fprintln(w, "  version              Print version")
}

func noPairAddrMsg(configPath string) string {
	return "Your phone has no way to reach this computer yet.\n" +
		"Install Tailscale on this computer and on your phone (https://tailscale.com/download), sign both in to the same account, then run simpled pair again.\n" +
		"To pair over your home network instead, set \"lan\": true in " + configPath + ", run simpled install to restart it, then run simpled pair again."
}

func startHint() string {
	switch runtime.GOOS {
	case "windows":
		return "schtasks /Run /TN simpled"
	case "darwin":
		return "launchctl kickstart gui/$(id -u)/sh.simple.simpled"
	default:
		return "systemctl --user start simpled"
	}
}

func notRunningMsg() string {
	return "simpled is not running. Start it with: " + startHint() + " (or run simpled install once)"
}

func exitNotRunning() {
	fmt.Fprintln(os.Stderr, notRunningMsg())
	os.Exit(1)
}

func runStatus() {
	store := config.NewStore("", "")
	if !control.Alive(store.ControlSock) {
		fmt.Println(notRunningMsg())
		os.Exit(1)
	}
	fmt.Println("simpled is running")
	if !install.LingerEnabled() {
		fmt.Println("Tip: run loginctl enable-linger $USER so simpled keeps running after you log out.")
	}
}
