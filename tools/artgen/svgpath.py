#!/usr/bin/env python3
"""SVG geometry -> absolute path segments, with no third-party dependencies.

This is the reusable half of the art pipeline: `svg2java.py` is the CLI, but a
GUI designer will want to call these functions directly (preview an SVG, read
its bounding box, report unsupported features) without shelling out.

Design constraints that come from the game side:

* Android's `android.graphics.Path` and the headless stub in `tools/stub` only
  agree on `moveTo`, `lineTo`, `quadTo`, `cubicTo`, `close`, `reset`, `set` and
  `setFillType`. So every command is normalised down to **M / L / Q / C / Z**:
  H/V become L, S/T become C/Q, and elliptical arcs are flattened to cubics.
* All transforms (element and ancestor) are **baked into the coordinates**.
  The generated Java then costs nothing at runtime beyond `drawPath`.
* Coordinates keep SVG's y-down convention, which is also Canvas's, so no
  flip is needed anywhere.

Segment tuples are `('M', x, y)`, `('L', x, y)`, `('Q', x1, y1, x, y)`,
`('C', x1, y1, x2, y2, x, y)` or `('Z',)`.
"""
from __future__ import annotations

import math
import re
from dataclasses import dataclass


class SvgError(ValueError):
    """Raised for malformed SVG or path data (message is user-facing)."""


# ----------------------------------------------------------------------
# affine transforms
# ----------------------------------------------------------------------

@dataclass(frozen=True)
class Matrix:
    """A 2D affine transform: x' = a*x + c*y + e, y' = b*x + d*y + f."""

    a: float = 1.0
    b: float = 0.0
    c: float = 0.0
    d: float = 1.0
    e: float = 0.0
    f: float = 0.0

    def __matmul__(self, o: "Matrix") -> "Matrix":
        """`self @ o` applies `o` first, then `self` (SVG transform order)."""
        return Matrix(
            self.a * o.a + self.c * o.b,
            self.b * o.a + self.d * o.b,
            self.a * o.c + self.c * o.d,
            self.b * o.c + self.d * o.d,
            self.a * o.e + self.c * o.f + self.e,
            self.b * o.e + self.d * o.f + self.f,
        )

    @property
    def is_identity(self) -> bool:
        return self == IDENTITY

    def apply(self, x: float, y: float) -> tuple[float, float]:
        return (self.a * x + self.c * y + self.e,
                self.b * x + self.d * y + self.f)

    def apply_pairs(self, v: tuple[float, ...]) -> tuple[float, ...]:
        out: list[float] = []
        for i in range(0, len(v), 2):
            x, y = self.apply(v[i], v[i + 1])
            out.append(x)
            out.append(y)
        return tuple(out)

    def max_scale(self) -> float:
        """Largest singular value — used to scale baked stroke widths."""
        # sqrt of the largest eigenvalue of M^T M.
        m00 = self.a * self.a + self.b * self.b
        m01 = self.a * self.c + self.b * self.d
        m11 = self.c * self.c + self.d * self.d
        tr = m00 + m11
        det = m00 * m11 - m01 * m01
        disc = max(0.0, tr * tr / 4.0 - det)
        return math.sqrt(max(0.0, tr / 2.0 + math.sqrt(disc)))


IDENTITY = Matrix()

_NUM = r"[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?"
_FUNC_RE = re.compile(r"([A-Za-z]+)\s*\(([^)]*)\)")
_NUM_RE = re.compile(_NUM)


def _numbers(text: str) -> list[float]:
    return [float(m.group(0)) for m in _NUM_RE.finditer(text)]


def parse_transform(text: str | None) -> Matrix:
    """Parses an SVG `transform` attribute; unknown functions are rejected."""
    if not text or not text.strip():
        return IDENTITY
    m = IDENTITY
    found = False
    for name, args in _FUNC_RE.findall(text):
        v = _numbers(args)
        found = True
        fn = name.lower()
        if fn == "translate":
            tx = v[0] if v else 0.0
            ty = v[1] if len(v) > 1 else 0.0
            m = m @ Matrix(e=tx, f=ty)
        elif fn == "scale":
            sx = v[0] if v else 1.0
            sy = v[1] if len(v) > 1 else sx
            m = m @ Matrix(a=sx, d=sy)
        elif fn == "rotate":
            deg = v[0] if v else 0.0
            rad = math.radians(deg)
            cs, sn = math.cos(rad), math.sin(rad)
            r = Matrix(cs, sn, -sn, cs, 0.0, 0.0)
            if len(v) >= 3:
                cx, cy = v[1], v[2]
                r = Matrix(e=cx, f=cy) @ r @ Matrix(e=-cx, f=-cy)
            m = m @ r
        elif fn == "matrix" and len(v) >= 6:
            m = m @ Matrix(*v[:6])
        elif fn == "skewx":
            m = m @ Matrix(c=math.tan(math.radians(v[0] if v else 0.0)))
        elif fn == "skewy":
            m = m @ Matrix(b=math.tan(math.radians(v[0] if v else 0.0)))
        else:
            raise SvgError(f"unsupported transform function: {name}()")
    if not found and text.strip():
        raise SvgError(f"cannot parse transform: {text!r}")
    return m


def transform_segments(segments, m: Matrix):
    """Bakes `m` into every coordinate pair of `segments`."""
    if m.is_identity:
        return list(segments)
    out = []
    for s in segments:
        if s[0] == "Z":
            out.append(s)
        else:
            out.append((s[0],) + m.apply_pairs(s[1:]))
    return out


# ----------------------------------------------------------------------
# path data
# ----------------------------------------------------------------------

_TOKEN_RE = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]|" + _NUM)

_ARG_COUNT = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6,
              "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}


def _tokenize_path(d: str) -> list[str]:
    tokens: list[str] = []
    pos = 0
    n = len(d)
    while pos < n:
        ch = d[pos]
        if ch in " \t\r\n,":
            pos += 1
            continue
        m = _TOKEN_RE.match(d, pos)
        if not m:
            raise SvgError(f"bad character {ch!r} in path data at offset {pos}")
        tokens.append(m.group(0))
        pos = m.end()
    return tokens


def parse_path_data(d: str | None) -> list[tuple]:
    """Parses an SVG `d` attribute into absolute M/L/Q/C/Z segments.

    Implicit repetitions are supported (`M x y x y` means `M` then `L`), as
    are relative commands, smooth curves (S/T) and elliptical arcs (A).
    """
    if not d or not d.strip():
        return []
    tokens = _tokenize_path(d)
    segs: list[tuple] = []
    i = 0
    cmd: str | None = None
    cur = (0.0, 0.0)
    start = (0.0, 0.0)
    prev_cmd: str | None = None
    prev_ctrl: tuple[float, float] | None = None

    def take(n: int) -> list[float]:
        nonlocal i
        if i + n > len(tokens):
            raise SvgError(f"path command {cmd!r} needs {n} arguments")
        vals = []
        for _ in range(n):
            tok = tokens[i]
            if tok.isalpha():
                raise SvgError(f"path command {cmd!r} is missing arguments")
            vals.append(float(tok))
            i += 1
        return vals

    while i < len(tokens):
        tok = tokens[i]
        if tok.isalpha():
            cmd = tok
            i += 1
        elif cmd is None:
            raise SvgError("path data must start with a command")
        else:
            # Implicit repetition: after M/m the next pair means L/l.
            cmd = {"M": "L", "m": "l"}.get(cmd, cmd)

        assert cmd is not None
        upper = cmd.upper()
        rel = cmd.islower()

        if upper == "Z":
            segs.append(("Z",))
            cur = start
            prev_cmd = "Z"
            prev_ctrl = None
            cmd = None          # a number after Z would be malformed
            continue

        args = take(_ARG_COUNT[upper])

        if upper == "M":
            x, y = args
            if rel:
                x += cur[0]
                y += cur[1]
            segs.append(("M", x, y))
            cur = (x, y)
            start = cur
            prev_ctrl = None
        elif upper == "L":
            x, y = args
            if rel:
                x += cur[0]
                y += cur[1]
            segs.append(("L", x, y))
            cur = (x, y)
            prev_ctrl = None
        elif upper == "H":
            x = args[0] + (cur[0] if rel else 0.0)
            segs.append(("L", x, cur[1]))
            cur = (x, cur[1])
            prev_ctrl = None
        elif upper == "V":
            y = args[0] + (cur[1] if rel else 0.0)
            segs.append(("L", cur[0], y))
            cur = (cur[0], y)
            prev_ctrl = None
        elif upper == "C":
            x1, y1, x2, y2, x, y = args
            if rel:
                x1 += cur[0]; y1 += cur[1]
                x2 += cur[0]; y2 += cur[1]
                x += cur[0]; y += cur[1]
            segs.append(("C", x1, y1, x2, y2, x, y))
            prev_ctrl = (x2, y2)
            cur = (x, y)
        elif upper == "S":
            x2, y2, x, y = args
            if rel:
                x2 += cur[0]; y2 += cur[1]
                x += cur[0]; y += cur[1]
            if prev_cmd in ("C", "S") and prev_ctrl is not None:
                x1 = 2.0 * cur[0] - prev_ctrl[0]
                y1 = 2.0 * cur[1] - prev_ctrl[1]
            else:
                x1, y1 = cur
            segs.append(("C", x1, y1, x2, y2, x, y))
            prev_ctrl = (x2, y2)
            cur = (x, y)
        elif upper == "Q":
            x1, y1, x, y = args
            if rel:
                x1 += cur[0]; y1 += cur[1]
                x += cur[0]; y += cur[1]
            segs.append(("Q", x1, y1, x, y))
            prev_ctrl = (x1, y1)
            cur = (x, y)
        elif upper == "T":
            x, y = args
            if rel:
                x += cur[0]
                y += cur[1]
            if prev_cmd in ("Q", "T") and prev_ctrl is not None:
                x1 = 2.0 * cur[0] - prev_ctrl[0]
                y1 = 2.0 * cur[1] - prev_ctrl[1]
            else:
                x1, y1 = cur
            segs.append(("Q", x1, y1, x, y))
            prev_ctrl = (x1, y1)
            cur = (x, y)
        elif upper == "A":
            rx, ry, rot, large, sweep, x, y = args
            if rel:
                x += cur[0]
                y += cur[1]
            segs.extend(arc_to_cubics(cur[0], cur[1], rx, ry, rot,
                                      large != 0.0, sweep != 0.0, x, y))
            cur = (x, y)
            prev_ctrl = None

        prev_cmd = upper

    return segs


def arc_to_cubics(x1: float, y1: float, rx: float, ry: float, rotation: float,
                  large_arc: bool, sweep: bool,
                  x2: float, y2: float) -> list[tuple]:
    """Endpoint-parameterised SVG arc -> one or more cubic Béziers.

    Follows SVG 1.1 appendix F.6.5, then splits the sweep into <=90 degree
    pieces (the standard cubic approximation, error < 1e-4 of the radius).
    """
    if x1 == x2 and y1 == y2:
        return []
    rx, ry = abs(rx), abs(ry)
    if rx == 0.0 or ry == 0.0:
        return [("L", x2, y2)]

    phi = math.radians(rotation)
    cphi, sphi = math.cos(phi), math.sin(phi)

    dx2 = (x1 - x2) / 2.0
    dy2 = (y1 - y2) / 2.0
    x1p = cphi * dx2 + sphi * dy2
    y1p = -sphi * dx2 + cphi * dy2

    lam = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    if lam > 1.0:
        s = math.sqrt(lam)
        rx *= s
        ry *= s

    num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    coef = math.sqrt(max(0.0, num / den)) if den else 0.0
    if large_arc == sweep:
        coef = -coef

    cxp = coef * rx * y1p / ry
    cyp = -coef * ry * x1p / rx
    cx = cphi * cxp - sphi * cyp + (x1 + x2) / 2.0
    cy = sphi * cxp + cphi * cyp + (y1 + y2) / 2.0

    ux = (x1p - cxp) / rx
    uy = (y1p - cyp) / ry
    vx = (-x1p - cxp) / rx
    vy = (-y1p - cyp) / ry

    theta1 = math.atan2(uy, ux)
    dtheta = math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
    if not sweep and dtheta > 0.0:
        dtheta -= 2.0 * math.pi
    elif sweep and dtheta < 0.0:
        dtheta += 2.0 * math.pi

    n = max(1, int(math.ceil(abs(dtheta) / (math.pi / 2.0))))
    delta = dtheta / n
    t = 4.0 / 3.0 * math.tan(delta / 4.0)

    def point(a: float) -> tuple[float, float]:
        ca, sa = math.cos(a), math.sin(a)
        return (cx + rx * ca * cphi - ry * sa * sphi,
                cy + rx * ca * sphi + ry * sa * cphi)

    def deriv(a: float) -> tuple[float, float]:
        ca, sa = math.cos(a), math.sin(a)
        return (-rx * sa * cphi - ry * ca * sphi,
                -rx * sa * sphi + ry * ca * cphi)

    out: list[tuple] = []
    for k in range(n):
        a1 = theta1 + k * delta
        a2 = a1 + delta
        p1 = point(a1)
        p2 = point(a2)
        d1 = deriv(a1)
        d2 = deriv(a2)
        out.append(("C",
                    p1[0] + t * d1[0], p1[1] + t * d1[1],
                    p2[0] - t * d2[0], p2[1] - t * d2[1],
                    p2[0], p2[1]))
    # Snap the final endpoint to the requested one so repeated arcs don't drift.
    last = out[-1]
    out[-1] = (last[0], last[1], last[2], last[3], last[4], x2, y2)
    return out


# ----------------------------------------------------------------------
# primitive shapes
# ----------------------------------------------------------------------

def _f(attrs: dict, name: str, default: float = 0.0) -> float:
    raw = attrs.get(name)
    if raw is None or raw == "":
        return default
    try:
        return float(str(raw).strip())
    except ValueError:
        raise SvgError(f"attribute {name}={raw!r} is not a number") from None


def _points(text: str | None) -> list[tuple[float, float]]:
    nums = _numbers(text or "")
    if len(nums) % 2:
        raise SvgError("point list has an odd number of coordinates")
    return [(nums[i], nums[i + 1]) for i in range(0, len(nums), 2)]


def shape_to_segments(tag: str, attrs: dict) -> list[tuple] | None:
    """Converts an SVG shape element to segments, or None if unsupported.

    Keeping this separate from `<path>` handling means Inkscape's "Plain SVG"
    output works even when the artist forgot Path > Object to Path.
    """
    if tag == "path":
        return parse_path_data(attrs.get("d"))

    if tag == "rect":
        x, y = _f(attrs, "x"), _f(attrs, "y")
        w, h = _f(attrs, "width"), _f(attrs, "height")
        if w <= 0 or h <= 0:
            return []
        rx = _f(attrs, "rx", -1.0)
        ry = _f(attrs, "ry", -1.0)
        if rx < 0 and ry < 0:
            rx = ry = 0.0
        elif rx < 0:
            rx = ry
        elif ry < 0:
            ry = rx
        rx = min(rx, w / 2.0)
        ry = min(ry, h / 2.0)
        if rx <= 0.0 or ry <= 0.0:
            return [("M", x, y), ("L", x + w, y),
                    ("L", x + w, y + h), ("L", x, y + h), ("Z",)]
        segs: list[tuple] = [("M", x + rx, y), ("L", x + w - rx, y)]
        segs += arc_to_cubics(x + w - rx, y, rx, ry, 0, False, True,
                              x + w, y + ry)
        segs += [("L", x + w, y + h - ry)]
        segs += arc_to_cubics(x + w, y + h - ry, rx, ry, 0, False, True,
                              x + w - rx, y + h)
        segs += [("L", x + rx, y + h)]
        segs += arc_to_cubics(x + rx, y + h, rx, ry, 0, False, True,
                              x, y + h - ry)
        segs += [("L", x, y + ry)]
        segs += arc_to_cubics(x, y + ry, rx, ry, 0, False, True,
                              x + rx, y)
        segs += [("Z",)]
        return segs

    if tag in ("circle", "ellipse"):
        cx, cy = _f(attrs, "cx"), _f(attrs, "cy")
        if tag == "circle":
            rx = ry = _f(attrs, "r")
        else:
            rx, ry = _f(attrs, "rx"), _f(attrs, "ry")
        if rx <= 0 or ry <= 0:
            return []
        return ([("M", cx + rx, cy)]
                + arc_to_cubics(cx + rx, cy, rx, ry, 0, False, True,
                                cx - rx, cy)
                + arc_to_cubics(cx - rx, cy, rx, ry, 0, False, True,
                                cx + rx, cy)
                + [("Z",)])

    if tag == "line":
        return [("M", _f(attrs, "x1"), _f(attrs, "y1")),
                ("L", _f(attrs, "x2"), _f(attrs, "y2"))]

    if tag in ("polyline", "polygon"):
        pts = _points(attrs.get("points"))
        if not pts:
            return []
        segs = [("M", pts[0][0], pts[0][1])]
        segs += [("L", p[0], p[1]) for p in pts[1:]]
        if tag == "polygon":
            segs.append(("Z",))
        return segs

    return None


# ----------------------------------------------------------------------
# geometry queries
# ----------------------------------------------------------------------

def _cubic_at(p0, p1, p2, p3, t: float) -> tuple[float, float]:
    mt = 1.0 - t
    a = mt * mt * mt
    b = 3.0 * mt * mt * t
    c = 3.0 * mt * t * t
    d = t * t * t
    return (a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0],
            a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1])


def bbox_of_segments(segments, samples: int = 12):
    """Approximate ink bounds as (minx, miny, maxx, maxy).

    Curves are sampled rather than solved analytically: the result is only used
    to fit art into a target size, where 1/12-of-a-span accuracy is far below
    what a human can see.
    """
    minx = miny = math.inf
    maxx = maxy = -math.inf
    cur = (0.0, 0.0)
    start = (0.0, 0.0)

    def add(x: float, y: float) -> None:
        nonlocal minx, miny, maxx, maxy
        if x < minx:
            minx = x
        if x > maxx:
            maxx = x
        if y < miny:
            miny = y
        if y > maxy:
            maxy = y

    for s in segments:
        op = s[0]
        if op == "M":
            cur = start = (s[1], s[2])
            add(*cur)
        elif op == "L":
            cur = (s[1], s[2])
            add(*cur)
        elif op == "Q":
            p0 = cur
            p1 = (s[1], s[2])
            p2 = (s[3], s[4])
            add(*p1)
            add(*p2)
            for i in range(1, samples):
                t = i / samples
                mt = 1.0 - t
                add(mt * mt * p0[0] + 2 * mt * t * p1[0] + t * t * p2[0],
                    mt * mt * p0[1] + 2 * mt * t * p1[1] + t * t * p2[1])
            cur = p2
        elif op == "C":
            p0 = cur
            p1 = (s[1], s[2])
            p2 = (s[3], s[4])
            p3 = (s[5], s[6])
            add(*p1)
            add(*p2)
            add(*p3)
            for i in range(1, samples):
                add(*_cubic_at(p0, p1, p2, p3, i / samples))
            cur = p3
        elif op == "Z":
            cur = start

    if minx == math.inf:
        return (0.0, 0.0, 0.0, 0.0)
    return (minx, miny, maxx, maxy)


# ----------------------------------------------------------------------
# colours
# ----------------------------------------------------------------------

# The full CSS colour keyword list, so any name Inkscape writes is understood.
_NAMED_RAW = """
aliceblue f0f8ff        antiquewhite faebd7      aqua 00ffff
aquamarine 7fffd4       azure f0ffff             beige f5f5dc
bisque ffe4c4           black 000000             blanchedalmond ffebcd
blue 0000ff             blueviolet 8a2be2        brown a52a2a
burlywood deb887        cadetblue 5f9ea0         chartreuse 7fff00
chocolate d2691e        coral ff7f50             cornflowerblue 6495ed
cornsilk fff8dc         crimson dc143c           cyan 00ffff
darkblue 00008b         darkcyan 008b8b          darkgoldenrod b8860b
darkgray a9a9a9         darkgreen 006400         darkgrey a9a9a9
darkkhaki bdb76b        darkmagenta 8b008b       darkolivegreen 556b2f
darkorange ff8c00       darkorchid 9932cc        darkred 8b0000
darksalmon e9967a       darkseagreen 8fbc8f      darkslateblue 483d8b
darkslategray 2f4f4f    darkslategrey 2f4f4f     darkturquoise 00ced1
darkviolet 9400d3       deeppink ff1493          deepskyblue 00bfff
dimgray 696969          dimgrey 696969           dodgerblue 1e90ff
firebrick b22222        floralwhite fffaf0        forestgreen 228b22
fuchsia ff00ff          gainsboro dcdcdc         ghostwhite f8f8ff
gold ffd700             goldenrod daa520         gray 808080
grey 808080             green 008000             greenyellow adff2f
honeydew f0fff0         hotpink ff69b4           indianred cd5c5c
indigo 4b0082           ivory fffff0             khaki f0e68c
lavender e6e6fa         lavenderblush fff0f5     lawngreen 7cfc00
lemonchiffon fffacd    lightblue add8e6         lightcoral f08080
lightcyan e0ffff        lightgoldenrodyellow fafad2 lightgray d3d3d3
lightgreen 90ee90       lightgrey d3d3d3         lightpink ffb6c1
lightsalmon ffa07a      lightseagreen 20b2aa     lightskyblue 87cefa
lightslategray 778899   lightslategrey 778899    lightsteelblue b0c4de
lightyellow ffffe0      lime 00ff00              limegreen 32cd32
linen faf0e6            magenta ff00ff           maroon 800000
mediumaquamarine 66cdaa mediumblue 0000cd        mediumorchid ba55d3
mediumpurple 9370db     mediumseagreen 3cb371    mediumslateblue 7b68ee
mediumspringgreen 00fa9a mediumturquoise 48d1cc  mediumvioletred c71585
midnightblue 191970     mintcream f5fffa         mistyrose ffe4e1
moccasin ffe4b5         navajowhite ffdead       navy 000080
oldlace fdf5e6          olive 808000             olivedrab 6b8e23
orange ffa500           orangered ff4500         orchid da70d6
palegoldenrod eee8aa    palegreen 98fb98         paleturquoise afeeee
palevioletred db7093    papayawhip ffefd5        peachpuff ffdab9
peru cd853f             pink ffc0cb              plum dda0dd
powderblue b0e0e6       purple 800080            rebeccapurple 663399
red ff0000              rosybrown bc8f8f         royalblue 4169e1
saddlebrown 8b4513      salmon fa8072            sandybrown f4a460
seagreen 2e8b57         seashell fff5ee          sienna a0522d
silver c0c0c0           skyblue 87ceeb           slateblue 6a5acd
slategray 708090        slategrey 708090         snow fffafa
springgreen 00ff7f      steelblue 4682b4         tan d2b48c
teal 008080             thistle d8bfd8           tomato ff6347
turquoise 40e0d0        violet ee82ee            wheat f5deb3
white ffffff            whitesmoke f5f5f5        yellow ffff00
yellowgreen 9acd32
"""

NAMED_COLORS: dict[str, tuple[int, int, int] | None] = {}
for _named_line in _NAMED_RAW.strip().splitlines():
    _tokens = _named_line.split()
    for _i in range(0, len(_tokens), 2):
        _name, _hex = _tokens[_i], _tokens[_i + 1]
        NAMED_COLORS[_name] = (int(_hex[0:2], 16), int(_hex[2:4], 16),
                               int(_hex[4:6], 16))
NAMED_COLORS["transparent"] = None

_HEX_RE = re.compile(r"^#([0-9a-fA-F]{3,8})$")
_RGB_RE = re.compile(r"^rgba?\(([^)]*)\)$")


def parse_color(text: str | None):
    """Returns (r, g, b), None for "none", or ("ref", id) for paint servers."""
    if text is None:
        return None
    s = text.strip().lower()
    if not s or s == "none" or s == "transparent":
        return None
    if s == "currentcolor":
        return (0, 0, 0)
    if s.startswith("url("):
        ref = s[s.find("(") + 1:s.rfind(")")].strip().strip("'\"")
        if ref.startswith("#"):
            return ("ref", ref[1:])
        return None
    m = _HEX_RE.match(s)
    if m:
        h = m.group(1)
        if len(h) == 3:
            return tuple(int(c * 2, 16) for c in h)  # type: ignore[return-value]
        if len(h) in (6, 8):                         # 8 = #rrggbbaa
            return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))
        raise SvgError(f"bad hex colour: {text!r}")
    m = _RGB_RE.match(s)
    if m:
        parts = [p.strip() for p in m.group(1).replace("/", ",").split(",")]
        if len(parts) < 3:
            raise SvgError(f"bad rgb() colour: {text!r}")
        vals = []
        for p in parts[:3]:
            if p.endswith("%"):
                vals.append(round(float(p[:-1]) * 2.55))
            else:
                vals.append(round(float(p)))
        return tuple(max(0, min(255, v)) for v in vals)  # type: ignore[return-value]
    if s in NAMED_COLORS:
        return NAMED_COLORS[s]
    raise SvgError(f"unsupported colour: {text!r}")


def to_argb(rgb: tuple[int, int, int], alpha: float = 1.0) -> int:
    """Packs RGB + 0..1 alpha into Android's ARGB int layout."""
    a = max(0, min(255, round(alpha * 255.0)))
    r, g, b = (max(0, min(255, c)) for c in rgb)
    return (a << 24) | (r << 16) | (g << 8) | b
