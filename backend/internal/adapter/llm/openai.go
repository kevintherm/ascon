package llm

import (
	"bytes"
	"context"
	_ "embed"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strings"

	"github.com/kevintherm/ascon/backend/internal/domain/rule"
)

//go:embed prompt.txt
var systemPrompt string

// DefaultMaxSampleBytes caps each sample sent to the model. The app sends
// sanitized pages well under this; the cap keeps a large one from using up a
// request's tokens.
const DefaultMaxSampleBytes = 60 << 10

// maxResponseBytes bounds how much of a provider's answer is read.
const maxResponseBytes = 1 << 20

// OpenAI generates rules with any provider that speaks the OpenAI chat
// completions API in JSON mode, such as DeepSeek or OpenAI.
type OpenAI struct {
	// BaseURL ends before /chat/completions, for example
	// https://api.deepseek.com/v1.
	BaseURL string
	APIKey  string
	Model   string
	Client  *http.Client
	// MaxSampleBytes caps each sample's HTML. Zero means
	// DefaultMaxSampleBytes.
	MaxSampleBytes int
	// OnUsage, when set, is told the tokens each request used.
	OnUsage func(Usage)
}

// Usage is what one request cost in tokens, as the provider reports it.
// Reasoning tokens are part of Completion; Cached tokens are part of Prompt.
type Usage struct {
	Prompt     int `json:"prompt_tokens"`
	Completion int `json:"completion_tokens"`
	Details    struct {
		Reasoning int `json:"reasoning_tokens"`
	} `json:"completion_tokens_details"`
	PromptDetails struct {
		Cached int `json:"cached_tokens"`
	} `json:"prompt_tokens_details"`
}

var _ rule.Generator = (*OpenAI)(nil)

type chatMessage struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

type chatRequest struct {
	Model          string        `json:"model"`
	Messages       []chatMessage `json:"messages"`
	ResponseFormat struct {
		Type string `json:"type"`
	} `json:"response_format"`
}

type chatResponse struct {
	Usage   *Usage `json:"usage"`
	Choices []struct {
		FinishReason string `json:"finish_reason"`
		Message      struct {
			Content string `json:"content"`
		} `json:"message"`
	} `json:"choices"`
}

// Generate asks the model for a rule. Earlier attempts go back to it as its
// own answers, each followed by why the rule failed.
func (o *OpenAI) Generate(ctx context.Context, domain, _ string, samples []rule.Sample, previous []rule.Attempt) (rule.Rule, error) {
	req := chatRequest{Model: o.Model, Messages: o.messages(domain, samples, previous)}
	req.ResponseFormat.Type = "json_object"
	content, err := o.complete(ctx, req)
	if err != nil {
		return rule.Rule{}, err
	}
	return decodeRule(content)
}

func (o *OpenAI) messages(domain string, samples []rule.Sample, previous []rule.Attempt) []chatMessage {
	limit := o.MaxSampleBytes
	if limit <= 0 {
		limit = DefaultMaxSampleBytes
	}
	var b strings.Builder
	fmt.Fprintf(&b, "Site: %s\n", domain)
	for i, s := range samples {
		html := s.HTML
		cut := ""
		if len(html) > limit {
			html, cut = html[:limit], "\n[cut here]"
		}
		fmt.Fprintf(&b, "\nSample %d URL: %s\nSample %d HTML:\n%s%s\n", i+1, s.URL, i+1, html, cut)
	}
	msgs := []chatMessage{{Role: "system", Content: systemPrompt}, {Role: "user", Content: b.String()}}
	for _, a := range previous {
		answer, _ := json.Marshal(struct {
			ChapterPage rule.ChapterPage `json:"chapterPage"`
			SeriesPage  *rule.SeriesPage `json:"seriesPage,omitempty"`
		}{a.Rule.ChapterPage, a.Rule.SeriesPage})
		msgs = append(msgs,
			chatMessage{Role: "assistant", Content: string(answer)},
			chatMessage{Role: "user", Content: "That rule failed the check: " + a.Problem + ". Reply with a corrected rule."},
		)
	}
	return msgs
}

func (o *OpenAI) complete(ctx context.Context, req chatRequest) (string, error) {
	body, err := json.Marshal(req)
	if err != nil {
		return "", err
	}
	httpReq, err := http.NewRequestWithContext(ctx, http.MethodPost,
		strings.TrimSuffix(o.BaseURL, "/")+"/chat/completions", bytes.NewReader(body))
	if err != nil {
		return "", err
	}
	httpReq.Header.Set("Authorization", "Bearer "+o.APIKey)
	httpReq.Header.Set("Content-Type", "application/json")

	client := o.Client
	if client == nil {
		client = http.DefaultClient
	}
	resp, err := client.Do(httpReq)
	if err != nil {
		return "", fmt.Errorf("llm request: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()
	data, err := io.ReadAll(io.LimitReader(resp.Body, maxResponseBytes))
	if err != nil {
		return "", fmt.Errorf("llm response: %w", err)
	}
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("llm answered %d: %s", resp.StatusCode, truncate(string(data), 300))
	}

	var chat chatResponse
	if err := json.Unmarshal(data, &chat); err != nil {
		return "", fmt.Errorf("llm response is not a chat completion: %w", err)
	}
	if chat.Usage != nil && o.OnUsage != nil {
		o.OnUsage(*chat.Usage)
	}
	if len(chat.Choices) == 0 {
		return "", errors.New("llm answered with no choices")
	}
	choice := chat.Choices[0]
	if choice.FinishReason != "stop" {
		return "", fmt.Errorf("llm stopped early: %s", choice.FinishReason)
	}
	return choice.Message.Content, nil
}

// decodeRule reads the model's answer strictly, so a misspelled field is an
// error instead of a silently missing selector.
func decodeRule(content string) (rule.Rule, error) {
	content = strings.TrimSpace(content)
	content = strings.TrimPrefix(content, "```json")
	content = strings.TrimPrefix(content, "```")
	content = strings.TrimSuffix(content, "```")

	dec := json.NewDecoder(strings.NewReader(content))
	dec.DisallowUnknownFields()
	var r rule.Rule
	if err := dec.Decode(&r); err != nil {
		return rule.Rule{}, fmt.Errorf("llm answer is not a rule: %w", err)
	}
	return r, nil
}

func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n] + "…"
}
