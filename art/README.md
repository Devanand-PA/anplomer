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
* Flatten gradients to solid fills, or accept the middle-stop approximation
  that the converter reports as a warning.
* Compound paths with holes need `fill-rule: evenodd`.
* Stroke caps/joins are not baked; strokes are drawn with the round default.

## Why not VectorDrawable

`VectorDrawable` would need `Resources`, `Context` and XML parsing at runtime,
which breaks the headless test harness (`tools/run_headless_test.sh` compiles
`Renderer` against a stub `android.graphics`) and adds first-draw parsing cost.
Baked `Path` code keeps `Renderer` on plain `Canvas` primitives, works in the
JVM smoke test, and is the fastest option at draw time.
