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

// Accounts implements account.Repository. Create also makes accounts with a
// token directly, for cmd/devaccount and tests.
type Accounts struct{ db *DB }

// NewAccounts returns the account repository.
func NewAccounts(db *DB) *Accounts { return &Accounts{db} }

var _ account.Repository = (*Accounts)(nil)

// ForIdentity returns the account id signs in to, creating a free account the
// first time.
func (a *Accounts) ForIdentity(ctx context.Context, id account.Identity, now time.Time) (account.Account, error) {
	var acc account.Account
	err := a.db.inTx(ctx, func(q *sqlcgen.Queries) error {
		row, err := q.AccountByIdentity(ctx, sqlcgen.AccountByIdentityParams{Provider: id.Provider, Subject: id.Subject})
		if err == nil {
			acc = account.Account{ID: row.ID, Tier: account.Tier(row.Tier)}
			return nil
		}
		if !errors.Is(err, sql.ErrNoRows) {
			return err
		}
		acc = account.Account{ID: domain.NewID(), Tier: account.Free}
		if err := q.CreateAccount(ctx, sqlcgen.CreateAccountParams{ID: acc.ID, Tier: string(acc.Tier), CreatedAt: formatTime(now)}); err != nil {
			return err
		}
		return q.CreateAccountIdentity(ctx, sqlcgen.CreateAccountIdentityParams{
			Provider: id.Provider, Subject: id.Subject, AccountID: acc.ID, CreatedAt: formatTime(now),
		})
	})
	return acc, err
}

// AddToken stores a token's hash for the account.
func (a *Accounts) AddToken(ctx context.Context, accountID string, tokenHash []byte, now time.Time) error {
	return a.db.w.CreateAccountToken(ctx, sqlcgen.CreateAccountTokenParams{TokenHash: tokenHash, AccountID: accountID, CreatedAt: formatTime(now)})
}

// ByTokenHash returns the account holding the token.
func (a *Accounts) ByTokenHash(ctx context.Context, tokenHash []byte) (account.Account, error) {
	row, err := a.db.r.AccountByTokenHash(ctx, tokenHash)
	if errors.Is(err, sql.ErrNoRows) {
		return account.Account{}, domain.ErrNotFound
	}
	if err != nil {
		return account.Account{}, err
	}
	return account.Account{ID: row.ID, Tier: account.Tier(row.Tier)}, nil
}

// RemoveToken forgets a token. Removing one it doesn't hold is not an error.
func (a *Accounts) RemoveToken(ctx context.Context, tokenHash []byte) error {
	return a.db.w.DeleteAccountToken(ctx, tokenHash)
}

// Create makes an account of the given tier and returns it with a new token.
// It is for development and tests; users get accounts by signing in.
func (a *Accounts) Create(ctx context.Context, tier account.Tier, now time.Time) (account.Account, string, error) {
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
