package com.example.anplomer;

/**
 * Everything that persists between sessions, kept as plain data so the
 * headless tests can exercise it without Android.
 *
 * The game auto-saves the level you are on: {@link #levelIndex} is written
 * every time a level is entered, so losing all lives restarts the last saved
 * level rather than the first one.
 */
public final class SaveState {

    /** Current format marker; bump when the encoding changes. */
    public static final int VERSION = 2;

    /** 0-based level to continue on. */
    public int levelIndex;
    /** Number of levels unlocked (1-based count, min 1). */
    public int unlocked = 1;
    /** Selected game mode ordinal (Game.Mode). */
    public int mode;
    /** Sound preference. */
    public boolean sound = true;
    /** Music volume, percent (0..100). */
    public int music = 70;
    /** Movement button size, percent (50..150). */
    public int buttons = 100;

    /** Clamp bounds shared with the settings UI. */
    public static final int MUSIC_MIN = 0;
    public static final int MUSIC_MAX = 100;
    public static final int BUTTONS_MIN = 50;
    public static final int BUTTONS_MAX = 150;

    public SaveState() {
    }

    private SaveState(int levelIndex, int unlocked, int mode, boolean sound,
                      int music, int buttons) {
        this.levelIndex = levelIndex;
        this.unlocked = unlocked;
        this.mode = mode;
        this.sound = sound;
        this.music = music;
        this.buttons = buttons;
    }

    public static SaveState defaults() {
        return new SaveState(0, 1, 0, true, 70, 100);
    }

    /**
     * Compact pipe-separated encoding. Field order is fixed; unknown trailing
     * fields are ignored so newer saves still load their prefix.
     */
    public String encode() {
        return VERSION + "|" + levelIndex + "|" + unlocked + "|" + mode + "|" + (sound ? 1 : 0)
                + "|" + music + "|" + buttons;
    }

    /**
     * Parses a blob; any malformed or empty input yields defaults. Version 1
     * blobs (before the settings menu existed) load with default settings.
     */
    public static SaveState decode(String blob) {
        SaveState s = defaults();
        if (blob == null || blob.isEmpty()) {
            return s;
        }
        try {
            String[] f = blob.split("\\|", -1);
            int version = Integer.parseInt(f[0]);
            if (version == VERSION && f.length >= 7) {
                s.music = clamp(Integer.parseInt(f[5]), MUSIC_MIN, MUSIC_MAX);
                s.buttons = clamp(Integer.parseInt(f[6]), BUTTONS_MIN, BUTTONS_MAX);
            } else if (version != 1 || f.length < 5) {
                return s;   // unrecognised: all defaults
            }
            s.levelIndex = clamp(Integer.parseInt(f[1]), 0, Levels.TEXT.length - 1);
            s.unlocked = clamp(Integer.parseInt(f[2]), 1, Levels.TEXT.length);
            s.mode = clamp(Integer.parseInt(f[3]), 0, Game.Mode.values().length - 1);
            s.sound = Integer.parseInt(f[4]) != 0;
        } catch (RuntimeException malformed) {
            return defaults();
        }
        return s;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
