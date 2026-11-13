package wire

type Message struct {
	ID     string   `json:"id"`
	Role   string   `json:"role"`
	Ts     int64    `json:"ts"`
	Text   *string  `json:"text,omitempty"`
	Tool   *Tool    `json:"tool,omitempty"`
	Images []string `json:"images,omitempty"`
}

type Tool struct {
	Name    string  `json:"name"`
	Summary string  `json:"summary"`
	Input   *string `json:"input,omitempty"`
	Output  *string `json:"output,omitempty"`
	Status  string  `json:"status"`
	Diff    *string `json:"diff,omitempty"`
}

type TranscriptAppend struct {
	ID       string    `json:"id"`
	Messages []Message `json:"messages"`
}

type ScreenEvent struct {
	ID   string `json:"id"`
	Text string `json:"text"`
	Rows int    `json:"rows"`
	Cols int    `json:"cols"`
}

type AgentStartParams struct {
	Kind     string          `json:"kind"`
	Runtime  string          `json:"runtime,omitempty"`
	Cwd      string          `json:"cwd"`
	Prompt   *string         `json:"prompt,omitempty"`
	Model    *string         `json:"model,omitempty"`
	Effort   *string         `json:"effort,omitempty"`
	Mode     string          `json:"mode"`
	Worktree *WorktreeParams `json:"worktree,omitempty"`
}

type WorktreeParams struct {
	Branch string `json:"branch"`
}

type AgentModelsResult struct {
	Models []ModelInfo `json:"models"`
	Modes  []string    `json:"modes"`
}

type ModelInfo struct {
	ID      string   `json:"id"`
	Label   string   `json:"label"`
	Efforts []string `json:"efforts"`
}

type AgentKindsResult struct {
	Kinds map[string]bool `json:"kinds"`
}

type InboxListResult struct {
	Items []Item `json:"items"`
}

type InboxAnswerParams struct {
	ID       string  `json:"id"`
	Decision string  `json:"decision"`
	Option   *string `json:"option,omitempty"`
	Text     *string `json:"text,omitempty"`
}

type InboxReadParams struct {
	IDs []string `json:"ids"`
}

type AgentTranscriptParams struct {
	ID     string  `json:"id"`
	Before *string `json:"before,omitempty"`
	Limit  *int    `json:"limit,omitempty"`
}

type AgentTranscriptResult struct {
	Messages []Message `json:"messages"`
	Before   *string   `json:"before,omitempty"`
	Since    int64     `json:"since,omitempty"`
}

type AgentWatchParams struct {
	ID     string `json:"id"`
	Screen bool   `json:"screen"`
}

type AgentConfigureParams struct {
	ID     string  `json:"id"`
	Model  *string `json:"model,omitempty"`
	Effort *string `json:"effort,omitempty"`
	Mode   *string `json:"mode,omitempty"`
}

type FileEntry struct {
	Name    string `json:"name"`
	Dir     bool   `json:"dir"`
	Size    int64  `json:"size"`
	MtimeMs int64  `json:"mtime_ms"`
}

type FilesListParams struct {
	Path string `json:"path"`
}

type FilesListResult struct {
	Entries []FileEntry `json:"entries"`
}

type FilesReadParams struct {
	Path   string `json:"path"`
	Offset *int   `json:"offset,omitempty"`
	Limit  *int   `json:"limit,omitempty"`
}

type FilesReadResult struct {
	Text      string `json:"text"`
	Lines     int    `json:"lines"`
	Truncated bool   `json:"truncated"`
}

type FilesWriteParams struct {
	Path string `json:"path"`
	Text string `json:"text"`
}

type FilesMkdirParams struct {
	Path string `json:"path"`
	Name string `json:"name"`
}

type FilesUploadParams struct {
	Workspace string `json:"workspace"`
	Ext       string `json:"ext"`
}

type FilesUploadResult struct {
	Path string `json:"path"`
}

type AgentCommand struct {
	Name        string  `json:"name"`
	Description string  `json:"description"`
	Hint        *string `json:"hint,omitempty"`
}

type AgentCommandsParams struct {
	ID string `json:"id"`
}

type AgentCommandsResult struct {
	Commands []AgentCommand `json:"commands"`
}

type FilesSearchParams struct {
	Cwd   string `json:"cwd"`
	Query string `json:"query"`
	Limit *int   `json:"limit,omitempty"`
}

type FilesSearchResult struct {
	Paths []string `json:"paths"`
}

type GitStatusParams struct {
	Cwd string `json:"cwd"`
}

type GitFileStatus struct {
	Path   string `json:"path"`
	Status string `json:"status"`
	Add    int    `json:"add"`
	Del    int    `json:"del"`
}

type GitStatusResult struct {
	Branch   string          `json:"branch"`
	Ahead    int             `json:"ahead"`
	Behind   int             `json:"behind"`
	Upstream bool            `json:"upstream,omitempty"`
	Files    []GitFileStatus `json:"files"`
}

type GitDiffParams struct {
	Cwd  string  `json:"cwd"`
	Path *string `json:"path,omitempty"`
}

type GitDiffResult struct {
	Diff string `json:"diff"`
}

type GitCommitParams struct {
	Cwd     string `json:"cwd"`
	Message string `json:"message"`
	Push    bool   `json:"push"`
}

type GitCommitResult struct {
	Commit string `json:"commit"`
}

type UsageBucket struct {
	Input      int64    `json:"input"`
	Output     int64    `json:"output"`
	CacheRead  int64    `json:"cache_read"`
	CacheWrite int64    `json:"cache_write"`
	CostUSD    *float64 `json:"cost_usd,omitempty"`
}

type UsageDayRow struct {
	Day string `json:"day"`
	UsageBucket
}

type UsageAgentRow struct {
	ID      string      `json:"id"`
	Kind    string      `json:"kind"`
	Title   string      `json:"title"`
	HasData bool        `json:"has_data"`
	Total   UsageBucket `json:"total"`
}

type UsageSummaryParams struct {
	Days *int `json:"days,omitempty"`
}

type UsageSummaryResult struct {
	Days   int             `json:"days"`
	Today  UsageBucket     `json:"today"`
	Period UsageBucket     `json:"period"`
	Daily  []UsageDayRow   `json:"daily"`
	Agents []UsageAgentRow `json:"agents"`
}

type ServerEntry struct {
	Port    int    `json:"port"`
	PID     int    `json:"pid"`
	Command string `json:"command"`
	Cwd     string `json:"cwd"`
}

type ServersListResult struct {
	Servers []ServerEntry `json:"servers"`
}

type ServersKillParams struct {
	PID int `json:"pid"`
}

type PreviewOpenParams struct {
	Port int `json:"port"`
}

type PreviewOpenResult struct {
	Path      string `json:"path"`
	ExpiresMs int64  `json:"expires_ms"`
	Session   string `json:"session"`
}

type WebhookView struct {
	ID    string   `json:"id"`
	URL   string   `json:"url"`
	Kinds []string `json:"kinds"`
}

type WebhooksListResult struct {
	Webhooks []WebhookView `json:"webhooks"`
}

type WebhooksSetParams struct {
	ID    *string  `json:"id,omitempty"`
	URL   string   `json:"url"`
	Kinds []string `json:"kinds"`
}

type WebhooksSetResult struct {
	Webhook WebhookView `json:"webhook"`
	Secret  string      `json:"secret"`
}

type WebhooksRemoveParams struct {
	ID string `json:"id"`
}

type WebhooksTestParams struct {
	ID string `json:"id"`
}

type FontEntry struct {
	Path   string `json:"path"`
	Family string `json:"family"`
}

type FontsListResult struct {
	Fonts []FontEntry `json:"fonts"`
}

type FontsGetParams struct {
	Path string `json:"path"`
}

type TermOpenParams struct {
	ID   string `json:"id"`
	Cols int    `json:"cols"`
	Rows int    `json:"rows"`
}

type TermOpenResult struct {
	Sid string `json:"sid"`
}

type TermResizeParams struct {
	Sid  string `json:"sid"`
	Cols int    `json:"cols"`
	Rows int    `json:"rows"`
}

type TermCloseParams struct {
	Sid string `json:"sid"`
}

type TermSnapParams struct {
	Sid string `json:"sid"`
}

type TermFrame struct {
	T    string `json:"t"`
	B    string `json:"b,omitempty"`
	Done bool   `json:"done,omitempty"`
	Full bool   `json:"full,omitempty"`
}
