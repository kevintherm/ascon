// Package memory holds in-memory repositories for use case tests.
package memory

import (
	"context"
	"sync"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/rule"
)

type ruleKey struct {
	domain  string
	version int
}

// Rules implements rule.Repository.
type Rules struct {
	mu      sync.Mutex
	stored  map[ruleKey]rule.Stored
	health  map[ruleKey]rule.Health
	devices map[ruleKey]map[string]bool
}

// NewRules returns an empty repository.
func NewRules() *Rules {
	return &Rules{
		stored: map[ruleKey]rule.Stored{}, health: map[ruleKey]rule.Health{},
		devices: map[ruleKey]map[string]bool{},
	}
}

// Latest implements rule.Repository.
func (m *Rules) Latest(_ context.Context, d string) (rule.Stored, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.latest(d)
}

func (m *Rules) latest(d string) (rule.Stored, error) {
	best := rule.Stored{}
	for k, s := range m.stored {
		if k.domain == d && s.Status != rule.Retired && k.version > best.Rule.Version {
			best = s
		}
	}
	if best.Rule.Version == 0 {
		return rule.Stored{}, domain.ErrNotFound
	}
	return best, nil
}

// Get implements rule.Repository.
func (m *Rules) Get(_ context.Context, d string, v int) (rule.Stored, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	s, ok := m.stored[ruleKey{d, v}]
	if !ok {
		return rule.Stored{}, domain.ErrNotFound
	}
	return s, nil
}

// Fingerprinted implements rule.Repository.
func (m *Rules) Fingerprinted(_ context.Context) ([]rule.Stored, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	seen := map[string]bool{}
	var out []rule.Stored
	for k := range m.stored {
		if seen[k.domain] {
			continue
		}
		seen[k.domain] = true
		if s, err := m.latest(k.domain); err == nil && s.Rule.Fingerprint != "" {
			out = append(out, s)
		}
	}
	return out, nil
}

// MaxVersion implements rule.Repository.
func (m *Rules) MaxVersion(_ context.Context, d string) (int, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	maxV := 0
	for k := range m.stored {
		if k.domain == d && k.version > maxV {
			maxV = k.version
		}
	}
	return maxV, nil
}

// Insert implements rule.Repository.
func (m *Rules) Insert(_ context.Context, s rule.Stored) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	k := ruleKey{s.Rule.Domain, s.Rule.Version}
	if _, ok := m.stored[k]; ok {
		return domain.ErrConflict
	}
	m.stored[k] = s
	return nil
}

// SetStatus implements rule.Repository.
func (m *Rules) SetStatus(_ context.Context, d string, v int, st rule.Status) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	s := m.stored[ruleKey{d, v}]
	s.Status = st
	m.stored[ruleKey{d, v}] = s
	return nil
}

// Health implements rule.Repository.
func (m *Rules) Health(_ context.Context, d string, v int) (rule.Health, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.health[ruleKey{d, v}], nil
}

// AddHealth implements rule.Repository.
func (m *Rules) AddHealth(_ context.Context, d string, v int, h rule.Health) (rule.Health, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	k := ruleKey{d, v}
	t := m.health[k]
	t.Successes += h.Successes
	t.EmptyResults += h.EmptyResults
	t.BackwardJumps += h.BackwardJumps
	m.health[k] = t
	return t, nil
}

// AddReport implements rule.Repository.
func (m *Rules) AddReport(_ context.Context, r rule.Report) (int, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	k := ruleKey{r.Domain, r.Version}
	if m.devices[k] == nil {
		m.devices[k] = map[string]bool{}
	}
	m.devices[k][r.DeviceID] = true
	return len(m.devices[k]), nil
}

// Status returns a stored version's status, for assertions.
func (m *Rules) Status(d string, v int) rule.Status {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.stored[ruleKey{d, v}].Status
}

// Candidates implements rule.CandidateRepository.
type Candidates struct {
	mu       sync.Mutex
	byID     map[string]rule.Candidate
	failures map[string]time.Time
}

// NewCandidates returns an empty repository.
func NewCandidates() *Candidates {
	return &Candidates{byID: map[string]rule.Candidate{}, failures: map[string]time.Time{}}
}

// RecordFailure implements rule.CandidateRepository.
func (m *Candidates) RecordFailure(_ context.Context, d, _ string, at time.Time) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.failures[d] = at
	return nil
}

// LastFailure implements rule.CandidateRepository.
func (m *Candidates) LastFailure(_ context.Context, d string) (time.Time, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.failures[d], nil
}

// Insert implements rule.CandidateRepository.
func (m *Candidates) Insert(_ context.Context, c rule.Candidate) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.byID[c.ID] = c
	return nil
}

// Get implements rule.CandidateRepository.
func (m *Candidates) Get(_ context.Context, id, accountID string) (rule.Candidate, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	c, ok := m.byID[id]
	if !ok || c.AccountID != accountID {
		return rule.Candidate{}, domain.ErrNotFound
	}
	return c, nil
}

// HasPending implements rule.CandidateRepository.
func (m *Candidates) HasPending(_ context.Context, d string) (bool, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	for _, c := range m.byID {
		if c.Domain == d && c.Status == rule.Pending {
			return true, nil
		}
	}
	return false, nil
}

// Resolve implements rule.CandidateRepository.
func (m *Candidates) Resolve(_ context.Context, d string, st rule.CandidateStatus, reason string, v int, at time.Time) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	for id, c := range m.byID {
		if c.Domain == d && c.Status == rule.Pending {
			c.Status, c.Reason, c.RuleVersion, c.UpdatedAt = st, reason, v, at
			m.byID[id] = c
		}
	}
	return nil
}

// AbandonPending implements rule.CandidateRepository.
func (m *Candidates) AbandonPending(_ context.Context, reason string, at time.Time) (int, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	n := 0
	for id, c := range m.byID {
		if c.Status == rule.Pending {
			c.Status, c.Reason, c.UpdatedAt = rule.Rejected, reason, at
			m.byID[id] = c
			n++
		}
	}
	return n, nil
}

// Quotas implements account.QuotaRepository.
type Quotas struct {
	mu   sync.Mutex
	used map[string]int
}

// NewQuotas returns empty quotas.
func NewQuotas() *Quotas { return &Quotas{used: map[string]int{}} }

// Use implements account.QuotaRepository.
func (m *Quotas) Use(_ context.Context, accountID, period string, limit int) (int, bool, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	k := accountID + "/" + period
	if m.used[k] >= limit {
		return m.used[k], false, nil
	}
	m.used[k]++
	return m.used[k], true, nil
}

// Refund implements account.QuotaRepository.
func (m *Quotas) Refund(_ context.Context, accountID, period string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if k := accountID + "/" + period; m.used[k] > 0 {
		m.used[k]--
	}
	return nil
}

// Used implements account.QuotaRepository.
func (m *Quotas) Used(_ context.Context, accountID, period string) (int, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.used[accountID+"/"+period], nil
}

// Signer signs by prefixing "sig:" so tests can check what was signed.
type Signer struct{}

// Sign implements rule.Signer.
func (Signer) Sign(payload []byte) ([]byte, string) {
	return append([]byte("sig:"), payload...), "test"
}
