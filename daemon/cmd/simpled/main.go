package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"log"
	"os"
	"os/signal"
	"path/filepath"
	"sh.simple/simpled/internal/opencode"
	"strings"
	"sync"
	"syscall"
	"time"

	"rsc.io/qr"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/api"
	"sh.simple/simpled/internal/config"
	"sh.simple/simpled/internal/control"
	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/herdr"
	"sh.simple/simpled/internal/hooks"
	"sh.simple/simpled/internal/hub"
	"sh.simple/simpled/internal/inbox"
	"sh.simple/simpled/internal/install"
	"sh.simple/simpled/internal/pair"
	"sh.simple/simpled/internal/servers"
	"sh.simple/simpled/internal/transcript"
	"sh.simple/simpled/internal/usage"
	"sh.simple/simpled/internal/webhooks"
	"sh.simple/simpled/internal/wire"
)

var version = "0.1.0"

func init() {
	config.Version = version
}

func main() {
	log.SetFlags(0)
	if len(os.Args) < 2 {
		printHelp(os.Stdout)
		return
	}
	switch os.Args[1] {
	case "serve":
		runServe()
	case "pair":
		runPair()
	case "devices":
		runDevices()
	case "install":
		runInstall()
	case "uninstall":
		runUninstall()
	case "status":
		runStatus()
	case "hooks":
		runHooks()
	case "hook":
		runHook()
	case "version":
		fmt.Println(version)
	case "help", "-h", "--help":
		printHelp(os.Stdout)
	default:
		fmt.Fprintf(os.Stderr, "unknown command %q\n", os.Args[1])
		printHelp(os.Stderr)
		os.Exit(2)
	}
}

func runServe() {
	store := config.NewStore("", "")
	if control.Alive(store.ControlSock) {
		fmt.Fprintln(os.Stderr, "simpled is already running. Check it with: simpled status")
		os.Exit(1)
	}
	if err := store.EnsureDirs(); err != nil {
		log.Fatal(err)
	}
	cfg, err := store.LoadConfig()
	if err != nil {
		log.Fatal(err)
	}
	hostID, err := store.HostID()
	if err != nil {
		log.Fatal(err)
	}
	epoch, err := config.RandomID("e_")
	if err != nil {
		log.Fatal(err)
	}
	h := hub.New(epoch)
	pm := pair.NewManager()
	inboxStore := inbox.New(store, func(ev string, data any) {
		h.Publish(ev, data)
	})
	agentStore := agents.NewStore(func(ev string, data any) {
		h.Publish(ev, data)
		if gone, ok := data.(wire.AgentRemoved); ok {
			inboxStore.ResolveForAgent(gone.ID)
		}
	})
	simpledPath, _ := install.SelfPath()
	transcriptMgr := transcript.NewManager(func(ev string, data any) {
		h.Publish(ev, data)
	})
	transcriptMgr.SetAgentLookup(agentStore.Get)
	transcriptMgr.SetOpenCodeObserver(agentStore.ObserveOpenCodeTurn, agentStore.SendFailureText)
	transcriptMgr.PersistBindings(filepath.Join(store.StateDir, "transcript-bindings.json"))
	agentStore.PersistHerdr(filepath.Join(store.StateDir, "herdr-agents.json"))
	transcriptMgr.SetContextSync(agentStore.SetAgentContext, agentStore.PollableAgentIDs)
	transcriptMgr.SetModelSync(agentStore.SetAgentModel)
	transcriptMgr.SetPromptTitleSync(agentStore.SetAgentPromptTitle)
	transcriptMgr.SetCursorLifecycle(
		func(id, st string) { agentStore.SetAgentFields(id, st, nil) },
		func(id, body string) {
			itemID, ierr := inbox.NewItemID()
			if ierr != nil {
				return
			}
			if body == "" {
				body = "Finished"
			}
			b := body
			_ = inboxStore.Add(wire.Item{ID: itemID, Agent: id, Kind: "done", Title: "Finished", Body: &b, CreatedMs: inbox.NowMs()})
		},
	)
	hookDaemon := hooks.New(inboxStore, agentStore, transcriptMgr, simpledPath)
	hcClient := hc.NewClient(store.HCSock)
	hcClient.SetStarter(startHCServer)
	herdrClient := herdr.NewClient(store.HerdrSocket())
	linger := install.LingerEnabled()
	screens := api.NewScreenWatcher(hcClient, herdrClient, func(ev string, data any) {
		h.Publish(ev, data)
	}, api.NewPromptTracker(agentStore, inboxStore), agentStore)
	agentStore.SetPollSync(screens.SyncPollers)
	wh := &webhooks.Dispatcher{
		Store:    store,
		HostName: store.HostName,
		Agent:    agentStore.Get,
	}
	inboxStore.SetNotify(wh.NotifyInboxAdd)
	openCode := opencode.NewClient()
	approvals := &opencode.Approvals{API: openCode, Inbox: inboxStore, Agents: agentStore.Snapshot}
	srv := api.New(api.Options{
		Store: store, Hub: h, Agents: agentStore, HC: hcClient, Herdr: herdrClient, Pair: pm,
		Inbox: inboxStore, Hooks: hookDaemon, Transcripts: transcriptMgr,
		Usage: usage.NewSummarizer(), Preview: servers.NewPreviewStore(), Webhooks: wh, ListenPort: cfg.Port,
		Screens: screens, SimpledPath: simpledPath, HostID: hostID, Linger: linger,
		OpenCode: openCode, Approvals: approvals,
	})
	agentStore.SetSendFailRelease(srv.ReleaseSend)
	ctx, cancel := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer cancel()
	ctl := control.New(store, pm, hookDaemon)
	if err := ctl.Listen(); err != nil {
		log.Fatal(err)
	}
	go ctl.Serve()
	go transcriptMgr.Run(ctx)
	go approvals.Run(ctx)
	turns := &opencode.Turns{
		API: openCode, Inbox: inboxStore, Agents: agentStore.Snapshot,
		SetStatus: func(id, status string) { agentStore.SetAgentFields(id, status, nil) },
		Observe:   agentStore.ObserveOpenCodeTurn,
	}
	go turns.Run(ctx)
	hcLoaded := newOnce()
	herdrLoaded := newOnce()
	go hcClient.MaintainSubscribe(ctx, func(sub hc.SubscribeResult) {
		agentStore.LoadSnapshot(sub.Terminals)
		hcLoaded.done()
		known := make(map[string]bool, len(sub.Terminals))
		for _, t := range sub.Terminals {
			known[t.Address] = true
		}
		for _, a := range agentStore.Snapshot() {
			if a.Runtime == "herdr" {
				known[a.ID] = true
			}
		}
		inboxStore.ResolveMissingAgents(known)
	}, func(ev hc.Event) {
		agentStore.HandleEvent(ev)
	})
	publishHost := func() {
		var hcVer, herdrVer *string
		if hcClient.Status() == hc.StatusUp {
			hcVer = hcClient.Version()
		}
		if herdrClient.Status() == herdr.StatusUp {
			herdrVer = herdrClient.Version(ctx)
		}
		agents.PublishHostUpdate(h, hcVer, herdrVer, linger)
	}
	hcClient.SetStatusHandler(func(st hc.Status) {
		publishHost()
	})
	herdrClient.SetStatusHandler(func(st herdr.Status) {
		publishHost()
	})
	herdrFeed := herdr.NewFeed(herdrClient, func(list []herdr.AgentInfo) {
		agentStore.LoadHerdrAgents(list)
		transcriptMgr.SyncHerdrAgents(agentStore)
		herdrLoaded.done()
	})
	go herdrFeed.Run(ctx)
	waitLoaded(startupLoadWait, hcLoaded.ch, herdrLoaded.ch)
	if err := srv.Start(ctx, cfg); err != nil {
		log.Fatal(err)
	}
	<-ctx.Done()
	shCtx, c := context.WithTimeout(context.Background(), 5*time.Second)
	defer c()
	srv.Shutdown(shCtx)
	ctl.Close()
}

func runHooks() {
	if len(os.Args) < 3 {
		fmt.Fprintf(os.Stderr, "usage: simpled hooks install|uninstall\n")
		os.Exit(2)
	}
	switch os.Args[2] {
	case "install":
		path, err := install.SelfPath()
		if err != nil {
			log.Fatal(err)
		}
		if err := install.InstallHooks(path); err != nil {
			log.Fatal(err)
		}
	case "uninstall":
		if err := install.UninstallHooks(); err != nil {
			log.Fatal(err)
		}
	default:
		fmt.Fprintf(os.Stderr, "usage: simpled hooks install|uninstall\n")
		os.Exit(2)
	}
}

func runHook() {
	if len(os.Args) < 4 {
		os.Exit(0)
	}
	kind := os.Args[2]
	event := os.Args[3]
	body, _ := io.ReadAll(os.Stdin)
	term := os.Getenv("HC_TERMINAL_ID")
	if term == "" {
		if cursorPermissionEvent(kind, event) {
			fmt.Println(`{"permission":"ask"}`)
		}
		os.Exit(0)
	}
	store := config.NewStore("", "")
	r, err := control.Request(store.ControlSock, "hook", map[string]any{
		"terminal": term,
		"kind":     kind,
		"event":    event,
		"body":     json.RawMessage(body),
	})
	if err != nil {
		if cursorPermissionEvent(kind, event) {
			fmt.Println(`{"permission":"ask"}`)
		}
		os.Exit(0)
	}
	if cursorPermissionEvent(kind, event) {
		if !validCursorPermission(r) {
			r = []byte(`{"permission":"ask"}`)
		}
		os.Stdout.Write(r)
		return
	}
	if len(r) > 0 {
		os.Stdout.Write(r)
	}
}

func validCursorPermission(r []byte) bool {
	var out struct {
		Permission string `json:"permission"`
	}
	if json.Unmarshal(r, &out) != nil {
		return false
	}
	return out.Permission == "allow" || out.Permission == "deny" || out.Permission == "ask"
}

func cursorPermissionEvent(kind, event string) bool {
	if kind != "cursor" {
		return false
	}
	return event == "beforeShellExecution" || event == "beforeMCPExecution"
}

func runInstall() {
	path, err := install.SelfPath()
	if err != nil {
		log.Fatal(err)
	}
	if err := install.Install(path); err != nil {
		log.Fatal(err)
	}
}

func runUninstall() {
	if err := install.Uninstall(); err != nil {
		log.Fatal(err)
	}
}

func runPair() {
	store := config.NewStore("", "")
	cfg, _ := store.LoadConfig()
	addrs, _ := api.PairAddrs(cfg.Port, cfg.Lan)
	if len(addrs) == 0 {
		fmt.Fprintln(os.Stderr, noPairAddrMsg(filepath.Join(store.ConfigDir, "config.json")))
		os.Exit(1)
	}
	fp, err := store.Fingerprint()
	if err != nil {
		fmt.Fprintln(os.Stderr, "cannot read certificate:", err)
		os.Exit(1)
	}
	r, err := control.Request(store.ControlSock, "pair.code", nil)
	if err != nil {
		exitNotRunning()
	}
	var out struct {
		Code string `json:"code"`
	}
	if json.Unmarshal(r, &out) != nil || out.Code == "" {
		fmt.Fprintln(os.Stderr, "pair.code failed")
		os.Exit(1)
	}
	uri, err := pairLink(store.HostName(), addrs, fp, out.Code)
	if err != nil {
		fmt.Fprintln(os.Stderr, "cannot read certificate:", err)
		os.Exit(1)
	}
	code, err := qr.Encode(uri, qr.M)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	writePairing(os.Stdout, qrLines(code.Size, code.Black), uri)
}

func runDevices() {
	store := config.NewStore("", "")
	jsonOut := false
	args := os.Args[2:]
	if len(args) > 0 && args[0] == "rm" {
		if len(args) < 2 {
			fmt.Fprintln(os.Stderr, "usage: simpled devices rm <id>")
			os.Exit(2)
		}
		id := args[1]
		r, err := control.Request(store.ControlSock, "devices.rm", map[string]string{"id": id})
		if err != nil {
			if err.Error() == "not_found" {
				fmt.Fprintf(os.Stderr, "No device with id %q.\n", id)
				os.Exit(1)
			}
			exitNotRunning()
		}
		var out struct {
			Name string `json:"name"`
		}
		if json.Unmarshal(r, &out) != nil || out.Name == "" {
			fmt.Fprintf(os.Stderr, "devices.rm failed\n")
			os.Exit(1)
		}
		fmt.Printf("Removed %s. That phone can no longer connect.\n", out.Name)
		return
	}
	i := 0
	for i < len(args) && strings.HasPrefix(args[i], "-") {
		if args[i] == "--json" {
			jsonOut = true
		} else if args[i] == "-h" || args[i] == "--help" {
			fmt.Fprintln(os.Stderr, "usage: simpled devices [--json]")
			os.Exit(0)
		} else {
			fmt.Fprintf(os.Stderr, "unknown flag %q\n", args[i])
			os.Exit(2)
		}
		i++
	}
	if i < len(args) {
		fmt.Fprintf(os.Stderr, "unknown argument %q\n", args[i])
		os.Exit(2)
	}
	r, err := control.Request(store.ControlSock, "devices.list", nil)
	if err != nil {
		exitNotRunning()
	}
	if jsonOut {
		fmt.Println(string(r))
		return
	}
	var list wire.DevicesListResult
	if json.Unmarshal(r, &list) != nil {
		fmt.Fprintln(os.Stderr, "devices.list failed")
		os.Exit(1)
	}
	printDevicesTable(list.Devices)
}

func init() {
	flag.CommandLine = flag.NewFlagSet(os.Args[0], flag.ExitOnError)
}

const startupLoadWait = 3 * time.Second

type once struct {
	ch chan struct{}
	o  sync.Once
}

func newOnce() *once { return &once{ch: make(chan struct{})} }

func (o *once) done() { o.o.Do(func() { close(o.ch) }) }

func waitLoaded(limit time.Duration, chans ...chan struct{}) {
	deadline := time.After(limit)
	for _, ch := range chans {
		select {
		case <-ch:
		case <-deadline:
			return
		}
	}
}
