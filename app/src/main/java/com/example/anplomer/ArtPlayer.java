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
    public static final float WIDTH = 14.13f;
    public static final float HEIGHT = 36f;

    /** One filled/stroked sub-path. {@code color == 0} means "no fill". */
    public static final class Part {
        public final String name;
        public final int color;
        public final int strokeColor;
        public final float strokeWidth;
        public final Path path;

        Part(String name, int color, int strokeColor, float strokeWidth,
             Path path) {
            this.name = name;
            this.color = color;
            this.strokeColor = strokeColor;
            this.strokeWidth = strokeWidth;
            this.path = path;
        }
    }

    private static final Part[] P = {
        new Part("leg-left", 0xFF2A3F7A, 0x00000000, 0f, leg_left()),
        new Part("leg-right", 0xFF2A3F7A, 0x00000000, 0f, leg_right()),
        new Part("shoe-left", 0xFF1B243F, 0x00000000, 0f, shoe_left()),
        new Part("shoe-right", 0xFF1B243F, 0x00000000, 0f, shoe_right()),
        new Part("body", 0xFF3F6BE0, 0x00000000, 0f, body()),
        new Part("chest", 0xFF5A85F5, 0x00000000, 0f, chest()),
        new Part("belt", 0xFF243458, 0x00000000, 0f, belt()),
        new Part("buckle", 0xFFFFCB3D, 0x00000000, 0f, buckle()),
        new Part("arm-back", 0xFF3560D2, 0x00000000, 0f, arm_back()),
        new Part("arm-front", 0xFF3560D2, 0x00000000, 0f, arm_front()),
        new Part("hand-back", 0xFFF0BE92, 0x00000000, 0f, hand_back()),
        new Part("hand-front", 0xFFF0BE92, 0x00000000, 0f, hand_front()),
        new Part("head", 0xFFF3C79A, 0x00000000, 0f, head()),
        new Part("hair", 0xFF4A2E1D, 0x00000000, 0f, hair()),
        new Part("eye-white", 0xFFFFFFFF, 0x00000000, 0f, eye_white()),
        new Part("pupil", 0xFF2A2118, 0x00000000, 0f, pupil()),
        new Part("brow", 0xFF4A2E1D, 0x00000000, 0f, brow()),
        new Part("mouth", 0xFFB3754B, 0x00000000, 0f, mouth()),
        new Part("circle1", 0xFFFFFFFF, 0x00000000, 0f, circle1()),
        new Part("circle2", 0xFF2A2118, 0x00000000, 0f, circle2()),
        new Part("path2", 0xFF4A2E1D, 0x00000000, 0f, path2()),
    };

    private ArtPlayer() { }

    /** Number of sub-paths, in draw order. */
    public static int count() {
        return P.length;
    }

    public static Part part(int i) {
        return P[i];
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
        p.moveTo(-2.01f, -9.86f);
        p.lineTo(-1.7f, -9.86f);
        p.cubicTo(-1.01f, -9.86f, -0.45f, -9.15f, -0.45f, -8.28f);
        p.lineTo(-0.45f, -4.35f);
        p.cubicTo(-0.45f, -3.48f, -1.01f, -2.78f, -1.7f, -2.78f);
        p.lineTo(-2.01f, -2.78f);
        p.cubicTo(-2.7f, -2.78f, -3.26f, -3.48f, -3.26f, -4.35f);
        p.lineTo(-3.26f, -8.28f);
        p.cubicTo(-3.26f, -9.15f, -2.7f, -9.86f, -2.01f, -9.86f);
        p.close();
        return p;
    }

    private static Path leg_right() {
        Path p = new Path();
        p.moveTo(1.42f, -9.86f);
        p.lineTo(1.73f, -9.86f);
        p.cubicTo(2.42f, -9.86f, 2.98f, -9.15f, 2.98f, -8.28f);
        p.lineTo(2.98f, -4.35f);
        p.cubicTo(2.98f, -3.48f, 2.42f, -2.78f, 1.73f, -2.78f);
        p.lineTo(1.42f, -2.78f);
        p.cubicTo(0.73f, -2.78f, 0.17f, -3.48f, 0.17f, -4.35f);
        p.lineTo(0.17f, -8.28f);
        p.cubicTo(0.17f, -9.15f, 0.73f, -9.86f, 1.42f, -9.86f);
        p.close();
        return p;
    }

    private static Path shoe_left() {
        Path p = new Path();
        p.moveTo(-3.26f, -3.14f);
        p.lineTo(-1.75f, -3.14f);
        p.cubicTo(-1.25f, -3.14f, -0.85f, -2.62f, -0.85f, -1.97f);
        p.lineTo(-0.85f, -1.18f);
        p.cubicTo(-0.85f, -0.53f, -1.25f, 0f, -1.75f, 0f);
        p.lineTo(-3.26f, 0f);
        p.cubicTo(-3.77f, 0f, -4.17f, -0.53f, -4.17f, -1.18f);
        p.lineTo(-4.17f, -1.97f);
        p.cubicTo(-4.17f, -2.62f, -3.77f, -3.14f, -3.26f, -3.14f);
        p.close();
        return p;
    }

    private static Path shoe_right() {
        Path p = new Path();
        p.moveTo(0.66f, -3.14f);
        p.lineTo(2.17f, -3.14f);
        p.cubicTo(2.67f, -3.14f, 3.08f, -2.62f, 3.08f, -1.97f);
        p.lineTo(3.08f, -1.18f);
        p.cubicTo(3.08f, -0.53f, 2.67f, 0f, 2.17f, 0f);
        p.lineTo(0.66f, 0f);
        p.cubicTo(0.16f, 0f, -0.24f, -0.53f, -0.24f, -1.18f);
        p.lineTo(-0.24f, -1.97f);
        p.cubicTo(-0.24f, -2.62f, 0.16f, -3.14f, 0.66f, -3.14f);
        p.close();
        return p;
    }

    private static Path body() {
        Path p = new Path();
        p.moveTo(-1.73f, -22.52f);
        p.lineTo(1.69f, -22.52f);
        p.cubicTo(3.26f, -22.52f, 4.53f, -21.24f, 4.53f, -19.67f);
        p.lineTo(4.53f, -11.99f);
        p.cubicTo(4.53f, -10.42f, 3.26f, -9.15f, 1.69f, -9.15f);
        p.lineTo(-1.73f, -9.15f);
        p.cubicTo(-3.3f, -9.15f, -4.57f, -10.42f, -4.57f, -11.99f);
        p.lineTo(-4.57f, -19.67f);
        p.cubicTo(-4.57f, -21.24f, -3.3f, -22.52f, -1.73f, -22.52f);
        p.close();
        return p;
    }

    private static Path chest() {
        Path p = new Path();
        p.moveTo(-1.73f, -21.34f);
        p.lineTo(1.69f, -21.34f);
        p.cubicTo(2.63f, -21.34f, 3.39f, -20.57f, 3.39f, -19.63f);
        p.lineTo(3.39f, -18.33f);
        p.cubicTo(3.39f, -17.38f, 2.63f, -16.62f, 1.69f, -16.62f);
        p.lineTo(-1.73f, -16.62f);
        p.cubicTo(-2.67f, -16.62f, -3.43f, -17.38f, -3.43f, -18.33f);
        p.lineTo(-3.43f, -19.63f);
        p.cubicTo(-3.43f, -20.57f, -2.67f, -21.34f, -1.73f, -21.34f);
        p.close();
        return p;
    }

    private static Path belt() {
        Path p = new Path();
        p.moveTo(-4.57f, -13.87f);
        p.lineTo(4.53f, -13.87f);
        p.lineTo(4.53f, -11.9f);
        p.lineTo(-4.57f, -11.9f);
        p.close();
        return p;
    }

    private static Path buckle() {
        Path p = new Path();
        p.moveTo(-0.87f, -13.87f);
        p.lineTo(0.83f, -13.87f);
        p.lineTo(0.83f, -11.9f);
        p.lineTo(-0.87f, -11.9f);
        p.close();
        return p;
    }

    private static Path arm_back() {
        Path p = new Path();
        p.moveTo(-5.86f, -20.7f);
        p.lineTo(-5.86f, -20.7f);
        p.cubicTo(-5.29f, -20.7f, -4.83f, -20.25f, -4.83f, -19.7f);
        p.lineTo(-4.83f, -16.35f);
        p.cubicTo(-4.83f, -15.79f, -5.29f, -15.35f, -5.86f, -15.35f);
        p.lineTo(-5.86f, -15.35f);
        p.cubicTo(-6.43f, -15.35f, -6.89f, -15.79f, -6.89f, -16.35f);
        p.lineTo(-6.89f, -19.7f);
        p.cubicTo(-6.89f, -20.25f, -6.43f, -20.7f, -5.86f, -20.7f);
        p.close();
        return p;
    }

    private static Path arm_front() {
        Path p = new Path();
        p.moveTo(5.86f, -20.7f);
        p.lineTo(5.86f, -20.7f);
        p.cubicTo(6.43f, -20.7f, 6.89f, -20.25f, 6.89f, -19.7f);
        p.lineTo(6.89f, -16.35f);
        p.cubicTo(6.89f, -15.79f, 6.43f, -15.35f, 5.86f, -15.35f);
        p.lineTo(5.86f, -15.35f);
        p.cubicTo(5.29f, -15.35f, 4.83f, -15.79f, 4.83f, -16.35f);
        p.lineTo(4.83f, -19.7f);
        p.cubicTo(4.83f, -20.25f, 5.29f, -20.7f, 5.86f, -20.7f);
        p.close();
        return p;
    }

    private static Path hand_back() {
        Path p = new Path();
        p.moveTo(-4.65f, -14.68f);
        p.cubicTo(-4.65f, -14.03f, -5.19f, -13.5f, -5.86f, -13.5f);
        p.cubicTo(-6.53f, -13.5f, -7.07f, -14.03f, -7.07f, -14.68f);
        p.cubicTo(-7.07f, -15.32f, -6.53f, -15.85f, -5.86f, -15.85f);
        p.cubicTo(-5.19f, -15.85f, -4.65f, -15.32f, -4.65f, -14.68f);
        p.close();
        return p;
    }

    private static Path hand_front() {
        Path p = new Path();
        p.moveTo(7.07f, -14.68f);
        p.cubicTo(7.07f, -14.03f, 6.53f, -13.5f, 5.86f, -13.5f);
        p.cubicTo(5.19f, -13.5f, 4.65f, -14.03f, 4.65f, -14.68f);
        p.cubicTo(4.65f, -15.32f, 5.19f, -15.85f, 5.86f, -15.85f);
        p.cubicTo(6.53f, -15.85f, 7.07f, -15.32f, 7.07f, -14.68f);
        p.close();
        return p;
    }

    private static Path head() {
        Path p = new Path();
        p.moveTo(-1.63f, -31.79f);
        p.lineTo(1.51f, -31.79f);
        p.cubicTo(3.25f, -31.79f, 4.66f, -30.38f, 4.66f, -28.64f);
        p.lineTo(4.66f, -25.5f);
        p.cubicTo(4.66f, -23.76f, 3.25f, -22.35f, 1.51f, -22.35f);
        p.lineTo(-1.63f, -22.35f);
        p.cubicTo(-3.37f, -22.35f, -4.78f, -23.76f, -4.78f, -25.5f);
        p.lineTo(-4.78f, -28.64f);
        p.cubicTo(-4.78f, -30.38f, -3.37f, -31.79f, -1.63f, -31.79f);
        p.close();
        return p;
    }

    private static Path hair() {
        Path p = new Path();
        p.moveTo(-4.98f, -28.72f);
        p.lineTo(-4.98f, -30.8f);
        p.quadTo(-0.07f, -36f, 4.83f, -30.8f);
        p.lineTo(4.83f, -28.72f);
        p.lineTo(2.38f, -30.28f);
        p.lineTo(-0.07f, -28.72f);
        p.lineTo(-2.53f, -30.28f);
        p.close();
        return p;
    }

    private static Path eye_white() {
        Path p = new Path();
        p.moveTo(-0.82f, -26.67f);
        p.cubicTo(-0.82f, -25.8f, -1.53f, -25.09f, -2.4f, -25.09f);
        p.cubicTo(-3.26f, -25.09f, -3.97f, -25.8f, -3.97f, -26.67f);
        p.cubicTo(-3.97f, -27.54f, -3.26f, -28.24f, -2.4f, -28.24f);
        p.cubicTo(-1.53f, -28.24f, -0.82f, -27.54f, -0.82f, -26.67f);
        p.close();
        return p;
    }

    private static Path pupil() {
        Path p = new Path();
        p.moveTo(-1.22f, -26.67f);
        p.cubicTo(-1.22f, -26.23f, -1.57f, -25.88f, -2f, -25.88f);
        p.cubicTo(-2.44f, -25.88f, -2.79f, -26.23f, -2.79f, -26.67f);
        p.cubicTo(-2.79f, -27.1f, -2.44f, -27.45f, -2f, -27.45f);
        p.cubicTo(-1.57f, -27.45f, -1.22f, -27.1f, -1.22f, -26.67f);
        p.close();
        return p;
    }

    private static Path brow() {
        Path p = new Path();
        p.moveTo(0.73f, -29.04f);
        p.lineTo(4.26f, -29.43f);
        p.lineTo(4.26f, -28.64f);
        p.lineTo(0.73f, -28.25f);
        p.close();
        return p;
    }

    private static Path mouth() {
        Path p = new Path();
        p.moveTo(-0.71f, -24.39f);
        p.quadTo(0.47f, -23.6f, 1.65f, -24.39f);
        p.lineTo(1.65f, -24f);
        p.quadTo(0.47f, -23.21f, -0.71f, -24f);
        p.close();
        return p;
    }

    private static Path circle1() {
        Path p = new Path();
        p.moveTo(3.99f, -26.67f);
        p.cubicTo(3.99f, -25.8f, 3.29f, -25.09f, 2.42f, -25.09f);
        p.cubicTo(1.55f, -25.09f, 0.85f, -25.8f, 0.85f, -26.67f);
        p.cubicTo(0.85f, -27.54f, 1.55f, -28.24f, 2.42f, -28.24f);
        p.cubicTo(3.29f, -28.24f, 3.99f, -27.54f, 3.99f, -26.67f);
        p.close();
        return p;
    }

    private static Path circle2() {
        Path p = new Path();
        p.moveTo(3.6f, -26.67f);
        p.cubicTo(3.6f, -26.23f, 3.25f, -25.88f, 2.81f, -25.88f);
        p.cubicTo(2.38f, -25.88f, 2.03f, -26.23f, 2.03f, -26.67f);
        p.cubicTo(2.03f, -27.1f, 2.38f, -27.45f, 2.81f, -27.45f);
        p.cubicTo(3.25f, -27.45f, 3.6f, -27.1f, 3.6f, -26.67f);
        p.close();
        return p;
    }

    private static Path path2() {
        Path p = new Path();
        p.moveTo(-4.33f, -28.98f);
        p.lineTo(-0.79f, -29.37f);
        p.lineTo(-0.79f, -28.58f);
        p.lineTo(-4.33f, -28.19f);
        p.close();
        return p;
    }

}
