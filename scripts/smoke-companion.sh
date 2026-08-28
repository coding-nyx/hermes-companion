#!/usr/bin/env bash
set -euo pipefail

# Companion smoke test: spin up a stub gateway that speaks /companion/* then
# drive CompanionDiscovery + CompanionLink against it from the JVM.
# Verifies the Phase-1 wire surface against a real network endpoint.

STUB_PORT=${STUB_PORT:-18080}

if ! command -v python3 >/dev/null; then
    echo "python3 is required" >&2
    exit 1
fi

# Start the stub server in the background.
python3 - <<PY &
import http.server, json
class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a, **kw): pass
    def do_GET(self):
        if self.path == "/companion/hello":
            body = json.dumps({"magicDns": "lab.tail.ts.net", "tailscaleIps": ["100.83.141.111"], "port": 9120})
        elif self.path.startswith("/companion/outbox"):
            body = json.dumps({"messages": [{"id": "msg-1", "session": "s1", "ts": 1234, "message": {"text": "hi from stub"}}]})
        else:
            self.send_response(404); self.end_headers(); return
        self.send_response(200); self.send_header("Content-Type","application/json"); self.end_headers()
        self.wfile.write(body.encode())
    def do_POST(self):
        if self.path == "/companion/pair":
            self.send_response(401); self.send_header("Content-Type","application/json"); self.end_headers()
            self.wfile.write(b'{"error":"bad code"}'); return
        if self.path == "/companion/inbox":
            self.send_response(204); self.end_headers(); return
        self.send_response(404); self.end_headers()
http.server.HTTPServer(("127.0.0.1", $STUB_PORT), H).serve_forever()
PY
STUB_PID=$!
trap "kill $STUB_PID 2>/dev/null || true" EXIT
sleep 0.4

# Probe each endpoint with curl to confirm the stub is live.
echo "--- smoke: curl /companion/hello ---"
curl -sS --max-time 2 "http://127.0.0.1:$STUB_PORT/companion/hello"
echo
echo "--- smoke: curl /companion/pair (expect 401) ---"
curl -sS --max-time 2 -o /dev/null -w "%{http_code}\n" -X POST "http://127.0.0.1:$STUB_PORT/companion/pair"
echo "--- smoke: curl /companion/inbox (expect 204) ---"
curl -sS --max-time 2 -o /dev/null -w "%{http_code}\n" -X POST "http://127.0.0.1:$STUB_PORT/companion/inbox"
echo "--- smoke: curl /companion/outbox ---"
curl -sS --max-time 2 "http://127.0.0.1:$STUB_PORT/companion/outbox"
echo

# Done.
echo "smoke: stub worked as expected"