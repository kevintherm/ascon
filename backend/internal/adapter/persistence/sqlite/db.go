// Package sqlite stores everything in one SQLite file, through queries that
// sqlc generates from queries/*.sql. Writes go through a single connection;
// reads use a small pool. See AGENTS.md, Stack.
package sqlite

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"net/url"
	"time"

	"github.com/pressly/goose/v3"
	_ "modernc.org/sqlite" // registers the "sqlite" driver

	"github.com/kevintherm/ascon/backend/internal/adapter/persistence/sqlite/sqlcgen"
	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/migrations"
)

// DB holds the writer and reader connections to one database file.
type DB struct {
	writer *sql.DB
	reader *sql.DB
	w      *sqlcgen.Queries
	r      *sqlcgen.Queries
}

// Open opens the database at path, creating it if needed, and applies
// pending migrations.
func Open(ctx context.Context, path string) (*DB, error) {
	writer, err := sql.Open("sqlite", dsn(path, false))
	if err != nil {
		return nil, err
	}
	writer.SetMaxOpenConns(1)

	if err := migrate(ctx, writer); err != nil {
		_ = writer.Close()
		return nil, err
	}

	reader, err := sql.Open("sqlite", dsn(path, true))
	if err != nil {
		_ = writer.Close()
		return nil, err
	}
	reader.SetMaxOpenConns(4)

	return &DB{writer: writer, reader: reader, w: sqlcgen.New(writer), r: sqlcgen.New(reader)}, nil
}

// Close closes both connections.
func (db *DB) Close() error {
	return errors.Join(db.reader.Close(), db.writer.Close())
}

func dsn(path string, readOnly bool) string {
	q := url.Values{}
	q.Add("_pragma", "busy_timeout(5000)")
	q.Add("_pragma", "foreign_keys(1)")
	if readOnly {
		q.Add("mode", "ro")
	} else {
		q.Add("_pragma", "journal_mode(WAL)")
		q.Add("_pragma", "synchronous(NORMAL)")
		q.Add("_txlock", "immediate")
	}
	return "file:" + path + "?" + q.Encode()
}

func migrate(ctx context.Context, db *sql.DB) error {
	provider, err := goose.NewProvider(goose.DialectSQLite3, db, migrations.FS)
	if err != nil {
		return fmt.Errorf("migrations: %w", err)
	}
	if _, err := provider.Up(ctx); err != nil {
		return fmt.Errorf("migrations: %w", err)
	}
	return nil
}

// inTx runs fn in a write transaction.
func (db *DB) inTx(ctx context.Context, fn func(*sqlcgen.Queries) error) error {
	tx, err := db.writer.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	if err := fn(db.w.WithTx(tx)); err != nil {
		_ = tx.Rollback()
		return err
	}
	return tx.Commit()
}

// Times are stored as RFC 3339 text in UTC, with nanoseconds.
func formatTime(t time.Time) string { return t.UTC().Format(time.RFC3339Nano) }

func parseTime(s string) (time.Time, error) { return time.Parse(time.RFC3339Nano, s) }

// notFound maps sql.ErrNoRows to domain.ErrNotFound.
func notFound(err error) error {
	if errors.Is(err, sql.ErrNoRows) {
		return domain.ErrNotFound
	}
	return err
}

func nullString(s string) sql.NullString { return sql.NullString{String: s, Valid: s != ""} }
