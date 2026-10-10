package http

import (
	"bytes"
	"context"
	"crypto/ed25519"
	"encoding/base64"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/kevintherm/ascon/backend/internal/adapter/evaluator"
	"github.com/kevintherm/ascon/backend/internal/adapter/persistence/sqlite"
	"github.com/kevintherm/ascon/backend/internal/adapter/signing"
	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/account"
	"github.com/kevintherm/ascon/backend/internal/domain/rule"
	"github.com/kevintherm/ascon/backend/internal/usecase/generaterule"
	"github.com/kevintherm/ascon/backend/internal/usecase/registerdevice"
	"github.com/kevintherm/ascon/backend/internal/usecase/reportrule"
	"github.com/kevintherm/ascon/backend/internal/usecase/resolverule"
	"github.com/kevintherm/ascon/backend/internal/usecase/signin"
	"github.com/kevintherm/ascon/backend/internal/usecase/synclibrary"
)

const fixtures = "../../../../contracts/fixtures/conformance"

// fixtureGenerator answers with the glasslight fixture rule, standing in
// for a language model.
type fixtureGenerator struct{}

func (g fixtureGenerator) Generate(context.Context, string, string, []rule.Sample, []rule.Attempt) (rule.Rule, error) {
	var r rule.Rule
	b, err := os.ReadFile(filepath.Join(fixtures, "glasslight/rule.json"))
	if err != nil {
		return r, err
	}
	return r, json.Unmarshal(b, &r)
}

// googleStub accepts ID tokens of the form "google:<sub>".
type googleStub struct{}

func (googleStub) VerifyIdentity(_ context.Context, idToken string) (account.Identity, error) {
	sub, ok := strings.CutPrefix(idToken, "google:")
	if !ok {
		return account.Identity{}, domain.ErrUnauthenticated
	}
	return account.Identity{Provider: account.Google, Subject: sub}, nil
}

type env struct {
	t        *testing.T
	srv      *httptest.Server
	accounts *sqlite.Accounts
	pub      ed25519.PublicKey
}

func newEnv(t *testing.T, limits account.Limits) *env {
	t.Helper()
	ctx := context.Background()
	db, err := sqlite.Open(ctx, filepath.Join(t.TempDir(), "api.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = db.Close() })

	signer, err := signing.New(make([]byte, ed25519.SeedSize), "test")
	if err != nil {
		t.Fatal(err)
	}
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	rules := sqlite.NewRules(db)
	signIn := signin.New(googleStub{}, sqlite.NewAccounts(db), time.Now)
	api := &Server{
		Devices:  registerdevice.New(sqlite.NewDevices(db), time.Now),
		Accounts: signIn,
		SignIn:   signIn,
		Resolve:  resolverule.New(rules, signer),
		Reports:  reportrule.New(rules, time.Now),
		Generate: generaterule.New(rules, sqlite.NewCandidates(db), sqlite.NewQuotas(db),
			fixtureGenerator{}, evaluator.HTML{},
			generaterule.Config{Limits: limits, MinImages: 3, Timeout: 10 * time.Second}, time.Now, log),
		Sync: synclibrary.New(sqlite.NewSync(db), time.Now),
		Log:  log,
	}
	srv := httptest.NewServer(api.Handler())
	t.Cleanup(srv.Close)
	return &env{t: t, srv: srv, accounts: sqlite.NewAccounts(db), pub: signer.PublicKey()}
}

// reply is what a call returns; the body is already decoded and closed.
type reply struct {
	method, path string
	status       int
	header       http.Header
}

func (e *env) call(method, path, token string, body any, out any) reply {
	e.t.Helper()
	var rd io.Reader
	if body != nil {
		b, err := json.Marshal(body)
		if err != nil {
			e.t.Fatal(err)
		}
		rd = bytes.NewReader(b)
	}
	req, err := http.NewRequest(method, e.srv.URL+path, rd)
	if err != nil {
		e.t.Fatal(err)
	}
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		e.t.Fatal(err)
	}
	defer func() { _ = res.Body.Close() }()
	if out != nil {
		if err := json.NewDecoder(res.Body).Decode(out); err != nil {
			e.t.Fatalf("%s %s: decoding %d response: %v", method, path, res.StatusCode, err)
		}
	}
	return reply{method: method, path: path, status: res.StatusCode, header: res.Header}
}

func (e *env) expect(res reply, status int) {
	e.t.Helper()
	if res.status != status {
		e.t.Fatalf("%s %s = %d, want %d", res.method, res.path, res.status, status)
	}
}

func (e *env) newAccount(tier account.Tier) string {
	e.t.Helper()
	_, token, err := e.accounts.Create(context.Background(), tier, time.Now())
	if err != nil {
		e.t.Fatal(err)
	}
	return token
}

func TestRuleLifecycle(t *testing.T) {
	e := newEnv(t, account.Limits{account.Free: 10, account.Premium: 200})

	var dev deviceDTO
	e.expect(e.call("POST", "/v1/devices", "", map[string]string{"appVersion": "0.1.0"}, &dev), 201)

	e.expect(e.call("GET", "/v1/rules?domain=glasslight.example", dev.Token, nil, nil), 404)

	// A free account asks for a rule, sending the fixture chapter page.
	acct := e.newAccount(account.Free)
	page, err := os.ReadFile(filepath.Join(fixtures, "glasslight/chapter.html"))
	if err != nil {
		t.Fatal(err)
	}
	var cand candidateDTO
	res := e.call("POST", "/v1/rule-candidates", acct, map[string]any{
		"domain":  "glasslight.example",
		"samples": []map[string]string{{"url": "https://glasslight.example/manga/salt-and-ember/chapter-10-5/", "html": string(page)}},
	}, &cand)
	e.expect(res, 202)
	if res.header.Get("Location") != "/v1/rule-candidates/"+cand.CandidateID || cand.Status != "pending" || cand.RetryAfter == 0 {
		t.Fatalf("candidate = %+v, location %q", cand, res.header.Get("Location"))
	}

	deadline := time.Now().Add(5 * time.Second)
	for cand.Status == "pending" && time.Now().Before(deadline) {
		time.Sleep(20 * time.Millisecond)
		e.expect(e.call("GET", "/v1/rule-candidates/"+cand.CandidateID, acct, nil, &cand), 200)
	}
	if cand.Status != "accepted" || cand.Rule == nil {
		t.Fatalf("candidate = %+v, want accepted with a rule", cand)
	}

	// The device can now look the rule up, and the signature verifies.
	var match ruleMatchDTO
	e.expect(e.call("GET", "/v1/rules?domain=glasslight.example", dev.Token, nil, &match), 200)
	payload, _ := base64.RawURLEncoding.DecodeString(match.Rule.Payload)
	sig, _ := base64.RawURLEncoding.DecodeString(match.Rule.Signature)
	if !ed25519.Verify(e.pub, payload, sig) {
		t.Fatal("rule signature does not verify")
	}
	var r rule.Rule
	if err := json.Unmarshal(payload, &r); err != nil || r.Domain != "glasslight.example" || r.Version != 1 || match.MatchedBy != "domain" {
		t.Fatalf("rule = %+v via %s, %v", r, match.MatchedBy, err)
	}

	var q quotaDTO
	e.expect(e.call("GET", "/v1/quota", acct, nil, &q), 200)
	if q.Tier != "free" || q.Limit != 10 || q.Remaining != 9 {
		t.Fatalf("quota = %+v, want 9 of 10 left", q)
	}

	// Asking again for a domain that has a rule is a conflict.
	e.expect(e.call("POST", "/v1/rule-candidates", acct, map[string]any{
		"domain":  "glasslight.example",
		"samples": []map[string]string{{"url": "https://glasslight.example/x", "html": "x"}},
	}, nil), 409)

	e.expect(e.call("POST", "/v1/rules/glasslight.example/reports", dev.Token, map[string]any{
		"version": 1, "problem": "wrong_chapter", "url": "https://glasslight.example/manga/salt-and-ember/chapter-11/",
	}, nil), 202)
	e.expect(e.call("POST", "/v1/rules/health", dev.Token, map[string]any{
		"entries": []map[string]any{{"domain": "glasslight.example", "version": 1, "successes": 4, "emptyResults": 0, "backwardJumps": 0}},
	}, nil), 202)
}

func TestQuotaExceededAnswers429(t *testing.T) {
	e := newEnv(t, account.Limits{account.Free: 0, account.Premium: 200})
	acct := e.newAccount(account.Free)

	var p problem
	res := e.call("POST", "/v1/rule-candidates", acct, map[string]any{
		"domain":  "glasslight.example",
		"samples": []map[string]string{{"url": "https://glasslight.example/manga/a/chapter-1/", "html": "<p>"}},
	}, &p)
	e.expect(res, 429)
	if res.header.Get("Retry-After") == "" || p.Quota == nil || p.Quota.Remaining != 0 || res.header.Get("Content-Type") != "application/problem+json" {
		t.Fatalf("problem = %+v, Retry-After %q", p, res.header.Get("Retry-After"))
	}
}

func TestAuthBoundaries(t *testing.T) {
	e := newEnv(t, account.Limits{account.Free: 10})
	var dev deviceDTO
	e.expect(e.call("POST", "/v1/devices", "", map[string]string{"appVersion": "0.1.0"}, &dev), 201)

	e.expect(e.call("GET", "/v1/rules?domain=a.example", "", nil, nil), 401)
	// A device token is not an account token.
	e.expect(e.call("GET", "/v1/quota", dev.Token, nil, nil), 401)
	e.expect(e.call("GET", "/v1/sync/changes", dev.Token, nil, nil), 401)

	e.expect(e.call("DELETE", "/v1/devices/me", dev.Token, nil, nil), 204)
	e.expect(e.call("GET", "/v1/rules?domain=a.example", dev.Token, nil, nil), 401)

	e.expect(e.call("POST", "/v1/devices", "", map[string]any{"appVersion": "0.1.0", "extra": 1}, nil), 400)
}

func TestSyncRoundTrip(t *testing.T) {
	e := newEnv(t, account.Limits{account.Free: 10})
	acct := e.newAccount(account.Free)
	other := e.newAccount(account.Free)

	id := "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90"
	var pushed pushResultDTO
	e.expect(e.call("POST", "/v1/sync/changes", acct, map[string]any{"changes": []map[string]any{{
		"entity": "progress", "id": id,
		"fields": map[string]any{"chapter": map[string]any{"value": "10.5", "updatedAt": "2026-10-08T10:00:00Z"}},
	}}}, &pushed), 200)
	if pushed.Applied != 1 {
		t.Fatalf("applied = %d, want 1", pushed.Applied)
	}

	var page changePageDTO
	e.expect(e.call("GET", "/v1/sync/changes", acct, nil, &page), 200)
	if len(page.Changes) != 1 || page.Cursor != "1" || string(page.Changes[0].Fields["chapter"].Value) != `"10.5"` {
		t.Fatalf("page = %+v", page)
	}
	e.expect(e.call("GET", "/v1/sync/changes?since="+page.Cursor, acct, nil, &page), 200)
	if len(page.Changes) != 0 {
		t.Fatalf("second pull = %+v, want nothing new", page)
	}
	e.expect(e.call("GET", "/v1/sync/changes", other, nil, &page), 200)
	if len(page.Changes) != 0 {
		t.Fatal("another account pulled this account's changes")
	}

	e.expect(e.call("POST", "/v1/sync/changes", acct, map[string]any{"changes": []map[string]any{{
		"entity": "progress", "id": id,
		"fields": map[string]any{"chapter": map[string]any{"value": "10.50", "updatedAt": "2026-10-08T10:00:00Z"}},
	}}}, nil), 400)
}

func TestSignInAndOut(t *testing.T) {
	e := newEnv(t, account.Limits{account.Free: 10})

	var s sessionDTO
	e.expect(e.call("POST", "/v1/sessions", "", map[string]string{"idToken": "google:111"}, &s), 201)
	if s.Token == "" || s.AccountID == "" || s.Tier != "free" {
		t.Fatalf("session = %+v", s)
	}
	var q quotaDTO
	e.expect(e.call("GET", "/v1/quota", s.Token, nil, &q), 200)
	if q.Limit != 10 || q.Remaining != 10 {
		t.Fatalf("quota = %+v", q)
	}

	// The same Google user on another phone gets the same account.
	var again sessionDTO
	e.expect(e.call("POST", "/v1/sessions", "", map[string]string{"idToken": "google:111"}, &again), 201)
	if again.AccountID != s.AccountID || again.Token == s.Token {
		t.Fatalf("second sign-in = %+v, first %+v", again, s)
	}

	e.expect(e.call("DELETE", "/v1/sessions/current", s.Token, nil, nil), 204)
	e.expect(e.call("GET", "/v1/quota", s.Token, nil, nil), 401)
	e.expect(e.call("GET", "/v1/quota", again.Token, nil, nil), 200)

	e.expect(e.call("POST", "/v1/sessions", "", map[string]string{"idToken": "forged"}, nil), 401)
	e.expect(e.call("POST", "/v1/sessions", "", map[string]string{}, nil), 400)
	e.expect(e.call("DELETE", "/v1/sessions/current", "", nil, nil), 401)
}
