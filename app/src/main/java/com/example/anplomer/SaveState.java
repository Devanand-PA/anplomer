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
    public static final int VERSION = 1;

    /** 0-based level to continue on. */
    public int levelIndex;
    /** Number of levels unlocked (1-based count, min 1). */
    public int unlocked = 1;
    /** Selected game mode ordinal (Game.Mode). */
    public int mode;
    /** Sound preference. */
    public boolean sound = true;

    public SaveState() {
    }

    private SaveState(int levelIndex, int unlocked, int mode, boolean sound) {
        this.levelIndex = levelIndex;
        this.unlocked = unlocked;
        this.mode = mode;
        this.sound = sound;
    }

    public static SaveState defaults() {
        return new SaveState(0, 1, 0, true);
    }

    /**
     * Compact pipe-separated encoding. Field order is fixed; unknown trailing
     * fields are ignored so newer saves still load their prefix.
     */
    public String encode() {
        return VERSION + "|" + levelIndex + "|" + unlocked + "|" + mode + "|" + (sound ? 1 : 0);
    }

    /** Parses a blob; any malformed or empty input yields defaults. */
    public static SaveState decode(String blob) {
        SaveState s = defaults();
        if (blob == null || blob.isEmpty()) {
            return s;
        }
        try {
            String[] f = blob.split("\\|", -1);
            if (Integer.parseInt(f[0]) != VERSION || f.length < 5) {
                return s;
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
