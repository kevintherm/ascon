// Command devgenerate asks the configured LLM for a rule for saved chapter
// pages and checks it the way the server does, without a database or quota.
// It reads ASCON_LLM_* from the environment or from .env.
//
//	go run ./cmd/devgenerate https://site.example/a/ch-1=ch1.html https://site.example/a/ch-2=ch2.html
package main

import (
	"context"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/kevintherm/ascon/backend/internal/adapter/dotenv"
	"github.com/kevintherm/ascon/backend/internal/adapter/evaluator"
	"github.com/kevintherm/ascon/backend/internal/adapter/llm"
	"github.com/kevintherm/ascon/backend/internal/domain/rule"
	"github.com/kevintherm/ascon/backend/internal/usecase/generaterule"
)

func main() {
	attempts := flag.Int("attempts", 2, "rules the model may offer")
	minImages := flag.Int("min-images", 3, "images each sample must yield")
	flag.Parse()
	if flag.NArg() == 0 {
		fmt.Fprintln(os.Stderr, "usage: devgenerate [-attempts n] [-min-images n] url=page.html ...")
		os.Exit(2)
	}
	if err := run(flag.Args(), *attempts, *minImages); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func run(args []string, attempts, minImages int) error {
	if err := dotenv.Load(".env"); err != nil {
		return err
	}
	gen := &llm.OpenAI{
		BaseURL: os.Getenv("ASCON_LLM_BASE_URL"), APIKey: os.Getenv("ASCON_LLM_API_KEY"), Model: os.Getenv("ASCON_LLM_MODEL"),
	}
	if gen.BaseURL == "" || gen.APIKey == "" || gen.Model == "" {
		return errors.New("set ASCON_LLM_BASE_URL, ASCON_LLM_API_KEY and ASCON_LLM_MODEL")
	}

	samples, site, err := readSamples(args)
	if err != nil {
		return err
	}
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Minute)
	defer cancel()

	var previous []rule.Attempt
	for n := 1; n <= attempts; n++ {
		start := time.Now()
		r, err := gen.Generate(ctx, site, "", samples, previous)
		if err != nil {
			return err
		}
		out, _ := json.MarshalIndent(r, "", "  ")
		out = []byte(strings.NewReplacer(`\u003c`, "<", `\u003e`, ">", `\u0026`, "&").Replace(string(out)))
		fmt.Printf("attempt %d, %s:\n%s\n", n, time.Since(start).Round(time.Second), out)
		problem := generaterule.Check(evaluator.HTML{}, r, samples, minImages)
		if problem == "" {
			printResults(r, samples)
			fmt.Println("PASSED")
			return nil
		}
		fmt.Println("failed:", problem)
		previous = append(previous, rule.Attempt{Rule: r, Problem: problem})
	}
	return errors.New("REJECTED")
}

func readSamples(args []string) ([]rule.Sample, string, error) {
	var samples []rule.Sample
	var site string
	for _, arg := range args {
		// The last = splits, since a URL's query may hold one.
		i := strings.LastIndex(arg, "=")
		raw, file := arg[:max(i, 0)], arg[i+1:]
		u, err := url.Parse(raw)
		if i < 0 || err != nil || u.Hostname() == "" {
			return nil, "", fmt.Errorf("%q is not url=page.html", arg)
		}
		html, err := os.ReadFile(filepath.Clean(file))
		if err != nil {
			return nil, "", err
		}
		site = u.Hostname()
		samples = append(samples, rule.Sample{URL: raw, HTML: html})
	}
	return samples, site, nil
}

func printResults(r rule.Rule, samples []rule.Sample) {
	for i, s := range samples {
		res, _ := evaluator.HTML{}.Evaluate(r, s.URL, s.HTML)
		if c := res.Chapter; c != nil {
			fmt.Printf("sample %d: title %q, chapter %v, %d images, next %v\n",
				i+1, deref(c.Title), deref(c.Chapter), len(c.Images), deref(c.Next))
		}
	}
}

func deref(s *string) string {
	if s == nil {
		return "<none>"
	}
	return *s
}
