package com.example.anplomer;

import java.util.ArrayList;
import java.util.List;

/**
 * A parsed level: static tile grid plus the spawn descriptors embedded in it.
 *
 * <pre>
 *  '#' solid ground      '=' solid stone block      '^' spikes (hazard)
 *  '~' lava (hazard)     'o' coin                   'E' walker enemy
 *  'B' flyer enemy       'M' horizontal mover       'V' vertical mover
 *  'P' player start      'C' checkpoint             'G' goal flag
 *
 *  power-ups (all spawn in air, at the tile centre, like coins):
 *  'S' speed boots       'H' shield                 'A' coin magnet
 *  'J' double jump       'L' extra life
 * </pre>
 */
public final class Level {

    public static final char EMPTY = '.';
    public static final char GROUND = '#';
    public static final char STONE = '=';
    public static final char SPIKE = '^';
    public static final char LAVA = '~';
    public static final char COIN = 'o';
    public static final char WALKER = 'E';
    public static final char FLYER = 'B';
    public static final char MOVER_H = 'M';
    public static final char MOVER_V = 'V';
    public static final char START = 'P';
    public static final char CHECKPOINT = 'C';
    public static final char GOAL = 'G';

    // power-ups
    public static final char POWER_SPEED = 'S';
    public static final char POWER_SHIELD = 'H';
    public static final char POWER_MAGNET = 'A';
    public static final char POWER_JUMP = 'J';
    public static final char POWER_LIFE = 'L';

    public final int w;
    public final int h;
    private final char[] tiles;

    // spawn points
    public float startX, startY;
    public float goalX, goalY;           // world coords of goal tile (bottom-left)
    public final List<float[]> coinSpawns = new ArrayList<>();     // {x,y}
    public final List<float[]> walkerSpawns = new ArrayList<>();   // {x,y}
    public final List<float[]> flyerSpawns = new ArrayList<>();    // {x,y}
    public final List<float[]> moverSpawns = new ArrayList<>();    // {x,y,verticalFlag}
    public final List<float[]> checkpointSpawns = new ArrayList<>();
    /** Power-up spawns as {worldX, worldY, PowerUp.Type.ordinal()}. */
    public final List<float[]> powerUpSpawns = new ArrayList<>();

    /** coins already taken this life; persists across respawns. */
    private final boolean[] coinTaken;
    /** power-ups already taken this life; persists across respawns. */
    private final boolean[] powerUpTaken;

    /** par time in seconds for the level-clear time bonus. */
    public int parTime = 60;

    public Level(String[] rows, int parTime) {
        this.parTime = parTime;
        this.h = rows.length;
        int width = 0;
        for (String r : rows) {
            width = Math.max(width, r.length());
        }
        this.w = width;
        this.tiles = new char[w * h];
        boolean sawStart = false;
        boolean sawGoal = false;
        for (int y = 0; y < h; y++) {
            String row = rows[y];
            for (int x = 0; x < w; x++) {
                char c = x < row.length() ? row.charAt(x) : EMPTY;
                tiles[y * w + x] = c;
                float wx = x * Constants.TILE;
                float wy = y * Constants.TILE;
                switch (c) {
                    case START:
                        startX = wx + (Constants.TILE - Constants.PLAYER_W) / 2f;
                        startY = wy + Constants.TILE - Constants.PLAYER_H;
                        sawStart = true;
                        break;
                    case GOAL:
                        goalX = wx;
                        goalY = wy;
                        sawGoal = true;
                        break;
                    case COIN:
                        coinSpawns.add(new float[]{wx + Constants.TILE / 2f, wy + Constants.TILE / 2f});
                        break;
                    case WALKER:
                        walkerSpawns.add(new float[]{wx + (Constants.TILE - Constants.WALKER_W) / 2f,
                                wy + Constants.TILE - Constants.WALKER_H});
                        break;
                    case FLYER:
                        flyerSpawns.add(new float[]{wx + (Constants.TILE - Constants.FLYER_W) / 2f,
                                wy + (Constants.TILE - Constants.FLYER_H) / 2f});
                        break;
                    case MOVER_H:
                    case MOVER_V:
                        moverSpawns.add(new float[]{wx, wy, c == MOVER_V ? 1f : 0f});
                        break;
                    case CHECKPOINT:
                        checkpointSpawns.add(new float[]{wx, wy});
                        break;
                    case POWER_SPEED:
                    case POWER_SHIELD:
                    case POWER_MAGNET:
                    case POWER_JUMP:
                    case POWER_LIFE:
                        powerUpSpawns.add(new float[]{
                                wx + Constants.TILE / 2f,
                                wy + Constants.TILE / 2f,
                                powerUpOrdinal(c)});
                        break;
                    default:
                        break;
                }
            }
        }
        if (!sawStart) {
            startX = Constants.TILE;
            startY = (h - 2) * Constants.TILE;
        }
        if (!sawGoal) {
            goalX = (w - 3) * Constants.TILE;
            goalY = (h - 3) * Constants.TILE;
        }
        this.coinTaken = new boolean[coinSpawns.size()];
        this.powerUpTaken = new boolean[powerUpSpawns.size()];
    }

    /** Maps a level glyph to its {@link PowerUp.Type} ordinal. */
    private static float powerUpOrdinal(char c) {
        switch (c) {
            case POWER_SPEED:
                return PowerUp.Type.SPEED.ordinal();
            case POWER_SHIELD:
                return PowerUp.Type.SHIELD.ordinal();
            case POWER_MAGNET:
                return PowerUp.Type.MAGNET.ordinal();
            case POWER_JUMP:
                return PowerUp.Type.DOUBLE_JUMP.ordinal();
            case POWER_LIFE:
                return PowerUp.Type.EXTRA_LIFE.ordinal();
            default:
                return 0f;
        }
    }

    // ---- queries ------------------------------------------------------

    public char tileAt(int tx, int ty) {
        if (tx < 0 || tx >= w || ty < 0 || ty >= h) {
            // outside horizontally = wall, outside vertically = open air
            return tx < 0 || tx >= w ? GROUND : EMPTY;
        }
        return tiles[ty * w + tx];
    }

    public char tileAtWorld(float wx, float wy) {
        return tileAt((int) Math.floor(wx / Constants.TILE), (int) Math.floor(wy / Constants.TILE));
    }

    public boolean isSolid(int tx, int ty) {
        if (tx < 0 || tx >= w) {
            return true;   // side walls
        }
        if (ty < 0) {
            return false;  // open sky
        }
        if (ty >= h) {
            return false;  // open pit below
        }
        char c = tiles[ty * w + tx];
        return c == GROUND || c == STONE;
    }

    public boolean isHazard(int tx, int ty) {
        if (tx < 0 || tx >= w || ty < 0 || ty >= h) {
            return false;
        }
        char c = tiles[ty * w + tx];
        return c == SPIKE || c == LAVA;
    }

    /** True if any solid tile overlaps the given world-space rectangle. */
    public boolean solidOverlap(float x, float y, float bw, float bh) {
        int tx0 = (int) Math.floor(x / Constants.TILE);
        int tx1 = (int) Math.floor((x + bw - 0.001f) / Constants.TILE);
        int ty0 = (int) Math.floor(y / Constants.TILE);
        int ty1 = (int) Math.floor((y + bh - 0.001f) / Constants.TILE);
        for (int ty = ty0; ty <= ty1; ty++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                if (isSolid(tx, ty)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Hazard overlap test. Spikes only occupy the bottom part of their tile so
     * they read as ground-mounted, lava fills its tile.
     *
     * @return true when the rectangle touches a hazard.
     */
    public boolean hazardOverlap(float x, float y, float bw, float bh) {
        int tx0 = (int) Math.floor(x / Constants.TILE);
        int tx1 = (int) Math.floor((x + bw - 0.001f) / Constants.TILE);
        int ty0 = (int) Math.floor(y / Constants.TILE);
        int ty1 = (int) Math.floor((y + bh - 0.001f) / Constants.TILE);
        for (int ty = ty0; ty <= ty1; ty++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                char c = tileAt(tx, ty);
                float hx, hy, hw, hh;
                if (c == SPIKE) {
                    hx = tx * Constants.TILE + 6f;
                    hy = ty * Constants.TILE + Constants.TILE * 0.55f;
                    hw = Constants.TILE - 12f;
                    hh = Constants.TILE * 0.45f;
                } else if (c == LAVA) {
                    hx = tx * Constants.TILE;
                    hy = ty * Constants.TILE;
                    hw = Constants.TILE;
                    hh = Constants.TILE;
                } else {
                    continue;
                }
                if (rectsOverlap(x, y, bw, bh, hx, hy, hw, hh)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean rectsOverlap(float ax, float ay, float aw, float ah,
                                       float bx, float by, float bw, float bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    // ---- coins --------------------------------------------------------

    public boolean coinTaken(int index) {
        return coinTaken[index];
    }

    public void takeCoin(int index) {
        if (index >= 0 && index < coinTaken.length && !coinTaken[index]) {
            coinTaken[index] = true;
            coinsTakenCount++;
        }
    }

    public void resetCoins() {
        java.util.Arrays.fill(coinTaken, false);
        coinsTakenCount = 0;
    }

    public int coinsTaken() {
        int n = 0;
        for (boolean b : coinTaken) {
            if (b) {
                n++;
            }
        }
        return n;
    }

    public int coinCount() {
        return coinTaken.length;
    }

    // ---- power-ups ----------------------------------------------------

    public boolean powerUpTaken(int index) {
        return index >= 0 && index < powerUpTaken.length && powerUpTaken[index];
    }

    public void takePowerUp(int index) {
        if (index >= 0 && index < powerUpTaken.length && !powerUpTaken[index]) {
            powerUpTaken[index] = true;
            powerUpsTakenCount++;
        }
    }

    public int powerUpCount() {
        return powerUpTaken.length;
    }

    /** Cached coin count so the HUD can ask every frame without an O(n) scan. */
    private int coinsTakenCount;
    /** Cached power-up count, same reason. */
    private int powerUpsTakenCount;

    public int powerUpsTakenFast() {
        return powerUpsTakenCount;
    }

    /** Number of coins taken, maintained incrementally. */
    public int coinsTakenFast() {
        return coinsTakenCount;
    }
}
