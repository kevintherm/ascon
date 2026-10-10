package llm

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"github.com/kevintherm/ascon/backend/internal/adapter/evaluator"
	"github.com/kevintherm/ascon/backend/internal/domain/rule"
)

const fixtures = "../../../../contracts/fixtures/conformance"

// provider answers every request with content and records what it was sent.
func provider(t *testing.T, status int, finish, content string) (*OpenAI, *chatRequest) {
	t.Helper()
	var got chatRequest
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/chat/completions" || r.Header.Get("Authorization") != "Bearer sk-test" {
			t.Errorf("request %s with %q", r.URL.Path, r.Header.Get("Authorization"))
		}
		body, _ := io.ReadAll(r.Body)
		if err := json.Unmarshal(body, &got); err != nil {
			t.Error(err)
		}
		w.WriteHeader(status)
		resp := map[string]any{"usage": map[string]any{
			"prompt_tokens": 900, "completion_tokens": 300,
			"completion_tokens_details": map[string]any{"reasoning_tokens": 250},
		}, "choices": []any{map[string]any{
			"finish_reason": finish, "message": map[string]any{"content": content},
		}}}
		_ = json.NewEncoder(w).Encode(resp)
	}))
	t.Cleanup(srv.Close)
	return &OpenAI{BaseURL: srv.URL + "/v1/", APIKey: "sk-test", Model: "m", MaxSampleBytes: 10}, &got
}

var samples = []rule.Sample{
	{URL: "https://t.example/a/ch-1", HTML: []byte("<p>short</p>")},
	{URL: "https://t.example/a/ch-2", HTML: []byte("<p>ok</p>")},
}

func TestGenerateSendsSamplesAndReadsTheRule(t *testing.T) {
	o, got := provider(t, http.StatusOK, "stop",
		"```json\n{\"chapterPage\":{\"url\":\"https://t\\\\.example/.*\",\"images\":{\"selector\":\"img\"}}}\n```")

	var usage Usage
	o.OnUsage = func(u Usage) { usage = u }
	r, err := o.Generate(context.Background(), "t.example", "", samples, nil)
	if err != nil {
		t.Fatal(err)
	}
	if usage.Prompt != 900 || usage.Completion != 300 || usage.Details.Reasoning != 250 {
		t.Errorf("usage = %+v", usage)
	}
	if r.ChapterPage.URL != `https://t\.example/.*` || r.ChapterPage.Images.Selector != "img" {
		t.Fatalf("rule = %+v", r)
	}

	if got.Model != "m" || got.ResponseFormat.Type != "json_object" || len(got.Messages) != 2 {
		t.Fatalf("request = %+v", got)
	}
	if got.Messages[0].Role != "system" || got.Messages[0].Content != systemPrompt {
		t.Fatal("first message is not the system prompt")
	}
	user := got.Messages[1].Content
	for _, want := range []string{"Site: t.example", "Sample 1 URL: https://t.example/a/ch-1", "<p>short</\n[cut here]", "<p>ok</p>\n"} {
		if !strings.Contains(user, want) {
			t.Errorf("user message lacks %q:\n%s", want, user)
		}
	}
}

func TestGenerateTellsTheModelWhyEarlierRulesFailed(t *testing.T) {
	o, got := provider(t, http.StatusOK, "stop", `{"chapterPage":{"url":".*","images":{"selector":"img"}}}`)
	earlier := rule.Rule{Domain: "t.example", ChapterPage: rule.ChapterPage{URL: "x", Images: rule.Images{Selector: "div"}}}

	if _, err := o.Generate(context.Background(), "t.example", "", samples, []rule.Attempt{{Rule: earlier, Problem: "no title on sample 1"}}); err != nil {
		t.Fatal(err)
	}
	msgs := got.Messages
	if len(msgs) != 4 || msgs[2].Role != "assistant" || msgs[3].Role != "user" {
		t.Fatalf("messages = %+v", msgs)
	}
	if msgs[2].Content != `{"chapterPage":{"url":"x","images":{"selector":"div"}}}` {
		t.Errorf("earlier rule sent as %s", msgs[2].Content)
	}
	if !strings.Contains(msgs[3].Content, "no title on sample 1") {
		t.Errorf("problem sent as %q", msgs[3].Content)
	}
}

func TestGenerateFailures(t *testing.T) {
	cases := []struct {
		name, finish, content string
		status                int
		want                  string
	}{
		{"provider error", "stop", "", http.StatusUnauthorized, "llm answered 401"},
		{"cut off", "length", `{"chapterPage":`, http.StatusOK, "stopped early: length"},
		{"unknown field", "stop", `{"chapterPage":{"url":".*","images":{"selector":"img"},"author":{}}}`, http.StatusOK, "not a rule"},
		{"not json", "stop", "Sure! Here is a rule", http.StatusOK, "not a rule"},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			o, _ := provider(t, c.status, c.finish, c.content)
			_, err := o.Generate(context.Background(), "t.example", "", samples, nil)
			if err == nil || !strings.Contains(err.Error(), c.want) {
				t.Fatalf("err = %v, want %q", err, c.want)
			}
			if strings.Contains(err.Error(), "sk-test") {
				t.Fatal("error carries the API key")
			}
		})
	}
}

// The prompt's example is the glasslight fixture rule, so it is known to pass
// the schema, the subset and both evaluators.
func TestPromptExampleIsTheGlasslightRule(t *testing.T) {
	start := strings.Index(systemPrompt, "\n{\n  \"chapterPage\": {\n    \"url\": \"https://glasslight")
	end := strings.Index(systemPrompt, "\n\nThe checker")
	if start < 0 || end < start {
		t.Fatal("example not found in prompt.txt")
	}
	example, err := decodeRule(systemPrompt[start:end])
	if err != nil {
		t.Fatal(err)
	}

	data, err := os.ReadFile(filepath.Join(fixtures, "glasslight/rule.json"))
	if err != nil {
		t.Fatal(err)
	}
	var fixture rule.Rule
	if err := json.Unmarshal(data, &fixture); err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(example.ChapterPage, fixture.ChapterPage) || !reflect.DeepEqual(example.SeriesPage, fixture.SeriesPage) {
		t.Fatalf("prompt example differs from glasslight/rule.json:\n%+v\n%+v", example, fixture)
	}
	if err := rule.Portable(example); err != nil {
		t.Fatal(err)
	}
	page, _ := os.ReadFile(filepath.Join(fixtures, "glasslight/chapter.html"))
	res, err := evaluator.Evaluate(example, "https://glasslight.example/manga/salt-and-ember/chapter-10-5/", page)
	if err != nil || res.PageType != rule.PageChapter || len(res.Chapter.Images) != 3 {
		t.Fatalf("example on its own page = %+v, %v", res, err)
	}
}
