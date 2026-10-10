package signin

import (
	"context"
	"errors"
	"sync"
	"testing"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/account"
)

// identities accepts the tokens it maps to a subject.
type identities map[string]string

func (m identities) VerifyIdentity(_ context.Context, idToken string) (account.Identity, error) {
	sub, ok := m[idToken]
	if !ok {
		return account.Identity{}, domain.ErrUnauthenticated
	}
	return account.Identity{Provider: account.Google, Subject: sub}, nil
}

type accounts struct {
	mu     sync.Mutex
	bySub  map[string]account.Account
	tokens map[string]account.Account
}

func newAccounts() *accounts {
	return &accounts{bySub: map[string]account.Account{}, tokens: map[string]account.Account{}}
}

func (a *accounts) ForIdentity(_ context.Context, id account.Identity, _ time.Time) (account.Account, error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	acc, ok := a.bySub[id.Subject]
	if !ok {
		acc = account.Account{ID: domain.NewID(), Tier: account.Free}
		a.bySub[id.Subject] = acc
	}
	return acc, nil
}

func (a *accounts) AddToken(_ context.Context, accountID string, h []byte, _ time.Time) error {
	a.mu.Lock()
	defer a.mu.Unlock()
	for _, acc := range a.bySub {
		if acc.ID == accountID {
			a.tokens[string(h)] = acc
		}
	}
	return nil
}

func (a *accounts) ByTokenHash(_ context.Context, h []byte) (account.Account, error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	acc, ok := a.tokens[string(h)]
	if !ok {
		return account.Account{}, domain.ErrNotFound
	}
	return acc, nil
}

func (a *accounts) RemoveToken(_ context.Context, h []byte) error {
	a.mu.Lock()
	defer a.mu.Unlock()
	delete(a.tokens, string(h))
	return nil
}

func TestSignInKeepsOneAccountPerUser(t *testing.T) {
	ctx := context.Background()
	s := New(identities{"phone": "111", "tablet": "111", "other": "222"}, newAccounts(), time.Now)

	first, token, err := s.SignIn(ctx, "phone")
	if err != nil || token == "" || first.Tier != account.Free {
		t.Fatalf("SignIn = %+v, %q, %v", first, token, err)
	}
	second, token2, err := s.SignIn(ctx, "tablet")
	if err != nil || second != first || token2 == token {
		t.Fatalf("second device = %+v, %q, %v; want %+v with a new token", second, token2, err, first)
	}
	other, _, err := s.SignIn(ctx, "other")
	if err != nil || other.ID == first.ID {
		t.Fatalf("another user = %+v, %v", other, err)
	}

	for _, tok := range []string{token, token2} {
		if got, err := s.Verify(ctx, tok); err != nil || got != first {
			t.Fatalf("Verify = %+v, %v", got, err)
		}
	}
}

func TestSignOutEndsOnlyThatToken(t *testing.T) {
	ctx := context.Background()
	s := New(identities{"a": "111", "b": "111"}, newAccounts(), time.Now)
	_, phone, _ := s.SignIn(ctx, "a")
	_, tablet, _ := s.SignIn(ctx, "b")

	if err := s.SignOut(ctx, phone); err != nil {
		t.Fatal(err)
	}
	if _, err := s.Verify(ctx, phone); !errors.Is(err, domain.ErrUnauthenticated) {
		t.Fatalf("signed-out token: %v, want ErrUnauthenticated", err)
	}
	if _, err := s.Verify(ctx, tablet); err != nil {
		t.Fatalf("other token stopped working: %v", err)
	}
}

func TestSignInRefusals(t *testing.T) {
	ctx := context.Background()
	s := New(identities{}, newAccounts(), time.Now)

	if _, _, err := s.SignIn(ctx, ""); !errors.Is(err, domain.ErrInvalid) {
		t.Fatalf("empty token: %v, want ErrInvalid", err)
	}
	if _, _, err := s.SignIn(ctx, "forged"); !errors.Is(err, domain.ErrUnauthenticated) {
		t.Fatalf("refused token: %v, want ErrUnauthenticated", err)
	}
	if _, err := s.Verify(ctx, ""); !errors.Is(err, domain.ErrUnauthenticated) {
		t.Fatalf("Verify(empty): %v", err)
	}
}
