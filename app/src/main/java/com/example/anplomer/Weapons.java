package com.example.anplomer;

/**
 * The weapon catalogue. The primary weapon is always the sword; the secondary
 * weapon is swappable per career and persisted with the profile.
 *
 * Ids are saved in the profile blob, so never renumber them.
 */
public final class Weapons {

    public static final int NONE = 0;
    public static final int BOW = 1;
    public static final int BOOMERANG = 2;

    public static final int SECONDARY_COUNT = 3;

    private Weapons() {
    }

    public static final String[] SECONDARY_NAME = {
            "NO WEAPON",
            "BOW",
            "BOOMERANG",
    };

    /** One short line per weapon for the loadout screen. */
    public static final String[] SECONDARY_DESC = {
            "sword only",
            "long, straight shot",
            "flies out and comes back",
    };

    /** Clamps an arbitrary id into the catalogue, defaulting to {@link #NONE}. */
    public static int clampSecondary(int id) {
        return (id < 0 || id >= SECONDARY_COUNT) ? NONE : id;
    }

    public static String secondaryName(int id) {
        return SECONDARY_NAME[clampSecondary(id)];
    }

    public static String secondaryDesc(int id) {
        return SECONDARY_DESC[clampSecondary(id)];
    }

    /** True when the weapon actually fires a projectile. */
    public static boolean isRanged(int id) {
        return id == BOW || id == BOOMERANG;
    }

    /** Seconds between two shots of the secondary weapon. */
    public static float cooldown(int id) {
        Stats.Weapon w = Stats.findWeapon(statsId(id));
        if (w != null) {
            return w.cooldown;
        }
        switch (id) {
            case BOW:
                return Constants.BOW_COOLDOWN;
            case BOOMERANG:
                return Constants.BOOMERANG_COOLDOWN;
            default:
                return 0f;
        }
    }

    /** Damage one projectile of this kind deals (tuned in art/stats.json). */
    public static int projectileDamage(int kind) {
        Stats.Weapon w = Stats.findWeapon(kind == Projectile.BOOMERANG
                ? "boomerang" : "bow");
        // No stats entry (older stats.json): the pre-stats behaviour was
        // "every projectile hits with the hero's attack".
        return w != null ? w.damage : Stats.Hero.ATTACK;
    }

    /** The stats.json id for a catalogue weapon, or null for the sword/NONE. */
    private static String statsId(int id) {
        switch (id) {
            case BOW:
                return "bow";
            case BOOMERANG:
                return "boomerang";
            default:
                return null;
        }
    }
}
