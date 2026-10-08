// Package resolverule finds the rule for a site and signs it for the app.
package resolverule

import (
	"context"
	"encoding/json"
	"errors"

	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/rule"
)

// MaxFingerprintDistance is how many of the 64 simhash bits may differ for a
// rule to be reused on another domain.
const MaxFingerprintDistance = 4

// How a rule was found.
const (
	ByDomain      = "domain"
	ByFingerprint = "fingerprint"
)

// Match is a signed rule ready to send.
type Match struct {
	MatchedBy string
	Payload   []byte
	Signature []byte
	KeyID     string
}

// Service looks up rules.
type Service struct {
	rules  rule.Repository
	signer rule.Signer
}

// New returns the service.
func New(rules rule.Repository, signer rule.Signer) *Service {
	return &Service{rules: rules, signer: signer}
}

// Lookup returns the rule for site. With no rule for the domain, it falls
// back to the rule whose fingerprint is closest, if fingerprint is given and
// one is close enough. It returns domain.ErrNotFound otherwise.
func (s *Service) Lookup(ctx context.Context, site, fingerprint string) (Match, error) {
	site, ok := rule.NormalizeDomain(site)
	if !ok {
		return Match{}, domain.Invalidf("domain is not a valid host name")
	}
	if fingerprint != "" && !rule.ValidFingerprint(fingerprint) {
		return Match{}, domain.Invalidf("fingerprint must be 16 lowercase hex digits")
	}

	stored, err := s.rules.Latest(ctx, site)
	if err == nil {
		return s.sign(ctx, stored, ByDomain)
	}
	if !errors.Is(err, domain.ErrNotFound) || fingerprint == "" {
		return Match{}, err
	}

	candidates, err := s.rules.Fingerprinted(ctx)
	if err != nil {
		return Match{}, err
	}
	best, bestDistance := -1, MaxFingerprintDistance+1
	for i, c := range candidates {
		if d := rule.FingerprintDistance(fingerprint, c.Rule.Fingerprint); d >= 0 && d < bestDistance {
			best, bestDistance = i, d
		}
	}
	if best < 0 {
		return Match{}, domain.ErrNotFound
	}
	return s.sign(ctx, candidates[best], ByFingerprint)
}

// sign attaches the current confidence and signs the rule's JSON bytes.
func (s *Service) sign(ctx context.Context, stored rule.Stored, matchedBy string) (Match, error) {
	health, err := s.rules.Health(ctx, stored.Rule.Domain, stored.Rule.Version)
	if err != nil {
		return Match{}, err
	}
	r := stored.Rule
	confidence := health.Confidence()
	r.Confidence = &confidence

	payload, err := json.Marshal(r)
	if err != nil {
		return Match{}, err
	}
	sig, keyID := s.signer.Sign(payload)
	return Match{MatchedBy: matchedBy, Payload: payload, Signature: sig, KeyID: keyID}, nil
}
