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

	"github.com/kevintherm/ascon/backend/internal/adapter/evaluator"
	httpadapter "github.com/kevintherm/ascon/backend/internal/adapter/http"
	"github.com/kevintherm/ascon/backend/internal/adapter/llm"
	"github.com/kevintherm/ascon/backend/internal/adapter/persistence/sqlite"
	"github.com/kevintherm/ascon/backend/internal/usecase/generaterule"
	"github.com/kevintherm/ascon/backend/internal/usecase/registerdevice"
	"github.com/kevintherm/ascon/backend/internal/usecase/reportrule"
	"github.com/kevintherm/ascon/backend/internal/usecase/resolverule"
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
		llm.Unconfigured{}, evaluator.HTML{},
		generaterule.Config{Limits: cfg.limits, MinImages: 3, Timeout: 2 * time.Minute},
		now, logger,
	)
	if err := generate.Recover(ctx); err != nil {
		return err
	}

	api := &httpadapter.Server{
		Devices:  registerdevice.New(sqlite.NewDevices(db), now),
		Accounts: sqlite.NewDevAccounts(db),
		Resolve:  resolverule.New(rules, cfg.signer),
		Reports:  reportrule.New(rules, now),
		Generate: generate,
		Sync:     synclibrary.New(sqlite.NewSync(db), now),
		Log:      logger,
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
