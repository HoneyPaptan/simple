package herdr

import (
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"os"
	"sync/atomic"
	"time"

	"sh.simple/simpled/internal/hcsock"
)

type Status int

const (
	StatusDown Status = iota
	StatusUp
)

type AgentSession struct {
	Agent  string `json:"agent"`
	Kind   string `json:"kind"`
	Source string `json:"source"`
	Value  string `json:"value"`
}

type AgentInfo struct {
	Agent                 string        `json:"agent"`
	AgentStatus           string        `json:"agent_status"`
	InteractiveReady      bool          `json:"interactive_ready"`
	AgentSession          *AgentSession `json:"agent_session"`
	Cwd                   string        `json:"cwd"`
	ForegroundCwd         string        `json:"foreground_cwd"`
	Name                  string        `json:"name"`
	PaneID                string        `json:"pane_id"`
	Revision              uint64        `json:"revision"`
	StateChangeSeq        uint64        `json:"state_change_seq"`
	TabID                 string        `json:"tab_id"`
	TerminalID            string        `json:"terminal_id"`
	TerminalTitleStripped string        `json:"terminal_title_stripped"`
	WorkspaceID           string        `json:"workspace_id"`
}

type AgentListResult struct {
	Type   string      `json:"type"`
	Agents []AgentInfo `json:"agents"`
}

type PaneRead struct {
	Text      string `json:"text"`
	Truncated bool   `json:"truncated"`
}

type AgentReadResult struct {
	Type string   `json:"type"`
	Read PaneRead `json:"read"`
}

type PingResult struct {
	Type     string `json:"type"`
	Version  string `json:"version"`
	Protocol int    `json:"protocol"`
}

type Client struct {
	path   string
	seq    atomic.Int64
	status atomic.Int32

	onStatus func(Status)
}

func DefaultSocketPath() string {
	return hcsock.HerdrSocket()
}

func NewClient(path string) *Client {
	if path == "" {
		path = DefaultSocketPath()
	}
	return &Client{path: path}
}

func (c *Client) SetStatusHandler(fn func(Status)) {
	c.onStatus = fn
}

func (c *Client) Status() Status {
	return Status(c.status.Load())
}

func (c *Client) Available() bool {
	if hcsock.IsNamedPipe(c.path) {
		ctx, cancel := context.WithTimeout(context.Background(), 50*time.Millisecond)
		defer cancel()
		conn, err := hcsock.Dial(ctx, c.path)
		if err != nil {
			return false
		}
		conn.Close()
		return true
	}
	_, err := os.Stat(c.path)
	return err == nil
}

func (c *Client) Version(ctx context.Context) *string {
	if c.Status() != StatusUp {
		return nil
	}
	var out PingResult
	if err := c.Call(ctx, "ping", map[string]any{}, &out); err != nil {
		return nil
	}
	if out.Version == "" {
		return nil
	}
	v := out.Version
	return &v
}

func (c *Client) dial(ctx context.Context) (net.Conn, error) {
	return hcsock.Dial(ctx, c.path)
}

func (c *Client) setStatus(s Status) {
	if Status(c.status.Swap(int32(s))) == s {
		return
	}
	if c.onStatus != nil {
		c.onStatus(s)
	}
}

type apiError struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

func (e *apiError) Error() string {
	if e.Message != "" {
		return e.Message
	}
	return e.Code
}

const callTimeout = 10 * time.Second

func callDeadline(ctx context.Context) time.Time {
	deadline := time.Now().Add(callTimeout)
	if d, ok := ctx.Deadline(); ok && d.Before(deadline) {
		return d
	}
	return deadline
}

func (c *Client) callOn(ctx context.Context, conn net.Conn, method string, params any, out any) error {
	if err := conn.SetDeadline(callDeadline(ctx)); err == nil {
		defer conn.SetDeadline(time.Time{})
	}
	rid := fmt.Sprintf("simpled-%d", c.seq.Add(1))
	if params == nil {
		params = map[string]any{}
	}
	req := map[string]any{
		"id":     rid,
		"method": method,
		"params": params,
	}
	line, err := json.Marshal(req)
	if err != nil {
		return err
	}
	line = append(line, '\n')
	if _, err := conn.Write(line); err != nil {
		return err
	}
	br := bufio.NewReader(conn)
	respLine, err := br.ReadBytes('\n')
	if err != nil {
		return err
	}
	var resp struct {
		ID     string          `json:"id"`
		Result json.RawMessage `json:"result"`
		Error  *apiError       `json:"error"`
	}
	if err := json.Unmarshal(respLine, &resp); err != nil {
		return err
	}
	if resp.Error != nil {
		return resp.Error
	}
	if out != nil && len(resp.Result) > 0 {
		return json.Unmarshal(resp.Result, out)
	}
	return nil
}

func (c *Client) Call(ctx context.Context, method string, params any, out any) error {
	conn, err := c.dial(ctx)
	if err != nil {
		c.setStatus(StatusDown)
		return err
	}
	defer conn.Close()
	err = c.callOn(ctx, conn, method, params, out)
	var apiErr *apiError
	if err == nil || errors.As(err, &apiErr) {
		c.setStatus(StatusUp)
	} else {
		c.setStatus(StatusDown)
	}
	return err
}

func (c *Client) OneShotCall(ctx context.Context, method string, params any, out any) error {
	conn, err := c.dial(ctx)
	if err != nil {
		return err
	}
	defer conn.Close()
	return c.callOn(ctx, conn, method, params, out)
}

func (c *Client) AgentList(ctx context.Context) ([]AgentInfo, error) {
	var res AgentListResult
	if err := c.Call(ctx, "agent.list", map[string]any{}, &res); err != nil {
		return nil, err
	}
	return res.Agents, nil
}

func (c *Client) AgentRead(ctx context.Context, target, source string, lines *int) (string, error) {
	params := map[string]any{
		"target":     target,
		"source":     source,
		"format":     "text",
		"strip_ansi": true,
	}
	if lines != nil {
		params["lines"] = *lines
	}
	var res AgentReadResult
	if err := c.Call(ctx, "agent.read", params, &res); err != nil {
		return "", err
	}
	return res.Read.Text, nil
}

func (c *Client) AgentPrompt(ctx context.Context, target, text string) error {
	return c.Call(ctx, "agent.prompt", map[string]any{
		"target": target,
		"text":   text,
	}, nil)
}

func (c *Client) AgentSendKeys(ctx context.Context, target string, keys []string) error {
	return c.Call(ctx, "agent.send_keys", map[string]any{
		"target": target,
		"keys":   keys,
	}, nil)
}

func (c *Client) Interrupt(ctx context.Context, target string) error {
	return c.AgentSendKeys(ctx, target, []string{"ctrl+c"})
}

var ErrUnavailable = errors.New("herdr unavailable")

func IsConnErr(err error) bool {
	return errors.Is(err, io.EOF) || errors.Is(err, net.ErrClosed)
}
