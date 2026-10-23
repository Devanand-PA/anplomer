package com.example.anplomer;

/**
 * Physics body for the hero: run, jump, tile/platform collision and the
 * squash/stretch used by the renderer.
 */
public final class Player {

    public interface Listener {
        void onJump();

        void onLand(float impactSpeed);

        void onStep();

        /** A sword swing just started, with the arc chosen from the body state. */
        void onSwing(SwordStyle style);
    }

    public float x, y;
    public final float w = Constants.PLAYER_W;
    public final float h = Constants.PLAYER_H;
    public float vx, vy;

    public boolean onGround;
    public boolean facingRight = true;
    public boolean dead;
    public float deadTimer;

    // jump feel
    float coyote;          // package-visible for HeadlessTest diagnostics
    float jumpBuffer;
    private boolean prevJumpHeld;
    public float runCycle;     // used by the renderer for leg animation
    private float stepTimer;

    // ---- career upgrades + power-ups ----------------------------------
    /** Run-speed multiplier from upgrades and the SPEED power-up. */
    public float speedMul = 1f;
    /** Jump-velocity multiplier from the JUMP upgrade. */
    public float jumpMul = 1f;
    /** True while the DOUBLE_JUMP power-up is active. */
    public boolean doubleJumpEnabled;
    /** Mid-air jumps left for the current airborne period. */
    public int airJumps;

    // renderer helpers
    public float squash = 1f;     // >1 stretches vertically
    public float landFlash;
    public float prevBottom;

    // ---- health --------------------------------------------------------
    /**
     * Hit points from {@link Stats.Hero}. A life is only spent when this
     * reaches zero: hazards, pits and enemy contact take HP instead.
     */
    public int hp = Stats.Hero.MAX_HP;
    /** Never changes during a run; {@link Stats.Hero#MAX_HP} baked at build time. */
    public final int maxHp = Stats.Hero.MAX_HP;

    /** @return true when the hero is out of hit points */
    public boolean outOfHp() {
        return hp <= 0;
    }

    // ---- sword combat --------------------------------------------------
    /** Style of the current (or most recent) swing; picked when it starts. */
    public SwordStyle swingStyle = SwordStyle.GROUND_IDLE;
    /** Seconds left in the active swing window; > 0 while the blade is out. */
    public float swingTimer;
    /** Seconds until another swing may start. */
    public float attackCooldown;
    /** Swings made since the last respawn (tests / renderer). */
    public int swings;

    /** Scratch hitbox reused every frame so the frame path allocates nothing. */
    private final float[] attackBoxBuf = new float[4];

    public Player() {
        respawn(Constants.TILE, Constants.TILE);
    }

    /**
     * Moves the body and clears transient state without touching health, so a
     * pit fall can put the hero back on solid ground while keeping the damage.
     */
    public void reposition(float px, float py) {
        x = px;
        y = py;
        vx = 0;
        vy = 0;
        dead = false;
        deadTimer = 0;
        onGround = false;
        coyote = 0;
        jumpBuffer = 0;
        prevJumpHeld = false;
        squash = 1f;
        landFlash = 0;
        prevBottom = y + h;
        airJumps = 0;
        swingTimer = 0f;
        attackCooldown = 0f;
        swings = 0;
    }

    /** A spent life: back to full health at the spawn point. */
    public void respawn(float px, float py) {
        reposition(px, py);
        hp = maxHp;
    }

    public float centerX() {
        return x + w / 2f;
    }

    public float centerY() {
        return y + h / 2f;
    }

    /**
     * @return fall speed at the moment of landing, or 0 when no landing happened
     */
    public float update(float dt, InputController in, Level lvl,
                        java.util.List<MovingPlatform> plats, Listener listener) {
        prevBottom = y + h;

        if (dead) {
            deadTimer -= dt;
            vy += Constants.GRAVITY * 0.75f * dt;
            if (vy > Constants.MAX_FALL) {
                vy = Constants.MAX_FALL;
            }
            y += vy * dt;
            return 0f;
        }

        // ---- horizontal input (analog: pad deflection, or full tilt) ----
        float dir = in.moveAxis;
        if (dir > 1f) {
            dir = 1f;
        } else if (dir < -1f) {
            dir = -1f;
        }
        float maxSpeed = Constants.RUN_SPEED * speedMul;
        if (Math.abs(dir) > 0.02f) {
            float accel = onGround ? Constants.ACCEL_GROUND : Constants.ACCEL_AIR;
            vx += dir * accel * dt;
            // A partial deflection tops out at a partial speed, so the pad
            // gives a walk at half tilt and a sprint at full tilt.
            float target = dir * maxSpeed;
            if (dir > 0f && vx > target) {
                vx = target;
            } else if (dir < 0f && vx < target) {
                vx = target;
            }
            facingRight = dir > 0f;
        } else {
            float fr = (onGround ? Constants.FRICTION : Constants.FRICTION * 0.3f) * dt;
            if (Math.abs(vx) <= fr) {
                vx = 0f;
            } else {
                vx -= Math.signum(vx) * fr;
            }
        }

        // ---- jump buffering / coyote time ----
        if (in.consumeJump()) {
            jumpBuffer = Constants.JUMP_BUFFER;
        }
        jumpBuffer -= dt;
        coyote -= dt;

        float jumpVel = Constants.JUMP_VELOCITY * jumpMul;
        if (jumpBuffer > 0f && coyote > 0f) {
            vy = -jumpVel;
            onGround = false;
            coyote = 0f;
            jumpBuffer = 0f;
            squash = 1.35f;
            if (listener != null) {
                listener.onJump();
            }
        } else if (jumpBuffer > 0f && doubleJumpEnabled && airJumps > 0 && !onGround) {
            // DOUBLE_JUMP power-up: one extra kick in mid-air, slightly weaker
            // than a ground jump so it reads as a recovery move.
            vy = -jumpVel * 0.92f;
            airJumps--;
            jumpBuffer = 0f;
            squash = 1.22f;
            if (listener != null) {
                listener.onJump();
            }
        }

        // variable jump height: releasing early cuts the rise, and dragging the
        // pad down cuts it even while a jump input is still held
        boolean jumpHeldNow = in.jumpHeld && !in.jumpCut;
        if (prevJumpHeld && !jumpHeldNow && vy < 0f) {
            float cut = -jumpVel * Constants.JUMP_CUT;
            if (vy < cut) {
                vy = cut;
            }
        }
        prevJumpHeld = jumpHeldNow;
        if (!jumpHeldNow) {
            // holding jump buffered? nothing; edge already consumed
            in.clearJumpEdge();
        }

        // ---- gravity ----
        vy += Constants.GRAVITY * dt;
        if (vy > Constants.MAX_FALL) {
            vy = Constants.MAX_FALL;
        }

        // ---- integrate with collision ----
        float impact = 0f;
        boolean wasGround = onGround;
        float fallSpeed = vy;

        moveAxis(vx * dt, 0f, lvl, plats);

        onGround = false;
        moveAxis(0f, vy * dt, lvl, plats);

        if (onGround) {
            airJumps = doubleJumpEnabled ? 1 : 0;
            coyote = Constants.COYOTE_TIME;
            if (!wasGround && fallSpeed > 420f) {
                impact = fallSpeed;
                squash = Math.max(squash, 0.68f);
                landFlash = 1f;
                if (listener != null) {
                    listener.onLand(fallSpeed);
                }
            }
            // running dust / footstep cue
            if (Math.abs(vx) > 60f) {
                stepTimer -= dt;
                if (stepTimer <= 0f) {
                    stepTimer = 0.22f;
                    if (listener != null) {
                        listener.onStep();
                    }
                }
            } else {
                stepTimer = 0.1f;
            }
            if (Math.abs(vx) > 40f) {
                runCycle += dt * Math.abs(vx) * 0.045f;
            } else {
                runCycle += dt * 2f;
            }
        } else {
            stepTimer = 0.1f;
        }

        // ---- sword: pick the arc from how the body is moving right now ----
        attackCooldown = Math.max(0f, attackCooldown - dt);
        if (swingTimer > 0f) {
            swingTimer = Math.max(0f, swingTimer - dt);
        }
        // A fresh press starts a swing; holding the disc auto-repeats at the
        // cooldown, which is what a thumb resting on ATTACK expects.
        boolean wantSwing = in.consumeAttack() || (in.attackHeld && attackCooldown <= 0f);
        if (wantSwing && attackCooldown <= 0f && swingTimer <= 0f) {
            startSwing(listener);
        }

        // ease squash back to normal
        squash += (1f - squash) * Math.min(1f, dt * 12f);
        landFlash = Math.max(0f, landFlash - dt * 3f);

        return impact;
    }

    /** Begins a swing, choosing the arc from the body's current motion. */
    private void startSwing(Listener listener) {
        if (!onGround) {
            swingStyle = vy < 0f ? SwordStyle.AIR_RISE : SwordStyle.AIR_FALL;
        } else {
            swingStyle = Math.abs(vx) >= Constants.SWORD_RUN_SPEED
                    ? SwordStyle.GROUND_RUN : SwordStyle.GROUND_IDLE;
        }
        swingTimer = Constants.SWORD_TIME;
        attackCooldown = Constants.SWORD_COOLDOWN;
        swings++;
        if (listener != null) {
            listener.onSwing(swingStyle);
        }
    }

    /** True while the blade is out and {@link #attackBox()} is live. */
    public boolean attacking() {
        return swingTimer > 0f;
    }

    /**
     * World-space sword hitbox for the active swing, or {@code null} when the
     * player is not swinging. The returned array is reused between calls.
     *
     * Standing chops reach forward at chest height, running slashes reach
     * further ahead and lower, the rising arc covers the space above the head
     * and the falling arc the space below the feet.
     */
    public float[] attackBox() {
        if (swingTimer <= 0f) {
            return null;
        }
        float reach = swingStyle == SwordStyle.GROUND_RUN
                ? Constants.SWORD_REACH_RUN : Constants.SWORD_REACH;
        float bh = Constants.SWORD_HEIGHT;
        float bx;
        float by;
        if (swingStyle == SwordStyle.GROUND_RUN) {
            bh = Constants.SWORD_HEIGHT * 0.62f;
            bx = facingRight ? x + w * 0.40f : x + w * 0.60f - reach;
            by = y + h * 0.22f;
        } else if (swingStyle == SwordStyle.AIR_RISE) {
            bx = facingRight ? x + w * 0.35f : x + w * 0.65f - reach;
            by = y - bh * 0.85f;
        } else if (swingStyle == SwordStyle.AIR_FALL) {
            bx = facingRight ? x + w * 0.35f : x + w * 0.65f - reach;
            by = y + h * 0.35f;
        } else {
            bx = facingRight ? x + w * 0.45f : x + w * 0.55f - reach;
            by = y + h * 0.10f - bh * 0.45f;
        }
        attackBoxBuf[0] = bx;
        attackBoxBuf[1] = by;
        attackBoxBuf[2] = reach;
        attackBoxBuf[3] = bh;
        return attackBoxBuf;
    }

    // ---- collision ----------------------------------------------------

    private void moveAxis(float dx, float dy, Level lvl, java.util.List<MovingPlatform> plats) {
        float remaining = dx + dy;
        boolean horizontal = Math.abs(dx) > Math.abs(dy);
        while (Math.abs(remaining) > 0.0001f) {
            float step = Math.signum(remaining) * Math.min(Math.abs(remaining), 16f);
            if (horizontal) {
                x += step;
                resolveX(step, lvl, plats);
            } else {
                y += step;
                resolveY(step, lvl, plats);
            }
            remaining -= step;
        }
    }

    private void resolveX(float step, Level lvl, java.util.List<MovingPlatform> plats) {
        if (lvl.solidOverlap(x, y, w, h)) {
            if (step > 0f) {
                int tx = (int) Math.floor((x + w - 0.001f) / Constants.TILE);
                x = tx * Constants.TILE - w - 0.01f;
            } else {
                int tx = (int) Math.floor(x / Constants.TILE);
                x = (tx + 1) * Constants.TILE + 0.01f;
            }
            vx = 0f;
        }
        if (plats != null) {
            for (MovingPlatform p : plats) {
                if (p.overlaps(x, y, w, h)) {
                    if (step > 0f) {
                        x = p.x - w - 0.01f;
                    } else {
                        x = p.x + p.w + 0.01f;
                    }
                    vx = 0f;
                }
            }
        }
    }

    private void resolveY(float step, Level lvl, java.util.List<MovingPlatform> plats) {
        if (lvl.solidOverlap(x, y, w, h)) {
            if (step > 0f) {
                int ty = (int) Math.floor((y + h - 0.001f) / Constants.TILE);
                y = ty * Constants.TILE - h - 0.01f;
                onGround = true;
            } else {
                int ty = (int) Math.floor(y / Constants.TILE);
                y = (ty + 1) * Constants.TILE + 0.01f;
            }
            vy = 0f;
        }
        if (plats != null) {
            for (MovingPlatform p : plats) {
                if (p.overlaps(x, y, w, h)) {
                    if (step > 0f) {
                        y = p.y - h - 0.01f;
                        onGround = true;
                    } else {
                        y = p.y + p.h + 0.01f;
                    }
                    vy = 0f;
                }
            }
        }
    }

    /** Push the body out of any platform it ended up inside (platform moved). */
    public void separateFrom(java.util.List<MovingPlatform> plats) {
        if (plats == null || dead) {
            return;
        }
        for (MovingPlatform p : plats) {
            if (p.overlaps(x, y, w, h)) {
                float dxLeft = (x + w) - p.x;          // push left amount
                float dxRight = (p.x + p.w) - x;       // push right amount
                float dyTop = (y + h) - p.y;           // push up
                float dyBottom = (p.y + p.h) - y;      // push down
                float m = Math.min(Math.min(dxLeft, dxRight), Math.min(dyTop, dyBottom));
                if (m == dyTop) {
                    y = p.y - h - 0.01f;
                    if (vy > 0f) {
                        vy = 0f;
                    }
                    onGround = true;
                } else if (m == dyBottom) {
                    y = p.y + p.h + 0.01f;
                    if (vy < 0f) {
                        vy = 0f;
                    }
                } else if (m == dxLeft) {
                    x = p.x - w - 0.01f;
                    vx = Math.min(vx, 0f);
                } else {
                    x = p.x + p.w + 0.01f;
                    vx = Math.max(vx, 0f);
                }
            }
        }
    }
}
