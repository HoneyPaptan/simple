package opencode

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"os"
	"time"

	"sh.simple/simpled/internal/appdirs"
)

type service struct {
	URL      string `json:"url"`
	Password string `json:"password"`
}

type Client struct {
	statePath string
	http      *http.Client
}

func NewClient() *Client {
	return &Client{statePath: serviceStatePath(), http: &http.Client{Timeout: 5 * time.Second}}
}

func serviceStatePath() string {
	return appdirs.OpenCodeStatePath("")
}

func (c *Client) loadService() (service, error) {
	var s service
	raw, err := os.ReadFile(c.statePath)
	if err != nil {
		return s, err
	}
	if err := json.Unmarshal(raw, &s); err != nil {
		return s, err
	}
	if s.URL == "" {
		return s, errors.New("opencode service has no url")
	}
	return s, nil
}

type messagePage struct {
	Data   []Item `json:"data"`
	Cursor struct {
		Next *string `json:"next"`
	} `json:"cursor"`
}

const maxPages = 20

func (c *Client) Messages(ctx context.Context, sessionID string) ([]Item, error) {
	svc, err := c.loadService()
	if err != nil {
		return nil, err
	}
	var all []Item
	query := url.Values{"order": {"asc"}, "limit": {"200"}}
	for page := 0; page < maxPages; page++ {
		var p messagePage
		status, err := c.get(ctx, svc, "/api/session/"+url.PathEscape(sessionID)+"/message?"+query.Encode(), &p)
		if status == http.StatusNotFound {
			return nil, nil
		}
		if err != nil {
			return nil, err
		}
		all = append(all, p.Data...)
		if p.Cursor.Next == nil || *p.Cursor.Next == "" || len(p.Data) == 0 {
			break
		}
		query = url.Values{"cursor": {*p.Cursor.Next}, "limit": {"200"}}
	}
	return all, nil
}

func (c *Client) get(ctx context.Context, svc service, path string, out any) (int, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, svc.URL+path, nil)
	if err != nil {
		return 0, err
	}
	req.SetBasicAuth("opencode", svc.Password)
	res, err := c.http.Do(req)
	if err != nil {
		return 0, err
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		return res.StatusCode, fmt.Errorf("opencode %s: %s", path, res.Status)
	}
	return res.StatusCode, json.NewDecoder(res.Body).Decode(out)
}

func (c *Client) post(ctx context.Context, path string, body any) error {
	return c.send(ctx, http.MethodPost, path, body)
}

func (c *Client) send(ctx context.Context, method, path string, body any) error {
	svc, err := c.loadService()
	if err != nil {
		return err
	}
	var buf bytes.Buffer
	if body != nil {
		if err := json.NewEncoder(&buf).Encode(body); err != nil {
			return err
		}
	}
	req, err := http.NewRequestWithContext(ctx, method, svc.URL+path, &buf)
	if err != nil {
		return err
	}
	req.SetBasicAuth("opencode", svc.Password)
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	res, err := c.http.Do(req)
	if err != nil {
		return err
	}
	defer res.Body.Close()
	if res.StatusCode/100 != 2 {
		return fmt.Errorf("opencode %s: %s", path, res.Status)
	}
	return nil
}

func sessionPath(sessionID string) string {
	return "/api/session/" + url.PathEscape(sessionID)
}

func (c *Client) Permissions(ctx context.Context, sessionID string) ([]Permission, error) {
	svc, err := c.loadService()
	if err != nil {
		return nil, err
	}
	var out struct {
		Data []Permission `json:"data"`
	}
	status, err := c.get(ctx, svc, sessionPath(sessionID)+"/permission", &out)
	if status == http.StatusNotFound {
		return nil, nil
	}
	return out.Data, err
}

func (c *Client) ReplyPermission(ctx context.Context, sessionID, requestID, decision string) error {
	return c.post(ctx, sessionPath(sessionID)+"/permission/"+url.PathEscape(requestID)+"/reply", map[string]string{"decision": decision})
}

func (c *Client) SetRules(ctx context.Context, sessionID string, rules []Rule) error {
	return c.send(ctx, http.MethodPatch, sessionPath(sessionID), map[string][]Rule{"permissions": rules})
}

func (c *Client) ActiveSessions(ctx context.Context) (map[string]json.RawMessage, error) {
	svc, err := c.loadService()
	if err != nil {
		return nil, err
	}
	var out struct {
		Data map[string]json.RawMessage `json:"data"`
	}
	if _, err := c.get(ctx, svc, "/api/session/active", &out); err != nil {
		return nil, err
	}
	return out.Data, nil
}

func (c *Client) Prompt(ctx context.Context, sessionID, text string) error {
	return c.post(ctx, sessionPath(sessionID)+"/prompt", map[string]string{"text": text})
}

func (c *Client) Interrupt(ctx context.Context, sessionID string) error {
	return c.post(ctx, sessionPath(sessionID)+"/interrupt", nil)
}

type Command struct {
	Name        string `json:"name"`
	Description string `json:"description"`
}

func (c *Client) Commands(ctx context.Context) ([]Command, error) {
	svc, err := c.loadService()
	if err != nil {
		return nil, err
	}
	var out struct {
		Data []Command `json:"data"`
	}
	status, err := c.get(ctx, svc, "/api/command", &out)
	if status == http.StatusNotFound {
		return nil, nil
	}
	return out.Data, err
}

func (c *Client) Models(ctx context.Context) ([]Model, error) {
	svc, err := c.loadService()
	if err != nil {
		return nil, err
	}
	var out struct {
		Data []Model `json:"data"`
	}
	_, err = c.get(ctx, svc, "/api/model", &out)
	return out.Data, err
}

func (c *Client) ConnectedProviders(ctx context.Context) ([]string, error) {
	svc, err := c.loadService()
	if err != nil {
		return nil, err
	}
	var out struct {
		Connected []string `json:"connected"`
	}
	_, err = c.get(ctx, svc, "/api/provider", &out)
	return out.Connected, err
}

func (c *Client) SwitchModel(ctx context.Context, sessionID string, ref ModelRef) error {
	return c.post(ctx, sessionPath(sessionID)+"/model", map[string]ModelRef{"model": ref})
}

const sessionIDAlphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

func NewSessionID() string {
	return newSessionIDAt(time.Now())
}

func newSessionIDAt(now time.Time) string {
	stamp := ^(uint64(now.UnixMilli())*0x1000 + 1) & 0xffffffffffff
	var r [14]byte
	_, _ = rand.Read(r[:])
	tail := make([]byte, len(r))
	for i, b := range r {
		tail[i] = sessionIDAlphabet[int(b)%len(sessionIDAlphabet)]
	}
	return fmt.Sprintf("ses_%012x%s", stamp, tail)
}
