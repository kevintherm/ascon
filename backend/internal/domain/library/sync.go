package library

import (
	"bytes"
	"context"
	"encoding/json"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain"
)

// FieldValue is one field's value and when a device last changed it.
type FieldValue struct {
	Value     json.RawMessage
	UpdatedAt time.Time
}

// Change is one record's changed fields, a tombstone, or both.
type Change struct {
	Entity  string
	ID      string
	Deleted *time.Time
	Fields  map[string]FieldValue
}

// Validate checks a change pushed by a device.
func (c Change) Validate() error {
	known, ok := fields[c.Entity]
	if !ok {
		return domain.Invalidf("unknown entity %q", c.Entity)
	}
	if !uuidPattern.MatchString(c.ID) {
		return domain.Invalidf("%s id %q is not a UUID", c.Entity, c.ID)
	}
	if c.Deleted == nil && len(c.Fields) == 0 {
		return domain.Invalidf("%s %s has no fields and no tombstone", c.Entity, c.ID)
	}
	for name, v := range c.Fields {
		k, ok := known[name]
		if !ok {
			return domain.Invalidf("%s has no field %q", c.Entity, name)
		}
		if !validValue(k, v.Value) {
			return domain.Invalidf("%s.%s has an invalid value", c.Entity, name)
		}
		if v.UpdatedAt.IsZero() {
			return domain.Invalidf("%s.%s has no updatedAt", c.Entity, name)
		}
	}
	return nil
}

// Store is the view of one account's synced data inside a write transaction.
type Store interface {
	Field(entity, id, field string) (FieldValue, bool, error)
	PutField(entity, id, field string, v FieldValue, seq int64) error
	Tombstone(entity, id string) (time.Time, bool, error)
	PutTombstone(entity, id string, at time.Time, seq int64) error
	// NextSeq advances the account's clock and returns the new value.
	NextSeq() (int64, error)
}

// Row is one stored field or tombstone, as read for a pull.
type Row struct {
	Entity    string
	ID        string
	Field     string // empty for a tombstone
	Value     json.RawMessage
	UpdatedAt time.Time
	Seq       int64
}

// Repository stores synced data per account.
type Repository interface {
	// Update runs fn in one write transaction for the account.
	Update(ctx context.Context, accountID string, fn func(Store) error) error
	// Since returns up to limit rows with seq above cursor, oldest first.
	Since(ctx context.Context, accountID string, cursor int64, limit int) ([]Row, error)
	// AtSeq returns every row with exactly this seq.
	AtSeq(ctx context.Context, accountID string, seq int64) ([]Row, error)
}

// Merge applies changes to s. For each field and tombstone the later
// updatedAt wins; on a tie the larger JSON value wins, so every device and
// the server pick the same one. It returns how many fields and tombstones
// changed.
func Merge(s Store, changes []Change, now time.Time) (int, error) {
	seq, err := s.NextSeq()
	if err != nil {
		return 0, err
	}
	applied := 0
	for _, c := range changes {
		if c.Deleted != nil {
			at := clamp(*c.Deleted, now)
			old, ok, err := s.Tombstone(c.Entity, c.ID)
			if err != nil {
				return applied, err
			}
			if !ok || at.After(old) {
				if err := s.PutTombstone(c.Entity, c.ID, at, seq); err != nil {
					return applied, err
				}
				applied++
			}
		}
		for name, v := range c.Fields {
			v.UpdatedAt = clamp(v.UpdatedAt, now)
			old, ok, err := s.Field(c.Entity, c.ID, name)
			if err != nil {
				return applied, err
			}
			if ok && !wins(v, old) {
				continue
			}
			if err := s.PutField(c.Entity, c.ID, name, v, seq); err != nil {
				return applied, err
			}
			applied++
		}
	}
	return applied, nil
}

func wins(v, old FieldValue) bool {
	if !v.UpdatedAt.Equal(old.UpdatedAt) {
		return v.UpdatedAt.After(old.UpdatedAt)
	}
	return bytes.Compare(v.Value, old.Value) > 0
}

// clamp stops a device with a fast clock from writing a timestamp that would
// win every later conflict.
func clamp(t, now time.Time) time.Time {
	if t.After(now) {
		return now
	}
	return t
}

// Group turns rows into changes, one per record, in the order records first
// appear.
func Group(rows []Row) []Change {
	type key struct{ entity, id string }
	index := map[key]int{}
	var out []Change
	for _, r := range rows {
		k := key{r.Entity, r.ID}
		i, ok := index[k]
		if !ok {
			i = len(out)
			index[k] = i
			out = append(out, Change{Entity: r.Entity, ID: r.ID})
		}
		if r.Field == "" {
			at := r.UpdatedAt
			out[i].Deleted = &at
			continue
		}
		if out[i].Fields == nil {
			out[i].Fields = map[string]FieldValue{}
		}
		out[i].Fields[r.Field] = FieldValue{Value: r.Value, UpdatedAt: r.UpdatedAt}
	}
	return out
}
