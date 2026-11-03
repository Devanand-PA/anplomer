# Anplomer designer

A local web GUI for editing gameplay stats and triggering the SVG → Java
build. Barebones by design: today it edits the hero, mob and weapon numbers,
binds artwork files to entities (weapon icons included), and runs the
generators; the level/enemy editors grow on top of it later.

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

- **Entities** (left): three tabs — Player, Mobs, Weapons — so the list stays
  short; the hero, each mob and each weapon (name, damage, cooldown) from
  `art/stats.json`.
- **Preview** (top of the inspector, always visible while you work): the
  bound SVG, big enough to judge. Drag to pan, scroll to zoom about the
  cursor, double-click or **Fit** to re-fit; `+`/`-` step the zoom and the
  readout is relative to fit (100% = fits the box). Hovering a part in the
  Animation panel dims the rest of the artwork and lights that part up, so
  you can always tell which vector you are editing.
- **Inspector** (right): number inputs for the selected entity's stats
  (HP, attack, hurt-invulnerability for the hero; HP, attack, speed, size for
  mobs; damage, cooldown for weapons) with inline validation straight from the
  server. Below that, the
  **Artwork** block binds an SVG to any entity — for a weapon that SVG is its
  **icon**, drawn on the secondary disc, the HUD chip and the loadout rows
  after Convert to Java (suggested anchor `center`, since icons are drawn
  centred); for the hero and mobs it is the body. A **live preview** renders the
  current file as you pick or type it, an **in-game** line says whether the
  entity is drawn from its SVG or the hand-drawn fallback, then fit `WxH`,
  anchor (prefilled to what the game actually anchors that entity at — feet
  for the hero and walkers, centre for hovering mobs), precision and the
  generated Java class name, then Bind/Remove. Below that, the **Animation**
  panel (characters only — hidden for weapons, whose icons are static
  glyphs): a collapsible **"How animation works"** help box, a row of
  **quick-set** presets (Auto / Wave / Sway all / Freeze — they only fill the
  rows in, nothing is written until you save), and one row per part with a
  **channel** dropdown (`limb`, `arm`, `wing`, `flutter`, `none`) plus
  **phase** and **amplitude** editors in degrees — the whole motion
  definition, prefilled with the by-name default and hoverable to identify
  the part. Save makes the mapping explicit and authoritative (the button
  shows when there are unsaved changes); "Reset to layer names" clears it
  back to inference. Nothing about the character's shape is assumed: a snake
  is segments on `limb` with phases 0/60/120/..., a ghost is everything on
  `flutter`.
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
| POST   | `/api/asset`    | `{entity, src, class, fit, anchor, precision}`; `entity` is `player`, a mob id or a weapon id; `src: null` unbinds |
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
isolation), the UI files (every `$('id')` the script uses exists in the
markup, zoom/pan and help-box structure, the global `.hidden` rule), the
preview view maths (executed in Node when it is installed: fit, zoom about
the cursor, clamping, highlight swaps — skipped, not failed, without Node),
the build runner (stub CLIs, success + failure + progress + busy refusal),
and the server end-to-end on a loopback port (all routes, 400/404/409 paths,
path-traversal rejection).
