// GENERATED FILE - DO NOT EDIT BY HAND.
// Source:      art/art.json
// Generator:   tools/artgen/svg2java.py
// Regenerate:  python3 tools/artgen/svg2java.py --manifest art/art.json
//
// Entity -> baked-artwork registry. Renderer asks has()/draw() whether
// an entity has generated art and falls back to its hand-drawn vector
// code when it does not.
package com.example.anplomer;

import android.graphics.Canvas;
import android.graphics.Paint;

/** Registry of entities that have baked artwork (from the art manifest). */
public final class ArtBindings {

    private ArtBindings() {
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
}
