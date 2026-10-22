package com.example.anplomer;

/**
 * The permanent upgrade catalogue sold in the career store. Prices and effect
 * steps are pure data; {@link Profile} derives the actual multipliers.
 *
 * Ids are also array positions in {@link Profile#upgrades}, so the save blob
 * and the store rows are ordered by these constants — never renumber them.
 */
public final class Upgrades {

    public static final int SPEED = 0;
    public static final int JUMP = 1;
    public static final int LIVES = 2;
    public static final int MAGNET = 3;
    public static final int POWER = 4;
    public static final int SHIELD = 5;

    public static final int COUNT = 6;

    private Upgrades() {
    }

    public static final String[] NAME = {
            "SPEED BOOTS",
            "JUMP SPRINGS",
            "EXTRA LIVES",
            "COIN MAGNET",
            "POWER TIMER",
            "STARTER SHIELD",
    };

    public static final String[] DESC = {
            "run faster",
            "jump higher",
            "start with more lives",
            "coins come to you",
            "power-ups last longer",
            "begin each level shielded",
    };

    public static final int[] MAX = {5, 5, 3, 3, 4, 2};

    /** Coins for the first level of each upgrade. */
    private static final int[] BASE_PRICE = {25, 25, 60, 40, 35, 90};

    /** Added to the price for every level already owned. */
    private static final int[] PRICE_STEP = {20, 20, 60, 35, 30, 90};

    /**
     * Price of buying the level after {@code ownedLevel} of upgrade {@code id}.
     *
     * @param ownedLevel 0-based level already purchased
     * @return coins required, or -1 when the upgrade is already maxed
     */
    public static int price(int id, int ownedLevel) {
        if (id < 0 || id >= COUNT) {
            return -1;
        }
        int owned = Math.max(0, Math.min(MAX[id], ownedLevel));
        if (owned >= MAX[id]) {
            return -1;
        }
        return BASE_PRICE[id] + PRICE_STEP[id] * owned;
    }

    /** Short effect description for the level about to be bought. */
    public static String effect(int id, int nextLevel) {
        if (id < 0 || id >= COUNT) {
            return "";
        }
        switch (id) {
            case SPEED:
                return "+" + Math.round(Constants.UPGRADE_SPEED_STEP * nextLevel * 100f) + "% RUN";
            case JUMP:
                return "+" + Math.round(Constants.UPGRADE_JUMP_STEP * nextLevel * 100f) + "% JUMP";
            case LIVES:
                return "+" + nextLevel + " LIFE" + (nextLevel == 1 ? "" : "S");
            case MAGNET:
                return "MAGNET " + Math.round(Constants.UPGRADE_MAGNET_STEP * nextLevel) + "PX";
            case POWER:
                return "+" + Math.round(Constants.UPGRADE_POWER_STEP * nextLevel * 100f) + "% DURATION";
            case SHIELD:
                return nextLevel + " SHIELD" + (nextLevel == 1 ? "" : "S");
            default:
                return "";
        }
    }

    /** Level pips as a compact ASCII string, e.g. "███.." style. */
    public static String pips(int id, int level) {
        if (id < 0 || id >= COUNT) {
            return "";
        }
        StringBuilder sb = new StringBuilder(MAX[id]);
        for (int i = 0; i < MAX[id]; i++) {
            sb.append(i < level ? '#' : '.');
        }
        return sb.toString();
    }
}
