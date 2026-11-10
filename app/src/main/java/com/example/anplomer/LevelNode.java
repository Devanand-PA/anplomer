package com.example.anplomer;

/**
 * One level as a node on the world map. Nodes are placed in unlock order
 * along a winding trail; tapping an unlocked node opens its loadout.
 */
public final class LevelNode {

    /** Index into {@link Levels#TEXT}. */
    public final int levelIndex;
    /** Position on the world map, in world units. */
    public final float worldX;
    public final float worldY;
    /** Display name, e.g. "EMERALD MEADOW". */
    public final String name;
    /** Par time in seconds. */
    public final int parTime;

    /** Visual radius of the node, in world units. */
    public static final float RADIUS = 46f;

    public LevelNode(int levelIndex, float worldX, float worldY, String name, int parTime) {
        this.levelIndex = levelIndex;
        this.worldX = worldX;
        this.worldY = worldY;
        this.name = name;
        this.parTime = parTime;
    }
}
