// Package llm holds language model providers that generate detection rules.
// OpenAI talks to any OpenAI-compatible chat API. The production provider is
// not chosen yet; see AGENTS.md, Open questions.
package llm

import (
	"context"
	"errors"

	"github.com/kevintherm/ascon/backend/internal/domain/rule"
)

// ErrUnconfigured is returned while no provider is configured.
var ErrUnconfigured = errors.New("no LLM provider configured")

// Unconfigured rejects every request. Generation requests still run their
// quota and candidate bookkeeping, then settle as rejected with the quota
// refunded.
type Unconfigured struct{}

var _ rule.Generator = Unconfigured{}

// Generate always fails.
func (Unconfigured) Generate(context.Context, string, string, []rule.Sample, []rule.Attempt) (rule.Rule, error) {
	return rule.Rule{}, ErrUnconfigured
}
