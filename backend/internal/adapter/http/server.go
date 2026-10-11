// Package http holds the HTTP handlers and DTOs for contracts/openapi.yaml.
// Handlers translate requests into use case calls and never reach
// persistence directly.
package http

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/account"
	"github.com/kevintherm/ascon/backend/internal/domain/device"
	"github.com/kevintherm/ascon/backend/internal/domain/series"
	"github.com/kevintherm/ascon/backend/internal/usecase/generaterule"
	"github.com/kevintherm/ascon/backend/internal/usecase/registerdevice"
	"github.com/kevintherm/ascon/backend/internal/usecase/reportrule"
	"github.com/kevintherm/ascon/backend/internal/usecase/resolverule"
	"github.com/kevintherm/ascon/backend/internal/usecase/searchmetadata"
	"github.com/kevintherm/ascon/backend/internal/usecase/signin"
	"github.com/kevintherm/ascon/backend/internal/usecase/synclibrary"
)

// Body size limits.
const (
	maxBody          = 1 << 20
	maxCandidateBody = 1 << 20 // three 256 KiB samples, JSON-escaped
	maxSyncBody      = 4 << 20 // up to 1000 changes
)

// Server holds the use cases the handlers call.
type Server struct {
	Devices *registerdevice.Service
	// Accounts checks account tokens; SignIn issues and ends them.
	Accounts account.Verifier
	SignIn   *signin.Service
	Resolve  *resolverule.Service
	Reports  *reportrule.Service
	Generate *generaterule.Service
	Sync     *synclibrary.Service
	Metadata *searchmetadata.Service
	Log      *slog.Logger
}

// Handler returns the API's routes.
func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", healthz)

	mux.HandleFunc("POST /v1/devices", s.registerDevice)
	mux.HandleFunc("DELETE /v1/devices/me", s.withDevice(s.deleteDevice))

	mux.HandleFunc("POST /v1/sessions", s.signIn)
	mux.HandleFunc("DELETE /v1/sessions/current", s.withAccount(s.signOut))

	mux.HandleFunc("GET /v1/rules", s.withDevice(s.lookupRule))
	mux.HandleFunc("POST /v1/rules/{domain}/reports", s.withDevice(s.reportRule))
	mux.HandleFunc("POST /v1/rules/health", s.withDevice(s.reportHealth))
	mux.HandleFunc("POST /v1/rule-candidates", s.withAccount(s.requestRule))
	mux.HandleFunc("GET /v1/rule-candidates/{id}", s.withAccount(s.getCandidate))
	mux.HandleFunc("GET /v1/quota", s.withAccount(s.getQuota))

	mux.HandleFunc("GET /v1/metadata/search", s.withDevice(s.searchMetadata))

	mux.HandleFunc("GET /v1/sync/changes", s.withAccount(s.pullChanges))
	mux.HandleFunc("POST /v1/sync/changes", s.withAccount(s.pushChanges))
	return mux
}

func healthz(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	_, _ = w.Write([]byte("ok"))
}

type ctxKey int

const (
	deviceKey ctxKey = iota
	accountKey
)

func bearer(r *http.Request) string {
	h := r.Header.Get("Authorization")
	if len(h) > 7 && strings.EqualFold(h[:7], "Bearer ") {
		return strings.TrimSpace(h[7:])
	}
	return ""
}

func (s *Server) withDevice(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		d, err := s.Devices.Authenticate(r.Context(), bearer(r))
		if err != nil {
			s.fail(w, r, err)
			return
		}
		next(w, r.WithContext(context.WithValue(r.Context(), deviceKey, d)))
	}
}

func (s *Server) withAccount(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		token := bearer(r)
		if token == "" {
			s.fail(w, r, domain.ErrUnauthenticated)
			return
		}
		a, err := s.Accounts.Verify(r.Context(), token)
		if err != nil {
			s.fail(w, r, err)
			return
		}
		next(w, r.WithContext(context.WithValue(r.Context(), accountKey, a)))
	}
}

func deviceFrom(r *http.Request) device.Device {
	d, _ := r.Context().Value(deviceKey).(device.Device)
	return d
}

func accountFrom(r *http.Request) account.Account {
	a, _ := r.Context().Value(accountKey).(account.Account)
	return a
}

// decode reads a JSON body of at most limit bytes into v, rejecting unknown
// fields.
func decode(w http.ResponseWriter, r *http.Request, limit int64, v any) error {
	dec := json.NewDecoder(http.MaxBytesReader(w, r.Body, limit))
	dec.DisallowUnknownFields()
	if err := dec.Decode(v); err != nil {
		var tooLarge *http.MaxBytesError
		if errors.As(err, &tooLarge) {
			return errTooLarge
		}
		return domain.Invalidf("body is not valid JSON for this call: %v", err)
	}
	return nil
}

var errTooLarge = errors.New("request body too large")

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

// problem is an RFC 9457 problem details body.
type problem struct {
	Type   string    `json:"type"`
	Title  string    `json:"title"`
	Status int       `json:"status"`
	Detail string    `json:"detail,omitempty"`
	Quota  *quotaDTO `json:"quota,omitempty"`
}

func (s *Server) fail(w http.ResponseWriter, r *http.Request, err error) {
	p := problem{Status: http.StatusInternalServerError, Type: "about:blank", Title: "Internal error"}
	var quota *generaterule.QuotaExceededError
	var limited *series.RateLimitedError

	switch {
	case errors.As(err, &quota):
		p = problem{Status: http.StatusTooManyRequests, Type: problemType("quota-exceeded"), Title: "AI detection quota used up", Detail: err.Error()}
		q := toQuotaDTO(quota.Quota)
		p.Quota = &q
		wait := int(time.Until(quota.Quota.ResetsAt).Seconds()) + 1
		w.Header().Set("Retry-After", strconv.Itoa(max(wait, 1)))
	case errors.As(err, &limited):
		p = problem{Status: http.StatusTooManyRequests, Type: problemType("rate-limited"), Title: "Metadata search is busy", Detail: err.Error()}
		w.Header().Set("Retry-After", strconv.Itoa(max(int(limited.RetryAfter.Seconds()), 1)))
	case errors.Is(err, series.ErrUpstream):
		p = problem{Status: http.StatusBadGateway, Type: problemType("upstream"), Title: "AniList and MangaUpdates both failed"}
		s.Log.Warn("metadata search failed", "err", err)
	case errors.Is(err, errTooLarge):
		p = problem{Status: http.StatusRequestEntityTooLarge, Type: problemType("too-large"), Title: "Request body too large"}
	case errors.Is(err, domain.ErrInvalid):
		p = problem{Status: http.StatusBadRequest, Type: problemType("invalid"), Title: "Invalid request", Detail: err.Error()}
	case errors.Is(err, domain.ErrUnauthenticated):
		p = problem{Status: http.StatusUnauthorized, Type: problemType("unauthenticated"), Title: "Missing or invalid token"}
		w.Header().Set("WWW-Authenticate", "Bearer")
	case errors.Is(err, domain.ErrNotFound):
		p = problem{Status: http.StatusNotFound, Type: problemType("not-found"), Title: "Not found"}
	case errors.Is(err, domain.ErrConflict):
		p = problem{Status: http.StatusConflict, Type: problemType("conflict"), Title: "Conflict", Detail: err.Error()}
	default:
		s.Log.Error("request failed", "method", r.Method, "path", r.URL.Path, "err", err)
	}

	w.Header().Set("Content-Type", "application/problem+json")
	w.WriteHeader(p.Status)
	_ = json.NewEncoder(w).Encode(p)
}

func problemType(slug string) string { return fmt.Sprintf("https://ascon.app/problems/%s", slug) }
