package google

import (
	"context"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"math/big"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/account"
)

const client = "web.apps.googleusercontent.com"

var now = time.Date(2026, 10, 10, 12, 0, 0, 0, time.UTC)

type issuer struct {
	key     *rsa.PrivateKey
	kid     atomic.Value // the key id the certs URL publishes the key under
	fetches atomic.Int32
	srv     *httptest.Server
}

// newIssuer serves a key set holding one fresh key, as Google's certs URL does.
func newIssuer(t *testing.T) *issuer {
	t.Helper()
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	is := &issuer{key: key}
	is.kid.Store("k1")
	is.srv = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		is.fetches.Add(1)
		w.Header().Set("Cache-Control", "public, max-age=3600, must-revalidate")
		_ = json.NewEncoder(w).Encode(map[string]any{"keys": []any{map[string]string{
			"kid": is.kid.Load().(string), "kty": "RSA", "alg": "RS256", "use": "sig",
			"n": b64(key.N.Bytes()), "e": b64(big.NewInt(int64(key.E)).Bytes()),
		}}})
	}))
	t.Cleanup(is.srv.Close)
	return is
}

func (is *issuer) verifier() *IDTokens {
	return &IDTokens{ClientID: client, CertsURL: is.srv.URL, Now: func() time.Time { return now }}
}

func (is *issuer) verifierAt(clock *time.Time) *IDTokens {
	return &IDTokens{ClientID: client, CertsURL: is.srv.URL, Now: func() time.Time { return *clock }}
}

func (is *issuer) sign(t *testing.T, alg, kid string, c map[string]any) string {
	t.Helper()
	h, _ := json.Marshal(map[string]string{"alg": alg, "kid": kid, "typ": "JWT"})
	p, _ := json.Marshal(c)
	signed := b64(h) + "." + b64(p)
	digest := sha256.Sum256([]byte(signed))
	sig, err := rsa.SignPKCS1v15(rand.Reader, is.key, crypto.SHA256, digest[:])
	if err != nil {
		t.Fatal(err)
	}
	return signed + "." + b64(sig)
}

func valid() map[string]any {
	return map[string]any{
		"iss": "https://accounts.google.com", "aud": client, "azp": "android.apps.googleusercontent.com",
		"sub": "1234567890", "email": "reader@example.com",
		"iat": now.Add(-time.Minute).Unix(), "exp": now.Add(time.Hour).Unix(),
	}
}

func b64(b []byte) string { return base64.RawURLEncoding.EncodeToString(b) }

func TestVerifyIdentityAcceptsAGoogleToken(t *testing.T) {
	is := newIssuer(t)
	v := is.verifier()

	for range 2 {
		id, err := v.VerifyIdentity(context.Background(), is.sign(t, "RS256", "k1", valid()))
		if err != nil {
			t.Fatal(err)
		}
		if id != (account.Identity{Provider: account.Google, Subject: "1234567890"}) {
			t.Fatalf("identity = %+v", id)
		}
	}
	if n := is.fetches.Load(); n != 1 {
		t.Fatalf("fetched keys %d times, want once while cached", n)
	}
}

func TestVerifyIdentityRefusals(t *testing.T) {
	is := newIssuer(t)
	other, _ := rsa.GenerateKey(rand.Reader, 2048)

	with := func(k string, v any) map[string]any {
		c := valid()
		c[k] = v
		return c
	}
	cases := map[string]string{
		"not a jwt":      "abc.def",
		"wrong audience": is.sign(t, "RS256", "k1", with("aud", "someone-else")),
		"wrong issuer":   is.sign(t, "RS256", "k1", with("iss", "https://evil.example")),
		"expired":        is.sign(t, "RS256", "k1", with("exp", now.Add(-time.Hour).Unix())),
		"future":         is.sign(t, "RS256", "k1", with("iat", now.Add(time.Hour).Unix())),
		"no subject":     is.sign(t, "RS256", "k1", with("sub", "")),
		"alg none":       is.sign(t, "none", "k1", valid()),
		"unknown key":    is.sign(t, "RS256", "k9", valid()),
	}
	// Signed with a key Google doesn't publish, under Google's key id.
	forged := &issuer{key: other}
	cases["forged"] = forged.sign(t, "RS256", "k1", valid())

	v := is.verifier()
	for name, token := range cases {
		t.Run(name, func(t *testing.T) {
			if _, err := v.VerifyIdentity(context.Background(), token); !errors.Is(err, domain.ErrUnauthenticated) {
				t.Fatalf("err = %v, want ErrUnauthenticated", err)
			}
		})
	}
	// Unknown key ids refetch at most once a minute.
	if n := is.fetches.Load(); n != 1 {
		t.Fatalf("fetched keys %d times, want 1", n)
	}
}

func TestVerifyIdentityFetchesRotatedKeys(t *testing.T) {
	is := newIssuer(t)
	clock := now
	v := is.verifierAt(&clock)
	if _, err := v.VerifyIdentity(context.Background(), is.sign(t, "RS256", "k1", valid())); err != nil {
		t.Fatal(err)
	}

	// Google rotates to a new key id; a token signed with it arrives later.
	is.kid.Store("k2")
	clock = clock.Add(2 * time.Minute)
	if _, err := v.VerifyIdentity(context.Background(), is.sign(t, "RS256", "k2", valid())); err != nil {
		t.Fatalf("rotated key: %v", err)
	}
}

func TestVerifyIdentityReportsAnUnreachableGoogle(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusServiceUnavailable)
	}))
	t.Cleanup(srv.Close)
	is := newIssuer(t)
	v := &IDTokens{ClientID: client, CertsURL: srv.URL, Now: func() time.Time { return now }}

	_, err := v.VerifyIdentity(context.Background(), is.sign(t, "RS256", "k1", valid()))
	if err == nil || errors.Is(err, domain.ErrUnauthenticated) {
		t.Fatalf("err = %v, want a server error, not a refusal", err)
	}
}

func TestMaxAge(t *testing.T) {
	if got := maxAge("public, max-age=21600, must-revalidate"); got != 6*time.Hour {
		t.Fatalf("maxAge = %v", got)
	}
	if got := maxAge("no-cache"); got != defaultKeyLife {
		t.Fatalf("maxAge without max-age = %v", got)
	}
}
