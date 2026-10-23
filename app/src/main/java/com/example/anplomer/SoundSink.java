package com.example.anplomer;

/**
 * Sound abstraction so the simulation can run silently in the headless test.
 * Implementations receive symbolic effect names, never file paths.
 */
public interface SoundSink {

    String JUMP = "jump";
    String COIN = "coin";
    String STOMP = "stomp";
    String DIE = "die";
    String CLEAR = "clear";
    String SELECT = "select";
    String PAUSE = "pause";
    String CHECKPOINT = "checkpoint";
    String WIN = "win";
    String GAME_OVER = "gameover";
    String HURT = "hurt";
    String POWERUP = "powerup";
    String PURCHASE = "purchase";
    /** Sword swing whoosh. */
    String SWING = "swing";
    /** Weapon hit on an enemy. */
    String HIT = "hit";
    /** Bow shot. */
    String ARROW = "arrow";
    /** Boomerang throw. */
    String THROW = "throw";

    void play(String name);

    void setEnabled(boolean enabled);

    boolean isEnabled();

    /**
     * Music volume, 0..1. Implementations without music may ignore it.
     * The default keeps third-party sinks (headless tests) compiling.
     */
    default void setMusicVolume(float volume) {
    }

    /** Called when the host activity is no longer visible. */
    default void suspendAudio() {
    }

    /** Called when the host activity becomes visible again. */
    default void resumeAudio() {
    }
}
