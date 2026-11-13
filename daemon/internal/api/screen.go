package api

import (
	"context"
	"sync"
	"time"

	"sh.simple/simpled/internal/agents"
	"sh.simple/simpled/internal/hc"
	"sh.simple/simpled/internal/herdr"
	"sh.simple/simpled/internal/wire"
)

type ScreenWatcher struct {
	hc      *hc.Client
	herdr   *herdr.Client
	pub     func(string, any)
	prompts *PromptTracker
	agents  *agents.Store

	mu           sync.Mutex
	jobs         map[string]*screenJob
	slowInterval time.Duration
	fastInterval time.Duration
	tickInterval time.Duration
}

type screenJob struct {
	cancel   context.CancelFunc
	watchers map[*session]struct{}
	joined   bool
}

func NewScreenWatcher(hcClient *hc.Client, herdrClient *herdr.Client, pub func(string, any), prompts *PromptTracker, agentStore *agents.Store) *ScreenWatcher {
	return &ScreenWatcher{
		hc:           hcClient,
		herdr:        herdrClient,
		pub:          pub,
		prompts:      prompts,
		agents:       agentStore,
		jobs:         make(map[string]*screenJob),
		slowInterval: time.Second,
		fastInterval: 300 * time.Millisecond,
		tickInterval: 100 * time.Millisecond,
	}
}

func (s *ScreenWatcher) SyncPollers() {
	if s == nil || s.agents == nil {
		return
	}
	want := map[string]bool{}
	for _, id := range s.agents.PollableAgentIDs() {
		want[id] = true
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	for id := range want {
		if _, ok := s.jobs[id]; ok {
			continue
		}
		s.startJobLocked(id)
	}
	for id, job := range s.jobs {
		if want[id] {
			continue
		}
		if len(job.watchers) > 0 {
			continue
		}
		job.cancel()
		delete(s.jobs, id)
	}
}

func (s *ScreenWatcher) startJobLocked(agentID string) {
	ctx, cancel := context.WithCancel(context.Background())
	job := &screenJob{cancel: cancel, watchers: make(map[*session]struct{})}
	s.jobs[agentID] = job
	go s.pollLoop(ctx, agentID, job)
}

func (s *ScreenWatcher) ensureJob(agentID string) *screenJob {
	s.mu.Lock()
	defer s.mu.Unlock()
	if job := s.jobs[agentID]; job != nil {
		return job
	}
	s.startJobLocked(agentID)
	return s.jobs[agentID]
}

func (s *ScreenWatcher) Start(agentID string, sess *session) {
	job := s.ensureJob(agentID)
	s.mu.Lock()
	job.watchers[sess] = struct{}{}
	job.joined = true
	s.mu.Unlock()
}

func (s *ScreenWatcher) Stop(agentID string, sess *session) {
	s.mu.Lock()
	if job := s.jobs[agentID]; job != nil {
		delete(job.watchers, sess)
	}
	s.mu.Unlock()
}

func (s *ScreenWatcher) StopAll(sess *session) {
	s.mu.Lock()
	for _, job := range s.jobs {
		delete(job.watchers, sess)
	}
	s.mu.Unlock()
}

func (s *ScreenWatcher) TestIntervals(slow, fast, tick time.Duration) {
	s.slowInterval = slow
	s.fastInterval = fast
	s.tickInterval = tick
}

func (s *ScreenWatcher) PollAgentOnce(ctx context.Context, agentID string) {
	text, ok := s.readScreen(ctx, agentID)
	if !ok {
		return
	}
	if s.prompts != nil {
		s.prompts.OnScreen(agentID, text)
		s.prompts.Tick()
	}
}

func (s *ScreenWatcher) pollLoop(ctx context.Context, agentID string, job *screenJob) {
	last := ""
	nextPoll := time.Now()
	tick := time.NewTicker(s.tickInterval)
	defer tick.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case now := <-tick.C:
			if s.prompts != nil {
				s.prompts.Tick()
			}
			if now.Before(nextPoll) {
				continue
			}
			s.mu.Lock()
			watchers := len(job.watchers)
			s.mu.Unlock()
			interval := s.slowInterval
			if watchers > 0 {
				interval = s.fastInterval
			}
			nextPoll = now.Add(interval)
			outText, ok := s.readScreen(ctx, agentID)
			if !ok {
				continue
			}
			if s.prompts != nil {
				s.prompts.OnScreen(agentID, outText)
			}
			s.mu.Lock()
			publish := len(job.watchers) > 0
			joined := job.joined
			job.joined = false
			s.mu.Unlock()
			if publish && (joined || outText != last) {
				last = outText
				s.pub("screen", wire.ScreenEvent{ID: agentID, Text: outText, Rows: 0, Cols: 0})
			}
			if !publish {
				last = outText
			}
		}
	}
}

func (s *ScreenWatcher) readScreen(ctx context.Context, agentID string) (string, bool) {
	if s.agents != nil && s.agents.AgentRuntime(agentID) == "herdr" {
		if s.herdr == nil || s.herdr.Status() != herdr.StatusUp {
			return "", false
		}
		target, ok := s.agents.HerdrTarget(agentID)
		if !ok {
			return "", false
		}
		text, err := s.herdr.AgentRead(ctx, target, "recent_unwrapped", nil)
		if err != nil {
			return "", false
		}
		return text, true
	}
	var out struct {
		Text string `json:"text"`
	}
	if err := s.hc.Call(ctx, "terminal.read", map[string]any{"terminal": agentID}, &out); err != nil {
		return "", false
	}
	return out.Text, true
}
