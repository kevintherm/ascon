"""Serves the test site for UI flows on port 8765.

Static files come from maestro/site. Chapter pages of "Aztec Turning of Heaven", a series
in the fake library, are built from the Madara fixture the JS tests use, so the flows
exercise the same markup: /manga/aztec-turning-of-heaven/chapter-<n>/.

The emulator reaches the host at 10.0.2.2. Debug builds allow plain http to that
address only, see app/src/debug/res/xml/network_security_config.xml.
"""

import functools
import http.server
import pathlib
import re

PORT = 8765
HERE = pathlib.Path(__file__).parent
SITE = HERE / "site"
FIXTURE = HERE.parent / "engine/detection/src/test/fixtures/madara/chapter.html"
CHAPTER = re.compile(r"^/manga/aztec-turning-of-heaven/chapter-(\d+)/$")


def chapter_page(n):
    html = FIXTURE.read_text().replace("https://tidepool.example", f"http://10.0.2.2:{PORT}")
    html = html.replace("chapter-11/", "{prev}").replace("chapter-13/", "{next}")
    html = html.replace("Chapter 12", f"Chapter {n}").replace("/12/", f"/{n}/")
    return html.replace("{prev}", f"chapter-{n - 1}/").replace("{next}", f"chapter-{n + 1}/")


class Handler(http.server.SimpleHTTPRequestHandler):
    extensions_map = {**http.server.SimpleHTTPRequestHandler.extensions_map,
                      ".apk": "application/vnd.android.package-archive"}

    def do_GET(self):
        m = CHAPTER.match(self.path)
        if not m:
            return super().do_GET()
        body = chapter_page(int(m.group(1))).encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


handler = functools.partial(Handler, directory=SITE)
http.server.ThreadingHTTPServer(("0.0.0.0", PORT), handler).serve_forever()
