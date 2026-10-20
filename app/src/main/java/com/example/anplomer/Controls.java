package com.example.anplomer;

/**
 * Geometry of the on-screen controls and HUD widgets, in screen pixels.
 * Recomputed whenever the surface size changes so drawing and hit-testing
 * always agree.
 */
public final class Controls {

    public float w, h;
    public float btnR;          // base d-pad radius
    public float leftX, leftY;
    public float rightX, rightY;
    public float jumpX, jumpY, jumpR;
    public float pauseX, pauseY, pauseR;
    public float muteX, muteY, muteR;

    /**
     * Touch radii. These are the only radii used for hit testing AND for
     * drawing the discs, so a tap can never land on an invisible target (or
     * miss a visible one).
     */
    public float leftHitR, rightHitR, jumpHitR, pauseHitR, muteHitR;

    public static Controls compute(float screenWidth, float screenHeight) {
        Controls c = new Controls();
        c.w = screenWidth;
        c.h = screenHeight;
        float base = Math.min(screenWidth, screenHeight);
        c.btnR = Math.max(48f, base * 0.072f);
        float m = c.btnR * 0.55f;

        c.leftY = screenHeight - m - c.btnR;
        c.leftX = m + c.btnR;
        c.rightX = c.leftX + c.btnR * 2.55f;
        c.rightY = c.leftY;

        c.jumpR = c.btnR * 1.3f;
        c.jumpX = screenWidth - m - c.jumpR;
        c.jumpY = screenHeight - m - c.jumpR;

        c.pauseR = c.btnR * 0.72f;
        c.pauseX = screenWidth - m - c.pauseR;
        c.pauseY = m + c.pauseR;

        c.muteR = c.btnR * 0.56f;
        c.leftHitR = c.btnR * 1.15f;
        c.rightHitR = c.leftHitR;
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

    public boolean inLeft(float px, float py) {
        return inCircle(leftX, leftY, leftHitR, px, py);
    }

    public boolean inRight(float px, float py) {
        return inCircle(rightX, rightY, rightHitR, px, py);
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
