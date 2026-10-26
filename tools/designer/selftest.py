#!/usr/bin/env python3
"""Self-test for the Anplomer designer web app.

Run after touching anything under tools/designer/:

    python3 tools/designer/selftest.py

Three layers, mirroring tools/artgen/selftest.py's style:

1. Model checks: fixture stats round-trip, unknown-key preservation, the .bak
   behaviour of atomic saves, every validation rule, and --root isolation.
2. Build runner checks: against a scratch root with stub generator CLIs
   (a passing one and a failing one) - success, failure propagation, progress
   reaching 1.0, and the busy refusal.
3. Server checks: start on port 0 in a thread, exercise every route with
   urllib.request (200/400/404/409), assert JSON shapes, and prove path
   traversal is rejected.

Headless: no display, no network beyond 127.0.0.1. Exit 0 on success, 1 on any
failure.
"""
from __future__ import annotations

import json
import os
import shutil
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from build import create_runner  # noqa: E402
from model import ModelError, Project  # noqa: E402
from server import make_server  # noqa: E402

failures: list[str] = []


def check(name: str, ok: bool, detail: str = "") -> None:
    if ok:
        print(f"  ok    {name}")
    else:
        print(f"  FAIL  {name}{': ' + detail if detail else ''}")
        failures.append(name)


# ---------------------------------------------------------------- fixtures

FIXTURE_STATS = {
    "$comment": ["designer fixture", "keep me"],
    "version": 1,
    "surpriseKey": "must survive saves",
    "player": {"maxHp": 100, "attack": 25, "hurtInvuln": 0.8, "hat": "red"},
    "mobs": [
        {"id": "walker", "name": "Walker", "maxHp": 20, "attack": 15,
         "speed": 95, "width": 48, "height": 42, "hat": "green"},
    ],
    "weapons": [{"id": "sword", "damage": 10}],
}

FIXTURE_ART = {
    "$comment": "art fixture",
    "package": "com.example.anplomer",
    "outDir": "app/src/main/java/com/example/anplomer",
    "assets": [
        {"src": "art/legacy.svg", "class": "ArtLegacy",
         "fit": "26x36", "anchor": "bottom-center", "precision": 2},
    ],
}

SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
<path d="M0 10 L5 0 L10 10 Z" fill="#f00"/></svg>
"""

STUB_STATS_OK = """#!%s
import sys
print("art/stats.json -> app/src/.../Stats.java  wrote: hero 100hp/25atk")
sys.exit(0)
"""

STUB_ART_OK = """#!%s
print("art/mob.svg -> app/src/.../ArtMob.java  wrote")
print("art/legacy.svg -> app/src/.../ArtLegacy.java  wrote")
"""

STUB_STATS_FAIL = """#!%s
import sys
print("art/stats.json: error: bad numbers", file=sys.stderr)
sys.exit(3)
"""

STUB_STATS_SLOW = """#!%s
import time
time.sleep(1.5)
print("art/stats.json -> app/src/.../Stats.java  wrote: slow")
"""


def make_root(tmp: Path, stats=FIXTURE_STATS, art=FIXTURE_ART,
              stats_stub=STUB_STATS_OK, art_stub=STUB_ART_OK) -> Path:
    """Scratch project root with fixture data and stub generator CLIs."""
    root = tmp / "proj"
    (root / "art").mkdir(parents=True)
    (root / "art" / "stats.json").write_text(json.dumps(stats, indent=2))
    (root / "art" / "art.json").write_text(json.dumps(art, indent=2))
    (root / "art" / "mob.svg").write_text(SVG)
    (root / "tools" / "statgen").mkdir(parents=True)
    (root / "tools" / "artgen").mkdir(parents=True)
    for rel, text in (
            ("tools/statgen/stats2java.py", stats_stub),
            ("tools/artgen/svg2java.py", art_stub)):
        p = root / rel
        p.write_text(text % sys.executable)
        p.chmod(0o755)
    return root


# ------------------------------------------------------------------ model

def test_model(tmp: Path) -> None:
    print("model:")
    root = make_root(tmp / "model")
    proj = Project(str(root))

    stats = proj.load_stats()
    check("load stats fixture", stats["player"]["maxHp"] == 100)

    stats["player"]["maxHp"] = 120
    proj.save_stats(stats)
    on_disk = json.loads((root / "art" / "stats.json").read_text())
    check("save writes the edit",
          on_disk["player"]["maxHp"] == 120)
    check("save preserves unknown keys",
          on_disk["$comment"] == FIXTURE_STATS["$comment"]
          and on_disk["surpriseKey"] == "must survive saves"
          and on_disk["weapons"] == FIXTURE_STATS["weapons"]
          and on_disk["player"]["hat"] == "red"
          and on_disk["mobs"][0]["hat"] == "green")

    bak = root / "art" / "stats.json.bak"
    check("atomic save keeps a .bak",
          bak.exists()
          and json.loads(bak.read_text())["player"]["maxHp"] == 100)

    bad_id = json.loads(json.dumps(FIXTURE_STATS))
    bad_id["mobs"].append(dict(bad_id["mobs"][0]))          # duplicate id
    errs = proj.validate_stats(bad_id)
    check("duplicate mob id rejected",
          any(e["field"].endswith(".id") and "duplicate" in e["message"]
              for e in errs), str(errs))

    cases = [
        ("mob id must be lowercase", {"mobs": [{"id": "Walker"}]}),
        ("mob maxHp low", {"mobs": [{"id": "w", "maxHp": 0}]}),
        ("mob maxHp high", {"mobs": [{"id": "w", "maxHp": 10000}]}),
        ("mob attack negative", {"mobs": [{"id": "w", "attack": -1}]}),
        ("mob speed high", {"mobs": [{"id": "w", "speed": 3000}]}),
        ("mob width low", {"mobs": [{"id": "w", "width": 3}]}),
        ("mob height high", {"mobs": [{"id": "w", "height": 600}]}),
        ("player maxHp string", {"player": {"maxHp": "lots"}}),
        ("player maxHp bool", {"player": {"maxHp": True}}),
        ("player attack high", {"player": {"attack": 10000}}),
        ("player hurtInvuln high", {"player": {"hurtInvuln": 6}}),
        ("player maxHp zero", {"player": {"maxHp": 0}}),
    ]
    for name, patch in cases:
        doc = json.loads(json.dumps(FIXTURE_STATS))
        doc.update(patch)
        if "player" in patch and "maxHp" in patch and patch["player"]["maxHp"] in (0, "lots", True):
            pass  # shape kept as authored on purpose
        errs = proj.validate_stats(doc)
        check(f"rejects {name}", bool(errs), str(errs))

    other = make_root(tmp / "model-other")
    check("--root isolation",
          Project(str(other)).stats_path != proj.stats_path
          and Project(str(other)).load_stats()["player"]["maxHp"] == 100)

    # asset upsert: bind, preserve unknown keys, unbind
    art = proj.load_art()
    new_art = proj.update_asset(art, "player", "art/mob.svg", "ArtPlayer",
                                "26x36", "bottom-center", None)
    bound = [a for a in new_art["assets"] if a.get("entity") == "player"]
    check("bind upserts an asset entry",
          len(bound) == 1 and bound[0]["src"] == "art/mob.svg"
          and bound[0]["precision"] == 2, str(bound))
    check("bind preserves manifest keys",
          new_art["$comment"] == "art fixture"
          and new_art["package"] == "com.example.anplomer"
          and any(a["src"] == "art/legacy.svg" for a in new_art["assets"]))
    try:
        proj.update_asset(art, "player", "art/missing.svg", "ArtPlayer",
                          "26x36", "bottom-center", 2)
        check("bind rejects a missing svg", False, "no error raised")
    except ModelError:
        check("bind rejects a missing svg", True)
    try:
        proj.update_asset(art, "player", "art/mob.svg", "ArtPlayer",
                          "wide", "bottom-center", 2)
        check("bind rejects a bad fit", False, "no error raised")
    except ModelError:
        check("bind rejects a bad fit", True)
    try:
        proj.update_asset(art, "player", "art/mob.svg", "ArtPlayer",
                          "26x36", "middle", 2)
        check("bind rejects a bad anchor", False, "no error raised")
    except ModelError:
        check("bind rejects a bad anchor", True)
    try:
        proj.update_asset(art, "player", "art/mob.svg", "ArtPlayer",
                          "26x36", "bottom-center", 9)
        check("bind rejects precision > 6", False, "no error raised")
    except ModelError:
        check("bind rejects precision > 6", True)

    new_art = proj.update_asset(new_art, "player", None, None, None, None, None)
    check("src null unbinds",
          not any(a.get("entity") == "player" for a in new_art["assets"]))

    check("svgs listed repo-relative",
          proj.list_svgs() == ["art/mob.svg"], str(proj.list_svgs()))


# ------------------------------------------------------------------ build

def wait_for(runner, state, timeout=10.0) -> dict:
    deadline = time.time() + timeout
    while time.time() < deadline:
        snap = runner.snapshot()
        if snap["state"] == state:
            return snap
        time.sleep(0.02)
    return runner.snapshot()


def test_build(tmp: Path) -> None:
    print("build:")
    root = make_root(tmp / "build")
    runner = create_runner(str(root))
    check("runner starts idle", runner.snapshot()["state"] == "idle")

    runner.start()
    snap = wait_for(runner, "done")
    check("stub build succeeds", snap["state"] == "done", json.dumps(snap))
    check("progress reaches 1.0", snap["progress"] == 1.0, str(snap["progress"]))
    check("steps both done with logs",
          all(s["state"] == "done" and " -> " in s["log"]
              for s in snap["steps"]), json.dumps(snap["steps"]))
    again = create_runner(str(root))
    again.start()
    check("a fresh build after done is allowed",
          wait_for(again, "done")["state"] == "done")

    froot = make_root(tmp / "build-fail", stats_stub=STUB_STATS_FAIL)
    frunner = create_runner(str(froot))
    frunner.start()
    snap = wait_for(frunner, "failed")
    check("failing stats stub fails the build",
          snap["state"] == "failed" and snap["exitCode"] == 3,
          json.dumps(snap))
    check("failure names the step",
          "stats" in (snap["message"] or ""), snap["message"] or "?")
    check("failing step has a log",
          "bad numbers" in snap["steps"][0]["log"], snap["steps"][0]["log"])

    sroot = make_root(tmp / "build-busy", stats_stub=STUB_STATS_SLOW)
    srunner = create_runner(str(sroot))
    srunner.start()
    try:
        srunner.start()
        check("second start while running refused", False, "no error raised")
    except RuntimeError:
        check("second start while running refused", True)
    finally:
        wait_for(srunner, "done")


# ----------------------------------------------------------------- server

def http(method, url, payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method,
                                 headers={"Content-Type": "application/json"}
                                 if data else {})
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            raw = resp.read().decode()
            ctype = resp.headers.get("Content-Type", "")
            if "json" in ctype:
                return resp.status, json.loads(raw)
            return resp.status, {"raw": raw}
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode()
        try:
            return exc.code, json.loads(raw)
        except json.JSONDecodeError:
            return exc.code, {"raw": raw}


def test_server(tmp: Path) -> None:
    print("server:")
    root = make_root(tmp / "server")
    httpd = make_server(str(root), 0)
    port = httpd.server_address[1]
    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    base = f"http://127.0.0.1:{port}"
    try:
        status, body = http("GET", f"{base}/api/state")
        check("GET /api/state 200", status == 200 and body["ok"])
        check("state has the player entity",
              body["entities"][0]["id"] == "player"
              and body["entities"][0]["kind"] == "player")
        check("state has mob entities",
              any(e["id"] == "walker" for e in body["entities"]))
        check("entities carry in-game status + anchor hints",
              body["entities"][0]["suggestedAnchor"] == "bottom-center"
              and "fallback" in body["entities"][0]["inGame"]
              and body["entities"][0]["asset"] is None)
        check("state carries stats, art, svgs, build",
              "player" in body["stats"] and "assets" in body["art"]
              and body["svgs"] == ["art/mob.svg"]
              and body["build"]["state"] == "idle")

        status, body = http("GET", f"{base}/")
        check("GET / serves index.html",
              status == 200 and "Anplomer" in body["raw"]
              and "<!DOCTYPE html>" in body["raw"], f"status {status}")

        status, body = http("GET", f"{base}/ui/app.js")
        check("GET /ui/app.js 200",
              status == 200 and "startBuild" in body["raw"], f"status {status}")

        status, body = http("GET", f"{base}/ui/style.css")
        check("GET /ui/style.css 200",
              status == 200 and "progress-bar" in body["raw"], f"status {status}")

        status, body = http("GET", f"{base}/ui/not-there.js")
        check("unknown ui file 404", status == 404, f"status {status}")

        status, body = http("GET", f"{base}/api/svgs")
        check("GET /api/svgs lists svg paths",
              status == 200 and body["svgs"] == ["art/mob.svg"])

        status, body = http("GET", f"{base}/api/svg?path=art/mob.svg")
        check("GET /api/svg serves the file",
              status == 200 and "<svg" in body["raw"], f"status {status}")
        status, body = http("GET", f"{base}/api/svg?path=art/missing.svg")
        check("GET /api/svg 400 on missing file", status == 400)
        status, body = http("GET", f"{base}/api/svg?path=tools/designer/model.py")
        check("GET /api/svg rejects non-svg", status == 400)
        status, body = http("GET",
              f"{base}/api/svg?path=" + urllib.parse.quote("../server.py"))
        check("GET /api/svg rejects traversal", status == 400)

        status, body = http("GET", f"{base}/nope")
        check("unknown route 404 JSON", status == 404 and body["ok"] is False)

        status, body = http("GET", f"{base}/ui/../model.py")
        check("plain traversal rejected", status == 404, f"status {status}")
        status, body = http("GET", f"{base}/ui/%2e%2e/model.py")
        check("encoded traversal rejected", status == 404, f"status {status}")

        status, body = http("POST", f"{base}/api/stats", {
            "player": {"maxHp": 140, "attack": 25, "hurtInvuln": 0.8},
            "mobs": FIXTURE_STATS["mobs"],
        })
        check("POST /api/stats saves", status == 200 and body["ok"])
        on_disk = json.loads((root / "art" / "stats.json").read_text())
        check("stats save landed on disk",
              on_disk["player"]["maxHp"] == 140)
        check("stats save preserved unknown keys",
              on_disk["surpriseKey"] == "must survive saves"
              and on_disk["weapons"] == FIXTURE_STATS["weapons"])
        check("stats save kept a .bak",
              (root / "art" / "stats.json.bak").exists())

        status, body = http("POST", f"{base}/api/stats", {
            "player": {"maxHp": 0, "attack": 25, "hurtInvuln": 0.8},
        })
        check("invalid stats 400 with field errors",
              status == 400 and body["ok"] is False
              and any(e["field"] == "player.maxHp" for e in body["errors"]),
              json.dumps(body))

        status, body = http("POST", f"{base}/api/asset", {
            "entity": "player", "src": "art/mob.svg", "class": "ArtPlayer",
            "fit": "26x36", "anchor": "bottom-center", "precision": 2,
        })
        check("POST /api/asset binds", status == 200 and body["ok"]
              and body["asset"]["src"] == "art/mob.svg", json.dumps(body))
        art_disk = json.loads((root / "art" / "art.json").read_text())
        check("asset bind landed in art.json",
              any(a.get("entity") == "player" for a in art_disk["assets"]))
        check("asset bind preserved manifest keys",
              art_disk["$comment"] == "art fixture"
              and art_disk["package"] == "com.example.anplomer")

        status, body = http("POST", f"{base}/api/asset", {
            "entity": "player", "src": None,
        })
        check("POST /api/asset unbinds",
              status == 200 and body["ok"] and body["asset"] is None)
        art_disk = json.loads((root / "art" / "art.json").read_text())
        check("unbind removed the entry",
              not any(a.get("entity") == "player" for a in art_disk["assets"]))

        status, body = http("POST", f"{base}/api/asset", {
            "entity": "boss", "src": "art/mob.svg",
        })
        check("unknown entity 400", status == 400, f"status {status}")

        status, body = http("POST", f"{base}/api/asset", {
            "entity": "player", "src": "art/missing.svg", "class": "ArtPlayer",
            "fit": "26x36", "anchor": "bottom-center", "precision": 2,
        })
        check("missing svg 400", status == 400)

        status, body = http("POST", f"{base}/api/build", {})
        check("POST /api/build starts", status == 200 and body["ok"])
        snap = None
        deadline = time.time() + 10
        while time.time() < deadline:
            s, body = http("GET", f"{base}/api/build")
            snap = body
            if body["state"] != "running":
                break
            time.sleep(0.05)
        check("build snapshot reaches done", snap and snap["state"] == "done",
              json.dumps(snap))
        check("done snapshot has progress 1.0",
              snap["progress"] == 1.0, str(snap and snap["progress"]))

        sroot = make_root(tmp / "server-busy", stats_stub=STUB_STATS_SLOW)
        busy = make_server(str(sroot), 0)
        threading.Thread(target=busy.serve_forever, daemon=True).start()
        bport = busy.server_address[1]
        status, body = http("POST", f"http://127.0.0.1:{bport}/api/build", {})
        check("first start accepted", status == 200)
        status, body = http("POST", f"http://127.0.0.1:{bport}/api/build", {})
        check("second start 409", status == 409 and body["ok"] is False,
              f"status {status}")
        deadline = time.time() + 10
        while time.time() < deadline:
            s, body = http("GET", f"http://127.0.0.1:{bport}/api/build")
            if body["state"] != "running":
                break
            time.sleep(0.05)
        busy.shutdown()
        busy.server_close()
    finally:
        httpd.shutdown()
        httpd.server_close()
        thread.join(timeout=2)


def main(argv=None) -> int:
    if argv is None:
        argv = sys.argv[1:]
    if argv:
        print(__doc__)
        return 2
    with tempfile.TemporaryDirectory(prefix="designer-selftest-") as tmpdir:
        tmp = Path(tmpdir)
        test_model(tmp)
        test_build(tmp)
        test_server(tmp)
    print()
    if failures:
        print(f"FAILED: {len(failures)} check(s):")
        for name in failures:
            print(f"  - {name}")
        return 1
    print("all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
