// Package synclibrary pushes and pulls an account's library between its
// devices. Conflicts resolve per field in library.Merge.
package synclibrary

import (
	"context"
	"strconv"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/library"
)

// Limits on one call.
const (
	MaxPush      = 1000
	DefaultPull  = 500
	MaxPullLimit = 1000
)

// Service syncs libraries.
type Service struct {
	repo library.Repository
	now  func() time.Time
}

// New returns the service.
func New(repo library.Repository, now func() time.Time) *Service {
	return &Service{repo: repo, now: now}
}

// Push merges a device's changes and returns how many fields and tombstones
// changed. The device keeps its own pull cursor; its pushed changes come
// back on the next pull, which is harmless because merging is idempotent.
func (s *Service) Push(ctx context.Context, accountID string, changes []library.Change) (int, error) {
	if len(changes) > MaxPush {
		return 0, domain.Invalidf("push at most %d changes", MaxPush)
	}
	for _, c := range changes {
		if err := c.Validate(); err != nil {
			return 0, err
		}
	}
	if len(changes) == 0 {
		return 0, nil
	}
	var applied int
	err := s.repo.Update(ctx, accountID, func(st library.Store) error {
		var err error
		applied, err = library.Merge(st, changes, s.now())
		return err
	})
	return applied, err
}

// Page is one pull.
type Page struct {
	Changes []library.Change
	Cursor  string
	HasMore bool
}

// Pull returns changes after cursor. Rows written by one push share a seq
// and are never split across pages, so a page may exceed limit when a single
// push was larger.
func (s *Service) Pull(ctx context.Context, accountID, cursor string, limit int) (Page, error) {
	after, err := parseCursor(cursor)
	if err != nil {
		return Page{}, err
	}
	if limit <= 0 {
		limit = DefaultPull
	}
	if limit > MaxPullLimit {
		return Page{}, domain.Invalidf("limit must be at most %d", MaxPullLimit)
	}

	rows, err := s.repo.Since(ctx, accountID, after, limit+1)
	if err != nil {
		return Page{}, err
	}
	hasMore := len(rows) > limit
	if hasMore {
		boundary := rows[limit].Seq
		kept := rows[:0:0]
		for _, r := range rows[:limit] {
			if r.Seq < boundary {
				kept = append(kept, r)
			}
		}
		if len(kept) == 0 {
			// One push alone is larger than the page: send all of it.
			if kept, err = s.repo.AtSeq(ctx, accountID, boundary); err != nil {
				return Page{}, err
			}
		}
		rows = kept
	}

	next := after
	if len(rows) > 0 {
		next = rows[len(rows)-1].Seq
	}
	changes := library.Group(rows)
	if changes == nil {
		changes = []library.Change{}
	}
	return Page{Changes: changes, Cursor: strconv.FormatInt(next, 10), HasMore: hasMore}, nil
}

func parseCursor(c string) (int64, error) {
	if c == "" {
		return 0, nil
	}
	n, err := strconv.ParseInt(c, 10, 64)
	if err != nil || n < 0 {
		return 0, domain.Invalidf("cursor is not valid")
	}
	return n, nil
}
