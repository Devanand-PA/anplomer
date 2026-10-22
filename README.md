# Anplomer

A complete, self-contained 2D platformer for Android. Eight hand-designed levels,
five power-ups, per-player career profiles with a coin store of permanent
upgrades, on-screen touch controls, parallax scenery, particles and procedural
sound. No third-party or AndroidX dependencies — plain Java 17 +
`SurfaceView`/`Canvas`.

```
app/src/main/java/com/example/anplomer/
├── Constants.java        tuning table (speeds, gravity, jump, timings, tile size)
├── Level.java            tile grid, solid/hazard queries, movers/enemies/coins
├── Levels.java           GENERATED level data (see tools/make_levels.py)
├── Controls.java         on-screen control + HUD geometry
├── InputController.java  key/pointer state, edge-triggered jump
├── Player.java           movement, coyote time, jump buffer, collision
├── Enemy.java            walkers (turn at walls/ledges) and sine flyers
├── MovingPlatform.java   horizontal / vertical movers that carry riders
├── Coin.java  Camera.java  Particles.java  SoundSink.java
├── PowerUp.java          pickup glyphs (speed, shield, magnet, double jump, life)
├── Profile.java          one career: name, colour, wallet, unlock, stats, upgrades
├── Upgrades.java         store catalogue: names, descriptions, prices, effects
├── SaveState.java        persisted prefs + every career profile (v3 pipe format)
├── SaveStore.java        persistence interface (SharedPreferences impl lives in GameView)
├── Game.java             state machine + simulation (no android.* imports)
├── Renderer.java         all drawing (vector art, no bitmaps)
├── GameView.java         SurfaceView + fixed-timestep loop thread + save store
├── MainActivity.java     fullscreen landscape shell
└── SoundManager.java     SoundPool effects
```

The simulation (`Game` and everything below it) is deliberately free of
`android.*` imports, so the whole game loop can be driven headlessly — that is
how the levels are regression-tested (see *Tests* below).

`Renderer` is the one class that touches `android.graphics`. To make it
testable off-device, `tools/stub/android/graphics` provides a minimal
`Canvas`/`Paint`/`Path`/`RectF`/`Shader`/`LinearGradient` stand-in that records
every call, rejects non-finite geometry and negative radii, and (for frame dumps)
rasterises into a `BufferedImage`.

## Home screen, saves and modes

The title screen is a real menu, not a "tap to play" poster:

* **START GAME** — fresh run at level 1.
* **CONTINUE • LEVEL n** — appears when a save exists; resumes on the saved
  level with fresh score and lives.
* **MODE: CLASSIC / COIN COLLECTOR** — in *Coin Collector* the goal flag is
  padlocked until ~60 % of the level's coins are collected; the HUD shows a
  `GOAL LOCKED` chip and bumping the flag flashes how many coins are missing.
* **CAREER** — the career screen: one row per profile (name, colour, wallet,
  unlocked levels), plus **NEW CAREER** and a two-tap **DELETE**. Up to six
  careers; the active one is saved and resumed.
* **STORE** — spend the active career's coins on six permanent upgrades:
  speed, jump, extra lives, magnet radius, power-up duration and a starting
  shield. Prices rise with each level bought; maxed rows read `MAX`.
* **HOW TO PLAY** — a one-screen tutorial overlay that names every component
  (controls, coins, enemies, hazards, checkpoints, the goal, careers, store and
  the five power-ups, with their icons). Reachable from the menu, closes with
  **BACK** or the system back key.
* **SOUND: ON / OFF** and a small **RESET SAVE** with a two-tap confirm.

Progress auto-saves through a `SaveStore` interface (`Game` passes plain data;
`GameView` plugs in a `SharedPreferences` blob). The level you are on is written
every time a level is entered, so **losing all lives restarts the last saved
level**, not level 1. Mode and sound choice persist too. `SaveState` encodes a
versioned, malformed-input-tolerant pipe string, and the headless suite covers
the whole flow (autosave, continue, retry-at-saved-level, mode gating).

## Tutorial

Two tutorials ship with the build:

* **In game** — the `HOW TO PLAY` button on the home screen opens a single
  overlay (`Game.State.HELP`) with a two-column component list and the five
  power-up icons. Every label is measured and shrunk to its panel, so it reads
  the same on a phone in landscape and on a 2400×1080 display.
* **On paper** — [`docs/TUTORIAL.md`](docs/TUTORIAL.md) is the full guide:
  playing the game (controls, HUD anatomy, coins and scoring, hazards, enemies,
  power-ups, modes, careers, store prices, level table, saving), then how each
  source component works (`Game`, `Level`, `Player`, `Renderer`, `SaveState`,
  the level generator, the test harnesses) and recipes for adding levels,
  power-ups, upgrades and palettes.

## Gameplay

| # | Level | Width | Par | Coins | Movers | New hazard mix |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Emerald Meadow | 152 | 50s | 55 | 0 | gentle gaps, walkers |
| 2 | Sunset Ruins | 170 | 65s | 58 | 4 | first lava ferries, flyers |
| 3 | Moonlit Peak | 190 | 80s | 55 | 4 | long ferries, bat swarms |
| 4 | Crystal Caverns | 195 | 90s | 66 | 4 | stone shelves, vertical movers |
| 5 | Ember Forge | 210 | 100s | 70 | 5 | lava channels, spike fields |
| 6 | Windy Bluffs | 239 | 115s | 78 | 4 | flyer swarms, wide gaps |
| 7 | Frozen Spire | 240 | 120s | 77 | 5 | stair shelves, tight hops |
| 8 | Starfall Summit | 255 | 130s | 75 | 5 | everything, longest climb |

Stomp enemies to bounce, grab coins, touch the flag to clear a level.
Checkpoints respawn you in place; three lives per level (more with the *Lives*
upgrade); running out of lives returns you to the last saved level (see
*Home screen, saves and modes*).

### Power-ups

Five pickups live in the air along each level and are drawn as glowing
glyphs with their own colour:

| Glyph | Power-up | Effect |
| --- | --- | --- |
| `S` | Speed | ×1.45 run speed for `POWERUP_TIME` (12 s, longer with the *Power* upgrade) |
| `H` | Shield | one free hit; the HUD shows shield pips |
| `A` | Magnet | pulls nearby coins in for 10 s (radius scales with the *Magnet* upgrade) |
| `J` | Double jump | one extra mid-air jump for 15 s |
| `L` | Extra life | +1 life immediately |

Power-ups survive a respawn (you do not have to re-collect the same one), each
level carries between one and five of them, and the HUD lists every active
timer with seconds remaining.

### Careers and the coin store

Every coin you pick up and every level you clear pays into the **active
career's wallet** (a level clear pays 8 coins, +12 more for collecting every
coin). The store turns that wallet into permanent, per-career upgrades:

| Upgrade | Effect per level | Max | Base price |
| --- | --- | --- | --- |
| Speed | +6 % run speed | 5 | 25 |
| Jump | +5 % jump velocity | 5 | 25 |
| Lives | +1 starting life | 3 | 60 |
| Magnet | +45 px pickup radius | 3 | 40 |
| Power | +15 % power-up duration | 4 | 35 |
| Shield | start each level with a shield | 2 | 90 |

Each tier costs more than the last, purchases persist in the save file, and
the store refuses a purchase you cannot afford (with a message, not a crash).
Careers are independent: separate wallet, separate unlocked level, separate
upgrade levels and lifetime stats (coins, level, best score, stomps, deaths,
play time).

### Controls (touch)

| Widget | Action |
| --- | --- |
| bottom-left ◀ ▶ | run left / right |
| bottom-right ● | jump (hold higher, tap shorter) |
| top-right ⏸ | pause / resume (also Android *Back*) |
| top-right 🔊 | mute / unmute |

The pause and mute discs are hit-tested at exactly the radius they are drawn
with, and a tap on them raises the same edge as their keyboard keys.

Keyboard is supported too: arrows/WASD, `Space`/`W`/`Up` to jump, `P`/`Esc` pause,
`M` mute, `R` restart level.

## Performance

The v1 loop locked the canvas, drew, and posted again as fast as the device
would go; a 60 Hz simulation was being rendered at whatever rate the GPU
allowed, and the frame budget was spent on thousands of draw calls.

What changed:

* **Paced loop.** `GameView` now targets `FRAME_NS = 16_666_667` and sleeps the
  remainder of each frame, and it skips `lockCanvas`/draw entirely while the
  surface is not ready or the host is paused. Idle menu frames no longer burn
  a full core.
* **Draw-call diet.** Sprites are drawn from cached geometry instead of
  rebuilding `Path` objects every frame; repeated shapes (coins, clouds,
  hills, tiles) share one path/paint state per frame; the parallax layers are
  pre-transformed instead of per-shape `save/restore`. On level 1 at
  2400×1080 the frame went from **679 to 374 draw calls** (worst level 487),
  and 1280×720 is at 300.
* **No per-frame allocation** in the steady-state player/camera/enemy update
  paths: index loops instead of iterators over hot lists, cached HUD label
  strings, and no `Math.pow` in the camera lerp (it is now
  `1 - 3.9·dt`, clamped).
* **Cheaper collision queries**: `Level` keeps counters for taken coins and
  power-ups so the HUD does not rescan the grid, and solid/hazard overlap tests
  reject by bounding box before touching the tile grid.

`tools/run_bench.sh` measures it headlessly (the stub `Canvas` records calls,
no device needed):

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./tools/run_bench.sh 600
# 2400x1080 L1   0.032 ms/frame   374 draws/frame
# 2400x1080 L8   0.026 ms/frame   487 draws/frame
# 1280x720  L1   0.020 ms/frame   300 draws/frame
# career overlay 0.027 ms/frame   352 draws/frame
```

## Build

Requires JDK 17 and an Android SDK with platform 34 and build-tools installed
(the path is read from `local.properties`).

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
GRADLE_USER_HOME=$PWD/.gradle-home \
./gradlew :app:assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

Install and launch:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.anplomer/.MainActivity
```

The debug build is signed with the project-local keystore in `.keystore/`
(the default `~/.android/debug.keystore` is not available in every sandbox).

## Level editing

`Levels.java` is generated — never edit it by hand.

```bash
python3 tools/make_levels.py            # regenerate + validate
python3 tools/make_levels.py --check    # validate only
python3 tools/make_levels.py --print    # ASCII preview of every level
```

The glyphs are `#` ground, `=` stone, `~` lava, `^` spike, `o` coin, `g` walker,
`b` flyer, `M`/`V` horizontal/vertical mover, `P` start, `C` checkpoint,
`G` goal — plus the power-ups `S` speed, `H` shield, `A` magnet, `J` double
jump and `L` extra life.

The generator enforces fairness rules (spikes clear of pit edges and landing
zones, no flyer inside a required jump arc, no rise taller than the jump can
clear, every platform reachable, every power-up within a jump of walkable
ground) and refuses to overwrite a glyph silently.

## Tests

`tools/run_headless_test.sh` runs both headless suites — no Android SDK or
device required:

1. **`RendererSmoke`** (`tools/RendererSmoke.java`) — executes the real
   `Renderer` against the `android.graphics` stub while the real simulation
   runs underneath. It draws every game state × palette × screen size (12
   sizes, down to 1×1), 3000 frames of live gameplay with rendering, forced
   deaths → respawn → game over, the level-clear/win flow and the pause
   overlay. The stub throws on non-finite coordinates or negative radii, so a
   bad draw call fails the test instead of silently drawing garbage.

   It also contains the **camera framing guard**: after the simulation settles,
   it asserts the player's projected screen position is inside the viewport at
   every tested size and at the level end. This is the regression test for a
   real bug found by looking at an on-device screenshot: `Camera.update()`
   clamped the *lerped* position instead of the follow target, so the camera
   collapsed onto the top edge of the level and pushed the player and the floor
   off-screen.

   `DUMP_FRAME=<path> ./tools/run_headless_test.sh` additionally rasterises one
   frame with `java.awt` (run with `-Djava.awt.headless=true`, which the script
   sets) so a frame can be inspected without a device.

   **Caveat:** the `java.awt` rasteriser is only trustworthy for geometry, not
   for pixels. Its `Graphics2D` can report device bounds of
   `Integer.MAX_VALUE`, in which case every `fillRect` is silently discarded —
   a frame appears "unpainted" when it is actually fine. Do not use it to judge
   whether the game paints correctly; use on-device frames (below).

2. **`HeadlessTest`** (`tools/HeadlessTest.java`) — drives the real simulation
   with a reactive bot (run, jump at ledges, verify landing spots before
   committing, wait for ferry platforms, stomp or avoid enemies) and asserts
   each level can be finished.

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./tools/run_headless_test.sh       # both suites, all levels
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./tools/run_headless_test.sh 2     # just level 2
ANPLOMER_TRACE=1 JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./tools/run_headless_test.sh 3
DUMP_FRAME=/tmp/frame.png JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./tools/run_headless_test.sh
```

`ANPLOMER_TRACE=1` dumps a 34-frame window after every jump press plus every
air/land transition and the death location, which is how level-fairness
problems in this project were found and fixed.

Current status:

```
RENDERER SMOKE OK: no exceptions, all geometry finite, all states/palettes/sizes drawn
level 1 OK  (24.6s sim, 1478 frames, score 2510, coins 21/55, deaths 0)
level 2 OK  (31.9s sim, 1913 frames, score 3390, coins 23/58, deaths 0)
level 3 OK  (41.7s sim, 2501 frames, score 2590, coins 20/55, deaths 1)
level 4 OK  (40.1s sim, 2407 frames, score 2800, coins 21/66, deaths 0)
level 5 OK  (50.4s sim, 3024 frames, score 3700, coins 23/70, deaths 1)
level 6 OK  (40.8s sim, 2446 frames, score 2650, coins 19/78, deaths 0)
level 7 OK  (48.2s sim, 2891 frames, score 3920, coins 26/77, deaths 1)
level 8 OK  (47.0s sim, 2818 frames, score 3990, coins 26/75, deaths 0)
smoke OK: menu -> play -> pause -> resume -> resize
save OK: v3 autosave, continue, retry-at-saved-level, career round trip, v1/v2 migration
coinrush OK: gate locked, then opens at the threshold
powerups OK: all five pickups, timer expiry, one-hit shield, magnet, persistence across respawn
career OK: create/select/two-tap delete, per-career wallets and levels, active career persisted
store OK: prices, deduction, refusal, max clamp, and every upgrade effect reaches the simulation
wallet OK: clear pays 8 coins once, into the active career
```

The suite also covers the career/store round trip: two careers with different
wallets and unlocked levels, switching between them, two-tap delete, buying an
upgrade and confirming its effect reaches the simulation (speed multiplier,
jump multiplier, extra lives, magnet radius, power-up duration, starting
shield), plus migration of v1 and v2 save blobs into a single career.

### Off-device screen dumps

`shots/new/` holds frames rasterised on the JVM by `RendererSmoke`:

```
DUMP_SHOTS=$PWD/shots/new JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./tools/run_headless_test.sh
```

- `menu.png` — home screen with the active-career badge and the three button groups
- `career.png` — career list with an active row, wallet and level per career
- `store.png` — store rows with effect, pips, price and the wallet header
- `help.png` — the "HOW TO PLAY" tutorial overlay with the power-up legend
- `level4-caverns.png`, `level8-summit.png` — the new palettes plus the power-up
  chips, shield pip and wallet chip in the HUD
- `menu-1080x480.png`, `career-1080x480.png`, `store-1080x480.png`,
  `playing-1080x480.png`, `cleared-1080x480.png`, `win-1080x480.png`,
  `help-1080x480.png`, `menu-480x320.png`, `help-480x320.png` — the same screens
  at phone-landscape and small sizes, where labels used to spill out of their
  boxes

These come from the `java.awt` rasteriser in `tools/RendererSmoke.java`, whose
font metrics differ from Android's, so treat them as *geometry* evidence (layout,
sprites, palettes, HUD placement) rather than pixel-exact screenshots.

## Device verification

`shots/` holds real screenshots from the physical phone described below
(`phone-*.png`, 1604x720) plus one frame rasterised off-device by
`DUMP_FRAME` (`jvm-frame.png`).

### Verified on a physical device

The APK was installed and played on a real **OPPO A3x (CPH2641, Android 15,
arm64-v8a, 720x1604)** over USB:

```
adb -s 47958c75 install -r app-debug.apk        -> Success
adb -s 47958c75 shell am start -n com.example.anplomer/.MainActivity
   Status: ok   LaunchState: COLD   TotalTime: 1595ms
   mCurrentFocus=Window{... com.example.anplomer/com.example.anplomer.MainActivity}
```

It rotated to landscape (1604x720), rendered the menu, loaded level 1 on tap
and responded to the run and jump controls — frame diffs of 1.1M, 215k and
27k pixels respectively, with ~10k distinct colours per frame. No crashes, no
ANRs and no `frame failed` log entries; the process stayed up throughout.

### UI overhaul round (menu, HUD, saves)

The "black-on-black, too small" text had a one-line root cause: every one of
the 27 `drawText` calls configured the `text` paint (size, colour, shadow) and
then drew with the **`fill`** paint — so all text rendered in whatever colour
`fill` happened to hold (usually the translucent black of the HUD panels) at
the default ~12 px size. One mechanical pass moved all 27 calls to `text`.

The UI round also fixed three structural bugs found while verifying:

* **The on-screen pause/mute discs were never touch-wired** — drawn but with
  zero callers of `Controls.inPause()/inMute()`. A tap on them now raises the
  same edges as `P`/`M`; verified on device (tap disc → PAUSED, tap again →
  resume) with temporary tap-position logging that was removed afterwards.
* **Backgrounding froze the game forever**: `GameView.onHostResume()` existed
  but had zero callers, so `hostPaused` was never cleared and the loop kept
  drawing a stale frame. `MainActivity.onResume` now clears it (and applies
  the PAUSED state on the loop thread, not the UI thread). Verified by
  Home → relaunch → pause tap responding.
* The HUD centre panel is clamped between the left cluster and the pause/mute
  discs, so panels can never overlap at any width; on this phone the surface
  is 1540x720 (ColorOS reserves a ~64 px cutout strip on the left) and every
  panel boundary measured within a few pixels of its computed position.

Persistence was verified end-to-end on the device: toggling to COIN COLLECTOR,
killing the process, relaunching — the menu still showed the saved mode, and
the death → retry → *saved level* flow was exercised live (the player was left
standing at a spawn a walker eventually reaches, which turned out to be a
convenient way to observe `GAME_OVER` → tap → back on the saved level).

> Note: on this Linux host the agent shell runs sandboxed with a private
> `/dev`, so adb can only see the phone when run outside that sandbox — and
> the USB scan needs ~15 s before the device appears in `adb devices`. Neither
> affects the app.

### Capturing real consecutive frames (ghost/trail debugging)

`adb shell screencap` is useless for motion artifacts on this app: while it
holds the display the game loop stops advancing, so every remotely captured
frame is the *same* image. Verified — during a 14-frame on-device burst the
player sprite sat at identical pixel coordinates (cx=85.9, cy=491.7) in 13 of
the 14 frames while Run was held. `screenrecord` is not an option on this
device either: ColorOS denies `/sdcard` and `/data/local/tmp` writes to the
shell user, and `run-as` cannot exec it.

So the game captures its own frames instead. `FrameDumper` re-renders each
posted frame into an offscreen bitmap and writes PNGs to app-private storage,
armed by a sentinel file:

```sh
tools/dump_frames.sh 8          # records 8s of running/jumping, pulls to /tmp/gameframes
```

It is inert unless `files/dumpframes` exists (one `File.exists()` polled at most
every 250 ms), caps at 300 frames per arming, disables itself on any error so a
debug aid can never kill the game loop, and consumes the sentinel when the cap
is reached so a later capture can re-arm. Start the level *before* arming, and
check `dumpsys window | grep mCurrentFocus` — if the notification shade is open
the app is `hostPaused`, the loop is frozen, and nothing dumps.

This is the instrument the trail investigation lacked, and what it showed once
the stub `Paint` was made faithful: over 43 touch-driven gameplay frames
(running and jumping, pads pressed so the bug condition is live) the player is
one clean sprite (span 30–34 px against a 1604 px frame) and the sky band never
darkens. The earlier "double-pass" off-device experiment that appeared to prove
the renderer ghost-free was itself an artifact of the unfaithful stub — its
`Paint` could not represent the 0x33 alpha leak at all.

### One root cause behind the ghost trail and the dark sky

`Renderer` shares a single `Paint fill` across everything it draws, and the sky
is drawn with a `LinearGradient` **shader**. On Android a shader is modulated by
the paint's alpha. While a run pad or jump button is held, `drawControls` — the
last thing drawn in a playing frame — leaves the **0x33 pressed-pad highlight**
in `fill`, so the next frame's sky rendered at **20% alpha**:

- over the **reused** `lockCanvas()` buffer the previous frame showed through it
  → ghost trails behind the player and the coins;
- over a **black-wiped** buffer (an interim "fix") the 20% sky over black is
  dark navy → "the sky turns black while moving", with the bright world drawn
  opaquely on top.

One bug, two symptoms — which is why the wipe build appeared to fix the trail
while blackening the sky: it swapped one symptom for the other. When no control
is held the highlight is not drawn, `fill` ends the frame opaque, and the sky
looked fine — hence "only when moving".

The fix is one line in `drawSky`: the sky is the backdrop for the whole frame,
so it must force its own opacity before setting the shader:

```java
fill.setAlpha(255);                    // the sky must be opaque
fill.setShader(new LinearGradient(...));
```

The opaque sky then overwrites every pixel of the reused buffer each frame, so
no wipe is needed and none exists. (`PixelFormat.RGBA_8888` stays as it always
was.)

An interim diagnosis blamed surface formats and xfermode leakage. It was wrong,
and it shipped once — verified by inspecting `dumpsys SurfaceFlinger` plumbing
instead of the picture. Two lessons are encoded in the tooling:

1. **The stub `Paint` is now faithful to Android**: `setColor`, `setAlpha` and
   `getAlpha` share the color's alpha byte. The old stub kept a separate
   always-255 alpha and forced every colour opaque, which made this entire bug
   class impossible to reproduce off-device.
2. **`RendererSmoke` section 6 (sky opacity guard)** drives 600 frames with a
   run pad held (so the pressed highlight is drawn) and asserts the first
   full-screen fill of every frame carries alpha 255. With the fix reverted it
   fails at alpha 51 (0x33) — the leak, caught.

Verified on the phone after the fix: sky band mean 98.0 identically while idle,
mid-jump and running; single player sprite per frame (span 30–31 px of 1604)
across 43 touch-driven dumped frames; 0 frame failures, 0 crashes.

### Emulator caveat

The emulator used here has **no KVM**, so it runs under TCG software
emulation. That makes it very slow and, under load, its `system_server`
process is watchdog-killed — installs then fail with `Broken pipe (32)` or
`Can't find service: package`, and adb reports the device `offline`.
Two helper scripts live in `.emulator-lab/`:

* `start-emulator.sh` — boots the AVD (headless, software GPU).
* `install-loop.sh` — installs the APK, retrying until PackageManager actually
  resolves `com.example.anplomer/.MainActivity`, then launches and captures.
  This is the only reliable way to get the app onto this emulator.

None of this affects the app itself: the same APK installs and runs normally
on real hardware. The final build is reproducible — rebuilding an unchanged
tree yields a byte-identical APK
(`sha256 f4452efb…742b7c`, 57,898 bytes).

### The camera bug these screenshots found

An early on-device capture showed parallax scenery, coins and the control
overlay but **no ground and no player**. Reproducing the situation headlessly
gave `camera y=0.0` while the player stood at world `y≈712`.

Cause: `Camera.update()` lerped toward the follow target and then clamped the
*lerped position* into the world bounds. Standing on the floor of a
15-row level puts the follow target below the world, so the clamp kept
pinning the camera to the top edge (y=0) — where nothing but sky exists.

Fix: clamp the **target**, not the current position, in `desiredX()` /
`desiredY()`; the camera then lerps toward an always-valid point. The
`RendererSmoke` camera-framing guard asserts the projected player position
stays inside the viewport at every tested size and at the level end.
