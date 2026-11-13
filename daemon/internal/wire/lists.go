package wire

import "encoding/json"

func orEmpty[T any](s []T) []T {
	if s == nil {
		return []T{}
	}
	return s
}

func (s Snapshot) MarshalJSON() ([]byte, error) {
	type plain Snapshot
	p := plain(s)
	p.Agents, p.Inbox, p.Workspaces = orEmpty(s.Agents), orEmpty(s.Inbox), orEmpty(s.Workspaces)
	return json.Marshal(p)
}

func (r AgentsListResult) MarshalJSON() ([]byte, error) {
	type plain AgentsListResult
	p := plain(r)
	p.Agents = orEmpty(r.Agents)
	return json.Marshal(p)
}

func (r WorkspacesListResult) MarshalJSON() ([]byte, error) {
	type plain WorkspacesListResult
	p := plain(r)
	p.Workspaces = orEmpty(r.Workspaces)
	return json.Marshal(p)
}

func (r InboxListResult) MarshalJSON() ([]byte, error) {
	type plain InboxListResult
	p := plain(r)
	p.Items = orEmpty(r.Items)
	return json.Marshal(p)
}

func (r FilesListResult) MarshalJSON() ([]byte, error) {
	type plain FilesListResult
	p := plain(r)
	p.Entries = orEmpty(r.Entries)
	return json.Marshal(p)
}

func (r GitStatusResult) MarshalJSON() ([]byte, error) {
	type plain GitStatusResult
	p := plain(r)
	p.Files = orEmpty(r.Files)
	return json.Marshal(p)
}

func (r UsageSummaryResult) MarshalJSON() ([]byte, error) {
	type plain UsageSummaryResult
	p := plain(r)
	p.Daily = orEmpty(r.Daily)
	p.Agents = orEmpty(r.Agents)
	return json.Marshal(p)
}

func (r ServersListResult) MarshalJSON() ([]byte, error) {
	type plain ServersListResult
	p := plain(r)
	p.Servers = orEmpty(r.Servers)
	return json.Marshal(p)
}

func (r FontsListResult) MarshalJSON() ([]byte, error) {
	type plain FontsListResult
	p := plain(r)
	p.Fonts = orEmpty(r.Fonts)
	return json.Marshal(p)
}

func (r WebhooksListResult) MarshalJSON() ([]byte, error) {
	type plain WebhooksListResult
	p := plain(r)
	p.Webhooks = orEmpty(r.Webhooks)
	return json.Marshal(p)
}

func (r PanesListResult) MarshalJSON() ([]byte, error) {
	type plain PanesListResult
	p := plain(r)
	p.Tabs = orEmpty(r.Tabs)
	for i := range p.Tabs {
		p.Tabs[i].Panes = orEmpty(p.Tabs[i].Panes)
	}
	return json.Marshal(p)
}

func (r PanesPeekResult) MarshalJSON() ([]byte, error) {
	type plain PanesPeekResult
	p := plain(r)
	p.Peeks = orEmpty(r.Peeks)
	return json.Marshal(p)
}
