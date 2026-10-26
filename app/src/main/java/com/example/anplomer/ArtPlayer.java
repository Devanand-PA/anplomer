// GENERATED FILE - DO NOT EDIT BY HAND.
// Source:      art/player.svg
// Generator:   tools/artgen/svg2java.py
// Regenerate:  python3 tools/artgen/svg2java.py art/player.svg --class ArtPlayer --fit 52x72
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
    public static final float WIDTH = 28.27f;
    public static final float HEIGHT = 72f;

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
        new Part("leg-left", 0xFF2A3F7A, 0x00000000, 0f, leg_left(), -3.72f, -19.71f),
        new Part("leg-right", 0xFF2A3F7A, 0x00000000, 0f, leg_right(), 3.15f, -19.71f),
        new Part("shoe-left", 0xFF1B243F, 0x00000000, 0f, shoe_left(), -5.02f, -6.29f),
        new Part("shoe-right", 0xFF1B243F, 0x00000000, 0f, shoe_right(), 2.83f, -6.29f),
        new Part("body", 0xFF3F6BE0, 0x00000000, 0f, body(), -0.04f, -45.03f),
        new Part("chest", 0xFF5A85F5, 0x00000000, 0f, chest(), -0.04f, -42.67f),
        new Part("belt", 0xFF243458, 0x00000000, 0f, belt(), -0.04f, -27.74f),
        new Part("buckle", 0xFFFFCB3D, 0x00000000, 0f, buckle(), -0.04f, -27.74f),
        new Part("arm-back", 0xFF3560D2, 0x00000000, 0f, arm_back(), -11.72f, -41.4f),
        new Part("arm-front", 0xFF3560D2, 0x00000000, 0f, arm_front(), 11.72f, -41.4f),
        new Part("hand-back", 0xFFF0BE92, 0x00000000, 0f, hand_back(), -11.72f, -31.69f),
        new Part("hand-front", 0xFFF0BE92, 0x00000000, 0f, hand_front(), 11.72f, -31.69f),
        new Part("head", 0xFFF3C79A, 0x00000000, 0f, head(), -0.12f, -63.58f),
        new Part("hair", 0xFF4A2E1D, 0x00000000, 0f, hair(), -0.15f, -72f),
        new Part("eye-white", 0xFFFFFFFF, 0x00000000, 0f, eye_white(), -4.79f, -56.48f),
        new Part("pupil", 0xFF2A2118, 0x00000000, 0f, pupil(), -4f, -54.91f),
        new Part("brow", 0xFF4A2E1D, 0x00000000, 0f, brow(), 4.99f, -58.86f),
        new Part("mouth", 0xFFB3754B, 0x00000000, 0f, mouth(), 0.93f, -48.78f),
        new Part("circle1", 0xFFFFFFFF, 0x00000000, 0f, circle1(), 4.84f, -56.48f),
        new Part("circle2", 0xFF2A2118, 0x00000000, 0f, circle2(), 5.62f, -54.91f),
        new Part("path2", 0xFF4A2E1D, 0x00000000, 0f, path2(), -5.13f, -58.74f),
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
        p.moveTo(-4.03f, -19.71f);
        p.lineTo(-3.41f, -19.71f);
        p.cubicTo(-2.03f, -19.71f, -0.91f, -18.3f, -0.91f, -16.57f);
        p.lineTo(-0.91f, -8.71f);
        p.cubicTo(-0.91f, -6.97f, -2.03f, -5.56f, -3.41f, -5.56f);
        p.lineTo(-4.03f, -5.56f);
        p.cubicTo(-5.41f, -5.56f, -6.53f, -6.97f, -6.53f, -8.71f);
        p.lineTo(-6.53f, -16.57f);
        p.cubicTo(-6.53f, -18.3f, -5.41f, -19.71f, -4.03f, -19.71f);
        p.close();
        return p;
    }

    private static Path leg_right() {
        Path p = new Path();
        p.moveTo(2.84f, -19.71f);
        p.lineTo(3.46f, -19.71f);
        p.cubicTo(4.84f, -19.71f, 5.96f, -18.3f, 5.96f, -16.57f);
        p.lineTo(5.96f, -8.71f);
        p.cubicTo(5.96f, -6.97f, 4.84f, -5.56f, 3.46f, -5.56f);
        p.lineTo(2.84f, -5.56f);
        p.cubicTo(1.46f, -5.56f, 0.34f, -6.97f, 0.34f, -8.71f);
        p.lineTo(0.34f, -16.57f);
        p.cubicTo(0.34f, -18.3f, 1.46f, -19.71f, 2.84f, -19.71f);
        p.close();
        return p;
    }

    private static Path shoe_left() {
        Path p = new Path();
        p.moveTo(-6.53f, -6.29f);
        p.lineTo(-3.51f, -6.29f);
        p.cubicTo(-2.51f, -6.29f, -1.7f, -5.23f, -1.7f, -3.93f);
        p.lineTo(-1.7f, -2.36f);
        p.cubicTo(-1.7f, -1.06f, -2.51f, 0f, -3.51f, 0f);
        p.lineTo(-6.53f, 0f);
        p.cubicTo(-7.53f, 0f, -8.34f, -1.06f, -8.34f, -2.36f);
        p.lineTo(-8.34f, -3.93f);
        p.cubicTo(-8.34f, -5.23f, -7.53f, -6.29f, -6.53f, -6.29f);
        p.close();
        return p;
    }

    private static Path shoe_right() {
        Path p = new Path();
        p.moveTo(1.32f, -6.29f);
        p.lineTo(4.34f, -6.29f);
        p.cubicTo(5.35f, -6.29f, 6.16f, -5.23f, 6.16f, -3.93f);
        p.lineTo(6.16f, -2.36f);
        p.cubicTo(6.16f, -1.06f, 5.35f, 0f, 4.34f, 0f);
        p.lineTo(1.32f, 0f);
        p.cubicTo(0.32f, 0f, -0.49f, -1.06f, -0.49f, -2.36f);
        p.lineTo(-0.49f, -3.93f);
        p.cubicTo(-0.49f, -5.23f, 0.32f, -6.29f, 1.32f, -6.29f);
        p.close();
        return p;
    }

    private static Path body() {
        Path p = new Path();
        p.moveTo(-3.45f, -45.03f);
        p.lineTo(3.37f, -45.03f);
        p.cubicTo(6.51f, -45.03f, 9.06f, -42.49f, 9.06f, -39.35f);
        p.lineTo(9.06f, -23.99f);
        p.cubicTo(9.06f, -20.85f, 6.51f, -18.3f, 3.37f, -18.3f);
        p.lineTo(-3.45f, -18.3f);
        p.cubicTo(-6.59f, -18.3f, -9.14f, -20.85f, -9.14f, -23.99f);
        p.lineTo(-9.14f, -39.35f);
        p.cubicTo(-9.14f, -42.49f, -6.59f, -45.03f, -3.45f, -45.03f);
        p.close();
        return p;
    }

    private static Path chest() {
        Path p = new Path();
        p.moveTo(-3.45f, -42.67f);
        p.lineTo(3.37f, -42.67f);
        p.cubicTo(5.26f, -42.67f, 6.78f, -41.15f, 6.78f, -39.26f);
        p.lineTo(6.78f, -36.65f);
        p.cubicTo(6.78f, -34.77f, 5.26f, -33.24f, 3.37f, -33.24f);
        p.lineTo(-3.45f, -33.24f);
        p.cubicTo(-5.34f, -33.24f, -6.87f, -34.77f, -6.87f, -36.65f);
        p.lineTo(-6.87f, -39.26f);
        p.cubicTo(-6.87f, -41.15f, -5.34f, -42.67f, -3.45f, -42.67f);
        p.close();
        return p;
    }

    private static Path belt() {
        Path p = new Path();
        p.moveTo(-9.14f, -27.74f);
        p.lineTo(9.06f, -27.74f);
        p.lineTo(9.06f, -23.81f);
        p.lineTo(-9.14f, -23.81f);
        p.close();
        return p;
    }

    private static Path buckle() {
        Path p = new Path();
        p.moveTo(-1.75f, -27.74f);
        p.lineTo(1.67f, -27.74f);
        p.lineTo(1.67f, -23.81f);
        p.lineTo(-1.75f, -23.81f);
        p.close();
        return p;
    }

    private static Path arm_back() {
        Path p = new Path();
        p.moveTo(-11.72f, -41.4f);
        p.lineTo(-11.72f, -41.4f);
        p.cubicTo(-10.58f, -41.4f, -9.65f, -40.5f, -9.65f, -39.39f);
        p.lineTo(-9.65f, -32.7f);
        p.cubicTo(-9.65f, -31.59f, -10.58f, -30.69f, -11.72f, -30.69f);
        p.lineTo(-11.72f, -30.69f);
        p.cubicTo(-12.86f, -30.69f, -13.79f, -31.59f, -13.79f, -32.7f);
        p.lineTo(-13.79f, -39.39f);
        p.cubicTo(-13.79f, -40.5f, -12.86f, -41.4f, -11.72f, -41.4f);
        p.close();
        return p;
    }

    private static Path arm_front() {
        Path p = new Path();
        p.moveTo(11.72f, -41.4f);
        p.lineTo(11.72f, -41.4f);
        p.cubicTo(12.86f, -41.4f, 13.79f, -40.5f, 13.79f, -39.39f);
        p.lineTo(13.79f, -32.7f);
        p.cubicTo(13.79f, -31.59f, 12.86f, -30.69f, 11.72f, -30.69f);
        p.lineTo(11.72f, -30.69f);
        p.cubicTo(10.58f, -30.69f, 9.65f, -31.59f, 9.65f, -32.7f);
        p.lineTo(9.65f, -39.39f);
        p.cubicTo(9.65f, -40.5f, 10.58f, -41.4f, 11.72f, -41.4f);
        p.close();
        return p;
    }

    private static Path hand_back() {
        Path p = new Path();
        p.moveTo(-9.31f, -29.35f);
        p.cubicTo(-9.31f, -28.06f, -10.39f, -27.01f, -11.72f, -27.01f);
        p.cubicTo(-13.05f, -27.01f, -14.13f, -28.06f, -14.13f, -29.35f);
        p.cubicTo(-14.13f, -30.65f, -13.05f, -31.69f, -11.72f, -31.69f);
        p.cubicTo(-10.39f, -31.69f, -9.31f, -30.65f, -9.31f, -29.35f);
        p.close();
        return p;
    }

    private static Path hand_front() {
        Path p = new Path();
        p.moveTo(14.13f, -29.35f);
        p.cubicTo(14.13f, -28.06f, 13.05f, -27.01f, 11.72f, -27.01f);
        p.cubicTo(10.39f, -27.01f, 9.31f, -28.06f, 9.31f, -29.35f);
        p.cubicTo(9.31f, -30.65f, 10.39f, -31.69f, 11.72f, -31.69f);
        p.cubicTo(13.05f, -31.69f, 14.13f, -30.65f, 14.13f, -29.35f);
        p.close();
        return p;
    }

    private static Path head() {
        Path p = new Path();
        p.moveTo(-3.27f, -63.58f);
        p.lineTo(3.02f, -63.58f);
        p.cubicTo(6.5f, -63.58f, 9.31f, -60.76f, 9.31f, -57.29f);
        p.lineTo(9.31f, -51f);
        p.cubicTo(9.31f, -47.53f, 6.5f, -44.71f, 3.02f, -44.71f);
        p.lineTo(-3.27f, -44.71f);
        p.cubicTo(-6.74f, -44.71f, -9.56f, -47.53f, -9.56f, -51f);
        p.lineTo(-9.56f, -57.29f);
        p.cubicTo(-9.56f, -60.76f, -6.74f, -63.58f, -3.27f, -63.58f);
        p.close();
        return p;
    }

    private static Path hair() {
        Path p = new Path();
        p.moveTo(-9.96f, -57.43f);
        p.lineTo(-9.96f, -61.59f);
        p.quadTo(-0.15f, -72f, 9.66f, -61.59f);
        p.lineTo(9.66f, -57.43f);
        p.lineTo(4.75f, -60.55f);
        p.lineTo(-0.15f, -57.43f);
        p.lineTo(-5.05f, -60.55f);
        p.close();
        return p;
    }

    private static Path eye_white() {
        Path p = new Path();
        p.moveTo(-1.65f, -53.33f);
        p.cubicTo(-1.65f, -51.6f, -3.05f, -50.19f, -4.79f, -50.19f);
        p.cubicTo(-6.53f, -50.19f, -7.94f, -51.6f, -7.94f, -53.33f);
        p.cubicTo(-7.94f, -55.07f, -6.53f, -56.48f, -4.79f, -56.48f);
        p.cubicTo(-3.05f, -56.48f, -1.65f, -55.07f, -1.65f, -53.33f);
        p.close();
        return p;
    }

    private static Path pupil() {
        Path p = new Path();
        p.moveTo(-2.43f, -53.33f);
        p.cubicTo(-2.43f, -52.47f, -3.14f, -51.76f, -4f, -51.76f);
        p.cubicTo(-4.87f, -51.76f, -5.58f, -52.47f, -5.58f, -53.33f);
        p.cubicTo(-5.58f, -54.2f, -4.87f, -54.91f, -4f, -54.91f);
        p.cubicTo(-3.14f, -54.91f, -2.43f, -54.2f, -2.43f, -53.33f);
        p.close();
        return p;
    }

    private static Path brow() {
        Path p = new Path();
        p.moveTo(1.45f, -58.07f);
        p.lineTo(8.53f, -58.86f);
        p.lineTo(8.53f, -57.29f);
        p.lineTo(1.45f, -56.5f);
        p.close();
        return p;
    }

    private static Path mouth() {
        Path p = new Path();
        p.moveTo(-1.43f, -48.78f);
        p.quadTo(0.93f, -47.21f, 3.29f, -48.78f);
        p.lineTo(3.29f, -47.99f);
        p.quadTo(0.93f, -46.42f, -1.43f, -47.99f);
        p.close();
        return p;
    }

    private static Path circle1() {
        Path p = new Path();
        p.moveTo(7.98f, -53.33f);
        p.cubicTo(7.98f, -51.6f, 6.58f, -50.19f, 4.84f, -50.19f);
        p.cubicTo(3.1f, -50.19f, 1.69f, -51.6f, 1.69f, -53.33f);
        p.cubicTo(1.69f, -55.07f, 3.1f, -56.48f, 4.84f, -56.48f);
        p.cubicTo(6.58f, -56.48f, 7.98f, -55.07f, 7.98f, -53.33f);
        p.close();
        return p;
    }

    private static Path circle2() {
        Path p = new Path();
        p.moveTo(7.2f, -53.33f);
        p.cubicTo(7.2f, -52.47f, 6.49f, -51.76f, 5.62f, -51.76f);
        p.cubicTo(4.76f, -51.76f, 4.05f, -52.47f, 4.05f, -53.33f);
        p.cubicTo(4.05f, -54.2f, 4.76f, -54.91f, 5.62f, -54.91f);
        p.cubicTo(6.49f, -54.91f, 7.2f, -54.2f, 7.2f, -53.33f);
        p.close();
        return p;
    }

    private static Path path2() {
        Path p = new Path();
        p.moveTo(-8.66f, -57.95f);
        p.lineTo(-1.59f, -58.74f);
        p.lineTo(-1.59f, -57.17f);
        p.lineTo(-8.66f, -56.38f);
        p.close();
        return p;
    }

}
