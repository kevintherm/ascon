// Package rule holds the detection rule entity and the result of evaluating
// one. contracts/rule.schema.json and contracts/README.md define both.
package rule

// Rule says how to read series, chapter and images from one site.
type Rule struct {
	SchemaVersion int         `json:"schemaVersion"`
	Domain        string      `json:"domain"`
	Version       int         `json:"version"`
	Confidence    *float64    `json:"confidence,omitempty"`
	Fingerprint   string      `json:"fingerprint,omitempty"`
	ChapterPage   ChapterPage `json:"chapterPage"`
	SeriesPage    *SeriesPage `json:"seriesPage,omitempty"`
}

// ChapterPage reads a page that shows one chapter.
type ChapterPage struct {
	URL          string   `json:"url"`
	Title        *Extract `json:"title,omitempty"`
	ChapterLabel *Extract `json:"chapterLabel,omitempty"`
	Images       Images   `json:"images"`
	Next         *Link    `json:"next,omitempty"`
	Previous     *Link    `json:"previous,omitempty"`
}

// SeriesPage reads a page that lists a series' chapters.
type SeriesPage struct {
	URL          string   `json:"url"`
	Title        *Extract `json:"title,omitempty"`
	ChapterLinks string   `json:"chapterLinks"`
}

// Extract reads one value from the first element Selector matches.
type Extract struct {
	Selector  string `json:"selector"`
	Attribute string `json:"attribute,omitempty"`
	Pattern   string `json:"pattern,omitempty"`
}

// Link reads a URL from the first element Selector matches.
type Link struct {
	Selector  string `json:"selector"`
	Attribute string `json:"attribute,omitempty"`
}

// Images reads one URL from every element Selector matches.
type Images struct {
	Selector   string   `json:"selector"`
	Attributes []string `json:"attributes,omitempty"`
}

// AttributeText reads an element's text instead of an attribute.
const AttributeText = "text"
