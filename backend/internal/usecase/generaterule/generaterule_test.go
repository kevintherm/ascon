package generaterule

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"strings"
	"testing"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/account"
	"github.com/kevintherm/ascon/backend/internal/domain/rule"
	"github.com/kevintherm/ascon/backend/internal/usecase/memory"
)

var now = time.Date(2026, 10, 8, 10, 0, 0, 0, time.UTC)

type fakeGenerator struct {
	err   error
	calls int
	// urls are the chapter URL patterns of the rules offered in turn. The
	// last one repeats. "none" makes fakeEvaluator read no chapter.
	urls     []string
	previous [][]rule.Attempt
}

func (g *fakeGenerator) Generate(_ context.Context, _, _ string, _ []rule.Sample, previous []rule.Attempt) (rule.Rule, error) {
	g.calls++
	g.previous = append(g.previous, previous)
	if g.err != nil {
		return rule.Rule{}, g.err
	}
	url := ".*"
	if len(g.urls) > 0 {
		url = g.urls[min(g.calls, len(g.urls))-1]
	}
	return rule.Rule{ChapterPage: rule.ChapterPage{URL: url, Images: rule.Images{Selector: "img"}}}, nil
}

// fakeEvaluator reads the chapter number, title and image count from the
// sample HTML, written as "chapter|title|images", optionally followed by
// "|next|previous" link URLs. Without a page, as for a link, it reads a URL
// like https://host/<series>/c/<chapter>.
type fakeEvaluator struct{}

func (fakeEvaluator) Evaluate(r rule.Rule, pageURL string, page []byte) (rule.Result, error) {
	if r.ChapterPage.URL == "none" {
		return rule.Result{PageType: rule.PageNone}, nil
	}
	if page == nil {
		parts := strings.Split(strings.TrimPrefix(pageURL, "https://"), "/")
		if len(parts) != 4 || parts[2] != "c" {
			return rule.Result{PageType: rule.PageNone}, nil
		}
		return rule.Result{PageType: rule.PageChapter, Chapter: &rule.ChapterResult{Series: &parts[1], Chapter: &parts[3]}}, nil
	}
	parts := strings.Split(string(page), "|")
	if len(parts) != 3 && len(parts) != 5 {
		return rule.Result{PageType: rule.PageNone}, nil
	}
	series := "s"
	c := &rule.ChapterResult{Series: &series, Chapter: &parts[0], Title: &parts[1], Images: make([]string, len(parts[2]))}
	if len(parts) == 5 {
		c.Next, c.Previous = link(parts[3]), link(parts[4])
	}
	return rule.Result{PageType: rule.PageChapter, Chapter: c}, nil
}

func link(u string) *string {
	if u == "" {
		return nil
	}
	return &u
}

type harness struct {
	s          *Service
	rules      *memory.Rules
	candidates *memory.Candidates
	quotas     *memory.Quotas
	generator  *fakeGenerator
	queued     []func()
}

func setup(t *testing.T, limits account.Limits) *harness {
	t.Helper()
	h := &harness{
		rules: memory.NewRules(), candidates: memory.NewCandidates(), quotas: memory.NewQuotas(),
		generator: &fakeGenerator{},
	}
	h.s = New(h.rules, h.candidates, h.quotas, h.generator, fakeEvaluator{},
		Config{Limits: limits, MinImages: 3, Timeout: time.Minute},
		func() time.Time { return now }, slog.New(slog.NewTextHandler(io.Discard, nil)))
	// Queue generations so tests decide when they run.
	h.s.spawn = func(f func()) { h.queued = append(h.queued, f) }
	return h
}

func (h *harness) runQueued() {
	for _, f := range h.queued {
		f()
	}
	h.queued = nil
}

var (
	free    = account.Account{ID: "acc-free", Tier: account.Free}
	premium = account.Account{ID: "acc-premium", Tier: account.Premium}
	limits  = account.Limits{account.Free: 1, account.Premium: 5}
)

func request(site string, pages ...string) Request {
	r := Request{Domain: site}
	for i, p := range pages {
		r.Samples = append(r.Samples, rule.Sample{URL: "https://" + site + "/c/" + string(rune('1'+i)), HTML: []byte(p)})
	}
	return r
}

func TestGenerationAcceptedAndStored(t *testing.T) {
	ctx := context.Background()
	h := setup(t, limits)

	c, err := h.s.Request(ctx, free, request("a.example", "10|Salt|xxx", "11|Salt|xxxx"))
	if err != nil {
		t.Fatal(err)
	}
	if c.Status != rule.Pending {
		t.Fatalf("status before running = %s, want pending", c.Status)
	}
	h.runQueued()

	got, _ := h.s.Candidate(ctx, free, c.ID)
	if got.Status != rule.Accepted || got.RuleVersion != 1 {
		t.Fatalf("candidate = %+v, want accepted v1", got)
	}
	stored, err := h.rules.Latest(ctx, "a.example")
	if err != nil || stored.Rule.Domain != "a.example" || stored.Rule.SchemaVersion != 1 {
		t.Fatalf("stored rule = %+v, %v", stored.Rule, err)
	}
	if q, _ := h.s.Quota(ctx, free); q.Remaining != 0 {
		t.Fatalf("remaining = %d, want 0 after one accepted generation", q.Remaining)
	}
}

func TestRejectedGenerationRefundsQuota(t *testing.T) {
	ctx := context.Background()
	cases := []struct {
		name   string
		pages  []string
		genErr error
		reason string
	}{
		{"titles differ", []string{"10|Salt|xxx", "11|Ember|xxx"}, nil, "titles differ"},
		{"same chapter twice", []string{"10|Salt|xxx", "10|Salt|xxx"}, nil, "two samples read as chapter 10"},
		{"too few images", []string{"10|Salt|xx"}, nil, "only 2 images"},
		{"not a chapter", []string{"nothing"}, nil, "not read as a chapter"},
		{"model fails", []string{"10|Salt|xxx"}, errors.New("timeout"), "generation failed"},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			h := setup(t, limits)
			h.generator.err = c.genErr
			cand, err := h.s.Request(ctx, free, request("a.example", c.pages...))
			if err != nil {
				t.Fatal(err)
			}
			h.runQueued()

			got, _ := h.s.Candidate(ctx, free, cand.ID)
			if got.Status != rule.Rejected || !strings.Contains(got.Reason, c.reason) {
				t.Fatalf("candidate = %s %q, want rejected containing %q", got.Status, got.Reason, c.reason)
			}
			if q, _ := h.s.Quota(ctx, free); q.Remaining != 1 {
				t.Fatalf("remaining = %d, want the quota refunded", q.Remaining)
			}
			if _, err := h.rules.Latest(ctx, "a.example"); !errors.Is(err, domain.ErrNotFound) {
				t.Fatal("a rejected rule was stored")
			}
		})
	}
}

func TestLinksMustLeadToTheNeighboringChapters(t *testing.T) {
	ev := fakeEvaluator{}
	r := rule.Rule{ChapterPage: rule.ChapterPage{URL: ".*"}}
	sample := func(next, previous string) []rule.Sample {
		return []rule.Sample{{URL: "https://a.example/s/c/10", HTML: []byte("10|Salt|xxx|" + next + "|" + previous)}}
	}
	cases := []struct {
		name, next, previous, want string
	}{
		{"both right", "https://a.example/s/c/11", "https://a.example/s/c/9.5", ""},
		{"none found", "", "", ""},
		{"next goes back", "https://a.example/s/c/1", "", "the next link on sample 1, chapter 10, goes to chapter 1"},
		{"next is this chapter", "https://a.example/s/c/10", "", "goes to chapter 10"},
		{"previous goes ahead", "", "https://a.example/s/c/11", "the previous link on sample 1, chapter 10, goes to chapter 11"},
		{"previous is the series page", "", "https://a.example/s", "is not a chapter page"},
		{"next is another series", "https://a.example/t/c/11", "", "is in another series"},
	}
	for _, c := range cases {
		got := Check(ev, r, sample(c.next, c.previous), 3)
		if c.want == "" && got != "" || c.want != "" && !strings.Contains(got, c.want) {
			t.Errorf("%s: Check = %q, want %q", c.name, got, c.want)
		}
	}
}

func TestFailedRuleIsRetriedWithItsProblem(t *testing.T) {
	ctx := context.Background()
	h := setup(t, limits)
	h.s.cfg.Attempts = 2
	h.generator.urls = []string{"none", ".*"}

	c, _ := h.s.Request(ctx, free, request("a.example", "10|Salt|xxx"))
	h.runQueued()

	if got, _ := h.s.Candidate(ctx, free, c.ID); got.Status != rule.Accepted {
		t.Fatalf("candidate = %s %q, want accepted on the second attempt", got.Status, got.Reason)
	}
	if len(h.generator.previous) != 2 || len(h.generator.previous[0]) != 0 {
		t.Fatalf("previous attempts = %+v", h.generator.previous)
	}
	retry := h.generator.previous[1]
	if len(retry) != 1 || retry[0].Rule.ChapterPage.URL != "none" || !strings.Contains(retry[0].Problem, "not read as a chapter") {
		t.Fatalf("second call was told %+v", retry)
	}
}

func TestRejectedAfterTheLastAttempt(t *testing.T) {
	ctx := context.Background()
	h := setup(t, limits)
	h.s.cfg.Attempts = 2
	h.generator.urls = []string{"none"}

	c, _ := h.s.Request(ctx, free, request("a.example", "10|Salt|xxx"))
	h.runQueued()

	got, _ := h.s.Candidate(ctx, free, c.ID)
	if got.Status != rule.Rejected || h.generator.calls != 2 {
		t.Fatalf("candidate = %s after %d calls, want rejected after 2", got.Status, h.generator.calls)
	}
}

func TestNonPortableRuleIsRejected(t *testing.T) {
	ctx := context.Background()
	h := setup(t, limits)
	h.generator.urls = []string{"(?i).*"}

	c, _ := h.s.Request(ctx, free, request("a.example", "10|Salt|xxx"))
	h.runQueued()

	if got, _ := h.s.Candidate(ctx, free, c.ID); got.Status != rule.Rejected || !strings.Contains(got.Reason, "chapterPage.url") {
		t.Fatalf("candidate = %s %q, want rejected naming chapterPage.url", got.Status, got.Reason)
	}
}

func TestQuotaExceeded(t *testing.T) {
	ctx := context.Background()
	h := setup(t, limits)
	if _, err := h.s.Request(ctx, free, request("a.example", "10|Salt|xxx")); err != nil {
		t.Fatal(err)
	}
	h.runQueued()

	_, err := h.s.Request(ctx, free, request("b.example", "10|Salt|xxx"))
	var qe *QuotaExceededError
	if !errors.As(err, &qe) {
		t.Fatalf("error = %v, want QuotaExceededError", err)
	}
	if qe.Quota.Limit != 1 || qe.Quota.Remaining != 0 || !qe.Quota.ResetsAt.Equal(time.Date(2026, 11, 1, 0, 0, 0, 0, time.UTC)) {
		t.Fatalf("quota = %+v", qe.Quota)
	}
	if _, err := h.s.Request(ctx, premium, request("b.example", "10|Salt|xxx")); err != nil {
		t.Fatalf("premium account: %v", err)
	}
}

func TestJoiningARunningGenerationIsFree(t *testing.T) {
	ctx := context.Background()
	h := setup(t, limits)

	first, err := h.s.Request(ctx, free, request("a.example", "10|Salt|xxx"))
	if err != nil {
		t.Fatal(err)
	}
	second, err := h.s.Request(ctx, premium, request("a.example", "11|Salt|xxx"))
	if err != nil {
		t.Fatal(err)
	}
	if len(h.queued) != 1 {
		t.Fatalf("%d generations queued, want 1 shared run", len(h.queued))
	}
	if q, _ := h.s.Quota(ctx, premium); q.Remaining != 5 {
		t.Fatalf("joiner remaining = %d, want 5 untouched", q.Remaining)
	}

	h.runQueued()
	for _, c := range []struct {
		acc account.Account
		id  string
	}{{free, first.ID}, {premium, second.ID}} {
		if got, _ := h.s.Candidate(ctx, c.acc, c.id); got.Status != rule.Accepted {
			t.Fatalf("candidate %s = %s, want accepted", c.id, got.Status)
		}
	}
	if h.generator.calls != 1 {
		t.Fatalf("model called %d times, want 1", h.generator.calls)
	}
}

func TestRequestRejectsExistingRuleAndBadSamples(t *testing.T) {
	ctx := context.Background()
	h := setup(t, limits)
	if err := h.rules.Insert(ctx, rule.Stored{Rule: rule.Rule{Domain: "a.example", Version: 1}, Status: rule.Active}); err != nil {
		t.Fatal(err)
	}
	if _, err := h.s.Request(ctx, free, request("a.example", "10|Salt|xxx")); !errors.Is(err, domain.ErrConflict) {
		t.Fatalf("existing rule: %v, want ErrConflict", err)
	}

	bad := request("b.example", "10|Salt|xxx")
	bad.Samples[0].URL = "https://elsewhere.example/c/1"
	if _, err := h.s.Request(ctx, free, bad); !errors.Is(err, domain.ErrInvalid) {
		t.Fatalf("sample on another host: %v, want ErrInvalid", err)
	}
	if _, err := h.s.Request(ctx, free, Request{Domain: "b.example"}); !errors.Is(err, domain.ErrInvalid) {
		t.Fatalf("no samples: %v, want ErrInvalid", err)
	}
	if q, _ := h.s.Quota(ctx, free); q.Remaining != 1 {
		t.Fatalf("remaining = %d, want no quota spent on refused requests", q.Remaining)
	}
}

func TestCandidateBelongsToItsAccount(t *testing.T) {
	ctx := context.Background()
	h := setup(t, limits)
	c, _ := h.s.Request(ctx, free, request("a.example", "10|Salt|xxx"))
	if _, err := h.s.Candidate(ctx, premium, c.ID); !errors.Is(err, domain.ErrNotFound) {
		t.Fatalf("other account: %v, want ErrNotFound", err)
	}
}
