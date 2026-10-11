package http

import (
	"net/http"
	"strconv"

	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/series"
)

type seriesMetadataDTO struct {
	Ref       string   `json:"ref"`
	Title     string   `json:"title"`
	AltTitles []string `json:"altTitles"`
	Format    string   `json:"format,omitempty"`
	Status    string   `json:"status,omitempty"`
	Year      int      `json:"year,omitempty"`
	CoverURL  string   `json:"coverUrl,omitempty"`
	OtherRef  string   `json:"otherRef,omitempty"`
}

type metadataResultsDTO struct {
	Results []seriesMetadataDTO `json:"results"`
}

func (s *Server) searchMetadata(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	limit := 0
	if v := q.Get("limit"); v != "" {
		n, err := strconv.Atoi(v)
		if err != nil {
			s.fail(w, r, domain.Invalidf("limit must be a number"))
			return
		}
		limit = n
	}
	results, err := s.Metadata.Search(r.Context(), q.Get("q"), limit)
	if err != nil {
		s.fail(w, r, err)
		return
	}
	out := metadataResultsDTO{Results: make([]seriesMetadataDTO, 0, len(results))}
	for _, m := range results {
		out.Results = append(out.Results, toSeriesMetadataDTO(m))
	}
	writeJSON(w, http.StatusOK, out)
}

func toSeriesMetadataDTO(m series.Metadata) seriesMetadataDTO {
	alt := m.AltTitles
	if alt == nil {
		alt = []string{}
	}
	return seriesMetadataDTO{
		Ref: m.Ref, Title: m.Title, AltTitles: alt, Format: m.Format, Status: m.Status,
		Year: m.Year, CoverURL: m.CoverURL, OtherRef: m.OtherRef,
	}
}
