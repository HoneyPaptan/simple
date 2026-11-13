package control

import (
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"net"
	"os"
	"sync"
	"time"

	"sh.simple/simpled/internal/config"
	"sh.simple/simpled/internal/hooks"
	"sh.simple/simpled/internal/pair"
	"sh.simple/simpled/internal/wire"
)

type Server struct {
	store *config.Store
	pair  *pair.Manager
	hooks *hooks.Daemon
	ln    net.Listener
	mu    sync.Mutex
}

func New(store *config.Store, pm *pair.Manager, hd *hooks.Daemon) *Server {
	return &Server{store: store, pair: pm, hooks: hd}
}

var ErrRunning = errors.New("simpled is already running")

func Alive(sock string) bool {
	c, err := net.DialTimeout("unix", sock, time.Second)
	if err != nil {
		return false
	}
	c.Close()
	return true
}

func (s *Server) Listen() error {
	if Alive(s.store.ControlSock) {
		return ErrRunning
	}
	if err := os.Remove(s.store.ControlSock); err != nil && !os.IsNotExist(err) {
		return err
	}
	ln, err := net.Listen("unix", s.store.ControlSock)
	if err != nil {
		return err
	}
	if err := os.Chmod(s.store.ControlSock, 0o600); err != nil {
		ln.Close()
		return err
	}
	s.ln = ln
	return nil
}

func (s *Server) Serve() {
	for {
		c, err := s.ln.Accept()
		if err != nil {
			return
		}
		go s.handle(c)
	}
}

func (s *Server) Close() error {
	if s.ln != nil {
		return s.ln.Close()
	}
	return nil
}

type req struct {
	M string          `json:"m"`
	P json.RawMessage `json:"p,omitempty"`
}

type resp struct {
	R json.RawMessage `json:"r,omitempty"`
	E *wire.ErrorBody `json:"e,omitempty"`
}

func (s *Server) handle(c net.Conn) {
	defer c.Close()
	br := bufio.NewReader(c)
	for {
		line, err := br.ReadBytes('\n')
		if err != nil {
			return
		}
		var rq req
		if json.Unmarshal(line, &rq) != nil {
			writeResp(c, resp{E: &wire.ErrorBody{Kind: "bad_request"}})
			continue
		}
		switch rq.M {
		case "pair.code":
			code, _ := s.pair.NewCode()
			b, _ := json.Marshal(map[string]string{"code": code})
			writeResp(c, resp{R: b})
		case "devices.list":
			list, err := s.store.LoadDevices()
			if err != nil {
				writeResp(c, resp{E: &wire.ErrorBody{Kind: "internal"}})
				continue
			}
			views := make([]wire.DeviceView, len(list))
			for i, d := range list {
				views[i] = wire.DeviceView{
					ID: d.ID, Name: d.Name, Model: d.Model,
					Created: d.Created, LastSeen: d.LastSeen,
				}
			}
			b, _ := json.Marshal(wire.DevicesListResult{Devices: views})
			writeResp(c, resp{R: b})
		case "devices.rm":
			var p struct {
				ID string `json:"id"`
			}
			_ = json.Unmarshal(rq.P, &p)
			if p.ID == "" {
				writeResp(c, resp{E: &wire.ErrorBody{Kind: "bad_request"}})
				continue
			}
			list, err := s.store.LoadDevices()
			if err != nil {
				writeResp(c, resp{E: &wire.ErrorBody{Kind: "internal"}})
				continue
			}
			var name string
			for _, d := range list {
				if d.ID == p.ID {
					name = d.Name
					break
				}
			}
			if name == "" {
				writeResp(c, resp{E: &wire.ErrorBody{Kind: "not_found"}})
				continue
			}
			if err := s.store.RevokeDevice(p.ID); err != nil {
				if errors.Is(err, os.ErrNotExist) {
					writeResp(c, resp{E: &wire.ErrorBody{Kind: "not_found"}})
				} else {
					writeResp(c, resp{E: &wire.ErrorBody{Kind: "internal"}})
				}
				continue
			}
			b, _ := json.Marshal(map[string]string{"name": name})
			writeResp(c, resp{R: b})
		case "hook":
			var p struct {
				Terminal string          `json:"terminal"`
				Kind     string          `json:"kind"`
				Event    string          `json:"event"`
				Body     json.RawMessage `json:"body"`
			}
			if json.Unmarshal(rq.P, &p) != nil || p.Terminal == "" || p.Event == "" {
				writeResp(c, resp{E: &wire.ErrorBody{Kind: "bad_request"}})
				continue
			}
			if s.hooks == nil {
				writeResp(c, resp{R: json.RawMessage(`{}`)})
				continue
			}
			if !s.hooks.KnowsTerminal(p.Terminal) {
				writeResp(c, resp{R: json.RawMessage(`{}`)})
				continue
			}
			out, ok := s.hooks.Handle(context.Background(), p.Kind, p.Terminal, p.Event, p.Body)
			if !ok || out == nil {
				writeResp(c, resp{R: json.RawMessage(`{}`)})
				continue
			}
			writeResp(c, resp{R: out})
		default:
			writeResp(c, resp{E: &wire.ErrorBody{Kind: "bad_request"}})
		}
	}
}

func writeResp(c net.Conn, r resp) {
	b, _ := json.Marshal(r)
	b = append(b, '\n')
	_, _ = c.Write(b)
}

func Request(sock, method string, params any) (json.RawMessage, error) {
	c, err := net.Dial("unix", sock)
	if err != nil {
		return nil, err
	}
	defer c.Close()
	p, _ := json.Marshal(params)
	line, _ := json.Marshal(req{M: method, P: p})
	line = append(line, '\n')
	if _, err := c.Write(line); err != nil {
		return nil, err
	}
	br := bufio.NewReader(c)
	out, err := br.ReadBytes('\n')
	if err != nil {
		return nil, err
	}
	var rs resp
	if err := json.Unmarshal(out, &rs); err != nil {
		return nil, err
	}
	if rs.E != nil {
		return nil, errors.New(rs.E.Kind)
	}
	return rs.R, nil
}
