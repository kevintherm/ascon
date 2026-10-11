package metadata

import (
	"context"
	"net/http"
	"strings"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain/series"
)

// AniListURL is AniList's GraphQL endpoint.
const AniListURL = "https://graphql.anilist.co"

// anilistQuery asks for comics matching a title, in AniList's own best-match
// order. AniList's MANGA type also holds light novels; their format says so.
const anilistQuery = `query ($search: String, $perPage: Int) {
  Page(perPage: $perPage) {
    media(search: $search, type: MANGA) {
      id
      title { romaji english native }
      synonyms
      format
      status
      countryOfOrigin
      startDate { year }
      coverImage { large }
    }
  }
}`

// AniList searches AniList.
type AniList struct {
	http *polite
	url  string
}

// NewAniList returns a client sending requests through client, interval apart.
func NewAniList(client *http.Client, interval time.Duration) *AniList {
	return &AniList{http: newPolite(series.AniList, client, interval), url: AniListURL}
}

// Source implements series.Searcher.
func (a *AniList) Source() series.Source { return series.AniList }

type anilistAnswer struct {
	Data struct {
		Page struct {
			Media []anilistMedia `json:"media"`
		} `json:"Page"`
	} `json:"data"`
}

type anilistMedia struct {
	ID    int64 `json:"id"`
	Title struct {
		Romaji  string `json:"romaji"`
		English string `json:"english"`
		Native  string `json:"native"`
	} `json:"title"`
	Synonyms        []string `json:"synonyms"`
	Format          string   `json:"format"`
	Status          string   `json:"status"`
	CountryOfOrigin string   `json:"countryOfOrigin"`
	StartDate       struct {
		Year int `json:"year"`
	} `json:"startDate"`
	CoverImage struct {
		Large string `json:"large"`
	} `json:"coverImage"`
}

// Search implements series.Searcher.
func (a *AniList) Search(ctx context.Context, query string, limit int) ([]series.Metadata, error) {
	body := map[string]any{"query": anilistQuery, "variables": map[string]any{"search": query, "perPage": limit}}
	var answer anilistAnswer
	if err := a.http.postJSON(ctx, a.url, body, &answer); err != nil {
		return nil, err
	}
	media := answer.Data.Page.Media
	out := make([]series.Metadata, 0, len(media))
	for _, m := range media {
		out = append(out, m.metadata())
	}
	return out, nil
}

func (m anilistMedia) metadata() series.Metadata {
	// The romaji title is AniList's main one; the rest are alternates.
	titles := uniqueTitles(append([]string{m.Title.Romaji, m.Title.English, m.Title.Native}, m.Synonyms...))
	md := series.Metadata{
		Ref:      series.AniList.Ref(m.ID),
		Format:   anilistFormat(m.Format, m.CountryOfOrigin),
		Status:   anilistStatus(m.Status),
		Year:     m.StartDate.Year,
		CoverURL: m.CoverImage.Large,
	}
	if len(titles) > 0 {
		md.Title, md.AltTitles = titles[0], titles[1:]
	}
	return md
}

func anilistFormat(format, country string) string {
	switch format {
	case "NOVEL":
		return series.FormatNovel
	case "MANGA", "ONE_SHOT":
		switch country {
		case "KR":
			return series.FormatManhwa
		case "CN", "TW":
			return series.FormatManhua
		case "JP":
			return series.FormatManga
		}
		return series.FormatComic
	}
	return series.FormatOther
}

func anilistStatus(status string) string {
	switch status {
	case "RELEASING":
		return series.StatusOngoing
	case "FINISHED":
		return series.StatusCompleted
	case "HIATUS":
		return series.StatusHiatus
	case "CANCELLED":
		return series.StatusCancelled
	}
	return series.StatusUnknown
}

// uniqueTitles drops empty titles and repeats, keeping the first spelling.
func uniqueTitles(titles []string) []string {
	seen := map[string]bool{}
	var out []string
	for _, t := range titles {
		t = strings.TrimSpace(t)
		if k := strings.ToLower(t); t != "" && !seen[k] {
			seen[k] = true
			out = append(out, t)
		}
	}
	return out
}
