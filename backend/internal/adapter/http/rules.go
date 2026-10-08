package http

import (
	"encoding/base64"
	"encoding/json"
	"net/http"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain/rule"
	"github.com/kevintherm/ascon/backend/internal/usecase/generaterule"
	"github.com/kevintherm/ascon/backend/internal/usecase/reportrule"
	"github.com/kevintherm/ascon/backend/internal/usecase/resolverule"
)

// How long the app may cache a looked-up rule before asking again.
const ruleCacheSeconds = "86400"

// How long the app should wait between polls of a pending candidate.
const candidatePollSeconds = 2

type signedRuleDTO struct {
	Payload   string `json:"payload"`
	Signature string `json:"signature"`
	KeyID     string `json:"keyId"`
}

type ruleMatchDTO struct {
	MatchedBy string        `json:"matchedBy"`
	Rule      signedRuleDTO `json:"rule"`
}

func toSignedRule(m resolverule.Match) signedRuleDTO {
	enc := base64.RawURLEncoding
	return signedRuleDTO{Payload: enc.EncodeToString(m.Payload), Signature: enc.EncodeToString(m.Signature), KeyID: m.KeyID}
}

func (s *Server) lookupRule(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	m, err := s.Resolve.Lookup(r.Context(), q.Get("domain"), q.Get("fingerprint"))
	if err != nil {
		s.fail(w, r, err)
		return
	}
	w.Header().Set("Cache-Control", "private, max-age="+ruleCacheSeconds)
	writeJSON(w, http.StatusOK, ruleMatchDTO{MatchedBy: m.MatchedBy, Rule: toSignedRule(m)})
}

type ruleReportDTO struct {
	Version    int             `json:"version"`
	Problem    string          `json:"problem"`
	URL        string          `json:"url"`
	Correction json.RawMessage `json:"correction,omitempty"`
}

func (s *Server) reportRule(w http.ResponseWriter, r *http.Request) {
	var req ruleReportDTO
	if err := decode(w, r, maxBody, &req); err != nil {
		s.fail(w, r, err)
		return
	}
	err := s.Reports.Report(r.Context(), deviceFrom(r).ID, r.PathValue("domain"), reportrule.Report{
		Version: req.Version, Problem: req.Problem, URL: req.URL, Correction: req.Correction,
	})
	if err != nil {
		s.fail(w, r, err)
		return
	}
	w.WriteHeader(http.StatusAccepted)
}

type healthBatchDTO struct {
	Entries []struct {
		Domain        string `json:"domain"`
		Version       int    `json:"version"`
		Successes     int    `json:"successes"`
		EmptyResults  int    `json:"emptyResults"`
		BackwardJumps int    `json:"backwardJumps"`
	} `json:"entries"`
}

func (s *Server) reportHealth(w http.ResponseWriter, r *http.Request) {
	var req healthBatchDTO
	if err := decode(w, r, maxBody, &req); err != nil {
		s.fail(w, r, err)
		return
	}
	entries := make([]reportrule.Entry, 0, len(req.Entries))
	for _, e := range req.Entries {
		entries = append(entries, reportrule.Entry{Domain: e.Domain, Version: e.Version, Counts: rule.Health{
			Successes: e.Successes, EmptyResults: e.EmptyResults, BackwardJumps: e.BackwardJumps,
		}})
	}
	if err := s.Reports.RecordHealth(r.Context(), entries); err != nil {
		s.fail(w, r, err)
		return
	}
	w.WriteHeader(http.StatusAccepted)
}

type candidateRequestDTO struct {
	Domain      string `json:"domain"`
	Fingerprint string `json:"fingerprint,omitempty"`
	Samples     []struct {
		URL  string `json:"url"`
		HTML string `json:"html"`
	} `json:"samples"`
}

type candidateDTO struct {
	CandidateID string         `json:"candidateId"`
	Domain      string         `json:"domain"`
	Status      string         `json:"status"`
	Rule        *signedRuleDTO `json:"rule,omitempty"`
	Reason      string         `json:"reason,omitempty"`
	RetryAfter  int            `json:"retryAfter,omitempty"`
}

func (s *Server) requestRule(w http.ResponseWriter, r *http.Request) {
	var req candidateRequestDTO
	if err := decode(w, r, maxCandidateBody, &req); err != nil {
		s.fail(w, r, err)
		return
	}
	in := generaterule.Request{Domain: req.Domain, Fingerprint: req.Fingerprint}
	for _, sm := range req.Samples {
		in.Samples = append(in.Samples, rule.Sample{URL: sm.URL, HTML: []byte(sm.HTML)})
	}
	c, err := s.Generate.Request(r.Context(), accountFrom(r), in)
	if err != nil {
		s.fail(w, r, err)
		return
	}
	w.Header().Set("Location", "/v1/rule-candidates/"+c.ID)
	s.writeCandidate(w, r, http.StatusAccepted, c)
}

func (s *Server) getCandidate(w http.ResponseWriter, r *http.Request) {
	c, err := s.Generate.Candidate(r.Context(), accountFrom(r), r.PathValue("id"))
	if err != nil {
		s.fail(w, r, err)
		return
	}
	s.writeCandidate(w, r, http.StatusOK, c)
}

func (s *Server) writeCandidate(w http.ResponseWriter, r *http.Request, status int, c rule.Candidate) {
	out := candidateDTO{CandidateID: c.ID, Domain: c.Domain, Status: string(c.Status), Reason: c.Reason}
	switch c.Status {
	case rule.Pending:
		out.RetryAfter = candidatePollSeconds
	case rule.Accepted:
		m, err := s.Resolve.Lookup(r.Context(), c.Domain, "")
		if err != nil {
			s.fail(w, r, err)
			return
		}
		signed := toSignedRule(m)
		out.Rule = &signed
	case rule.Rejected:
	}
	writeJSON(w, status, out)
}

type quotaDTO struct {
	Tier      string    `json:"tier"`
	Limit     int       `json:"limit"`
	Remaining int       `json:"remaining"`
	ResetsAt  time.Time `json:"resetsAt"`
}

func toQuotaDTO(q generaterule.Quota) quotaDTO {
	return quotaDTO{Tier: string(q.Tier), Limit: q.Limit, Remaining: q.Remaining, ResetsAt: q.ResetsAt}
}

func (s *Server) getQuota(w http.ResponseWriter, r *http.Request) {
	q, err := s.Generate.Quota(r.Context(), accountFrom(r))
	if err != nil {
		s.fail(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, toQuotaDTO(q))
}
