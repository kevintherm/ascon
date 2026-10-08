package rule

import (
	"context"
	"time"
)

// CandidateStatus is where an AI generation request stands.
type CandidateStatus string

// Candidate statuses.
const (
	Pending  CandidateStatus = "pending"
	Accepted CandidateStatus = "accepted"
	Rejected CandidateStatus = "rejected"
)

// Candidate is one account's request for a rule for a domain. Requests for a
// domain that is already being generated share that run.
type Candidate struct {
	ID          string
	Domain      string
	AccountID   string
	Status      CandidateStatus
	Reason      string
	RuleVersion int
	CreatedAt   time.Time
	UpdatedAt   time.Time
}

// CandidateRepository stores generation requests.
type CandidateRepository interface {
	Insert(ctx context.Context, c Candidate) error
	// Get returns domain.ErrNotFound when the candidate does not exist or
	// belongs to another account.
	Get(ctx context.Context, id, accountID string) (Candidate, error)
	HasPending(ctx context.Context, domain string) (bool, error)
	// Resolve settles every pending candidate for domain.
	Resolve(ctx context.Context, domain string, status CandidateStatus, reason string, ruleVersion int, at time.Time) error
	// AbandonPending rejects every pending candidate, for a server that
	// restarted while generations were running.
	AbandonPending(ctx context.Context, reason string, at time.Time) (int, error)
}
