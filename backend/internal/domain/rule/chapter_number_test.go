package rule

import (
	"encoding/json"
	"os"
	"testing"
)

func TestParseChapterNumberConformance(t *testing.T) {
	data, err := os.ReadFile("../../../../contracts/fixtures/chapter-numbers.json")
	if err != nil {
		t.Fatal(err)
	}
	var cases []struct {
		Label  string  `json:"label"`
		Number *string `json:"number"`
	}
	if err := json.Unmarshal(data, &cases); err != nil {
		t.Fatal(err)
	}

	for _, c := range cases {
		got, ok := ParseChapterNumber(c.Label)
		switch {
		case c.Number == nil && ok:
			t.Errorf("ParseChapterNumber(%q) = %q, want no number", c.Label, got)
		case c.Number != nil && !ok:
			t.Errorf("ParseChapterNumber(%q) gave no number, want %q", c.Label, *c.Number)
		case c.Number != nil && got != *c.Number:
			t.Errorf("ParseChapterNumber(%q) = %q, want %q", c.Label, got, *c.Number)
		}
	}
}
