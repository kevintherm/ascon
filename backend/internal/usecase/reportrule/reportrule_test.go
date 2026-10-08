package reportrule

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/rule"
	"github.com/kevintherm/ascon/backend/internal/usecase/memory"
)

func setup(t *testing.T) (*Service, *memory.Rules) {
	t.Helper()
	rules := memory.NewRules()
	err := rules.Insert(context.Background(), rule.Stored{
		Rule:   rule.Rule{SchemaVersion: 1, Domain: "a.example", Version: 1},
		Status: rule.Active,
	})
	if err != nil {
		t.Fatal(err)
	}
	return New(rules, func() time.Time { return time.Unix(0, 0) }), rules
}

func TestReportsFromSeveralDevicesMarkSuspect(t *testing.T) {
	ctx := context.Background()
	s, rules := setup(t)
	report := Report{Version: 1, Problem: "no_images", URL: "https://a.example/c/1"}

	for i := 0; i < 5; i++ {
		if err := s.Report(ctx, "device-1", "a.example", report); err != nil {
			t.Fatal(err)
		}
	}
	if got := rules.Status("a.example", 1); got != rule.Active {
		t.Fatalf("one device reporting five times: status = %s, want active", got)
	}

	for _, d := range []string{"device-2", "device-3"} {
		if err := s.Report(ctx, d, "a.example", report); err != nil {
			t.Fatal(err)
		}
	}
	if got := rules.Status("a.example", 1); got != rule.Suspect {
		t.Fatalf("three devices: status = %s, want suspect", got)
	}
}

func TestReportValidates(t *testing.T) {
	s, _ := setup(t)
	ctx := context.Background()
	if err := s.Report(ctx, "d", "a.example", Report{Version: 1, Problem: "bored", URL: "u"}); !errors.Is(err, domain.ErrInvalid) {
		t.Errorf("unknown problem: %v, want ErrInvalid", err)
	}
	if err := s.Report(ctx, "d", "a.example", Report{Version: 9, Problem: "other", URL: "u"}); !errors.Is(err, domain.ErrNotFound) {
		t.Errorf("unknown version: %v, want ErrNotFound", err)
	}
}

func TestHealthMarksSuspectOnlyWithEnoughData(t *testing.T) {
	ctx := context.Background()
	s, rules := setup(t)

	bad := []Entry{{Domain: "a.example", Version: 1, Counts: rule.Health{Successes: 1, EmptyResults: 9}}}
	if err := s.RecordHealth(ctx, bad); err != nil {
		t.Fatal(err)
	}
	if got := rules.Status("a.example", 1); got != rule.Active {
		t.Fatalf("10 extractions: status = %s, want active until %d", got, MinExtractions)
	}
	if err := s.RecordHealth(ctx, bad); err != nil {
		t.Fatal(err)
	}
	if got := rules.Status("a.example", 1); got != rule.Suspect {
		t.Fatalf("20 mostly failed extractions: status = %s, want suspect", got)
	}
}

func TestHealthSkipsUnknownRules(t *testing.T) {
	s, _ := setup(t)
	err := s.RecordHealth(context.Background(), []Entry{{Domain: "other.example", Version: 1, Counts: rule.Health{Successes: 1}}})
	if err != nil {
		t.Fatalf("unknown rule: %v, want skipped", err)
	}
}
