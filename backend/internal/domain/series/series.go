// Package series describes canonical series on AniList and MangaUpdates, the
// sources the app links detected titles to.
package series

import (
	"context"
	"errors"
	"fmt"
	"time"
)

// Source names a metadata service. A Ref is "<source>:<id>".
type Source string

// The metadata services.
const (
	AniList      Source = "anilist"
	MangaUpdates Source = "mangaupdates"
)

// Ref returns the reference to series id on s, such as "anilist:30013".
func (s Source) Ref(id int64) string { return fmt.Sprintf("%s:%d", s, id) }

// Format values, as in contracts/openapi.yaml's SeriesMetadata.
const (
	FormatManga  = "manga"
	FormatManhwa = "manhwa"
	FormatManhua = "manhua"
	FormatComic  = "comic"
	FormatNovel  = "novel"
	FormatOther  = "other"
)

// Status values, as in contracts/openapi.yaml's SeriesMetadata.
const (
	StatusOngoing   = "ongoing"
	StatusCompleted = "completed"
	StatusHiatus    = "hiatus"
	StatusCancelled = "cancelled"
	StatusUnknown   = "unknown"
)

// Metadata is one series as a metadata service describes it. Empty fields are
// unknown.
type Metadata struct {
	Ref       string
	Title     string
	AltTitles []string
	Format    string
	Status    string
	Year      int
	CoverURL  string
	// OtherRef is the same series on the other service, when known.
	OtherRef string
}

// Titles returns the main title and every alternate title.
func (m Metadata) Titles() []string { return append([]string{m.Title}, m.AltTitles...) }

// Searcher searches one metadata service by title, best match first.
type Searcher interface {
	Source() Source
	Search(ctx context.Context, query string, limit int) ([]Metadata, error)
}

// RateLimitedError is a service asking Ascon to wait before its next request.
type RateLimitedError struct {
	Source     Source
	RetryAfter time.Duration
}

func (e *RateLimitedError) Error() string {
	return fmt.Sprintf("%s asked to wait %s", e.Source, e.RetryAfter)
}

// ErrUpstream marks a failed request to a metadata service.
var ErrUpstream = errors.New("metadata service failed")
