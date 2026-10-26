#!/usr/bin/env python3
"""HTTP layer for the Anplomer designer.

A stdlib-only local web app: it serves the single-page UI from ui/, exposes a
small JSON API over art/stats.json + art/art.json, and proxies the "Convert to
Java" button to BuildRunner, which shells out to the generator CLIs.

Binds 127.0.0.1 only. Every response carries Cache-Control: no-store.
"""
from __future__ import annotations

import json
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote, urlsplit

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from build import create_runner  # noqa: E402
from model import ModelError, Project  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
UI_DIR = os.path.join(HERE, "ui")
UI_FILES = {
    "index.html": "text/html; charset=utf-8",
    "app.js": "text/javascript; charset=utf-8",
    "style.css": "text/css; charset=utf-8",
}
MAX_BODY = 1_000_000


class ApiError(Exception):
    """An error that already knows its HTTP status and JSON payload."""

    def __init__(self, status, payload):
        super().__init__(payload.get("error", "error"))
        self.status = status
        self.payload = payload


def make_server(root, port=8765):
    """Build the ThreadingHTTPServer for a project root (port 0 = pick one)."""
    project = Project(root)
    runner = create_runner(project.root)

    class Handler(DesignerHandler):
        pass

    Handler.project = project
    Handler.runner = runner
    httpd = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    httpd.daemon_threads = True
    httpd.project = project
    httpd.runner = runner
    return httpd


class DesignerHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    project = None          # set by make_server
    runner = None           # set by make_server

    # ---- plumbing ----------------------------------------------------

    def log_message(self, fmt, *args):
        # Local single-user tool; keep the console quiet unless asked.
        if os.environ.get("DESIGNER_VERBOSE"):
            super().log_message(fmt, *args)

    def _send_bytes(self, body, ctype, status=200):
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _send_json(self, obj, status=200):
        body = json.dumps(obj, indent=2).encode("utf-8")
        self._send_bytes(body, "application/json; charset=utf-8", status)

    def _send_ui_file(self, name):
        with open(os.path.join(UI_DIR, name), "rb") as f:
            body = f.read()
        self._send_bytes(body, UI_FILES[name])

    def _read_json(self):
        """Reads and parses the request body; raises ApiError(400) on junk."""
        length = self.headers.get("Content-Length")
        try:
            length = int(length)
        except (TypeError, ValueError):
            raise ApiError(400, {"ok": False, "error": "bad Content-Length"})
        if length < 0 or length > MAX_BODY:
            raise ApiError(400, {"ok": False, "error": "body too large"})
        raw = self.rfile.read(length) if length else b""
        try:
            body = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise ApiError(400, {"ok": False, "error": f"bad JSON body: {exc}"})
        if not isinstance(body, dict):
            raise ApiError(400, {"ok": False, "error": "body must be a JSON object"})
        return body

    def _path(self):
        return unquote(urlsplit(self.path).path)

    # ---- GET ----------------------------------------------------------

    def do_GET(self):
        try:
            path = self._path()
            if path == "/" or path == "/index.html":
                return self._send_ui_file("index.html")
            if path.startswith("/ui/"):
                name = path[len("/ui/"):]
                if name in UI_FILES and "/" not in name and "\\" not in name:
                    return self._send_ui_file(name)
                raise ApiError(404, {"ok": False, "error": f"no such file: {name!r}"})
            if path == "/api/state":
                return self.api_state()
            if path == "/api/svgs":
                return self.api_svgs()
            if path == "/api/svg":
                return self.api_svg()
            if path == "/api/build":
                return self._send_json(self.runner.snapshot())
            raise ApiError(404, {"ok": False, "error": f"no such route: {path}"})
        except ApiError as exc:
            self._send_json(exc.payload, exc.status)
        except Exception as exc:  # never a traceback page
            self._send_json({"ok": False, "error": f"{type(exc).__name__}: {exc}"}, 500)

    def api_state(self):
        errors = []
        stats = {}
        art = {}
        try:
            stats = self.project.load_stats()
        except Exception as exc:
            errors.append({"field": "stats", "message": str(exc)})
        try:
            art = self.project.load_art()
        except Exception as exc:
            errors.append({"field": "art", "message": str(exc)})
        entities = self.project.get_entities(stats, art) if stats else []
        self._send_json({
            "ok": True,
            "root": self.project.root,
            "entities": entities,
            "stats": stats,
            "art": art,
            "svgs": self.project.list_svgs(),
            "build": self.runner.snapshot(),
            "errors": errors,
        })

    def api_svgs(self):
        self._send_json({"ok": True, "svgs": self.project.list_svgs()})

    def api_svg(self):
        # Preview source for the inspector's <img>: same validation as a bind,
        # raw file bytes with an SVG content type (no JSON wrapping).
        from urllib.parse import parse_qs
        query = parse_qs(urlsplit(self.path).query)
        src = (query.get("path") or [None])[0]
        try:
            text = self.project.read_svg(src)
        except ModelError as exc:
            raise ApiError(400, {"ok": False, "errors": exc.args[0]})
        self._send_bytes(text.encode("utf-8"), "image/svg+xml")

    # ---- POST ----------------------------------------------------------

    def do_POST(self):
        try:
            path = self._path()
            if path == "/api/stats":
                return self.api_stats()
            if path == "/api/asset":
                return self.api_asset()
            if path == "/api/build":
                return self.api_build()
            raise ApiError(404, {"ok": False, "error": f"no such route: {path}"})
        except ApiError as exc:
            self._send_json(exc.payload, exc.status)
        except Exception as exc:
            self._send_json({"ok": False, "error": f"{type(exc).__name__}: {exc}"}, 500)

    def api_stats(self):
        body = self._read_json()
        try:
            current = self.project.load_stats()
        except Exception as exc:
            raise ApiError(400, {"ok": False,
                                 "errors": [{"field": "stats", "message": str(exc)}]})
        # Replace only the edited sections; unknown top-level keys
        # (version, weapons, $comment, ...) survive untouched.
        merged = dict(current)
        if "player" in body:
            merged["player"] = body["player"]
        if "mobs" in body:
            merged["mobs"] = body["mobs"]
        if "weapons" in body:
            merged["weapons"] = body["weapons"]
        try:
            self.project.save_stats(merged)
        except ModelError as exc:
            raise ApiError(400, {"ok": False, "errors": exc.args[0]})
        self._send_json({"ok": True})

    def api_asset(self):
        body = self._read_json()
        entity = body.get("entity")
        try:
            stats = self.project.load_stats()
        except Exception as exc:
            raise ApiError(400, {"ok": False,
                                 "errors": [{"field": "stats", "message": str(exc)}]})
        known = ({"player"}
                 | {m.get("id") for m in stats.get("mobs", [])
                    if isinstance(m, dict)}
                 | {w.get("id") for w in stats.get("weapons", [])
                    if isinstance(w, dict)})
        if entity not in known:
            raise ApiError(400, {"ok": False, "errors": [
                {"field": "entity", "message": f"unknown entity {entity!r}"}]})
        try:
            art = self.project.load_art()
        except Exception as exc:
            raise ApiError(400, {"ok": False,
                                 "errors": [{"field": "art", "message": str(exc)}]})
        try:
            new_art = self.project.update_asset(
                art, entity, body.get("src"), body.get("class"),
                body.get("fit"), body.get("anchor"), body.get("precision"))
        except ModelError as exc:
            raise ApiError(400, {"ok": False, "errors": exc.args[0]})
        self.project.save_art(new_art)
        self._send_json({
            "ok": True,
            "asset": self.project._find_asset_for_entity(new_art, entity),
        })

    def api_build(self):
        try:
            self.runner.start()
        except RuntimeError as exc:
            raise ApiError(409, {"ok": False, "error": str(exc)})
        self._send_json({"ok": True})


if __name__ == "__main__":
    # `python3 server.py ROOT [PORT]` for quick manual runs; designer.py is
    # the real entry point.
    root = sys.argv[1] if len(sys.argv) > 1 else os.path.abspath(
        os.path.join(HERE, "..", ".."))
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 8765
    httpd = make_server(root, port)
    print(f"designer serving {httpd.project.root} at http://127.0.0.1:{port}")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
