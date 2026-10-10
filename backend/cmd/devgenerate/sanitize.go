package main

import (
	"bytes"
	"strings"

	"golang.org/x/net/html"
)

// dropped elements carry no detection signal, or hold what the user typed.
var dropped = map[string]bool{
	"script": true, "style": true, "noscript": true, "svg": true, "iframe": true, "object": true, "embed": true,
	"link": true, "template": true, "canvas": true, "video": true, "audio": true,
	"input": true, "textarea": true, "select": true,
}

// kept attributes match the app's snapshot in bridge.js and PageSnapshot in
// contracts/openapi.yaml. Meta tags keep name, property and content.
var kept = map[string]bool{
	"class": true, "id": true, "href": true, "src": true, "data-src": true,
	"data-lazy-src": true, "srcset": true, "data-srcset": true, "rel": true,
}

var keptOnMeta = map[string]bool{"name": true, "property": true, "content": true}

const maxText = 80

// sanitize shrinks a raw page the way the app will before sending it: no
// scripts or styles, few attributes, short text.
func sanitize(page []byte) ([]byte, error) {
	doc, err := html.Parse(bytes.NewReader(page))
	if err != nil {
		return nil, err
	}
	clean(doc)
	var out bytes.Buffer
	if err := html.Render(&out, doc); err != nil {
		return nil, err
	}
	return out.Bytes(), nil
}

func clean(n *html.Node) {
	for c := n.FirstChild; c != nil; {
		next := c.NextSibling
		switch {
		case c.Type == html.CommentNode, c.Type == html.ElementNode && dropped[c.Data]:
			n.RemoveChild(c)
		case c.Type == html.TextNode:
			c.Data = shorten(c.Data)
		case c.Type == html.ElementNode:
			attrs := c.Attr[:0]
			for _, a := range c.Attr {
				if kept[a.Key] || c.Data == "meta" && keptOnMeta[a.Key] {
					if a.Key == "content" {
						a.Val = shorten(a.Val)
					}
					attrs = append(attrs, a)
				}
			}
			c.Attr = attrs
			clean(c)
		}
		c = next
	}
}

// shorten collapses whitespace and cuts text at maxText characters, as
// bridge.js does.
func shorten(s string) string {
	text := []rune(strings.Join(strings.Fields(s), " "))
	if len(text) > maxText {
		return string(text[:maxText]) + "…"
	}
	return string(text)
}
