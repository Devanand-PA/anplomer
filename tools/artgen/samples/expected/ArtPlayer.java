// GENERATED FILE - DO NOT EDIT BY HAND.
// Source:      tools/artgen/samples/player.svg
// Generator:   tools/artgen/svg2java.py
// Regenerate:  python3 tools/artgen/svg2java.py tools/artgen/samples/player.svg --class ArtPlayer --fit 26x36
//
// Segments are baked to absolute M/L/Q/C/Z in game units (y down);
// `--fit` moved the artwork's anchor to the origin, so drawing needs
// no extra scaling or offset.
package com.example.anplomer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

public final class ArtPlayer {

    /** Artwork extents in game units (informational, not the entity size). */
    public static final float WIDTH = 18.63f;
    public static final float HEIGHT = 36f;

    /** One filled/stroked sub-path. {@code color == 0} means "no fill". */
    public static final class Part {
        public final String name;
        public final int color;
        public final int strokeColor;
        public final float strokeWidth;
        public final Path path;
        /** Top-centre of the part's ink box in art units: joint pivot. */
        public final float pivotX;
        public final float pivotY;

        Part(String name, int color, int strokeColor, float strokeWidth,
             Path path, float pivotX, float pivotY) {
            this.name = name;
            this.color = color;
            this.strokeColor = strokeColor;
            this.strokeWidth = strokeWidth;
            this.path = path;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
        }
    }

    private static final Part[] P = {
        new Part("leg-left", 0xFF2A3F7A, 0x00000000, 0f, leg_left(), -1.57f, -10.88f),
        new Part("leg-right", 0xFF2A3F7A, 0x00000000, 0f, leg_right(), 3.03f, -10.88f),
        new Part("shoe-left", 0xFF1B243F, 0x00000000, 0f, shoe_left(), -1.99f, -3.35f),
        new Part("shoe-right", 0xFF1B243F, 0x00000000, 0f, shoe_right(), 3.45f, -3.35f),
        new Part("scarf", 0xFFE14A4A, 0x00000000, 0f, scarf(), -5.97f, -24.28f),
        new Part("body", 0xFF3F6BE0, 0x00000000, 0f, body(), 0.73f, -24.28f),
        new Part("chest", 0xFF5A85F5, 0x00000000, 0f, chest(), 0.73f, -23.02f),
        new Part("belt", 0xFF243458, 0x00000000, 0f, belt(), 0.73f, -15.07f),
        new Part("buckle", 0xFFFFCB3D, 0x00000000, 0f, buckle(), 0.73f, -15.07f),
        new Part("arm-back", 0xFF3560D2, 0x00000000, 0f, arm_back(), -6.38f, -22.6f),
        new Part("arm-front", 0xFF3560D2, 0x00000000, 0f, arm_front(), 7.85f, -22.6f),
        new Part("hand-back", 0xFFF0BE92, 0x00000000, 0f, hand_back(), -6.38f, -16.53f),
        new Part("hand-front", 0xFFF0BE92, 0x00000000, 0f, hand_front(), 7.85f, -16.53f),
        new Part("head", 0xFFF3C79A, 0x00000000, 0f, head(), 0.73f, -34.33f),
        new Part("hair", 0xFF4A2E1D, 0x00000000, 0f, hair(), 0.73f, -36f),
        new Part("eye-white", 0xFFFFFFFF, 0x00000000, 0f, eye_white(), 3.24f, -30.98f),
        new Part("pupil", 0xFF2A2118, 0x00000000, 0f, pupil(), 3.66f, -30.14f),
        new Part("brow", 0xFF4A2E1D, 0x00000000, 0f, brow(), 3.45f, -31.81f),
        new Part("mouth", 0xFFB3754B, 0x00000000, 0f, mouth(), 3.66f, -26.79f),
    };

    private ArtPlayer() { }

    /** Number of sub-paths, in draw order. */
    public static int count() {
        return P.length;
    }

    public static Part part(int i) {
        return P[i];
    }

    /** The layer's SVG id (lowercased), for name-driven animation. */
    public static String partName(int i) {
        return P[i].name;
    }

    /** Top-centre of part i's ink box in art units: its joint pivot. */
    public static float partPivotX(int i) {
        return P[i].pivotX;
    }

    /** @see #partPivotX */
    public static float partPivotY(int i) {
        return P[i].pivotY;
    }

    /**
     * Draws a single part with the anchor at (x, y), scaled about that
     * point. Renderer animates limbs by rotating the canvas about the
     * part's pivot and calling this with x = y = 0.
     */
    public static void drawPart(int i, Canvas c, Paint fill, Paint stroke,
                               float x, float y, float scale) {
        Part p = P[i];
        c.save();
        c.translate(x, y);
        if (scale != 1f) {
            c.scale(scale, scale);
        }
        if (p.color != 0) {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(p.color);
            c.drawPath(p.path, fill);
        }
        if (p.strokeColor != 0 && stroke != null) {
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setColor(p.strokeColor);
            stroke.setStrokeWidth(p.strokeWidth);
            c.drawPath(p.path, stroke);
        }
        c.restore();
    }

    /**
     * Draws the artwork with its anchor at (x, y), scaled about that point.
     * Mutates both paints (style, colour, stroke width), exactly like the
     * hand-written drawing code it replaces.
     */
    public static void draw(Canvas c, Paint fill, Paint stroke,
                           float x, float y, float scale) {
        if (P.length == 0) {
            return;
        }
        c.save();
        c.translate(x, y);
        if (scale != 1f) {
            c.scale(scale, scale);
        }
        for (int i = 0; i < P.length; i++) {
            Part p = P[i];
            if (p.color != 0) {
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(p.color);
                c.drawPath(p.path, fill);
            }
            if (p.strokeColor != 0 && stroke != null) {
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setColor(p.strokeColor);
                stroke.setStrokeWidth(p.strokeWidth);
                c.drawPath(p.path, stroke);
            }
        }
        c.restore();
    }

    private static Path leg_left() {
        Path p = new Path();
        p.moveTo(-1.78f, -10.88f);
        p.lineTo(-1.36f, -10.88f);
        p.cubicTo(-0.44f, -10.88f, 0.31f, -10.13f, 0.31f, -9.21f);
        p.lineTo(0.31f, -5.02f);
        p.cubicTo(0.31f, -4.1f, -0.44f, -3.35f, -1.36f, -3.35f);
        p.lineTo(-1.78f, -3.35f);
        p.cubicTo(-2.7f, -3.35f, -3.45f, -4.1f, -3.45f, -5.02f);
        p.lineTo(-3.45f, -9.21f);
        p.cubicTo(-3.45f, -10.13f, -2.7f, -10.88f, -1.78f, -10.88f);
        p.close();
        return p;
    }

    private static Path leg_right() {
        Path p = new Path();
        p.moveTo(2.83f, -10.88f);
        p.lineTo(3.24f, -10.88f);
        p.cubicTo(4.17f, -10.88f, 4.92f, -10.13f, 4.92f, -9.21f);
        p.lineTo(4.92f, -5.02f);
        p.cubicTo(4.92f, -4.1f, 4.17f, -3.35f, 3.24f, -3.35f);
        p.lineTo(2.83f, -3.35f);
        p.cubicTo(1.9f, -3.35f, 1.15f, -4.1f, 1.15f, -5.02f);
        p.lineTo(1.15f, -9.21f);
        p.cubicTo(1.15f, -10.13f, 1.9f, -10.88f, 2.83f, -10.88f);
        p.close();
        return p;
    }

    private static Path shoe_left() {
        Path p = new Path();
        p.moveTo(-3.03f, -3.35f);
        p.lineTo(-0.94f, -3.35f);
        p.cubicTo(-0.25f, -3.35f, 0.31f, -2.79f, 0.31f, -2.09f);
        p.lineTo(0.31f, -1.26f);
        p.cubicTo(0.31f, -0.56f, -0.25f, 0f, -0.94f, 0f);
        p.lineTo(-3.03f, 0f);
        p.cubicTo(-3.73f, 0f, -4.29f, -0.56f, -4.29f, -1.26f);
        p.lineTo(-4.29f, -2.09f);
        p.cubicTo(-4.29f, -2.79f, -3.73f, -3.35f, -3.03f, -3.35f);
        p.close();
        return p;
    }

    private static Path shoe_right() {
        Path p = new Path();
        p.moveTo(2.41f, -3.35f);
        p.lineTo(4.5f, -3.35f);
        p.cubicTo(5.19f, -3.35f, 5.76f, -2.79f, 5.76f, -2.09f);
        p.lineTo(5.76f, -1.26f);
        p.cubicTo(5.76f, -0.56f, 5.19f, 0f, 4.5f, 0f);
        p.lineTo(2.41f, 0f);
        p.cubicTo(1.71f, 0f, 1.15f, -0.56f, 1.15f, -1.26f);
        p.lineTo(1.15f, -2.09f);
        p.cubicTo(1.15f, -2.79f, 1.71f, -3.35f, 2.41f, -3.35f);
        p.close();
        return p;
    }

    private static Path scarf() {
        Path p = new Path();
        p.moveTo(-2.62f, -22.6f);
        p.lineTo(-9.31f, -24.28f);
        p.lineTo(-9.31f, -20.93f);
        p.lineTo(-2.62f, -20.93f);
        p.close();
        return p;
    }

    private static Path body() {
        Path p = new Path();
        p.moveTo(-1.78f, -24.28f);
        p.lineTo(3.24f, -24.28f);
        p.cubicTo(5.56f, -24.28f, 7.43f, -22.4f, 7.43f, -20.09f);
        p.lineTo(7.43f, -14.23f);
        p.cubicTo(7.43f, -11.92f, 5.56f, -10.05f, 3.24f, -10.05f);
        p.lineTo(-1.78f, -10.05f);
        p.cubicTo(-4.09f, -10.05f, -5.97f, -11.92f, -5.97f, -14.23f);
        p.lineTo(-5.97f, -20.09f);
        p.cubicTo(-5.97f, -22.4f, -4.09f, -24.28f, -1.78f, -24.28f);
        p.close();
        return p;
    }

    private static Path chest() {
        Path p = new Path();
        p.moveTo(-1.78f, -23.02f);
        p.lineTo(3.24f, -23.02f);
        p.cubicTo(4.63f, -23.02f, 5.76f, -21.9f, 5.76f, -20.51f);
        p.lineTo(5.76f, -20.51f);
        p.cubicTo(5.76f, -19.12f, 4.63f, -18f, 3.24f, -18f);
        p.lineTo(-1.78f, -18f);
        p.cubicTo(-3.17f, -18f, -4.29f, -19.12f, -4.29f, -20.51f);
        p.lineTo(-4.29f, -20.51f);
        p.cubicTo(-4.29f, -21.9f, -3.17f, -23.02f, -1.78f, -23.02f);
        p.close();
        return p;
    }

    private static Path belt() {
        Path p = new Path();
        p.moveTo(-5.97f, -15.07f);
        p.lineTo(7.43f, -15.07f);
        p.lineTo(7.43f, -12.98f);
        p.lineTo(-5.97f, -12.98f);
        p.close();
        return p;
    }

    private static Path buckle() {
        Path p = new Path();
        p.moveTo(-0.52f, -15.07f);
        p.lineTo(1.99f, -15.07f);
        p.lineTo(1.99f, -12.98f);
        p.lineTo(-0.52f, -12.98f);
        p.close();
        return p;
    }

    private static Path arm_back() {
        Path p = new Path();
        p.moveTo(-6.38f, -22.6f);
        p.lineTo(-6.38f, -22.6f);
        p.cubicTo(-5.69f, -22.6f, -5.13f, -22.04f, -5.13f, -21.35f);
        p.lineTo(-5.13f, -17.16f);
        p.cubicTo(-5.13f, -16.47f, -5.69f, -15.91f, -6.38f, -15.91f);
        p.lineTo(-6.38f, -15.91f);
        p.cubicTo(-7.08f, -15.91f, -7.64f, -16.47f, -7.64f, -17.16f);
        p.lineTo(-7.64f, -21.35f);
        p.cubicTo(-7.64f, -22.04f, -7.08f, -22.6f, -6.38f, -22.6f);
        p.close();
        return p;
    }

    private static Path arm_front() {
        Path p = new Path();
        p.moveTo(7.85f, -22.6f);
        p.lineTo(7.85f, -22.6f);
        p.cubicTo(8.54f, -22.6f, 9.1f, -22.04f, 9.1f, -21.35f);
        p.lineTo(9.1f, -17.16f);
        p.cubicTo(9.1f, -16.47f, 8.54f, -15.91f, 7.85f, -15.91f);
        p.lineTo(7.85f, -15.91f);
        p.cubicTo(7.16f, -15.91f, 6.59f, -16.47f, 6.59f, -17.16f);
        p.lineTo(6.59f, -21.35f);
        p.cubicTo(6.59f, -22.04f, 7.16f, -22.6f, 7.85f, -22.6f);
        p.close();
        return p;
    }

    private static Path hand_back() {
        Path p = new Path();
        p.moveTo(-4.92f, -15.07f);
        p.cubicTo(-4.92f, -14.26f, -5.57f, -13.6f, -6.38f, -13.6f);
        p.cubicTo(-7.19f, -13.6f, -7.85f, -14.26f, -7.85f, -15.07f);
        p.cubicTo(-7.85f, -15.88f, -7.19f, -16.53f, -6.38f, -16.53f);
        p.cubicTo(-5.57f, -16.53f, -4.92f, -15.88f, -4.92f, -15.07f);
        p.close();
        return p;
    }

    private static Path hand_front() {
        Path p = new Path();
        p.moveTo(9.31f, -15.07f);
        p.cubicTo(9.31f, -14.26f, 8.66f, -13.6f, 7.85f, -13.6f);
        p.cubicTo(7.04f, -13.6f, 6.38f, -14.26f, 6.38f, -15.07f);
        p.cubicTo(6.38f, -15.88f, 7.04f, -16.53f, 7.85f, -16.53f);
        p.cubicTo(8.66f, -16.53f, 9.31f, -15.88f, 9.31f, -15.07f);
        p.close();
        return p;
    }

    private static Path head() {
        Path p = new Path();
        p.moveTo(-0.94f, -34.33f);
        p.lineTo(2.41f, -34.33f);
        p.cubicTo(4.26f, -34.33f, 5.76f, -32.83f, 5.76f, -30.98f);
        p.lineTo(5.76f, -27.63f);
        p.cubicTo(5.76f, -25.78f, 4.26f, -24.28f, 2.41f, -24.28f);
        p.lineTo(-0.94f, -24.28f);
        p.cubicTo(-2.79f, -24.28f, -4.29f, -25.78f, -4.29f, -27.63f);
        p.lineTo(-4.29f, -30.98f);
        p.cubicTo(-4.29f, -32.83f, -2.79f, -34.33f, -0.94f, -34.33f);
        p.close();
        return p;
    }

    private static Path hair() {
        Path p = new Path();
        p.moveTo(-4.29f, -30.14f);
        p.lineTo(-4.29f, -31.81f);
        p.quadTo(0.73f, -36f, 5.76f, -31.81f);
        p.lineTo(5.76f, -30.14f);
        p.lineTo(3.24f, -31.4f);
        p.lineTo(0.73f, -30.14f);
        p.lineTo(-1.78f, -31.4f);
        p.close();
        return p;
    }

    private static Path eye_white() {
        Path p = new Path();
        p.moveTo(4.92f, -29.3f);
        p.cubicTo(4.92f, -28.38f, 4.17f, -27.63f, 3.24f, -27.63f);
        p.cubicTo(2.32f, -27.63f, 1.57f, -28.38f, 1.57f, -29.3f);
        p.cubicTo(1.57f, -30.23f, 2.32f, -30.98f, 3.24f, -30.98f);
        p.cubicTo(4.17f, -30.98f, 4.92f, -30.23f, 4.92f, -29.3f);
        p.close();
        return p;
    }

    private static Path pupil() {
        Path p = new Path();
        p.moveTo(4.5f, -29.3f);
        p.cubicTo(4.5f, -28.84f, 4.13f, -28.47f, 3.66f, -28.47f);
        p.cubicTo(3.2f, -28.47f, 2.83f, -28.84f, 2.83f, -29.3f);
        p.cubicTo(2.83f, -29.76f, 3.2f, -30.14f, 3.66f, -30.14f);
        p.cubicTo(4.13f, -30.14f, 4.5f, -29.76f, 4.5f, -29.3f);
        p.close();
        return p;
    }

    private static Path brow() {
        Path p = new Path();
        p.moveTo(1.57f, -31.4f);
        p.lineTo(5.34f, -31.81f);
        p.lineTo(5.34f, -30.98f);
        p.lineTo(1.57f, -30.56f);
        p.close();
        return p;
    }

    private static Path mouth() {
        Path p = new Path();
        p.moveTo(2.41f, -26.79f);
        p.quadTo(3.66f, -25.95f, 4.92f, -26.79f);
        p.lineTo(4.92f, -26.37f);
        p.quadTo(3.66f, -25.53f, 2.41f, -26.37f);
        p.close();
        return p;
    }

}
