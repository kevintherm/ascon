package evaluator

import (
	"net"
	"net/url"
	"strconv"
	"strings"

	"golang.org/x/net/idna"
)

// resolveURL resolves raw against base and normalizes the result the way
// Chromium's URL parser does, so it matches what the JS evaluator gives in the
// WebView. Only http and https results count; the fragment is dropped.
func resolveURL(raw *string, base string) *string {
	if raw == nil {
		return nil
	}
	b, err := url.Parse(base)
	if err != nil {
		return nil
	}
	ref, err := url.Parse(whatwgInput(*raw))
	if err != nil {
		return nil
	}
	u := b.ResolveReference(ref)

	if u.Scheme != "http" && u.Scheme != "https" {
		return nil
	}
	host, ok := normalizeHost(u.Hostname())
	if !ok {
		return nil
	}
	if port := u.Port(); port != "" && !isDefaultPort(u.Scheme, port) {
		host += ":" + port
	}
	u.Host = host
	u.User = nil
	if u.Path == "" {
		u.Path = "/"
		u.RawPath = ""
	}
	u.Fragment = ""
	u.RawFragment = ""
	u.RawQuery = escapeQuery(u.RawQuery)

	s := u.String()
	return &s
}

// whatwgInput applies the input rules Go's parser lacks. Tabs and newlines
// are dropped anywhere, and so is the fragment. Before the query and fragment, a backslash counts as a
// slash, and the path is percent-encoded with Chromium's set so that Go keeps
// it as written instead of re-encoding characters such as parentheses.
func whatwgInput(raw string) string {
	s := strings.NewReplacer("\t", "", "\n", "", "\r", "").Replace(raw)
	// The fragment is dropped from the result, so it is never parsed.
	if i := strings.IndexByte(s, '#'); i >= 0 {
		s = s[:i]
	}
	end := strings.IndexByte(s, '?')
	if end < 0 {
		end = len(s)
	}
	head := strings.ReplaceAll(s[:end], `\`, "/")
	split := pathStart(head)
	return head[:split] + escapePath(head[split:]) + s[end:]
}

// pathStart returns where the path begins, after any scheme and authority.
func pathStart(s string) int {
	i := strings.Index(s, "//")
	if i < 0 || (i > 0 && (s[i-1] != ':' || strings.ContainsAny(s[:i-1], "/."))) {
		return 0
	}
	if j := strings.IndexByte(s[i+2:], '/'); j >= 0 {
		return i + 2 + j
	}
	return len(s)
}

// escapePath percent-encodes the bytes Chromium encodes in a path, and turns
// encoded dot segments such as %2e%2e into real ones so they resolve.
func escapePath(p string) string {
	segments := strings.Split(p, "/")
	for i, seg := range segments {
		switch strings.ToLower(seg) {
		case "%2e":
			segments[i] = "."
		case "%2e%2e", ".%2e", "%2e.":
			segments[i] = ".."
		}
	}
	p = strings.Join(segments, "/")

	var b strings.Builder
	for i := 0; i < len(p); i++ {
		c := p[i]
		switch {
		case c <= 0x20, c >= 0x7f, strings.IndexByte("\"<>`{}|^", c) >= 0:
			b.WriteString(percent(c))
		default:
			b.WriteByte(c)
		}
	}
	return b.String()
}

func percent(c byte) string {
	const hex = "0123456789ABCDEF"
	return string([]byte{'%', hex[c>>4], hex[c&15]})
}

// normalizeHost lowercases and punycodes a domain, and brackets an IPv6
// literal again after Hostname stripped its brackets.
func normalizeHost(h string) (string, bool) {
	if ip := net.ParseIP(h); ip != nil {
		if ip.To4() != nil {
			return ip.String(), true
		}
		return "[" + ip.String() + "]", true
	}
	host, err := idna.Lookup.ToASCII(h)
	if err != nil || host == "" {
		return "", false
	}
	return host, true
}

func isDefaultPort(scheme, port string) bool {
	p, err := strconv.Atoi(port)
	if err != nil {
		return false
	}
	return (scheme == "http" && p == 80) || (scheme == "https" && p == 443)
}

// escapeQuery percent-encodes the characters Chromium encodes in a query and
// Go leaves alone.
func escapeQuery(q string) string {
	var b strings.Builder
	for i := 0; i < len(q); i++ {
		c := q[i]
		switch {
		case c <= 0x20, c == '"', c == '#', c == '<', c == '>', c == '\'', c >= 0x7f:
			b.WriteString(percent(c))
		default:
			b.WriteByte(c)
		}
	}
	return b.String()
}
