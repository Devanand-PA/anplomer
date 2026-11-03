#!/usr/bin/env python3
"""SVG part listing for the designer's animation editor.

The designer never guesses what the generator will see: this module imports
artgen's own parser, so the part list in the UI is by construction the part
list that will be baked into Java.
"""
from __future__ import annotations

import os
import sys
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
ARTGEN = os.path.abspath(os.path.join(HERE, "..", "artgen"))
if ARTGEN not in sys.path:
    sys.path.insert(0, ARTGEN)

import svg2java  # noqa: E402  (tools/artgen -- same repo, PC-side only)

# The motion vocabulary the UI offers (same source of truth as the generator).
CHANNELS = list(svg2java.CHANNELS)
CHANNEL_AMPLITUDE = dict(svg2java.CHANNEL_AMPLITUDE)
ROLE_PRESETS = {k: dict(v) for k, v in svg2java.ROLE_PRESETS.items()}


def parse_motion(value):
    """One anim entry -> (channel, phase, amplitude or None for default).

    Raises ValueError with a human message on anything the generator would
    reject, so the designer can 400 before touching art.json.
    """
    try:
        motion = svg2java.parse_anim_value(value, "anim")
    except svg2java.SvgError as exc:
        raise ValueError(str(exc)) from None
    return motion["channel"], motion["phase"], motion.get("amplitude")


def motion_of(part_name):
    """The by-name inferred motion, as parse_motion returns it."""
    return parse_motion(inferred_preset(part_name))


def inferred_preset(part_name):
    """The by-name inferred preset string ("limb-a", "static", ...)."""
    return svg2java.infer_role(part_name)


def highlight_svg(svg_text, part_name):
    """Returns the SVG with every other top-level layer dimmed and the
    named part accented -- the visual answer to "which layer is which".
    Unknown names return the SVG untouched."""
    import xml.etree.ElementTree as ET
    for prefix, uri in (
            ("", "http://www.w3.org/2000/svg"),
            ("inkscape", "http://www.inkscape.org/namespaces/inkscape"),
            ("sodipodi", "http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd"),
            ("xlink", "http://www.w3.org/1999/xlink"),
            ("dc", "http://purl.org/dc/elements/1.1/"),
            ("cc", "http://creativecommons.org/ns#"),
            ("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#")):
        ET.register_namespace(prefix, uri)
    root = ET.fromstring(svg_text)
    label_uri = "{http://www.inkscape.org/namespaces/inkscape}label"
    target = None
    for el in root.iter():
        if el.get("id") == part_name or el.get(label_uri) == part_name:
            target = el
            break
    if target is None:
        return svg_text
    parents = {c: p for p in root.iter() for c in p}
    chain = set()
    node = target
    while node is not None:
        chain.add(id(node))
        node = parents.get(node)
    skip = {"defs", "namedview", "metadata"}
    for child in list(root):
        tag = child.tag.rsplit("}", 1)[-1]
        if tag in skip or id(child) in chain:
            continue
        child.set("opacity", "0.15")
    glow = "filter:drop-shadow(0 0 2.5px #4f8cff)"
    style = (target.get("style") or "").rstrip(";")
    target.set("style", (style + ";" + glow) if style else glow)
    return ET.tostring(root, encoding="unicode")


def _names(root) -> list[str]:
    warnings: list[str] = []
    grads = svg2java.collect_gradients(root, warnings)
    parts = svg2java.collect_parts(root, warnings, grads)
    return [p.name for p in parts]


def parts_of(svg_path: str) -> list[str]:
    """Part names of an SVG file, in draw order, as artgen will bake them."""
    return _names(ET.parse(svg_path).getroot())


def parts_of_text(svg_text: str) -> list[str]:
    """Part names of SVG source text (same order artgen will bake)."""
    return _names(ET.fromstring(svg_text))


def inferred_role(part_name: str) -> str:
    """The by-name role artgen falls back to when no explicit map exists."""
    return svg2java.infer_role(part_name)
