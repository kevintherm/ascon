// Package generaterule asks a language model for a rule for a site that has
// none, checks the result against the samples the app sent, and stores it.
// It needs an account and spends one unit of the account's monthly quota.
package generaterule

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/url"
	"sync"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/account"
	"github.com/kevintherm/ascon/backend/internal/domain/rule"
)

// Limits on what the app may send.
const (
	MaxSamples    = 3
	MaxSampleHTML = 256 << 10
	MaxURLLength  = 2048
)

// Config tunes generation.
type Config struct {
	// Limits are the monthly requests per tier. AGENTS.md has the starting
	// values; production tunes them.
	Limits account.Limits
	// MinImages is how many images each sample must yield.
	MinImages int
	// Timeout bounds one generation.
	Timeout time.Duration
}

// Request is what the app sends.
type Request struct {
	Domain      string
	Fingerprint string
	Samples     []rule.Sample
}

// Quota is an account's AI detection allowance this month.
type Quota struct {
	Tier      account.Tier
	Limit     int
	Remaining int
	ResetsAt  time.Time
}

// QuotaExceededError is returned when the account has no quota left.
type QuotaExceededError struct{ Quota Quota }

func (e *QuotaExceededError) Error() string {
	return fmt.Sprintf("AI detection quota used up until %s", e.Quota.ResetsAt.Format(time.RFC3339))
}

// Service runs generations.
type Service struct {
	rules      rule.Repository
	candidates rule.CandidateRepository
	quotas     account.QuotaRepository
	generator  rule.Generator
	evaluator  rule.Evaluator
	cfg        Config
	now        func() time.Time
	log        *slog.Logger

	// spawn runs a generation in the background. Tests replace it to run
	// generations synchronously.
	spawn func(func())

	mu       sync.Mutex
	inflight map[string]bool
}

// New returns the service.
func New(rules rule.Repository, candidates rule.CandidateRepository, quotas account.QuotaRepository,
	generator rule.Generator, evaluator rule.Evaluator, cfg Config, now func() time.Time, log *slog.Logger,
) *Service {
	return &Service{
		rules: rules, candidates: candidates, quotas: quotas, generator: generator, evaluator: evaluator,
		cfg: cfg, now: now, log: log,
		spawn:    func(f func()) { go f() },
		inflight: map[string]bool{},
	}
}

// Request starts a generation for the domain, or joins the one running.
// Joining costs no quota.
func (s *Service) Request(ctx context.Context, acc account.Account, req Request) (rule.Candidate, error) {
	site, err := validate(req)
	if err != nil {
		return rule.Candidate{}, err
	}

	_, err = s.rules.Latest(ctx, site)
	if err == nil {
		return rule.Candidate{}, fmt.Errorf("%w: a rule already exists for %s", domain.ErrConflict, site)
	}
	if !errors.Is(err, domain.ErrNotFound) {
		return rule.Candidate{}, err
	}

	now := s.now()
	c, joining, err := s.enqueue(ctx, acc, site, now)
	if err != nil {
		return rule.Candidate{}, err
	}
	if !joining {
		samples := req.Samples
		s.spawn(func() { s.generate(site, req.Fingerprint, samples, acc.ID, account.Period(now)) })
	}
	return c, nil
}

// enqueue records the candidate and spends quota unless a generation for the
// domain is already running. It holds the lock so two requests for the same
// domain cannot both start one.
func (s *Service) enqueue(ctx context.Context, acc account.Account, site string, now time.Time) (rule.Candidate, bool, error) {
	period := account.Period(now)

	s.mu.Lock()
	defer s.mu.Unlock()

	joining := s.inflight[site]
	if !joining {
		limit := s.cfg.Limits[acc.Tier]
		used, ok, err := s.quotas.Use(ctx, acc.ID, period, limit)
		if err != nil {
			return rule.Candidate{}, false, err
		}
		if !ok {
			return rule.Candidate{}, false, &QuotaExceededError{Quota: quota(acc.Tier, limit, used, now)}
		}
	}

	c := rule.Candidate{
		ID: domain.NewID(), Domain: site, AccountID: acc.ID, Status: rule.Pending,
		CreatedAt: now, UpdatedAt: now,
	}
	if err := s.candidates.Insert(ctx, c); err != nil {
		if !joining {
			_ = s.quotas.Refund(ctx, acc.ID, period)
		}
		return rule.Candidate{}, false, err
	}
	if !joining {
		s.inflight[site] = true
	}
	return c, joining, nil
}

// Recover rejects candidates left pending by a previous run of the server.
// Call it once at start, before serving requests.
func (s *Service) Recover(ctx context.Context) error {
	n, err := s.candidates.AbandonPending(ctx, "server restarted during generation, please retry", s.now())
	if n > 0 {
		s.log.Warn("abandoned pending rule generations", "count", n)
	}
	return err
}

// Candidate returns one of the account's requests.
func (s *Service) Candidate(ctx context.Context, acc account.Account, id string) (rule.Candidate, error) {
	return s.candidates.Get(ctx, id, acc.ID)
}

// Quota returns the account's allowance this month.
func (s *Service) Quota(ctx context.Context, acc account.Account) (Quota, error) {
	now := s.now()
	used, err := s.quotas.Used(ctx, acc.ID, account.Period(now))
	if err != nil {
		return Quota{}, err
	}
	return quota(acc.Tier, s.cfg.Limits[acc.Tier], used, now), nil
}

func quota(tier account.Tier, limit, used int, now time.Time) Quota {
	return Quota{Tier: tier, Limit: limit, Remaining: max(limit-used, 0), ResetsAt: account.PeriodEnd(now)}
}

// generate runs in the background and settles every pending candidate for
// the domain. A failed generation gives the requester's quota back.
func (s *Service) generate(site, fingerprint string, samples []rule.Sample, accountID, period string) {
	defer func() {
		s.mu.Lock()
		delete(s.inflight, site)
		s.mu.Unlock()
	}()

	ctx, cancel := context.WithTimeout(context.Background(), s.cfg.Timeout)
	defer cancel()

	version, reason, err := s.generateAndStore(ctx, site, fingerprint, samples)
	status := rule.Accepted
	if err != nil || reason != "" {
		status = rule.Rejected
		if err != nil {
			s.log.Error("rule generation failed", "domain", site, "err", err)
			reason = "generation failed"
		}
		if err := s.quotas.Refund(ctx, accountID, period); err != nil {
			s.log.Error("quota refund failed", "account", accountID, "err", err)
		}
	}
	if err := s.candidates.Resolve(ctx, site, status, reason, version, s.now()); err != nil {
		s.log.Error("resolving candidates failed", "domain", site, "err", err)
	}
}

// generateAndStore returns the stored version, or a reason the rule was
// rejected, or an error for failures that are not the rule's fault.
func (s *Service) generateAndStore(ctx context.Context, site, fingerprint string, samples []rule.Sample) (int, string, error) {
	r, err := s.generator.Generate(ctx, site, fingerprint, samples)
	if err != nil {
		return 0, "", err
	}
	if reason := Check(s.evaluator, r, samples, s.cfg.MinImages); reason != "" {
		return 0, reason, nil
	}

	maxVersion, err := s.rules.MaxVersion(ctx, site)
	if err != nil {
		return 0, "", err
	}
	r.SchemaVersion = 1
	r.Domain = site
	r.Version = maxVersion + 1
	r.Confidence = nil
	if r.Fingerprint == "" {
		r.Fingerprint = fingerprint
	}
	if err := s.rules.Insert(ctx, rule.Stored{Rule: r, Status: rule.Active, CreatedAt: s.now()}); err != nil {
		return 0, "", err
	}
	return r.Version, "", nil
}

// Check runs a generated rule against every sample and returns why it fails,
// or "" when it passes. Every sample must be read as a chapter with a title,
// a chapter number and at least minImages images. Titles must agree and
// chapter numbers must differ across samples.
func Check(ev rule.Evaluator, r rule.Rule, samples []rule.Sample, minImages int) string {
	var title string
	chapters := map[string]bool{}
	for i, s := range samples {
		n := i + 1
		res, err := ev.Evaluate(r, s.URL, s.HTML)
		if err != nil {
			return fmt.Sprintf("rule does not run on sample %d: %v", n, err)
		}
		if res.PageType != rule.PageChapter {
			return fmt.Sprintf("sample %d is not read as a chapter page", n)
		}
		c := res.Chapter
		switch {
		case c.Title == nil:
			return fmt.Sprintf("no title on sample %d", n)
		case c.Chapter == nil:
			return fmt.Sprintf("no chapter number on sample %d", n)
		case len(c.Images) < minImages:
			return fmt.Sprintf("only %d images on sample %d, need %d", len(c.Images), n, minImages)
		}
		if i == 0 {
			title = *c.Title
		} else if *c.Title != title {
			return fmt.Sprintf("titles differ: %q and %q", title, *c.Title)
		}
		if chapters[*c.Chapter] {
			return fmt.Sprintf("two samples read as chapter %s", *c.Chapter)
		}
		chapters[*c.Chapter] = true
	}
	return ""
}

func validate(req Request) (string, error) {
	site, ok := rule.NormalizeDomain(req.Domain)
	if !ok {
		return "", domain.Invalidf("domain is not a valid host name")
	}
	if req.Fingerprint != "" && !rule.ValidFingerprint(req.Fingerprint) {
		return "", domain.Invalidf("fingerprint must be 16 lowercase hex digits")
	}
	if len(req.Samples) == 0 || len(req.Samples) > MaxSamples {
		return "", domain.Invalidf("send 1 to %d samples", MaxSamples)
	}
	for i, s := range req.Samples {
		if len(s.URL) > MaxURLLength {
			return "", domain.Invalidf("sample %d url is too long", i+1)
		}
		u, err := url.Parse(s.URL)
		if err != nil || (u.Scheme != "http" && u.Scheme != "https") {
			return "", domain.Invalidf("sample %d url is not an http or https URL", i+1)
		}
		if host, _ := rule.NormalizeDomain(u.Hostname()); host != site {
			return "", domain.Invalidf("sample %d is not on %s", i+1, site)
		}
		if len(s.HTML) == 0 || len(s.HTML) > MaxSampleHTML {
			return "", domain.Invalidf("sample %d html must be 1 byte to 256 KiB", i+1)
		}
	}
	return site, nil
}
