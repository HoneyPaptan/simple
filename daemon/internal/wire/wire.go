package wire

import "encoding/json"

type HelloParams struct {
	App   string `json:"app,omitempty"`
	Epoch string `json:"epoch,omitempty"`
	Since *int64 `json:"since,omitempty"`
}

type HostInfo struct {
	ID      string  `json:"id"`
	Name    string  `json:"name"`
	Version string  `json:"version"`
	Epoch   string  `json:"epoch,omitempty"`
	HC      *string `json:"hc"`
	Herdr   *string `json:"herdr,omitempty"`
	Linger  bool    `json:"linger"`
}

type HelloResult struct {
	Host     HostInfo        `json:"host"`
	Seq      int64           `json:"seq"`
	Replay   bool            `json:"replay"`
	Snapshot *Snapshot       `json:"snapshot"`
}

type Snapshot struct {
	Agents     []Agent     `json:"agents"`
	Inbox      []Item      `json:"inbox"`
	Workspaces []Workspace `json:"workspaces"`
}

type AgentContext struct {
	Used   int `json:"used"`
	Window int `json:"window"`
}

type Agent struct {
	ID         string        `json:"id"`
	Runtime    string        `json:"runtime,omitempty"`
	Kind       string        `json:"kind"`
	Title      string        `json:"title"`
	Cwd        string        `json:"cwd"`
	Workspace  string        `json:"workspace"`
	Status     string        `json:"status"`
	Waiting    *string       `json:"waiting,omitempty"`
	Model      *string       `json:"model,omitempty"`
	Effort     *string       `json:"effort,omitempty"`
	Mode       *string       `json:"mode,omitempty"`
	StartedMs  int64         `json:"started_ms"`
	UpdatedMs  int64         `json:"updated_ms"`
	Pinned     bool          `json:"pinned"`
	Unread     bool          `json:"unread"`
	Branch     *string       `json:"branch,omitempty"`
	Session    *string       `json:"session,omitempty"`
	Context    *AgentContext `json:"context,omitempty"`
}

type Item struct {
	ID        string   `json:"id"`
	Agent     string   `json:"agent"`
	Kind      string   `json:"kind"`
	Title     string   `json:"title"`
	Body      *string  `json:"body,omitempty"`
	Command   *string  `json:"command,omitempty"`
	Tool      *string  `json:"tool,omitempty"`
	Options   []Option `json:"options,omitempty"`
	Detector  *string  `json:"detector,omitempty"`
	Link      *string  `json:"link,omitempty"`
	CreatedMs int64    `json:"created_ms"`
	Read      bool     `json:"read"`
	Resolved  bool     `json:"resolved"`
}

type Option struct {
	ID     string  `json:"id"`
	Label  string  `json:"label"`
	Detail *string `json:"detail,omitempty"`
}

type Workspace struct {
	Path      string  `json:"path"`
	Name      string  `json:"name"`
	Branch    *string `json:"branch,omitempty"`
	Dirty     bool    `json:"dirty"`
	Agents    int     `json:"agents"`
	Pinned    bool    `json:"pinned"`
	UpdatedMs int64   `json:"updated_ms"`
}

type ErrorBody struct {
	Kind string `json:"kind"`
	Msg  string `json:"msg,omitempty"`
}

type WSRequest struct {
	ID int64           `json:"id"`
	M  string          `json:"m"`
	P  json.RawMessage `json:"p,omitempty"`
}

type WSResult struct {
	ID int64           `json:"id"`
	R  json.RawMessage `json:"r"`
}

type WSError struct {
	ID int64     `json:"id"`
	E  ErrorBody `json:"e"`
}

type WSEvent struct {
	Ev  string          `json:"ev"`
	Seq int64           `json:"seq"`
	D   json.RawMessage `json:"d"`
}

type PairRequest struct {
	Code          string     `json:"code"`
	Device        PairDevice `json:"device"`
	ReplaceToken  *string    `json:"replace_token,omitempty"`
}

type PairDevice struct {
	Name  string `json:"name"`
	Model string `json:"model"`
}

type PairResponse struct {
	DeviceID string   `json:"device_id"`
	Token    string   `json:"token"`
	Host     HostInfo `json:"host"`
}

type PairError struct {
	Kind string `json:"kind"`
}

type AgentsListResult struct {
	Agents []Agent `json:"agents"`
}

type WorkspacesListResult struct {
	Workspaces []Workspace `json:"workspaces"`
}

type AgentRemoved struct {
	ID string `json:"id"`
}

type DevicesListResult struct {
	Devices []DeviceView `json:"devices"`
}

type DeviceView struct {
	ID       string `json:"id"`
	Name     string `json:"name"`
	Model    string `json:"model"`
	Created  int64  `json:"created"`
	LastSeen int64  `json:"last_seen"`
}
