"""Serves the test site in maestro/site on port 8765 for UI flows.

The emulator reaches the host at 10.0.2.2. Debug builds allow plain http to that
address only, see app/src/debug/res/xml/network_security_config.xml.
"""

import functools
import http.server
import pathlib

PORT = 8765
ROOT = pathlib.Path(__file__).parent / "site"

handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT)
http.server.ThreadingHTTPServer(("0.0.0.0", PORT), handler).serve_forever()
