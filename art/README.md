# art/ — source artwork

SVG sources for the game's sprites live here. They are **not** shipped in the
APK: `tools/artgen/svg2java.py` bakes them into Java `Path` code under
`app/src/main/java/com/example/anplomer/`, and only that generated code is
compiled into the game.

```
art/
├── art.json          manifest: which SVG becomes which Java class
└── *.svg             Inkscape sources (add these as the art lands)
```

## Workflow

```bash
# after editing/adding an SVG and listing it in art.json
tools/artgen/regen.sh            # write the generated .java files
tools/artgen/regen.sh --check    # verify without writing
python3 tools/artgen/selftest.py # if you touched the generator itself
```

Then rebuild the app as usual. Generated classes are committed alongside
`Levels.java`, so a plain Gradle build needs no Python.

## Authoring rules

Full detail (and the complete list of what is and is not supported) is in
[`tools/artgen/README.md`](../tools/artgen/README.md). The short version:

* Design at the sprite's **local frame** — the same units the renderer already
  uses (`Renderer.drawPlayer` works in a 26×36 box with the origin at the feet).
* Use `fit` in `art.json` rather than scaling inside Inkscape; the tool scales
  the ink box to `WxH` and moves the anchor to the origin.
* **Path ▸ Object to Path** everything. No text, no embedded images, no
  filters, masks, clip paths or patterns.
* One `<g id="...">` per part that Java will animate separately (run cycle,
  scarf, squash/stretch, weapon swing). The `id` becomes the `Part` name.
* A **weapon icon** binds in the designer's Weapons tab and is drawn centred
  on the secondary disc, the HUD chip and the loadout rows — design it in a
  square-ish frame and keep the manifest `anchor` at `center`.

## Animation: you author it, the game drives it

The game supplies a handful of **motion channels** — `limb` (walk cycle),
`arm` (also walk-driven; use phases to counter-swing), `wing` (flap) and
`flutter` (a continuous sway that runs even when standing) — plus the game
state that drives them (walking, airborne, dead). For each part of your SVG
you decide the channel, the **phase** (where in the cycle it sits, degrees)
and the **amplitude** (how far it swings). Nothing is assumed about the
shape of your character:

* humanoid walk: `leg-left` limb@0, `leg-right` limb@180, shoes on the same
  phases (they then ride the legs' pivots as one rigid group), arms at
  180/0 to counter-swing
* a snake: every body segment on `limb`, phases 0, 60, 120, 180, ... — a
  travelling wave
* a ghost or slime: everything on `flutter`, phases spread across the body
* capes, tails, antennae: `flutter` with a phase offset and a small amplitude

**How do you know the layer names?** They are the Inkscape layer/group ids
(rename them in the Layers panel; `Object ▸ Properties` also sets ids).
Bind even a rough sketch, then open the designer's **Animation** panel:
it lists every part the generator sees — hovering a row dims the rest of the
artwork and makes that part glow, so you always know which vector you are
assigning. The listed names are exactly what the generator bakes.

Without any explicit mapping the layer names still infer a sensible default
(the table below), and the panel prefills its dropdowns with it.

| the id contains… | default channel |
|------------------|-----------------|
| `leg`, `foot`, `shoe`, `boot` | `limb` (`left`/`front` at phase 0, `right`/`back` at 180) |
| `arm`, `hand` | `arm` (same sides, opposite phase to the limbs) |
| `wing` | `wing` (mirrored by side) |
| anything else | `none` (static) |

Everything else — squash and stretch on landing, the mirror when facing
left, the death tumble, the hover wobble on flyers — is applied by the game
around the whole sprite.

## Why not VectorDrawable

`VectorDrawable` would need `Resources`, `Context` and XML parsing at runtime,
which breaks the headless test harness (`tools/run_headless_test.sh` compiles
`Renderer` against a stub `android.graphics`) and adds first-draw parsing cost.
Baked `Path` code keeps `Renderer` on plain `Canvas` primitives, works in the
JVM smoke test, and is the fastest option at draw time.
