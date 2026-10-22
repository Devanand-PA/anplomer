package com.example.anplomer;

/**
 * Tuning knobs for the whole game. All distances are in "world units"
 * (1 unit == 1 pixel of art at zoom 1); the tile grid is {@link #TILE} units.
 */
public final class Constants {

    private Constants() {
    }

    // ---- world / grid -------------------------------------------------
    public static final float TILE = 64f;
    public static final int LEVEL_ROWS = 15;

    // ---- player -------------------------------------------------------
    public static final float PLAYER_W = 40f;
    public static final float PLAYER_H = 56f;
    public static final float RUN_SPEED = 380f;
    public static final float ACCEL_GROUND = 4400f;
    public static final float ACCEL_AIR = 2900f;
    public static final float FRICTION = 5200f;
    public static final float GRAVITY = 3200f;
    public static final float MAX_FALL = 1700f;
    // v^2/2g gives a 3.88 tile rise in continuous physics; the fixed 60 Hz
    // integrator actually reaches ~3.71 tiles, so level rules cap steps at 3.
    public static final float JUMP_VELOCITY = 1260f;
    public static final float JUMP_CUT = 0.45f;        // release jump -> shorter hop
    public static final float COYOTE_TIME = 0.10f;
    public static final float JUMP_BUFFER = 0.13f;
    public static final float STOMP_BOUNCE = 780f;
    public static final float DEATH_POP = 880f;
    public static final float RESPAWN_DELAY = 1.05f;

    // ---- enemies ------------------------------------------------------
    public static final float WALKER_W = 48f;
    public static final float WALKER_H = 42f;
    public static final float WALKER_SPEED = 95f;
    public static final float FLYER_W = 46f;
    public static final float FLYER_H = 38f;
    public static final float FLYER_SPEED = 120f;
    public static final float FLYER_RANGE = 150f;
    public static final float SQUASH_TIME = 0.45f;

    // ---- items --------------------------------------------------------
    public static final float COIN_SIZE = 40f;
    public static final float PLATFORM_W = 192f;   // 3 tiles
    public static final float PLATFORM_H = 26f;
    public static final float PLATFORM_RANGE_H = 160f;  // +/- 2.5 tiles
    public static final float PLATFORM_RANGE_V = 132f;  // +/- ~2 tiles
    public static final float PLATFORM_SPEED = 1.15f;   // radians / second

    // ---- scoring ------------------------------------------------------
    public static final int SCORE_COIN = 100;
    public static final int SCORE_STOMP = 200;
    public static final int SCORE_ALL_COINS = 1000;
    public static final int SCORE_POWERUP = 150;
    public static final int SCORE_TIME_UNIT = 10;   // per second under par


    // ---- power-ups ----------------------------------------------------
    public static final float POWERUP_SIZE = 46f;
    /** Base seconds a timed power-up lasts (scaled by the POWER upgrade). */
    public static final float POWERUP_TIME = 12f;
    /** Run-speed multiplier while SPEED is active. */
    public static final float SPEED_BOOST_MUL = 1.45f;
    /** Seconds the MAGNET power-up lasts. */
    public static final float MAGNET_TIME = 10f;
    /** Base pickup-attraction radius while MAGNET is active (world units). */
    public static final float MAGNET_RADIUS = 300f;
    /** Seconds the DOUBLE_JUMP power-up lasts. */
    public static final float DOUBLE_JUMP_TIME = 15f;
    /** Grace after a shield soaks a hit, so it cannot be re-hit instantly. */
    public static final float INVULN_TIME = 1.2f;
    public static final int MAX_LIVES = 9;
    public static final int START_LIVES = 3;

    // ---- career / store ----------------------------------------------
    /** Coins banked per coin picked up. */
    public static final int COINS_PER_PICKUP = 1;
    /** Coins awarded for clearing a level. */
    public static final int COINS_LEVEL_CLEAR = 8;
    /** Extra coins for clearing a level with every coin collected. */
    public static final int COINS_ALL_COINS = 12;

    // per-level upgrade effects (see Upgrades / Profile)
    public static final float UPGRADE_SPEED_STEP = 0.04f;
    public static final float UPGRADE_JUMP_STEP = 0.03f;
    public static final float UPGRADE_MAGNET_STEP = 90f;
    public static final float UPGRADE_POWER_STEP = 0.15f;

    // ---- flow ---------------------------------------------------------
    public static final float LEVEL_FAIL_SECONDS = 150f;
}
