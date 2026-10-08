package rule

import "encoding/json"

// Page types an evaluation can report.
const (
	PageChapter = "chapter"
	PageSeries  = "series"
	PageNone    = "none"
)

// Result is what evaluating a rule against one page gives. Exactly one of
// Chapter and Series is set, unless PageType is PageNone.
type Result struct {
	PageType string
	Chapter  *ChapterResult
	Series   *SeriesResult
}

// ChapterResult is read from a chapter page. Nil pointers mean no value.
type ChapterResult struct {
	Series       *string  `json:"series"`
	Title        *string  `json:"title"`
	ChapterLabel *string  `json:"chapterLabel"`
	Chapter      *string  `json:"chapter"`
	Images       []string `json:"images"`
	Next         *string  `json:"next"`
	Previous     *string  `json:"previous"`
}

// SeriesResult is read from a series page.
type SeriesResult struct {
	Series   *string       `json:"series"`
	Title    *string       `json:"title"`
	Chapters []ChapterLink `json:"chapters"`
}

// ChapterLink is one entry in a series page's chapter list.
type ChapterLink struct {
	URL    string  `json:"url"`
	Label  *string `json:"label"`
	Number *string `json:"number"`
}

// MarshalJSON writes the flat shape in contracts/README.md, with pageType
// beside the page's fields.
func (r Result) MarshalJSON() ([]byte, error) {
	switch r.PageType {
	case PageChapter:
		return json.Marshal(struct {
			PageType string `json:"pageType"`
			*ChapterResult
		}{r.PageType, r.Chapter})
	case PageSeries:
		return json.Marshal(struct {
			PageType string `json:"pageType"`
			*SeriesResult
		}{r.PageType, r.Series})
	default:
		return json.Marshal(struct {
			PageType string `json:"pageType"`
		}{PageNone})
	}
}
