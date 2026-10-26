# SVG → Java art pipeline

The game ships **no bitmaps and no SVG parser**. Artwork is authored as SVG in
Inkscape and *baked* into plain Java `Path` construction code by
`svg2java.py`, which runs on the developer's PC. At runtime the game just calls
`Canvas.drawPath` a handful of times per sprite — the same cost as the
hand-written drawing code in `Renderer.java` it replaces, with zero parsing,
zero asset loading and no `android.graphics` API beyond `Canvas`/`Paint`/`Path`.

```
art/*.svg ──► svg2java.py ──► Art<Name>.java ──► javac ──► APK
   (Inkscape)     (PC only)      (generated)     (build)
```

## Quick start

```bash
# one sprite, sized to the player's 26x36 local frame
python3 tools/artgen/svg2java.py art/player.svg --class ArtPlayer --fit 26x36

# every sprite in the manifest (this is what the designer GUI calls)
python3 tools/artgen/svg2java.py --manifest art/art.json

# check without writing, machine-readable
python3 tools/artgen/svg2java.py art/player.svg --check --json
```

`svg2java.py` needs Python 3 and nothing else — no pip packages, no Android
SDK, no Inkscape CLI. Only the generated `.java` file is ever compiled into the
game.

## CLI contract

This is the interface the level / enemy / weapon designer should code against:
it is deliberately boring and stable.

### Exit codes

| code | meaning |
|------|---------|
| `0`  | success |
| `2`  | bad usage (argparse: unknown flag, bad `--fit`, both/neither input) |
| `3`  | input or output error (missing file, malformed XML, unwritable path) |
| `4`  | conversion succeeded but reported warnings, and `--strict` was given |

### Output streams

* **stdout** — the Java source when `--stdout` is passed; otherwise the human
  summary. With `--json` stdout carries *only* the JSON document.
* **stderr** — every warning and error, always, one per line, prefixed
  `warning: ` / `error: `. Safe to surface verbatim in a GUI log pane.

### `--json` document

```json
{
  "ok": true,
  "assets": [
    {
      "src": "art/player.svg",
      "class": "ArtPlayer",
      "package": "com.example.anplomer",
      "out": "app/src/main/java/com/example/anplomer/ArtPlayer.java",
      "parts": 19,
      "segments": 147,
      "width": 18.63,
      "height": 36.0,
      "scale": 0.4061,
      "bytes": 11902,
      "written": true,
      "warnings": [],
      "partNames": ["leg-left", "leg-right", "shoe-left", "..."]
    }
  ]
}
```

On failure the document is `{"ok": false, "error": "..."}` (with `assets`
absent). The `partNames` list is what a GUI needs to let the artist click a
layer in the game preview and jump back to the SVG element.

### Flags

| flag | meaning |
|------|---------|
| `SVG` | source file; must be an `<svg>` root |
| `--manifest FILE` | batch mode, see below (mutually exclusive with `SVG`) |
| `--class NAME` | Java class name, default `Art<FileStem>` in PascalCase |
| `--package P` | Java package, default `com.example.anplomer` |
| `--out FILE` | exact output path |
| `--out-dir DIR` | output directory, filename from `--class` |
| `--fit WxH` | scale the ink box to fit `WxH` (aspect preserved) and move the anchor to the origin |
| `--scale K` | uniform scale with no re-anchoring (art keeps its own offsets) |
| `--anchor A` | where the origin lands under `--fit`: `bottom-center` (default), `bottom-left`, `bottom-right`, `center`, `top-left` |
| `--precision N` | decimal places in the generated floats, default `2` |
| `--check` | parse and report, write nothing |
| `--stdout` | emit the Java to stdout instead of a file |
| `--json` | machine-readable summary on stdout |
| `--strict` | exit `4` if anything warned |

Default output path follows the package:
`app/src/main/java/<package as dirs>/<Class>.java`.

### Manifest (`art/art.json`)

```json
{
  "package": "com.example.anplomer",
  "outDir": "app/src/main/java/com/example/anplomer",
  "assets": [
    { "src": "art/player.svg", "class": "ArtPlayer", "fit": "26x36" },
    { "src": "art/enemies/walker.svg", "class": "ArtWalker", "fit": "24x24", "anchor": "bottom-center" }
  ]
}
```

Per-asset keys: `src` (required), `class`, `package`, `fit`, `anchor`,
`precision`, `out`, and the optional `entity` (an id from `art/stats.json`, or
`"player"`). Relative paths resolve against the repository root, so the
same manifest works from any working directory — and from a GUI process that
shells out.

Every manifest run also writes **`ArtBindings.java`** into `outDir`: the
entity → artwork registry the game reads. `Renderer` calls
`ArtBindings.has(entity)`; when an asset declares `"entity"`, the registry
maps it to the generated class (`has` → `true`, `draw` → `ArtPlayer.draw(...)`,
plus `width`/`height` so the caller can fit the art to the entity's box).
Assets without an `entity` key are still baked as classes but stay out of the
registry. An **empty** manifest still emits the registry — every method a
no-op — so the game always compiles and every entity falls back to the
hand-drawn vector code. Duplicate `entity` bindings keep the first and warn.

## Generated class shape

```java
public final class ArtPlayer {
    public static final float WIDTH = 18.63f;   // ink extents in game units
    public static final float HEIGHT = 36f;

    public static final class Part {
        public final String name;
        public final int color;        // 0 = no fill
        public final int strokeColor;  // 0 = no stroke
        public final float strokeWidth;
        public final Path path;
    }

    public static int count();
    public static Part part(int i);
    public static void draw(Canvas c, Paint fill, Paint stroke,
                            float x, float y, float scale);
}
```

`draw` saves the canvas, translates to `(x, y)`, applies `scale`, paints each
part in document order and restores. Entities are scaled to their hitbox
height by the caller (`scale = entity.h / ArtBindings.height(entity)`), so
`fit` controls proportions and the entity box controls on-screen size. It **mutates** the two `Paint` objects
(style, colour, stroke width) exactly like the hand-written drawing code, so a
caller can hand it the renderer's shared `fill`/`stroke` paints.

All coordinates are absolute `moveTo` / `lineTo` / `quadTo` / `cubicTo` /
`close` values in game units with **y down** (SVG and `Canvas` agree), with
every transform already multiplied in. Nothing is computed at runtime.

### Why `bottom-center` is the default anchor

`Renderer.drawPlayer` translates to `(p.x + w/2, p.y + h)` — the player's
*feet*. With `--fit WxH --anchor bottom-center`, the generated art's local
origin is its own bottom centre, so:
`ArtPlayer.draw(canvas, fill, stroke, x + w/2, y + h, 1f)` lands correctly with
no arithmetic. Entities anchored at the centre use `--anchor center`.

## What Inkscape must not export (and why)

The bake step has to turn every style into one colour per sub-path, because
`Canvas.drawPath` takes a `Paint`, not a paint server.

| feature | handling |
|---------|----------|
| `path` (all commands, absolute/relative, `A` arcs) | fully supported |
| `rect` (incl. `rx`/`ry`), `circle`, `ellipse`, `line`, `polyline`, `polygon` | converted to path segments |
| `transform` (`translate`, `scale`, `rotate`, `matrix`, `skewX`, `skewY`, nested) | baked into coordinates |
| `style=""` and presentation attributes | both read; `style` wins |
| `fill`, `stroke`, `stroke-width`, `fill-opacity`, `stroke-opacity`, `opacity` | supported (opacity multiplies down the tree) |
| `fill-rule: evenodd` | supported → `Path.FillType.EVEN_ODD` |
| `display:none`, `visibility:hidden` | dropped |
| `<g>` groups | flattened; `id`/`inkscape:label` names the parts inside |
| gradients (`linearGradient`, `radialGradient`) | **approximated** with the stop nearest offset 0.5 + warning |
| `stroke-linecap`, `stroke-linejoin`, `dasharray` | ignored (round joins are the default in the renderer) |
| `text`, `image`, `use`, `filter`, `mask`, `clipPath`, `pattern`, nested `<svg>` | skipped + warning |

Two unit notes: lengths may carry CSS units (`1.6px`, `2pt`, `3mm`) and are
converted, and the `<svg width height>` attributes are **ignored** — geometry is
taken in `viewBox` user units, so sizing is the job of `--fit`/`--scale`.

Use **Path ▸ Object to Path** (so text and primitives become paths), flatten
gradients to solid fills when the exact colour matters, and prefer hex colours.
Inkscape's *Plain SVG* or *Optimized SVG* both work; *Inkscape SVG* also works
and is the only way to keep layer names (`inkscape:label`) as part names.

**One layer/group per animated part.** The generator only produces rest-pose
geometry. Everything time-varying stays in Java: the run cycle
(`p.runCycle`), scarf flutter, squash/stretch, death spin, and the sword swing
arc are driven by gameplay state and cannot be baked. Give each part that Java
will move separately its own `<g id="...">` so it becomes its own `Part`, which
`Art*.part(i)` exposes for custom transforms.

## Self-test

```bash
python3 tools/artgen/selftest.py            # verify
python3 tools/artgen/selftest.py --update   # rewrite golden files
```

Three layers: parser unit checks (relative commands, `S`/`T` reflection, arcs,
transform order, colours), golden-file comparison of `samples/*.svg` →
`samples/expected/*.java`, and an end-to-end `javac` + run of the generated
classes against `tools/stub/android/graphics`, which also proves no coordinate
is non-finite. Run this after touching `svgpath.py`/`svg2java.py`.

## Files

```
tools/artgen/
├── svgpath.py        SVG parsing: path data, arcs→cubics, transforms, colours (library)
├── svg2java.py       CLI + Java emitter + manifest batch mode
├── selftest.py       parser/pipeline/golden/compile checks
├── regen.sh          regenerate everything listed in art/art.json
└── samples/          fixture SVG + expected output (exercised by selftest.py)
```

`svgpath.py` is importable on its own:

```python
import sys; sys.path.insert(0, "tools/artgen")
import svgpath
segs = svgpath.parse_path_data("M0 0 A10 10 0 0 1 20 0")
print(svgpath.bbox_of_segments(segs))
```

That matters if the designer wants to compute hitboxes or preview bounds
without spawning the CLI.
