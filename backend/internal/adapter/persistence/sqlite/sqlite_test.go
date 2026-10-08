package sqlite

import (
	"context"
	"encoding/json"
	"errors"
	"path/filepath"
	"testing"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/account"
	"github.com/kevintherm/ascon/backend/internal/domain/library"
	"github.com/kevintherm/ascon/backend/internal/domain/rule"
)

var now = time.Date(2026, 10, 8, 10, 0, 0, 0, time.UTC)

func openTest(t *testing.T) *DB {
	t.Helper()
	db, err := Open(context.Background(), filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = db.Close() })
	return db
}

func newAccount(t *testing.T, db *DB, tier account.Tier) (account.Account, string) {
	t.Helper()
	acc, token, err := NewDevAccounts(db).Create(context.Background(), tier, now)
	if err != nil {
		t.Fatal(err)
	}
	return acc, token
}

func TestDevAccountsVerify(t *testing.T) {
	ctx := context.Background()
	db := openTest(t)
	acc, token := newAccount(t, db, account.Premium)

	got, err := NewDevAccounts(db).Verify(ctx, token)
	if err != nil || got != acc {
		t.Fatalf("Verify = %+v, %v; want %+v", got, err, acc)
	}
	if _, err := NewDevAccounts(db).Verify(ctx, "nope"); !errors.Is(err, domain.ErrUnauthenticated) {
		t.Fatalf("Verify(bad token) error = %v, want ErrUnauthenticated", err)
	}
}

func TestQuotaStopsAtLimit(t *testing.T) {
	ctx := context.Background()
	db := openTest(t)
	acc, _ := newAccount(t, db, account.Free)
	q := NewQuotas(db)

	for i := 1; i <= 2; i++ {
		used, ok, err := q.Use(ctx, acc.ID, "2026-10", 2)
		if err != nil || !ok || used != i {
			t.Fatalf("use %d: used=%d ok=%v err=%v", i, used, ok, err)
		}
	}
	if _, ok, err := q.Use(ctx, acc.ID, "2026-10", 2); err != nil || ok {
		t.Fatalf("third use: ok=%v err=%v, want refused", ok, err)
	}
	if err := q.Refund(ctx, acc.ID, "2026-10"); err != nil {
		t.Fatal(err)
	}
	if used, _ := q.Used(ctx, acc.ID, "2026-10"); used != 1 {
		t.Fatalf("after refund used = %d, want 1", used)
	}
	if used, _ := q.Used(ctx, acc.ID, "2026-11"); used != 0 {
		t.Fatalf("next month used = %d, want 0", used)
	}
}

func testRule(domainName string, version int, fingerprint string) rule.Stored {
	return rule.Stored{
		Rule: rule.Rule{
			SchemaVersion: 1, Domain: domainName, Version: version, Fingerprint: fingerprint,
			ChapterPage: rule.ChapterPage{URL: "https://" + domainName + "/c/.*", Images: rule.Images{Selector: "img"}},
		},
		Status:    rule.Active,
		CreatedAt: now,
	}
}

func TestRulesLatestSkipsRetired(t *testing.T) {
	ctx := context.Background()
	db := openTest(t)
	rules := NewRules(db)

	for v := 1; v <= 3; v++ {
		if err := rules.Insert(ctx, testRule("a.example", v, "00000000000000ff")); err != nil {
			t.Fatal(err)
		}
	}
	if err := rules.SetStatus(ctx, "a.example", 3, rule.Retired); err != nil {
		t.Fatal(err)
	}

	got, err := rules.Latest(ctx, "a.example")
	if err != nil || got.Rule.Version != 2 {
		t.Fatalf("Latest = v%d, %v; want v2", got.Rule.Version, err)
	}
	if _, err := rules.Latest(ctx, "b.example"); !errors.Is(err, domain.ErrNotFound) {
		t.Fatalf("Latest(unknown) error = %v, want ErrNotFound", err)
	}

	fp, err := rules.Fingerprinted(ctx)
	if err != nil || len(fp) != 1 || fp[0].Rule.Version != 2 {
		t.Fatalf("Fingerprinted = %+v, %v; want only v2", fp, err)
	}
}

func TestReportsCountDevicesOnce(t *testing.T) {
	ctx := context.Background()
	db := openTest(t)
	rules := NewRules(db)
	if err := rules.Insert(ctx, testRule("a.example", 1, "")); err != nil {
		t.Fatal(err)
	}

	report := rule.Report{Domain: "a.example", Version: 1, DeviceID: "d1", Problem: "no_images", URL: "https://a.example/c/1", CreatedAt: now}
	for i := 0; i < 3; i++ {
		if n, err := rules.AddReport(ctx, report); err != nil || n != 1 {
			t.Fatalf("repeat report: devices=%d err=%v, want 1", n, err)
		}
	}
	report.DeviceID = "d2"
	if n, _ := rules.AddReport(ctx, report); n != 2 {
		t.Fatalf("second device: devices=%d, want 2", n)
	}

	if _, err := rules.AddHealth(ctx, "a.example", 1, rule.Health{Successes: 5, EmptyResults: 1}); err != nil {
		t.Fatal(err)
	}
	h, _ := rules.AddHealth(ctx, "a.example", 1, rule.Health{Successes: 2, BackwardJumps: 1})
	if h != (rule.Health{Successes: 7, EmptyResults: 1, BackwardJumps: 1}) {
		t.Fatalf("health = %+v", h)
	}
}

func TestSyncMergeAndPull(t *testing.T) {
	ctx := context.Background()
	db := openTest(t)
	acc, _ := newAccount(t, db, account.Free)
	sync := NewSync(db)

	const id = "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90"
	push := func(changes ...library.Change) {
		t.Helper()
		err := sync.Update(ctx, acc.ID, func(s library.Store) error {
			_, err := library.Merge(s, changes, now)
			return err
		})
		if err != nil {
			t.Fatal(err)
		}
	}
	push(library.Change{Entity: library.Progress, ID: id, Fields: map[string]library.FieldValue{
		"chapter": {Value: json.RawMessage(`"10"`), UpdatedAt: now.Add(-time.Minute)},
	}})
	deleted := now.Add(-time.Second)
	push(library.Change{Entity: library.LibraryEntry, ID: id, Deleted: &deleted})
	// Older write must lose.
	push(library.Change{Entity: library.Progress, ID: id, Fields: map[string]library.FieldValue{
		"chapter": {Value: json.RawMessage(`"9"`), UpdatedAt: now.Add(-time.Hour)},
	}})

	rows, err := sync.Since(ctx, acc.ID, 0, 10)
	if err != nil {
		t.Fatal(err)
	}
	if len(rows) != 2 || rows[0].Seq != 1 || string(rows[0].Value) != `"10"` || rows[1].Field != "" || rows[1].Seq != 2 {
		t.Fatalf("Since(0) = %+v", rows)
	}
	if rows, _ := sync.Since(ctx, acc.ID, 1, 10); len(rows) != 1 || rows[0].Seq != 2 {
		t.Fatalf("Since(1) = %+v", rows)
	}
	if rows, _ := sync.AtSeq(ctx, acc.ID, 1); len(rows) != 1 {
		t.Fatalf("AtSeq(1) = %+v", rows)
	}
}
