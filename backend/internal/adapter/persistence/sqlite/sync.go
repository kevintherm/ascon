package sqlite

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"time"

	"github.com/kevintherm/ascon/backend/internal/adapter/persistence/sqlite/sqlcgen"
	"github.com/kevintherm/ascon/backend/internal/domain/library"
)

// Sync implements library.Repository.
type Sync struct{ db *DB }

// NewSync returns the sync repository.
func NewSync(db *DB) *Sync { return &Sync{db} }

var _ library.Repository = (*Sync)(nil)

// Update runs fn in one write transaction for the account.
func (s *Sync) Update(ctx context.Context, accountID string, fn func(library.Store) error) error {
	return s.db.inTx(ctx, func(q *sqlcgen.Queries) error {
		return fn(&syncStore{ctx: ctx, q: q, account: accountID})
	})
}

// Since returns up to limit rows after cursor, oldest first.
func (s *Sync) Since(ctx context.Context, accountID string, cursor int64, limit int) ([]library.Row, error) {
	rows, err := s.db.r.SyncSince(ctx, sqlcgen.SyncSinceParams{AccountID: accountID, Cursor: cursor, RowLimit: int64(limit)})
	if err != nil {
		return nil, err
	}
	out := make([]library.Row, 0, len(rows))
	for _, r := range rows {
		row, err := toRow(r.Entity, r.RecordID, r.Field, r.Value, r.UpdatedAt, r.Seq)
		if err != nil {
			return nil, err
		}
		out = append(out, row)
	}
	return out, nil
}

// AtSeq returns every row written with seq.
func (s *Sync) AtSeq(ctx context.Context, accountID string, seq int64) ([]library.Row, error) {
	rows, err := s.db.r.SyncAtSeq(ctx, sqlcgen.SyncAtSeqParams{AccountID: accountID, Seq: seq})
	if err != nil {
		return nil, err
	}
	out := make([]library.Row, 0, len(rows))
	for _, r := range rows {
		row, err := toRow(r.Entity, r.RecordID, r.Field, r.Value, r.UpdatedAt, r.Seq)
		if err != nil {
			return nil, err
		}
		out = append(out, row)
	}
	return out, nil
}

func toRow(entity, id, field, value, updatedAt string, seq int64) (library.Row, error) {
	at, err := parseTime(updatedAt)
	if err != nil {
		return library.Row{}, err
	}
	row := library.Row{Entity: entity, ID: id, Field: field, UpdatedAt: at, Seq: seq}
	if field != "" {
		row.Value = json.RawMessage(value)
	}
	return row, nil
}

type syncStore struct {
	ctx     context.Context
	q       *sqlcgen.Queries
	account string
}

func (s *syncStore) Field(entity, id, field string) (library.FieldValue, bool, error) {
	row, err := s.q.SyncField(s.ctx, sqlcgen.SyncFieldParams{AccountID: s.account, Entity: entity, RecordID: id, Field: field})
	if errors.Is(err, sql.ErrNoRows) {
		return library.FieldValue{}, false, nil
	}
	if err != nil {
		return library.FieldValue{}, false, err
	}
	at, err := parseTime(row.UpdatedAt)
	if err != nil {
		return library.FieldValue{}, false, err
	}
	return library.FieldValue{Value: json.RawMessage(row.Value), UpdatedAt: at}, true, nil
}

func (s *syncStore) PutField(entity, id, field string, v library.FieldValue, seq int64) error {
	return s.q.PutSyncField(s.ctx, sqlcgen.PutSyncFieldParams{
		AccountID: s.account, Entity: entity, RecordID: id, Field: field,
		Value: string(v.Value), UpdatedAt: formatTime(v.UpdatedAt), Seq: seq,
	})
}

func (s *syncStore) Tombstone(entity, id string) (time.Time, bool, error) {
	at, err := s.q.SyncTombstone(s.ctx, sqlcgen.SyncTombstoneParams{AccountID: s.account, Entity: entity, RecordID: id})
	if errors.Is(err, sql.ErrNoRows) {
		return time.Time{}, false, nil
	}
	if err != nil {
		return time.Time{}, false, err
	}
	t, err := parseTime(at)
	return t, err == nil, err
}

func (s *syncStore) PutTombstone(entity, id string, at time.Time, seq int64) error {
	return s.q.PutSyncTombstone(s.ctx, sqlcgen.PutSyncTombstoneParams{
		AccountID: s.account, Entity: entity, RecordID: id, UpdatedAt: formatTime(at), Seq: seq,
	})
}

func (s *syncStore) NextSeq() (int64, error) {
	return s.q.NextSyncSeq(s.ctx, s.account)
}
