#!/usr/bin/env python3
"""Convert SVG artwork into Java `Path` construction code for Anplomer.

The game never parses SVG: this runs on the artist's PC (or from the level /
entity designer) and writes a plain Java class that the APK compiles. Rendering
then costs one `Canvas.drawPath` per sub-path and nothing else.

Usage
-----
    # one file
    python3 tools/artgen/svg2java.py art/player.svg --class ArtPlayer --fit 26x36

    # every asset listed in a manifest (what the GUI designer calls)
    python3 tools/artgen/svg2java.py --manifest art/art.json

    # check only, machine-readable summary on stdout
    python3 tools/artgen/svg2java.py art/player.svg --check --json

Exit codes: 0 ok, 2 bad usage, 3 input/output error, 4 warnings under --strict.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import svgpath  # noqa: E402
from svgpath import Matrix, SvgError  # noqa: E402

PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_PACKAGE = "com.example.anplomer"

SHAPE_TAGS = {"path", "rect", "circle", "ellipse", "line", "polyline", "polygon"}
CONTAINER_TAGS = {"svg", "g", "a", "switch"}
SKIP_TAGS = {"defs", "clipPath", "mask", "pattern", "symbol", "marker",
             "filter", "style", "title", "desc", "metadata", "script",
             "linearGradient", "radialGradient", "stop", "namedview"}

INHERITED = {"fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin",
             "stroke-opacity", "fill-opacity", "fill-rule", "visibility"}
DEFAULTS = {
    "fill": "#000000", "stroke": "none", "stroke-width": "1",
    "fill-opacity": "1", "stroke-opacity": "1", "fill-rule": "nonzero",
    "stroke-linecap": "butt", "stroke-linejoin": "miter",
    "visibility": "visible",
}

JAVA_KEYWORDS = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
    "class", "const", "continue", "default", "do", "double", "else", "enum",
    "extends", "final", "finally", "float", "for", "goto", "if", "implements",
    "import", "instanceof", "int", "interface", "long", "native", "new",
    "package", "private", "protected", "public", "return", "short", "static",
    "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
    "transient", "try", "void", "volatile", "while", "true", "false", "null",
}


@dataclass
class Part:
    """One drawable sub-path after style resolution and transform baking."""

    name: str
    color: int = 0              # ARGB; 0 means "no fill"
    stroke_color: int = 0       # ARGB; 0 means "no stroke"
    stroke_width: float = 0.0
    even_odd: bool = False
    segments: list = field(default_factory=list)


@dataclass
class Result:
    ok: bool
    summary: dict
    warnings: list[str]
    java: str | None = None
    parts: list = field(default_factory=list)


# ----------------------------------------------------------------------
# style helpers
# ----------------------------------------------------------------------

def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _label(elem) -> str | None:
    """Inkscape writes layer names to `inkscape:label` (a namespaced attr)."""
    for key, value in elem.attrib.items():
        if key.rsplit("}", 1)[-1] in ("label", "name"):
            if value and value.strip():
                return value.strip()
    return None


def _parse_style_attr(text: str | None) -> dict[str, str]:
    out: dict[str, str] = {}
    if not text:
        return out
    for decl in text.split(";"):
        if ":" not in decl:
            continue
        key, value = decl.split(":", 1)
        key = key.strip()
        if key:
            out[key] = value.strip()
    return out


def _prop(elem, inline: dict[str, str], name: str) -> str | None:
    if name in inline:
        return inline[name]
    raw = elem.get(name)
    return raw if raw is not None else None


# CSS units an artist may leave on a length. SVG user units == CSS px here.
_LENGTH_UNITS = {"px": 1.0, "pt": 4.0 / 3.0, "pc": 16.0, "mm": 96.0 / 25.4,
                 "cm": 96.0 / 2.54, "in": 96.0, "": 1.0}


def _float(raw: str | None, default: float = 0.0) -> float:
    """Lenient length parse: handles `2`, `2px`, `2 pt`, `50%`."""
    if raw is None:
        return default
    s = raw.strip()
    if s.endswith("%"):
        try:
            return float(s[:-1]) / 100.0
        except ValueError:
            return default
    m = re.fullmatch(r"([-+0-9.eE]+)\s*([A-Za-z]*)", s)
    if not m:
        return default
    unit = m.group(2).lower()
    if unit not in _LENGTH_UNITS:
        return default
    try:
        return float(m.group(1)) * _LENGTH_UNITS[unit]
    except ValueError:
        return default


# ----------------------------------------------------------------------
# gradients (approximated: Canvas.drawPath has no paint server)
# ----------------------------------------------------------------------

def _stop_color(stop) -> tuple[int, int, int] | None:
    inline = _parse_style_attr(stop.get("style"))
    raw = inline.get("stop-color", stop.get("stop-color", "#000000"))
    try:
        parsed = svgpath.parse_color(raw)
    except SvgError:
        return None
    return parsed if isinstance(parsed, tuple) else None


def _offset(stop) -> float:
    raw = (stop.get("offset") or "0").strip()
    try:
        return float(raw.rstrip("%")) / (100.0 if raw.endswith("%") else 1.0)
    except ValueError:
        return 0.0


def collect_gradients(root, warnings: list[str]) -> dict[str, tuple[int, int, int]]:
    """Maps gradient id -> a single representative colour.

    A gradient cannot survive the bake-to-Path stage, so the stop closest to
    the middle of the ramp is used and the approximation is reported. Artists
    who want exact colours should flatten gradients (Path > Object to Path, or
    a solid fill) before export.
    """
    stops: dict[str, list[tuple[float, tuple[int, int, int]]]] = {}
    hrefs: dict[str, str] = {}
    order: list[str] = []

    for elem in root.iter():
        if local_name(elem.tag) not in ("linearGradient", "radialGradient"):
            continue
        gid = elem.get("id")
        if not gid:
            continue
        order.append(gid)
        found = []
        for child in elem:
            if local_name(child.tag) != "stop":
                continue
            rgb = _stop_color(child)
            if rgb is not None:
                found.append((_offset(child), rgb))
        stops[gid] = found
        for key, value in elem.attrib.items():
            if key.rsplit("}", 1)[-1] == "href" and value.startswith("#"):
                hrefs[gid] = value[1:]

    out: dict[str, tuple[int, int, int]] = {}
    for gid in order:
        ramp = stops.get(gid) or []
        # A gradient may inherit its stops from another one via xlink:href.
        seen: set[str] = set()
        cursor = gid
        while not ramp and cursor in hrefs and cursor not in seen:
            seen.add(cursor)
            cursor = hrefs[cursor]
            ramp = stops.get(cursor) or []
        if not ramp:
            warnings.append(f"gradient '{gid}' has no usable stops; skipped")
            continue
        out[gid] = min(ramp, key=lambda s: abs(s[0] - 0.5))[1]
    return out


def _resolve_paint(paint, grads, name: str, what: str,
                   warnings: list[str]):
    if paint is None:
        return None
    if isinstance(paint, tuple) and len(paint) == 2 and paint[0] == "ref":
        gid = paint[1]
        if gid in grads:
            warnings.append(
                f"{name}: {what} uses gradient '{gid}'; baked to one solid colour "
                f"(flatten gradients in Inkscape for exact output)")
            return grads[gid]
        warnings.append(f"{name}: {what} references unknown gradient '{gid}'; "
                        f"using magenta so it is visible")
        return (255, 0, 255)
    return paint


# ----------------------------------------------------------------------
# document -> parts
# ----------------------------------------------------------------------

def _unique(name: str, used: set[str]) -> str:
    if name not in used:
        used.add(name)
        return name
    i = 2
    while f"{name}_{i}" in used:
        i += 1
    final = f"{name}_{i}"
    used.add(final)
    return final


def collect_parts(root, warnings: list[str], grads) -> list[Part]:
    parts: list[Part] = []
    used_names: set[str] = set()
    shape_counter = [0]

    def walk(elem, matrix: Matrix, style: dict[str, str], opacity: float,
             layer: str | None) -> None:
        tag = local_name(elem.tag)
        if tag in SKIP_TAGS:
            return
        inline = _parse_style_attr(elem.get("style"))

        if _prop(elem, inline, "display") == "none":
            return
        if _prop(elem, inline, "visibility") in ("hidden", "collapse"):
            return

        local_matrix = matrix
        if elem.get("transform"):
            try:
                local_matrix = matrix @ svgpath.parse_transform(elem.get("transform"))
            except SvgError as exc:
                warnings.append(f"<{tag}>: {exc}; transform ignored")
        here_opacity = opacity * max(0.0, min(1.0, _float(
            _prop(elem, inline, "opacity"), 1.0)))

        if tag in CONTAINER_TAGS:
            child_style = dict(style)
            for prop in INHERITED:
                value = _prop(elem, inline, prop)
                if value is not None:
                    child_style[prop] = value
            child_layer = _label(elem) or elem.get("id") or layer
            for child in elem:
                walk(child, local_matrix, child_style, here_opacity, child_layer)
            return

        if tag not in SHAPE_TAGS:
            warnings.append(f"<{tag}> is not supported and was skipped")
            return

        shape_counter[0] += 1
        name = elem.get("id") or _label(elem) or (
            f"{layer}_{shape_counter[0]}" if layer else f"shape_{shape_counter[0]}")
        name = _unique(name, used_names)

        shape_style = dict(style)
        for prop in INHERITED:
            value = _prop(elem, inline, prop)
            if value is not None:
                shape_style[prop] = value

        try:
            segments = svgpath.shape_to_segments(tag, elem.attrib)
        except SvgError as exc:
            raise SvgError(f"{name}: {exc}") from None
        if segments is None or not segments:
            return
        segments = svgpath.transform_segments(segments, local_matrix)

        fill = _resolve_paint(svgpath.parse_color(shape_style.get("fill")),
                              grads, name, "fill", warnings)
        stroke = _resolve_paint(svgpath.parse_color(shape_style.get("stroke")),
                                grads, name, "stroke", warnings)
        fill_alpha = _float(shape_style.get("fill-opacity"), 1.0) * here_opacity
        stroke_alpha = _float(shape_style.get("stroke-opacity"), 1.0) * here_opacity
        stroke_width = _float(shape_style.get("stroke-width"), 1.0)
        baked_width = stroke_width * local_matrix.max_scale()

        color = svgpath.to_argb(fill, fill_alpha) if fill is not None else 0
        stroke_color = (svgpath.to_argb(stroke, stroke_alpha)
                        if stroke is not None and baked_width > 0.0 else 0)
        # An invisible fully transparent fill is the same as no fill.
        if color != 0 and (color >> 24) == 0:
            color = 0
        if stroke_color != 0 and (stroke_color >> 24) == 0:
            stroke_color = 0
        if color == 0 and stroke_color == 0:
            return

        parts.append(Part(
            name=name,
            color=color,
            stroke_color=stroke_color,
            stroke_width=baked_width if stroke_color != 0 else 0.0,
            even_odd=shape_style.get("fill-rule", "nonzero").strip() == "evenodd",
            segments=segments,
        ))

    root_style = dict(DEFAULTS)
    walk(root, Matrix(), root_style, 1.0, None)
    return parts


# ----------------------------------------------------------------------
# fitting / baking units
# ----------------------------------------------------------------------

def parse_fit(text: str) -> tuple[float, float]:
    m = re.fullmatch(r"\s*([0-9.]+)\s*[x,]\s*([0-9.]+)\s*", text)
    if not m:
        raise argparse.ArgumentTypeError(
            f"--fit wants WxH (e.g. 26x36), got {text!r}")
    w, h = float(m.group(1)), float(m.group(2))
    if w <= 0 or h <= 0:
        raise argparse.ArgumentTypeError("--fit dimensions must be positive")
    return (w, h)


ANCHORS = {
    "bottom-center": lambda b: ((b[0] + b[2]) / 2.0, b[3]),
    "bottom-left": lambda b: (b[0], b[3]),
    "bottom-right": lambda b: (b[2], b[3]),
    "center": lambda b: ((b[0] + b[2]) / 2.0, (b[1] + b[3]) / 2.0),
    "top-left": lambda b: (b[0], b[1]),
}


def normalise(parts: list[Part], fit, scale: float | None, anchor: str):
    """Bakes a uniform scale (and origin shift) into every coordinate.

    With --fit the artwork's ink box is scaled to fit inside WxH preserving
    aspect, and `anchor` is moved to (0, 0). The default anchor,
    bottom-center, matches the player's local frame in `Renderer.drawPlayer`
    (origin at the feet), so the renderer can draw at `(x, y)` with no maths.
    """
    if not parts:
        return (0.0, 0.0, 0.0)
    segs = [s for p in parts for s in p.segments]
    b = svgpath.bbox_of_segments(segs)
    pad = max((p.stroke_width for p in parts), default=0.0) / 2.0
    b = (b[0] - pad, b[1] - pad, b[2] + pad, b[3] + pad)
    bw, bh = b[2] - b[0], b[3] - b[1]

    if fit is not None:
        tw, th = fit
        k = min(tw / bw, th / bh) if bw > 0 and bh > 0 else 1.0
        ax, ay = ANCHORS[anchor](b)
        matrix = Matrix(a=k, d=k, e=-ax * k, f=-ay * k)
    elif scale is not None:
        k = scale
        matrix = Matrix(a=k, d=k)
    else:
        return (bw, bh, 1.0)

    for part in parts:
        part.segments = svgpath.transform_segments(part.segments, matrix)
        part.stroke_width *= k
    return (bw * k, bh * k, k)


# ----------------------------------------------------------------------
# Java emission
# ----------------------------------------------------------------------

def fmt(v: float, precision: int) -> str:
    if abs(v) < 0.5 * 10.0 ** (-precision):
        v = 0.0
    text = f"{v:.{precision}f}"
    if "." in text:
        text = text.rstrip("0").rstrip(".")
    if text in ("", "-0"):
        text = "0"
    return text + "f"


def hex_argb(v: int) -> str:
    return f"0x{v:08X}"


def method_name(name: str) -> str:
    ident = re.sub(r"[^0-9A-Za-z_]", "_", name)
    ident = re.sub(r"_+", "_", ident).strip("_") or "part"
    if ident[0].isdigit():
        ident = "p" + ident
    if ident in JAVA_KEYWORDS:
        ident += "_"
    return ident


def _statements(part: Part, precision: int) -> list[str]:
    out = []
    for s in part.segments:
        op = s[0]
        if op == "M":
            out.append(f"p.moveTo({fmt(s[1], precision)}, {fmt(s[2], precision)});")
        elif op == "L":
            out.append(f"p.lineTo({fmt(s[1], precision)}, {fmt(s[2], precision)});")
        elif op == "Q":
            out.append("p.quadTo(" + ", ".join(fmt(v, precision) for v in s[1:]) + ");")
        elif op == "C":
            out.append("p.cubicTo(" + ", ".join(fmt(v, precision) for v in s[1:]) + ");")
        elif op == "Z":
            out.append("p.close();")
    return out


MAX_OPS = 300


def emit_java(parts: list[Part], class_name: str, package: str, source: str,
              regen: str, width: float, height: float, precision: int) -> str:
    names = []
    used = set()
    for p in parts:
        n = method_name(p.name)
        base, i = n, 2
        while n in used:
            n = f"{base}{i}"
            i += 1
        used.add(n)
        names.append(n)

    # Per-part pivot: top-centre of the part's own ink box, in fitted units.
    # Legs pivot at the hip, arms at the shoulder, wings at the root -- exactly
    # what a name-driven animation wants to rotate around.
    pivots = []
    for p in parts:
        if not p.segments:
            pivots.append((0.0, 0.0))
            continue
        x0, _y0, x1, _y1 = svgpath.bbox_of_segments(p.segments)
        if x1 < x0:
            pivots.append((0.0, 0.0))
            continue
        pivots.append(((x0 + x1) / 2.0, _y0))

    lines = [
        "// GENERATED FILE - DO NOT EDIT BY HAND.",
        f"// Source:      {source}",
        "// Generator:   tools/artgen/svg2java.py",
        f"// Regenerate:  {regen}",
        "//",
        "// Segments are baked to absolute M/L/Q/C/Z in game units (y down);",
        "// `--fit` moved the artwork's anchor to the origin, so drawing needs",
        "// no extra scaling or offset.",
        f"package {package};",
        "",
        "import android.graphics.Canvas;",
        "import android.graphics.Paint;",
        "import android.graphics.Path;",
        "",
        f"public final class {class_name} {{",
        "",
        "    /** Artwork extents in game units (informational, not the entity size). */",
        f"    public static final float WIDTH = {fmt(width, precision)};",
        f"    public static final float HEIGHT = {fmt(height, precision)};",
        "",
        "    /** One filled/stroked sub-path. {@code color == 0} means \"no fill\". */",
        "    public static final class Part {",
        "        public final String name;",
        "        public final int color;",
        "        public final int strokeColor;",
        "        public final float strokeWidth;",
        "        public final Path path;",
        "        /** Top-centre of the part's ink box in art units: joint pivot. */",
        "        public final float pivotX;",
        "        public final float pivotY;",
        "",
        "        Part(String name, int color, int strokeColor, float strokeWidth,",
        "             Path path, float pivotX, float pivotY) {",
        "            this.name = name;",
        "            this.color = color;",
        "            this.strokeColor = strokeColor;",
        "            this.strokeWidth = strokeWidth;",
        "            this.path = path;",
        "            this.pivotX = pivotX;",
        "            this.pivotY = pivotY;",
        "        }",
        "    }",
        "",
    ]

    if parts:
        lines.append("    private static final Part[] P = {")
        for part, name, piv in zip(parts, names, pivots):
            lines.append(
                f'        new Part({json.dumps(part.name)}, {hex_argb(part.color)}, '
                f'{hex_argb(part.stroke_color)}, {fmt(part.stroke_width, precision)}, '
                f"{name}(), {fmt(piv[0], precision)}, {fmt(piv[1], precision)}),")
        lines.append("    };")
    else:
        lines.append("    private static final Part[] P = {};")
    lines += [
        "",
        f"    private {class_name}() {{ }}",
        "",
        "    /** Number of sub-paths, in draw order. */",
        "    public static int count() {",
        "        return P.length;",
        "    }",
        "",
        "    public static Part part(int i) {",
        "        return P[i];",
        "    }",
        "",
        "    /** The layer's SVG id (lowercased), for name-driven animation. */",
        "    public static String partName(int i) {",
        "        return P[i].name;",
        "    }",
        "",
        "    /** Top-centre of part i's ink box in art units: its joint pivot. */",
        "    public static float partPivotX(int i) {",
        "        return P[i].pivotX;",
        "    }",
        "",
        "    /** @see #partPivotX */",
        "    public static float partPivotY(int i) {",
        "        return P[i].pivotY;",
        "    }",
        "",
        "    /**",
        "     * Draws a single part with the anchor at (x, y), scaled about that",
        "     * point. Renderer animates limbs by rotating the canvas about the",
        "     * part's pivot and calling this with x = y = 0.",
        "     */",
        "    public static void drawPart(int i, Canvas c, Paint fill, Paint stroke,",
        "                               float x, float y, float scale) {",
        "        Part p = P[i];",
        "        c.save();",
        "        c.translate(x, y);",
        "        if (scale != 1f) {",
        "            c.scale(scale, scale);",
        "        }",
        "        if (p.color != 0) {",
        "            fill.setStyle(Paint.Style.FILL);",
        "            fill.setColor(p.color);",
        "            c.drawPath(p.path, fill);",
        "        }",
        "        if (p.strokeColor != 0 && stroke != null) {",
        "            stroke.setStyle(Paint.Style.STROKE);",
        "            stroke.setColor(p.strokeColor);",
        "            stroke.setStrokeWidth(p.strokeWidth);",
        "            c.drawPath(p.path, stroke);",
        "        }",
        "        c.restore();",
        "    }",
        "",
        "    /**",
        "     * Draws the artwork with its anchor at (x, y), scaled about that point.",
        "     * Mutates both paints (style, colour, stroke width), exactly like the",
        "     * hand-written drawing code it replaces.",
        "     */",
        "    public static void draw(Canvas c, Paint fill, Paint stroke,",
        "                           float x, float y, float scale) {",
        "        if (P.length == 0) {",
        "            return;",
        "        }",
        "        c.save();",
        "        c.translate(x, y);",
        "        if (scale != 1f) {",
        "            c.scale(scale, scale);",
        "        }",
        "        for (int i = 0; i < P.length; i++) {",
        "            Part p = P[i];",
        "            if (p.color != 0) {",
        "                fill.setStyle(Paint.Style.FILL);",
        "                fill.setColor(p.color);",
        "                c.drawPath(p.path, fill);",
        "            }",
        "            if (p.strokeColor != 0 && stroke != null) {",
        "                stroke.setStyle(Paint.Style.STROKE);",
        "                stroke.setColor(p.strokeColor);",
        "                stroke.setStrokeWidth(p.strokeWidth);",
        "                c.drawPath(p.path, stroke);",
        "            }",
        "        }",
        "        c.restore();",
        "    }",
        "",
    ]

    for part, name in zip(parts, names):
        body = _statements(part, precision)
        even_odd = "        p.setFillType(Path.FillType.EVEN_ODD);\n" if part.even_odd else ""
        if len(body) <= MAX_OPS:
            lines.append(f"    private static Path {name}() {{")
            lines.append("        Path p = new Path();")
            if even_odd:
                lines.append(even_odd.rstrip("\n"))
            lines += ["        " + s for s in body]
            lines.append("        return p;")
            lines.append("    }")
            lines.append("")
            continue
        # A single Inkscape node-heavy trace can exceed the JVM's 64 KB
        # per-method bytecode limit, so long paths are split across helpers.
        chunks = [body[i:i + MAX_OPS] for i in range(0, len(body), MAX_OPS)]
        lines.append(f"    private static Path {name}() {{")
        lines.append("        Path p = new Path();")
        if even_odd:
            lines.append(even_odd.rstrip("\n"))
        for i in range(len(chunks)):
            lines.append(f"        {name}_{i}(p);")
        lines.append("        return p;")
        lines.append("    }")
        for i, chunk in enumerate(chunks):
            lines.append("")
            lines.append(f"    private static void {name}_{i}(Path p) {{")
            lines += ["        " + s for s in chunk]
            lines.append("    }")
        lines.append("")

    lines.append("}")
    return "\n".join(lines) + "\n"


# ----------------------------------------------------------------------
# conversion driver
# ----------------------------------------------------------------------

def convert(svg_path: Path, class_name: str, package: str, out_path: Path | None,
            fit, scale, anchor: str, precision: int, write: bool,
            regen_cmd: str, source_label: str | None = None) -> Result:
    warnings: list[str] = []
    try:
        tree = ET.parse(svg_path)
    except ET.ParseError as exc:
        raise SvgError(f"{svg_path}: XML parse error: {exc}") from None
    except OSError as exc:
        raise SvgError(f"{svg_path}: {exc.strerror or exc}") from None

    root = tree.getroot()
    if local_name(root.tag) != "svg":
        raise SvgError(f"{svg_path}: root element is <{local_name(root.tag)}>, not <svg>")

    grads = collect_gradients(root, warnings)
    parts = collect_parts(root, warnings, grads)
    width, height, scale_used = normalise(parts, fit, scale, anchor)

    java = emit_java(parts, class_name, package,
                     source_label or rel(svg_path), regen_cmd,
                     width, height, precision)
    if write and out_path is not None:
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(java, encoding="utf-8")

    segments = sum(len(p.segments) for p in parts)
    summary = {
        "src": rel(svg_path),
        "class": class_name,
        "package": package,
        "out": rel(out_path) if out_path is not None else None,
        "parts": len(parts),
        "segments": segments,
        "width": round(width, 4),
        "height": round(height, 4),
        "scale": round(scale_used, 6),
        "bytes": len(java.encode("utf-8")),
        "written": bool(write and out_path is not None),
        "warnings": warnings,
        "partNames": [p.name for p in parts],
    }
    return Result(ok=True, summary=summary, warnings=warnings, java=java,
                  parts=parts)


def pascal(stem: str) -> str:
    bits = re.split(r"[^0-9A-Za-z]+", stem)
    return "".join(b[:1].upper() + b[1:] for b in bits if b)


def default_class(svg_path: Path) -> str:
    return "Art" + pascal(svg_path.stem)


def package_dir(package: str) -> Path:
    base = PROJECT_ROOT / "app" / "src" / "main" / "java"
    return base.joinpath(*package.split("."))


def default_out(package: str, class_name: str) -> Path:
    return package_dir(package) / f"{class_name}.java"


def shell_quote(p: str) -> str:
    return f'"{p}"' if " " in p else p


def rel(path: Path) -> str:
    try:
        return str(path.resolve().relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


def build_regen(args, svg_path: Path, class_name: str, package: str,
                out_path: Path, fit, anchor: str, precision: int) -> str:
    bits = ["python3", "tools/artgen/svg2java.py", rel(svg_path),
            "--class", class_name]
    if package != DEFAULT_PACKAGE:
        bits += ["--package", package]
    if fit:
        bits += ["--fit", f"{fit[0]:g}x{fit[1]:g}"]
    if args.scale:
        bits += ["--scale", f"{args.scale:g}"]
    if anchor != "bottom-center":
        bits += ["--anchor", anchor]
    if precision != 2:
        bits += ["--precision", str(precision)]
    expected = default_out(package, class_name)
    if out_path.resolve() != expected.resolve():
        bits += ["--out", rel(out_path)]
    return " ".join(shell_quote(b) for b in bits)


def run_one(args, svg_path: Path, class_name: str, package: str,
            out_path: Path, fit, anchor: str, precision: int,
            reporter) -> Result:
    regen = build_regen(args, svg_path, class_name, package, out_path,
                        fit, anchor, precision)
    result = convert(
        svg_path=svg_path,
        class_name=class_name,
        package=package,
        out_path=out_path,
        fit=fit,
        scale=args.scale,
        anchor=anchor,
        precision=precision,
        write=not args.check,
        regen_cmd=regen,
    )
    s = result.summary
    reporter.info(
        f"{rel(svg_path)} -> {rel(out_path) if out_path else '(stdout)'}  "
        f"{s['parts']} part(s), {s['segments']} segment(s), "
        f"{s['width']:.2f}x{s['height']:.2f} units, {s['bytes']} bytes")
    for w in result.warnings:
        reporter.warn(w)
    return result


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(
        prog="svg2java.py",
        description="Bake SVG artwork into Java Path code for Anplomer.")
    parser.add_argument("svg", nargs="?", help="source .svg (or use --manifest)")
    parser.add_argument("--manifest", help="JSON batch descriptor (see art/art.json)")
    parser.add_argument("--class", dest="class_name",
                        help="Java class name (default: Art<FileStem>)")
    parser.add_argument("--package", default=DEFAULT_PACKAGE,
                        help=f"Java package (default: {DEFAULT_PACKAGE})")
    parser.add_argument("--out", help="output .java path")
    parser.add_argument("--out-dir", help="directory for generated .java files")
    parser.add_argument("--fit", type=parse_fit, metavar="WxH",
                        help="scale ink to fit WxH and anchor it at the origin")
    parser.add_argument("--scale", type=float, help="uniform scale (no re-anchor)")
    parser.add_argument("--anchor", default=None, choices=sorted(ANCHORS),
                        help="where the origin lands with --fit (default: bottom-center)")
    parser.add_argument("--precision", type=int, default=2,
                        help="decimal places in generated floats (default: 2)")
    parser.add_argument("--check", action="store_true",
                        help="parse and report, write nothing")
    parser.add_argument("--stdout", action="store_true", help="write Java to stdout")
    parser.add_argument("--json", action="store_true",
                        help="machine-readable summary on stdout")
    parser.add_argument("--strict", action="store_true",
                        help="exit 4 if any warning was reported")
    args = parser.parse_args(argv)

    if args.precision < 0 or args.precision > 6:
        parser.error("--precision must be 0..6")
    if bool(args.svg) == bool(args.manifest):
        parser.error("give exactly one of SVG or --manifest")
    if args.stdout and args.json:
        parser.error("--stdout and --json are mutually exclusive")

    reporter = Reporter(quiet=args.json or args.stdout)
    anchor = args.anchor or "bottom-center"
    if args.anchor and args.fit is None and not args.manifest:
        reporter.warn("--anchor only applies with --fit; ignoring it")

    try:
        if args.manifest:
            return run_manifest(args, reporter)

        svg_path = Path(args.svg)
        class_name = args.class_name or default_class(svg_path)
        if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", class_name):
            parser.error(f"invalid Java class name: {class_name!r}")
        if args.stdout:
            result = convert(svg_path, class_name, args.package, None,
                             args.fit, args.scale, anchor, args.precision,
                             write=False,
                             regen_cmd=build_regen(
                                 args, svg_path, class_name, args.package,
                                 default_out(args.package, class_name),
                                 args.fit, anchor, args.precision))
            for w in result.warnings:
                reporter.warn(w)
            sys.stdout.write(result.java or "")
            return 4 if (args.strict and result.warnings) else 0

        if args.out:
            out_path = Path(args.out)
        elif args.out_dir:
            out_path = Path(args.out_dir) / f"{class_name}.java"
        else:
            out_path = default_out(args.package, class_name)
        result = run_one(args, svg_path, class_name, args.package, out_path,
                         args.fit, anchor, args.precision, reporter)
        if args.json:
            print(json.dumps({"ok": True, "assets": [result.summary]}, indent=2))
        return 4 if (args.strict and result.warnings) else 0

    except SvgError as exc:
        if args.json:
            print(json.dumps({"ok": False, "error": str(exc)}, indent=2))
        else:
            print(f"error: {exc}", file=sys.stderr)
        return 3
    except OSError as exc:
        msg = f"{exc.filename or ''}: {exc.strerror or exc}"
        if args.json:
            print(json.dumps({"ok": False, "error": msg}, indent=2))
        else:
            print(f"error: {msg}", file=sys.stderr)
        return 3


def emit_bindings(package: str, out_dir, bound, manifest_path: Path,
                  reporter) -> dict:
    """Writes ArtBindings.java, the entity -> artwork registry Renderer uses.

    The game never reads art.json; this generated class is the one place that
    knows which entity has baked artwork and which generated class draws it.
    An empty manifest still emits the class (every method a no-op), so the
    game always compiles against a registry.
    """
    target_dir = out_dir or package_dir(package)
    target = target_dir / "ArtBindings.java"
    regen = f"python3 tools/artgen/svg2java.py --manifest {rel(manifest_path)}"
    lines = [
        "// GENERATED FILE - DO NOT EDIT BY HAND.",
        f"// Source:      {rel(manifest_path)}",
        "// Generator:   tools/artgen/svg2java.py",
        f"// Regenerate:  {regen}",
        "//",
        "// Entity -> baked-artwork registry. Renderer asks has()/draw() whether",
        "// an entity has generated art and falls back to its hand-drawn vector",
        "// code when it does not.",
        f"package {package};",
        "",
        "import android.graphics.Canvas;",
        "import android.graphics.Paint;",
        "",
        "/** Registry of entities that have baked artwork (from the art manifest). */",
        "public final class ArtBindings {",
        "",
        "    private ArtBindings() {",
        "    }",
        "",
        "    /** @return true when {@code entity} has artwork the renderer can draw */",
        "    public static boolean has(String entity) {",
        "        switch (entity) {",
    ]
    for entity, _cls in bound:
        lines.append(f"            case {json.dumps(entity)}:")
        lines.append("                return true;")
    lines += [
        "            default:",
        "                return false;",
        "        }",
        "    }",
        "",
        "    /** Fitted ink-box width of the bound artwork; 0 when there is none. */",
        "    public static float width(String entity) {",
        "        switch (entity) {",
    ]
    for entity, cls in bound:
        lines.append(f"            case {json.dumps(entity)}:")
        lines.append(f"                return {cls}.WIDTH;")
    lines += [
        "            default:",
        "                return 0f;",
        "        }",
        "    }",
        "",
        "    /** Fitted ink-box height of the bound artwork; 0 when there is none. */",
        "    public static float height(String entity) {",
        "        switch (entity) {",
    ]
    for entity, cls in bound:
        lines.append(f"            case {json.dumps(entity)}:")
        lines.append(f"                return {cls}.HEIGHT;")
    lines += [
        "            default:",
        "                return 0f;",
        "        }",
        "    }",
        "",
        "    /**",
        "     * Draws the bound artwork with its manifest anchor at (x, y),",
        "     * scaled about that point. No-op when the entity has no artwork.",
        "     */",
        "    public static void draw(String entity, Canvas c, Paint fill,",
        "                            Paint stroke, float x, float y, float scale) {",
        "        switch (entity) {",
    ]
    for entity, cls in bound:
        lines.append(f"            case {json.dumps(entity)}:")
        lines.append(f"                {cls}.draw(c, fill, stroke, x, y, scale);")
        lines.append("                return;")
    lines += [
        "            default:",
        "        }",
        "    }",
        "",
        "    /** Part count of the bound artwork; 0 when the entity has none. */",
        "    public static int partCount(String entity) {",
        "        switch (entity) {",
    ]
    for entity, cls in bound:
        lines.append(f"            case {json.dumps(entity)}:")
        lines.append(f"                return {cls}.count();")
    lines += [
        "            default:",
        "                return 0;",
        "        }",
        "    }",
        "",
        "    /** Layer name of part i (drives the name-based animation). */",
        "    public static String partName(String entity, int i) {",
        "        switch (entity) {",
    ]
    for entity, cls in bound:
        lines.append(f"            case {json.dumps(entity)}:")
        lines.append(f"                return {cls}.partName(i);")
    lines += [
        "            default:",
        "                return \"\";",
        "        }",
        "    }",
        "",
        "    /** Part pivot (joint) in art units; 0 when the entity has none. */",
        "    public static float partPivotX(String entity, int i) {",
        "        switch (entity) {",
    ]
    for entity, cls in bound:
        lines.append(f"            case {json.dumps(entity)}:")
        lines.append(f"                return {cls}.partPivotX(i);")
    lines += [
        "            default:",
        "                return 0f;",
        "        }",
        "    }",
        "",
        "    /** @see #partPivotX */",
        "    public static float partPivotY(String entity, int i) {",
        "        switch (entity) {",
    ]
    for entity, cls in bound:
        lines.append(f"            case {json.dumps(entity)}:")
        lines.append(f"                return {cls}.partPivotY(i);")
    lines += [
        "            default:",
        "                return 0f;",
        "        }",
        "    }",
        "",
        "    /**",
        "     * Draws one part of the bound artwork. Renderer rotates limbs by",
        "     * transforming the canvas about the part's pivot, then calls this.",
        "     */",
        "    public static void drawPart(String entity, int i, Canvas c,",
        "                               Paint fill, Paint stroke,",
        "                               float x, float y, float scale) {",
        "        switch (entity) {",
    ]
    for entity, cls in bound:
        lines.append(f"            case {json.dumps(entity)}:")
        lines.append(f"                {cls}.drawPart(i, c, fill, stroke, x, y, scale);")
        lines.append("                return;")
    lines += [
        "            default:",
        "        }",
        "    }",
        "}",
        "",
    ]
    text = "\n".join(lines)
    target_dir.mkdir(parents=True, exist_ok=True)
    changed = True
    if target.exists():
        changed = target.read_text(encoding="utf-8") != text
    if changed:
        tmp = target.with_suffix(".java.tmp")
        tmp.write_text(text, encoding="utf-8")
        os.replace(tmp, target)
    reporter.info(f"{rel(manifest_path)} -> {rel(target)}  "
                  f"ArtBindings: {len(bound)} entit"
                  f"{'y' if len(bound) == 1 else 'ies'} bound"
                  + ("" if changed else "  (unchanged)"))
    return {"out": rel(target), "entities": [e for e, _ in bound],
            "written": changed}

def run_manifest(args, reporter) -> int:
    manifest_path = Path(args.manifest)
    try:
        data = json.loads(manifest_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise SvgError(f"{manifest_path}: bad JSON: {exc}") from None

    base_package = data.get("package", args.package)
    out_dir = data.get("outDir") or args.out_dir
    if out_dir:
        out_dir = Path(out_dir)
        if not out_dir.is_absolute():
            out_dir = PROJECT_ROOT / out_dir
    else:
        out_dir = package_dir(base_package)

    assets = data.get("assets", [])
    if not isinstance(assets, list):
        raise SvgError(f"{manifest_path}: 'assets' must be a list")

    results = []
    failed = False
    bound: list[tuple[str, str]] = []
    seen_entities: set[str] = set()
    for i, asset in enumerate(assets):
        try:
            src = Path(asset["src"])
            if not src.is_absolute():
                src = PROJECT_ROOT / src
            package = asset.get("package", base_package)
            class_name = asset.get("class") or default_class(src)
            entity = asset.get("entity")
            if isinstance(entity, str) and entity.strip():
                entity = entity.strip()
                if entity in seen_entities:
                    reporter.warn(
                        f"asset {i}: entity {entity!r} already bound; keeping "
                        f"the first binding")
                else:
                    seen_entities.add(entity)
                    bound.append((entity, class_name))
            fit = parse_fit(asset["fit"]) if asset.get("fit") else None
            anchor = asset.get("anchor", "bottom-center")
            if anchor not in ANCHORS:
                raise SvgError(f"unknown anchor {anchor!r}")
            precision = int(asset.get("precision", args.precision))
            out_path = Path(asset["out"]) if asset.get("out") else out_dir / f"{class_name}.java"
            if not out_path.is_absolute():
                out_path = PROJECT_ROOT / out_path
            result = run_one(args, src, class_name, package, out_path,
                             fit, anchor, precision, reporter)
            results.append(result.summary)
        except (SvgError, KeyError, ValueError, argparse.ArgumentTypeError) as exc:
            failed = True
            reporter.error(f"asset {i}: {exc}")

    warnings = sum(len(r["warnings"]) for r in results)
    if not failed and not args.check:
        bindings_out = emit_bindings(base_package, out_dir, bound,
                                     Path(args.manifest), reporter)
    else:
        bindings_out = None
    if args.json:
        payload = {"ok": not failed, "assets": results}
        if bindings_out is not None:
            payload["bindings"] = bindings_out
        print(json.dumps(payload, indent=2))
    if failed:
        return 3
    return 4 if (args.strict and warnings) else 0


class Reporter:
    """Human output goes to stdout normally, stderr in machine-readable modes."""

    def __init__(self, quiet: bool):
        self.quiet = quiet
        self._stream = sys.stderr if quiet else sys.stdout

    def info(self, msg: str) -> None:
        if not self.quiet:
            print(msg, file=self._stream)

    def warn(self, msg: str) -> None:
        print(f"warning: {msg}", file=sys.stderr)

    def error(self, msg: str) -> None:
        print(f"error: {msg}", file=sys.stderr)


if __name__ == "__main__":
    sys.exit(main())
