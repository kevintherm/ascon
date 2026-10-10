package rule

import (
	"context"
	"errors"
	"math/bits"
	"strconv"
	"time"
)

// Status is where a stored rule version is in its life.
type Status string

// Statuses.
const (
	// Active rules are served.
	Active Status = "active"
	// Suspect rules are still served but are due for regeneration.
	Suspect Status = "suspect"
	// Retired rules are kept for rollback and never served.
	Retired Status = "retired"
)

// Stored is one version of a site's rule as kept by the server.
type Stored struct {
	Rule      Rule
	Status    Status
	CreatedAt time.Time
}

// Health counts how a rule version has done on devices.
type Health struct {
	Successes     int
	EmptyResults  int
	BackwardJumps int
}

// Confidence estimates how often the rule works, from 0 to 1. It starts at
// 0.5 with no data and moves toward the observed success rate.
func (h Health) Confidence() float64 {
	return float64(h.Successes+1) / float64(h.Total()+2)
}

// Total is the number of extractions counted.
func (h Health) Total() int {
	return h.Successes + h.EmptyResults + h.BackwardJumps
}

// Report says a rule got a page wrong.
type Report struct {
	Domain     string
	Version    int
	DeviceID   string
	Problem    string
	URL        string
	Correction []byte // JSON, or nil
	CreatedAt  time.Time
}

// Problems a report can name.
var Problems = map[string]bool{
	"wrong_title": true, "wrong_chapter": true, "no_images": true,
	"wrong_images": true, "not_a_chapter": true, "other": true,
}

// Repository stores rule versions, their health and reports.
type Repository interface {
	// Latest returns the newest version for domain that is not retired, or
	// domain.ErrNotFound.
	Latest(ctx context.Context, domain string) (Stored, error)
	// Get returns one version, or domain.ErrNotFound.
	Get(ctx context.Context, domain string, version int) (Stored, error)
	// Fingerprinted lists the newest non-retired version of every rule that
	// has a fingerprint.
	Fingerprinted(ctx context.Context) ([]Stored, error)
	// MaxVersion returns the highest version ever stored for domain, retired
	// ones included, or 0.
	MaxVersion(ctx context.Context, domain string) (int, error)
	Insert(ctx context.Context, s Stored) error
	SetStatus(ctx context.Context, domain string, version int, status Status) error
	Health(ctx context.Context, domain string, version int) (Health, error)
	// AddHealth adds to a version's counts and returns the new totals.
	AddHealth(ctx context.Context, domain string, version int, h Health) (Health, error)
	// AddReport records a report and returns how many devices have reported
	// this version. A device reporting the same problem twice counts once.
	AddReport(ctx context.Context, r Report) (devices int, err error)
}

// Signer signs rule payloads with the server's Ed25519 key.
type Signer interface {
	Sign(payload []byte) (signature []byte, keyID string)
}

// Evaluator runs a rule against a page. It is the Go twin of the JS
// evaluator the app injects into the WebView.
type Evaluator interface {
	Evaluate(r Rule, pageURL string, page []byte) (Result, error)
}

// Sample is a sanitized chapter page sent by the app for rule generation.
type Sample struct {
	URL  string
	HTML []byte
}

// Attempt is a rule generated earlier for the same samples and why it failed
// the check, so the next try can correct it.
type Attempt struct {
	Rule    Rule
	Problem string
}

// ErrBadAnswer marks a generator answer that is not a rule at all, such as
// malformed JSON. Like a rule that fails the check, it counts as a failed
// attempt, so the next attempt is told what was wrong.
var ErrBadAnswer = errors.New("the answer is not a rule")

// Generator asks a language model for a rule. Its output is untrusted until
// the generaterule use case has checked it with an Evaluator.
type Generator interface {
	Generate(ctx context.Context, domain, fingerprint string, samples []Sample, previous []Attempt) (Rule, error)
}

// FingerprintDistance counts the bits that differ between two 16-digit hex
// simhashes. It returns -1 if either is not a valid fingerprint.
func FingerprintDistance(a, b string) int {
	x, errA := strconv.ParseUint(a, 16, 64)
	y, errB := strconv.ParseUint(b, 16, 64)
	if errA != nil || errB != nil || len(a) != 16 || len(b) != 16 {
		return -1
	}
	return bits.OnesCount64(x ^ y)
}
