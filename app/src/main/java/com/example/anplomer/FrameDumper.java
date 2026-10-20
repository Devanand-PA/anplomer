package com.example.anplomer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Debug-only: writes consecutive finished frames to app-private storage so the
 * actual on-device pixels can be analysed off-device.
 *
 * Why this exists: {@code adb shell screencap} freezes this app's game loop
 * while it holds the display, so every remotely captured frame is the same
 * static image and can never show a motion artifact (verified: the player sat
 * at identical pixel coordinates in 13 of 14 burst frames). Letting the game
 * render its own frames is the only way to observe real motion on the phone.
 *
 * Armed by creating the sentinel file {@code files/dumpframes}. When absent,
 * the per-frame cost is one {@code File.exists()} polled at most every 250 ms.
 */
final class FrameDumper {

    private static final String TAG = "AnplomerDump";

    /** Create this file inside the app's files/ dir to start dumping. */
    private static final String SENTINEL = "dumpframes";

    /** Upper bound on frames per arming, so a forgotten sentinel cannot fill storage. */
    private static final int LIMIT = 300;

    /** Re-check interval for the sentinel while disarmed, in ms. */
    private static final long POLL_MS = 250;

    private final File dir;
    private final File sentinel;
    private final Renderer renderer = new Renderer();
    private final Game game;

    private Bitmap frame;
    private Canvas frameCanvas;

    private boolean enabled;
    private long checkedAtMs;
    private int written;

    FrameDumper(Context ctx, Game game) {
        this.game = game;
        this.dir = new File(ctx.getFilesDir(), "frames");
        this.sentinel = new File(ctx.getFilesDir(), SENTINEL);
    }

    /** True while dumping is active. */
    boolean active() {
        return enabled;
    }

    /**
     * Called on the render thread once per frame, after the surface frame has
     * been drawn. Renders the same state into an offscreen bitmap and writes it
     * out. Never throws: a debug aid must not be able to kill the game loop.
     */
    void maybeDump(int w, int h) {
        try {
            if (!arm()) {
                return;
            }
            if (w <= 0 || h <= 0) {
                return;
            }
            if (frame == null || frame.getWidth() != w || frame.getHeight() != h) {
                if (frame != null) {
                    frame.recycle();
                }
                frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                frameCanvas = new Canvas(frame);
            }

            // Same draw order, camera and state as the posted surface frame.
            renderer.draw(game, frameCanvas, w, h);

            File out = new File(dir, String.format(Locale.US, "f%04d.png", written));
            OutputStream os = new FileOutputStream(out);
            try {
                frame.compress(Bitmap.CompressFormat.PNG, 100, os);
            } finally {
                os.close();
            }

            written++;
            if (written == 1) {
                Log.i(TAG, "first frame -> " + out.getAbsolutePath());
            }
            if (written >= LIMIT) {
                enabled = false;
                // Consume the sentinel so a later capture can re-arm with a
                // fresh touch, while a forgotten sentinel cannot write forever.
                //noinspection ResultOfMethodCallIgnored
                sentinel.delete();
                Log.i(TAG, "dumped " + written + " frames, stopping");
            }
        } catch (Throwable t) {
            enabled = false;
            Log.e(TAG, "frame dumping disabled after failure", t);
        }
    }

    /** Arms when the sentinel appears. Returns true if dumping should proceed. */
    private boolean arm() {
        if (enabled) {
            return true;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - checkedAtMs < POLL_MS) {
            return false;
        }
        checkedAtMs = now;
        if (!sentinel.exists()) {
            return false;
        }
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        written = 0;
        enabled = true;
        Log.i(TAG, "armed, writing to " + dir.getAbsolutePath());
        return true;
    }
}
