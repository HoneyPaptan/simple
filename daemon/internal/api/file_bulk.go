package api

import (
	"net/http"
	"os"
	"path/filepath"
	"strings"

	"sh.simple/simpled/internal/files"
	"sh.simple/simpled/internal/fonts"
)

func (s *Server) handleFile(w http.ResponseWriter, r *http.Request) {
	token := bearerToken(r)
	if token == "" {
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return
	}
	dev, err := s.Store.FindDeviceByToken(token)
	if err != nil || dev == nil {
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return
	}
	_ = s.Store.TouchDevice(dev.ID)
	path := r.URL.Query().Get("path")
	if path == "" {
		http.Error(w, "bad request", http.StatusBadRequest)
		return
	}
	switch r.Method {
	case http.MethodGet:
		s.serveFileGet(w, r, path)
	case http.MethodPut:
		s.serveFilePut(w, r, path)
	default:
		http.Error(w, "method", http.StatusMethodNotAllowed)
	}
}

func (s *Server) serveFileGet(w http.ResponseWriter, r *http.Request, path string) {
	s.mu.Lock()
	cache := s.fontCache
	s.mu.Unlock()
	if fonts.Allowed(cache, path) {
		servePath(w, r, path, "font/ttf")
		return
	}
	dir := filepath.Dir(path)
	if _, ok := files.AttachmentWorkspaceRoot(dir); ok {
		servePath(w, r, path, contentTypeForPath(path))
		return
	}
	http.Error(w, "not found", http.StatusNotFound)
}

func (s *Server) serveFilePut(w http.ResponseWriter, r *http.Request, path string) {
	ct := r.Header.Get("Content-Type")
	if i := strings.Index(ct, ";"); i >= 0 {
		ct = ct[:i]
	}
	if err := files.PutAttachment(path, ct, r.Body); err != nil {
		kind := files.ErrKind(err)
		switch kind {
		case "bad_request", "too_large":
			http.Error(w, kind, http.StatusBadRequest)
		case "denied":
			http.Error(w, kind, http.StatusForbidden)
		case "not_found":
			http.Error(w, kind, http.StatusNotFound)
		default:
			http.Error(w, "internal", http.StatusInternalServerError)
		}
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func servePath(w http.ResponseWriter, r *http.Request, path, defaultType string) {
	f, err := os.Open(path)
	if err != nil {
		http.Error(w, "not found", http.StatusNotFound)
		return
	}
	defer f.Close()
	st, err := f.Stat()
	if err != nil || st.IsDir() {
		http.Error(w, "not found", http.StatusNotFound)
		return
	}
	w.Header().Set("Content-Type", contentTypeForPath(path))
	if defaultType == "font/ttf" {
		if ext := filepath.Ext(path); ext == ".otf" {
			w.Header().Set("Content-Type", "font/otf")
		}
	}
	http.ServeContent(w, r, filepath.Base(path), st.ModTime(), f)
}

func contentTypeForPath(path string) string {
	switch strings.ToLower(filepath.Ext(path)) {
	case ".jpg", ".jpeg":
		return "image/jpeg"
	case ".png":
		return "image/png"
	case ".webp":
		return "image/webp"
	case ".gif":
		return "image/gif"
	default:
		return "application/octet-stream"
	}
}
