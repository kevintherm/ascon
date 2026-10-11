// Command api starts the Ascon backend. It only wires configuration and
// dependencies; behavior lives under internal/.
package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/kevintherm/ascon/backend/internal/adapter/dotenv"
	"github.com/kevintherm/ascon/backend/internal/adapter/evaluator"
	httpadapter "github.com/kevintherm/ascon/backend/internal/adapter/http"
	"github.com/kevintherm/ascon/backend/internal/adapter/persistence/sqlite"
	"github.com/kevintherm/ascon/backend/internal/usecase/generaterule"
	"github.com/kevintherm/ascon/backend/internal/usecase/registerdevice"
	"github.com/kevintherm/ascon/backend/internal/usecase/reportrule"
	"github.com/kevintherm/ascon/backend/internal/usecase/resolverule"
	"github.com/kevintherm/ascon/backend/internal/usecase/searchmetadata"
	"github.com/kevintherm/ascon/backend/internal/usecase/signin"
	"github.com/kevintherm/ascon/backend/internal/usecase/synclibrary"
)

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	if err := run(logger); err != nil {
		logger.Error("server stopped", "err", err)
		os.Exit(1)
	}
}

func run(logger *slog.Logger) error {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	// A local .env holds development settings such as the LLM key. Real
	// environment variables override it.
	if err := dotenv.Load(".env"); err != nil {
		return err
	}
	cfg, err := loadConfig(logger)
	if err != nil {
		return err
	}

	db, err := sqlite.Open(ctx, cfg.dbPath)
	if err != nil {
		return err
	}
	defer func() { _ = db.Close() }()

	now := time.Now
	rules := sqlite.NewRules(db)
	generate := generaterule.New(
		rules, sqlite.NewCandidates(db), sqlite.NewQuotas(db),
		cfg.generator, evaluator.HTML{},
		generaterule.Config{Limits: cfg.limits, MinImages: 3, Timeout: 3 * time.Minute, Attempts: cfg.attempts,
			FailureCooldown: 7 * 24 * time.Hour},
		now, logger,
	)
	if err := generate.Recover(ctx); err != nil {
		return err
	}

	signIn := signin.New(cfg.identities, sqlite.NewAccounts(db), now)
	api := &httpadapter.Server{
		Devices:  registerdevice.New(sqlite.NewDevices(db), now),
		Accounts: signIn,
		SignIn:   signIn,
		Resolve:  resolverule.New(rules, cfg.signer),
		Reports:  reportrule.New(rules, now),
		Generate: generate,
		Sync:     synclibrary.New(sqlite.NewSync(db), now),
		// Searches are kept an hour, or 5 minutes when one service failed.
		Metadata: searchmetadata.New(cfg.metadata,
			searchmetadata.Config{CacheFor: time.Hour, PartialCacheFor: 5 * time.Minute, MaxCached: 5000}, now),
		Log: logger,
	}

	srv := &http.Server{
		Addr:              cfg.addr,
		Handler:           api.Handler(),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      30 * time.Second,
	}

	serveErr := make(chan error, 1)
	go func() {
		logger.Info("listening", "addr", cfg.addr, "db", cfg.dbPath)
		serveErr <- srv.ListenAndServe()
	}()

	select {
	case err := <-serveErr:
		if errors.Is(err, http.ErrServerClosed) {
			return nil
		}
		return err
	case <-ctx.Done():
	}

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	return srv.Shutdown(shutdownCtx)
}
