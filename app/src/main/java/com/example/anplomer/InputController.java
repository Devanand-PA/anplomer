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
    /** Primary weapon (sword swing); the keyboard uses J / X. */
    public static final int KEY_ATTACK = 8;
    /** Secondary weapon (bow / boomerang); the keyboard uses K / C. */
    public static final int KEY_SECONDARY = 9;

    /** pointerId -> {x, y} in screen pixels. */
    private final Map<Integer, float[]> pointers = new HashMap<>();

    private boolean keyLeft, keyRight, keyJump, keyConfirm, keyPause, keyRestart, keyBack, keyMute;
    private boolean keyAttack, keySecondary;

    private Controls controls;
    private float screenW, screenH;

    // ---- movement pad -------------------------------------------------
    /** Deflection threshold before a drag counts as a direction. */
    public static final float PAD_DEAD = 0.20f;
    /** Upward deflection (negative Y) that starts a jump... */
    public static final float PAD_JUMP_ON = -0.42f;
    /** ...and the lower threshold that keeps it held (hysteresis). */
    public static final float PAD_JUMP_OFF = -0.28f;
    /** Downward deflection that cuts the current rise. */
    public static final float PAD_CUT = 0.30f;

    /** Pointer id currently steering the pad, or -1. */
    private int padPointer = -1;
    /** True while a finger is on the pad. */
    public boolean padActive;
    /** Pad deflection, -1..1 in each axis (screen space: +Y is down). */
    public float padX, padY;
    private boolean padJumpLatch;

    // combined, recomputed on every change
    public boolean left;
    public boolean right;
    public boolean jumpHeld;
    /** Downward pad drag: the jump is cut even if a jump input is still held. */
    public boolean jumpCut;
    /** Analog run direction, -1..1. Pad deflection, or a key/disc as full tilt. */
    public float moveAxis;

    /** True while the attack disc (or J/X) is held: the sword auto-repeats. */
    public boolean attackHeld;
    /** True while the secondary disc (or K/C) is held. */
    public boolean secondaryHeld;

    private boolean jumpEdge;
    private boolean prevJumpHeld;
    private boolean attackEdge;
    private boolean prevAttackHeld;
    private boolean secondaryEdge;
    private boolean prevSecondaryHeld;
    private boolean confirmEdge;
    private boolean pauseEdge;
    private boolean restartEdge;
    private boolean backEdge;
    private boolean muteEdge;

    private boolean tapPending;
    private float tapX, tapY;

    public synchronized void setScreen(float w, float h) {
        setScreen(w, h, 1f);
    }

    /** Same as {@link #setScreen(float, float)} with a movement-button scale. */
    public synchronized void setScreen(float w, float h, float moveScale) {
        screenW = w;
        screenH = h;
        controls = Controls.compute(w, h, moveScale);
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
            case KEY_ATTACK:
                keyAttack = down;
                break;
            case KEY_SECONDARY:
                keySecondary = down;
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
        if (id == padPointer) {
            padPointer = -1;
        }
        recompute();
    }

    public synchronized void pointersCancel() {
        pointers.clear();
        padPointer = -1;
        recompute();
    }

    private synchronized void recompute() {
        float px = 0f;
        float py = 0f;
        boolean onPad = false;
        boolean tAttack = false;
        boolean tSecondary = false;
        if (controls != null) {
            // A pointer that lands inside the ring owns the pad until it lifts;
            // it keeps steering even when it is dragged past the ring.
            if (padPointer != -1 && !pointers.containsKey(padPointer)) {
                padPointer = -1;
            }
            if (padPointer == -1) {
                for (Map.Entry<Integer, float[]> e : pointers.entrySet()) {
                    float[] p = e.getValue();
                    if (controls.inPad(p[0], p[1])) {
                        padPointer = e.getKey();
                        break;
                    }
                }
            }
            if (padPointer != -1) {
                float[] p = pointers.get(padPointer);
                if (p != null) {
                    onPad = true;
                    px = (p[0] - controls.padCX) / controls.padR;
                    py = (p[1] - controls.padCY) / controls.padR;
                    float len = (float) Math.sqrt(px * px + py * py);
                    if (len > 1f) {
                        px /= len;
                        py /= len;
                    }
                }
            }
            for (float[] p : pointers.values()) {
                if (controls.inAttack(p[0], p[1])) {
                    tAttack = true;
                }
                if (controls.inSecondary(p[0], p[1])) {
                    tSecondary = true;
                }
            }
        }
        padActive = onPad;
        padX = px;
        padY = py;

        // Up = jump. Hysteresis between the two thresholds keeps a thumb that
        // wobbles on the edge from stuttering, and holding the pad up keeps the
        // jump "held" so the arc stays high.
        padJumpLatch = onPad && py <= (padJumpLatch ? PAD_JUMP_OFF : PAD_JUMP_ON);
        // Down = cut the rise, even while some other input still holds jump.
        jumpCut = onPad && py >= PAD_CUT;

        jumpHeld = keyJump || padJumpLatch;
        // Keyboard keeps its old "held = continuous presses" feel (the buffered
        // jump re-arms while held). The pad raises exactly one press per upward
        // crossing, so a thumb resting on the edge cannot machine-gun hops.
        if (keyJump || (jumpHeld && !prevJumpHeld)) {
            jumpEdge = true;
        }
        prevJumpHeld = jumpHeld;

        // Weapon discs behave like held buttons: the edge starts a swing / shot
        // and the held flag lets the simulation auto-repeat at its cooldown.
        attackHeld = keyAttack || tAttack;
        if (attackHeld && !prevAttackHeld) {
            attackEdge = true;
        }
        prevAttackHeld = attackHeld;
        secondaryHeld = keySecondary || tSecondary;
        if (secondaryHeld && !prevSecondaryHeld) {
            secondaryEdge = true;
        }
        prevSecondaryHeld = secondaryHeld;

        float axis;
        if (keyLeft == keyRight) {
            axis = onPad && Math.abs(px) > PAD_DEAD ? px : 0f;
        } else {
            axis = keyLeft ? -1f : 1f;
        }
        moveAxis = axis;
        left = axis < -0.01f;
        right = axis > 0.01f;
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

    /** A fresh press of the primary weapon (edge-triggered). */
    public synchronized boolean consumeAttack() {
        boolean v = attackEdge;
        attackEdge = false;
        return v;
    }

    /** A fresh press of the secondary weapon (edge-triggered). */
    public synchronized boolean consumeSecondary() {
        boolean v = secondaryEdge;
        secondaryEdge = false;
        return v;
    }

    public boolean peekAttack() {
        return attackEdge;
    }

    public boolean peekSecondary() {
        return secondaryEdge;
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

    /**
     * Copies the current touch positions into {@code out} as x,y pairs (at
     * most {@code out.length / 2} of them) and returns the count. Used by
     * overlay widgets that track drags, such as the settings sliders.
     */
    public synchronized int pointerPositions(float[] out) {
        int max = out == null ? 0 : out.length / 2;
        int n = Math.min(max, pointers.size());
        int i = 0;
        for (float[] p : pointers.values()) {
            if (i >= n) {
                break;
            }
            out[i * 2] = p[0];
            out[i * 2 + 1] = p[1];
            i++;
        }
        return i;
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
        padPointer = -1;
        padActive = false;
        padX = padY = 0f;
        padJumpLatch = false;
        jumpCut = false;
        moveAxis = 0f;
        prevJumpHeld = false;
        attackHeld = secondaryHeld = false;
        prevAttackHeld = prevSecondaryHeld = false;
        keyLeft = keyRight = keyJump = keyConfirm = keyPause = keyRestart = keyBack = keyMute = false;
        keyAttack = keySecondary = false;
        jumpEdge = confirmEdge = pauseEdge = restartEdge = backEdge = muteEdge = false;
        attackEdge = secondaryEdge = false;
        tapPending = false;
        recompute();
    }
}
