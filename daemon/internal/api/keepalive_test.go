package api

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/coder/websocket"
)

func keepAlivePair(t *testing.T) (*websocket.Conn, chan error) {
	t.Helper()
	ended := make(chan error, 1)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		c, err := websocket.Accept(w, r, nil)
		if err != nil {
			return
		}
		sess := newSession(&Server{}, c, "dev")
		ctx, cancel := context.WithCancel(r.Context())
		go sess.keepAlive(ctx, cancel, 50*time.Millisecond, 100*time.Millisecond)
		_, _, err = c.Read(ctx)
		ended <- err
	}))
	t.Cleanup(srv.Close)
	c, _, err := websocket.Dial(context.Background(), "ws"+strings.TrimPrefix(srv.URL, "http"), nil)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { c.CloseNow() })
	return c, ended
}

func TestKeepAliveKeepsQuietClientThatAnswersPings(t *testing.T) {
	c, ended := keepAlivePair(t)
	ctx, cancel := context.WithTimeout(context.Background(), 600*time.Millisecond)
	defer cancel()
	go func() { _, _, _ = c.Read(ctx) }()
	select {
	case err := <-ended:
		t.Fatalf("session closed while the client answered pings: %v", err)
	case <-ctx.Done():
	}
}

func TestKeepAliveDropsClientThatNeverAnswers(t *testing.T) {
	_, ended := keepAlivePair(t)
	select {
	case <-ended:
	case <-time.After(2 * time.Second):
		t.Fatal("session stayed open without pongs")
	}
}
