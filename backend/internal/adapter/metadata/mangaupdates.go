package metadata

import (
	"context"
	"html"
	"net/http"
	"strconv"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain/series"
)

// MangaUpdatesURL is MangaUpdates' series search.
const MangaUpdatesURL = "https://api.mangaupdates.com/v1/series/search"

// MangaUpdates searches MangaUpdates. Its search answers with loose matches
// even for a title it doesn't know, so ranking and the app's threshold must
// never trust its first result alone.
type MangaUpdates struct {
	http *polite
	url  string
}

// NewMangaUpdates returns a client sending requests through client, interval apart.
func NewMangaUpdates(client *http.Client, interval time.Duration) *MangaUpdates {
	return &MangaUpdates{http: newPolite(series.MangaUpdates, client, interval), url: MangaUpdatesURL}
}

// Source implements series.Searcher.
func (m *MangaUpdates) Source() series.Source { return series.MangaUpdates }

type muAnswer struct {
	Results []struct {
		Record   muRecord `json:"record"`
		HitTitle string   `json:"hit_title"`
	} `json:"results"`
}

type muRecord struct {
	SeriesID int64  `json:"series_id"`
	Title    string `json:"title"`
	Type     string `json:"type"`
	Year     string `json:"year"`
	Image    struct {
		URL struct {
			Original string `json:"original"`
		} `json:"url"`
	} `json:"image"`
}

// Search implements series.Searcher.
func (m *MangaUpdates) Search(ctx context.Context, query string, limit int) ([]series.Metadata, error) {
	body := map[string]any{"search": query, "perpage": limit}
	var answer muAnswer
	if err := m.http.postJSON(ctx, m.url, body, &answer); err != nil {
		return nil, err
	}
	out := make([]series.Metadata, 0, len(answer.Results))
	for _, r := range answer.Results {
		rec := r.Record
		// Search results carry the main title and the title the search hit, which
		// may be an alternate one. Status needs the full record, so it's unknown.
		titles := uniqueTitles([]string{html.UnescapeString(rec.Title), html.UnescapeString(r.HitTitle)})
		md := series.Metadata{
			Ref:      series.MangaUpdates.Ref(rec.SeriesID),
			Format:   muFormat(rec.Type),
			Status:   series.StatusUnknown,
			CoverURL: rec.Image.URL.Original,
		}
		md.Year, _ = strconv.Atoi(rec.Year)
		if len(titles) > 0 {
			md.Title, md.AltTitles = titles[0], titles[1:]
		}
		out = append(out, md)
	}
	return out, nil
}

func muFormat(kind string) string {
	switch kind {
	case "Manga", "Doujinshi":
		return series.FormatManga
	case "Manhwa":
		return series.FormatManhwa
	case "Manhua":
		return series.FormatManhua
	case "OEL", "Filipino", "Indonesian", "Thai", "Vietnamese", "Malaysian", "Nordic", "French", "Spanish":
		return series.FormatComic
	case "Novel":
		return series.FormatNovel
	}
	return series.FormatOther
}
