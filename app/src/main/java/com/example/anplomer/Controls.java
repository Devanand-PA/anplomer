package com.example.anplomer;

/**
 * Geometry of the on-screen controls and HUD widgets, in screen pixels.
 * Recomputed whenever the surface size changes so drawing and hit-testing
 * always agree.
 */
public final class Controls {

    public float w, h;
    public float btnR;          // base control radius
    /** Movement circle-pad centre, outer ring radius and knob radius. */
    public float padCX, padCY, padR, padKnobR;
    public float jumpX, jumpY, jumpR;
    public float pauseX, pauseY, pauseR;
    public float muteX, muteY, muteR;

    /**
     * Touch radii. These are the only radii used for hit testing AND for
     * drawing the discs, so a tap can never land on an invisible target (or
     * miss a visible one).
     */
    public float padHitR, jumpHitR, pauseHitR, muteHitR;

    public static Controls compute(float screenWidth, float screenHeight) {
        return compute(screenWidth, screenHeight, 1f);
    }

    /**
     * @param moveScale multiplier for the movement pads and jump button
     *                  (0.5..1.5); the pause/mute discs keep a fixed size.
     */
    public static Controls compute(float screenWidth, float screenHeight, float moveScale) {
        Controls c = new Controls();
        c.w = screenWidth;
        c.h = screenHeight;
        float base = Math.min(screenWidth, screenHeight);
        float rawR = Math.max(48f, base * 0.072f);
        moveScale = Math.max(0.5f, Math.min(1.5f, moveScale));
        c.btnR = rawR * moveScale;
        float m = rawR * 0.55f;

        // The movement control is one analogue pad: the thumb drags the knob
        // and the deflection (not a button press) is what the simulation reads.
        c.padR = c.btnR * 1.62f;
        c.padKnobR = c.btnR * 0.60f;
        c.padCX = m + c.padR;
        c.padCY = screenHeight - m - c.padR;

        c.jumpR = c.btnR * 1.3f;
        c.jumpX = screenWidth - m - c.jumpR;
        c.jumpY = screenHeight - m - c.jumpR;

        c.pauseR = rawR * 0.72f;
        c.pauseX = screenWidth - m - c.pauseR;
        c.pauseY = m + c.pauseR;

        c.muteR = c.btnR * 0.56f;
        // A finger does not have to land dead centre: the pad accepts a tap
        // anywhere inside its ring, and keeps the drag afterwards even when it
        // wanders outside (the vector is clamped, not dropped).
        c.padHitR = c.padR * 1.14f;
        c.jumpHitR = c.jumpR * 1.10f;
        c.pauseHitR = c.pauseR * 1.30f;
        c.muteHitR = c.muteR * 1.40f;
        // Space the centres by the DRAWN radii (which equal the hit radii) so
        // the pause and mute discs never overlap.
        c.muteX = c.pauseX - c.pauseHitR - c.muteHitR - m * 0.4f;
        c.muteY = c.pauseY;
        return c;
    }

    private static boolean inCircle(float cx, float cy, float r, float px, float py) {
        float dx = px - cx;
        float dy = py - cy;
        return dx * dx + dy * dy <= r * r;
    }

    /** True when {@code (px,py)} is inside the movement pad's ring. */
    public boolean inPad(float px, float py) {
        return inCircle(padCX, padCY, padHitR, px, py);
    }

    public boolean inJump(float px, float py) {
        return inCircle(jumpX, jumpY, jumpHitR, px, py);
    }

    public boolean inPause(float px, float py) {
        return inCircle(pauseX, pauseY, pauseHitR, px, py);
    }

    public boolean inMute(float px, float py) {
        return inCircle(muteX, muteY, muteHitR, px, py);
    }
}
