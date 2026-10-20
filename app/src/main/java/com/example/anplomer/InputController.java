package com.example.anplomer;

import java.util.HashMap;
import java.util.Map;

/**
 * Collects keyboard and multi-touch input into simple game-facing flags.
 * Edge-triggered flags ("pressed") are cleared by the matching consume method.
 *
 * The class is deliberately free of Android types so the whole simulation can
 * be driven headlessly by the level self-test in {@code tools/}.
 *
 * Touch and key events arrive on the UI thread while the game loop reads the
 * flags on the render thread, so every accessor is synchronized: the pointer
 * map can never be iterated while it is being mutated.
 */
public final class InputController {

    public static final int KEY_LEFT = 0;
    public static final int KEY_RIGHT = 1;
    public static final int KEY_JUMP = 2;
    public static final int KEY_CONFIRM = 3;
    public static final int KEY_PAUSE = 4;
    public static final int KEY_RESTART = 5;
    public static final int KEY_BACK = 6;
    public static final int KEY_MUTE = 7;

    /** pointerId -> {x, y} in screen pixels. */
    private final Map<Integer, float[]> pointers = new HashMap<>();

    private boolean keyLeft, keyRight, keyJump, keyConfirm, keyPause, keyRestart, keyBack, keyMute;

    private Controls controls;
    private float screenW, screenH;

    // combined, recomputed on every change
    public boolean left;
    public boolean right;
    public boolean jumpHeld;

    private boolean jumpEdge;
    private boolean confirmEdge;
    private boolean pauseEdge;
    private boolean restartEdge;
    private boolean backEdge;
    private boolean muteEdge;

    private boolean tapPending;
    private float tapX, tapY;

    public synchronized void setScreen(float w, float h) {
        screenW = w;
        screenH = h;
        controls = Controls.compute(w, h);
        recompute();
    }

    public Controls controls() {
        return controls;
    }

    public float screenW() {
        return screenW;
    }

    public float screenH() {
        return screenH;
    }

    // ---- keyboard -----------------------------------------------------

    public synchronized void key(int key, boolean down) {
        switch (key) {
            case KEY_LEFT:
                keyLeft = down;
                break;
            case KEY_RIGHT:
                keyRight = down;
                break;
            case KEY_JUMP:
                keyJump = down;
                break;
            case KEY_CONFIRM:
                if (down) {
                    confirmEdge = true;
                }
                break;
            case KEY_PAUSE:
                if (down) {
                    pauseEdge = true;
                }
                break;
            case KEY_RESTART:
                if (down) {
                    restartEdge = true;
                }
                break;
            case KEY_BACK:
                if (down) {
                    backEdge = true;
                }
                break;
            case KEY_MUTE:
                if (down) {
                    muteEdge = true;
                }
                break;
            default:
                break;
        }
        recompute();
    }

    // ---- touch --------------------------------------------------------

    public synchronized void pointerDown(int id, float x, float y) {
        pointers.put(id, new float[]{x, y});
        tapPending = true;
        tapX = x;
        tapY = y;
        // The pause and mute discs are buttons, not movement pads: a tap on
        // them raises the same edges the keyboard keys do. Without this they
        // were drawn but untouchable.
        if (controls != null) {
            if (controls.inPause(x, y)) {
                pauseEdge = true;
            }
            if (controls.inMute(x, y)) {
                muteEdge = true;
            }
        }
        recompute();
    }

    public synchronized void pointerMove(int id, float x, float y) {
        if (pointers.containsKey(id)) {
            pointers.put(id, new float[]{x, y});
            recompute();
        }
    }

    public synchronized void pointerUp(int id) {
        pointers.remove(id);
        recompute();
    }

    public synchronized void pointersCancel() {
        pointers.clear();
        recompute();
    }

    private synchronized void recompute() {
        if (controls == null) {
            left = keyLeft;
            right = keyRight;
            jumpHeld = keyJump;
        } else {
            boolean tLeft = false;
            boolean tRight = false;
            boolean tJump = false;
            for (float[] p : pointers.values()) {
                if (controls.inLeft(p[0], p[1])) {
                    tLeft = true;
                }
                if (controls.inRight(p[0], p[1])) {
                    tRight = true;
                }
                if (controls.inJump(p[0], p[1])) {
                    tJump = true;
                }
            }
            left = keyLeft || tLeft;
            right = keyRight || tRight;
            jumpHeld = keyJump || tJump;
        }
        if (jumpHeld) {
            jumpEdge = true;
        }
    }

    // ---- edge accessors ------------------------------------------------

    public synchronized boolean consumeJump() {
        boolean v = jumpEdge;
        jumpEdge = false;
        return v;
    }

    public synchronized void clearJumpEdge() {
        jumpEdge = false;
    }

    public boolean peekJump() {
        return jumpEdge;
    }

    public synchronized boolean consumeConfirm() {
        boolean v = confirmEdge;
        confirmEdge = false;
        return v;
    }

    public synchronized boolean consumePause() {
        boolean v = pauseEdge;
        pauseEdge = false;
        return v;
    }

    public synchronized boolean consumeRestart() {
        boolean v = restartEdge;
        restartEdge = false;
        return v;
    }

    public synchronized boolean consumeBack() {
        boolean v = backEdge;
        backEdge = false;
        return v;
    }

    public synchronized boolean consumeMute() {
        boolean v = muteEdge;
        muteEdge = false;
        return v;
    }

    /** A fresh screen tap, consumed by overlay (menu) states. */
    public synchronized boolean consumeTap(float[] out) {
        if (!tapPending) {
            return false;
        }
        tapPending = false;
        if (out != null && out.length >= 2) {
            out[0] = tapX;
            out[1] = tapY;
        }
        return true;
    }

    public synchronized void clearTap() {
        tapPending = false;
    }

    public float tapX() {
        return tapX;
    }

    public float tapY() {
        return tapY;
    }

    public boolean hasTap() {
        return tapPending;
    }

    /** Drop all transient edges — used when the view loses the surface. */
    public synchronized void reset() {
        pointers.clear();
        keyLeft = keyRight = keyJump = keyConfirm = keyPause = keyRestart = keyBack = keyMute = false;
        jumpEdge = confirmEdge = pauseEdge = restartEdge = backEdge = muteEdge = false;
        tapPending = false;
        recompute();
    }
}
