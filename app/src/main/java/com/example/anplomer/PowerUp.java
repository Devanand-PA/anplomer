package com.example.anplomer;

/**
 * A collectable power-up floating in the level. World position is the centre,
 * the same convention {@link Coin} uses. Timed effects live on {@link Game};
 * this class is only the pickup in the world.
 */
public final class PowerUp {

    public enum Type {
        /** Temporary run-speed multiplier. */
        SPEED,
        /** Absorbs one hit; a shield charge. */
        SHIELD,
        /** Attracts coins from a radius for a while. */
        MAGNET,
        /** One extra jump while airborne. */
        DOUBLE_JUMP,
        /** Immediate +1 life. */
        EXTRA_LIFE
    }

    public final Type type;
    public final float x, y;
    public final int index;
    /** Renderer animation phase, randomised at spawn so pickups don't pulse in sync. */
    public float phase;

    public PowerUp(Type type, float x, float y, int index, float phase) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.index = index;
        this.phase = phase;
    }

    /** Generous circular pickup radius, like coins. */
    public boolean overlaps(float px, float py, float pw, float ph) {
        float r = Constants.POWERUP_SIZE / 2f + 6f;
        float cx = Math.max(px, Math.min(x, px + pw));
        float cy = Math.max(py, Math.min(y, py + ph));
        float dx = x - cx;
        float dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    /** Glyph that spawns this type in a level grid. */
    public static char glyph(Type type) {
        switch (type) {
            case SPEED:
                return Level.POWER_SPEED;
            case SHIELD:
                return Level.POWER_SHIELD;
            case MAGNET:
                return Level.POWER_MAGNET;
            case DOUBLE_JUMP:
                return Level.POWER_JUMP;
            case EXTRA_LIFE:
                return Level.POWER_LIFE;
            default:
                return '?';
        }
    }
}
