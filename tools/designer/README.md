# Anplomer designer

A local web GUI for editing gameplay stats and triggering the SVG → Java
build. Barebones by design: today it edits the hero and mob numbers, binds
artwork files to entities, and runs the generators; the level/enemy/weapon
editors grow on top of it later.

It is a Python 3 stdlib-only web app (no pip packages, no JS build step, no
CDN assets). The server binds `127.0.0.1` only and never modifies anything
outside the project root you point it at.

## Run it

```sh
python3 tools/designer/designer.py                # repo root, http://127.0.0.1:8765
python3 tools/designer/designer.py --port 9000    # another port
python3 tools/designer/designer.py --no-browser  # don't open a tab
python3 tools/designer/designer.py --check       # run the selftest and exit
```

Then open <http://127.0.0.1:8765>. The page has three regions:

- **Entities** (left): the hero, each mob, then each weapon (damage,
  cooldown) from `art/stats.json`.
- **Inspector** (right): number inputs for the selected entity's stats
  (HP, attack, hurt-invulnerability for the hero; HP, attack, speed, size for
  mobs; damage, cooldown for weapons) with inline validation straight from the
  server. Below that, the
  **Artwork** block binds an SVG to the entity: a **live preview** renders the
  current file as you pick or type it, an **in-game** line says whether the
  entity is drawn from its SVG or the hand-drawn fallback, then fit `WxH`,
  anchor (prefilled to what the game actually anchors that entity at — feet
  for the hero and walkers, centre for hovering mobs), precision and the
  generated Java class name, then Bind/Remove.
- **Build** (bottom): **Convert to Java** runs the real generators in the repo
  root and shows a live progress bar plus the merged log.

## What the button does

Exactly two subprocesses, in order, with `cwd` = project root:

1. `python3 tools/statgen/stats2java.py art/stats.json` → `Stats.java`
2. `python3 tools/artgen/svg2java.py --manifest art/art.json` → art classes

A second click while a build runs is refused with HTTP 409 (not queued).
Saving stats is atomic (temp file + `os.replace`) and keeps a `.bak` of the
previous file next to it. Unknown keys in either JSON file survive every
save — the designer merges, it never rewrites from scratch.

## HTTP API

| Method | Route           | Purpose                                        |
|--------|-----------------|------------------------------------------------|
| GET    | `/`             | the single-page UI (`ui/index.html`)           |
| GET    | `/ui/<file>`    | `app.js` / `style.css` (whitelisted)           |
| GET    | `/api/state`    | `{ok, root, entities, stats, art, svgs, build, errors}` |
| POST   | `/api/stats`    | `{player, mobs}` → validate + atomic save      |
| POST   | `/api/asset`    | `{entity, src, class, fit, anchor, precision}`; `src: null` unbinds |
| GET    | `/api/svgs`    | repo-relative `.svg` paths, sorted, cap 500    |
| GET    | `/api/svg?path=` | raw SVG bytes for the inspector's live preview |
| POST   | `/api/build`    | start a build (409 while one runs)             |
| GET    | `/api/build`    | `{state, steps, progress, message, exitCode, startedAt, finishedAt}` |

Errors are JSON (`{ok: false, errors: [{field, message}]}`); every response
carries `Cache-Control: no-store`.

## Layout

```
tools/designer/
├── designer.py   # entry point + CLI (--root, --port, --no-browser, --check)
├── model.py      # Project: load/validate/save stats.json + art.json, SVG listing
├── build.py      # BuildRunner: threaded, two subprocess steps, progress + logs
├── server.py     # ThreadingHTTPServer + routing (the API table above)
├── ui/           # index.html, app.js, style.css — no external requests
└── selftest.py   # headless suite: model, build runner, server (run with --check)
```

## Adding an entity type later

1. Extend the frozen schema in `art/stats.json` (e.g. a `turrets` array) and
   the validation table in `model.py` + `tools/statgen/stats2java.py` to match.
2. Add a `kind` entry in `Project.get_entities()` so the UI lists it; the
   inspector renders whatever fields appear in `entities[i].stats`.
3. A new generated-art class is just another `art/art.json` asset entry; the
   build step needs no changes.

## Note on generated art

The art step writes `Art*.java` classes plus **`ArtBindings.java`**, and
`Renderer` draws through that registry: bind an SVG to an entity, hit
**Convert to Java**, rebuild the APK, and that entity is drawn from your SVG.
Unbound entities keep the hand-drawn vector code.

The SVG is the body, not the behaviour: running, jumping, squash, facing and
the death tumble all come from the game and are applied around the artwork —
author the player side-on facing right and let the renderer mirror it. Limbs
animate from **layer names**: name a group `leg-left`, `arm-front`, `wing`…
and it swings/flaps around its own pivot. The full convention (and the list
of recognised names) is in [`art/README.md`](../../art/README.md).

## Tests

```sh
python3 tools/designer/selftest.py        # or: designer.py --check
```

Exercises the model (round-trip, `.bak`, every validation rule, root
isolation), the build runner (stub CLIs, success + failure + progress + busy
refusal), and the server end-to-end on a loopback port (all routes, 400/404/
409 paths, path-traversal rejection).
