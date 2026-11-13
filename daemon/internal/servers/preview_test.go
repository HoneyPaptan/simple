package servers_test

import (
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"testing"
	"time"

	"sh.simple/simpled/internal/servers"
)

func TestPreviewTokenExpiry(t *testing.T) {
	store := servers.NewPreviewStore()
	path, exp, _ := store.Open(8080)
	if path == "" || exp <= time.Now().UnixMilli() {
		t.Fatalf("open %s %d", path, exp)
	}
	token, _, ok := servers.ParsePreviewPathForTest(path)
	if !ok {
		t.Fatal("path")
	}
	if _, ok := store.Lookup(token); !ok {
		t.Fatal("lookup")
	}
	store.ExpireTokenForTest(token, time.Now().Add(-time.Second).UnixMilli())
	if _, ok := store.Lookup(token); ok {
		t.Fatal("expired")
	}
}

func TestPreviewPortBinding(t *testing.T) {
	store := servers.NewPreviewStore()
	path, _, _ := store.Open(3000)
	token, _, _ := servers.ParsePreviewPathForTest(path)
	grant, ok := store.Lookup(token)
	if !ok || grant.Port != 3000 {
		t.Fatalf("%+v", grant)
	}
}

func upstream(t *testing.T, seen *http.Request) int {
	up := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		*seen = *r.Clone(r.Context())
		_, _ = w.Write([]byte("ok"))
	}))
	t.Cleanup(up.Close)
	port, _ := strconv.Atoi(up.URL[strings.LastIndex(up.URL, ":")+1:])
	return port
}

func withSession(req *http.Request, session string) *http.Request {
	req.AddCookie(&http.Cookie{Name: "simple_preview", Value: session})
	return req
}

func TestPreviewProxyPathAndCredentials(t *testing.T) {
	var seen http.Request
	store := servers.NewPreviewStore()
	path, _, session := store.Open(upstream(t, &seen))
	req := withSession(httptest.NewRequest("GET", path+"static/app.js?v=2", nil), session)
	req.Header.Set("Authorization", "Bearer secret")
	req.AddCookie(&http.Cookie{Name: "sid", Value: "1"})
	rec := httptest.NewRecorder()
	store.Handler().ServeHTTP(rec, req)
	if rec.Code != 200 || seen.URL.Path != "/static/app.js" || seen.URL.RawQuery != "v=2" || seen.Header.Get("Authorization") != "" || seen.Header.Get("Cookie") != "sid=1" {
		t.Fatalf("code %d path %q query %q auth %q cookie %q", rec.Code, seen.URL.Path, seen.URL.RawQuery, seen.Header.Get("Authorization"), seen.Header.Get("Cookie"))
	}
}

func TestPreviewNeedsSession(t *testing.T) {
	var seen http.Request
	store := servers.NewPreviewStore()
	path, _, session := store.Open(upstream(t, &seen))
	for name, req := range map[string]*http.Request{
		"no cookie":       httptest.NewRequest("GET", path, nil),
		"wrong cookie":    withSession(httptest.NewRequest("GET", path, nil), "nope"),
		"token as cookie": withSession(httptest.NewRequest("GET", path, nil), strings.Split(path, "/")[3]),
		"unknown token":   withSession(httptest.NewRequest("GET", "/v1/preview/nope/", nil), session),
	} {
		rec := httptest.NewRecorder()
		store.Handler().ServeHTTP(rec, req)
		if rec.Code != http.StatusNotFound {
			t.Fatalf("%s: %d", name, rec.Code)
		}
	}
}

func TestPreviewSessionRoutesAbsolutePaths(t *testing.T) {
	var seen http.Request
	store := servers.NewPreviewStore()
	_, _, session := store.Open(upstream(t, &seen))
	rec := httptest.NewRecorder()
	store.RootHandler().ServeHTTP(rec, withSession(httptest.NewRequest("GET", "/assets/app.js", nil), session))
	if rec.Code != 200 || seen.URL.Path != "/assets/app.js" {
		t.Fatalf("code %d path %q", rec.Code, seen.URL.Path)
	}
	none := httptest.NewRecorder()
	store.RootHandler().ServeHTTP(none, httptest.NewRequest("GET", "/assets/app.js", nil))
	if none.Code != http.StatusNotFound {
		t.Fatalf("no cookie %d", none.Code)
	}
}
