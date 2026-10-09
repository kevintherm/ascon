#!/usr/bin/env python3
"""Runs JavaScript in the debug app's WebView on a connected device or emulator.

Debug builds of the app can be inspected with the Chrome DevTools protocol. This finds
the app's WebView, forwards its socket, and evaluates an expression in the page it
shows, so you can check a real site's DOM as Ascon sees it.

    tools/webview.py 'document.title'
    tools/webview.py --nav https://example.com/chapter-1 [seconds to wait, default 6]

Needs `adb` and the websocket-client package. The app must be running with a page open.
"""
import json
import re
import subprocess
import sys
import time
import urllib.request

import websocket

PORT = 9222


def forward():
    sockets = subprocess.run(["adb", "shell", "cat", "/proc/net/unix"], capture_output=True, text=True).stdout
    names = re.findall(r"webview_devtools_remote_\d+", sockets)
    if not names:
        sys.exit("No WebView to inspect. Is the debug app running with a page open?")
    subprocess.run(["adb", "forward", f"tcp:{PORT}", f"localabstract:{names[0]}"], check=True, capture_output=True)


def connect():
    pages = json.load(urllib.request.urlopen(f"http://localhost:{PORT}/json"))
    page = next((p for p in pages if p["type"] == "page" and p.get("url")), None)
    if page is None:
        sys.exit("The WebView has no page loaded.")
    # DevTools rejects WebSocket connections that send an Origin header.
    return websocket.create_connection(page["webSocketDebuggerUrl"], timeout=60, suppress_origin=True)


def main():
    forward()
    ws = connect()
    ids = iter(range(1, 1_000_000))

    def call(method, **params):
        n = next(ids)
        ws.send(json.dumps({"id": n, "method": method, "params": params}))
        while True:
            message = json.loads(ws.recv())
            if message.get("id") == n:
                return message

    if sys.argv[1] == "--nav":
        call("Page.navigate", url=sys.argv[2])
        time.sleep(float(sys.argv[3]) if len(sys.argv) > 3 else 6)
        expression = "location.href + ' | ' + document.title"
    else:
        expression = sys.argv[1]
    result = call("Runtime.evaluate", expression=expression, returnByValue=True, awaitPromise=True)
    value = result.get("result", {}).get("result", {})
    shown = value.get("value", value)
    print(shown if isinstance(shown, str) else json.dumps(shown, indent=1, ensure_ascii=False))


if __name__ == "__main__":
    main()
