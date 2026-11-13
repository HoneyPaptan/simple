package opencode

import (
	"context"
	"strings"
	"sync"
	"time"

	"sh.simple/simpled/internal/inbox"
	"sh.simple/simpled/internal/wire"
)

type Permission struct {
	ID        string         `json:"id"`
	SessionID string         `json:"sessionID"`
	Action    string         `json:"action"`
	Resources []string       `json:"resources"`
	Metadata  map[string]any `json:"metadata"`
	Message   string         `json:"message"`
}

type PermissionAPI interface {
	Permissions(ctx context.Context, sessionID string) ([]Permission, error)
	ReplyPermission(ctx context.Context, sessionID, requestID, decision string) error
	SetRules(ctx context.Context, sessionID string, rules []Rule) error
}

type Rule struct {
	Action   string `json:"action"`
	Resource string `json:"resource"`
	Effect   string `json:"effect"`
}

var readOnlyActions = []string{"read", "list", "glob", "grep", "todoread", "todowrite", "lsp", "skill", "question"}

var editActions = []string{"edit", "write", "patch", "multiedit"}

func RulesFor(mode string) []Rule {
	if mode == "full" {
		return []Rule{{Action: "*", Resource: "*", Effect: "allow"}}
	}
	allow := readOnlyActions
	if mode == "auto" {
		allow = append(append([]string(nil), readOnlyActions...), editActions...)
	}
	rules := []Rule{{Action: "*", Resource: "*", Effect: "ask"}}
	for _, action := range allow {
		rules = append(rules, Rule{Action: action, Resource: "*", Effect: "allow"})
	}
	return rules
}

type InboxSink interface {
	Add(item wire.Item) error
	Resolve(id string)
}

type pendingPermission struct {
	agent   string
	session string
	request string
}

type Approvals struct {
	API    PermissionAPI
	Inbox  InboxSink
	Agents func() []wire.Agent
	Every  time.Duration

	mu      sync.Mutex
	byKey   map[string]string
	byItem  map[string]pendingPermission
	replied map[string]bool
	applied map[string]appliedRules
}

type appliedRules struct {
	mode string
	at   time.Time
}

const rulesRefresh = time.Minute

func (a *Approvals) init() {
	if a.byKey == nil {
		a.byKey = make(map[string]string)
		a.byItem = make(map[string]pendingPermission)
		a.replied = make(map[string]bool)
		a.applied = make(map[string]appliedRules)
	}
}

func (a *Approvals) Run(ctx context.Context) {
	every := a.Every
	if every <= 0 {
		every = time.Second
	}
	t := time.NewTicker(every)
	defer t.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-t.C:
			a.Poll(ctx)
		}
	}
}

func (a *Approvals) Poll(ctx context.Context) {
	for _, agent := range a.Agents() {
		if !openCodeAgent(agent) {
			continue
		}
		a.applyMode(ctx, *agent.Session, agentMode(agent))
		pctx, cancel := context.WithTimeout(ctx, 3*time.Second)
		perms, err := a.API.Permissions(pctx, *agent.Session)
		cancel()
		if err != nil {
			continue
		}
		a.sync(ctx, agent, perms)
	}
}

func (a *Approvals) applyMode(ctx context.Context, session, mode string) {
	a.mu.Lock()
	a.init()
	last := a.applied[session]
	done := last.mode == mode && time.Since(last.at) < rulesRefresh
	a.mu.Unlock()
	if done {
		return
	}
	rctx, cancel := context.WithTimeout(ctx, 3*time.Second)
	defer cancel()
	if a.API.SetRules(rctx, session, RulesFor(mode)) != nil {
		return
	}
	a.mu.Lock()
	a.applied[session] = appliedRules{mode: mode, at: time.Now()}
	a.mu.Unlock()
}

func permKey(session, request string) string {
	return session + "/" + request
}

func (a *Approvals) sync(ctx context.Context, agent wire.Agent, perms []Permission) {
	session := *agent.Session
	open := make(map[string]bool, len(perms))
	for _, p := range perms {
		key := permKey(session, p.ID)
		open[key] = true
		a.mu.Lock()
		a.init()
		_, tracked := a.byKey[key]
		replied := a.replied[key]
		a.mu.Unlock()
		if tracked || replied {
			continue
		}
		if autoAllowed(agentMode(agent)) {
			a.reply(ctx, key, session, p.ID, "once")
			continue
		}
		a.track(agent.ID, session, p)
	}
	a.mu.Lock()
	a.init()
	var gone []string
	for key, itemID := range a.byKey {
		if strings.HasPrefix(key, session+"/") && !open[key] {
			gone = append(gone, itemID)
			delete(a.byKey, key)
			delete(a.byItem, itemID)
		}
	}
	for key := range a.replied {
		if strings.HasPrefix(key, session+"/") && !open[key] {
			delete(a.replied, key)
		}
	}
	a.mu.Unlock()
	for _, itemID := range gone {
		a.Inbox.Resolve(itemID)
	}
}

func (a *Approvals) track(agentID, session string, p Permission) {
	itemID, err := inbox.NewItemID()
	if err != nil {
		return
	}
	cmd := strings.Join(p.Resources, "\n")
	item := wire.Item{
		ID: itemID, Agent: agentID, Kind: "approval", Title: PermissionTitle(p),
		CreatedMs: inbox.NowMs(),
	}
	if cmd != "" {
		item.Command = &cmd
	}
	a.mu.Lock()
	a.init()
	a.byKey[permKey(session, p.ID)] = itemID
	a.byItem[itemID] = pendingPermission{agent: agentID, session: session, request: p.ID}
	a.mu.Unlock()
	_ = a.Inbox.Add(item)
}

func (a *Approvals) reply(ctx context.Context, key, session, request, decision string) {
	rctx, cancel := context.WithTimeout(ctx, 3*time.Second)
	defer cancel()
	if a.API.ReplyPermission(rctx, session, request, decision) != nil {
		return
	}
	a.mu.Lock()
	a.init()
	a.replied[key] = true
	a.mu.Unlock()
}

func (a *Approvals) Answer(ctx context.Context, itemID, decision string) bool {
	a.mu.Lock()
	a.init()
	p, ok := a.byItem[itemID]
	if ok {
		delete(a.byItem, itemID)
		delete(a.byKey, permKey(p.session, p.request))
	}
	a.mu.Unlock()
	if !ok {
		return false
	}
	a.reply(ctx, permKey(p.session, p.request), p.session, p.request, ReplyFor(decision))
	return true
}

func ReplyFor(decision string) string {
	switch decision {
	case "allow":
		return "once"
	case "always":
		return "always"
	default:
		return "reject"
	}
}

func agentMode(agent wire.Agent) string {
	if agent.Mode == nil {
		return "ask"
	}
	return *agent.Mode
}

func autoAllowed(mode string) bool {
	return mode == "full"
}

func PermissionTitle(p Permission) string {
	first := ""
	if len(p.Resources) > 0 {
		first = p.Resources[0]
	}
	switch p.Action {
	case "bash":
		if first != "" {
			return "Run " + first
		}
		return "Run a command"
	case "edit", "write":
		if first != "" {
			return "Edit " + first
		}
		return "Edit files"
	case "webfetch":
		if first != "" {
			return "Fetch " + first
		}
		return "Fetch a page"
	case "external_directory":
		if first != "" {
			return "Use " + first
		}
		return "Use a folder outside the project"
	}
	if p.Message != "" {
		return p.Message
	}
	if first != "" {
		return p.Action + " " + first
	}
	return p.Action
}
