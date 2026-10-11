package metadata

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"slices"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain/series"
	"github.com/kevintherm/ascon/backend/internal/usecase/searchmetadata"
)

// saved replays the responses in saved/, so no test reaches the services.
var saved = &http.Client{Transport: Saved{}}

func TestAniListReadsTitlesFormatAndStatus(t *testing.T) {
	got, err := NewAniList(saved, 0).Search(context.Background(), "Solo Leveling", 25)
	if err != nil {
		t.Fatal(err)
	}
	solo := got[0]
	if solo.Ref != "anilist:105398" || solo.Title != "Na Honjaman Level Up" {
		t.Fatalf("first = %s %q, want Solo Leveling's AniList entry", solo.Ref, solo.Title)
	}
	if !slices.Contains(solo.AltTitles, "Solo Leveling") || !slices.Contains(solo.AltTitles, "나 혼자만 레벨업") {
		t.Errorf("AltTitles = %v, want the English and native titles", solo.AltTitles)
	}
	if solo.Format != series.FormatManhwa || solo.Status != series.StatusCompleted || solo.Year != 2018 {
		t.Errorf("format, status, year = %s, %s, %d", solo.Format, solo.Status, solo.Year)
	}
	if solo.CoverURL == "" {
		t.Error("no cover")
	}
}

func TestMangaUpdatesKeepsTheTitleTheSearchHit(t *testing.T) {
	got, err := NewMangaUpdates(saved, 0).Search(context.Background(), "Trash of the Count's Family", 25)
	if err != nil {
		t.Fatal(err)
	}
	first := got[0]
	if first.Title != "Lout of Count's Family" || !slices.Contains(first.AltTitles, "Trash of the Count's Family") {
		t.Fatalf("first = %q %v, want the hit title kept as an alternate", first.Title, first.AltTitles)
	}
	if first.Format != series.FormatManhwa || first.Year != 2020 || first.Status != series.StatusUnknown {
		t.Errorf("format, year, status = %s, %d, %s", first.Format, first.Year, first.Status)
	}
	if got[1].Format != series.FormatNovel {
		t.Errorf("second format = %s, want the novel told apart", got[1].Format)
	}
}

func TestAnUnsavedQueryFindsNothing(t *testing.T) {
	got, err := NewAniList(saved, 0).Search(context.Background(), "never saved", 25)
	if err != nil || len(got) != 0 {
		t.Fatalf("got %v, %v; want no results", got, err)
	}
}

func search(t *testing.T, query string) []series.Metadata {
	t.Helper()
	s := searchmetadata.New(
		[]series.Searcher{NewAniList(saved, 0), NewMangaUpdates(saved, 0)},
		searchmetadata.Config{CacheFor: time.Hour, MaxCached: 10},
		time.Now,
	)
	got, err := s.Search(context.Background(), query, 10)
	if err != nil {
		t.Fatal(err)
	}
	return got
}

func TestBothServicesMergeIntoOneSeries(t *testing.T) {
	solo := search(t, "Solo Leveling")[0]
	if solo.Ref != "anilist:105398" || solo.OtherRef != "mangaupdates:15180124327" {
		t.Errorf("Solo Leveling = %s with %s, want AniList's entry joined to MangaUpdates'", solo.Ref, solo.OtherRef)
	}
	// MangaUpdates lists it as Lout of Count's Family, with the searched title as the hit.
	trash := search(t, "Trash of the Count's Family")[0]
	if trash.OtherRef == "" {
		t.Errorf("Trash of the Count's Family = %s, not joined to the other service", trash.Ref)
	}
}

func TestATitleNoServiceKnowsRanksLooseMatchesLow(t *testing.T) {
	// MangaUpdates answers with loose matches even for nonsense.
	got := search(t, "zzqxv no such series")
	if len(got) == 0 {
		t.Fatal("want MangaUpdates' loose matches passed on for the app to judge")
	}
	for _, m := range got {
		if !strings.HasPrefix(m.Ref, "mangaupdates:") {
			t.Errorf("%s from AniList, which found nothing", m.Ref)
		}
	}
}

func TestRequestsNameAsconAndWaitAfterA429(t *testing.T) {
	var calls atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		if r.Header.Get("User-Agent") != UserAgent {
			t.Errorf("User-Agent = %q", r.Header.Get("User-Agent"))
		}
		w.Header().Set("Retry-After", "30")
		w.WriteHeader(http.StatusTooManyRequests)
	}))
	defer server.Close()

	a := NewAniList(server.Client(), 0)
	a.url = server.URL
	_, err := a.Search(context.Background(), "Solo Leveling", 10)
	var limited *series.RateLimitedError
	if !errors.As(err, &limited) || limited.RetryAfter != 30*time.Second {
		t.Fatalf("err = %v, want to wait 30s", err)
	}
	// The next search doesn't call again while the service asked to wait.
	if _, err := a.Search(context.Background(), "Solo Leveling", 10); !errors.As(err, &limited) {
		t.Fatalf("err = %v, want still rate limited", err)
	}
	if calls.Load() != 1 {
		t.Fatalf("calls = %d, want 1", calls.Load())
	}
}

func TestRequestsAreSpacedApart(t *testing.T) {
	p := newPolite(series.AniList, nil, 2*time.Second)
	start := time.Date(2026, 10, 11, 8, 0, 0, 0, time.UTC)
	p.now = func() time.Time { return start }
	ctx, cancel := context.WithCancel(context.Background())
	if err := p.wait(ctx); err != nil {
		t.Fatal(err)
	}
	// The second request's turn is 2 seconds off; cancelling shows it waited.
	cancel()
	if err := p.wait(ctx); !errors.Is(err, context.Canceled) {
		t.Fatalf("err = %v, want the second request held back", err)
	}
	// Three more turns would be 8 seconds off, past the queue limit.
	p.next = start.Add(8 * time.Second)
	var limited *series.RateLimitedError
	if err := p.wait(context.Background()); !errors.As(err, &limited) {
		t.Fatalf("err = %v, want refused rather than queued", err)
	}
}
