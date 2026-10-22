package com.example.anplomer;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything that persists between sessions, kept as plain data so the
 * headless tests can exercise it without Android.
 *
 * A save holds global preferences (sound, music, button size, game mode) plus
 * a list of {@link Profile} careers. Each career owns its own progress, wallet
 * and purchased upgrades, so two players on one phone never share coins or
 * levels. The active career index is persisted too.
 *
 * <h2>Format</h2>
 * Version 3 is one pipe-separated string; the profile list is a semicolon
 * separated list of comma-separated records:
 *
 * <pre>
 * 3|sound|music|buttons|mode|active|name,color,coins,level,unlocked,cleared,
 *                                         coinsCollected,bestScore,stomps,deaths,
 *                                         playMillis,u0,u1,u2,u3,u4,u5;...
 * </pre>
 *
 * Version 2 (single implicit career, no wallet/upgrades) and version 1 (before
 * settings existed) blobs still load: their level, mode, sound and settings are
 * carried into a single "PLAYER 1" career. Anything malformed falls back to
 * defaults rather than losing the ability to start the game.
 */
public final class SaveState {

    /** Current format marker; bump when the encoding changes. */
    public static final int VERSION = 3;

    /** Global preferences (shared by every career). */
    public boolean sound = true;
    public int music = 70;
    public int buttons = 100;
    /** Selected game mode ordinal (Game.Mode). */
    public int mode;

    /** Careers, in display order; never empty. */
    public final List<Profile> profiles = new ArrayList<>();
    /** Index of the active career in {@link #profiles}. */
    public int active;

    /** Clamp bounds shared with the settings UI. */
    public static final int MUSIC_MIN = 0;
    public static final int MUSIC_MAX = 100;
    public static final int BUTTONS_MIN = 50;
    public static final int BUTTONS_MAX = 150;

    /** Most careers a save may hold (the career screen lays them all out). */
    public static final int MAX_PROFILES = 6;

    public SaveState() {
    }

    public static SaveState defaults() {
        SaveState s = new SaveState();
        s.profiles.add(new Profile("PLAYER 1", 0));
        s.active = 0;
        return s;
    }

    /** Active career; never null after {@link #decode} or {@link #defaults}. */
    public Profile profile() {
        if (profiles.isEmpty()) {
            profiles.add(new Profile("PLAYER 1", 0));
        }
        active = clamp(active, 0, profiles.size() - 1);
        return profiles.get(active);
    }

    /**
     * Replaces this save's contents in place, keeping the {@link #profiles}
     * list identity so live views (the game, the career screen) never hold a
     * stale list after a reset.
     */
    public void copyFrom(SaveState other) {
        sound = other.sound;
        music = other.music;
        buttons = other.buttons;
        mode = other.mode;
        profiles.clear();
        for (Profile p : other.profiles) {
            profiles.add(Profile.copyOf(p));
        }
        if (profiles.isEmpty()) {
            profiles.add(new Profile("PLAYER 1", 0));
        }
        active = clamp(other.active, 0, profiles.size() - 1);
    }

    public void addProfile(Profile p) {
        if (profiles.size() >= MAX_PROFILES) {
            return;
        }
        profiles.add(p);
        active = profiles.size() - 1;
    }

    /** Removes the career at {@code index}; the last career can never be removed. */
    public boolean removeProfile(int index) {
        if (profiles.size() <= 1 || index < 0 || index >= profiles.size()) {
            return false;
        }
        profiles.remove(index);
        if (active >= profiles.size()) {
            active = profiles.size() - 1;
        }
        return true;
    }

    // ---- encoding ------------------------------------------------------

    public String encode() {
        StringBuilder sb = new StringBuilder(160);
        sb.append(VERSION).append('|')
                .append(sound ? 1 : 0).append('|')
                .append(music).append('|')
                .append(buttons).append('|')
                .append(mode).append('|')
                .append(active).append('|');
        for (int i = 0; i < profiles.size(); i++) {
            if (i > 0) {
                sb.append(';');
            }
            appendProfile(sb, profiles.get(i));
        }
        return sb.toString();
    }

    private static void appendProfile(StringBuilder sb, Profile p) {
        sb.append(Profile.sanitizeName(p.name)).append(',')
                .append(p.colorIndex).append(',')
                .append(p.coins).append(',')
                .append(p.levelIndex).append(',')
                .append(p.unlocked).append(',')
                .append(p.levelsCleared).append(',')
                .append(p.coinsCollected).append(',')
                .append(p.bestScore).append(',')
                .append(p.stomps).append(',')
                .append(p.deaths).append(',')
                .append((long) Math.max(0f, p.playSeconds * 1000f));
        for (int id = 0; id < Upgrades.COUNT; id++) {
            sb.append(',').append(p.upgradeLevel(id));
        }
    }

    /**
     * Parses a blob; any malformed or empty input yields defaults. Older
     * versions migrate into a single career.
     */
    public static SaveState decode(String blob) {
        if (blob == null || blob.isEmpty()) {
            return defaults();
        }
        try {
            String[] f = blob.split("\\|", -1);
            int version = Integer.parseInt(f[0].trim());
            if (version == VERSION) {
                return decodeV3(f);
            }
            if (version == 2 || version == 1) {
                return migrateOld(version, f);
            }
        } catch (RuntimeException malformed) {
            // fall through to defaults
        }
        return defaults();
    }

    private static SaveState decodeV3(String[] f) {
        if (f.length < 7) {
            return defaults();
        }
        SaveState s = new SaveState();
        s.sound = Integer.parseInt(f[1].trim()) != 0;
        s.music = clamp(Integer.parseInt(f[2].trim()), MUSIC_MIN, MUSIC_MAX);
        s.buttons = clamp(Integer.parseInt(f[3].trim()), BUTTONS_MIN, BUTTONS_MAX);
        s.mode = clamp(Integer.parseInt(f[4].trim()), 0, Game.Mode.values().length - 1);
        int active = Integer.parseInt(f[5].trim());

        // Re-join the tail: a malformed '|' inside a record must not shift
        // every field after it, it must only drop that record.
        StringBuilder rest = new StringBuilder();
        for (int i = 6; i < f.length; i++) {
            if (i > 6) {
                rest.append('|');
            }
            rest.append(f[i]);
        }
        for (String record : rest.toString().split(";", -1)) {
            if (record.trim().isEmpty() || s.profiles.size() >= MAX_PROFILES) {
                continue;
            }
            Profile p = decodeProfile(record);
            if (p != null) {
                s.profiles.add(p);
            }
        }
        if (s.profiles.isEmpty()) {
            return defaults();
        }
        s.active = clamp(active, 0, s.profiles.size() - 1);
        return s;
    }

    private static Profile decodeProfile(String record) {
        String[] parts = record.split(",", -1);
        if (parts.length < 17) {
            return null;
        }
        try {
            String name = Profile.sanitizeName(parts[0]);
            int color = Integer.parseInt(parts[1].trim());
            Profile p = new Profile(name, color);
            p.coins = Math.max(0, Integer.parseInt(parts[2].trim()));
            p.levelIndex = clamp(Integer.parseInt(parts[3].trim()), 0, Levels.TEXT.length - 1);
            p.unlocked = clamp(Integer.parseInt(parts[4].trim()), 1, Levels.TEXT.length);
            p.levelsCleared = Math.max(0, Integer.parseInt(parts[5].trim()));
            p.coinsCollected = Math.max(0, Integer.parseInt(parts[6].trim()));
            p.bestScore = Math.max(0, Integer.parseInt(parts[7].trim()));
            p.stomps = Math.max(0, Integer.parseInt(parts[8].trim()));
            p.deaths = Math.max(0, Integer.parseInt(parts[9].trim()));
            long millis = Math.max(0L, Long.parseLong(parts[10].trim()));
            p.playSeconds = millis / 1000f;
            for (int id = 0; id < Upgrades.COUNT; id++) {
                p.upgrades[id] = clamp(Integer.parseInt(parts[11 + id].trim()),
                        0, Upgrades.MAX[id]);
            }
            return p;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    /** v2: level|unlocked|mode|sound|music|buttons. v1: + no settings. */
    private static SaveState migrateOld(int version, String[] f) {
        SaveState s = defaults();
        if (version == 2) {
            if (f.length < 7) {
                return defaults();
            }
            s.music = clamp(Integer.parseInt(f[5].trim()), MUSIC_MIN, MUSIC_MAX);
            s.buttons = clamp(Integer.parseInt(f[6].trim()), BUTTONS_MIN, BUTTONS_MAX);
        } else if (f.length < 5) {
            return defaults();
        }
        Profile p = s.profiles.get(0);
        p.levelIndex = clamp(Integer.parseInt(f[1].trim()), 0, Levels.TEXT.length - 1);
        p.unlocked = clamp(Integer.parseInt(f[2].trim()), 1, Levels.TEXT.length);
        s.mode = clamp(Integer.parseInt(f[3].trim()), 0, Game.Mode.values().length - 1);
        s.sound = Integer.parseInt(f[4].trim()) != 0;
        return s;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
