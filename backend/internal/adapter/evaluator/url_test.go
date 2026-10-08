package evaluator

import (
	"encoding/json"
	"path/filepath"
	"testing"
)

// The expected URLs in urls.json were recorded from Chromium, which runs the
// JS evaluator in the WebView.
func TestResolveURLConformance(t *testing.T) {
	var fixture struct {
		Base  string `json:"base"`
		Cases []struct {
			Raw string  `json:"raw"`
			URL *string `json:"url"`
		} `json:"cases"`
	}
	if err := json.Unmarshal(readFile(t, filepath.Join(contracts, "fixtures/urls.json")), &fixture); err != nil {
		t.Fatal(err)
	}

	for _, c := range fixture.Cases {
		raw := c.Raw
		got := resolveURL(&raw, fixture.Base)
		switch {
		case c.URL == nil && got != nil:
			t.Errorf("resolveURL(%q) = %q, want no value", c.Raw, *got)
		case c.URL != nil && got == nil:
			t.Errorf("resolveURL(%q) gave no value, want %q", c.Raw, *c.URL)
		case c.URL != nil && *got != *c.URL:
			t.Errorf("resolveURL(%q) = %q, want %q", c.Raw, *got, *c.URL)
		}
	}
}
