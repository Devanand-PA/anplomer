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

# The role catalog the UI offers. Order must match artgen's ROLES list.
ANIM_ROLES = list(svg2java.ROLES)


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
