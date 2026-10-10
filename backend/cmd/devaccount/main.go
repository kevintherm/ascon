// Command devaccount creates an account and prints its bearer token, without
// signing in. It is for development and premium testing.
//
//	go run ./cmd/devaccount -db ascon.db -tier premium
package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"time"

	"github.com/kevintherm/ascon/backend/internal/adapter/persistence/sqlite"
	"github.com/kevintherm/ascon/backend/internal/domain/account"
)

func main() {
	dbPath := flag.String("db", "ascon.db", "database file")
	tier := flag.String("tier", "free", "free or premium")
	flag.Parse()

	if *tier != string(account.Free) && *tier != string(account.Premium) {
		fmt.Fprintln(os.Stderr, "tier must be free or premium")
		os.Exit(2)
	}

	if err := run(account.Tier(*tier), *dbPath); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func run(tier account.Tier, dbPath string) error {
	ctx := context.Background()
	db, err := sqlite.Open(ctx, dbPath)
	if err != nil {
		return err
	}
	defer func() { _ = db.Close() }()

	acc, token, err := sqlite.NewAccounts(db).Create(ctx, tier, time.Now())
	if err != nil {
		return err
	}
	fmt.Printf("account %s (%s)\ntoken   %s\n", acc.ID, acc.Tier, token)
	return nil
}
