#!/usr/bin/env python3
"""Turns a saved web page into a small, anonymous detection fixture.

Keeps the DOM structure and the attributes detection reads. Drops scripts other
than JSON-LD, styles, SVG and comments, shortens text, and renames hosts so a
fixture never names a real site.

    tools/fixture.py page.html out.html asurascans=reader-a komiku=reader-b
"""
import re
import sys
from html import escape
from html.parser import HTMLParser

KEEP_ATTRS = {
    "id", "class", "href", "src", "srcset", "data-src", "data-lazy-src", "alt",
    "content", "name", "property", "rel", "type", "charset", "lang",
}
DROP = {"style", "svg", "noscript", "iframe", "template", "link"}
VOID = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"}
MAX_TEXT = 80


class Sanitizer(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.out = []
        self.skip = 0  # depth inside a dropped element
        self.json_ld = False

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if self.skip or tag in DROP or (tag == "script" and a.get("type") != "application/ld+json"):
            if tag not in VOID:
                self.skip += 1
            return
        self.json_ld = tag == "script"
        kept = "".join(
            f' {k}="{escape(v, quote=True)}"' for k, v in attrs if k in KEEP_ATTRS and v is not None
        )
        self.out.append(f"<{tag}{kept}>")

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)

    def handle_endtag(self, tag):
        if tag in VOID:
            return
        if self.skip:
            self.skip -= 1
            return
        self.json_ld = False
        self.out.append(f"</{tag}>")

    def handle_data(self, data):
        if self.skip:
            return
        if self.json_ld:
            self.out.append(data)
            return
        text = re.sub(r"\s+", " ", data)
        if len(text) > MAX_TEXT:
            text = text[:MAX_TEXT].rstrip() + "…"
        self.out.append(escape(text, quote=False))


def main():
    src, dst, *renames = sys.argv[1:]
    s = Sanitizer()
    s.feed(open(src, encoding="utf-8").read())
    html = "<!DOCTYPE html>\n" + re.sub(r"\n\s*\n+", "\n", "".join(s.out))
    for pair in renames:
        old, new = pair.split("=", 1)
        html = re.sub(re.escape(old), new, html, flags=re.IGNORECASE)
    open(dst, "w", encoding="utf-8").write(html)


if __name__ == "__main__":
    main()
