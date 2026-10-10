package rule

import (
	"fmt"
	"regexp"
	"strings"
)

// Portable reports the first part of r that the Go and JS evaluators could
// read differently, or nil. contracts/README.md lists the shared subset of
// selectors and regular expressions. Hand-written rules are kept to it by
// review; generated rules are checked with this before they are stored.
func Portable(r Rule) error {
	c := r.ChapterPage
	patterns := map[string]string{"chapterPage.url": c.URL}
	selectors := map[string]string{"chapterPage.images": c.Images.Selector}
	addExtract(patterns, selectors, "chapterPage.title", c.Title)
	addExtract(patterns, selectors, "chapterPage.chapterLabel", c.ChapterLabel)
	if c.Next != nil {
		selectors["chapterPage.next"] = c.Next.Selector
	}
	if c.Previous != nil {
		selectors["chapterPage.previous"] = c.Previous.Selector
	}
	if s := r.SeriesPage; s != nil {
		patterns["seriesPage.url"] = s.URL
		selectors["seriesPage.chapterLinks"] = s.ChapterLinks
		addExtract(patterns, selectors, "seriesPage.title", s.Title)
	}
	for field, p := range patterns {
		if err := portablePattern(p); err != nil {
			return fmt.Errorf("%s: %w", field, err)
		}
	}
	for field, s := range selectors {
		if err := portableSelector(s); err != nil {
			return fmt.Errorf("%s: %w", field, err)
		}
	}
	return nil
}

func addExtract(patterns, selectors map[string]string, field string, x *Extract) {
	if x == nil {
		return
	}
	selectors[field] = x.Selector
	if x.Pattern != "" {
		patterns[field+".pattern"] = x.Pattern
	}
}

// Escapes only Go understands: \A \z \Q \E \C \p \P. JS without the u flag
// reads them as plain letters.
var goOnlyEscape = regexp.MustCompile(`\\[AzQECpP]`)

// A group may open only as (?: or as a named group (?<name>.
var groupOpening = regexp.MustCompile(`^\(\?(:|<[A-Za-z_][A-Za-z0-9_]*>)`)

func portablePattern(p string) error {
	if _, err := regexp.Compile(p); err != nil {
		return fmt.Errorf("pattern does not compile: %w", err)
	}
	for i := 0; i < len(p); i++ {
		switch {
		case p[i] == '\\':
			if goOnlyEscape.MatchString(p[i:min(i+2, len(p))]) {
				return fmt.Errorf("pattern uses %s, which JavaScript reads differently", p[i:i+2])
			}
			i++
		case strings.HasPrefix(p[i:], "(?") && !groupOpening.MatchString(p[i:]):
			return fmt.Errorf("pattern uses a group or flag outside the shared subset at %q", p[i:min(i+6, len(p))])
		case strings.HasPrefix(p[i:], "[[:"):
			return fmt.Errorf("pattern uses a POSIX class, which JavaScript does not support")
		}
	}
	return nil
}

var allowedPseudo = map[string]bool{
	"first-child": true, "last-child": true, "nth-child": true, "nth-of-type": true, "not": true,
}

// portableSelector rejects pseudo-classes and combinators outside the subset.
// It skips attribute brackets and quoted strings.
func portableSelector(s string) error {
	depth := 0
	for i := 0; i < len(s); i++ {
		switch c := s[i]; c {
		case '[':
			end := strings.IndexByte(s[i:], ']')
			if end < 0 {
				return fmt.Errorf("selector has an unclosed [")
			}
			i += end
		case '"', '\'':
			end := strings.IndexByte(s[i+1:], c)
			if end < 0 {
				return fmt.Errorf("selector has an unclosed quote")
			}
			i += end + 1
		case '(':
			depth++
		case ')':
			depth--
		case '+', '~':
			if depth == 0 {
				return fmt.Errorf("selector uses the %c combinator, which is outside the shared subset", c)
			}
		case ':':
			j := i + 1
			for j < len(s) && (s[j] == '-' || s[j] >= 'a' && s[j] <= 'z' || s[j] >= 'A' && s[j] <= 'Z') {
				j++
			}
			if name := strings.ToLower(s[i+1 : j]); !allowedPseudo[name] {
				return fmt.Errorf("selector uses :%s, which is outside the shared subset", name)
			}
			i = j - 1
		}
	}
	return nil
}
