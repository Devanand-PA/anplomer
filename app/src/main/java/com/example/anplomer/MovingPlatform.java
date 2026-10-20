package com.example.anplomer;

/**
 * A platform that slides back and forth (horizontally or vertically) on a
 * sine curve, carrying the player when they stand on it.
 */
public final class MovingPlatform {

    public final float baseX;
    public final float baseY;
    public final boolean vertical;
    public final float w = Constants.PLATFORM_W;
    public final float h = Constants.PLATFORM_H;

    public float x, y;
    public float dx, dy;
    private float time;
    private final float range;
    private final float phase;

    public MovingPlatform(float baseX, float baseY, boolean vertical, float phase) {
        this.baseX = baseX;
        this.baseY = baseY;
        this.vertical = vertical;
        this.range = vertical ? Constants.PLATFORM_RANGE_V : Constants.PLATFORM_RANGE_H;
        this.phase = phase;
        this.x = baseX;
        this.y = baseY;
    }

    public void update(float dt) {
        time += dt;
        float offset = (float) Math.sin(time * Constants.PLATFORM_SPEED + phase) * range;
        float nx = vertical ? baseX : baseX + offset;
        float ny = vertical ? baseY + offset : baseY;
        dx = nx - x;
        dy = ny - y;
        x = nx;
        y = ny;
    }

    public void reset() {
        time = 0f;
        x = baseX;
        y = baseY;
        dx = 0f;
        dy = 0f;
    }

    public boolean overlaps(float ox, float oy, float ow, float oh) {
        return ox < x + w && ox + ow > x && oy < y + h && oy + oh > y;
    }

    /** True when a body standing at the given x is resting on this platform. */
    public boolean supports(float bx, float bw, float feetY) {
        boolean horizontal = bx + bw > x + 4f && bx < x + w - 4f;
        boolean resting = feetY > y - 8f && feetY < y + 14f;
        return horizontal && resting;
    }
}
