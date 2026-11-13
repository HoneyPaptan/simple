package hc

import (
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"os"
	"sync"
	"sync/atomic"
	"time"

	"sh.simple/simpled/internal/hcsock"
)

const hcVersion = "0.1.17"

type Status int

const (
	StatusDown Status = iota
	StatusUp
)

type Terminal struct {
	Address    string            `json:"address"`
	Argv       []string          `json:"argv"`
	Cwd        string            `json:"cwd"`
	CreatedMs  uint64            `json:"created_ms"`
	ExitCode   *int32            `json:"exit_code"`
	FinishedMs *uint64           `json:"finished_ms"`
	Labels     map[string]string `json:"labels"`
	Reason     string            `json:"reason"`
	State      string            `json:"state"`
	Status     *StatusRecord     `json:"status"`
	StatusMs   *uint64           `json:"status_ms"`
	Title      string            `json:"title"`
	Foreground *Foreground       `json:"foreground"`
}

type StatusRecord struct {
	State string  `json:"state"`
	Kind  *string `json:"kind"`
	Msg   string  `json:"msg"`
	Title string  `json:"title"`
}

type Foreground struct {
	Argv []string `json:"argv"`
	Cwd  string   `json:"cwd"`
	Name string   `json:"name"`
}

type SubscribeResult struct {
	Seq       uint64     `json:"seq"`
	Terminals []Terminal `json:"terminals"`
}

type Event struct {
	Seq      uint64          `json:"seq"`
	Terminal string          `json:"terminal"`
	Type     string          `json:"type"`
	Data     json.RawMessage `json:"data"`
}

type Client struct {
	path string

	mu     sync.Mutex
	callMu sync.Mutex
	conn   net.Conn
	id     int64
	status atomic.Int32

	onStatus func(Status)

	starter   func(context.Context) error
	lastStart time.Time
}

func DefaultSocketPath() string {
	return hcsock.Resolve()
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

func (c *Client) SetStarter(fn func(context.Context) error) {
	c.starter = fn
}

func (c *Client) tryStart(ctx context.Context, now time.Time) {
	if c.starter == nil || now.Sub(c.lastStart) < startRetry {
		return
	}
	c.lastStart = now
	sctx, cancel := context.WithTimeout(ctx, 20*time.Second)
	defer cancel()
	_ = c.starter(sctx)
}

const startRetry = time.Minute

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

func (c *Client) Version() *string {
	if c.Status() != StatusUp {
		return nil
	}
	v := hcVersion
	return &v
}

func (c *Client) dial(ctx context.Context) (net.Conn, error) {
	return hcsock.Dial(ctx, c.path)
}

func (c *Client) setStatus(s Status) {
	c.status.Store(int32(s))
	if c.onStatus != nil {
		c.onStatus(s)
	}
}

func (c *Client) connect(ctx context.Context) error {
	conn, err := c.dial(ctx)
	if err != nil {
		return err
	}
	c.mu.Lock()
	if c.conn != nil {
		c.conn.Close()
	}
	c.conn = conn
	c.mu.Unlock()
	var helloResult json.RawMessage
	if err := c.callOn(ctx, conn, "hello", map[string]any{"generation": 1}, &helloResult); err != nil {
		conn.Close()
		return err
	}
	return nil
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
	c.mu.Lock()
	c.id++
	rid := c.id
	c.mu.Unlock()
	req := map[string]any{
		"jsonrpc": "2.0",
		"id":      rid,
		"method":  method,
		"params":  params,
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
		ID     int64           `json:"id"`
		Result json.RawMessage `json:"result"`
		Error  *rpcError       `json:"error"`
	}
	if err := json.Unmarshal(respLine, &resp); err != nil {
		return err
	}
	if resp.ID != rid {
		return fmt.Errorf("hc: response id %d for request %d", resp.ID, rid)
	}
	if resp.Error != nil {
		return resp.Error
	}
	if out != nil && len(resp.Result) > 0 {
		return json.Unmarshal(resp.Result, out)
	}
	return nil
}

type rpcError struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
}

func (e *rpcError) Error() string {
	return e.Message
}

func (c *Client) Call(ctx context.Context, method string, params any, out any) error {
	c.callMu.Lock()
	defer c.callMu.Unlock()
	c.mu.Lock()
	conn := c.conn
	c.mu.Unlock()
	if conn == nil {
		if err := c.connect(ctx); err != nil {
			return err
		}
		c.mu.Lock()
		conn = c.conn
		c.mu.Unlock()
	}
	err := c.callOn(ctx, conn, method, params, out)
	if err != nil {
		c.mu.Lock()
		if c.conn != nil {
			c.conn.Close()
			c.conn = nil
		}
		c.mu.Unlock()
	}
	return err
}

func (c *Client) OneShotCall(ctx context.Context, method string, params any, out any) error {
	conn, err := c.dial(ctx)
	if err != nil {
		return err
	}
	defer conn.Close()
	if err := c.callOn(ctx, conn, "hello", map[string]any{"generation": 1}, nil); err != nil {
		return err
	}
	return c.callOn(ctx, conn, method, params, out)
}

func (c *Client) Subscribe(ctx context.Context) (SubscribeResult, <-chan Event, error) {
	conn, err := c.dial(ctx)
	if err != nil {
		return SubscribeResult{}, nil, err
	}
	if err := c.callOn(ctx, conn, "hello", map[string]any{"generation": 1}, nil); err != nil {
		conn.Close()
		return SubscribeResult{}, nil, err
	}
	var sub SubscribeResult
	if err := c.callOn(ctx, conn, "events.subscribe", map[string]any{}, &sub); err != nil {
		conn.Close()
		return SubscribeResult{}, nil, err
	}
	ch := make(chan Event, 32)
	go func() {
		defer close(ch)
		defer conn.Close()
		br := bufio.NewReader(conn)
		for {
			select {
			case <-ctx.Done():
				return
			default:
			}
			line, err := br.ReadBytes('\n')
			if err != nil {
				return
			}
			var note struct {
				Method string `json:"method"`
				Params Event  `json:"params"`
			}
			if err := json.Unmarshal(line, &note); err != nil {
				continue
			}
			if note.Method != "event" {
				continue
			}
			select {
			case ch <- note.Params:
			case <-ctx.Done():
				return
			}
		}
	}()
	c.setStatus(StatusUp)
	return sub, ch, nil
}

func (c *Client) MaintainSubscribe(ctx context.Context, snap func(SubscribeResult), onEvent func(Event)) {
	backoff := 200 * time.Millisecond
	for {
		select {
		case <-ctx.Done():
			return
		default:
		}
		sub, ch, err := c.Subscribe(ctx)
		if err != nil {
			c.setStatus(StatusDown)
			c.tryStart(ctx, time.Now())
			select {
			case <-ctx.Done():
				return
			case <-time.After(backoff):
			}
			if backoff < 5*time.Second {
				backoff *= 2
				if backoff > 5*time.Second {
					backoff = 5 * time.Second
				}
			}
			continue
		}
		backoff = 200 * time.Millisecond
		if snap != nil {
			snap(sub)
		}
		for {
			select {
			case <-ctx.Done():
				return
			case ev, ok := <-ch:
				if !ok {
					c.setStatus(StatusDown)
					goto reconnect
				}
				if onEvent != nil {
					onEvent(ev)
				}
			}
		}
	reconnect:
	}
}

func MergeTerminal(t *Terminal, patch json.RawMessage) error {
	if t == nil {
		return errors.New("nil terminal")
	}
	b, err := json.Marshal(t)
	if err != nil {
		return err
	}
	var m map[string]json.RawMessage
	if err := json.Unmarshal(b, &m); err != nil {
		return err
	}
	var p map[string]json.RawMessage
	if err := json.Unmarshal(patch, &p); err != nil {
		return err
	}
	for k, v := range p {
		m[k] = v
	}
	merged, err := json.Marshal(m)
	if err != nil {
		return err
	}
	return json.Unmarshal(merged, t)
}

func ReadAll(ctx context.Context, c *Client) ([]Terminal, error) {
	var res struct {
		Terminals []Terminal `json:"terminals"`
	}
	if err := c.Call(ctx, "terminal.list", map[string]any{"all": true}, &res); err != nil {
		return nil, err
	}
	return res.Terminals, nil
}

var ErrUnavailable = errors.New("hc unavailable")

func IsConnErr(err error) bool {
	return errors.Is(err, io.EOF) || errors.Is(err, net.ErrClosed)
}
