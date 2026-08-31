package update

import (
	"context"
	"log/slog"
	"sync"
	"time"
)

type HealthChecker interface {
	Healthy(context.Context) (bool, string)
}

type MonitorStatus struct {
	Phase        string     `json:"phase"`
	HealthySince *time.Time `json:"healthySince,omitempty"`
	Detail       string     `json:"detail,omitempty"`
}

type Monitor struct {
	manager  *Manager
	health   HealthChecker
	window   time.Duration
	interval time.Duration

	mu     sync.RWMutex
	status MonitorStatus
}

func NewMonitor(manager *Manager, health HealthChecker, window, interval time.Duration) *Monitor {
	return &Monitor{
		manager:  manager,
		health:   health,
		window:   window,
		interval: interval,
		status:   MonitorStatus{Phase: "idle"},
	}
}

func (m *Monitor) Status() MonitorStatus {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.status
}

func (m *Monitor) set(status MonitorStatus) {
	m.mu.Lock()
	m.status = status
	m.mu.Unlock()
}

// Run commits only after every workload check has stayed healthy for the full
// window. An unhealthy observation resets the window; there is intentionally
// no timeout that could accidentally commit a partially working candidate.
func (m *Monitor) Run(ctx context.Context) {
	var status Status
	for {
		var err error
		status, err = m.manager.Status(ctx)
		if err == nil {
			break
		}
		m.set(MonitorStatus{Phase: "waiting", Detail: "update status unavailable: " + err.Error()})
		slog.Warn("read update status for trial monitor", "error", err)
		select {
		case <-ctx.Done():
			return
		case <-time.After(m.interval):
		}
	}
	if !status.IsCandidate() {
		m.set(MonitorStatus{Phase: "idle"})
		return
	}

	m.set(MonitorStatus{Phase: "waiting", Detail: "waiting for all workloads to become healthy"})
	ticker := time.NewTicker(m.interval)
	defer ticker.Stop()
	var healthySince time.Time

	for {
		healthy, detail := m.health.Healthy(ctx)
		now := time.Now()
		if healthy {
			if healthySince.IsZero() {
				healthySince = now
				slog.Info("update candidate is healthy; starting commit window", "window", m.window)
			}
			since := healthySince
			m.set(MonitorStatus{Phase: "monitoring", HealthySince: &since})
			if now.Sub(healthySince) >= m.window {
				if err := m.manager.Commit(ctx); err != nil {
					m.set(MonitorStatus{Phase: "error", Detail: err.Error()})
					slog.Error("commit healthy update candidate", "error", err)
					return
				}
				m.set(MonitorStatus{Phase: "committed"})
				slog.Info("committed update candidate after stable health window", "window", m.window)
				return
			}
		} else {
			if !healthySince.IsZero() {
				slog.Warn("update candidate health window reset", "reason", detail)
			}
			healthySince = time.Time{}
			m.set(MonitorStatus{Phase: "waiting", Detail: detail})
		}

		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		}
	}
}
