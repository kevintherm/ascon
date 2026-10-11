// Package metadata searches AniList and MangaUpdates. Each service gets one
// polite client: an Ascon User-Agent, requests spaced apart, and a pause for
// as long as the service asks after a 429.
//
// Development and tests never reach the services: Saved answers from
// responses saved once by cmd/devmetadata.
package metadata

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"sync"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain/series"
)

// UserAgent names Ascon to the services, as both ask of API clients.
const UserAgent = "Ascon/0.1 (manga reading tracker; https://ascon.app)"

// Spacing between requests and limits on waiting, per service.
const (
	DefaultInterval = 2 * time.Second
	// maxQueue is the longest a search waits for its turn. Past it the search
	// fails as rate limited instead of piling up behind others.
	maxQueue = 6 * time.Second
	// defaultRetryAfter is the pause after a 429 without a usable Retry-After.
	defaultRetryAfter = time.Minute
	maxResponse       = 2 << 20
)

// polite sends one service's requests in turn, at least interval apart.
type polite struct {
	source   series.Source
	client   *http.Client
	interval time.Duration
	now      func() time.Time

	mu   sync.Mutex
	next time.Time
}

func newPolite(source series.Source, client *http.Client, interval time.Duration) *polite {
	if client == nil {
		client = &http.Client{Timeout: 10 * time.Second}
	}
	return &polite{source: source, client: client, interval: interval, now: time.Now}
}

// postJSON sends body to url and decodes the JSON answer into out.
func (p *polite) postJSON(ctx context.Context, url string, body, out any) error {
	payload, err := json.Marshal(body)
	if err != nil {
		return err
	}
	if err := p.wait(ctx); err != nil {
		return err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(payload))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", UserAgent)

	resp, err := p.client.Do(req)
	if err != nil {
		return fmt.Errorf("%w: %s: %w", series.ErrUpstream, p.source, err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode == http.StatusTooManyRequests {
		after := retryAfter(resp.Header.Get("Retry-After"))
		p.pause(after)
		return &series.RateLimitedError{Source: p.source, RetryAfter: after}
	}
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("%w: %s answered %d", series.ErrUpstream, p.source, resp.StatusCode)
	}
	data, err := io.ReadAll(io.LimitReader(resp.Body, maxResponse))
	if err != nil {
		return fmt.Errorf("%w: %s: %w", series.ErrUpstream, p.source, err)
	}
	if err := json.Unmarshal(data, out); err != nil {
		return fmt.Errorf("%w: %s sent JSON Ascon can't read: %w", series.ErrUpstream, p.source, err)
	}
	return nil
}

// wait takes the next turn and sleeps until it comes. A turn further off than
// maxQueue is refused, so a burst of searches fails fast instead of queueing.
func (p *polite) wait(ctx context.Context) error {
	p.mu.Lock()
	now := p.now()
	turn := later(now, p.next)
	if delay := turn.Sub(now); delay > maxQueue {
		p.mu.Unlock()
		return &series.RateLimitedError{Source: p.source, RetryAfter: delay}
	}
	p.next = turn.Add(p.interval)
	p.mu.Unlock()

	delay := turn.Sub(now)
	if delay <= 0 {
		return nil
	}
	timer := time.NewTimer(delay)
	defer timer.Stop()
	select {
	case <-timer.C:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}

// pause holds every request back for after.
func (p *polite) pause(after time.Duration) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.next = later(p.next, p.now().Add(after))
}

// retryAfter reads a Retry-After in seconds. HTTP dates are rare from APIs
// and fall back to the default.
func retryAfter(header string) time.Duration {
	if n, err := strconv.Atoi(header); err == nil && n > 0 {
		return time.Duration(n) * time.Second
	}
	return defaultRetryAfter
}

func later(a, b time.Time) time.Time {
	if a.After(b) {
		return a
	}
	return b
}
