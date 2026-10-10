// Command devrule stores a detection rule as the newest version for its domain,
// so the app can be tested against the backend before rule generation works.
//
//	go run ./cmd/devrule -db ascon.db -domain 10.0.2.2 rule.json
package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/kevintherm/ascon/backend/internal/adapter/persistence/sqlite"
	"github.com/kevintherm/ascon/backend/internal/domain/rule"
)

func main() {
	dbPath := flag.String("db", "ascon.db", "database file")
	domain := flag.String("domain", "", "store the rule under this domain instead of its own")
	flag.Parse()
	if flag.NArg() != 1 {
		fmt.Fprintln(os.Stderr, "usage: devrule [-db file] [-domain host] rule.json")
		os.Exit(2)
	}
	if err := run(*dbPath, *domain, flag.Arg(0)); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func run(dbPath, domain, file string) error {
	raw, err := os.ReadFile(filepath.Clean(file))
	if err != nil {
		return err
	}
	var r rule.Rule
	if err := json.Unmarshal(raw, &r); err != nil {
		return err
	}
	if domain != "" {
		r.Domain = domain
	}

	ctx := context.Background()
	db, err := sqlite.Open(ctx, dbPath)
	if err != nil {
		return err
	}
	defer func() { _ = db.Close() }()

	rules := sqlite.NewRules(db)
	latest, err := rules.MaxVersion(ctx, r.Domain)
	if err != nil {
		return err
	}
	r.Version = latest + 1
	if err := rules.Insert(ctx, rule.Stored{Rule: r, Status: rule.Active, CreatedAt: time.Now()}); err != nil {
		return err
	}
	fmt.Printf("stored %s version %d\n", r.Domain, r.Version)
	return nil
}
