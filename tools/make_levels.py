#!/usr/bin/env python3
"""Build, validate and emit the Anplomer level data (Levels.java).

Run:  python3 tools/make_levels.py            # regenerate + validate
      python3 tools/make_levels.py --check    # validate only (no write)
"""
from __future__ import annotations

import os
import sys

H = 15
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "..", "app", "src", "main", "java", "com", "example",
                   "anplomer", "Levels.java")

EMPTY, GROUND, STONE = ".", "#", "="
SPIKE, LAVA, COIN, WALKER, FLYER = "^", "~", "o", "E", "B"
MOVER_H, MOVER_V, START, CHECK, GOAL = "M", "V", "P", "C", "G"


class Level:
    def __init__(self, w: int, par: int, name: str):
        self.w, self.par, self.name = w, par, name
        self.g = [[EMPTY] * w for _ in range(H)]

    # -- primitive ops -------------------------------------------------
    def put(self, x, y, ch):
        assert 0 <= x < self.w and 0 <= y < H, f"out of bounds {x},{y}"
        assert self.g[y][x] == EMPTY or self.g[y][x] == ch, \
            f"{self.name}: overwrite at {x},{y}: {self.g[y][x]} -> {ch}"
        self.g[y][x] = ch

    def seg(self, x0, x1, surface):
        """Ground slab whose walkable surface is row `surface`."""
        for x in range(x0, x1 + 1):
            for y in range(surface, H):
                if self.g[y][x] == EMPTY:
                    self.g[y][x] = GROUND

    def stone(self, x0, x1, y):
        for x in range(x0, x1 + 1):
            self.put(x, y, STONE)

    def coins(self, xs, y):
        for x in xs:
            self.put(x, y, COIN)

    def arc(self, x0, x1, y):
        """Coin arc spanning columns x0..x1 (inclusive), rising up to 2 rows."""
        n = x1 - x0 + 1
        for i, x in enumerate(range(x0, x1 + 1)):
            t = 0.5 if n == 1 else i / (n - 1)
            dy = round(2 * (1 - (2 * (t - 0.5)) ** 2))
            self.put(x, y - dy, COIN)

    def spikes(self, xs, surface):
        for x in xs:
            self.put(x, surface - 1, SPIKE)

    def lava(self, x0, x1, surface):
        for x in range(x0, x1 + 1):
            for y in range(surface, H):
                if self.g[y][x] == EMPTY:
                    self.g[y][x] = LAVA

    def walker(self, x, surface):
        self.put(x, surface - 1, WALKER)

    def flyer(self, x, y):
        self.put(x, y, FLYER)

    def mover_h(self, x, y):
        self.put(x, y, MOVER_H)

    def mover_v(self, x, y):
        self.put(x, y, MOVER_V)

    def start(self, x, surface):
        self.put(x, surface - 1, START)

    def checkpoint(self, x, surface):
        self.put(x, surface - 1, CHECK)

    def goal(self, x, surface):
        self.put(x, surface - 1, GOAL)

    def rows(self):
        return ["".join(r) for r in self.g]


# ----------------------------------------------------------------------
# LEVEL 1 — Emerald Meadow: gentle gaps, coins, a few grunts
# ----------------------------------------------------------------------
def level1() -> Level:
    L = Level(152, 50, "Emerald Meadow")
    L.seg(0, 17, 12)
    L.seg(21, 34, 12)
    L.seg(38, 54, 12)
    L.seg(58, 72, 11)
    L.seg(76, 88, 12)
    L.seg(92, 108, 12)
    L.seg(112, 126, 11)
    L.seg(130, 151, 12)

    L.start(2, 12)
    L.coins(range(6, 11), 10)
    L.arc(18, 20, 10)
    L.walker(13, 12)

    L.walker(26, 12)
    L.stone(30, 33, 9)
    L.coins(range(30, 34), 8)
    L.arc(35, 37, 10)

    L.walker(45, 12)
    L.spikes([48, 49], 12)
    L.coins(range(51, 54), 10)
    L.arc(55, 57, 10)

    L.coins(range(66, 69), 9)
    L.spikes([62], 11)
    L.arc(73, 75, 11)

    L.walker(80, 12)
    L.coins(range(83, 87), 10)
    L.spikes([84, 85], 12)
    L.arc(89, 91, 10)

    L.checkpoint(95, 12)
    L.stone(99, 101, 10)
    L.coins(range(99, 102), 9)
    L.stone(105, 107, 9)
    L.coins(range(105, 108), 8)
    L.arc(109, 111, 10)

    L.flyer(117, 7)
    L.coins(range(118, 122), 9)
    L.walker(123, 11)
    L.arc(127, 129, 10)

    L.walker(136, 12)
    L.coins(range(140, 145), 10)
    L.goal(148, 12)
    return L


# ----------------------------------------------------------------------
# LEVEL 2 — Sunset Ruins: movers over lava, flyers, tighter hops
# ----------------------------------------------------------------------
def level2() -> Level:
    L = Level(170, 65, "Sunset Ruins")
    L.seg(0, 16, 12)
    L.seg(27, 44, 12)          # lava pit 17..26 bridged by a mover
    L.seg(48, 60, 11)
    L.seg(64, 74, 12)
    L.seg(84, 98, 12)          # lava pit 75..83 bridged by a mover
    L.seg(102, 114, 11)
    L.seg(124, 138, 12)        # lava pit 115..123 bridged by a mover
    L.seg(142, 169, 10)        # raised finale plateau

    L.start(2, 12)
    L.coins(range(6, 10), 10)
    L.walker(14, 12)
    L.spikes([7, 8], 12)
    L.arc(17, 20, 10)

    L.lava(17, 26, 13)
    L.mover_h(21, 11)
    L.coins([19, 23, 25], 9)

    L.walker(29, 12)
    L.stone(31, 34, 9)
    L.coins(range(31, 35), 8)
    L.flyer(45, 5)
    L.spikes([39, 40], 12)
    L.arc(45, 47, 11)

    L.walker(50, 11)
    L.coins(range(52, 56), 9)
    L.stone(57, 59, 8)
    L.coins([58], 7)
    L.arc(61, 63, 11)

    L.walker(67, 12)
    L.checkpoint(70, 12)
    L.coins(range(64, 67), 10)

    L.lava(75, 83, 13)
    L.mover_h(79, 11)
    L.mover_v(79, 4)
    L.coins([76, 79, 82], 9)

    L.flyer(87, 5)
    L.stone(90, 93, 9)
    L.coins(range(90, 94), 8)
    L.walker(96, 12)
    L.arc(99, 101, 11)

    L.stone(105, 107, 8)
    L.coins(range(105, 108), 7)
    L.flyer(118, 5)

    L.lava(115, 123, 13)
    L.mover_h(119, 11)
    L.coins([116, 119, 122], 9)

    L.walker(128, 12)
    L.coins(range(130, 134), 10)
    L.stone(135, 137, 10)

    L.flyer(146, 4)
    L.walker(150, 10)
    L.coins(range(153, 158), 8)
    L.stone(160, 163, 7)
    L.coins(range(160, 164), 6)
    L.spikes([165, 166], 10)
    L.goal(168, 10)
    return L


# ----------------------------------------------------------------------
# LEVEL 3 — Moonlit Peak: shafts, vertical movers, lava and bat swarms
# ----------------------------------------------------------------------
def level3() -> Level:
    L = Level(190, 80, "Moonlit Peak")
    L.seg(0, 18, 12)
    L.seg(22, 36, 12)
    L.seg(40, 52, 12)
    L.seg(60, 74, 12)          # lava pit 53..59 crossed by a mover
    L.seg(78, 92, 11)
    L.seg(96, 110, 12)
    L.seg(118, 132, 12)        # lava pit 111..117 crossed by a mover
    L.seg(136, 150, 11)
    L.seg(158, 172, 12)        # lava pit 151..157 crossed by a mover
    L.seg(176, 189, 10)        # final plateau

    L.start(2, 12)
    L.coins(range(6, 11), 10)
    L.spikes([12, 13], 12)
    L.arc(19, 21, 10)

    L.walker(27, 12)
    L.flyer(31, 7)
    L.stone(32, 35, 9)
    L.coins(range(32, 36), 8)
    L.spikes([45, 46], 12)

    L.coins(range(47, 51), 10)

    L.lava(53, 59, 13)
    L.mover_h(56, 11)
    L.coins([54, 56, 58], 9)

    # no walker here: it patrolled 60-74 right up to the gap ledge at 74.9,
    # forcing an unavoidable meeting with no safe landing for a jump
    L.checkpoint(67, 12)
    L.spikes([69, 70], 12)
    L.arc(75, 77, 12)

    L.flyer(82, 6)
    L.stone(85, 88, 8)
    L.coins(range(85, 89), 7)
    L.walker(90, 11)
    L.arc(93, 95, 11)

    L.walker(100, 12)
    L.spikes([104, 105], 12)
    L.stone(105, 108, 9)
    L.coins(range(105, 109), 8)

    L.lava(111, 117, 13)
    L.mover_h(114, 11)
    L.mover_v(114, 4)
    L.coins([112, 114, 116], 9)

    L.flyer(121, 6)
    L.coins(range(128, 132), 10)
    L.spikes([127, 128], 12)

    L.flyer(139, 7)
    L.stone(141, 144, 9)
    L.coins(range(141, 145), 8)
    L.walker(146, 11)

    L.lava(151, 157, 13)
    L.mover_h(154, 10)
    L.coins([152, 154, 156], 8)

    L.checkpoint(161, 12)
    L.flyer(165, 7)
    L.stone(167, 170, 9)
    L.coins(range(167, 171), 8)

    L.flyer(179, 4)
    L.walker(182, 10)
    L.coins(range(184, 188), 8)
    L.goal(188, 10)
    return L


# ----------------------------------------------------------------------
# validation
# ----------------------------------------------------------------------
def validate(L: Level) -> list[str]:
    errs = []
    rows = L.rows()
    for i, r in enumerate(rows):
        if len(r) != L.w:
            errs.append(f"row {i} length {len(r)} != {L.w}")
        if r != r.rstrip():
            errs.append(f"row {i} has trailing whitespace")

    flat = "".join(rows)
    for ch, label in ((START, "start"), (GOAL, "goal")):
        if flat.count(ch) != 1:
            errs.append(f"expected exactly one {label} ('{ch}'), found {flat.count(ch)}")

    if flat.count(CHECK) > 2:
        errs.append("too many checkpoints")

    def solid(x, y):
        if x < 0 or x >= L.w:
            return True
        if y < 0 or y >= H:
            return False
        return rows[y][x] in (GROUND, STONE)

    def empty(x, y):
        return 0 <= x < L.w and 0 <= y < H and rows[y][x] == EMPTY

    # every marker must sit in the air
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in (COIN, WALKER, FLYER, START, GOAL, CHECK, MOVER_H, MOVER_V, SPIKE):
                if rows[y][x] in (GROUND, STONE, LAVA):
                    errs.append(f"{ch} at {x},{y} is buried in terrain")
            if ch == WALKER:
                if not (solid(x, y + 1) or solid(x + 1, y + 1)):
                    errs.append(f"walker at {x},{y} has no floor below")
            if ch == FLYER:
                if y > 9:
                    errs.append(f"flyer at {x},{y} too low (row {y})")
            if ch == SPIKE:
                if not solid(x, y + 1):
                    errs.append(f"spike at {x},{y} floats (no floor below)")
            if ch == MOVER_H:
                for dx in range(-4, 5):
                    if dx == 0:
                        continue
                    if 0 <= x + dx < L.w and rows[y][x + dx] != EMPTY:
                        errs.append(f"mover_h at {x},{y} blocked by {rows[y][x+dx]} at {x+dx}")
                        break
            if ch == MOVER_V:
                for dy in range(-3, 4):
                    for dx in (-1, 0, 1):
                        if dx == 0 and dy == 0:
                            continue
                        if 0 <= x + dx < L.w and 0 <= y + dy < H and rows[y + dy][x + dx] != EMPTY:
                            errs.append(f"mover_v at {x},{y} blocked at {x+dx},{y+dy}")
                            break

    # spike fairness: never at a gap landing spot, never right before a pit
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != SPIKE:
                continue
            floor = y + 1
            for dx in (-3, -2, -1):
                if x + dx >= 0 and not solid(x + dx, floor):
                    errs.append(f"spike at {x},{y} sits in the landing zone after a gap")
                    break
            for dx in (1, 2, 3):
                if x + dx < L.w and not solid(x + dx, floor):
                    errs.append(f"spike at {x},{y} is within {dx} tiles of a pit edge")
                    break

    # start / goal floors
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in (START, GOAL) and not (solid(x, y + 1) or solid(x, y + 2)):
                errs.append(f"{ch} at {x},{y} has no floor")

    # gap analysis: for each column find the walkable surface; any void run
    # between two surfaces wider than 4 tiles must contain a mover or lava-free
    # mover marker nearby.
    surfaces = []
    for x in range(L.w):
        top = None
        for y in range(H):
            if solid(x, y):
                top = y
                break
        surfaces.append(top)
    x = 0
    while x < L.w:
        if surfaces[x] is None:
            start = x
            while x < L.w and surfaces[x] is None:
                x += 1
            end = x - 1
            gap = end - start + 1
            if gap > 4:
                window = "".join(rows[y][start:end + 1] for y in range(H))
                if MOVER_H not in window and MOVER_V not in window:
                    errs.append(f"gap {start}..{end} is {gap} tiles wide with no mover")
        else:
            x += 1

    # height steps between adjacent columns (ignore gaps)
    for x in range(L.w - 1):
        a, b = surfaces[x], surfaces[x + 1]
        if a is not None and b is not None and abs(a - b) > 3:
            errs.append(f"surface step of {abs(a-b)} tiles at column {x}")

    return errs


def java_escape(rows):
    return "\n".join(rows)


def emit(levels: list[Level]) -> str:
    parts = [
        "package com.example.anplomer;",
        "",
        "/**",
        " * GENERATED FILE — edit tools/make_levels.py and re-run it.",
        " *",
        " * Each level is a character grid, 15 rows tall:",
        " * <pre>",
        " *  #  ground     =  stone      ^  spikes     ~  lava      o  coin",
        " *  E  walker     B  flyer      M  h-mover    V  v-mover",
        " *  P  start      C  checkpoint G  goal       .  empty",
        " * </pre>",
        " */",
        "public final class Levels {",
        "",
        "    private Levels() {",
        "    }",
        "",
    ]
    for i, L in enumerate(levels, start=1):
        parts.append(f"    /** Level {i}: {L.name}, par {L.par}s. */")
        parts.append(f"    public static final String LEVEL_{i} = \"\"\"")
        parts.extend("        " + r for r in L.rows())
        parts.append('        """;')
        parts.append("")
    parts.append("    public static final String[] TEXT = {")
    parts.extend(f"            LEVEL_{i}," for i in range(1, len(levels) + 1))
    parts.append("    };")
    parts.append("")
    parts.append("    public static final int[] PAR = {")
    parts.extend(f"            {L.par},   // {L.name}" for L in levels)
    parts.append("    };")
    parts.append("}")
    return "\n".join(parts) + "\n"


def main():
    check_only = "--check" in sys.argv
    levels = [level1(), level2(), level3()]
    ok = True
    for L in levels:
        errs = validate(L)
        n_coins = "".join(L.rows()).count(COIN)
        n_walk = "".join(L.rows()).count(WALKER)
        n_fly = "".join(L.rows()).count(FLYER)
        n_mov = "".join(L.rows()).count(MOVER_H) + "".join(L.rows()).count(MOVER_V)
        print(f"[{L.name}] {L.w}x{H} par={L.par}s coins={n_coins} walkers={n_walk} "
              f"flyers={n_fly} movers={n_mov}")
        for e in errs:
            ok = False
            print(f"   ERROR: {e}")
    if not ok:
        print("validation FAILED")
        return 1
    print("validation OK")
    if not check_only:
        os.makedirs(os.path.dirname(OUT), exist_ok=True)
        with open(OUT, "w") as f:
            f.write(emit(levels))
        print(f"wrote {os.path.normpath(OUT)}")
    if "--print" in sys.argv:
        for L in levels:
            print(f"--- {L.name} ---")
            print("\n".join(L.rows()))
    return 0


if __name__ == "__main__":
    sys.exit(main())
