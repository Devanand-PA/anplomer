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
import re
import shutil
import subprocess
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
    "weapons": [
        {"id": "sword", "name": "Sword", "damage": 25, "cooldown": 0.0},
        {"id": "bow", "name": "Bow", "damage": 10, "cooldown": 0.42},
    ],
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
<path id="leg-left" d="M0 10 L5 0 L10 10 Z" fill="#f00"/></svg>
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

    for name, patch in [
        ("weapon damage high", {"weapons": [{"id": "bow", "damage": 10000}]}),
        ("weapon cooldown high", {"weapons": [{"id": "bow", "damage": 5,
                                               "cooldown": 11}]}),
        ("weapon duplicate id", {"weapons": [
            {"id": "bow", "damage": 5}, {"id": "bow", "damage": 6}]}),
        ("weapon bad id", {"weapons": [{"id": "Big Sword", "damage": 5}]}),
        ("weapon damage string", {"weapons": [{"id": "bow", "damage": "lots"}]}),
    ]:
        doc = json.loads(json.dumps(FIXTURE_STATS))
        doc.update(patch)
        errs = proj.validate_stats(doc)
        check(f"rejects {name}", bool(errs), str(errs))

    good_weapons = json.loads(json.dumps(FIXTURE_STATS))
    good_weapons["weapons"][1]["damage"] = 33
    check("valid weapons pass", not proj.validate_stats(good_weapons))

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

    # weapons are bindable entities too: the icon lands in the entity list
    art = proj.load_art()
    wpn_art = proj.update_asset(art, "bow", "art/mob.svg", "ArtBow",
                                "32x32", "center", 2)
    ents = proj.get_entities(proj.load_stats(), wpn_art)
    bow = [e for e in ents if e["id"] == "bow"][0]
    check("weapon entity carries its bound icon",
          bow["kind"] == "weapon" and bow["asset"]["src"] == "art/mob.svg"
          and bow["suggestedAnchor"] == "center" and "SVG" in bow["inGame"],
          str(bow))
    ents = proj.get_entities(proj.load_stats(), art)
    bow = [e for e in ents if e["id"] == "bow"][0]
    check("unbound weapon reports the hand-drawn fallback",
          bow["asset"] is None and "hand-drawn" in bow["inGame"], str(bow))

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

        check("state lists weapon entities",
              any(e["id"] == "bow" and e["kind"] == "weapon"
                  and e["stats"]["damage"] == 10
                  for e in body["entities"]),
              str([e["id"] for e in body["entities"]]))

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
            # like the real UI: the whole sections as loaded, edits applied
            "player": dict(FIXTURE_STATS["player"], maxHp=140),
            "mobs": FIXTURE_STATS["mobs"],
            "weapons": [
                {"id": "sword", "name": "Sword", "damage": 25, "cooldown": 0.0},
                {"id": "bow", "name": "Bow", "damage": 33, "cooldown": 0.42},
            ],
        })
        check("POST /api/stats saves", status == 200 and body["ok"])
        on_disk = json.loads((root / "art" / "stats.json").read_text())
        check("stats save landed on disk",
              on_disk["player"]["maxHp"] == 140)
        check("weapons save landed on disk",
              on_disk["weapons"][1]["damage"] == 33
              and on_disk["weapons"][0]["id"] == "sword")
        check("stats save preserved unknown keys",
              on_disk["$comment"] == FIXTURE_STATS["$comment"]
              and on_disk["surpriseKey"] == "must survive saves"
              and on_disk["player"]["hat"] == "red"
              and on_disk["mobs"][0]["hat"] == "green"
              and on_disk["weapons"] == [
                  {"id": "sword", "name": "Sword", "damage": 25, "cooldown": 0.0},
                  {"id": "bow", "name": "Bow", "damage": 33, "cooldown": 0.42},
              ])
        check("stats save kept a .bak",
              (root / "art" / "stats.json.bak").exists())

        status, body = http("POST", f"{base}/api/stats", {
            "player": {"maxHp": 0, "attack": 25, "hurtInvuln": 0.8},
        })
        check("invalid stats 400 with field errors",
              status == 400 and body["ok"] is False
              and any(e["field"] == "player.maxHp" for e in body["errors"]),
              json.dumps(body))

        status, body = http("POST", f"{base}/api/stats", {
            "weapons": [{"id": "bow", "damage": 99999}],
        })
        check("invalid weapon 400 with field errors",
              status == 400 and body["ok"] is False
              and any(e["field"] == "weapons[0].damage" for e in body["errors"]),
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

        # --- highlight: dim others, glow the named part, ignore unknown
        status, plain = http("GET", f"{base}/api/svg?path=art/mob.svg")
        status, lit = http("GET", f"{base}/api/svg?path=art/mob.svg"
                           "&highlight=no-such-part")
        check("highlight of unknown part is a no-op",
              status == 200 and lit["raw"] == plain["raw"])
        status, lit = http("GET", f"{base}/api/svg?path=art/mob.svg"
                           "&highlight=leg-left")
        check("highlight dims and glows the named part",
              status == 200 and "drop-shadow" in lit["raw"]
              and lit["raw"] != plain["raw"], lit["raw"][:120])

        # --- animation editor: rebind, list real parts, map motions
        status, body = http("POST", f"{base}/api/asset", {
            "entity": "player", "src": "art/mob.svg", "class": "ArtPlayer",
            "fit": "26x36", "anchor": "bottom-center", "precision": 2,
        })
        check("rebind for anim tests", status == 200 and body["ok"])

        status, body = http("GET", f"{base}/api/anim?entity=player")
        check("GET /api/anim lists parts + channels",
              status == 200 and body["ok"] and len(body["parts"]) >= 1
              and "limb" in body["channels"] and "flutter" in body["channels"]
              and body["explicit"] is False,
              json.dumps(body)[:200])
        part = body["parts"][0]["name"]
        check("unmapped parts show the by-name motion",
              body["parts"][0]["motion"]["channel"] == "limb"
              and body["parts"][0]["motion"]["phase"] == 0.0
              and body["parts"][0]["motion"]["amplitude"] is None
              and body["defaults"]["limb"] == 26.0,
              json.dumps(body["parts"][0]))

        status, body = http("POST", f"{base}/api/anim", {
            "entity": "player",
            "anim": {part: {"channel": "limb", "phase": 45,
                            "amplitude": 12}}})
        check("POST /api/anim saves a motion object",
              status == 200 and body["ok"], json.dumps(body))
        art_disk = json.loads((root / "art" / "art.json").read_text())
        entry = [a for a in art_disk["assets"] if a.get("entity") == "player"][0]
        check("motion object landed in art.json",
              entry.get("anim") == {part: {"channel": "limb", "phase": 45,
                                           "amplitude": 12}},
              str(entry.get("anim")))

        status, body = http("POST", f"{base}/api/anim", {
            "entity": "player", "anim": {part: "flutter"}})
        check("preset strings still accepted",
              status == 200 and body["ok"], json.dumps(body))

        status, body = http("GET", f"{base}/api/anim?entity=player")
        check("saved motion is explicit and applied",
              body["explicit"] is True
              and body["parts"][0]["motion"]["channel"] == "flutter"
              and body["parts"][0]["motion"]["phase"] == 0.0,
              json.dumps(body["parts"][0]["motion"]))

        status, body = http("POST", f"{base}/api/anim", {
            "entity": "player", "anim": {"no-such-part": "static"}})
        check("anim typo in part name 400",
              status == 400 and "no part named" in
              json.dumps(body.get("errors", [])), json.dumps(body))

        status, body = http("POST", f"{base}/api/anim", {
            "entity": "player", "anim": {part: "jetpack"}})
        check("anim unknown preset 400", status == 400)

        status, body = http("POST", f"{base}/api/anim", {
            "entity": "player",
            "anim": {part: {"channel": "warp", "phase": 0}}})
        check("anim unknown channel 400", status == 400)

        status, body = http("POST", f"{base}/api/anim", {
            "entity": "player",
            "anim": {part: {"channel": "limb", "amplitude": 999}}})
        check("anim amplitude out of range 400", status == 400)

        status, body = http("POST", f"{base}/api/anim", {
            "entity": "walker", "anim": {}})
        check("anim on unbound entity 400", status == 400)

        status, body = http("POST", f"{base}/api/anim", {
            "entity": "player", "anim": {}})
        check("POST /api/anim {} clears the mapping",
              status == 200 and body["ok"] and body["anim"] is None)
        art_disk = json.loads((root / "art" / "art.json").read_text())
        entry = [a for a in art_disk["assets"] if a.get("entity") == "player"][0]
        check("cleared mapping removed from art.json",
              "anim" not in entry, str(entry))

        # leave art.json as the other checks expect it (unbound player)
        http("POST", f"{base}/api/asset", {"entity": "player", "src": None})
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

        # --- weapon icons: bind, surface in /api/state, unbind ------------
        status, body = http("POST", f"{base}/api/asset", {
            "entity": "bow", "src": "art/mob.svg", "class": "ArtBow",
            "fit": "32x32", "anchor": "center", "precision": 2,
        })
        check("POST /api/asset binds a weapon icon",
              status == 200 and body["ok"]
              and body["asset"]["src"] == "art/mob.svg"
              and body["asset"]["anchor"] == "center", json.dumps(body))
        art_disk = json.loads((root / "art" / "art.json").read_text())
        check("weapon icon landed in art.json",
              any(a.get("entity") == "bow" and a.get("class") == "ArtBow"
                  for a in art_disk["assets"]), str(art_disk["assets"]))
        status, body = http("GET", f"{base}/api/state")
        went = [e for e in body["entities"] if e["id"] == "bow"][0]
        check("weapon entity reports the bound icon + center anchor",
              went["kind"] == "weapon" and went["asset"]["src"] == "art/mob.svg"
              and went["suggestedAnchor"] == "center"
              and "SVG" in went["inGame"], json.dumps(went))
        status, body = http("POST", f"{base}/api/asset", {
            "entity": "bow", "src": None,
        })
        check("POST /api/asset unbinds a weapon",
              status == 200 and body["ok"] and body["asset"] is None)
        status, body = http("GET", f"{base}/api/state")
        went = [e for e in body["entities"] if e["id"] == "bow"][0]
        check("unbound weapon falls back in state",
              went["asset"] is None and "hand-drawn" in went["inGame"],
              json.dumps(went))

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


# Runs the page's own preview functions in Node against a stub DOM. The zoom
# "about the cursor" maths and the clamping are the parts a human cannot
# eyeball, so they get executed for real instead of being eyeballed.
# Skipped (not failed) when Node is not installed: the designer itself needs
# only the Python stdlib.
HARNESS_JS = """
'use strict';
const fs = require('fs');

const els = {};
function el(id) {
  if (!els[id]) {
    els[id] = {
      id: id,
      style: {}, dataset: {}, textContent: '', value: '',
      classList: {
        _s: new Set(),
        add(c) { this._s.add(c); },
        remove(c) { this._s.delete(c); },
        contains(c) { return this._s.has(c); },
        toggle(c, v) { if (v) { this.add(c); } else { this.remove(c); } },
      },
      addEventListener() {}, removeAttribute() {}, setAttribute() {},
      getAttribute() { return null; },
      clientWidth: 400, clientHeight: 300,
      getBoundingClientRect() {
        return { left: 0, top: 0, width: 400, height: 300 };
      },
      querySelector() { return null; }, querySelectorAll() { return []; },
      naturalWidth: 100, naturalHeight: 100, complete: true,
    };
  }
  return els[id];
}
globalThis.document = {
  getElementById: el,
  querySelector() { return { classList: { add() {}, remove() {} } }; },
  querySelectorAll() { return []; },
  addEventListener() {},
  activeElement: null,
};
globalThis.window = { addEventListener() {} };
globalThis.CSS = { escape: (x) => x };
globalThis.fetch = () => Promise.reject(new Error('no network in this test'));

// load the page's script with boot() (network) removed, then reach into its
// scope for the pure view functions
let src = fs.readFileSync(process.argv[2], 'utf8').replace(/boot\\(\\);\\s*$/, '');
src += '\\nglobalThis.__app = { view: view, zoomBy: zoomBy, fitView: fitView, '
     + 'onPreviewLoad: onPreviewLoad, applyView: applyView };\\n';
eval(src);
const app = globalThis.__app;
const view = app.view;

const results = [];
function ok(name, cond, detail) {
  results.push([name, !!cond, detail === undefined ? '' : String(detail)]);
}

el('svgPath').value = 'art/player.svg';
app.onPreviewLoad();

const fitted = view.scale;
ok('fit scales artwork into the viewport', Math.abs(fitted - 2.84) < 1e-6, fitted);
ok('fit centres the artwork', Math.abs(view.tx - 58) < 1e-6 && Math.abs(view.ty - 8) < 1e-6,
   view.tx + ',' + view.ty);

// the point under the cursor must not move while zooming
const cx = 100, cy = 50;
const beforeX = (cx - view.tx) / view.scale;
const beforeY = (cy - view.ty) / view.scale;
app.zoomBy(2, cx, cy);
const afterX = (cx - view.tx) / view.scale;
const afterY = (cy - view.ty) / view.scale;
ok('zoom doubles the scale', Math.abs(view.scale - fitted * 2) < 1e-6, view.scale);
ok('zoom keeps the point under the cursor pinned',
   Math.abs(afterX - beforeX) < 1e-6 && Math.abs(afterY - beforeY) < 1e-6,
   beforeX.toFixed(4) + ' -> ' + afterX.toFixed(4));
ok('readout is relative to fit', el('zoomLabel').textContent === '200%',
   el('zoomLabel').textContent);

app.zoomBy(1e9, cx, cy);
ok('zoom clamps at 40x fit', Math.abs(view.scale - fitted * 40) < 1e-6, view.scale);
app.zoomBy(1e-9, cx, cy);
ok('zoom clamps at 0.1x fit', Math.abs(view.scale - fitted * 0.1) < 1e-6, view.scale);

// a highlight swap re-loads the same file: the view must survive it
const scaleBefore = view.scale, txBefore = view.tx;
el('svgPreview').naturalWidth = 999;
app.onPreviewLoad();
ok('highlight swap keeps the current view',
   view.scale === scaleBefore && view.tx === txBefore);

// a genuinely different file starts from a fresh fit
el('svgPreview').naturalWidth = 100;
el('svgPath').value = 'art/mob.svg';
app.onPreviewLoad();
ok('a different file re-fits', Math.abs(view.scale - fitted) < 1e-6, view.scale);

let bad = 0;
for (const r of results) {
  if (r[1]) { console.log('  ok    ' + r[0]); }
  else { bad++; console.log('  FAIL  ' + r[0] + (r[2] ? ': ' + r[2] : '')); }
}
process.exit(bad ? 1 : 0);
"""


def test_ui_math(tmp: Path) -> None:
    """Executes the preview view maths in Node, when Node is available."""
    print("ui math:")
    node = shutil.which("node")
    if node is None:
        print("  skip  preview zoom/pan math (node not installed)")
        return
    harness_path = tmp / "preview_math.js"
    harness_path.write_text(HARNESS_JS, encoding="utf-8")
    try:
        proc = subprocess.run(
            [node, str(harness_path), str(HERE / "ui" / "app.js")],
            capture_output=True, text=True, timeout=60)
    except (OSError, subprocess.TimeoutExpired) as exc:
        check("preview zoom/pan math runs", False, str(exc))
        return
    for line in proc.stdout.rstrip().splitlines():
        print(line)
    if proc.returncode != 0 and not proc.stdout.strip():
        check("preview zoom/pan math runs", False,
              (proc.stderr.strip().splitlines() or ["no output"])[-1][:200])
    else:
        check("preview zoom/pan math (executed in node)", proc.returncode == 0)


def test_ui(tmp) -> None:
    """Static checks on the single-page UI.

    The page has no build step and no JS test runner, so these cover the
    failure mode that actually bites: an id referenced by app.js that does
    not exist in index.html (a silent no-op in the browser), plus the
    structural pieces the feature set depends on.
    """
    print("ui:")
    ui = HERE / "ui"
    html = (ui / "index.html").read_text(encoding="utf-8")
    js = (ui / "app.js").read_text(encoding="utf-8")
    css = (ui / "style.css").read_text(encoding="utf-8")

    # every $('id') in the script must exist as an id in the markup
    ids_in_html = set(re.findall(r'id="([A-Za-z0-9_-]+)"', html))
    ids_in_js = set(re.findall(r"\$\('([A-Za-z0-9_-]+)'\)", js))
    missing = sorted(ids_in_js - ids_in_html)
    check("every $('id') used by app.js exists in index.html",
          not missing, f"missing: {missing}")

    check("preview pane is outside the scrolling area",
          html.index('previewViewport') < html.index('class="scroll"'),
          "preview should stay visible while editing")
    check("zoom controls present",
          all(k in html for k in ("zoomInBtn", "zoomOutBtn", "zoomFitBtn",
                                  "zoomLabel")))
    check("zoom/pan implemented",
          all(k in js for k in ("function zoomBy", "function fitView",
                                "function applyView", "pointerdown", "wheel")))
    check("preview is bounded by the viewport, not the fit box",
          ".preview-viewport" in css and "overflow: hidden" in css
          and "clamp(" in css)
    check("pan cursor + transform-origin",
          "cursor: grab" in css and "transform-origin: 0 0" in css)
    check("animation help box present",
          '<details class="help">' in html and "How animation works" in html
          and "</summary>" in html)
    check("one-click animation presets",
          all(f'data-preset="{k}"' in html
              for k in ("auto", "wave", "sway", "freeze"))
          and "function applyPreset" in js)
    check("animation rows stay compact (name/channel/phase/amplitude)",
          "by-name" not in js and "animName" not in js)
    # regression: the app toggles .hidden all over; without a global rule the
    # class is inert and "hidden" panels stay on screen
    check("global .hidden rule exists",
          re.search(r"^\.hidden\s*\{[^}]*display:\s*none", css, re.M) is not None,
          "classList.add('hidden') is a no-op without it")
    check("toast keeps its fade despite the global rule",
          ".toast.hidden" in css and "display: block !important" in css)
    check("no stale 'not wired into Renderer' claim",
          "not yet wired into Renderer" not in html)
    check("weapons get the artwork block (no blanket hide)",
          ".art-block').classList.add('hidden')" not in js)
    check("weapon stats edits target weapons[i] in state.stats",
          "weapons[${entity.index}]" in js and "e.kind === 'weapon'" in js)
    check("weapon name rows reuse the generic list/idx inputs",
          'data-list="${list}"' in js and "input[data-list]" in js)
    check("weapon icons skip the animation panel",
          "isWeapon" in js and "$('animBlock').classList.add('hidden')" in js)

    test_ui_math(tmp)


def main(argv=None) -> int:
    if argv is None:
        argv = sys.argv[1:]
    if argv:
        print(__doc__)
        return 2
    with tempfile.TemporaryDirectory(prefix="designer-selftest-") as tmpdir:
        tmp = Path(tmpdir)
        test_model(tmp)
        test_ui(tmp)
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
