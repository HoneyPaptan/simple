package servers

import (
	"crypto/rand"
	"encoding/hex"
	"net/http"
	"net/http/httputil"
	"net/url"
	"sync"
	"time"
)

const previewTTL = 10 * time.Minute

type PreviewGrant struct {
	Port      int
	ExpiresMs int64
	Session   string
}

type PreviewStore struct {
	mu     sync.Mutex
	grants map[string]PreviewGrant
}

func NewPreviewStore() *PreviewStore {
	return &PreviewStore{grants: make(map[string]PreviewGrant)}
}

func (s *PreviewStore) Open(port int) (path string, expiresMs int64, session string) {
	token := randomToken()
	session = randomToken()
	expiresMs = time.Now().Add(previewTTL).UnixMilli()
	s.mu.Lock()
	s.grants[token] = PreviewGrant{Port: port, ExpiresMs: expiresMs, Session: session}
	s.mu.Unlock()
	return "/v1/preview/" + token + "/", expiresMs, session
}

func (s *PreviewStore) Lookup(token string) (PreviewGrant, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	g, ok := s.grants[token]
	if !ok {
		return PreviewGrant{}, false
	}
	if time.Now().UnixMilli() > g.ExpiresMs {
		delete(s.grants, token)
		return PreviewGrant{}, false
	}
	return g, true
}

func ParsePreviewPathForTest(path string) (token, rest string, ok bool) {
	return parsePreviewPath(path)
}

func (s *PreviewStore) ExpireTokenForTest(token string, expiresMs int64) {
	s.mu.Lock()
	defer s.mu.Unlock()
	g, ok := s.grants[token]
	if !ok {
		return
	}
	g.ExpiresMs = expiresMs
	s.grants[token] = g
}

func (s *PreviewStore) lookupSession(session string) (PreviewGrant, bool) {
	s.mu.Lock()
	var token string
	for t, g := range s.grants {
		if g.Session == session {
			token = t
			break
		}
	}
	s.mu.Unlock()
	if token == "" {
		return PreviewGrant{}, false
	}
	return s.Lookup(token)
}

func sessionCookie(r *http.Request) string {
	c, err := r.Cookie(previewCookie)
	if err != nil {
		return ""
	}
	return c.Value
}

func (s *PreviewStore) Handler() http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		token, rest, ok := parsePreviewPath(r.URL.Path)
		if !ok {
			http.NotFound(w, r)
			return
		}
		grant, ok := s.Lookup(token)
		if !ok {
			http.NotFound(w, r)
			return
		}
		if sessionCookie(r) != grant.Session {
			http.NotFound(w, r)
			return
		}
		proxy := previewProxy(grant.Port, rest)
		proxy.ServeHTTP(w, r)
	})
}

const previewCookie = "simple_preview"

func (s *PreviewStore) RootHandler() http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		session := sessionCookie(r)
		if session == "" {
			http.NotFound(w, r)
			return
		}
		grant, ok := s.lookupSession(session)
		if !ok {
			http.NotFound(w, r)
			return
		}
		previewProxy(grant.Port, r.URL.Path).ServeHTTP(w, r)
	})
}

func previewProxy(port int, rest string) *httputil.ReverseProxy {
	target := &url.URL{Scheme: "http", Host: "127.0.0.1:" + itoa(port)}
	return &httputil.ReverseProxy{
		Rewrite: func(pr *httputil.ProxyRequest) {
			pr.SetURL(target)
			pr.Out.URL.Path = rest
			pr.Out.URL.RawPath = ""
			pr.Out.Host = target.Host
			pr.Out.Header.Del("Authorization")
			dropCookie(pr.Out.Header, previewCookie)
		},
		ErrorHandler: func(w http.ResponseWriter, r *http.Request, err error) {
			http.Error(w, "preview unavailable", http.StatusBadGateway)
		},
	}
}

func parsePreviewPath(path string) (token, rest string, ok bool) {
	const prefix = "/v1/preview/"
	if len(path) <= len(prefix) || path[:len(prefix)] != prefix {
		return "", "", false
	}
	restPath := path[len(prefix):]
	slash := -1
	for i, c := range restPath {
		if c == '/' {
			slash = i
			break
		}
	}
	if slash < 0 {
		token = restPath
		rest = "/"
		ok = token != ""
		return
	}
	token = restPath[:slash]
	rest = restPath[slash:]
	if rest == "" {
		rest = "/"
	}
	ok = token != ""
	return
}

func randomToken() string {
	var b [24]byte
	_, _ = rand.Read(b[:])
	return hex.EncodeToString(b[:])
}

func itoa(n int) string {
	if n == 0 {
		return "0"
	}
	var buf [12]byte
	i := len(buf)
	for n > 0 {
		i--
		buf[i] = byte('0' + n%10)
		n /= 10
	}
	return string(buf[i:])
}

func dropCookie(h http.Header, name string) {
	cookies := (&http.Request{Header: http.Header{"Cookie": h.Values("Cookie")}}).Cookies()
	h.Del("Cookie")
	for _, c := range cookies {
		if c.Name != name {
			h.Add("Cookie", c.Name+"="+c.Value)
		}
	}
}
