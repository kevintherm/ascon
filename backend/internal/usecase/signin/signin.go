// Package signin signs users in with a provider's ID token and checks the
// account tokens it issues. See AGENTS.md, Accounts and monetization.
package signin

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"errors"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/account"
)

// maxIDToken bounds the ID token accepted. Google's are about 1 KiB.
const maxIDToken = 8 << 10

// Service signs users in and out.
type Service struct {
	identities account.IdentityVerifier
	accounts   account.Repository
	now        func() time.Time
}

var _ account.Verifier = (*Service)(nil)

// New returns the service.
func New(identities account.IdentityVerifier, accounts account.Repository, now func() time.Time) *Service {
	return &Service{identities: identities, accounts: accounts, now: now}
}

// SignIn checks idToken, finds or creates the account it belongs to, and
// returns the account with a new bearer token. Only the token's hash is kept.
func (s *Service) SignIn(ctx context.Context, idToken string) (account.Account, string, error) {
	if idToken == "" || len(idToken) > maxIDToken {
		return account.Account{}, "", domain.Invalidf("idToken must be 1 to %d characters", maxIDToken)
	}
	id, err := s.identities.VerifyIdentity(ctx, idToken)
	if err != nil {
		return account.Account{}, "", err
	}
	now := s.now()
	acc, err := s.accounts.ForIdentity(ctx, id, now)
	if err != nil {
		return account.Account{}, "", err
	}

	var raw [32]byte
	if _, err := rand.Read(raw[:]); err != nil {
		return account.Account{}, "", err
	}
	token := base64.RawURLEncoding.EncodeToString(raw[:])
	if err := s.accounts.AddToken(ctx, acc.ID, hash(token), now); err != nil {
		return account.Account{}, "", err
	}
	return acc, token, nil
}

// Verify returns the account holding token, or domain.ErrUnauthenticated.
func (s *Service) Verify(ctx context.Context, token string) (account.Account, error) {
	if token == "" {
		return account.Account{}, domain.ErrUnauthenticated
	}
	acc, err := s.accounts.ByTokenHash(ctx, hash(token))
	if errors.Is(err, domain.ErrNotFound) {
		return account.Account{}, domain.ErrUnauthenticated
	}
	return acc, err
}

// SignOut stops token from working. The account and its other tokens stay.
func (s *Service) SignOut(ctx context.Context, token string) error {
	return s.accounts.RemoveToken(ctx, hash(token))
}

func hash(token string) []byte {
	h := sha256.Sum256([]byte(token))
	return h[:]
}
