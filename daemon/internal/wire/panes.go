package wire

type PaneRect struct {
	X      int `json:"x"`
	Y      int `json:"y"`
	Width  int `json:"width"`
	Height int `json:"height"`
}

type Pane struct {
	ID        string    `json:"id"`
	Runtime   string    `json:"runtime"`
	Workspace string    `json:"workspace"`
	Tab       *string   `json:"tab,omitempty"`
	Title     string    `json:"title"`
	Kind      string    `json:"kind"`
	Status    string    `json:"status"`
	Rect      *PaneRect `json:"rect,omitempty"`
}

type PaneTab struct {
	ID    string `json:"id"`
	Label string `json:"label"`
	Panes []Pane `json:"panes"`
}

type PanesListParams struct {
	Workspace string `json:"workspace"`
}

type PanesListResult struct {
	Tabs []PaneTab `json:"tabs"`
}

type PanesPeekParams struct {
	IDs   []string `json:"ids"`
	Lines *int     `json:"lines,omitempty"`
}

type PanePeek struct {
	ID   string `json:"id"`
	Text string `json:"text"`
}

type PanesPeekResult struct {
	Peeks []PanePeek `json:"peeks"`
}

type PanesSpawnParams struct {
	Workspace string `json:"workspace"`
}

type PanesSpawnResult struct {
	ID string `json:"id"`
}
