// GENERATED FILE - DO NOT EDIT BY HAND.
// Source:      art/stats.json
// Generator:   tools/statgen/stats2java.py
// Regenerate:  python3 tools/statgen/stats2java.py art/stats.json
//
// Player and mob numbers. Edit art/stats.json (or use tools/designer) and
// regenerate; these constants are what the game actually reads.
package com.example.anplomer;

/** Baked gameplay stats: the hero plus every mob archetype. */
public final class Stats {

    private Stats() {
    }

    /** The hero. */
    public static final class Hero {
        public static final int MAX_HP = 100;
        public static final int ATTACK = 25;
        public static final float HURT_INVULN = 0.8f;

        private Hero() {
        }
    }

    /** One mob archetype; immutable. */
    public static final class Mob {
        public final String id;
        public final String name;
        public final int maxHp;
        public final int attack;
        public final float speed;
        public final float width;
        public final float height;

        Mob(String id, String name, int maxHp, int attack, float speed,
                float width, float height) {
            this.id = id;
            this.name = name;
            this.maxHp = maxHp;
            this.attack = attack;
            this.speed = speed;
            this.width = width;
            this.height = height;
        }

        @Override
        public String toString() {
            return name + "(" + maxHp + "hp/" + attack + "atk)";
        }
    }

    /** Every mob, in stats.json order. */
    public static final Mob[] MOBS = {
        new Mob("walker", "Walker", 20, 15, 95f, 48f, 42f),
        new Mob("flyer", "Flyer", 12, 12, 120f, 46f, 38f),
    };

    /**
     * Looks a mob up by id.
     *
     * @return the matching mob, or {@code null} when the id is unknown
     */
    public static Mob find(String id) {
        for (Mob m : MOBS) {
            if (m.id.equals(id)) {
                return m;
            }
        }
        return null;
    }

    /**
     * Looks a mob up by id, falling back to the first mob so callers never
     * have to null-check in the frame loop.
     */
    public static Mob mob(String id) {
        Mob m = find(id);
        return m != null ? m : MOBS[0];
    }
}
