// Package generaterule asks a language model for a rule for a site that has
// none, checks the result against the samples the app sent, and stores it.
// It needs an account and spends one unit of the account's monthly quota.
package generaterule

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"math/big"
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
	// Timeout bounds one generation, all attempts included.
	Timeout time.Duration
	// Attempts is how many rules the model may offer before the request is
	// rejected. Each retry is told why the previous rule failed. Zero means
	// one.
	Attempts int
	// FailureCooldown is how long a domain the model wrote no usable rule
	// for is turned away without asking it again. Zero turns it off.
	// Failures of the provider itself don't count.
	FailureCooldown time.Duration
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
	if c, failed, err := s.recentlyFailed(ctx, acc, site, now); failed || err != nil {
		return c, err
	}
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

// recentlyFailed answers with a rejected candidate, costing no quota, when
// generation for site failed within the cooldown.
func (s *Service) recentlyFailed(ctx context.Context, acc account.Account, site string, now time.Time) (rule.Candidate, bool, error) {
	if s.cfg.FailureCooldown <= 0 {
		return rule.Candidate{}, false, nil
	}
	last, err := s.candidates.LastFailure(ctx, site)
	if err != nil || last.IsZero() || !now.Before(last.Add(s.cfg.FailureCooldown)) {
		return rule.Candidate{}, false, err
	}
	c := rule.Candidate{
		ID: domain.NewID(), Domain: site, AccountID: acc.ID, Status: rule.Rejected,
		Reason: fmt.Sprintf("no rule could be generated for this site recently; try again after %s",
			last.Add(s.cfg.FailureCooldown).UTC().Format(time.RFC3339)),
		CreatedAt: now, UpdatedAt: now,
	}
	return c, true, s.candidates.Insert(ctx, c)
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
		} else if err := s.candidates.RecordFailure(ctx, site, reason, s.now()); err != nil {
			s.log.Error("recording the failure failed", "domain", site, "err", err)
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
	r, reason, err := s.attempt(ctx, site, fingerprint, samples)
	if err != nil || reason != "" {
		return 0, reason, err
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

// attempt asks for rules until one passes Check or the attempts run out, and
// returns the passing rule or the last reason.
func (s *Service) attempt(ctx context.Context, site, fingerprint string, samples []rule.Sample) (rule.Rule, string, error) {
	var previous []rule.Attempt
	for range max(s.cfg.Attempts, 1) {
		r, err := s.generator.Generate(ctx, site, fingerprint, samples, previous)
		var reason string
		switch {
		case errors.Is(err, rule.ErrBadAnswer):
			reason = err.Error()
		case err != nil:
			return rule.Rule{}, "", err
		default:
			reason = Check(s.evaluator, r, samples, s.cfg.MinImages)
		}
		if reason == "" {
			return r, "", nil
		}
		s.log.Info("generated rule failed the check", "domain", site, "attempt", len(previous)+1, "reason", reason)
		previous = append(previous, rule.Attempt{Rule: r, Problem: reason})
	}
	return rule.Rule{}, previous[len(previous)-1].Problem, nil
}

// Check runs a generated rule against every sample and returns why it fails,
// or "" when it passes. Every sample must be read as a chapter with a title,
// a chapter number and at least minImages images. Titles must agree and
// chapter numbers must differ across samples. A next or previous link, when
// found, must be a chapter page of the same series, later or earlier. The rule
// may use only what both evaluators read the same way.
func Check(ev rule.Evaluator, r rule.Rule, samples []rule.Sample, minImages int) string {
	if err := rule.Portable(r); err != nil {
		return err.Error()
	}
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
		if reason := checkLinks(ev, r, c, n); reason != "" {
			return reason
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

// checkLinks reads each link of sample n as the rule would read the linked
// page's URL. The linked page itself is not fetched, so its chapter number
// comes from the URL's chapter group; without one, only the page type and
// series are checked.
func checkLinks(ev rule.Evaluator, r rule.Rule, c *rule.ChapterResult, n int) string {
	links := []struct {
		name  string
		url   *string
		later bool
	}{{"next", c.Next, true}, {"previous", c.Previous, false}}
	here, _ := new(big.Rat).SetString(*c.Chapter)
	for _, l := range links {
		if l.url == nil {
			continue
		}
		res, err := ev.Evaluate(r, *l.url, nil)
		if err != nil || res.PageType != rule.PageChapter {
			return fmt.Sprintf("the %s link on sample %d, %s, is not a chapter page by chapterPage.url", l.name, n, *l.url)
		}
		linked := res.Chapter
		if c.Series != nil && linked.Series != nil && *c.Series != *linked.Series {
			return fmt.Sprintf("the %s link on sample %d, %s, is in another series", l.name, n, *l.url)
		}
		if linked.Chapter == nil || here == nil {
			continue
		}
		there, ok := new(big.Rat).SetString(*linked.Chapter)
		if !ok {
			continue
		}
		if cmp := there.Cmp(here); l.later && cmp <= 0 || !l.later && cmp >= 0 {
			return fmt.Sprintf("the %s link on sample %d, chapter %s, goes to chapter %s", l.name, n, *c.Chapter, *linked.Chapter)
		}
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
