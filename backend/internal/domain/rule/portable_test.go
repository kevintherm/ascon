package rule

import (
	"strings"
	"testing"
)

func TestPortableAcceptsTheSharedSubset(t *testing.T) {
	r := Rule{
		ChapterPage: ChapterPage{
			URL:          `https://t\.example/(?<series>[^/]+)/ch-(?<chapter>\d+(?:\.\d+)?)/?`,
			Title:        &Extract{Selector: `.breadcrumb li:nth-child(2) > a`},
			ChapterLabel: &Extract{Selector: `h1#chapter-heading`, Pattern: `(?<value>Ch(?:apter)?\.? ?\d+)`},
			Images:       Images{Selector: `.reading-content img:not(.ad), div[data-x~="a+b:c"] img`},
			Next:         &Link{Selector: `a[rel=next]:first-child`},
		},
		SeriesPage: &SeriesPage{URL: `https://t\.example/(?<series>[^/]+)/`, ChapterLinks: `li:nth-of-type(2n+1) a`},
	}
	if err := Portable(r); err != nil {
		t.Fatal(err)
	}
}

func TestPortableRejectsWhatEvaluatorsReadDifferently(t *testing.T) {
	cases := map[string]Rule{
		"inline flag":        {ChapterPage: ChapterPage{URL: `(?i)https://t\.example/.*`, Images: Images{Selector: "img"}}},
		"python named group": {ChapterPage: ChapterPage{URL: `https://t\.example/(?P<series>.+)`, Images: Images{Selector: "img"}}},
		"lookahead":          {ChapterPage: ChapterPage{URL: `https://t\.example/(?=x).*`, Images: Images{Selector: "img"}}},
		"go-only escape":     {ChapterPage: ChapterPage{URL: `\Ahttps://t\.example/.*`, Images: Images{Selector: "img"}}},
		"posix class":        {ChapterPage: ChapterPage{URL: `https://t\.example/[[:alpha:]]+`, Images: Images{Selector: "img"}}},
		"contains":           {ChapterPage: ChapterPage{URL: `.*`, Images: Images{Selector: `div:contains("x") img`}}},
		"has":                {ChapterPage: ChapterPage{URL: `.*`, Images: Images{Selector: `div:has(img) img`}}},
		"pseudo element":     {ChapterPage: ChapterPage{URL: `.*`, Images: Images{Selector: `img::before`}}},
		"sibling":            {ChapterPage: ChapterPage{URL: `.*`, Images: Images{Selector: `h1 + div img`}}},
		"label pattern": {ChapterPage: ChapterPage{
			URL: `.*`, Images: Images{Selector: "img"},
			ChapterLabel: &Extract{Selector: "h1", Pattern: `(?i)(?<value>\d+)`},
		}},
		"series links": {
			ChapterPage: ChapterPage{URL: `.*`, Images: Images{Selector: "img"}},
			SeriesPage:  &SeriesPage{URL: `.*`, ChapterLinks: `li ~ li a`},
		},
	}
	for name, r := range cases {
		err := Portable(r)
		if err == nil {
			t.Errorf("%s: accepted", name)
			continue
		}
		if !strings.Contains(err.Error(), ":") {
			t.Errorf("%s: error does not name the field: %v", name, err)
		}
	}
}
