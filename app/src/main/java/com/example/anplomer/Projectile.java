package com.example.anplomer;

/**
 * A secondary-weapon shot: the bow's arrow or the boomerang.
 *
 * The arrow flies straight and fast with a little gravity and dies on the
 * first wall or after {@link Constants#ARROW_LIFE}. The boomerang flies out,
 * then homes back to the player who threw it (dying when caught, on a wall it
 * cannot get round, or when its lifetime runs out).
 *
 * Positions are the projectile's centre; {@link Game} owns the live list and
 * does the enemy hit tests.
 */
public final class Projectile {

    public static final int ARROW = 0;
    public static final int BOOMERANG = 1;

    public final int kind;
    public float x, y;          // centre, world units
    public float vx, vy;
    public final float w, h;
    /** Seconds alive. */
    public float life;
    /** Renderer animation phase (spin for the boomerang, tilt for the arrow). */
    public float phase;
    /** Boomerang only: true once it has turned around and heads home. */
    public boolean returning;
    public boolean dead;

    /** Throw direction of the shot, -1 or +1. */
    public final int facing;
    /** Boomerang cannot be caught for this long, so it always leaves the hand. */
    private float catchGrace;

    public Projectile(int kind, float cx, float cy, int facing) {
        this.kind = kind;
        this.x = cx;
        this.y = cy;
        this.facing = facing >= 0 ? 1 : -1;
        if (kind == BOOMERANG) {
            w = Constants.BOOMERANG_W;
            h = Constants.BOOMERANG_H;
            vx = this.facing * Constants.BOOMERANG_SPEED;
            vy = -Constants.BOOMERANG_LIFT;
            catchGrace = 0.18f;
        } else {
            w = Constants.ARROW_W;
            h = Constants.ARROW_H;
            vx = this.facing * Constants.ARROW_SPEED;
            vy = 0f;
        }
    }

    public float centerX() {
        return x;
    }

    public float centerY() {
        return y;
    }

    public boolean overlaps(float px, float py, float pw, float ph) {
        return Level.rectsOverlap(x - w / 2f, y - h / 2f, w, h, px, py, pw, ph);
    }

    /**
     * Advances the shot.
     *
     * @param owner the player who fired it (the boomerang's return target)
     * @param lvl   the level, for wall collisions
     */
    public void update(float dt, Player owner, Level lvl) {
        if (dead) {
            return;
        }
        life += dt;
        if (kind == BOOMERANG) {
            updateBoomerang(dt, owner, lvl);
        } else {
            updateArrow(dt, lvl);
        }
    }

    private void updateArrow(float dt, Level lvl) {
        phase += dt * 9f;
        vy += Constants.ARROW_GRAVITY * dt;
        x += vx * dt;
        y += vy * dt;
        if (life > Constants.ARROW_LIFE) {
            dead = true;
            return;
        }
        if (lvl != null && lvl.solidOverlap(x - w / 2f, y - h / 2f, w, h)) {
            dead = true;
            return;
        }
        if (lvl != null && y > (lvl.h + 1) * Constants.TILE) {
            dead = true;
        }
    }

    private void updateBoomerang(float dt, Player owner, Level lvl) {
        phase += dt * 16f;
        if (catchGrace > 0f) {
            catchGrace -= dt;
        }
        if (life > Constants.BOOMERANG_OUT_TIME) {
            returning = true;
        }
        if (returning && owner != null) {
            float tx = owner.centerX();
            float ty = owner.centerY();
            float dx = tx - x;
            float dy = ty - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > 0.001f) {
                // accelerate home so the return always looks snappier than the
                // throw, and catch it once it is back in the hand
                vx = dx / d * Constants.BOOMERANG_RETURN_SPEED;
                vy = dy / d * Constants.BOOMERANG_RETURN_SPEED;
            }
            if (catchGrace <= 0f && d <= Constants.BOOMERANG_CATCH_R) {
                dead = true;
                return;
            }
        } else {
            vy += Constants.BOOMERANG_GRAVITY * dt;
        }
        x += vx * dt;
        y += vy * dt;
        if (life > Constants.BOOMERANG_LIFE) {
            dead = true;
            return;
        }
        if (lvl != null && lvl.solidOverlap(x - w / 2f, y - h / 2f, w, h)) {
            // a wall does not destroy it: it turns for home
            returning = true;
            vx = -vx;
            vy = -Math.abs(vy) * 0.5f;
        }
    }
}
