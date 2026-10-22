package com.example.anplomer;

import android.graphics.Canvas;

/**
 * Headless performance benchmark for the simulation + renderer.
 *
 * The renderer is driven through the recording {@code android.graphics} stub
 * (see {@code tools/stub}) so the real drawing code runs with no Android SDK or
 * device. Two numbers matter and both are printed per scenario:
 *
 *   - ms/frame  — wall-clock cost of one fixed-timestep update plus one draw
 *   - draws     — canvas draw calls per frame (a device-independent proxy for
 *                 GPU/rasterisation cost; the stub counts every primitive)
 *
 * The third column is bytes allocated per frame on the calling thread, which is
 * what drives Android GC pauses during play. READ IT WITH A GRAIN OF SALT:
 * the stub {@code Path} records every segment as a fresh {@code float[]}
 * ({@code tools/stub/android/graphics/Path.java}), so a frame that legitimately
 * builds a handful of long vector paths — batched grass blades, tile outlines,
 * a flag — shows tens of kilobytes of harness allocation that never happens on
 * a device, where Path geometry lives in native memory. The stub's
 * {@code LinearGradient} also clones its colour/position arrays. Use
 * {@code draws/frame} and {@code ms/frame} as the regression signals they are
 * meant to be, and read B/frame only for ORDER OF MAGNITUDE changes: a real
 * per-frame allocation regression (a rebuilt HUD string, a per-frame gradient)
 * shows up as a few hundred bytes to a couple of kilobytes, which is exactly
 * what the pre-optimisation baseline carried.
 *
 * Usage: {@code tools/run_bench.sh [frames]}
 *
 * This is a regression tool, not a micro-benchmark: it plays a deterministic
 * scripted input pattern so two runs on the same machine are comparable.
 */
public final class Bench {

    public static final class NullSound implements SoundSink {
        @Override
        public void play(String name) {
        }

        @Override
        public void setEnabled(boolean enabled) {
        }

        @Override
        public boolean isEnabled() {
            return false;
        }
    }

    /** One measured scenario. */
    public static final class Result {
        public final String name;
        public final float msPerFrame;
        public final float drawsPerFrame;
        public final long bytesPerFrame;

        Result(String name, float ms, float draws, long bytes) {
            this.name = name;
            this.msPerFrame = ms;
            this.drawsPerFrame = draws;
            this.bytesPerFrame = bytes;
        }

        @Override
        public String toString() {
            return String.format(java.util.Locale.US,
                    "%-34s %7.3f ms/frame %8.1f draws/frame %8d B/frame",
                    name, msPerFrame, drawsPerFrame, bytesPerFrame);
        }
    }

    private static final int WARMUP = 200;

    public static void main(String[] args) {
        int frames = args.length > 0 ? Integer.parseInt(args[0]) : 1200;
        System.out.println("Anplomer headless bench — " + frames + " measured frames/scenario");
        System.out.println("(one row per level palette; " + Levels.TEXT.length + " levels in Levels.TEXT)");
        System.out.println("NOTE: B/frame includes stub Path segment recording — see tools/Bench.java;"
                + " use draws/ms for regressions.");

        System.out.println(run("update only", 2400, 1080, 0, frames, false));

        // every level, one screen size: catches a palette regressing the frame cost
        for (int level = 0; level < Levels.TEXT.length; level++) {
            System.out.println(run("2400x1080 L" + (level + 1), 2400, 1080, level, frames, true));
        }
        // the same level at other surface sizes
        System.out.println(run("1920x1080 L1 meadow", 1920, 1080, 0, frames, true));
        System.out.println(run("1280x720 L1 meadow", 1280, 720, 0, frames, true));

        // overlays: the panel-heavy screens allocate strings and draw many
        // primitives, so they get their own rows.
        System.out.println(runOverlay("2400x1080 menu overlay", 2400, 1080, Game.State.MENU, frames));
        System.out.println(runOverlay("2400x1080 career overlay", 2400, 1080, Game.State.CAREER, frames));
        System.out.println(runOverlay("2400x1080 store overlay", 2400, 1080, Game.State.STORE, frames));

        // HUD with every power-up chip + shields live: the steady-state PLAYING
        // path must not allocate per frame with power-ups on screen.
        System.out.println(runPowerUps("2400x1080 L1 power-ups", 2400, 1080, frames));
    }

    public static Result run(String name, int w, int h, int level, int frames, boolean draw) {
        Game game = new Game(new NullSound());
        game.resize(w, h);
        game.newGame();
        if (level > 0) {
            game.loadLevel(level);
        }
        game.state = Game.State.PLAYING;
        return measure(name, game, w, h, frames, draw);
    }

    /** One of the menu-style overlays, opened through the real input path. */
    public static Result runOverlay(String name, int w, int h, Game.State state, int frames) {
        Game game = new Game(new NullSound());
        game.resize(w, h);
        if (state == Game.State.CAREER || state == Game.State.STORE) {
            // Let Game lay the buttons out and enter the screen exactly as a
            // tap would, so the benchmark covers the same geometry as device.
            tapButton(game, state == Game.State.CAREER
                    ? Game.BTN_CAREER : Game.BTN_STORE);
        }
        game.stateTime = 1f;
        return measure(name, game, w, h, frames, true);
    }

    /** PLAYING with a live speed/magnet/double-jump timer and shields. */
    public static Result runPowerUps(String name, int w, int h, int frames) {
        Game game = new Game(new NullSound());
        game.resize(w, h);
        game.newGame();
        game.state = Game.State.PLAYING;
        game.speedBoostTimer = 8f;
        game.magnetTimer = 6f;
        game.doubleJumpTimer = 12f;
        game.shieldCharges = 2;
        game.profile.coins = 240;
        return measure(name, game, w, h, frames, true);
    }

    private static void tapButton(Game game, int id) {
        for (Game.UiButton b : game.menuButtons) {
            if (b.id != id) {
                continue;
            }
            game.input.pointerDown(1, b.x + b.w / 2f, b.y + b.h / 2f);
            game.input.pointerUp(1);
            game.update(1f / 60f);
            return;
        }
    }

    private static Result measure(String name, Game game, int w, int h, int frames, boolean draw) {
        Canvas canvas = new Canvas(w, h);
        Renderer renderer = new Renderer();

        for (int i = 0; i < WARMUP; i++) {
            drive(game, i);
            if (draw) {
                renderer.draw(game, canvas, w, h);
            }
        }

        // Touch the allocator counter once so its lazy setup is not measured.
        long allocStart = allocatedBytes();
        long t0 = System.nanoTime();
        long draws = 0;
        for (int i = 0; i < frames; i++) {
            drive(game, i);
            if (draw) {
                canvas.drawCount = 0;
                renderer.draw(game, canvas, w, h);
                draws += canvas.drawCount;
            }
        }
        long t1 = System.nanoTime();
        long bytes = allocatedBytes() - allocStart;

        float ms = (t1 - t0) / 1_000_000f / frames;
        float drawAvg = draw ? draws / (float) frames : 0f;
        return new Result(name, ms, drawAvg, bytes / frames);
    }

    /** Deterministic "bot": run right, jump in a fixed rhythm, pause taps off. */
    private static void drive(Game game, int frame) {
        game.input.key(InputController.KEY_RIGHT, true);
        game.input.key(InputController.KEY_JUMP, frame % 37 == 0);
    }

    private static long allocatedBytes() {
        try {
            java.lang.management.ThreadMXBean bean =
                    java.lang.management.ManagementFactory.getThreadMXBean();
            if (bean instanceof com.sun.management.ThreadMXBean) {
                return ((com.sun.management.ThreadMXBean) bean)
                        .getThreadAllocatedBytes(Thread.currentThread().getId());
            }
        } catch (Throwable ignored) {
            // not a HotSpot JVM: report 0 rather than failing the bench
        }
        return 0L;
    }
}
