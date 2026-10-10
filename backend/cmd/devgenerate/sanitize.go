package main

import (
	"bytes"
	"strings"

	"golang.org/x/net/html"
)

// dropped elements carry no detection signal.
var dropped = map[string]bool{
	"script": true, "style": true, "noscript": true, "svg": true, "iframe": true,
	"link": true, "template": true, "canvas": true, "video": true, "audio": true,
}

// kept attributes are the ones AGENTS.md lists for snapshots, plus the few
// lazy-image and meta attributes rules commonly read. The app's sanitizer,
// built with the app side of generation, decides the final list.
var kept = map[string]bool{
	"class": true, "id": true, "href": true, "src": true, "data-src": true,
	"data-lazy-src": true, "srcset": true, "rel": true, "property": true, "name": true, "content": true,
}

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
			text := strings.Join(strings.Fields(c.Data), " ")
			if len(text) > maxText {
				text = text[:maxText] + "…"
			}
			c.Data = text
		case c.Type == html.ElementNode:
			attrs := c.Attr[:0]
			for _, a := range c.Attr {
				if kept[a.Key] {
					attrs = append(attrs, a)
				}
			}
			c.Attr = attrs
			clean(c)
		}
		c = next
	}
}
