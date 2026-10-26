// GENERATED FILE - DO NOT EDIT BY HAND.
// Source:      art/art.json
// Generator:   tools/artgen/svg2java.py
// Regenerate:  python3 tools/artgen/svg2java.py --manifest art/art.json
//
// Entity -> baked-artwork registry. Renderer asks has()/draw() whether
// an entity has generated art and falls back to its hand-drawn vector
// code when it does not. Roles (partRole) drive the name-mapped limb
// animation; rolePivot* give each role group its shared joint.
package com.example.anplomer;

import android.graphics.Canvas;
import android.graphics.Paint;

/** Registry of entities that have baked artwork (from the art manifest). */
public final class ArtBindings {

    private ArtBindings() {
    }

    public static final int ROLE_STATIC = 0;
    public static final int ROLE_LIMB_A = 1;
    public static final int ROLE_LIMB_B = 2;
    public static final int ROLE_ARM_A = 3;
    public static final int ROLE_ARM_B = 4;
    public static final int ROLE_WING_A = 5;
    public static final int ROLE_WING_B = 6;
    public static final int ROLE_FLUTTER = 7;

    private static final int[] PLAYER_ROLES = {1, 2, 1, 2, 0, 0, 0, 0, 4, 3, 4, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final float[] PLAYER_ROLE_PIVOTS = {-0.040f, -45.033f, -3.717f, -19.713f, 3.151f, -19.713f, 11.721f, -41.403f, -11.721f, -41.403f, 0.000f, 0.000f, 0.000f, 0.000f, 0.000f, 0.000f};

    /** @return true when {@code entity} has artwork the renderer can draw */
    public static boolean has(String entity) {
        switch (entity) {
            case "player":
                return true;
            default:
                return false;
        }
    }

    /** Fitted ink-box width of the bound artwork; 0 when there is none. */
    public static float width(String entity) {
        switch (entity) {
            case "player":
                return ArtPlayer.WIDTH;
            default:
                return 0f;
        }
    }

    /** Fitted ink-box height of the bound artwork; 0 when there is none. */
    public static float height(String entity) {
        switch (entity) {
            case "player":
                return ArtPlayer.HEIGHT;
            default:
                return 0f;
        }
    }

    /**
     * Draws the bound artwork with its manifest anchor at (x, y),
     * scaled about that point. No-op when the entity has no artwork.
     */
    public static void draw(String entity, Canvas c, Paint fill,
                            Paint stroke, float x, float y, float scale) {
        switch (entity) {
            case "player":
                ArtPlayer.draw(c, fill, stroke, x, y, scale);
                return;
            default:
        }
    }

    /** Part count of the bound artwork; 0 when the entity has none. */
    public static int partCount(String entity) {
        switch (entity) {
            case "player":
                return PLAYER_ROLES.length;
            default:
                return 0;
        }
    }

    /** Layer name of part i (informational; roles come from partRole). */
    public static String partName(String entity, int i) {
        switch (entity) {
            case "player":
                return ArtPlayer.partName(i);
            default:
                return "";
        }
    }

    /** Per-part top-centre pivot in art units (informational). */
    public static float partPivotX(String entity, int i) {
        switch (entity) {
            case "player":
                return ArtPlayer.partPivotX(i);
            default:
                return 0f;
        }
    }

    /** @see #partPivotX */
    public static float partPivotY(String entity, int i) {
        switch (entity) {
            case "player":
                return ArtPlayer.partPivotY(i);
            default:
                return 0f;
        }
    }

    /**
     * Draws one part of the bound artwork. Renderer rotates animated
     * roles about the shared pivot, then calls this.
     */
    public static void drawPart(String entity, int i, Canvas c,
                               Paint fill, Paint stroke,
                               float x, float y, float scale) {
        switch (entity) {
            case "player":
                ArtPlayer.drawPart(i, c, fill, stroke, x, y, scale);
                return;
            default:
        }
    }

    /**
     * The animation role of part i (one of the ROLE_* constants).
     * Explicit designer mapping first, layer-name inference fallback.
     */
    public static int partRole(String entity, int i) {
        switch (entity) {
            case "player":
                return PLAYER_ROLES[i];
            default:
                return 0;
        }
    }

    /** Shared pivot x of a role group, in art units. */
    public static float rolePivotX(String entity, int role) {
        switch (entity) {
            case "player":
                return PLAYER_ROLE_PIVOTS[role * 2];
            default:
                return 0f;
        }
    }

    /** Shared pivot y of a role group, in art units. */
    public static float rolePivotY(String entity, int role) {
        switch (entity) {
            case "player":
                return PLAYER_ROLE_PIVOTS[role * 2 + 1];
            default:
                return 0f;
        }
    }
}
