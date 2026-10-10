package main

import (
	"crypto/ed25519"
	"crypto/rand"
	"encoding/base64"
	"fmt"
	"log/slog"
	"os"
	"strconv"

	"github.com/kevintherm/ascon/backend/internal/adapter/llm"
	"github.com/kevintherm/ascon/backend/internal/adapter/signing"
	"github.com/kevintherm/ascon/backend/internal/domain/account"
	"github.com/kevintherm/ascon/backend/internal/domain/rule"
)

// config is read from ASCON_* environment variables.
type config struct {
	addr   string
	dbPath string
	signer *signing.Signer
	limits account.Limits
	// generator writes rules for AI detection. attempts is how many rules it
	// may offer per request.
	generator rule.Generator
	attempts  int
}

func loadConfig(logger *slog.Logger) (config, error) {
	c := config{
		addr:   env("ASCON_ADDR", ":8080"),
		dbPath: env("ASCON_DB", "ascon.db"),
	}

	free, err := envInt("ASCON_QUOTA_FREE", 10)
	if err != nil {
		return c, err
	}
	premium, err := envInt("ASCON_QUOTA_PREMIUM", 200)
	if err != nil {
		return c, err
	}
	c.limits = account.Limits{account.Free: free, account.Premium: premium}

	c.generator = generator(logger)
	if c.attempts, err = envInt("ASCON_LLM_ATTEMPTS", 2); err != nil {
		return c, err
	}

	keyID := env("ASCON_SIGNING_KEY_ID", "dev")
	seed, err := signingSeed(logger)
	if err != nil {
		return c, err
	}
	if c.signer, err = signing.New(seed, keyID); err != nil {
		return c, err
	}
	return c, nil
}

// generator reads ASCON_LLM_BASE_URL, ASCON_LLM_API_KEY and ASCON_LLM_MODEL
// for an OpenAI-compatible provider. Without them every AI detection request
// is rejected and its quota refunded.
func generator(logger *slog.Logger) rule.Generator {
	baseURL, key, model := os.Getenv("ASCON_LLM_BASE_URL"), os.Getenv("ASCON_LLM_API_KEY"), os.Getenv("ASCON_LLM_MODEL")
	if baseURL == "" || key == "" || model == "" {
		logger.Warn("ASCON_LLM_* is not set; AI detection requests will be rejected")
		return llm.Unconfigured{}
	}
	logger.Info("AI detection enabled", "model", model)
	return &llm.OpenAI{
		BaseURL: baseURL, APIKey: key, Model: model,
		OnUsage: func(u llm.Usage) {
			logger.Info("llm usage", "prompt_tokens", u.Prompt, "completion_tokens", u.Completion,
				"reasoning_tokens", u.Details.Reasoning, "cached_tokens", u.PromptDetails.Cached)
		},
	}
}

// signingSeed reads ASCON_SIGNING_KEY, a base64 Ed25519 seed. Without it the
// server makes a throwaway key, which is fine for development only: rules
// signed with it fail verification after a restart.
func signingSeed(logger *slog.Logger) ([]byte, error) {
	v := os.Getenv("ASCON_SIGNING_KEY")
	if v == "" {
		seed := make([]byte, ed25519.SeedSize)
		if _, err := rand.Read(seed); err != nil {
			return nil, err
		}
		logger.Warn("ASCON_SIGNING_KEY is not set; signing rules with a throwaway key")
		return seed, nil
	}
	seed, err := base64.StdEncoding.DecodeString(v)
	if err != nil {
		return nil, fmt.Errorf("ASCON_SIGNING_KEY is not base64: %w", err)
	}
	return seed, nil
}

func env(name, fallback string) string {
	if v := os.Getenv(name); v != "" {
		return v
	}
	return fallback
}

func envInt(name string, fallback int) (int, error) {
	v := os.Getenv(name)
	if v == "" {
		return fallback, nil
	}
	n, err := strconv.Atoi(v)
	if err != nil || n < 0 {
		return 0, fmt.Errorf("%s must be a non-negative integer", name)
	}
	return n, nil
}
