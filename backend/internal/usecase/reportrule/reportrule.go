// Package reportrule records what devices say about rules: user reports from
// the fix-detection sheet and batched extraction counts. Either can mark a
// rule suspect, which queues it for regeneration.
package reportrule

import (
	"context"
	"encoding/json"
	"errors"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/rule"
)

// Thresholds for marking a rule suspect.
const (
	// ReportingDevices is how many different devices must report a rule
	// version before it is suspect, so one confused user cannot break it.
	ReportingDevices = 3
	// MinExtractions is how many extractions must be counted before
	// confidence alone can make a rule suspect.
	MinExtractions = 20
	// MinConfidence is the confidence below which a rule is suspect.
	MinConfidence = 0.4
)

// Service records reports and health.
type Service struct {
	rules rule.Repository
	now   func() time.Time
}

// New returns the service.
func New(rules rule.Repository, now func() time.Time) *Service {
	return &Service{rules: rules, now: now}
}

// Report is what the app sends from the fix-detection sheet.
type Report struct {
	Version    int
	Problem    string
	URL        string
	Correction json.RawMessage
}

// Report records one device's report against a rule version.
func (s *Service) Report(ctx context.Context, deviceID, site string, r Report) error {
	site, ok := rule.NormalizeDomain(site)
	if !ok {
		return domain.Invalidf("domain is not a valid host name")
	}
	if !rule.Problems[r.Problem] {
		return domain.Invalidf("unknown problem %q", r.Problem)
	}
	if r.URL == "" || len(r.URL) > 2048 {
		return domain.Invalidf("url must be 1 to 2048 characters")
	}
	stored, err := s.rules.Get(ctx, site, r.Version)
	if err != nil {
		return err
	}

	devices, err := s.rules.AddReport(ctx, rule.Report{
		Domain: site, Version: r.Version, DeviceID: deviceID, Problem: r.Problem,
		URL: r.URL, Correction: r.Correction, CreatedAt: s.now(),
	})
	if err != nil {
		return err
	}
	if devices >= ReportingDevices && stored.Status == rule.Active {
		return s.rules.SetStatus(ctx, site, r.Version, rule.Suspect)
	}
	return nil
}

// Entry is one rule version's counts in a health batch.
type Entry struct {
	Domain  string
	Version int
	Counts  rule.Health
}

// RecordHealth adds a batch of counts. Entries for unknown rules are skipped,
// since the rule may have been retired since the device used it.
func (s *Service) RecordHealth(ctx context.Context, entries []Entry) error {
	for _, e := range entries {
		if e.Counts.Successes < 0 || e.Counts.EmptyResults < 0 || e.Counts.BackwardJumps < 0 {
			return domain.Invalidf("counts must not be negative")
		}
	}
	for _, e := range entries {
		site, ok := rule.NormalizeDomain(e.Domain)
		if !ok || e.Counts.Total() == 0 {
			continue
		}
		stored, err := s.rules.Get(ctx, site, e.Version)
		if errors.Is(err, domain.ErrNotFound) {
			continue
		}
		if err != nil {
			return err
		}
		total, err := s.rules.AddHealth(ctx, site, e.Version, e.Counts)
		if err != nil {
			return err
		}
		if stored.Status == rule.Active && total.Total() >= MinExtractions && total.Confidence() < MinConfidence {
			if err := s.rules.SetStatus(ctx, site, e.Version, rule.Suspect); err != nil {
				return err
			}
		}
	}
	return nil
}
