package http

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/library"
)

type fieldDTO struct {
	Value     json.RawMessage `json:"value"`
	UpdatedAt time.Time       `json:"updatedAt"`
}

type tombstoneDTO struct {
	UpdatedAt time.Time `json:"updatedAt"`
}

type changeDTO struct {
	Entity  string              `json:"entity"`
	ID      string              `json:"id"`
	Deleted *tombstoneDTO       `json:"deleted,omitempty"`
	Fields  map[string]fieldDTO `json:"fields,omitempty"`
}

func fromChangeDTO(c changeDTO) library.Change {
	out := library.Change{Entity: c.Entity, ID: c.ID}
	if c.Deleted != nil {
		at := c.Deleted.UpdatedAt
		out.Deleted = &at
	}
	if len(c.Fields) > 0 {
		out.Fields = make(map[string]library.FieldValue, len(c.Fields))
		for name, f := range c.Fields {
			out.Fields[name] = library.FieldValue{Value: f.Value, UpdatedAt: f.UpdatedAt}
		}
	}
	return out
}

func toChangeDTO(c library.Change) changeDTO {
	out := changeDTO{Entity: c.Entity, ID: c.ID}
	if c.Deleted != nil {
		out.Deleted = &tombstoneDTO{UpdatedAt: c.Deleted.UTC()}
	}
	if len(c.Fields) > 0 {
		out.Fields = make(map[string]fieldDTO, len(c.Fields))
		for name, f := range c.Fields {
			out.Fields[name] = fieldDTO{Value: f.Value, UpdatedAt: f.UpdatedAt.UTC()}
		}
	}
	return out
}

type changePageDTO struct {
	Changes []changeDTO `json:"changes"`
	Cursor  string      `json:"cursor"`
	HasMore bool        `json:"hasMore"`
}

func (s *Server) pullChanges(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	limit := 0
	if l := q.Get("limit"); l != "" {
		n, err := strconv.Atoi(l)
		if err != nil || n < 1 {
			s.fail(w, r, domain.Invalidf("limit must be a positive integer"))
			return
		}
		limit = n
	}
	page, err := s.Sync.Pull(r.Context(), accountFrom(r).ID, q.Get("since"), limit)
	if err != nil {
		s.fail(w, r, err)
		return
	}
	out := changePageDTO{Changes: make([]changeDTO, 0, len(page.Changes)), Cursor: page.Cursor, HasMore: page.HasMore}
	for _, c := range page.Changes {
		out.Changes = append(out.Changes, toChangeDTO(c))
	}
	writeJSON(w, http.StatusOK, out)
}

type pushDTO struct {
	Changes []changeDTO `json:"changes"`
}

type pushResultDTO struct {
	Applied int `json:"applied"`
}

func (s *Server) pushChanges(w http.ResponseWriter, r *http.Request) {
	var req pushDTO
	if err := decode(w, r, maxSyncBody, &req); err != nil {
		s.fail(w, r, err)
		return
	}
	changes := make([]library.Change, 0, len(req.Changes))
	for _, c := range req.Changes {
		changes = append(changes, fromChangeDTO(c))
	}
	applied, err := s.Sync.Push(r.Context(), accountFrom(r).ID, changes)
	if err != nil {
		s.fail(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, pushResultDTO{Applied: applied})
}
