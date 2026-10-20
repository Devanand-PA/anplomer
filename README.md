# Anplomer

A complete, self-contained 2D platformer for Android. Three hand-designed levels,
on-screen touch controls, parallax scenery, particles and procedural sound.
No third-party or AndroidX dependencies — plain Java 17 + `SurfaceView`/`Canvas`.

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
├── SaveState.java        persisted progress (level, unlocks, mode, sound) as plain data
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
* **SOUND: ON / OFF** and a small **RESET SAVE** with a two-tap confirm.

Progress auto-saves through a `SaveStore` interface (`Game` passes plain data;
`GameView` plugs in a `SharedPreferences` blob). The level you are on is written
every time a level is entered, so **losing all lives restarts the last saved
level**, not level 1. Mode and sound choice persist too. `SaveState` encodes a
versioned, malformed-input-tolerant pipe string, and the headless suite covers
the whole flow (autosave, continue, retry-at-saved-level, mode gating).

## Gameplay

* **Level 1 — Emerald Meadow** (152 tiles, par 50s, 55 coins)
* **Level 2 — Sunset Ruins** (170 tiles, par 65s, 58 coins, movers over lava)
* **Level 3 — Moonlit Peak** (190 tiles, par 80s, 55 coins, long mover ferries)

Stomp enemies to bounce, grab coins, touch the flag to clear a level.
Checkpoints respawn you in place; three lives per level; running out of lives
returns you to the last saved level (see *Home screen, saves and modes*).

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

The generator enforces fairness rules (spikes clear of pit edges and landing
zones, no flyer inside a required jump arc, no rise taller than the jump can
clear, every platform reachable) and refuses to overwrite a glyph silently.

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
level 1 OK  (24.6s sim, 1476 frames, score 2460, coins 22/55, deaths 0)
level 2 OK  (31.8s sim, 1909 frames, score 2740, coins 20/58, deaths 0)
level 3 OK  (40.5s sim, 2432 frames, score 2800, coins 20/55, deaths 1)
smoke OK: menu -> play -> pause -> resume -> resize
save OK: autosave, continue, and retry-at-saved-level
coinrush OK: gate locked, then opens at the threshold
```

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
