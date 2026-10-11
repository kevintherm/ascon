// Command devmetadata saves real AniList and MangaUpdates answers for the
// titles given, for tests and the dev backend to replay. It is the only
// development tool that calls the services, and it is run by hand, rarely:
// once per title, again only when an API changes.
//
//	go run ./cmd/devmetadata "Solo Leveling" "Overgeared"
package main

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"time"

	"github.com/kevintherm/ascon/backend/internal/adapter/metadata"
	"github.com/kevintherm/ascon/backend/internal/domain/series"
)

const savedDir = "internal/adapter/metadata/saved"

// maxTitles keeps a slip from sending dozens of requests.
const maxTitles = 6

func main() {
	titles := os.Args[1:]
	if len(titles) == 0 || len(titles) > maxTitles {
		fmt.Fprintf(os.Stderr, "usage: devmetadata <title>... (1 to %d titles)\n", maxTitles)
		os.Exit(2)
	}
	client := &http.Client{
		Timeout:   15 * time.Second,
		Transport: metadata.Recording{Next: http.DefaultTransport, Dir: savedDir},
	}
	sources := []series.Searcher{
		metadata.NewAniList(client, metadata.DefaultInterval),
		metadata.NewMangaUpdates(client, metadata.DefaultInterval),
	}
	ctx := context.Background()
	failed := false
	for _, title := range titles {
		for _, s := range sources {
			results, err := s.Search(ctx, title, 25)
			if err != nil {
				fmt.Fprintf(os.Stderr, "%s %q: %v\n", s.Source(), title, err)
				failed = true
				continue
			}
			fmt.Printf("%s %q: %d results, saved as %s.json\n", s.Source(), title, len(results), metadata.Slug(title))
		}
	}
	if failed {
		os.Exit(1)
	}
}
