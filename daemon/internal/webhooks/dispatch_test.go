package webhooks_test

import (
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"sh.simple/simpled/internal/config"
	"sh.simple/simpled/internal/webhooks"
)

func TestValidURLRules(t *testing.T) {
	if !webhooks.ValidURL("https://example.com/hook") {
		t.Fatal("https")
	}
	if !webhooks.ValidURL("http://127.0.0.1:9/h") {
		t.Fatal("loopback")
	}
	if webhooks.ValidURL("http://evil.com/h") {
		t.Fatal("http remote")
	}
}

func TestSignatureOnSample(t *testing.T) {
	var gotSig string
	var body []byte
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotSig = r.Header.Get("X-Simple-Signature")
		body, _ = io.ReadAll(r.Body)
		w.WriteHeader(http.StatusOK)
	}))
	defer srv.Close()
	d := &webhooks.Dispatcher{HostName: func() string { return "box" }}
	err := d.SendSample(config.Webhook{URL: srv.URL, Secret: "s3cret"})
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(gotSig, "sha256=") {
		t.Fatalf("sig %q", gotSig)
	}
	if len(body) == 0 {
		t.Fatal("body")
	}
}

func TestSendSampleReportsDeliveryFailures(t *testing.T) {
	d := &webhooks.Dispatcher{HostName: func() string { return "h" }}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusTeapot)
	}))
	defer srv.Close()
	var status webhooks.StatusError
	if err := d.SendSample(config.Webhook{URL: srv.URL}); !errors.As(err, &status) || status.Code != http.StatusTeapot {
		t.Fatalf("non 2xx: %v", err)
	}
	closed := httptest.NewServer(http.NotFoundHandler())
	url := closed.URL
	closed.Close()
	if err := d.SendSample(config.Webhook{URL: url}); !errors.Is(err, webhooks.ErrUnreachable) {
		t.Fatalf("closed port: %v", err)
	}
}
