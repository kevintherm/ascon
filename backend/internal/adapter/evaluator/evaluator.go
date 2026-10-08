// Package evaluator runs detection rules against HTML on the server. It is the
// Go twin of the JS evaluator in android/engine/detection; both follow
// contracts/README.md and must pass the same conformance fixtures.
package evaluator

import (
	"bytes"
	"fmt"
	"regexp"
	"strings"

	"github.com/andybalholm/cascadia"
	"golang.org/x/net/html"

	"github.com/kevintherm/ascon/backend/internal/domain/rule"
)

// Evaluate reads the page at pageURL, whose markup is page, using r.
func Evaluate(r rule.Rule, pageURL string, page []byte) (rule.Result, error) {
	doc, err := html.Parse(bytes.NewReader(page))
	if err != nil {
		return rule.Result{}, fmt.Errorf("parse html: %w", err)
	}
	e := evaluation{doc: doc, pageURL: pageURL}

	groups, ok, err := matchURL(r.ChapterPage.URL, pageURL)
	if err != nil {
		return rule.Result{}, fmt.Errorf("chapterPage.url: %w", err)
	}
	if ok {
		chapter, err := e.chapter(r.ChapterPage, groups)
		if err != nil {
			return rule.Result{}, err
		}
		return rule.Result{PageType: rule.PageChapter, Chapter: chapter}, nil
	}

	if r.SeriesPage != nil {
		groups, ok, err := matchURL(r.SeriesPage.URL, pageURL)
		if err != nil {
			return rule.Result{}, fmt.Errorf("seriesPage.url: %w", err)
		}
		if ok {
			series, err := e.series(*r.SeriesPage, groups)
			if err != nil {
				return rule.Result{}, err
			}
			return rule.Result{PageType: rule.PageSeries, Series: series}, nil
		}
	}

	return rule.Result{PageType: rule.PageNone}, nil
}

type evaluation struct {
	doc     *html.Node
	pageURL string
}

func (e evaluation) chapter(p rule.ChapterPage, groups map[string]string) (*rule.ChapterResult, error) {
	title, err := e.extract(p.Title)
	if err != nil {
		return nil, fmt.Errorf("chapterPage.title: %w", err)
	}
	label, err := e.extract(p.ChapterLabel)
	if err != nil {
		return nil, fmt.Errorf("chapterPage.chapterLabel: %w", err)
	}
	images, err := e.images(p.Images)
	if err != nil {
		return nil, fmt.Errorf("chapterPage.images: %w", err)
	}
	next, err := e.link(p.Next)
	if err != nil {
		return nil, fmt.Errorf("chapterPage.next: %w", err)
	}
	previous, err := e.link(p.Previous)
	if err != nil {
		return nil, fmt.Errorf("chapterPage.previous: %w", err)
	}

	var chapter *string
	if label != nil {
		chapter = chapterNumber(*label)
	}
	if chapter == nil {
		if g, ok := groups["chapter"]; ok {
			chapter = chapterNumber(g)
		}
	}

	return &rule.ChapterResult{
		Series:       group(groups, "series"),
		Title:        title,
		ChapterLabel: label,
		Chapter:      chapter,
		Images:       images,
		Next:         next,
		Previous:     previous,
	}, nil
}

func (e evaluation) series(p rule.SeriesPage, groups map[string]string) (*rule.SeriesResult, error) {
	title, err := e.extract(p.Title)
	if err != nil {
		return nil, fmt.Errorf("seriesPage.title: %w", err)
	}
	sel, err := cascadia.Compile(p.ChapterLinks)
	if err != nil {
		return nil, fmt.Errorf("seriesPage.chapterLinks: %w", err)
	}

	chapters := []rule.ChapterLink{}
	for _, n := range sel.MatchAll(e.doc) {
		u := resolveURL(attr(n, "href"), e.pageURL)
		if u == nil {
			continue
		}
		label := collapse(textContent(n))
		var number *string
		if label != nil {
			number = chapterNumber(*label)
		}
		chapters = append(chapters, rule.ChapterLink{URL: *u, Label: label, Number: number})
	}

	return &rule.SeriesResult{Series: group(groups, "series"), Title: title, Chapters: chapters}, nil
}

func (e evaluation) extract(x *rule.Extract) (*string, error) {
	if x == nil {
		return nil, nil
	}
	sel, err := cascadia.Compile(x.Selector)
	if err != nil {
		return nil, err
	}
	n := sel.MatchFirst(e.doc)
	if n == nil {
		return nil, nil
	}

	v := readValue(n, attributeOr(x.Attribute, rule.AttributeText))
	if v == nil || x.Pattern == "" {
		return v, nil
	}

	re, err := regexp.Compile(x.Pattern)
	if err != nil {
		return nil, fmt.Errorf("pattern: %w", err)
	}
	i := re.SubexpIndex("value")
	if i < 0 {
		return nil, fmt.Errorf("pattern has no group named value")
	}
	m := re.FindStringSubmatch(*v)
	if m == nil {
		return nil, nil
	}
	return nonEmpty(trim(m[i])), nil
}

func (e evaluation) link(l *rule.Link) (*string, error) {
	if l == nil {
		return nil, nil
	}
	sel, err := cascadia.Compile(l.Selector)
	if err != nil {
		return nil, err
	}
	n := sel.MatchFirst(e.doc)
	if n == nil {
		return nil, nil
	}
	return resolveURL(readValue(n, attributeOr(l.Attribute, "href")), e.pageURL), nil
}

func (e evaluation) images(im rule.Images) ([]string, error) {
	sel, err := cascadia.Compile(im.Selector)
	if err != nil {
		return nil, err
	}
	attrs := im.Attributes
	if len(attrs) == 0 {
		attrs = []string{"src"}
	}

	urls := []string{}
	for _, n := range sel.MatchAll(e.doc) {
		for _, a := range attrs {
			v := readValue(n, a)
			if a == "srcset" && v != nil {
				v = largestCandidate(*v)
			}
			if u := resolveURL(v, e.pageURL); u != nil {
				urls = append(urls, *u)
				break
			}
		}
	}
	return urls, nil
}

// matchURL matches pattern against the whole URL and returns its named groups.
func matchURL(pattern, pageURL string) (map[string]string, bool, error) {
	re, err := regexp.Compile("^(?:" + pattern + ")$")
	if err != nil {
		return nil, false, err
	}
	m := re.FindStringSubmatch(pageURL)
	if m == nil {
		return nil, false, nil
	}
	groups := map[string]string{}
	for i, name := range re.SubexpNames() {
		if name != "" && i < len(m) {
			groups[name] = m[i]
		}
	}
	return groups, true, nil
}

func group(groups map[string]string, name string) *string {
	if v, ok := groups[name]; ok {
		return nonEmpty(v)
	}
	return nil
}

func chapterNumber(label string) *string {
	if n, ok := rule.ParseChapterNumber(label); ok {
		return &n
	}
	return nil
}

func attributeOr(a, fallback string) string {
	if a == "" {
		return fallback
	}
	return a
}

// readValue reads an element's collapsed text or one of its trimmed attributes.
func readValue(n *html.Node, attribute string) *string {
	if attribute == rule.AttributeText {
		return collapse(textContent(n))
	}
	return attr(n, attribute)
}

func attr(n *html.Node, name string) *string {
	for _, a := range n.Attr {
		if a.Namespace == "" && a.Key == name {
			return nonEmpty(trim(a.Val))
		}
	}
	return nil
}

func textContent(n *html.Node) string {
	var b strings.Builder
	var walk func(*html.Node)
	walk = func(n *html.Node) {
		if n.Type == html.TextNode {
			b.WriteString(n.Data)
		}
		for c := n.FirstChild; c != nil; c = c.NextSibling {
			walk(c)
		}
	}
	walk(n)
	return b.String()
}

// Whitespace as contracts/README.md defines it.
const whitespace = "\t\n\f\r  "

var whitespaceRun = regexp.MustCompile(`[\t\n\f\r \x{00a0}]+`)

func collapse(s string) *string {
	return nonEmpty(trim(whitespaceRun.ReplaceAllString(s, " ")))
}

func trim(s string) string { return strings.Trim(s, whitespace) }

func nonEmpty(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}
