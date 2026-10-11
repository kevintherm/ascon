package searchmetadata

import (
	"cmp"
	"slices"
	"strings"
	"unicode"

	"github.com/kevintherm/ascon/backend/internal/domain/series"
)

// typographic marks AniList's search doesn't treat as their plain forms: it finds
// nothing for "Omniscient Reader’s Viewpoint" with a curly apostrophe.
var typographic = strings.NewReplacer(
	"’", "'", "‘", "'", "ʼ", "'", "`", "'",
	"“", `"`, "”", `"`,
	"–", "-", "—", "-",
	"\u00a0", " ", "　", " ",
)

// plain is query as the services search best: typographic quotes, dashes and
// spaces as their plain forms, and full-width letters and digits as ASCII.
func plain(query string) string {
	var b strings.Builder
	for _, r := range typographic.Replace(query) {
		if r >= '！' && r <= '～' {
			r -= '！' - '!'
		}
		b.WriteRune(r)
	}
	return strings.Join(strings.Fields(b.String()), " ")
}

// normalize lowercases a title and keeps only letters and digits, one space
// between words, so "Trash of the Count's Family" and "trash of the counts
// family" compare equal.
func normalize(title string) string {
	var b strings.Builder
	space := false
	for _, r := range strings.ToLower(title) {
		switch {
		case r == '\'' || r == '’':
			// Apostrophes join a word: count's is counts.
		case unicode.IsLetter(r) || unicode.IsDigit(r):
			if space && b.Len() > 0 {
				b.WriteByte(' ')
			}
			space = false
			b.WriteRune(r)
		default:
			space = true
		}
	}
	return b.String()
}

// merge joins the sources' lists, the first source's series first at each
// position. A series of a later source that shares a title with one already
// listed is the same series: its titles join that series and its ref becomes
// the series' OtherRef.
func merge(lists [][]series.Metadata) []series.Metadata {
	var merged []series.Metadata
	byTitle := map[string][]int{}
	longest := 0
	for _, list := range lists {
		longest = max(longest, len(list))
	}
	for i := range longest {
		for _, list := range lists {
			if i >= len(list) {
				continue
			}
			m := list[i]
			at, ok := sameSeries(merged, byTitle, m)
			if ok {
				merged[at] = joined(merged[at], m)
			} else {
				merged = append(merged, m)
				at = len(merged) - 1
			}
			for _, t := range merged[at].Titles() {
				if k := normalize(t); k != "" && !slices.Contains(byTitle[k], at) {
					byTitle[k] = append(byTitle[k], at)
				}
			}
		}
	}
	return merged
}

// sameSeries finds a listed series m is another source's entry for: one
// sharing a title and format, from another source, not yet joined to one.
func sameSeries(merged []series.Metadata, byTitle map[string][]int, m series.Metadata) (int, bool) {
	for _, t := range m.Titles() {
		for _, at := range byTitle[normalize(t)] {
			c := merged[at]
			sameFormat := c.Format == "" || m.Format == "" || c.Format == m.Format
			if sameFormat && c.OtherRef == "" && source(c.Ref) != source(m.Ref) {
				return at, true
			}
		}
	}
	return 0, false
}

func source(ref string) string {
	s, _, _ := strings.Cut(ref, ":")
	return s
}

// joined is a, with b's titles it lacks and b's ref as its OtherRef. Empty
// fields of a take b's.
func joined(a, b series.Metadata) series.Metadata {
	a.OtherRef = b.Ref
	have := map[string]bool{}
	for _, t := range a.Titles() {
		have[normalize(t)] = true
	}
	a.AltTitles = slices.Clone(a.AltTitles)
	for _, t := range b.Titles() {
		if k := normalize(t); k != "" && !have[k] {
			have[k] = true
			a.AltTitles = append(a.AltTitles, t)
		}
	}
	a.Format = cmp.Or(a.Format, b.Format)
	if a.Status == "" || a.Status == series.StatusUnknown {
		a.Status = cmp.Or(b.Status, a.Status)
	}
	a.Year = cmp.Or(a.Year, b.Year)
	a.CoverURL = cmp.Or(a.CoverURL, b.CoverURL)
	return a
}

// rank orders series by how closely their closest title matches the query,
// keeping the merged order on ties.
func rank(query string, list []series.Metadata) []series.Metadata {
	scores := make(map[string]float64, len(list))
	for _, m := range list {
		best := 0.0
		for _, t := range m.Titles() {
			best = max(best, similarity(query, normalize(t)))
		}
		scores[m.Ref] = best
	}
	ranked := slices.Clone(list)
	slices.SortStableFunc(ranked, func(a, b series.Metadata) int { return cmp.Compare(scores[b.Ref], scores[a.Ref]) })
	return ranked
}

// similarity scores two normalized titles from 0 to 1: 1 when equal, then a
// title that contains the other, then the share of words they have in common.
func similarity(a, b string) float64 {
	if a == "" || b == "" {
		return 0
	}
	if a == b {
		return 1
	}
	short, long := a, b
	if len(short) > len(long) {
		short, long = long, short
	}
	if strings.Contains(" "+long+" ", " "+short+" ") {
		return 0.6 + 0.3*float64(len(short))/float64(len(long))
	}
	return 0.6 * dice(strings.Fields(a), strings.Fields(b))
}

// dice is the Sørensen–Dice coefficient of two word lists.
func dice(a, b []string) float64 {
	counts := map[string]int{}
	for _, w := range a {
		counts[w]++
	}
	shared := 0
	for _, w := range b {
		if counts[w] > 0 {
			counts[w]--
			shared++
		}
	}
	return 2 * float64(shared) / float64(len(a)+len(b))
}
