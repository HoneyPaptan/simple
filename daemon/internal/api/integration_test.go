package api_test

import (
	"bufio"
	"bytes"
	"context"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/hex"
	"encoding/json"
	"encoding/pem"
	"io"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"sync"
	"testing"
	"time"

	"github.com/coder/websocket"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/api"
	"sh.simple/simpled/internal/config"
	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/hub"
	"sh.simple/simpled/internal/pair"
)

func TestIntegrationPairWSAgentUpdate(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	home := t.TempDir()
	rt := t.TempDir()
	store := config.NewStore(home, rt)
	if err := store.EnsureDirs(); err != nil {
		t.Fatal(err)
	}
	port := pickPort(t)
	cfg := config.Config{Port: port}
	hostID, err := store.HostID()
	if err != nil {
		t.Fatal(err)
	}
	epoch, _ := config.RandomID("e_")
	h := hub.New(epoch)
	pm := pair.NewManager()
	code, _ := pm.NewCode()
	agentStore := agents.NewStore(func(ev string, data any) {
		h.Publish(ev, data)
	})

	var mu sync.Mutex
	var notifyConn net.Conn
	hcSock := startFakeHCIntegration(t, &mu, &notifyConn)

	hcClient := hc.NewClient(hcSock)
	go hcClient.MaintainSubscribe(ctx, func(sub hc.SubscribeResult) {
		agentStore.LoadSnapshot(sub.Terminals)
	}, func(ev hc.Event) {
		agentStore.HandleEvent(ev)
	})

	srv := api.New(api.Options{
		Store: store, Hub: h, Agents: agentStore, HC: hcClient,
		Pair: pm, HostID: hostID, Linger: true,
	})
	if err := srv.Start(ctx, cfg); err != nil {
		t.Fatal(err)
	}
	time.Sleep(150 * time.Millisecond)

	pin, err := certFingerprint(store)
	if err != nil {
		t.Fatal(err)
	}
	tlsClient := &tls.Config{
		MinVersion:         tls.VersionTLS13,
		InsecureSkipVerify: true,
		VerifyConnection: func(cs tls.ConnectionState) error {
			if len(cs.PeerCertificates) == 0 {
				return x509.CertificateInvalidError{}
			}
			sum := sha256.Sum256(cs.PeerCertificates[0].Raw)
			if hex.EncodeToString(sum[:]) != pin {
				return x509.CertificateInvalidError{Reason: x509.NotAuthorizedToSign}
			}
			return nil
		},
	}
	httpClient := &http.Client{
		Transport: &http.Transport{TLSClientConfig: tlsClient},
		Timeout:   10 * time.Second,
	}
	addr := net.JoinHostPort("127.0.0.1", itoa(port))

	pairBody, _ := json.Marshal(map[string]any{
		"code":   code,
		"device": map[string]string{"name": "Test", "model": "unit"},
	})
	req, _ := http.NewRequest(http.MethodPost, "https://"+addr+"/v1/pair", bytes.NewReader(pairBody))
	resp, err := httpClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		b, _ := io.ReadAll(resp.Body)
		t.Fatalf("pair %d %s", resp.StatusCode, b)
	}
	var pairOut struct {
		Token string `json:"token"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&pairOut); err != nil {
		t.Fatal(err)
	}

	wsCtx := context.Background()
	c, respWS, err := websocket.Dial(wsCtx, "wss://"+addr+"/v1/ws", &websocket.DialOptions{
		HTTPHeader: http.Header{"Authorization": []string{"Bearer " + pairOut.Token}},
		HTTPClient: httpClient,
	})
	if err != nil {
		t.Fatal(err)
	}
	if respWS.StatusCode != http.StatusSwitchingProtocols {
		t.Fatal(respWS.Status)
	}
	defer c.Close(websocket.StatusNormalClosure, "")

	hello, _ := json.Marshal(map[string]any{"m": "hello", "p": map[string]any{"app": "0.1.0"}})
	if err := c.Write(wsCtx, websocket.MessageText, hello); err != nil {
		t.Fatal(err)
	}
	if _, _, err := c.Read(wsCtx); err != nil {
		t.Fatal(err)
	}

	listReq, _ := json.Marshal(map[string]any{"id": 1, "m": "agents.list", "p": map[string]any{}})
	if err := c.Write(wsCtx, websocket.MessageText, listReq); err != nil {
		t.Fatal(err)
	}
	_, listResp, err := c.Read(wsCtx)
	if err != nil {
		t.Fatal(err)
	}
	var listOut struct {
		ID int64 `json:"id"`
	}
	if err := json.Unmarshal(listResp, &listOut); err != nil || listOut.ID != 1 {
		t.Fatalf("list resp %s", listResp)
	}

	mu.Lock()
	nc := notifyConn
	mu.Unlock()
	if nc == nil {
		t.Fatal("no hc subscribe conn")
	}
	note := map[string]any{
		"jsonrpc": "2.0", "method": "event",
		"params": map[string]any{
			"seq": 2, "terminal": "t1", "type": "created",
			"data": map[string]any{
				"address": "t1", "argv": []string{"bash"}, "cwd": home,
				"created_ms": 1000, "state": "live", "labels": map[string]string{},
			},
		},
	}
	writeJSONLine(nc, note)

	deadline := time.Now().Add(3 * time.Second)
	for time.Now().Before(deadline) {
		_, msg, err := c.Read(wsCtx)
		if err != nil {
			t.Fatal(err)
		}
		var ev struct {
			Ev string `json:"ev"`
		}
		if json.Unmarshal(msg, &ev) == nil && ev.Ev == "agent.update" {
			return
		}
	}
	t.Fatal("no agent.update")
}

func pickPort(t *testing.T) int {
	t.Helper()
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	p := ln.Addr().(*net.TCPAddr).Port
	ln.Close()
	return p
}

func certFingerprint(store *config.Store) (string, error) {
	certPath, _ := store.CertPaths()
	b, err := os.ReadFile(certPath)
	if err != nil {
		return "", err
	}
	block, _ := pem.Decode(b)
	if block == nil {
		return "", x509.CertificateInvalidError{}
	}
	sum := sha256.Sum256(block.Bytes)
	return hex.EncodeToString(sum[:]), nil
}

func itoa(n int) string {
	if n == 0 {
		return "0"
	}
	var b [12]byte
	i := len(b)
	for n > 0 {
		i--
		b[i] = byte('0' + n%10)
		n /= 10
	}
	return string(b[i:])
}

func startFakeHCIntegration(t *testing.T, mu *sync.Mutex, notifyConn *net.Conn) string {
	dir := t.TempDir()
	sock := filepath.Join(dir, "hc.sock")
	ln, err := net.Listen("unix", sock)
	if err != nil {
		t.Fatal(err)
	}
	go func() {
		for {
			c, err := ln.Accept()
			if err != nil {
				return
			}
			go handleFakeHCConn(c, mu, notifyConn)
		}
	}()
	t.Cleanup(func() { ln.Close() })
	return sock
}

func handleFakeHCConn(c net.Conn, mu *sync.Mutex, notifyConn *net.Conn) {
	defer c.Close()
	br := bufio.NewReader(c)
	subscribed := false
	for {
		line, err := br.ReadBytes('\n')
		if err != nil {
			return
		}
		var req struct {
			ID     int64  `json:"id"`
			Method string `json:"method"`
		}
		if json.Unmarshal(line, &req) != nil {
			return
		}
		switch req.Method {
		case "hello":
			writeJSONLine(c, map[string]any{"id": req.ID, "jsonrpc": "2.0", "result": map[string]any{"generation": 1}})
		case "events.subscribe":
			writeJSONLine(c, map[string]any{
				"id": req.ID, "jsonrpc": "2.0",
				"result": map[string]any{"seq": 1, "channel": nil, "terminals": []any{}},
			})
			mu.Lock()
			*notifyConn = c
			mu.Unlock()
			subscribed = true
		default:
			writeJSONLine(c, map[string]any{"id": req.ID, "jsonrpc": "2.0", "result": map[string]any{}})
		}
		if subscribed {
			// keep connection open for notifications
			continue
		}
	}
}

func writeJSONLine(c net.Conn, v any) {
	b, _ := json.Marshal(v)
	b = append(b, '\n')
	_, _ = c.Write(b)
}
