// Package google verifies Google ID tokens, the credential the app gets from
// Android's Credential Manager when the user signs in with Google.
package google

import (
	"context"
	"crypto"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math/big"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/account"
)

// CertsURL is where Google publishes the keys that sign its ID tokens.
const CertsURL = "https://www.googleapis.com/oauth2/v3/certs"

const (
	// leeway allows for clocks that differ a little.
	leeway = 5 * time.Minute
	// refetchAfter is the least time between fetches for an unknown key id,
	// so forged key ids can't make the server hammer Google.
	refetchAfter = time.Minute
	// defaultKeyLife is how long keys are kept when Google's answer has no
	// max-age.
	defaultKeyLife = time.Hour
	maxCertsBytes  = 64 << 10
)

// IDTokens verifies Google ID tokens issued to ClientID, the OAuth Web client
// the app names as its server client. The token's signature is checked
// against Google's published keys, which are cached as Google's answer
// allows.
type IDTokens struct {
	ClientID string
	// CertsURL defaults to the package's CertsURL.
	CertsURL string
	Client   *http.Client
	Now      func() time.Time

	mu        sync.Mutex
	keys      map[string]*rsa.PublicKey
	expires   time.Time
	fetchedAt time.Time
}

var _ account.IdentityVerifier = (*IDTokens)(nil)

type header struct {
	Alg string `json:"alg"`
	Kid string `json:"kid"`
}

type claims struct {
	Iss string   `json:"iss"`
	Aud audience `json:"aud"`
	Sub string   `json:"sub"`
	Exp int64    `json:"exp"`
	Iat int64    `json:"iat"`
}

// audience is a JWT aud claim, which may be one string or a list.
type audience []string

func (a *audience) UnmarshalJSON(b []byte) error {
	var one string
	if err := json.Unmarshal(b, &one); err == nil {
		*a = audience{one}
		return nil
	}
	var many []string
	if err := json.Unmarshal(b, &many); err != nil {
		return err
	}
	*a = many
	return nil
}

// VerifyIdentity returns the Google user idToken belongs to. A token that is
// malformed, badly signed, expired or meant for another client is refused
// with domain.ErrUnauthenticated.
func (t *IDTokens) VerifyIdentity(ctx context.Context, idToken string) (account.Identity, error) {
	parts := strings.Split(idToken, ".")
	if len(parts) != 3 {
		return account.Identity{}, refused("not a JWT")
	}
	var h header
	if err := decodePart(parts[0], &h); err != nil || h.Alg != "RS256" {
		return account.Identity{}, refused("unsupported header")
	}
	key, err := t.key(ctx, h.Kid)
	if err != nil {
		return account.Identity{}, err
	}
	sig, err := base64.RawURLEncoding.DecodeString(parts[2])
	if err != nil {
		return account.Identity{}, refused("bad signature encoding")
	}
	digest := sha256.Sum256([]byte(parts[0] + "." + parts[1]))
	if err := rsa.VerifyPKCS1v15(key, crypto.SHA256, digest[:], sig); err != nil {
		return account.Identity{}, refused("bad signature")
	}

	var c claims
	if err := decodePart(parts[1], &c); err != nil {
		return account.Identity{}, refused("bad claims")
	}
	if err := t.check(c); err != nil {
		return account.Identity{}, err
	}
	return account.Identity{Provider: account.Google, Subject: c.Sub}, nil
}

func (t *IDTokens) check(c claims) error {
	now := t.now()
	switch {
	case c.Iss != "accounts.google.com" && c.Iss != "https://accounts.google.com":
		return refused("wrong issuer")
	case len(c.Aud) != 1 || c.Aud[0] != t.ClientID:
		return refused("meant for another client")
	case c.Sub == "":
		return refused("no subject")
	case now.After(time.Unix(c.Exp, 0).Add(leeway)):
		return refused("expired")
	case time.Unix(c.Iat, 0).After(now.Add(leeway)):
		return refused("issued in the future")
	}
	return nil
}

// key returns the public key kid names, fetching Google's keys when the
// cached ones expired or don't hold kid.
func (t *IDTokens) key(ctx context.Context, kid string) (*rsa.PublicKey, error) {
	t.mu.Lock()
	defer t.mu.Unlock()
	now := t.now()
	key, ok := t.keys[kid]
	if ok && now.Before(t.expires) {
		return key, nil
	}
	if !ok && t.keys != nil && now.Before(t.expires) && now.Sub(t.fetchedAt) < refetchAfter {
		return nil, refused("unknown key")
	}
	if err := t.fetch(ctx, now); err != nil {
		return nil, err
	}
	if key, ok = t.keys[kid]; !ok {
		return nil, refused("unknown key")
	}
	return key, nil
}

// fetch replaces the cached keys. Call it with mu held.
func (t *IDTokens) fetch(ctx context.Context, now time.Time) error {
	url := t.CertsURL
	if url == "" {
		url = CertsURL
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return err
	}
	client := t.Client
	if client == nil {
		client = http.DefaultClient
	}
	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("fetching Google keys: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("fetching Google keys: status %d", resp.StatusCode)
	}
	var set struct {
		Keys []struct {
			Kid string `json:"kid"`
			Kty string `json:"kty"`
			N   string `json:"n"`
			E   string `json:"e"`
		} `json:"keys"`
	}
	if err := json.NewDecoder(io.LimitReader(resp.Body, maxCertsBytes)).Decode(&set); err != nil {
		return fmt.Errorf("reading Google keys: %w", err)
	}

	keys := map[string]*rsa.PublicKey{}
	for _, k := range set.Keys {
		if k.Kty != "RSA" {
			continue
		}
		n, errN := base64.RawURLEncoding.DecodeString(k.N)
		e, errE := base64.RawURLEncoding.DecodeString(k.E)
		if errN != nil || errE != nil || len(e) == 0 || len(e) > 4 {
			continue
		}
		keys[k.Kid] = &rsa.PublicKey{N: new(big.Int).SetBytes(n), E: int(new(big.Int).SetBytes(e).Int64())}
	}
	if len(keys) == 0 {
		return errors.New("google published no usable keys")
	}
	t.keys, t.fetchedAt, t.expires = keys, now, now.Add(maxAge(resp.Header.Get("Cache-Control")))
	return nil
}

func (t *IDTokens) now() time.Time {
	if t.Now != nil {
		return t.Now()
	}
	return time.Now()
}

// maxAge reads max-age from a Cache-Control header.
func maxAge(cacheControl string) time.Duration {
	for _, d := range strings.Split(cacheControl, ",") {
		if v, ok := strings.CutPrefix(strings.TrimSpace(d), "max-age="); ok {
			if s, err := strconv.Atoi(v); err == nil && s > 0 {
				return time.Duration(s) * time.Second
			}
		}
	}
	return defaultKeyLife
}

func decodePart(part string, v any) error {
	b, err := base64.RawURLEncoding.DecodeString(part)
	if err != nil {
		return err
	}
	return json.Unmarshal(b, v)
}

func refused(why string) error {
	return fmt.Errorf("google ID token %s: %w", why, domain.ErrUnauthenticated)
}

// Unconfigured is used when the server has no Google client ID. It refuses
// every sign-in with an error the server logs.
type Unconfigured struct{}

// VerifyIdentity always fails.
func (Unconfigured) VerifyIdentity(context.Context, string) (account.Identity, error) {
	return account.Identity{}, errors.New("google sign-in is not configured: set ASCON_GOOGLE_CLIENT_ID")
}
