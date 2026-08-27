#!/usr/bin/env python3
"""
Hermes Live Gateway Server for Hub-11.
Implements the Hermes Gateway HTTP REST + SSE Contract and connects
to the live Hermes Agent on Hub-11.
"""
import http.server
import json
import os
import re
import socketserver
import subprocess
import threading
import time
import uuid
from urllib.parse import urlparse

PORT = int(os.environ.get("PORT", 7800))
HOST = "0.0.0.0"
GATEWAY_ID = "gw-hub11"
GATEWAY_LABEL = "Hub-11 (Live Hermes)"

HERMES_PYTHON = "/home/nyx/.hermes/hermes-agent/.venv/bin/python"

# In-memory session & message store
sessions_lock = threading.Lock()
sessions = {
    f"sess-{GATEWAY_ID}-ash-1": {
        "session_id": f"sess-{GATEWAY_ID}-ash-1",
        "title": "Welcome — Live Hermes",
        "model_lock": None,
        "run_state": "idle",
        "unread_count": 0,
        "profile": "ash",
    }
}
messages = {
    f"sess-{GATEWAY_ID}-ash-1": [
        {
            "id": "msg-init",
            "role": "assistant",
            "text": "Connected live to Hermes on Hub-11.",
            "created_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "session_id": f"sess-{GATEWAY_ID}-ash-1",
            "profile": "ash",
        }
    ]
}
runs = {}  # run_id -> dict

def run_hermes_agent(profile: str, text: str) -> str:
    """Executes live Hermes Agent in one-shot mode."""
    try:
        cmd = [
            HERMES_PYTHON,
            "-m", "hermes_cli.main",
            "--profile", profile,
            "-z", text,
        ]
        res = subprocess.run(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            timeout=120,
            cwd="/home/nyx",
        )
        if res.returncode == 0 and res.stdout.strip():
            return res.stdout.strip()
        elif res.stderr.strip():
            return f"Hermes response (stderr): {res.stderr.strip()[:300]}"
        return "Hermes completed with empty output."
    except subprocess.TimeoutExpired:
        return "Hermes agent execution timed out after 120s."
    except Exception as e:
        return f"Hermes execution error: {str(e)}"

class GatewayHandler(http.server.BaseHTTPRequestHandler):
    def send_json(self, status: int, data: dict):
        payload = json.dumps(data).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Hermes-Gateway")
        self.end_headers()
        self.wfile.write(payload)

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Hermes-Gateway")
        self.end_headers()

    def do_GET(self):
        url = urlparse(self.path)
        path = url.path

        if path in ("/", "/health"):
            return self.send_json(200, {
                "status": "ok",
                "gateways": [{"id": GATEWAY_ID, "label": GATEWAY_LABEL}],
                "tailscale_ip": "100.88.4.63",
                "port": PORT,
            })

        if path == "/v1/capabilities":
            return self.send_json(200, {
                "capabilities": {
                    "sessions.list": True,
                    "sessions.create": True,
                    "chat.stream": True,
                    "runs.events": True,
                    "approval.required": True,
                    "profiles.multiplexed": True,
                    "responses.stateful": True,
                    "gateways.available": [GATEWAY_ID],
                }
            })

        # Match /gw-hub11/... or /gw-home/... or bare /...
        prefix_match = re.match(r"^/(gw-[a-z0-9-]+)(/.*)?$", path)
        if prefix_match:
            rest = prefix_match.group(2) or "/"
        else:
            rest = path

        if rest in ("/v1/capabilities", "/p/ash/v1/capabilities"):
            return self.send_json(200, {
                "capabilities": {
                    "sessions.list": True,
                    "sessions.create": True,
                    "chat.stream": True,
                    "runs.events": True,
                    "approval.required": True,
                    "profiles.multiplexed": True,
                    "responses.stateful": True,
                }
            })

        if rest == "/api/profiles":
            return self.send_json(200, {
                "profiles": [
                    {"profile_id": "ash", "display_name": "Ash (Hub-11)"},
                ]
            })

        if rest == "/api/sessions":
            with sessions_lock:
                return self.send_json(200, {"sessions": list(sessions.values())})

        sess_msg_match = re.match(r"^/api/sessions/([^/]+)/messages$", rest)
        if sess_msg_match:
            sid = sess_msg_match.group(1)
            with sessions_lock:
                return self.send_json(200, {"messages": messages.get(sid, [])})

        events_match = re.match(r"^/v1/runs/([^/]+)/events$", rest)
        if events_match:
            run_id = events_match.group(1)
            run = runs.get(run_id)
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream; charset=utf-8")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Connection", "close")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()

            self.wfile.write(b":ok\n\n")
            self.wfile.flush()

            if not run:
                fail_data = json.dumps({"run_id": run_id, "session_id": None, "reason": "unknown run"}).encode("utf-8")
                self.wfile.write(b"event: run.failed\ndata: " + fail_data + b"\n\n")
                self.wfile.flush()
                self.close_connection = True
                return

            # Wait for execution or stream events
            while run["state"] == "running":
                time.sleep(0.05)

            # Emit events
            for ev_name, ev_payload in run["events"]:
                ev_data = json.dumps(ev_payload).encode("utf-8")
                self.wfile.write(f"event: {ev_name}\ndata: ".encode("utf-8") + ev_data + b"\n\n")
                self.wfile.flush()
                if ev_name == "assistant.delta":
                    time.sleep(0.02)
            self.close_connection = True
            return

        self.send_json(404, {"error": "not_found", "path": path})

    def do_POST(self):
        url = urlparse(self.path)
        path = url.path
        length = int(self.headers.get("Content-Length", 0))
        body_bytes = self.rfile.read(length) if length > 0 else b"{}"
        try:
            body = json.loads(body_bytes.decode("utf-8")) if body_bytes else {}
        except Exception:
            body = {}

        prefix_match = re.match(r"^/(gw-[a-z0-9-]+)(/.*)?$", path)
        if prefix_match:
            rest = prefix_match.group(2) or "/"
        else:
            rest = path

        if rest == "/api/sessions":
            profile = body.get("profile", "ash")
            title = body.get("title", "New Live Session")
            sid = f"sess-{GATEWAY_ID}-{profile}-{int(time.time())}"
            new_sess = {
                "session_id": sid,
                "title": title,
                "model_lock": None,
                "run_state": "idle",
                "unread_count": 0,
                "profile": profile,
            }
            with sessions_lock:
                sessions[sid] = new_sess
                messages[sid] = []
            return self.send_json(201, {"session": new_sess})

        if rest == "/v1/runs":
            sid = body.get("session_id", f"sess-{GATEWAY_ID}-ash-1")
            profile = body.get("profile", "ash")
            text = str(body.get("text", ""))
            run_id = f"run-{uuid.uuid4().hex[:8]}"

            # Record user message
            user_msg = {
                "id": f"msg-{uuid.uuid4().hex[:8]}",
                "role": "user",
                "text": text,
                "created_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                "session_id": sid,
                "profile": profile,
            }
            with sessions_lock:
                if sid not in messages:
                    messages[sid] = []
                messages[sid].append(user_msg)

            run_record = {
                "run_id": run_id,
                "session_id": sid,
                "profile": profile,
                "text": text,
                "state": "running",
                "events": [],
            }
            runs[run_id] = run_record

            # Execute background thread for live Hermes agent
            def worker():
                # Emit tool start
                tool_run = {
                    "id": f"tool-{uuid.uuid4().hex[:6]}",
                    "name": "hermes_live_engine",
                    "status": "running",
                    "input": text,
                    "started_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                }
                run_record["events"].append(("tool.started", {"run_id": run_id, "session_id": sid, "tool_run": tool_run}))

                # Call live agent on hub-11
                reply = run_hermes_agent(profile, text)

                tool_run["status"] = "completed"
                tool_run["output"] = "Executed on Hub-11"
                tool_run["completed_at"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                run_record["events"].append(("tool.completed", {"run_id": run_id, "session_id": sid, "tool_run": tool_run}))

                # Chunk assistant delta
                chunk_size = 8
                for i in range(0, len(reply), chunk_size):
                    delta = reply[i:i + chunk_size]
                    run_record["events"].append(("assistant.delta", {"run_id": run_id, "session_id": sid, "delta": delta}))

                # Record assistant message
                asst_msg = {
                    "id": f"msg-{uuid.uuid4().hex[:8]}",
                    "role": "assistant",
                    "text": reply,
                    "created_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                    "session_id": sid,
                    "profile": profile,
                }
                with sessions_lock:
                    messages[sid].append(asst_msg)

                run_record["events"].append(("run.completed", {"run_id": run_id, "session_id": sid, "final_text": reply}))
                run_record["state"] = "completed"

            threading.Thread(target=worker, daemon=True).start()
            return self.send_json(202, {"run_id": run_id, "session_id": sid, "state": "streaming"})

        if rest.endswith("/stop"):
            return self.send_json(200, {"ok": True})

        if rest.endswith("/approval"):
            return self.send_json(200, {"ok": True})

        self.send_json(404, {"error": "not_found", "path": path})

class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True

if __name__ == "__main__":
    server = ThreadedHTTPServer((HOST, PORT), GatewayHandler)
    print(f"Hermes Live Gateway listening on http://{HOST}:{PORT}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        server.shutdown()
