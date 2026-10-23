#!/usr/bin/env python3
"""Bake art/stats.json into Stats.java.

Player and mob numbers live in one JSON file so that the designer GUI (and a
human in a text editor) can change HP, attack, speed and size without touching
Java. This script validates the file and writes the constants the game
compiles against. It runs on the PC only; the game never reads JSON.

Usage:
    python3 tools/statgen/stats2java.py                  # art/stats.json -> Stats.java
    python3 tools/statgen/stats2java.py --check          # validate only
    python3 tools/statgen/stats2java.py --json           # machine-readable
    python3 tools/statgen/stats2java.py --stdout         # emit Java on stdout

Exit codes: 0 ok, 2 bad usage, 3 input/output error, 4 warnings with --strict.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SRC = PROJECT_ROOT / "art" / "stats.json"
DEFAULT_PACKAGE = "com.example.anplomer"
DEFAULT_CLASS = "Stats"

ID_RE = re.compile(r"^[a-z][a-z0-9_]*$")
JAVA_KEYWORDS = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
    "class", "const", "continue", "default", "do", "double", "else", "enum",
    "extends", "final", "finally", "float", "for", "goto", "if", "implements",
    "import", "instanceof", "int", "interface", "long", "native", "new",
    "package", "private", "protected", "public", "return", "short", "static",
    "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
    "transient", "try", "void", "volatile", "while", "true", "false", "null",
}

# field -> (kind, minimum, maximum)
PLAYER_FIELDS = {
    "maxHp": ("int", 1, 9999),
    "attack": ("int", 0, 9999),
    "hurtInvuln": ("float", 0.0, 5.0),
}
MOB_FIELDS = {
    "maxHp": ("int", 1, 9999),
    "attack": ("int", 0, 9999),
    "speed": ("float", 0.0, 2000.0),
    "width": ("float", 4.0, 512.0),
    "height": ("float", 4.0, 512.0),
}
# Enemy.Type in the Java enum; the game looks these up by id.
REQUIRED_MOB_IDS = ("walker", "flyer")


class StatsError(Exception):
    """Bad input file or unusable content; reported as exit 3."""


@dataclass
class Problem:
    field: str
    message: str


@dataclass
class Validation:
    player: dict = field(default_factory=dict)
    mobs: list[dict] = field(default_factory=list)
    errors: list[Problem] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    @property
    def ok(self) -> bool:
        return not self.errors


# ---------------------------------------------------------------- validation


def _number(raw, kind, lo, hi, where: str, out: list[Problem]):
    if isinstance(raw, bool) or raw is None:
        out.append(Problem(where, "must be a number"))
        return None
    if isinstance(raw, str):
        try:
            raw = float(raw) if kind == "float" else int(raw.strip())
        except ValueError:
            out.append(Problem(where, f"must be a number, got {raw!r}"))
            return None
    if not isinstance(raw, (int, float)):
        out.append(Problem(where, f"must be a number, got {type(raw).__name__}"))
        return None
    if kind == "int":
        if raw != int(raw):
            out.append(Problem(where, f"must be a whole number, got {raw}"))
            return None
        raw = int(raw)
    else:
        raw = float(raw)
    if not (lo <= raw <= hi):
        out.append(Problem(where, f"must be between {lo} and {hi}, got {raw}"))
        return None
    return raw


def validate(data) -> Validation:
    v = Validation()
    if not isinstance(data, dict):
        v.errors.append(Problem("<root>", "must be a JSON object"))
        return v

    player = data.get("player")
    if not isinstance(player, dict):
        v.errors.append(Problem("player", "missing or not an object"))
    else:
        for name, (kind, lo, hi) in PLAYER_FIELDS.items():
            value = _number(player.get(name), kind, lo, hi, f"player.{name}", v.errors)
            if value is not None:
                v.player[name] = value
        for key in player:
            if key not in PLAYER_FIELDS:
                v.warnings.append(f"player: ignoring unknown field {key!r}")

    mobs = data.get("mobs")
    if not isinstance(mobs, list):
        v.errors.append(Problem("mobs", "missing or not a list"))
        mobs = []
    if not mobs:
        v.errors.append(Problem("mobs", "at least one mob is required"))

    seen: set[str] = set()
    for i, mob in enumerate(mobs):
        where = f"mobs[{i}]"
        if not isinstance(mob, dict):
            v.errors.append(Problem(where, "must be an object"))
            continue
        mid = mob.get("id")
        if not isinstance(mid, str) or not ID_RE.match(mid or ""):
            v.errors.append(Problem(f"{where}.id", "must match [a-z][a-z0-9_]*"))
            continue
        where = f"mobs[{i}] ({mid})"
        if mid in seen:
            v.errors.append(Problem(f"{where}.id", "duplicate id"))
            continue
        seen.add(mid)
        if mid in JAVA_KEYWORDS:
            v.errors.append(Problem(f"{where}.id", f"'{mid}' is a Java keyword"))
            continue
        entry = {"id": mid}
        name = mob.get("name", mid)
        if not isinstance(name, str) or not name.strip():
            v.errors.append(Problem(f"{where}.name", "must be a non-empty string"))
            name = mid
        elif len(name) > 40:
            v.warnings.append(f"{where}.name is longer than 40 characters")
        entry["name"] = name.strip()
        for key, (kind, lo, hi) in MOB_FIELDS.items():
            value = _number(mob.get(key), kind, lo, hi, f"{where}.{key}", v.errors)
            if value is not None:
                entry[key] = value
        for key in mob:
            if key not in MOB_FIELDS and key not in ("id", "name"):
                v.warnings.append(f"{where}: ignoring unknown field {key!r}")
        v.mobs.append(entry)

    for required in REQUIRED_MOB_IDS:
        if required not in seen:
            v.warnings.append(
                f"no mob with id {required!r}; the game's {required.upper()} "
                f"enemy will fall back to the first mob")
    return v


# ------------------------------------------------------------------- emitter


def fmt(value) -> str:
    if isinstance(value, int):
        return str(value)
    text = f"{value:.3f}".rstrip("0").rstrip(".")
    return (text if text not in ("", "-0") else "0") + "f"


def java_string(text: str) -> str:
    return json.dumps(text)  # Java and JSON string escapes agree here


def emit(v: Validation, package: str, class_name: str, source: str,
         regen: str) -> str:
    out: list[str] = [
        "// GENERATED FILE - DO NOT EDIT BY HAND.",
        f"// Source:      {source}",
        "// Generator:   tools/statgen/stats2java.py",
        f"// Regenerate:  {regen}",
        "//",
        "// Player and mob numbers. Edit art/stats.json (or use tools/designer) and",
        "// regenerate; these constants are what the game actually reads.",
        f"package {package};",
        "",
        "/** Baked gameplay stats: the hero plus every mob archetype. */",
        f"public final class {class_name} {{",
        "",
        f"    private {class_name}() {{",
        "    }",
        "",
        "    /** The hero. */",
        "    public static final class Hero {",
    ]
    for name, (kind, _lo, _hi) in PLAYER_FIELDS.items():
        const = re.sub(r"(?<!^)(?=[A-Z])", "_", name).upper()
        java_type = "int" if kind == "int" else "float"
        out.append(f"        public static final {java_type} {const} = "
                   f"{fmt(v.player[name])};")
    out += [
        "",
        "        private Hero() {",
        "        }",
        "    }",
        "",
        "    /** One mob archetype; immutable. */",
        "    public static final class Mob {",
        "        public final String id;",
        "        public final String name;",
        "        public final int maxHp;",
        "        public final int attack;",
        "        public final float speed;",
        "        public final float width;",
        "        public final float height;",
        "",
        "        Mob(String id, String name, int maxHp, int attack, float speed,",
        "                float width, float height) {",
        "            this.id = id;",
        "            this.name = name;",
        "            this.maxHp = maxHp;",
        "            this.attack = attack;",
        "            this.speed = speed;",
        "            this.width = width;",
        "            this.height = height;",
        "        }",
        "",
        "        @Override",
        "        public String toString() {",
        "            return name + \"(\" + maxHp + \"hp/\" + attack + \"atk)\";",
        "        }",
        "    }",
        "",
        "    /** Every mob, in stats.json order. */",
        "    public static final Mob[] MOBS = {",
    ]
    for mob in v.mobs:
        out.append(
            f"        new Mob({java_string(mob['id'])}, {java_string(mob['name'])}, "
            f"{fmt(mob['maxHp'])}, {fmt(mob['attack'])}, {fmt(mob['speed'])}, "
            f"{fmt(mob['width'])}, {fmt(mob['height'])}),")
    out += [
        "    };",
        "",
        "    /**",
        "     * Looks a mob up by id.",
        "     *",
        "     * @return the matching mob, or {@code null} when the id is unknown",
        "     */",
        "    public static Mob find(String id) {",
        "        for (Mob m : MOBS) {",
        "            if (m.id.equals(id)) {",
        "                return m;",
        "            }",
        "        }",
        "        return null;",
        "    }",
        "",
        "    /**",
        "     * Looks a mob up by id, falling back to the first mob so callers never",
        "     * have to null-check in the frame loop.",
        "     */",
        "    public static Mob mob(String id) {",
        "        Mob m = find(id);",
        "        return m != null ? m : MOBS[0];",
        "    }",
        "}",
        "",
    ]
    return "\n".join(out)


# ------------------------------------------------------------------------ cli


def rel(path: Path) -> str:
    try:
        return str(path.resolve().relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


def default_out(package: str, class_name: str) -> Path:
    return PROJECT_ROOT / "app" / "src" / "main" / "java" / Path(
        *package.split(".")) / f"{class_name}.java"


class Reporter:
    def __init__(self, quiet: bool = False):
        self.quiet = quiet

    def info(self, text: str) -> None:
        if not self.quiet:
            print(text)

    def warn(self, text: str) -> None:
        print(f"warning: {text}", file=sys.stderr)

    def error(self, text: str) -> None:
        print(f"error: {text}", file=sys.stderr)


def load(src: Path) -> tuple[dict, Validation]:
    try:
        raw = src.read_text(encoding="utf-8")
    except OSError as exc:
        raise StatsError(f"{rel(src)}: {exc.strerror or exc}") from None
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise StatsError(
            f"{rel(src)}: bad JSON at line {exc.lineno}, column {exc.colno}: "
            f"{exc.msg}") from None
    return data, validate(data)


def run(args, reporter: Reporter) -> int:
    src = Path(args.src) if args.src else DEFAULT_SRC
    data, v = load(src)
    source_label = rel(src)

    for warning in v.warnings:
        reporter.warn(warning)
    if not v.ok:
        for problem in v.errors:
            reporter.error(f"{source_label}: {problem.field}: {problem.message}")
        if args.json:
            print(json.dumps({
                "ok": False,
                "src": source_label,
                "errors": [{"field": p.field, "message": p.message}
                           for p in v.errors],
            }, indent=2))
        return 3

    out_path = Path(args.out) if args.out else default_out(args.package, args.class_name)
    regen = f"python3 tools/statgen/stats2java.py {source_label}"
    if rel(out_path) != rel(default_out(args.package, args.class_name)):
        regen += f" --out {rel(out_path)}"
    java = emit(v, args.package, args.class_name, source_label, regen)

    if args.stdout:
        sys.stdout.write(java)
        return 0

    written = False
    if not args.check:
        try:
            out_path.parent.mkdir(parents=True, exist_ok=True)
            changed = True
            if out_path.exists():
                changed = out_path.read_text(encoding="utf-8") != java
            if changed:
                tmp = out_path.with_suffix(".java.tmp")
                tmp.write_text(java, encoding="utf-8")
                os.replace(tmp, out_path)
            written = changed
        except OSError as exc:
            raise StatsError(f"{rel(out_path)}: {exc.strerror or exc}") from None

    mobs = ", ".join(f"{m['id']}({m['maxHp']}hp/{m['attack']}atk)" for m in v.mobs)
    summary = {
        "ok": True,
        "src": source_label,
        "out": rel(out_path),
        "bytes": len(java.encode("utf-8")),
        "written": written,
        "player": dict(v.player),
        "mobs": [dict(m) for m in v.mobs],
        "warnings": list(v.warnings),
    }
    if args.json:
        print(json.dumps(summary, indent=2))
    else:
        verb = "unchanged" if (not args.check and not written) else (
            "checked" if args.check else "wrote")
        reporter.info(f"{source_label} -> {rel(out_path)}  {verb}: "
                      f"hero {v.player['maxHp']}hp/{v.player['attack']}atk, "
                      f"mobs {mobs}")
    return 4 if (args.strict and v.warnings) else 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="stats2java.py",
        description="Bake art/stats.json into Stats.java.")
    parser.add_argument("src", nargs="?", help="stats JSON (default: art/stats.json)")
    parser.add_argument("--out", help="output .java path")
    parser.add_argument("--package", default=DEFAULT_PACKAGE,
                        help=f"Java package (default: {DEFAULT_PACKAGE})")
    parser.add_argument("--class", dest="class_name", default=DEFAULT_CLASS,
                        help=f"Java class name (default: {DEFAULT_CLASS})")
    parser.add_argument("--check", action="store_true",
                        help="validate and report, write nothing")
    parser.add_argument("--stdout", action="store_true", help="emit Java on stdout")
    parser.add_argument("--json", action="store_true",
                        help="machine-readable summary on stdout")
    parser.add_argument("--strict", action="store_true",
                        help="exit 4 when warnings were reported")
    args = parser.parse_args(argv)

    if args.stdout and args.json:
        parser.error("--stdout and --json are mutually exclusive")
    if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", args.class_name):
        parser.error(f"invalid Java class name: {args.class_name!r}")

    reporter = Reporter(quiet=args.json or args.stdout)
    try:
        return run(args, reporter)
    except StatsError as exc:
        if args.json:
            print(json.dumps({"ok": False, "error": str(exc)}, indent=2))
        else:
            print(f"error: {exc}", file=sys.stderr)
        return 3


if __name__ == "__main__":
    sys.exit(main())
