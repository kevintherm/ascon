// Package account holds signed-in users. Accounts unlock sync and AI
// detection; premium accounts get larger quotas. See AGENTS.md, Accounts and
// monetization.
package account

import (
	"context"
	"time"
)

// Tier is the account's plan.
type Tier string

// Tiers.
const (
	Free    Tier = "free"
	Premium Tier = "premium"
)

// Account is a signed-in user.
type Account struct {
	ID   string
	Tier Tier
}

// Verifier turns an account bearer token into an account. It returns
// domain.ErrUnauthenticated for a token it does not accept.
type Verifier interface {
	Verify(ctx context.Context, token string) (Account, error)
}

// Identity is a user as a sign-in provider knows them. Subject is the
// provider's stable id for the user, Google's sub claim. Nothing else about
// the user, such as their email, is kept.
type Identity struct {
	Provider string
	Subject  string
}

// Google is the provider name of Google sign-in.
const Google = "google"

// IdentityVerifier checks an ID token from a sign-in provider. It returns
// domain.ErrUnauthenticated for a token it does not accept, and another error
// when it could not check, such as when the provider's keys can't be fetched.
type IdentityVerifier interface {
	VerifyIdentity(ctx context.Context, idToken string) (Identity, error)
}

// Repository stores accounts, the identities that sign in to them and their
// bearer tokens. Tokens are stored only as hashes.
type Repository interface {
	// ForIdentity returns the account id signs in to, creating a free account
	// the first time.
	ForIdentity(ctx context.Context, id Identity, now time.Time) (Account, error)
	AddToken(ctx context.Context, accountID string, tokenHash []byte, now time.Time) error
	// ByTokenHash returns domain.ErrNotFound for a token it doesn't hold.
	ByTokenHash(ctx context.Context, tokenHash []byte) (Account, error)
	RemoveToken(ctx context.Context, tokenHash []byte) error
}

// QuotaRepository counts AI detection use per account and period.
type QuotaRepository interface {
	// Use adds one to the account's use in period if it is below limit and
	// reports the use after the call. ok is false when the limit was reached.
	Use(ctx context.Context, accountID, period string, limit int) (used int, ok bool, err error)
	// Refund takes one back, for a request that failed through no fault of
	// the user.
	Refund(ctx context.Context, accountID, period string) error
	Used(ctx context.Context, accountID, period string) (int, error)
}

// Limits are the AI detection requests allowed per month for each tier.
type Limits map[Tier]int

// Period returns the quota period holding t: its calendar month in UTC.
func Period(t time.Time) string {
	return t.UTC().Format("2006-01")
}

// PeriodEnd returns when the period holding t ends.
func PeriodEnd(t time.Time) time.Time {
	t = t.UTC()
	return time.Date(t.Year(), t.Month()+1, 1, 0, 0, 0, 0, time.UTC)
}
