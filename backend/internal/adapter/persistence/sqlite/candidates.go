package sqlite

import (
	"context"
	"database/sql"
	"errors"
	"time"

	"github.com/kevintherm/ascon/backend/internal/adapter/persistence/sqlite/sqlcgen"
	"github.com/kevintherm/ascon/backend/internal/domain/rule"
)

// Candidates implements rule.CandidateRepository.
type Candidates struct{ db *DB }

// NewCandidates returns the candidate repository.
func NewCandidates(db *DB) *Candidates { return &Candidates{db} }

var _ rule.CandidateRepository = (*Candidates)(nil)

// Insert stores a new candidate.
func (c *Candidates) Insert(ctx context.Context, cand rule.Candidate) error {
	return c.db.w.InsertCandidate(ctx, sqlcgen.InsertCandidateParams{
		ID: cand.ID, Domain: cand.Domain, AccountID: cand.AccountID, Status: string(cand.Status),
		Reason: nullString(cand.Reason), RuleVersion: nullInt(cand.RuleVersion),
		CreatedAt: formatTime(cand.CreatedAt), UpdatedAt: formatTime(cand.UpdatedAt),
	})
}

// Get returns the account's candidate.
func (c *Candidates) Get(ctx context.Context, id, accountID string) (rule.Candidate, error) {
	row, err := c.db.r.GetCandidate(ctx, sqlcgen.GetCandidateParams{ID: id, AccountID: accountID})
	if err != nil {
		return rule.Candidate{}, notFound(err)
	}
	created, err := parseTime(row.CreatedAt)
	if err != nil {
		return rule.Candidate{}, err
	}
	updated, err := parseTime(row.UpdatedAt)
	if err != nil {
		return rule.Candidate{}, err
	}
	return rule.Candidate{
		ID: row.ID, Domain: row.Domain, AccountID: row.AccountID, Status: rule.CandidateStatus(row.Status),
		Reason: row.Reason.String, RuleVersion: int(row.RuleVersion.Int64),
		CreatedAt: created, UpdatedAt: updated,
	}, nil
}

// HasPending reports whether a generation is running for domain.
func (c *Candidates) HasPending(ctx context.Context, domain string) (bool, error) {
	return c.db.r.HasPendingCandidate(ctx, domain)
}

// Resolve settles every pending candidate for domain.
func (c *Candidates) Resolve(ctx context.Context, domain string, status rule.CandidateStatus, reason string, ruleVersion int, at time.Time) error {
	return c.db.w.ResolveCandidates(ctx, sqlcgen.ResolveCandidatesParams{
		Status: string(status), Reason: nullString(reason), RuleVersion: nullInt(ruleVersion),
		UpdatedAt: formatTime(at), Domain: domain,
	})
}

// AbandonPending rejects every pending candidate.
func (c *Candidates) AbandonPending(ctx context.Context, reason string, at time.Time) (int, error) {
	n, err := c.db.w.AbandonPendingCandidates(ctx, sqlcgen.AbandonPendingCandidatesParams{Reason: nullString(reason), UpdatedAt: formatTime(at)})
	return int(n), err
}

// RecordFailure notes that no usable rule could be written for domain.
func (c *Candidates) RecordFailure(ctx context.Context, domain, reason string, at time.Time) error {
	return c.db.w.RecordGenerationFailure(ctx, sqlcgen.RecordGenerationFailureParams{
		Domain: domain, Reason: reason, FailedAt: formatTime(at),
	})
}

// LastFailure returns when generation last failed for domain, or the zero time.
func (c *Candidates) LastFailure(ctx context.Context, domain string) (time.Time, error) {
	at, err := c.db.r.LastGenerationFailure(ctx, domain)
	if errors.Is(err, sql.ErrNoRows) {
		return time.Time{}, nil
	}
	if err != nil {
		return time.Time{}, err
	}
	return parseTime(at)
}

func nullInt(n int) sql.NullInt64 { return sql.NullInt64{Int64: int64(n), Valid: n != 0} }
