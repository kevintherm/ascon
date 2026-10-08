package synclibrary

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"sort"
	"sync"
	"testing"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/library"
)

// memRepo keeps rows per account in memory.
type memRepo struct {
	mu   sync.Mutex
	rows map[string]map[string]library.Row // account -> entity/id/field -> row
	seq  map[string]int64
}

func newMemRepo() *memRepo {
	return &memRepo{rows: map[string]map[string]library.Row{}, seq: map[string]int64{}}
}

type memStore struct {
	r       *memRepo
	account string
}

func (s memStore) key(e, id, f string) string { return e + "/" + id + "/" + f }

func (s memStore) Field(e, id, f string) (library.FieldValue, bool, error) {
	row, ok := s.r.rows[s.account][s.key(e, id, f)]
	return library.FieldValue{Value: row.Value, UpdatedAt: row.UpdatedAt}, ok, nil
}

func (s memStore) PutField(e, id, f string, v library.FieldValue, seq int64) error {
	s.r.rows[s.account][s.key(e, id, f)] = library.Row{Entity: e, ID: id, Field: f, Value: v.Value, UpdatedAt: v.UpdatedAt, Seq: seq}
	return nil
}

func (s memStore) Tombstone(e, id string) (time.Time, bool, error) {
	row, ok := s.r.rows[s.account][s.key(e, id, "")]
	return row.UpdatedAt, ok, nil
}

func (s memStore) PutTombstone(e, id string, at time.Time, seq int64) error {
	s.r.rows[s.account][s.key(e, id, "")] = library.Row{Entity: e, ID: id, UpdatedAt: at, Seq: seq}
	return nil
}

func (s memStore) NextSeq() (int64, error) { s.r.seq[s.account]++; return s.r.seq[s.account], nil }

func (r *memRepo) Update(_ context.Context, account string, fn func(library.Store) error) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.rows[account] == nil {
		r.rows[account] = map[string]library.Row{}
	}
	return fn(memStore{r, account})
}

func (r *memRepo) sorted(account string, keep func(library.Row) bool) []library.Row {
	var out []library.Row
	for _, row := range r.rows[account] {
		if keep(row) {
			out = append(out, row)
		}
	}
	sort.Slice(out, func(i, j int) bool {
		if out[i].Seq != out[j].Seq {
			return out[i].Seq < out[j].Seq
		}
		return out[i].ID+out[i].Field < out[j].ID+out[j].Field
	})
	return out
}

func (r *memRepo) Since(_ context.Context, account string, cursor int64, limit int) ([]library.Row, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	out := r.sorted(account, func(row library.Row) bool { return row.Seq > cursor })
	if len(out) > limit {
		out = out[:limit]
	}
	return out, nil
}

func (r *memRepo) AtSeq(_ context.Context, account string, seq int64) ([]library.Row, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.sorted(account, func(row library.Row) bool { return row.Seq == seq }), nil
}

var now = time.Date(2026, 10, 8, 10, 0, 0, 0, time.UTC)

func uuid(n int) string { return fmt.Sprintf("00000000-0000-4000-8000-%012d", n) }

func titleChange(n int, title string) library.Change {
	return library.Change{Entity: library.Series, ID: uuid(n), Fields: map[string]library.FieldValue{
		"title": {Value: json.RawMessage(`"` + title + `"`), UpdatedAt: now.Add(-time.Minute)},
	}}
}

func TestPushThenPullAcrossDevices(t *testing.T) {
	ctx := context.Background()
	s := New(newMemRepo(), func() time.Time { return now })

	if n, err := s.Push(ctx, "acc", []library.Change{titleChange(1, "Salt & Ember")}); err != nil || n != 1 {
		t.Fatalf("push = %d, %v", n, err)
	}
	page, err := s.Pull(ctx, "acc", "", 0)
	if err != nil {
		t.Fatal(err)
	}
	if len(page.Changes) != 1 || page.Cursor != "1" || page.HasMore {
		t.Fatalf("first pull = %+v", page)
	}

	// Nothing new: same cursor back, no changes.
	page, _ = s.Pull(ctx, "acc", page.Cursor, 0)
	if len(page.Changes) != 0 || page.Cursor != "1" {
		t.Fatalf("second pull = %+v", page)
	}

	// Other accounts see nothing.
	if page, _ := s.Pull(ctx, "other", "", 0); len(page.Changes) != 0 {
		t.Fatalf("other account pulled %+v", page.Changes)
	}
}

func TestPullPagesWithoutSplittingAPush(t *testing.T) {
	ctx := context.Background()
	s := New(newMemRepo(), func() time.Time { return now })

	// Push 1 writes three records at seq 1; pushes 2 and 3 one each.
	if _, err := s.Push(ctx, "acc", []library.Change{titleChange(1, "a"), titleChange(2, "b"), titleChange(3, "c")}); err != nil {
		t.Fatal(err)
	}
	for i := 4; i <= 5; i++ {
		if _, err := s.Push(ctx, "acc", []library.Change{titleChange(i, "x")}); err != nil {
			t.Fatal(err)
		}
	}

	// A page of 2 cannot hold push 1 without splitting it, so it sends all
	// three rows of push 1.
	page, _ := s.Pull(ctx, "acc", "", 2)
	if len(page.Changes) != 3 || page.Cursor != "1" || !page.HasMore {
		t.Fatalf("page 1 = %d changes, cursor %s, more %v", len(page.Changes), page.Cursor, page.HasMore)
	}
	page, _ = s.Pull(ctx, "acc", page.Cursor, 2)
	if len(page.Changes) != 2 || page.Cursor != "3" || page.HasMore {
		t.Fatalf("page 2 = %d changes, cursor %s, more %v", len(page.Changes), page.Cursor, page.HasMore)
	}
}

func TestPushValidates(t *testing.T) {
	s := New(newMemRepo(), func() time.Time { return now })
	bad := library.Change{Entity: "page", ID: uuid(1), Fields: map[string]library.FieldValue{"x": {Value: json.RawMessage(`1`), UpdatedAt: now}}}
	if _, err := s.Push(context.Background(), "acc", []library.Change{bad}); !errors.Is(err, domain.ErrInvalid) {
		t.Fatalf("bad entity: %v, want ErrInvalid", err)
	}
	if _, err := s.Pull(context.Background(), "acc", "abc", 0); !errors.Is(err, domain.ErrInvalid) {
		t.Fatalf("bad cursor: %v, want ErrInvalid", err)
	}
}
