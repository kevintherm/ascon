"""Serves the test site for UI flows on port 8765.

Static files come from maestro/site. Chapter pages are built from the Madara fixture the
JS tests use, so the flows exercise the same markup: /manga/<slug>/chapter-<n>/. The slug
names the series, so "aztec-turning-of-heaven" is the one in the sample library and
"moonlit-ferry" is one the library does not have. Their page images,
/pages/<chapter>/<n>.png, are plain gray PNGs drawn here.

The emulator reaches the host at 10.0.2.2. Debug builds allow plain http to that
address only, see app/src/debug/res/xml/network_security_config.xml.
"""

import functools
import http.server
import pathlib
import re
import struct
import zlib

PORT = 8765
HERE = pathlib.Path(__file__).parent
SITE = HERE / "site"
FIXTURE = HERE.parent / "engine/detection/src/test/fixtures/madara/chapter.html"
CHAPTER = re.compile(r"^/manga/([a-z-]+)/chapter-(\d+)/$")
PAGE = re.compile(r"^/pages/(\d+)/(\d+)\.(?:png|webp)$")
# An image the debug filter list blocks, so a flow can tell blocking from a missing file.
AD = "/ads/banner.png"


def png(width, height, gray):
    """A gray PNG, so the reader has real images to decode."""
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    rows = b"".join(b"\x00" + bytes([gray]) * width for _ in range(height))
    header = struct.pack(">IIBBBBB", width, height, 8, 0, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b"")


def chapter_page(slug, n):
    html = FIXTURE.read_text().replace("aztec-turning-of-heaven", slug)
    html = html.replace("Aztec Turning of Heaven", slug.replace("-", " ").title())
    html = html.replace("https://cdn.tidepool.example/aztec", f"http://10.0.2.2:{PORT}/pages")
    html = html.replace("https://tidepool.example", f"http://10.0.2.2:{PORT}")
    html = html.replace("chapter-11/", "{prev}").replace("chapter-13/", "{next}")
    html = html.replace("Chapter 12", f"Chapter {n}").replace("/12/", f"/{n}/")
    return html.replace("{prev}", f"chapter-{n - 1}/").replace("{next}", f"chapter-{n + 1}/")


class Handler(http.server.SimpleHTTPRequestHandler):
    extensions_map = {**http.server.SimpleHTTPRequestHandler.extensions_map,
                      ".apk": "application/vnd.android.package-archive"}

    def do_GET(self):
        page = PAGE.match(self.path)
        if self.path == AD:
            return self.reply("image/png", png(60, 20, 200))
        if page:
            return self.reply("image/png", png(600, 840, 120 + 10 * (int(page.group(2)) % 8)))
        m = CHAPTER.match(self.path)
        if not m:
            return super().do_GET()
        self.reply("text/html; charset=utf-8", chapter_page(m.group(1), int(m.group(2))).encode())

    def reply(self, content_type, body):
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


handler = functools.partial(Handler, directory=SITE)
http.server.ThreadingHTTPServer(("0.0.0.0", PORT), handler).serve_forever()
