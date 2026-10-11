// Package searchmetadata searches AniList and MangaUpdates for the series a
// detected title belongs to. Answers are cached briefly, so the same title
// from many phones reaches each service once, and only searches are kept,
// never a copy of either catalog.
package searchmetadata

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"sync"
	"time"
	"unicode/utf8"

	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/series"
)

// Limits on a search, as in contracts/openapi.yaml.
const (
	MaxQueryLength = 200
	DefaultLimit   = 10
	MaxLimit       = 25
)

// Config tunes the cache.
type Config struct {
	// CacheFor keeps an answer both services gave.
	CacheFor time.Duration
	// PartialCacheFor keeps an answer one service failed, shorter, so it is
	// asked again soon but not on every phone's search.
	PartialCacheFor time.Duration
	// MaxCached bounds the cache's entries.
	MaxCached int
}

// Service searches every source and merges their answers.
type Service struct {
	sources []series.Searcher
	cfg     Config
	now     func() time.Time

	mu    sync.Mutex
	cache map[string]cached
}

type cached struct {
	results []series.Metadata
	until   time.Time
}

// New returns the service. Sources are asked together; on equal scores the
// first source's series come first.
func New(sources []series.Searcher, cfg Config, now func() time.Time) *Service {
	return &Service{sources: sources, cfg: cfg, now: now, cache: map[string]cached{}}
}

// Search returns up to limit series for query, best match first. It fails
// only when every source fails: with a *series.RateLimitedError when every
// source asked to wait, else with series.ErrUpstream.
func (s *Service) Search(ctx context.Context, query string, limit int) ([]series.Metadata, error) {
	query = strings.TrimSpace(query)
	if query == "" || utf8.RuneCountInString(query) > MaxQueryLength {
		return nil, domain.Invalidf("q must be 1 to %d characters", MaxQueryLength)
	}
	if limit == 0 {
		limit = DefaultLimit
	}
	if limit < 1 || limit > MaxLimit {
		return nil, domain.Invalidf("limit must be 1 to %d", MaxLimit)
	}
	key := normalize(query)
	if key == "" {
		return nil, domain.Invalidf("q must contain a letter or digit")
	}

	results, ok := s.cached(key)
	if !ok {
		var err error
		if results, err = s.fetch(ctx, plain(query), key); err != nil {
			return nil, err
		}
	}
	return results[:min(limit, len(results))], nil
}

func (s *Service) cached(key string) ([]series.Metadata, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	c, ok := s.cache[key]
	if !ok || !s.now().Before(c.until) {
		return nil, false
	}
	return c.results, true
}

type answer struct {
	results []series.Metadata
	err     error
}

// fetch asks every source at once, each for MaxLimit series so one cached
// answer serves any limit.
func (s *Service) fetch(ctx context.Context, query, key string) ([]series.Metadata, error) {
	answers := make([]answer, len(s.sources))
	var wg sync.WaitGroup
	for i, source := range s.sources {
		wg.Go(func() {
			results, err := source.Search(ctx, query, MaxLimit)
			answers[i] = answer{results, err}
		})
	}
	wg.Wait()

	var lists [][]series.Metadata
	var errs []error
	for _, a := range answers {
		if a.err != nil {
			errs = append(errs, a.err)
			continue
		}
		lists = append(lists, a.results)
	}
	if len(lists) == 0 {
		return nil, failure(errs)
	}

	results := rank(key, merge(lists))
	keep := s.cfg.CacheFor
	if len(errs) > 0 {
		keep = s.cfg.PartialCacheFor
	}
	s.store(key, results, keep)
	return results, nil
}

// failure is the error for a search every source failed.
func failure(errs []error) error {
	var longest *series.RateLimitedError
	for _, err := range errs {
		var limited *series.RateLimitedError
		if !errors.As(err, &limited) {
			// Only ErrUpstream is kept in the chain: one service waiting doesn't
			// make the whole search a rate limit.
			return fmt.Errorf("%w: %s", series.ErrUpstream, errors.Join(errs...).Error())
		}
		if longest == nil || limited.RetryAfter > longest.RetryAfter {
			longest = limited
		}
	}
	if longest == nil {
		return series.ErrUpstream
	}
	return longest
}

func (s *Service) store(key string, results []series.Metadata, keep time.Duration) {
	if keep <= 0 {
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	now := s.now()
	if len(s.cache) >= s.cfg.MaxCached {
		for k, c := range s.cache {
			if !now.Before(c.until) {
				delete(s.cache, k)
			}
		}
	}
	// Still full of live answers: drop any one, since the cache only saves requests.
	for k := range s.cache {
		if len(s.cache) < s.cfg.MaxCached {
			break
		}
		delete(s.cache, k)
	}
	s.cache[key] = cached{results: results, until: now.Add(keep)}
}
