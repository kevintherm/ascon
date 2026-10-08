package resolverule

import (
	"context"
	"encoding/json"
	"errors"
	"testing"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/rule"
	"github.com/kevintherm/ascon/backend/internal/usecase/memory"
)

func stored(d string, v int, fp string, st rule.Status) rule.Stored {
	return rule.Stored{
		Rule: rule.Rule{
			SchemaVersion: 1, Domain: d, Version: v, Fingerprint: fp,
			ChapterPage: rule.ChapterPage{URL: "x", Images: rule.Images{Selector: "img"}},
		},
		Status: st, CreatedAt: time.Unix(0, 0),
	}
}

func setup(t *testing.T) (*Service, *memory.Rules) {
	t.Helper()
	rules := memory.NewRules()
	ctx := context.Background()
	for _, s := range []rule.Stored{
		stored("glasslight.example", 1, "00000000000000ff", rule.Active),
		stored("glasslight.example", 2, "00000000000000ff", rule.Active),
		stored("ninthgate.example", 1, "ffff000000000000", rule.Active),
		stored("gone.example", 1, "", rule.Retired),
	} {
		if err := rules.Insert(ctx, s); err != nil {
			t.Fatal(err)
		}
	}
	return New(rules, memory.Signer{}), rules
}

func TestLookupByDomainReturnsLatestSigned(t *testing.T) {
	s, rules := setup(t)
	if _, err := rules.AddHealth(context.Background(), "glasslight.example", 2, rule.Health{Successes: 8}); err != nil {
		t.Fatal(err)
	}

	m, err := s.Lookup(context.Background(), "GlassLight.example", "")
	if err != nil {
		t.Fatal(err)
	}
	if m.MatchedBy != ByDomain || m.KeyID != "test" || string(m.Signature) != "sig:"+string(m.Payload) {
		t.Fatalf("match = %+v", m)
	}
	var r rule.Rule
	if err := json.Unmarshal(m.Payload, &r); err != nil {
		t.Fatal(err)
	}
	if r.Version != 2 || r.Confidence == nil || *r.Confidence != 0.9 {
		t.Fatalf("payload rule = v%d confidence %v, want v2 with 0.9", r.Version, r.Confidence)
	}
}

func TestLookupFallsBackToFingerprint(t *testing.T) {
	s, _ := setup(t)
	// Three bits away from glasslight's fingerprint.
	m, err := s.Lookup(context.Background(), "glasslight-mirror.example", "00000000000000f8")
	if err != nil {
		t.Fatal(err)
	}
	var r rule.Rule
	_ = json.Unmarshal(m.Payload, &r)
	if m.MatchedBy != ByFingerprint || r.Domain != "glasslight.example" {
		t.Fatalf("matched %s via %s, want glasslight.example via fingerprint", r.Domain, m.MatchedBy)
	}
}

func TestLookupNotFound(t *testing.T) {
	s, _ := setup(t)
	cases := []struct{ site, fp string }{
		{"unknown.example", ""},
		{"unknown.example", "0f0f0f0f0f0f0f0f"}, // too far from every rule
		{"gone.example", ""},                    // only a retired version
	}
	for _, c := range cases {
		if _, err := s.Lookup(context.Background(), c.site, c.fp); !errors.Is(err, domain.ErrNotFound) {
			t.Errorf("Lookup(%q, %q) error = %v, want ErrNotFound", c.site, c.fp, err)
		}
	}
}

func TestLookupValidates(t *testing.T) {
	s, _ := setup(t)
	for _, c := range []struct{ site, fp string }{{"not a host", ""}, {"a.example", "XYZ"}} {
		if _, err := s.Lookup(context.Background(), c.site, c.fp); !errors.Is(err, domain.ErrInvalid) {
			t.Errorf("Lookup(%q, %q) error = %v, want ErrInvalid", c.site, c.fp, err)
		}
	}
}
