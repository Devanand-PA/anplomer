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

    // ---- health -------------------------------------------------------
    // Player and mob hit points, attack and size are authored in
    // art/stats.json and baked into Stats.java (see tools/designer). These are
    // the fixed damage numbers the simulation itself applies.
    /** Damage from standing in lava or on spikes. */
    public static final int HAZARD_DAMAGE = 25;
    /** Falling out of the level: costs HP and returns you to the checkpoint. */
    public static final int PIT_DAMAGE = 35;
    /** Damage a stomp deals; large enough to one-shot the default mobs. */
    public static final int STOMP_DAMAGE = 25;
    /** Seconds an enemy flashes after a hit that did not kill it. */
    public static final float HIT_FLASH_TIME = 0.16f;
    /** Height of a mob's health bar, in world units. */
    public static final float HP_BAR_H = 7f;
    /** Gap between a mob's head and its health bar. */
    public static final float HP_BAR_GAP = 10f;

    // ---- enemies ------------------------------------------------------
    // Fallbacks for the level grid: live values come from Stats.java (baked
    // from art/stats.json), and these numbers match that file's defaults.
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

    // ---- combat -------------------------------------------------------
    /** Seconds the sword hitbox stays active (and the swing animates). */
    public static final float SWORD_TIME = 0.20f;
    /** Seconds between two swings; the disc may be held to swing repeatedly. */
    public static final float SWORD_COOLDOWN = 0.30f;
    /** Reach of the standing chop and the air arcs. */
    public static final float SWORD_REACH = 58f;
    /** Reach of the running slash; it lances further ahead. */
    public static final float SWORD_REACH_RUN = 80f;
    /** Height of the standing chop arc. */
    public static final float SWORD_HEIGHT = 74f;
    /** Speed above which a grounded swing becomes the running slash. */
    public static final float SWORD_RUN_SPEED = 90f;
    /** Score for an enemy defeated by the sword or a secondary weapon. */
    public static final int SCORE_ATTACK = 250;

    public static final float BOW_COOLDOWN = 0.42f;
    public static final float ARROW_SPEED = 860f;
    public static final float ARROW_GRAVITY = 360f;
    public static final float ARROW_LIFE = 1.05f;
    public static final float ARROW_W = 30f;
    public static final float ARROW_H = 10f;

    public static final float BOOMERANG_COOLDOWN = 0.70f;
    public static final float BOOMERANG_SPEED = 560f;
    public static final float BOOMERANG_RETURN_SPEED = 760f;
    public static final float BOOMERANG_LIFT = 90f;
    public static final float BOOMERANG_GRAVITY = 260f;
    /** Seconds before a boomerang turns for home. */
    public static final float BOOMERANG_OUT_TIME = 0.42f;
    public static final float BOOMERANG_LIFE = 2.4f;
    /** Distance at which the returning boomerang is caught. */
    public static final float BOOMERANG_CATCH_R = 26f;
    public static final float BOOMERANG_W = 26f;
    public static final float BOOMERANG_H = 26f;

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

}
