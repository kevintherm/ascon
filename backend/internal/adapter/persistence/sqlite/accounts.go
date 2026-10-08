package sqlite

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"database/sql"
	"encoding/base64"
	"errors"
	"time"

	"github.com/kevintherm/ascon/backend/internal/adapter/persistence/sqlite/sqlcgen"
	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/account"
)

// DevAccounts verifies account tokens stored in the account_tokens table and
// can mint them. It stands in until a real sign-in flow exists.
type DevAccounts struct{ db *DB }

// NewDevAccounts returns the development account verifier.
func NewDevAccounts(db *DB) *DevAccounts { return &DevAccounts{db} }

var _ account.Verifier = (*DevAccounts)(nil)

// Verify returns the account holding token.
func (a *DevAccounts) Verify(ctx context.Context, token string) (account.Account, error) {
	hash := sha256.Sum256([]byte(token))
	row, err := a.db.r.AccountByTokenHash(ctx, hash[:])
	if errors.Is(err, sql.ErrNoRows) {
		return account.Account{}, domain.ErrUnauthenticated
	}
	if err != nil {
		return account.Account{}, err
	}
	return account.Account{ID: row.ID, Tier: account.Tier(row.Tier)}, nil
}

// Create makes an account of the given tier and returns it with a new token.
func (a *DevAccounts) Create(ctx context.Context, tier account.Tier, now time.Time) (account.Account, string, error) {
	acc := account.Account{ID: domain.NewID(), Tier: tier}
	var raw [32]byte
	if _, err := rand.Read(raw[:]); err != nil {
		return account.Account{}, "", err
	}
	token := base64.RawURLEncoding.EncodeToString(raw[:])
	hash := sha256.Sum256([]byte(token))

	err := a.db.inTx(ctx, func(q *sqlcgen.Queries) error {
		if err := q.CreateAccount(ctx, sqlcgen.CreateAccountParams{ID: acc.ID, Tier: string(tier), CreatedAt: formatTime(now)}); err != nil {
			return err
		}
		return q.CreateAccountToken(ctx, sqlcgen.CreateAccountTokenParams{TokenHash: hash[:], AccountID: acc.ID, CreatedAt: formatTime(now)})
	})
	return acc, token, err
}

// Quotas implements account.QuotaRepository.
type Quotas struct{ db *DB }

// NewQuotas returns the quota repository.
func NewQuotas(db *DB) *Quotas { return &Quotas{db} }

var _ account.QuotaRepository = (*Quotas)(nil)

// Use adds one to the account's use if it is below limit.
func (q *Quotas) Use(ctx context.Context, accountID, period string, limit int) (int, bool, error) {
	if limit <= 0 {
		used, err := q.Used(ctx, accountID, period)
		return used, false, err
	}
	var used int64
	err := q.db.inTx(ctx, func(tx *sqlcgen.Queries) error {
		if err := tx.EnsureQuotaRow(ctx, sqlcgen.EnsureQuotaRowParams{AccountID: accountID, Period: period}); err != nil {
			return err
		}
		var err error
		used, err = tx.UseQuota(ctx, sqlcgen.UseQuotaParams{AccountID: accountID, Period: period, QuotaLimit: int64(limit)})
		return err
	})
	if errors.Is(err, sql.ErrNoRows) {
		return limit, false, nil
	}
	if err != nil {
		return 0, false, err
	}
	return int(used), true, nil
}

// Refund takes one use back.
func (q *Quotas) Refund(ctx context.Context, accountID, period string) error {
	return q.db.w.RefundQuota(ctx, sqlcgen.RefundQuotaParams{AccountID: accountID, Period: period})
}

// Used returns the account's use in period.
func (q *Quotas) Used(ctx context.Context, accountID, period string) (int, error) {
	used, err := q.db.r.QuotaUsed(ctx, sqlcgen.QuotaUsedParams{AccountID: accountID, Period: period})
	if errors.Is(err, sql.ErrNoRows) {
		return 0, nil
	}
	return int(used), err
}
