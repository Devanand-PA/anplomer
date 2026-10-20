package com.example.anplomer;

/**
 * Enemy base data. {@code type} selects behaviour:
 * <ul>
 *   <li>{@link Type#WALKER} — patrols on the ground, turns at walls and ledges</li>
 *   <li>{@link Type#FLYER} — hovers, sine-bobbing along a patrol range</li>
 * </ul>
 */
public final class Enemy {

    public enum Type {
        WALKER, FLYER
    }

    public final Type type;
    public float x, y;
    public final float w;
    public final float h;
    public float vx, vy;
    public float facing = -1f;
    public boolean dead;
    public float deadTimer;
    public float animTime;
    private final float baseX;
    private final float baseY;
    private float walkPhase;

    public Enemy(Type type, float x, float y) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.baseX = x;
        this.baseY = y;
        if (type == Type.WALKER) {
            this.w = Constants.WALKER_W;
            this.h = Constants.WALKER_H;
            this.vx = -Constants.WALKER_SPEED;
            this.facing = -1f;
        } else {
            this.w = Constants.FLYER_W;
            this.h = Constants.FLYER_H;
            this.vx = -Constants.FLYER_SPEED;
            this.facing = -1f;
        }
    }

    public float centerX() {
        return x + w / 2f;
    }

    public float centerY() {
        return y + h / 2f;
    }

    public void update(float dt, Level lvl) {
        animTime += dt;
        if (dead) {
            deadTimer -= dt;
            return;
        }
        if (type == Type.FLYER) {
            updateFlyer(dt);
        } else {
            updateWalker(dt, lvl);
        }
    }

    private void updateFlyer(float dt) {
        walkPhase += dt;
        float nx = baseX + (float) Math.sin(walkPhase * 0.9f) * Constants.FLYER_RANGE;
        float ny = baseY + (float) Math.sin(walkPhase * 2.6f + 1.1f) * 22f;
        vx = (nx - x) / Math.max(dt, 0.0001f);
        if (Math.abs(vx) > 4f) {
            facing = vx > 0f ? 1f : -1f;
        }
        x = nx;
        y = ny;
    }

    private void updateWalker(float dt, Level lvl) {
        vx = facing * Constants.WALKER_SPEED;
        vy += Constants.GRAVITY * dt;
        if (vy > Constants.MAX_FALL) {
            vy = Constants.MAX_FALL;
        }

        // horizontal, in small steps
        float remaining = vx * dt;
        boolean blocked = false;
        while (Math.abs(remaining) > 0.0001f) {
            float step = Math.signum(remaining) * Math.min(Math.abs(remaining), 8f);
            x += step;
            if (lvl.solidOverlap(x, y, w, h)) {
                if (step > 0f) {
                    int tx = (int) Math.floor((x + w - 0.001f) / Constants.TILE);
                    x = tx * Constants.TILE - w - 0.01f;
                } else {
                    int tx = (int) Math.floor(x / Constants.TILE);
                    x = (tx + 1) * Constants.TILE + 0.01f;
                }
                blocked = true;
                break;
            }
            remaining -= step;
        }
        if (blocked) {
            facing = -facing;
        }

        // vertical
        float remY = vy * dt;
        boolean grounded = false;
        while (Math.abs(remY) > 0.0001f) {
            float step = Math.signum(remY) * Math.min(Math.abs(remY), 8f);
            y += step;
            if (lvl.solidOverlap(x, y, w, h)) {
                if (step > 0f) {
                    int ty = (int) Math.floor((y + h - 0.001f) / Constants.TILE);
                    y = ty * Constants.TILE - h - 0.01f;
                    grounded = true;
                } else {
                    int ty = (int) Math.floor(y / Constants.TILE);
                    y = (ty + 1) * Constants.TILE + 0.01f;
                }
                vy = 0f;
                break;
            }
            remY -= step;
        }

        // turn at ledges so patrollers never walk into a pit
        if (grounded) {
            float probeX = facing > 0f ? x + w + 4f : x - 4f;
            float probeY = y + h + 6f;
            if (!lvl.solidOverlap(probeX, probeY, 2f, 2f)) {
                facing = -facing;
            }
            walkPhase += dt;
        }
    }
}
