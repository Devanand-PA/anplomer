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
POWER = "SHAJL"            # speed, shield, magnet, double jump, extra life


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

    def power(self, x, y, kind):
        """Place a power-up glyph; `kind` must be one of POWER."""
        assert kind in POWER, f"unknown power-up glyph {kind!r}"
        self.put(x, y, kind)

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
    L.power(103, 10, "H")
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
    L.power(33, 10, "J")
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
    L.power(29, 9, "A")
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
# LEVEL 4 — Crystal Caverns: stone shelves, vertical movers, long caverns
# ----------------------------------------------------------------------
def level4() -> Level:
    L = Level(195, 90, "Crystal Caverns")
    L.seg(0, 17, 12)
    L.start(2, 12)
    L.coins(range(6, 11), 10)
    L.walker(13, 12)
    L.power(15, 10, "H")                 # shield before the first gap
    L.arc(18, 20, 10)

    L.seg(21, 37, 12)
    L.stone(24, 27, 9)
    L.coins(range(24, 28), 8)
    L.spikes([34], 12)
    L.coins(range(35, 38), 10)

    L.lava(38, 46, 13)
    L.mover_h(42, 11)
    L.coins([39, 42, 45], 9)

    L.seg(47, 62, 12)
    L.walker(49, 12)
    L.stone(53, 56, 9)
    L.coins(range(53, 57), 8)
    L.arc(60, 62, 10)

    L.seg(66, 82, 12)
    L.power(69, 10, "A")                 # magnet over open ground
    L.spikes([76, 77], 12)
    L.coins(range(78, 83), 10)

    L.seg(86, 102, 12)
    L.checkpoint(90, 12)
    L.stone(94, 97, 9)
    L.coins(range(94, 98), 8)

    L.lava(103, 111, 13)
    L.mover_h(107, 11)
    L.mover_v(107, 4)
    L.coins([104, 107, 110], 9)

    L.seg(112, 128, 12)
    L.coins(range(118, 123), 10)
    L.spikes([125], 12)
    L.arc(129, 131, 10)

    L.seg(132, 152, 10)
    L.stone(136, 139, 7)
    L.coins(range(136, 140), 6)
    L.power(142, 8, "J")                 # double jump on the high shelf
    L.spikes([149], 10)
    L.coins(range(150, 153), 8)

    L.lava(153, 161, 13)
    L.mover_h(157, 11)
    L.coins([154, 157, 160], 9)

    L.seg(162, 178, 12)
    L.checkpoint(166, 12)
    L.coins(range(169, 174), 10)
    L.walker(176, 12)
    L.arc(179, 181, 10)

    L.seg(182, 194, 10)
    L.power(185, 8, "L")                 # extra life before the summit
    L.walker(188, 10)
    L.coins(range(190, 193), 8)
    L.goal(192, 10)
    return L


# ----------------------------------------------------------------------
# LEVEL 5 — Ember Forge: lava channels, spikes and ferry platforms
# ----------------------------------------------------------------------
def level5() -> Level:
    L = Level(210, 100, "Ember Forge")
    L.seg(0, 15, 12)
    L.start(2, 12)
    L.coins(range(6, 10), 10)
    L.walker(12, 12)
    L.arc(16, 18, 10)

    L.seg(19, 34, 12)
    L.spikes([25, 26], 12)
    L.coins(range(27, 31), 10)

    L.lava(35, 43, 13)
    L.mover_h(39, 11)
    L.coins([36, 39, 42], 9)

    L.seg(44, 59, 12)
    L.walker(46, 12)
    L.stone(50, 53, 9)
    L.coins(range(50, 54), 8)
    L.arc(57, 59, 10)

    L.seg(63, 79, 12)
    L.power(66, 9, "S")                  # speed boots on clear flat ground
    L.coins(range(69, 74), 10)
    L.walker(76, 12)
    L.arc(80, 82, 10)

    L.seg(83, 99, 12)
    L.checkpoint(87, 12)
    L.spikes([90, 91], 12)
    L.coins(range(92, 97), 10)
    L.flyer(98, 6)

    L.lava(100, 108, 13)
    L.mover_h(104, 11)
    L.mover_v(104, 4)
    L.coins([101, 104, 107], 9)

    L.seg(109, 125, 12)
    L.walker(112, 12)
    L.stone(116, 119, 9)
    L.coins(range(116, 120), 8)
    L.power(122, 9, "H")
    L.arc(123, 125, 10)

    L.seg(129, 145, 12)
    L.spikes([135, 136], 12)
    L.coins(range(137, 142), 10)
    L.flyer(143, 5)

    L.lava(146, 154, 13)
    L.mover_h(150, 11)
    L.coins([147, 150, 153], 9)

    L.seg(155, 171, 12)
    L.checkpoint(159, 12)
    L.walker(161, 12)
    L.stone(163, 166, 9)
    L.coins(range(163, 167), 8)
    L.power(169, 9, "A")
    L.arc(172, 174, 10)

    L.seg(175, 191, 10)
    L.coins(range(181, 186), 8)
    L.spikes([188], 10)

    L.lava(192, 200, 13)
    L.mover_h(196, 11)
    L.coins([193, 196, 199], 9)

    L.seg(201, 209, 12)
    L.power(203, 9, "L")
    L.walker(205, 12)
    L.coins(range(206, 209), 10)
    L.goal(207, 12)
    return L


# ----------------------------------------------------------------------
# LEVEL 6 — Windy Bluffs: flyer swarms, coin runs, wide gaps
# ----------------------------------------------------------------------
def level6() -> Level:
    L = Level(239, 115, "Windy Bluffs")
    L.seg(0, 16, 12)
    L.start(2, 12)
    L.coins(range(6, 11), 10)
    L.flyer(14, 6)
    L.arc(17, 19, 10)

    L.seg(20, 36, 12)
    L.walker(23, 12)
    L.stone(26, 29, 9)
    L.coins(range(26, 30), 8)
    L.power(33, 10, "J")
    L.arc(37, 39, 10)

    L.seg(40, 56, 11)
    L.flyer(48, 5)
    L.coins(range(50, 55), 9)

    L.lava(57, 65, 13)
    L.mover_h(61, 11)
    L.coins([58, 61, 64], 9)

    L.seg(66, 82, 12)
    L.checkpoint(70, 12)
    L.flyer(74, 6)
    L.spikes([77, 78], 12)
    L.coins(range(79, 83), 10)
    L.arc(83, 85, 10)

    L.seg(86, 102, 12)
    L.stone(93, 96, 9)
    L.coins(range(93, 97), 8)
    L.flyer(100, 4)

    L.seg(106, 122, 11)
    L.walker(109, 11)
    L.coins(range(112, 117), 9)
    L.flyer(119, 7)

    L.lava(123, 131, 13)
    L.mover_h(127, 11)
    L.mover_v(127, 4)
    L.coins([124, 127, 130], 9)

    L.seg(132, 148, 12)
    L.checkpoint(136, 12)
    L.stone(142, 145, 9)
    L.coins(range(142, 146), 8)
    L.power(147, 10, "A")
    L.arc(149, 151, 10)

    L.seg(152, 168, 12)
    L.flyer(155, 6)
    L.coins(range(158, 163), 10)

    L.lava(169, 177, 13)
    L.mover_h(173, 11)
    L.coins([170, 173, 176], 9)

    L.seg(178, 194, 12)
    L.spikes([184, 185], 12)
    L.coins(range(186, 191), 10)
    L.flyer(193, 5)

    L.arc(195, 197, 10)

    L.seg(198, 214, 10)
    L.power(201, 8, "L")
    L.coins(range(207, 212), 8)

    L.arc(215, 217, 10)

    L.seg(218, 238, 10)
    L.power(221, 8, "L")
    L.walker(224, 10)
    L.coins(range(227, 232), 8)
    L.flyer(236, 5)
    L.goal(233, 10)
    return L


# ----------------------------------------------------------------------
# LEVEL 7 — Frozen Spire: stair shelves, tight hops, ice-cold ferries
# ----------------------------------------------------------------------
def level7() -> Level:
    L = Level(240, 120, "Frozen Spire")
    L.seg(0, 17, 12)
    L.start(2, 12)
    L.coins(range(6, 11), 10)
    L.walker(14, 12)
    L.power(16, 10, "H")
    L.arc(18, 20, 10)

    L.seg(21, 37, 12)
    L.stone(24, 27, 9)
    L.coins(range(24, 28), 8)
    L.walker(31, 12)
    L.spikes([33, 34], 12)
    L.arc(38, 40, 10)

    L.seg(41, 57, 12)
    L.walker(44, 12)
    L.stone(48, 51, 9)
    L.coins(range(48, 52), 8)
    L.coins(range(54, 58), 10)

    L.lava(58, 66, 13)
    L.mover_h(62, 11)
    L.coins([59, 62, 65], 9)

    L.seg(67, 83, 12)
    L.checkpoint(71, 12)
    L.walker(74, 12)
    L.stone(78, 81, 9)
    L.coins(range(78, 82), 8)
    L.arc(84, 86, 10)

    L.seg(87, 103, 10)
    L.stone(91, 94, 7)
    L.coins(range(91, 95), 6)
    L.power(97, 8, "J")
    L.walker(100, 10)

    L.seg(107, 123, 12)
    L.flyer(110, 6)
    L.coins(range(113, 118), 10)
    L.spikes([120], 12)

    L.lava(124, 132, 13)
    L.mover_h(128, 11)
    L.mover_v(128, 4)
    L.coins([125, 128, 131], 9)

    L.seg(133, 149, 12)
    L.checkpoint(137, 12)
    L.walker(140, 12)
    L.stone(144, 147, 9)
    L.coins(range(144, 148), 8)
    L.power(148, 9, "A")
    L.arc(150, 152, 10)

    L.seg(153, 169, 12)
    L.spikes([159, 160], 12)
    L.coins(range(161, 166), 10)
    L.flyer(168, 5)

    L.lava(170, 178, 13)
    L.mover_h(174, 11)
    L.coins([171, 174, 177], 9)

    L.seg(179, 195, 12)
    L.stone(186, 189, 9)
    L.coins(range(186, 190), 8)
    L.spikes([192], 12)

    L.seg(199, 215, 12)
    L.walker(202, 12)
    L.coins(range(205, 210), 10)
    L.flyer(213, 4)

    L.lava(216, 224, 13)
    L.mover_h(220, 11)
    L.coins([217, 220, 223], 9)

    L.seg(225, 239, 12)
    L.power(228, 9, "L")
    L.walker(231, 12)
    L.coins(range(233, 238), 10)
    L.goal(237, 12)
    return L


# ----------------------------------------------------------------------
# LEVEL 8 — Starfall Summit: the finale, every hazard in one climb
# ----------------------------------------------------------------------
def level8() -> Level:
    L = Level(255, 130, "Starfall Summit")
    L.seg(0, 17, 12)
    L.start(2, 12)
    L.coins(range(6, 11), 10)
    L.walker(14, 12)
    L.power(16, 10, "H")
    L.arc(18, 20, 10)

    L.seg(21, 37, 12)
    L.stone(24, 27, 9)
    L.coins(range(24, 28), 8)
    L.walker(31, 12)
    L.spikes([33, 34], 12)
    L.arc(38, 40, 10)

    L.seg(41, 57, 11)
    L.walker(44, 11)
    L.stone(48, 51, 8)
    L.coins(range(48, 52), 7)
    L.power(55, 9, "S")                  # speed on a clean stretch
    L.coins(range(58, 62), 10)

    L.lava(62, 70, 13)
    L.mover_h(66, 11)
    L.coins([63, 66, 69], 9)

    L.seg(71, 87, 12)
    L.checkpoint(75, 12)
    L.spikes([81, 82], 12)
    L.coins(range(83, 88), 10)

    L.seg(91, 107, 12)
    L.flyer(94, 6)
    L.stone(97, 100, 9)
    L.coins(range(97, 101), 8)
    L.walker(104, 12)

    L.lava(108, 116, 13)
    L.mover_h(112, 11)
    L.mover_v(112, 4)
    L.coins([109, 112, 115], 9)

    L.seg(117, 133, 12)
    L.checkpoint(121, 12)
    L.walker(124, 12)
    L.coins(range(127, 132), 10)
    L.power(132, 9, "J")
    L.arc(134, 136, 10)

    L.seg(137, 153, 12)
    L.spikes([143, 144], 12)
    L.coins(range(145, 150), 10)
    L.flyer(152, 5)

    L.seg(157, 173, 11)
    L.walker(160, 11)
    L.stone(164, 167, 8)
    L.coins(range(164, 168), 7)
    L.flyer(171, 7)

    L.lava(174, 182, 13)
    L.mover_h(178, 11)
    L.coins([175, 178, 181], 9)

    L.seg(183, 199, 12)
    L.spikes([189, 190], 12)
    L.coins(range(191, 196), 10)
    L.power(198, 10, "A")

    L.seg(203, 219, 12)
    L.stone(210, 213, 9)
    L.coins(range(210, 214), 8)
    L.spikes([216], 12)

    L.lava(220, 228, 13)
    L.mover_h(224, 11)
    L.coins([221, 224, 227], 9)

    L.seg(229, 254, 12)
    L.power(232, 9, "L")
    L.walker(235, 12)
    L.coins(range(238, 243), 10)
    L.flyer(246, 5)
    L.walker(249, 12)
    L.goal(251, 12)
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
            if ch in (COIN, WALKER, FLYER, START, GOAL, CHECK, MOVER_H, MOVER_V, SPIKE) or ch in POWER:
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

    # power-ups must be within a jump of some walkable surface
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch not in POWER:
                continue
            reachable = False
            for dx in range(-3, 4):
                nx = x + dx
                if 0 <= nx < L.w and surfaces[nx] is not None:
                    if 0 <= surfaces[nx] - y <= 3:
                        reachable = True
                        break
            if not reachable:
                errs.append(f"power-up {ch} at {x},{y} is not within a jump of ground")

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
        " *  S  speed      H  shield     A  magnet     J  double jump",
        " *  L  extra life",
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
    levels = [level1(), level2(), level3(), level4(), level5(),
              level6(), level7(), level8()]
    ok = True
    for L in levels:
        errs = validate(L)
        n_coins = "".join(L.rows()).count(COIN)
        n_walk = "".join(L.rows()).count(WALKER)
        n_fly = "".join(L.rows()).count(FLYER)
        n_mov = "".join(L.rows()).count(MOVER_H) + "".join(L.rows()).count(MOVER_V)
        n_pow = sum("".join(L.rows()).count(c) for c in POWER)
        print(f"[{L.name}] {L.w}x{H} par={L.par}s coins={n_coins} walkers={n_walk} "
              f"flyers={n_fly} movers={n_mov} powerups={n_pow}")
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
