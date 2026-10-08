package rule

import (
	"regexp"
	"strings"
)

// Whitespace as contracts/README.md defines it: tab, line feed, form feed,
// carriage return, space and no-break space.
const whitespace = "\t\n\f\r  "

var (
	volumeMarker  = regexp.MustCompile(`\b(?:volume|vol)\.?[\t\n\f\r \x{00a0}]*\d+(?:\.\d+)?`)
	keywordNumber = regexp.MustCompile(`\b(?:chapter|chap|ch|episode|ep)[.\t\n\f\r \x{00a0}#:_-]*(\d+(?:[.,-]\d+)?)`)
	wholeNumber   = regexp.MustCompile(`^\d+(?:[.,-]\d+)?$`)
	firstNumber   = regexp.MustCompile(`\d+(?:[.,]\d+)?`)
	numberParts   = regexp.MustCompile(`[.,-]`)
)

// ParseChapterNumber reads a chapter number from a label such as
// "Vol. 2 Ch. 15" and returns it as a normalized decimal string. It returns
// false when the label holds no chapter number.
func ParseChapterNumber(label string) (string, bool) {
	s := volumeMarker.ReplaceAllString(lowerASCII(label), " ")

	if m := keywordNumber.FindStringSubmatch(s); m != nil {
		return normalizeNumber(m[1]), true
	}
	if trimmed := strings.Trim(s, whitespace); wholeNumber.MatchString(trimmed) {
		return normalizeNumber(trimmed), true
	}
	if m := firstNumber.FindString(s); m != "" {
		return normalizeNumber(m), true
	}
	return "", false
}

func lowerASCII(s string) string {
	return strings.Map(func(r rune) rune {
		if r >= 'A' && r <= 'Z' {
			return r + ('a' - 'A')
		}
		return r
	}, s)
}

// normalizeNumber turns "010,50" into "10.5" and "7.0" into "7".
func normalizeNumber(n string) string {
	parts := numberParts.Split(n, 2)
	whole := strings.TrimLeft(parts[0], "0")
	if whole == "" {
		whole = "0"
	}
	if len(parts) == 1 {
		return whole
	}
	if frac := strings.TrimRight(parts[1], "0"); frac != "" {
		return whole + "." + frac
	}
	return whole
}
