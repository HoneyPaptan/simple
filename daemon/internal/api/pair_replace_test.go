package api_test

import (
	"bytes"
	"context"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/hex"
	"encoding/json"
	"io"
	"net"
	"net/http"
	"path/filepath"
	"testing"
	"time"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/api"
	"sh.simple/simpled/internal/config"
	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/hub"
	"sh.simple/simpled/internal/pair"
	"sh.simple/simpled/internal/wire"
)

func TestPairReplaceToken(t *testing.T) {
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
	agentStore := agents.NewStore(func(ev string, data any) {
		h.Publish(ev, data)
	})
	hcClient := hc.NewClient(filepath.Join(rt, "missing-hc.sock"))
	srv := api.New(api.Options{
		Store: store, Hub: h, Agents: agentStore, HC: hcClient,
		Pair: pm, HostID: hostID, Linger: true,
	})
	if err := srv.Start(ctx, cfg); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		cancel()
		shCtx, c := context.WithTimeout(context.Background(), 2*time.Second)
		defer c()
		srv.Shutdown(shCtx)
	})
	time.Sleep(100 * time.Millisecond)

	pin, err := certFingerprint(store)
	if err != nil {
		t.Fatal(err)
	}
	client := pairHTTPClient(pin)
	addr := net.JoinHostPort("127.0.0.1", itoa(port))

	name1, tok1 := pairOnce(t, client, addr, pm, "Pixel", "A")
	list, _ := store.LoadDevices()
	if len(list) != 1 {
		t.Fatalf("devices %d", len(list))
	}
	if list[0].Name != name1 {
		t.Fatalf("name %q", list[0].Name)
	}

	hash := tokenHash(tok1)
	code2, _ := pm.NewCode()
	name2 := pairWithReplace(t, client, addr, code2, "Other", "B", hash)
	list, _ = store.LoadDevices()
	if len(list) != 1 {
		t.Fatalf("after replace devices %d", len(list))
	}
	if list[0].Name != name1 {
		t.Fatalf("kept name %q want %q", list[0].Name, name1)
	}
	if name2 == tok1 {
		t.Fatal("expected new token")
	}

	code3, _ := pm.NewCode()
	unk := "0000000000000000000000000000000000000000000000000000000000000000"
	pairWithReplace(t, client, addr, code3, "X", "Y", unk)
	list, _ = store.LoadDevices()
	if len(list) != 2 {
		t.Fatalf("unknown replace devices %d", len(list))
	}
}

func tokenHash(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}

func pairHTTPClient(pin string) *http.Client {
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
	return &http.Client{
		Transport: &http.Transport{TLSClientConfig: tlsClient},
		Timeout:   10 * time.Second,
	}
}

func pairOnce(t *testing.T, client *http.Client, addr string, pm *pair.Manager, name, model string) (string, string) {
	t.Helper()
	code, _ := pm.NewCode()
	tok := pairWithReplace(t, client, addr, code, name, model, "")
	return name, tok
}

func pairWithReplace(t *testing.T, client *http.Client, addr, code, name, model, replace string) string {
	t.Helper()
	body := wire.PairRequest{
		Code:   code,
		Device: wire.PairDevice{Name: name, Model: model},
	}
	if replace != "" {
		body.ReplaceToken = &replace
	}
	b, _ := json.Marshal(body)
	req, _ := http.NewRequest(http.MethodPost, "https://"+addr+"/v1/pair", bytes.NewReader(b))
	resp, err := client.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		raw, _ := io.ReadAll(resp.Body)
		t.Fatalf("pair %d %s", resp.StatusCode, raw)
	}
	var out wire.PairResponse
	if err := json.NewDecoder(resp.Body).Decode(&out); err != nil {
		t.Fatal(err)
	}
	return out.Token
}
