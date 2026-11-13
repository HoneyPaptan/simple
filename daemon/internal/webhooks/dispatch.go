package webhooks

import (
	"bytes"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"strings"
	"time"

	"sh.simple/simpled/internal/config"
	"sh.simple/simpled/internal/wire"
)

const timeout = 5 * time.Second

type Dispatcher struct {
	Store    *config.Store
	HostName func() string
	Agent    func(id string) (wire.Agent, bool)
}

type Event struct {
	Event     string  `json:"event"`
	Host      string  `json:"host"`
	Agent     string  `json:"agent"`
	Workspace string  `json:"workspace"`
	Title     string  `json:"title"`
	Body      string  `json:"body,omitempty"`
	CreatedMs int64   `json:"created_ms"`
}

func (d *Dispatcher) NotifyInboxAdd(item wire.Item) {
	if d == nil || d.Store == nil {
		return
	}
	cfg, err := d.Store.LoadConfig()
	if err != nil {
		return
	}
	host := d.HostName()
	workspace := ""
	if d.Agent != nil {
		if a, ok := d.Agent(item.Agent); ok {
			workspace = a.Workspace
			if workspace == "" {
				workspace = a.Cwd
			}
		}
	}
	body := ""
	if item.Body != nil {
		body = *item.Body
	}
	ev := Event{
		Event:     item.Kind,
		Host:      host,
		Agent:     item.Agent,
		Workspace: workspace,
		Title:     item.Title,
		Body:      body,
		CreatedMs: item.CreatedMs,
	}
	payload, _ := json.Marshal(ev)
	for _, hook := range cfg.Webhooks {
		if !kindMatches(hook.Kinds, item.Kind) {
			continue
		}
		if !ValidURL(hook.URL) {
			continue
		}
		go postHook(hook.URL, hook.Secret, payload)
	}
}

func (d *Dispatcher) SendSample(hook config.Webhook) error {
	if !ValidURL(hook.URL) {
		return errors.New("bad url")
	}
	ev := Event{
		Event:     "webhook.test",
		Host:      d.HostName(),
		Agent:     "t0",
		Workspace: "/tmp",
		Title:     "Sample event",
		Body:      "This is a test webhook from simpled.",
		CreatedMs: time.Now().UnixMilli(),
	}
	payload, _ := json.Marshal(ev)
	return postHookSync(hook.URL, hook.Secret, payload)
}

func kindMatches(kinds []string, kind string) bool {
	for _, k := range kinds {
		if k == kind {
			return true
		}
	}
	return false
}

func ValidURL(raw string) bool {
	u, err := url.Parse(raw)
	if err != nil || u.Host == "" {
		return false
	}
	if u.Scheme == "https" {
		return true
	}
	if u.Scheme != "http" {
		return false
	}
	host := strings.ToLower(u.Hostname())
	return host == "127.0.0.1" || host == "localhost"
}

func postHook(url, secret string, body []byte) {
	_ = postHookSync(url, secret, body)
}

func postHookSync(url, secret string, body []byte) error {
	req, err := http.NewRequest(http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Simple-Signature", signature(secret, body))
	client := &http.Client{Timeout: timeout, CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
	resp, err := client.Do(req)
	if err != nil {
		var nerr net.Error
		if errors.As(err, &nerr) && nerr.Timeout() {
			return ErrTimeout
		}
		return ErrUnreachable
	}
	io.Copy(io.Discard, resp.Body)
	resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		return StatusError{Code: resp.StatusCode}
	}
	return nil
}

var (
	ErrUnreachable = errors.New("webhook unreachable")
	ErrTimeout     = errors.New("webhook timeout")
)

type StatusError struct{ Code int }

func (e StatusError) Error() string { return fmt.Sprintf("webhook answered %d", e.Code) }

func signature(secret string, body []byte) string {
	m := hmac.New(sha256.New, []byte(secret))
	m.Write(body)
	return "sha256=" + hex.EncodeToString(m.Sum(nil))
}

func NewSecret() (string, error) {
	var b [32]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", err
	}
	return hex.EncodeToString(b[:]), nil
}

func NewID() (string, error) {
	return config.RandomID("w_")
}
