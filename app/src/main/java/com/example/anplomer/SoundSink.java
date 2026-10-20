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

    void play(String name);

    void setEnabled(boolean enabled);

    boolean isEnabled();
}
