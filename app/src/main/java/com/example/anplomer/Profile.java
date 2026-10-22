package com.example.anplomer;

/**
 * One local career: its own progress, wallet, stats and purchased upgrades.
 *
 * Everything is plain data so the headless suites can create, compare and
 * persist profiles without Android. Effects are derived from {@link #upgrades}
 * here (never in the renderer), so the store UI and the simulation can never
 * disagree about what a level of an upgrade is worth.
 */
public final class Profile {

    /** Number of avatar tints offered to the renderer / career screen. */
    public static final int COLORS = 6;

    /** Longest accepted name; keeps the career rows and the save blob compact. */
    public static final int NAME_MAX = 12;

    public String name;
    public int colorIndex;
    public int coins;
    public int levelIndex;
    public int unlocked = 1;

    // career stats
    public int levelsCleared;
    public int coinsCollected;
    public int bestScore;
    public int stomps;
    public int deaths;
    public float playSeconds;

    /** Upgrade levels, indexed by the {@link Upgrades} id constants. */
    public final int[] upgrades = new int[Upgrades.COUNT];

    public Profile(String name, int colorIndex) {
        this.name = sanitizeName(name);
        this.colorIndex = Math.floorMod(colorIndex, COLORS);
    }

    /** Copy of another profile (used by the reset flow and tests). */
    public static Profile copyOf(Profile other) {
        Profile p = new Profile(other.name, other.colorIndex);
        p.coins = other.coins;
        p.levelIndex = other.levelIndex;
        p.unlocked = other.unlocked;
        p.levelsCleared = other.levelsCleared;
        p.coinsCollected = other.coinsCollected;
        p.bestScore = other.bestScore;
        p.stomps = other.stomps;
        p.deaths = other.deaths;
        p.playSeconds = other.playSeconds;
        System.arraycopy(other.upgrades, 0, p.upgrades, 0, Upgrades.COUNT);
        return p;
    }

    // ---- upgrades ------------------------------------------------------

    public int upgradeLevel(int id) {
        if (id < 0 || id >= Upgrades.COUNT) {
            return 0;
        }
        return Math.max(0, Math.min(Upgrades.MAX[id], upgrades[id]));
    }

    /** Price of the next level, or -1 when the upgrade is maxed. */
    public int nextPrice(int id) {
        if (id < 0 || id >= Upgrades.COUNT) {
            return -1;
        }
        int level = upgradeLevel(id);
        if (level >= Upgrades.MAX[id]) {
            return -1;
        }
        return Upgrades.price(id, level);
    }

    public boolean canAfford(int id) {
        int price = nextPrice(id);
        return price >= 0 && coins >= price;
    }

    /**
     * Buys the next level of an upgrade.
     *
     * @return true when the purchase happened (not maxed, enough coins)
     */
    public boolean buy(int id) {
        int price = nextPrice(id);
        if (price < 0 || coins < price) {
            return false;
        }
        coins -= price;
        upgrades[id] = upgradeLevel(id) + 1;
        return true;
    }

    // ---- derived effects ----------------------------------------------

    /** Run-speed multiplier from the SPEED upgrade. */
    public float runSpeedMul() {
        return 1f + Constants.UPGRADE_SPEED_STEP * upgradeLevel(Upgrades.SPEED);
    }

    /** Jump-velocity multiplier from the JUMP upgrade. */
    public float jumpMul() {
        return 1f + Constants.UPGRADE_JUMP_STEP * upgradeLevel(Upgrades.JUMP);
    }

    /** Extra starting lives from the LIVES upgrade. */
    public int extraLives() {
        return upgradeLevel(Upgrades.LIVES);
    }

    /** Starting lives for a fresh attempt at a level. */
    public int startLives() {
        return Math.min(Constants.MAX_LIVES,
                Constants.START_LIVES + extraLives());
    }

    /** Multiplier applied to timed power-up durations. */
    public float powerTimeMul() {
        return 1f + Constants.UPGRADE_POWER_STEP * upgradeLevel(Upgrades.POWER);
    }

    /** Always-on coin attraction radius from the MAGNET upgrade (0 = none). */
    public float magnetRadius() {
        return Constants.UPGRADE_MAGNET_STEP * upgradeLevel(Upgrades.MAGNET);
    }

    /** Shield charges granted at the start of every level. */
    public int startingShields() {
        return upgradeLevel(Upgrades.SHIELD);
    }

    // ---- stats ---------------------------------------------------------

    public void addPlaySeconds(float seconds) {
        if (seconds > 0f && Float.isFinite(seconds)) {
            playSeconds += seconds;
        }
    }

    public void recordScore(int score) {
        if (score > bestScore) {
            bestScore = score;
        }
    }

    /** Sanitises a player-entered name: trimmed, printable, bounded. */
    public static String sanitizeName(String raw) {
        if (raw == null) {
            return "PLAYER";
        }
        StringBuilder sb = new StringBuilder(NAME_MAX);
        for (int i = 0; i < raw.length() && sb.length() < NAME_MAX; i++) {
            char c = raw.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == ' ' || c == '-' || c == '_';
            if (ok) {
                sb.append(c);
            }
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? "PLAYER" : out;
    }

    /** Next free auto-name, e.g. PLAYER 3 when 1 and 2 exist. */
    public static String suggestName(java.util.List<Profile> existing) {
        for (int n = 1; n < 1000; n++) {
            String candidate = "PLAYER " + n;
            boolean taken = false;
            for (Profile p : existing) {
                if (p.name.equalsIgnoreCase(candidate)) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                return candidate;
            }
        }
        return "PLAYER";
    }
}
