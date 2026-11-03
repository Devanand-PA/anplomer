// GENERATED FILE - DO NOT EDIT BY HAND.
// Source:      art/art.json
// Generator:   tools/artgen/svg2java.py
// Regenerate:  python3 tools/artgen/svg2java.py --manifest art/art.json
//
// Entity -> baked-artwork registry. Renderer asks has()/draw() whether
// an entity has generated art and falls back to its hand-drawn vector
// code when it does. The animation arrays (partChannel, partPhase,
// partAmplitude, partPivot*) drive the channel-based limb animation:
// angle = amplitude * sin(drive + phase) around the part's pivot.
package com.example.anplomer;

import android.graphics.Canvas;
import android.graphics.Paint;

/** Registry of entities that have baked artwork (from the art manifest). */
public final class ArtBindings {

    private ArtBindings() {
    }

    public static final int CHANNEL_NONE = 0;
    public static final int CHANNEL_LIMB = 1;
    public static final int CHANNEL_ARM = 2;
    public static final int CHANNEL_WING = 3;
    public static final int CHANNEL_FLUTTER = 4;

    private static final int[] PLAYER_CHANNELS = {1, 1, 1, 1, 0, 0, 0, 0, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final float[] PLAYER_PHASES = {0.00f, 180.00f, 0.00f, 180.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 180.00f, 0.00f, 180.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f};
    private static final float[] PLAYER_AMPS = {26.00f, 26.00f, 26.00f, 26.00f, 0.00f, 0.00f, 0.00f, 0.00f, 16.00f, 16.00f, 16.00f, 16.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f};
    private static final float[] PLAYER_PIVOTS = {-3.717f, -19.713f, 3.151f, -19.713f, -3.717f, -19.713f, 3.151f, -19.713f, 0.000f, 0.000f, 0.000f, 0.000f, 0.000f, 0.000f, 0.000f, 0.000f, -11.721f, -41.403f, 11.721f, -41.403f, -11.721f, -41.403f, 0.000f, 0.000f, 0.000f, 0.000f, 0.000f, 0.000f, 0.000f, 0.000f, 0.000f, 0.000f, 0.000f, 0.000f, 0.000f, 0.000f, 0.000f, 0.000f, 0.000f, 0.000f, 0.000f, 0.000f};

    public static String partName(String entity, int i) {
        switch (entity) {
            case "player":
            return ArtPlayer.partName(i);
            default:
                return "";
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

    /**
     * Draws one part of the bound artwork. The renderer transforms
     * the canvas for animated parts, then calls this.
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

    /** Part count of the bound artwork; 0 when the entity has none. */
    public static int partCount(String entity) {
        switch (entity) {
            case "player":
                return PLAYER_CHANNELS.length;
            default:
                return 0;
        }
    }

    /**
     * Motion channel of part i (CHANNEL_* constant).
     */
    public static int partChannel(String entity, int i) {
        switch (entity) {
            case "player":
            return PLAYER_CHANNELS[i];
            default:
                return 0;
        }
    }

    /**
     * Cycle position of part i, in degrees.
     */
    public static float partPhase(String entity, int i) {
        switch (entity) {
            case "player":
            return PLAYER_PHASES[i];
            default:
                return 0f;
        }
    }

    /**
     * Swing amplitude of part i, in degrees.
     */
    public static float partAmplitude(String entity, int i) {
        switch (entity) {
            case "player":
            return PLAYER_AMPS[i];
            default:
                return 0f;
        }
    }

    /**
     * x of the shared pivot part i swings around,
     * in art units.
     */
    public static float partPivotX(String entity, int i) {
        switch (entity) {
            case "player":
            return PLAYER_PIVOTS[i * 2];
            default:
                return 0f;
        }
    }

    /**
     * y of the shared pivot part i swings around,
     * in art units.
     */
    public static float partPivotY(String entity, int i) {
        switch (entity) {
            case "player":
            return PLAYER_PIVOTS[i * 2 + 1];
            default:
                return 0f;
        }
    }
}
