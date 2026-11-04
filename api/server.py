#!/usr/bin/env python3
"""Local Flight Explorer API.

    python3 api/server.py                 # serve fixtures/flights.json on http://127.0.0.1:8080
    python3 api/server.py --mode empty    # 200 with {"flights": []}
    python3 api/server.py --mode error    # 500, to exercise failure handling
    python3 api/server.py --delay 3       # add latency, to see loading states

Standard library only; no install step. The fixture is re-read on every request,
so edits to flights.json show up on the next refresh.
"""
import argparse
import json
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

FIXTURE = Path(__file__).resolve().parent.parent / "fixtures" / "flights.json"


def make_handler(mode: str, delay: float):
    class Handler(BaseHTTPRequestHandler):
        server_version = "FlightExplorerAPI/1.0"

        def do_GET(self):
            path = self.path.split("?", 1)[0].rstrip("/")
            if path != "/flights":
                return self._send(404, {"error": "not found"})
            if delay:
                time.sleep(delay)
            if mode == "error":
                return self._send(500, {"error": "simulated server failure"})
            if mode == "empty":
                return self._send(200, {"flights": []})
            try:
                body = FIXTURE.read_bytes()
            except OSError as e:
                return self._send(500, {"error": f"could not read fixture: {e}"})
            self._send_bytes(200, body)

        def _send(self, status, payload):
            self._send_bytes(status, json.dumps(payload).encode())

        def _send_bytes(self, status, body: bytes):
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)

    return Handler


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--mode", choices=["ok", "empty", "error"], default="ok")
    parser.add_argument("--delay", type=float, default=0.0, help="seconds to wait before responding")
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), make_handler(args.mode, args.delay))
    print(f"Serving GET /flights on http://{args.host}:{args.port} (mode={args.mode}). Ctrl+C to stop.", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
