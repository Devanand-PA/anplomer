#!/usr/bin/env python3
"""Self-test for the SVG -> Java art pipeline.

Run after touching `svgpath.py` or `svg2java.py`:

    python3 tools/artgen/selftest.py             # verify (default)
    python3 tools/artgen/selftest.py --update    # rewrite samples/expected/*.java

The suite has three layers:

1. Unit checks on the parser (relative commands, H/V, S/T reflection, arcs,
   transforms, colours, shape helpers).
2. Golden-file comparison: converting `samples/*.svg` must reproduce
   `samples/expected/*.java` byte for byte.
3. An end-to-end compile + run of the generated classes against the
   `android.graphics` stubs, which also proves no coordinate is non-finite.

Exit code 0 on success, 1 on any failure.
"""
from __future__ import annotations

import contextlib
import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SAMPLES = HERE / "samples"
EXPECTED = SAMPLES / "expected"

sys.path.insert(0, str(HERE))

import svg2java  # noqa: E402
import svgpath  # noqa: E402

GENERATOR = "python3 tools/artgen/svg2java.py"

CASES = [
    {
        "svg": "shapes.svg",
        "class": "ArtShapes",
        "fit": None,
        "anchor": "bottom-center",
        "precision": 2,
    },
    {
        "svg": "player.svg",
        "class": "ArtPlayer",
        "fit": (26.0, 36.0),
        "anchor": "bottom-center",
        "precision": 2,
    },
]

failures: list[str] = []


def check(name: str, ok: bool, detail: str = "") -> None:
    if ok:
        print(f"  ok    {name}")
    else:
        print(f"  FAIL  {name}{': ' + detail if detail else ''}")
        failures.append(name)


def near(a, b, tol=1e-4) -> bool:
    return abs(a - b) <= tol


def case_regen(case) -> str:
    bits = [GENERATOR, f"tools/artgen/samples/{case['svg']}",
            "--class", case["class"]]
    if case["fit"]:
        bits += ["--fit", f"{case['fit'][0]:g}x{case['fit'][1]:g}"]
    if case["anchor"] != "bottom-center":
        bits += ["--anchor", case["anchor"]]
    if case["precision"] != 2:
        bits += ["--precision", str(case["precision"])]
    return " ".join(bits)


def generate(case) -> str:
    result = svg2java.convert(
        svg_path=SAMPLES / case["svg"],
        class_name=case["class"],
        package="com.example.anplomer",
        out_path=None,
        fit=case["fit"],
        scale=None,
        anchor=case["anchor"],
        precision=case["precision"],
        write=False,
        regen_cmd=case_regen(case),
        source_label=f"tools/artgen/samples/{case['svg']}",
    )
    return result.java or ""


# ----------------------------------------------------------------------
# layer 1: parser units
# ----------------------------------------------------------------------

def unit_checks() -> None:
    print("parser")
    p = svgpath.parse_path_data

    check("relative commands match absolute",
          p("M10 10 L20 10 C30 20 30 30 35 30 Z")
          == p("M10 10 l10 0 c10 10 10 20 15 20 z"),
          f"{p('M10 10 l10 0')}")

    check("H and V become L",
          p("M0 0 H5 V10") == [("M", 0.0, 0.0), ("L", 5.0, 0.0), ("L", 5.0, 10.0)],
          str(p("M0 0 H5 V10")))

    s_curve = p("M0 0 C0 0 10 0 10 10 S20 20 20 10")
    check("S reflects the previous control point",
          s_curve[2] == ("C", 10.0, 20.0, 20.0, 20.0, 20.0, 10.0),
          str(s_curve[-1]))

    t_curve = p("M0 0 Q5 10 10 0 T20 0")
    check("T reflects the previous quadratic control point",
          t_curve[2] == ("Q", 15.0, -10.0, 20.0, 0.0),
          str(t_curve[-1]))

    arc = p("M0 0 A10 10 0 0 1 20 0")
    bbox = svgpath.bbox_of_segments(arc)
    check("arc becomes cubics ending on the arc endpoint",
          arc[-1][0] == "C" and near(arc[-1][-2], 20.0) and near(arc[-1][-1], 0.0),
          str(arc[-1]))
    check("arc is split into <=90 degree pieces", len(arc) == 3, str(len(arc)))
    check("semicircle bbox is a half disc",
          near(bbox[0], 0.0) and near(bbox[2], 20.0)
          and near(bbox[1], -10.0) and near(bbox[3], 0.0),
          str(bbox))

    m = svgpath.parse_transform("translate(10,0) scale(2)")
    check("transform list applies left-to-right to the outside",
          near(m.apply(1.0, 0.0)[0], 12.0) and near(m.apply(1.0, 0.0)[1], 0.0)
          and near(m.apply(0.0, 1.0)[0], 10.0) and near(m.apply(0.0, 1.0)[1], 2.0),
          str(m.apply(1.0, 0.0)))

    r = svgpath.parse_transform("translate(5,5) rotate(90)")
    check("rotate drives the standard matrix",
          near(r.apply(1.0, 0.0)[0], 5.0) and near(r.apply(1.0, 0.0)[1], 6.0),
          str(r.apply(1.0, 0.0)))

    check("hex shorthand expands",
          svgpath.parse_color("#abc") == (0xAA, 0xBB, 0xCC))
    check("rgb() parses", svgpath.parse_color("rgb(255, 0, 0)") == (255, 0, 0))
    check("named colour parses", svgpath.parse_color("tomato") == (255, 99, 71))
    check("none is no paint", svgpath.parse_color("none") is None)
    check("url() reference is a paint server",
          svgpath.parse_color("url(#g)") == ("ref", "g"))
    check("alpha folds into ARGB",
          svgpath.to_argb((255, 0, 0), 0.5) == 0x80FF0000)

    rect = svgpath.shape_to_segments("rect", {"x": "2", "y": "3", "width": "10",
                                              "height": "4", "rx": "1"})
    rb = svgpath.bbox_of_segments(rect)
    check("rounded rect spans its box",
          near(rb[0], 2.0) and near(rb[1], 3.0)
          and near(rb[2], 12.0) and near(rb[3], 7.0), str(rb))
    check("rounded rect is closed", rect[-1] == ("Z",), str(rect[-1]))

    poly = svgpath.shape_to_segments("polygon", {"points": "0,0 10,0 5,8"})
    check("polygon closes itself", poly[-1] == ("Z",), str(poly))


# ----------------------------------------------------------------------
# layer 2: pipeline behaviour + goldens
# ----------------------------------------------------------------------

def pipeline_checks() -> None:
    print("pipeline")

    shapes = svg2java.convert(
        SAMPLES / "shapes.svg", "ArtShapes", "com.example.anplomer", None,
        None, None, "bottom-center", 2, False, "x")
    by_name = {p.name: p for p in _parts_of(shapes)}
    check("gradient fill is reported as an approximation",
          any("gradient" in w for w in shapes.warnings), str(shapes.warnings))
    check("even-odd survives", by_name["donut"].even_odd)

    oval = by_name["oval"]
    check("stroke colour is baked", oval.stroke_color == 0xFF222222,
          hex(oval.stroke_color))
    check("stroke width is baked", near(oval.stroke_width, 2.0),
          str(oval.stroke_width))
    check("gradient fill is approximated by its first stop",
          by_name["disc"].color == 0xFF102030, hex(by_name["disc"].color))
    check("stroke-only shapes have no fill",
          by_name["zig"].color == 0 and by_name["zig"].stroke_color == 0xFF00897B)
    check("unstoked parts carry no stroke width",
          by_name["rounded"].stroke_width == 0.0)

    player = svg2java.convert(
        SAMPLES / "player.svg", "ArtPlayer", "com.example.anplomer", None,
        (26.0, 36.0), None, "bottom-center", 2, False, "x")
    segs = [s for p in _parts_of(player) for s in p.segments]
    bbox = svgpath.bbox_of_segments(segs)
    check("--fit puts the bottom-centre anchor on the origin",
          near(bbox[3], 0.0, 1e-3) and near((bbox[0] + bbox[2]) / 2.0, 0.0, 1e-3),
          str(bbox))
    check("--fit fills one axis exactly",
          near(max(player.summary["width"], player.summary["height"]), 36.0, 1e-3),
          str(player.summary["width"]) + "x" + str(player.summary["height"]))

    raw = svg2java.convert(
        SAMPLES / "player.svg", "ArtPlayerRaw", "com.example.anplomer", None,
        None, None, "bottom-center", 2, False, "x")
    raw_segs = [s for p in _parts_of(raw) for s in p.segments]
    raw_bbox = svgpath.bbox_of_segments(raw_segs)
    raw_ratio = (raw_bbox[2] - raw_bbox[0]) / (raw_bbox[3] - raw_bbox[1])
    fit_ratio = (bbox[2] - bbox[0]) / (bbox[3] - bbox[1])
    check("--fit scales uniformly (aspect preserved)",
          near(raw_ratio, fit_ratio, 1e-3), f"{raw_ratio} vs {fit_ratio}")
    check("group ids name the parts",
          "leg-left" in [p.name for p in _parts_of(player)])

    tmp = Path(tempfile.mkdtemp())
    (tmp / "opacity.svg").write_text(
        '<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10">'
        '<g opacity="0.5"><rect id="r" x="1" y="1" width="4" height="4" '
        'fill="#FF0000" fill-opacity="0.5"/></g></svg>', encoding="utf-8")
    faded = svg2java.convert(tmp / "opacity.svg", "ArtFaded", "p", None,
                             None, None, "bottom-center", 2, False, "x")
    alpha = (_parts_of(faded)[0].color >> 24) if _parts_of(faded) else -1
    check("nested opacity multiplies into the alpha", alpha == 0x40, hex(alpha))

    (tmp / "hidden.svg").write_text(
        '<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10">'
        '<rect id="gone" display="none" fill="#000" width="4" height="4"/>'
        '<text id="t">hi</text>'
        '<rect id="kept" fill="#00FF00" width="4" height="4"/></svg>',
        encoding="utf-8")
    hidden = svg2java.convert(tmp / "hidden.svg", "ArtHidden", "p", None,
                              None, None, "bottom-center", 2, False, "x")
    names = [p.name for p in _parts_of(hidden)]
    check("display:none is dropped", names == ["kept"], str(names))
    check("unsupported elements warn",
          any("text" in w for w in hidden.warnings), str(hidden.warnings))

    check("--strict turns warnings into exit 4",
          _quiet_main([str(SAMPLES / "shapes.svg"), "--check", "--strict",
                       "--json"]) == 4)
    check("clean input exits 0",
          _quiet_main([str(SAMPLES / "player.svg"), "--check", "--strict",
                       "--json"]) == 0)
    check("missing file exits 3",
          _quiet_main([str(SAMPLES / "nope.svg"), "--check", "--json"]) == 3)
    check("bad usage exits 2",
          _quiet_main(["--fit", "nonsense", str(SAMPLES / "player.svg")]) == 2)

    # --manifest also bakes ArtBindings.java, the entity -> art registry the
    # game reads. One entity per asset (first binding wins on duplicates),
    # and an empty manifest still emits an empty registry so the game always
    # has the class to compile against.
    out = tmp / "out"
    manifest = tmp / "art.json"
    manifest.write_text(json.dumps({
        "package": "com.example.anplomer",
        "outDir": str(out),
        "assets": [
            {"src": str(SAMPLES / "shapes.svg"), "class": "ArtBindA",
             "fit": "10x10", "entity": "walker"},
            {"src": str(SAMPLES / "player.svg"), "class": "ArtBindB",
             "fit": "26x36", "entity": "flyer"},
            {"src": str(SAMPLES / "shapes.svg"), "class": "ArtBindC",
             "entity": "flyer"},
        ],
    }), encoding="utf-8")
    rc = _quiet_main(["--manifest", str(manifest), "--json"])
    bindings_path = out / "ArtBindings.java"
    text = bindings_path.read_text(encoding="utf-8") \
        if bindings_path.exists() else ""
    check("manifest exits 0", rc == 0, f"rc {rc}")
    check("ArtBindings.java emitted", bool(text), "file missing")
    check("bindings map entity -> class",
          'case "walker":' in text and "ArtBindA.draw(" in text
          and 'case "flyer":' in text and "ArtBindB.draw(" in text, text[:200])
    check("duplicate entity keeps first binding",
          "ArtBindB.draw(" in text and "ArtBindC" not in text)
    check("bindings import android.graphics",
          "import android.graphics.Canvas;" in text)
    empty = tmp / "empty.json"
    empty.write_text(json.dumps({"package": "com.example.anplomer",
                                 "outDir": str(tmp / "empty-out"),
                                 "assets": []}), encoding="utf-8")
    _quiet_main(["--manifest", str(empty)])
    empty_text = (tmp / "empty-out" / "ArtBindings.java").read_text(encoding="utf-8")
    check("empty manifest emits an empty registry",
          "public final class ArtBindings" in empty_text
          and 'case' not in empty_text, empty_text[:120])
    (tmp / "none-out").mkdir(exist_ok=True)
    check_only = tmp / "check.json"
    check_only.write_text(json.dumps({
        "package": "com.example.anplomer", "outDir": str(tmp / "none-out"),
        "assets": [{"src": str(SAMPLES / "shapes.svg"),
                    "class": "ArtBindD", "entity": "player"}],
    }), encoding="utf-8")
    _quiet_main(["--manifest", str(check_only), "--check"])
    check("--check writes no bindings",
          not (tmp / "none-out" / "ArtBindings.java").exists())

    shutil.rmtree(tmp, ignore_errors=True)


def _quiet_main(argv: list[str]) -> int:
    """Runs the CLI in-process, swallowing its stdout/stderr chatter."""
    buffer = io.StringIO()
    with contextlib.redirect_stdout(buffer), contextlib.redirect_stderr(buffer):
        try:
            return svg2java.main(argv)
        except SystemExit as exc:          # argparse errors
            return int(exc.code or 0)


def _parts_of(result) -> list:
    """`convert()` keeps the parsed parts alongside the generated source."""
    return result.parts


def golden_checks(update: bool) -> None:
    print("golden files")
    EXPECTED.mkdir(parents=True, exist_ok=True)
    for case in CASES:
        target = EXPECTED / f"{case['class']}.java"
        text = generate(case)
        if update:
            target.write_text(text, encoding="utf-8")
            print(f"  wrote {target.relative_to(ROOT)}")
            continue
        if not target.exists():
            check(f"{case['class']} golden exists", False,
                  f"run with --update to create {target.relative_to(ROOT)}")
            continue
        expected = target.read_text(encoding="utf-8")
        if expected == text:
            check(f"{case['class']} matches golden", True)
        else:
            check(f"{case['class']} matches golden", False,
                  "run --update if the change was intentional")
            for i, (a, b) in enumerate(zip(expected.splitlines(),
                                           text.splitlines())):
                if a != b:
                    print(f"        first diff at line {i + 1}:")
                    print(f"        expected: {a}")
                    print(f"        actual:   {b}")
                    break


# ----------------------------------------------------------------------
# layer 3: compile + run the generated classes
# ----------------------------------------------------------------------

def build_checks() -> None:
    print("compile + run")
    javac = shutil.which("javac")
    java = shutil.which("java")
    if not javac or not java:
        check("javac/java available", False, "install a JDK to run this layer")
        return

    work = ROOT / "build" / "artgen-selftest"
    shutil.rmtree(work, ignore_errors=True)
    src = work / "src" / "com" / "example" / "anplomer"
    src.mkdir(parents=True)
    out = work / "out"
    out.mkdir(parents=True)

    for case in CASES:
        (src / f"{case['class']}.java").write_text(generate(case), encoding="utf-8")

    stubs = sorted((ROOT / "tools" / "stub" / "android" / "graphics").glob("*.java"))
    sources = stubs + sorted(src.glob("*.java")) + [SAMPLES / "SampleArtCheck.java"]
    # Only the stubs go on the sourcepath; the generated files are listed
    # explicitly, otherwise javac loads each of them twice.
    proc = subprocess.run([javac, "-nowarn", "-d", str(out),
                           "-sourcepath", str(ROOT / "tools" / "stub"),
                           *map(str, sources)],
                          capture_output=True, text=True)
    if proc.returncode != 0:
        check("generated classes compile against the stubs", False,
              " | ".join(proc.stderr.strip().splitlines()[:3]))
        return
    check("generated classes compile against the stubs", True)

    # -Djava.awt.headless=true: the check rebuilds the paths as java.awt Path2D
    # to assert winding/bounds, and this box has a DISPLAY that points nowhere.
    run = subprocess.run([java, "-Djava.awt.headless=true",
                          "-cp", os.pathsep.join([str(out), str(ROOT / "tools" / "stub")]),
                          "SampleArtCheck"], capture_output=True, text=True)
    ok = run.returncode == 0 and "evenOddDonut=true" in run.stdout
    check("generated classes run with finite coordinates", ok,
          (run.stderr or run.stdout).strip()[:300])
    for line in run.stdout.strip().splitlines():
        print(f"        {line}")

    shutil.rmtree(work, ignore_errors=True)


def main(argv: list[str]) -> int:
    update = "--update" in argv
    print(f"artgen selftest  (python {sys.version.split()[0]})")
    unit_checks()
    pipeline_checks()
    golden_checks(update)
    if update:
        print("\ngoldens updated; re-run without --update to verify")
        return 0
    build_checks()
    print()
    if failures:
        print(f"FAILED: {len(failures)} check(s): {', '.join(failures)}")
        return 1
    print("all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
