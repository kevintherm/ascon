package rule

import (
	"regexp"
	"strings"
)

var domainPattern = regexp.MustCompile(`^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$`)

// NormalizeDomain lowercases a host and reports whether it is a valid rule
// domain as rule.schema.json defines it.
func NormalizeDomain(d string) (string, bool) {
	d = strings.ToLower(strings.TrimSpace(d))
	return d, len(d) <= 253 && domainPattern.MatchString(d)
}

var fingerprintPattern = regexp.MustCompile(`^[0-9a-f]{16}$`)

// ValidFingerprint reports whether f is 16 lowercase hex digits.
func ValidFingerprint(f string) bool { return fingerprintPattern.MatchString(f) }
