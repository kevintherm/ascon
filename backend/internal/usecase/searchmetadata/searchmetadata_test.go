package searchmetadata

import (
	"context"
	"errors"
	"reflect"
	"testing"
	"time"

	"github.com/kevintherm/ascon/backend/internal/domain"
	"github.com/kevintherm/ascon/backend/internal/domain/series"
)

type fakeSource struct {
	source  series.Source
	results []series.Metadata
	err     error
	calls   int
	queries []string
}

func (f *fakeSource) Source() series.Source { return f.source }

func (f *fakeSource) Search(_ context.Context, query string, _ int) ([]series.Metadata, error) {
	f.calls++
	f.queries = append(f.queries, query)
	return f.results, f.err
}

var (
	soloAniList = series.Metadata{
		Ref: "anilist:105398", Title: "Na Honjaman Level Up", AltTitles: []string{"Solo Leveling", "나 혼자만 레벨업"},
		Format: series.FormatManhwa, Status: series.StatusCompleted, Year: 2018, CoverURL: "https://img.anili.st/105398.jpg",
	}
	soloNovel = series.Metadata{
		Ref: "anilist:101024", Title: "Na Honjaman Level Up", AltTitles: []string{"Solo Leveling"},
		Format: series.FormatNovel, Status: series.StatusCompleted,
	}
	soloMU = series.Metadata{
		Ref: "mangaupdates:15180124327", Title: "Solo Leveling", AltTitles: []string{"I Level Up Alone"},
		Format: series.FormatManhwa, Status: series.StatusUnknown, Year: 2018,
	}
	ragnarokMU = series.Metadata{Ref: "mangaupdates:1", Title: "Solo Leveling: Ragnarok", Format: series.FormatManhwa}
)

type clock struct{ t time.Time }

func (c *clock) now() time.Time { return c.t }

func service(sources ...*fakeSource) (*Service, *clock) {
	c := &clock{t: time.Date(2026, 10, 11, 8, 0, 0, 0, time.UTC)}
	list := make([]series.Searcher, len(sources))
	for i, s := range sources {
		list[i] = s
	}
	cfg := Config{CacheFor: time.Hour, PartialCacheFor: 5 * time.Minute, MaxCached: 100}
	return New(list, cfg, c.now), c
}

func refs(list []series.Metadata) []string {
	out := make([]string, len(list))
	for i, m := range list {
		out[i] = m.Ref
	}
	return out
}

func TestTheSameSeriesOnBothServicesIsOneResult(t *testing.T) {
	anilist := &fakeSource{source: series.AniList, results: []series.Metadata{soloAniList, soloNovel}}
	mu := &fakeSource{source: series.MangaUpdates, results: []series.Metadata{soloMU, ragnarokMU}}
	s, _ := service(anilist, mu)

	got, err := s.Search(context.Background(), "Solo Leveling", 10)
	if err != nil {
		t.Fatal(err)
	}
	if want := []string{"anilist:105398", "anilist:101024", "mangaupdates:1"}; !reflect.DeepEqual(refs(got), want) {
		t.Fatalf("refs = %v, want %v", refs(got), want)
	}
	solo := got[0]
	if solo.OtherRef != soloMU.Ref {
		t.Errorf("OtherRef = %q, want the MangaUpdates entry", solo.OtherRef)
	}
	if want := []string{"Solo Leveling", "나 혼자만 레벨업", "I Level Up Alone"}; !reflect.DeepEqual(solo.AltTitles, want) {
		t.Errorf("AltTitles = %v, want %v", solo.AltTitles, want)
	}
	// The novel shares the title but not the format, so it stays apart.
	if got[1].OtherRef != "" {
		t.Errorf("the novel was joined to %q", got[1].OtherRef)
	}
}

func TestCloserTitlesComeFirst(t *testing.T) {
	anilist := &fakeSource{source: series.AniList, results: []series.Metadata{
		{Ref: "anilist:1", Title: "Overgeared Side Stories"},
		{Ref: "anilist:2", Title: "Overgeared"},
		{Ref: "anilist:3", Title: "Geared Over"},
	}}
	s, _ := service(anilist)
	got, err := s.Search(context.Background(), "overgeared!", 10)
	if err != nil {
		t.Fatal(err)
	}
	if want := []string{"anilist:2", "anilist:1", "anilist:3"}; !reflect.DeepEqual(refs(got), want) {
		t.Fatalf("refs = %v, want %v", refs(got), want)
	}
}

func TestAnswersAreCachedBriefly(t *testing.T) {
	anilist := &fakeSource{source: series.AniList, results: []series.Metadata{soloAniList}}
	s, c := service(anilist)
	ctx := context.Background()

	for _, q := range []string{"Solo Leveling", "  solo leveling!  "} {
		if _, err := s.Search(ctx, q, 10); err != nil {
			t.Fatal(err)
		}
	}
	if anilist.calls != 1 {
		t.Fatalf("calls = %d, want the second search answered from the cache", anilist.calls)
	}
	c.t = c.t.Add(time.Hour)
	if _, err := s.Search(ctx, "Solo Leveling", 10); err != nil {
		t.Fatal(err)
	}
	if anilist.calls != 2 {
		t.Fatalf("calls = %d, want the source asked again after an hour", anilist.calls)
	}
}

func TestOneFailedServiceStillAnswersAndIsAskedAgainSooner(t *testing.T) {
	anilist := &fakeSource{source: series.AniList, err: errors.New("timeout")}
	mu := &fakeSource{source: series.MangaUpdates, results: []series.Metadata{soloMU}}
	s, c := service(anilist, mu)
	ctx := context.Background()

	got, err := s.Search(ctx, "Solo Leveling", 10)
	if err != nil || len(got) != 1 {
		t.Fatalf("got %v, %v; want MangaUpdates' answer", refs(got), err)
	}
	c.t = c.t.Add(5 * time.Minute)
	if _, err := s.Search(ctx, "Solo Leveling", 10); err != nil {
		t.Fatal(err)
	}
	if anilist.calls != 2 {
		t.Fatalf("calls = %d, want a partial answer kept only 5 minutes", anilist.calls)
	}
}

func TestEveryServiceFailing(t *testing.T) {
	ctx := context.Background()
	limited := func(after time.Duration) error {
		return &series.RateLimitedError{Source: series.AniList, RetryAfter: after}
	}

	s, _ := service(
		&fakeSource{source: series.AniList, err: limited(10 * time.Second)},
		&fakeSource{source: series.MangaUpdates, err: limited(30 * time.Second)},
	)
	_, err := s.Search(ctx, "Solo Leveling", 10)
	var wait *series.RateLimitedError
	if !errors.As(err, &wait) || wait.RetryAfter != 30*time.Second {
		t.Fatalf("err = %v, want to wait for the slower service", err)
	}

	s, _ = service(
		&fakeSource{source: series.AniList, err: limited(10 * time.Second)},
		&fakeSource{source: series.MangaUpdates, err: errors.New("502")},
	)
	if _, err := s.Search(ctx, "Solo Leveling", 10); !errors.Is(err, series.ErrUpstream) {
		t.Fatalf("err = %v, want ErrUpstream", err)
	}
}

func TestLimitsAndQueries(t *testing.T) {
	anilist := &fakeSource{source: series.AniList, results: []series.Metadata{soloAniList, soloNovel}}
	s, _ := service(anilist)
	ctx := context.Background()

	got, err := s.Search(ctx, "Solo Leveling", 1)
	if err != nil || len(got) != 1 {
		t.Fatalf("got %d results, %v; want 1", len(got), err)
	}
	for _, c := range []struct {
		q     string
		limit int
	}{{"", 10}, {"   ", 10}, {"!!!", 10}, {string(make([]rune, 201)), 10}, {"ok", 26}, {"ok", -1}} {
		if _, err := s.Search(ctx, c.q, c.limit); !errors.Is(err, domain.ErrInvalid) {
			t.Errorf("Search(%q, %d) = %v, want ErrInvalid", c.q, c.limit, err)
		}
	}
	if anilist.calls != 1 {
		t.Errorf("calls = %d, want invalid searches never sent", anilist.calls)
	}
}

func TestServicesAreAskedWithPlainQuotesAndLetters(t *testing.T) {
	anilist := &fakeSource{source: series.AniList}
	s, _ := service(anilist)
	for _, q := range []string{"Omniscient Reader’s Viewpoint", "Ｓｏｌｏ　Ｌｅｖｅｌｉｎｇ", "Salt — Iron “Kitchen”"} {
		if _, err := s.Search(context.Background(), q, 10); err != nil {
			t.Fatal(err)
		}
	}
	want := []string{"Omniscient Reader's Viewpoint", "Solo Leveling", `Salt - Iron "Kitchen"`}
	if !reflect.DeepEqual(anilist.queries, want) {
		t.Fatalf("queries = %q, want %q", anilist.queries, want)
	}
}
