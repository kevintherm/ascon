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
// domain.ErrUnauthenticated for a token it does not accept. The sign-in flow
// is not designed yet, so this port is what a real one will implement.
type Verifier interface {
	Verify(ctx context.Context, token string) (Account, error)
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
