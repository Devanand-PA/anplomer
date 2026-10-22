# Anplomer — tutorial and component guide

Anplomer is a small side-scrolling platformer. This document has two halves:

1. **Playing the game** — what every screen, chip, pickup and rule does.
2. **How it is built** — what each source component is responsible for, and how
   to change it safely.

Everything below matches the code in `app/src/main/java/com/example/anplomer/`.
When a number is quoted it comes from `Constants.java`, `Upgrades.java` or
`Levels.java`, so it is the real tuning value rather than documentation drift.

---

## 1. Playing the game

### 1.1 The goal

Run right, jump the gaps, reach the glowing **goal flag** at the end of the
level. You start with **3 lives** (more with the `EXTRA LIVES` upgrade). Losing
all lives ends the run and sends you back to the menu; your *career* keeps the
level it had reached, so `CONTINUE` picks up there.

There is no clock that kills you. Each level has a **par time** (see the level
table in §1.11); every second you finish under par is worth bonus points.

### 1.2 Controls

| Action | Touch | Keyboard |
| --- | --- | --- |
| Run left / right | drag the circle-pad at the bottom left (analogue: half tilt walks, full tilt sprints) | `←` / `→` or `A` / `D` |
| Jump | drag the pad up, up-left or up-right — the jump fires as the drag crosses the up threshold and steers in the same gesture | `↑`, `W` or `Space` |
| Short hop | drag the pad down while rising | release the key early |
| Jump (button) | the round button at the bottom right also jumps | — |
| Pause | pause button, top-right | `P` or `Esc` |
| Restart the level | pause menu | `R` |
| Mute / unmute | speaker button, top-right | `M` |
| Confirm / continue | tap the screen | `Enter` or `Space` |

Jump height is variable: release early, or pull the pad down, for a shorter hop.
The game also forgives small timing mistakes — there is a short **coyote time**
after you step off a ledge and a short **jump buffer** if you press jump just
before landing.

The pad is a real analogue stick, not four buttons: it owns the finger that
lands inside its ring (even if the drag leaves the ring), it has a small dead
zone so a resting thumb does not drift, and the up/down gestures use hysteresis
so wobbling on a threshold cannot stutter a jump. The chevrons light up as the
knob approaches them.

The size of the on-screen arrows and jump button is adjustable in
`SETTINGS → MOVE BUTTON SIZE`.

### 1.3 The heads-up display

```
┌──────────────┐ ┌───────────┐      ┌─ LEVEL 4 ─┐ ┌────────┐
│ SCORE        │ │  ⬤ 12/66  │      │   1:23    │ │ ⬤ 186 │
│ 015800       │ │   coins   │      └───────────┘ │ WALLET │
└──────────────┘ └───────────┘                    └────────┘
 ♥ ♥ ♥  +1            (hearts = lives, "+n" only above five)
 ┌────────┐┌────────┐┌────────┐
 │ S 9.4s ││ A 5.1s ││ J 3.0s │   ← active power-up timers
 └────────┘└────────┘└────────┘
 ◈ ◈                              ← shield pips (charges)
```

| Element | Meaning |
| --- | --- |
| **SCORE** | points for this run (see §1.5) |
| **coin chip** | coins collected *in this level* out of the level's total |
| **LEVEL n / time** | which level you are on and elapsed time |
| **WALLET chip** | coins banked in the active career (spendable in the store) |
| **hearts** | remaining lives; grey hearts are spent ones |
| **power-up chips** | one per active timed effect, counting down |
| **shield pips** | one per shield charge you are carrying |

When the wallet chip would not fit beside the level panel (narrow screens) it
moves under the score panel instead.

### 1.4 What is in a level

| Thing | Looks like | Rule |
| --- | --- | --- |
| Ground / platforms | grass-topped or stone blocks | stand on it; the top edge is solid |
| Spikes | metal spikes on the floor | touching them costs a life |
| Lava | glowing orange pool | instant death, even mid-jump |
| Pit | gap in the ground | falling below the level is death |
| Coin | spinning gold disc | +1 level coin, +1 career coin, +100 points |
| Walker (`E`) | red blob with horns | patrols; stomp it from above, touch it from the side and you die |
| Flyer (`B`) | winged blob | bobs up and down in a fixed range; stomping works too |
| Mover (`M` / `V`) | floating platform | rides horizontally / vertically; wait for it at pits |
| Checkpoint (`C`) | small flag | once touched, deaths respawn you here instead of the level start |
| Goal (`G`) | tall flag | ends the level — if it is unlocked (§1.8) |
| Power-up (`S H A J L`) | glowing icon on a pedestal | §1.7 |

Stomping an enemy pops it (+200 points) and bounces you up. Stomped enemies do
not respawn during that level, and neither do collected power-ups.

### 1.5 Scoring and coins

| Event | Points | Career coins |
| --- | --- | --- |
| Coin | +100 | +1 |
| Stomp an enemy | +200 | — |
| Collect a power-up | +150 | — |
| Finish every coin in a level | +1000 | — |
| Each second under par time | +10 | — |
| Clear a level | — | +8 |
| Clear it with every coin | — | +12 extra |

Points are per run and feed `BEST` on the career screen. Career coins are the
wallet you spend in the store, and they persist between runs.

### 1.6 Lives, death and respawn

* One life is spent per death: spikes, lava, falling out of the level, or an
  enemy touching you from the side.
* A **shield** (power-up or `STARTER SHIELD` upgrade) soaks exactly one hit and
  then grants about a second of harmless flashing.
* You respawn after a short delay at the last **checkpoint**, or at the level
  start if you have not reached one.
* At zero lives the run ends. `CONTINUE` restarts from the career's saved level
  with a fresh set of lives; score for the new run starts again at zero, while
  `BEST` keeps the record.

### 1.7 Power-ups

Five pickups, each marked with a letter. Timed ones are stretched by the
`POWER TIMER` upgrade (+15% per level).

| Glyph | Name | Effect | Duration |
| --- | --- | --- | --- |
| `S` | Speed | run speed ×1.45 | 12 s × power multiplier |
| `H` | Shield | +1 shield charge (max 3) | until it soaks a hit |
| `A` | Magnet | pulls nearby coins toward you | 10 s × power multiplier |
| `J` | Double jump | one extra jump while airborne | 15 s × power multiplier |
| `L` | Extra life | +1 life immediately | permanent |

The magnet radius is 300 world units, +90 per `COIN MAGNET` upgrade level.

### 1.8 Game modes

Toggle with `MODE:` on the menu.

* **CLASSIC** — reach the goal.
* **COIN COLLECTOR** — the goal stays shut until you have collected **60% of the
  level's coins** (rounded up). The HUD shows a goal chip that turns from orange
  (`locked`) to green (`open`). Bumping the locked gate just flashes the chip;
  you are never trapped.

### 1.9 Careers

Open with `CAREER` on the menu. A career is a player profile: name, colour,
wallet, level progress and lifetime stats. Up to **6** careers can exist.

| Button | What it does |
| --- | --- |
| tap a row | make that career active (highlighted gold, marked `ACTIVE`) |
| `NEW CAREER` | creates and activates a fresh career at level 1 with an empty wallet |
| `DELETE` | two-tap delete (the second tap confirms); the last career can never be deleted |

Each career keeps its **own** coins, unlocks, upgrades and best score, so two
players on the same phone never overwrite each other's progress.

### 1.10 The store

Open with `STORE`. Spending happens on the **active career's** wallet only, and
upgrades are permanent for that career (they are not consumables).

| Upgrade | Effect per level | Max | Prices |
| --- | --- | --- | --- |
| `SPEED BOOTS` | +4% run speed | 5 | 25, 45, 65, 85, 105 |
| `JUMP SPRINGS` | +3% jump height | 5 | 25, 45, 65, 85, 105 |
| `EXTRA LIVES` | +1 starting life | 3 | 60, 120, 180 |
| `COIN MAGNET` | +90 px magnet radius | 3 | 40, 75, 110 |
| `POWER TIMER` | +15% power-up duration | 4 | 35, 65, 95, 125 |
| `STARTER SHIELD` | +1 shield charge at level start | 2 | 90, 180 |

A row is green when you can afford the next level, grey when you cannot, and
shows `MAX LEVEL` once fully bought. The pip strip shows how many levels you own.

**Suggested order of purchase:** `EXTRA LIVES` first (it converts to more
attempts per run), then `SPEED BOOTS`, then `POWER TIMER`, then `STARTING
SHIELD`. `COIN MAGNET` pays for itself quickly on levels 6–8 where coins are
spread out.

### 1.11 The levels

| # | Name | Width (tiles) | Par | Coins | Enemies (walk + fly) | Movers | Power-ups |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Emerald Meadow | 152 | 50 s | 55 | 6 + 1 | 0 | 1 |
| 2 | Sunset Ruins | 170 | 65 s | 58 | 7 + 4 | 4 | 1 |
| 3 | Moonlit Peak | 190 | 80 s | 55 | 5 + 6 | 4 | 1 |
| 4 | Crystal Caverns | 195 | 90 s | 66 | 4 + 0 | 4 | 4 |
| 5 | Ember Forge | 210 | 100 s | 70 | 6 + 2 | 5 | 4 |
| 6 | Windy Bluffs | 239 | 115 s | 78 | 3 + 8 | 4 | 4 |
| 7 | Frozen Spire | 240 | 120 s | 77 | 8 + 3 | 5 | 4 |
| 8 | Starfall Summit | 255 | 130 s | 75 | 8 + 4 | 5 | 5 |

Difficulty rises through wider gaps, more lava, more movers and faster enemy
placement, not through tighter input windows. Each level has its own palette,
so the new area is recognisable at a glance.

### 1.12 Saving

Progress saves itself: level unlocks, career wallets, upgrades and settings are
written whenever they change and when the app pauses. The format is version 3
and holds the whole career list plus the active career. Older version 1 and 2
saves are migrated automatically into a single career called `PLAYER 1`.
`RESET SAVE` on the menu wipes everything back to a fresh install.

### 1.13 Settings

| Setting | Effect |
| --- | --- |
| `MUSIC VOLUME` | 0–100%; applies immediately while you drag |
| `MOVE BUTTON SIZE` | scales the on-screen arrows and jump button |
| `RESET SAVE` | (menu, not settings) erases careers and settings |

Sound can be muted at any time with the speaker button or `M`; the mute state
is remembered.

---

## 2. How the game is built

The project deliberately splits "the game" from "the screen". Everything that
decides what happens is plain Java with no Android imports, which is why the
headless tests can play the whole game on a desktop JVM.

```
                 ┌──────────── GameView (Android) ─────────────┐
 touch/keys ───▶ │  SurfaceView loop, paced to 16.67 ms        │
                 │        │                    ▲               │
                 │        ▼                    │               │
                 │     Game.update(dt)     Renderer.draw(...)  │
                 │  (fixed 60 Hz steps)        │               │
                 └────────┬────────────────────┴───────────────┘
                          │
        ┌─────────────────┼──────────────────────────────┐
        ▼                 ▼                              ▼
   Level / Player    SaveState / Profile          SoundManager
   Enemy / Platform  Upgrades / PowerUp           (SoundSink)
```

### 2.1 `Game` — the state machine

`Game` owns the rules and holds every piece of run state: score, lives, timers,
power-up clock, wallet, particles, camera, the loaded `Level` and the
`Profile` list.

States: `MENU`, `PLAYING`, `PAUSED`, `LEVEL_CLEARED`, `GAME_OVER`, `WIN`,
`SETTINGS`, `CAREER`, `STORE`. `update(dt)` switches on the state and
`Renderer.draw` mirrors it. Buttons are `Game.UiButton` rects that `Game`
rebuilds on `resize()` and whenever the state changes; touch handling is a
rect hit test, so the renderer never invents geometry — it draws the rects the
game laid out. That is why every label is measured and shrunk to fit its rect
(see §2.7).

The per-frame order in `updatePlaying` is: input → mode rules → player physics →
platforms → enemies → pickups → checkpoints → goal → death bookkeeping →
effects.

### 2.2 `Level` — the tile grid

A level is an array of strings, one string per row, one character per tile
(`Constants.TILE = 64` world units). `Level` parses those rows once into
collision-friendly form: solid runs, spike and lava tiles, coin list, enemy
spawns, mover platforms, checkpoints, power-up spawns, start and goal.

Glyph legend:

| Glyph | Meaning | Glyph | Meaning |
| --- | --- | --- | --- |
| `.` | empty | `P` | player start |
| `#` | ground | `C` | checkpoint |
| `=` | stone platform | `G` | goal |
| `^` | spikes | `o` | coin |
| `~` | lava | `E` / `B` | walker / flyer |
| `M` / `V` | horizontal / vertical mover | `S H A J L` | speed, shield, magnet, jump, life |

`Levels.java` is **generated** by `tools/make_levels.py`; edit the Python, never
the Java. The generator validates every level (gaps jumpable, spikes not on a
pit edge, movers clear of landing zones, power-ups reachable in the air) and
refuses to write a level that breaks the rules.

### 2.3 `Player` — movement

Fixed-step physics with `RUN_SPEED 380`, `GRAVITY 3200`, `JUMP_VELOCITY 1260`,
`MAX_FALL 1700`, plus acceleration/friction so the character has weight. Jump
chaining: `COYOTE_TIME 0.10 s`, `JUMP_BUFFER 0.13 s`, `JUMP_CUT 0.45` on early
release. The career upgrades feed in as multipliers (`speedMul`, `jumpMul`) and
the double-jump power-up adds `airJumps`.

### 2.4 `Enemy` and `MovingPlatform`

`Enemy` is a walker (patrols, turns at walls and ledges) or a flyer (bobs in a
sine around its spawn). Both are stompable from above and lethal from the side;
`squashTimer` drives the pop animation and the +200 score.

`MovingPlatform` slides back and forth on a sine at `PLATFORM_SPEED 1.15 rad/s`,
range ±160 units horizontally or ±132 vertically, and carries the player along
when they stand on it. Its `dx/dy` delta is what moves the rider, so the platform
and the player can never drift apart.

### 2.5 `Camera`

Follows the player with a smoothed look-ahead and a shake impulse for impacts
and death. The camera is clamped to the level bounds, so the player is always
inside a valid frame — the smoke test asserts exactly that.

### 2.6 `GameView` — the Android driver

`SurfaceView` with a dedicated thread. The loop accumulates real time and steps
the simulation in fixed 1/60 s slices, then draws once per frame, paced to
`FRAME_NS = 16_666_667`. If a frame is late it renders the next one immediately;
if the surface is not ready or the app is backgrounded it skips canvas work
entirely rather than burning battery. Touch and key events become small intent
flags on the input queue (`consumeJump`, `consumePause`, …) so the simulation
never sees platform types.

### 2.7 `Renderer` — one draw pass

The renderer is stateless apart from caches; it reads `Game` and paints.

* **Culling** — only tiles/coins/enemies inside the camera rect are visited.
* **Batching** — adjacent solid tiles are merged into horizontal runs, so a
  ground floor is one rounded rect instead of one per tile. This is the single
  biggest draw-call saver: a full screen went from ~680 to ~374 draw calls.
* **Caching** — the sky gradient, HUD strings (score, coins, time, wallet,
  power-up seconds, menu labels) and text widths are computed once per change,
  never per frame. Trig is a 2048-entry table, hashes a 16384-entry table.
* **No allocation in the hot path** — one `Paint`/`Path` set reused, no
  `String.format`, no float boxing, no temporaries inside the per-item loops.
* **The pad** is drawn from the same numbers that hit-test it: ring, inner
  well, knob at the current deflection and four chevrons that brighten as the
  thumb gets close.
* **Text fitting** — every label drawn inside a box (buttons, rows, chips,
  panels, overlays) goes through `fitSize()`, which measures the string with the
  real `Paint.measureText` and shrinks the type until it fits the rect's inner
  width. The box geometry comes from `Game`, which cannot know font metrics, so
  fitting at draw time is what keeps labels inside their borders on every
  screen size and font.

Eight palettes (one per level) pick sky, ground, stone, spike, lava, coin and
accent colours; the career/store screens reuse the same palette under a scrim.

### 2.8 `SaveState`, `Profile`, `Upgrades`

* `SaveState` (with `SaveStore` for I/O) reads/writes a single string blob at
  version 3:
  `3|sound|music|buttons|mode|active|<profile>;<profile>;…` where a profile is
  `name,color,coins,level,unlocked,cleared,coinsCollected,bestScore,stomps,deaths,playMillis,u0…u5`.
  It clamps everything on load, migrates v1/v2, and falls back to defaults on
  garbage.
* `Profile` is one career: wallet, unlock pointer, stats and the six upgrade
  levels, with derived getters (`speedMul`, `jumpMul`, `startLives`,
  `powerTimeMul`, `magnetRadius`, `startShield`) that the simulation reads.
* `Upgrades` is pure data: ids, names, descriptions, `MAX`, base price and price
  step. `Profile.upgradeLevel(id)`, `canAfford(id)` and `nextPrice(id)` drive both
  the store rows and `Game.buyUpgrade`.

### 2.9 Small pieces

| File | Responsibility |
| --- | --- |
| `PowerUp` | the pickup in the world: type, position, generous overlap radius |
| `Coin` | a coin's position and spin phase |
| `Particles` | sparkles, dust, coin bursts, confetti — fixed 520-slot parallel arrays, zero allocation |
| `InputController` | turns key and multi-touch events into edge-triggered intents (`consumeJump`, `consumePause`, …) plus the analogue `moveAxis` and the pad's `padX/padY/padActive/jumpCut`; the pad pointer is captured on touch-down and released on lift; synchronized because UI and render threads both touch it |
| `Controls` | geometry of the circle-pad, jump, pause and mute, recomputed on resize so drawing and hit-testing agree (the pad is drawn at exactly its hit radius) |
| `SoundSink` / `SoundManager` | intent interface (`JUMP`, `COIN`, `POWERUP`, `PURCHASE`, …) plus the Android `SoundPool` implementation; the simulation only knows the interface |
| `SaveStore` | persistence interface, so the pure-Java game never imports `SharedPreferences`; tests inject memory or file stores |
| `FrameDumper` | debug-only: writes finished frames to app storage so on-device pixels can be inspected without `adb screencap` (which freezes this app's loop) |
| `Constants` | every tuning number in one place |
| `Levels` | generated level data |

### 2.10 Tests and benchmarks

| Tool | What it proves |
| --- | --- |
| `tools/HeadlessTest.java` | a bot plays all eight levels to the goal, plus flows: menu/pause/resize, save round-trip and migration, coin-collector gate, all five power-ups (timers, shield soak, magnet pull, respawn persistence), careers (create/select/delete/isolation), the store (prices, refusal, max clamp, effects reaching the simulation), and the level-clear wallet payout |
| `tools/RendererSmoke.java` | draws every state × palette × screen size (including 1×1), asserts geometry is finite, the sky fill is opaque, the camera keeps the player in frame, and the career/store/HUD-with-power-ups screens draw; with `DUMP_SHOTS=dir` it writes PNGs |
| `tools/Bench.java` | per-frame draw-call and time counts for scripting performance regressions |

Run them:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./tools/run_headless_test.sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./tools/run_bench.sh 800
DUMP_SHOTS=$PWD/shots/new JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./tools/run_headless_test.sh
```

The renderer runs on the JVM because `tools/stub` provides small
`android.graphics` fakes that record draw calls and rasterise for the dumps.

---

## 3. Recipes

### Add a level

1. Add the rows (15 strings, same width) to `LEVELS` in `tools/make_levels.py`,
   with the palette name and par time.
2. Run `python3 tools/make_levels.py`. It validates the level and rewrites
   `Levels.java`.
3. Run the headless test: the bot must reach the goal (`level N OK`). If it
   dies, the failure message names the tile coordinates.

### Add a power-up type

1. Add the enum value in `PowerUp.Type`, its glyph constant in `Level`, and its
   branch in `Level.power(char)`.
2. Handle the effect in `Game.collectPowerUp` and the HUD in `Renderer.drawHud`.
3. Add a glyph to the Python `POWER` map so levels can place it.

### Add or retune an upgrade

1. Add the id to `Upgrades` (append — ids are save-file indices), plus `NAME`,
   `DESC`, `MAX`, `BASE_PRICE`, `PRICE_STEP` entries.
2. Add the derived getter in `Profile` and use it in `Game`.
3. The store row appears automatically; add the effect assertion in
   `HeadlessTest.storeFlow`.

### Retune difficulty

Change `Player` constants or the level generator's parameters, then re-run the
validator and the bot. The bot is deliberately naive (it runs right and jumps at
the last moment), so if it can finish a level, the level has no unavoidable
death.

### Add a palette

Append to `Renderer.PALETTES` (sky top/bottom, ground, stone, spike, lava, coin,
accent). The level index picks the palette, clamped to the last one.

---

## 4. Known limits

* The bot only moves right and cannot back up, so the generator keeps plain gaps
  at three tiles, lava crossings exactly nine tiles wide with a mover at the
  centre, and no walkers on the same segment as spikes or a pit landing. That is
  a testability rule, not a gameplay requirement.
* The PNG dumps in `shots/` are rasterised by the JVM stub, so they are evidence
  for layout and colour, not pixel-exact device screenshots.
* `Renderer` is one ~112 KB class by design (single draw pass, no per-frame
  object churn); it is organised by section with a contract table in
  `docs/CONTRACT.md`.
