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
    public static final float WIDTH = 40.09f;
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
        new Part("scarf", 0xFFE14A4A, 0x00000000, 0f, scarf(), -9.82f, -38.45f),
        new Part("leg-left", 0xFF2A3F7A, 0x00000000, 0f, leg_left(), -0.82f, -22.09f),
        new Part("leg-right", 0xFF33477F, 0x00000000, 0f, leg_right(), 6.55f, -22.09f),
        new Part("shoe-left", 0xFF1B243F, 0x00000000, 0f, shoe_left(), -1.64f, -9f),
        new Part("shoe-right", 0xFF232F52, 0x00000000, 0f, shoe_right(), 7.36f, -9f),
        new Part("body", 0xFF3F6BE0, 0x00000000, 0f, body(), 2.86f, -46.64f),
        new Part("chest", 0xFF5A85F5, 0x00000000, 0f, chest(), 2.86f, -42.55f),
        new Part("flank", 0x8C2F55C0, 0x00000000, 0f, flank(), 9.41f, -40.09f),
        new Part("belt", 0xFF243458, 0x00000000, 0f, belt(), 2.86f, -27f),
        new Part("buckle", 0xFFFFCB3D, 0x00000000, 0f, buckle(), 2.86f, -27f),
        new Part("arm-back", 0xFF3560D2, 0x00000000, 0f, arm_back(), -11.05f, -40.09f),
        new Part("arm-front", 0xFF3560D2, 0x00000000, 0f, arm_front(), 16.77f, -40.09f),
        new Part("hand-back", 0xFFF0BE92, 0x00000000, 0f, hand_back(), -11.05f, -24.55f),
        new Part("hand-front", 0xFFF0BE92, 0x00000000, 0f, hand_front(), 16.77f, -24.55f),
        new Part("head", 0xFFF3C79A, 0x00000000, 0f, head(), 2.86f, -63f),
        new Part("ear", 0xFFE8B489, 0x00000000, 0f, ear(), -7.36f, -50.73f),
        new Part("helmet", 0xFFAAB6C6, 0x00000000, 0f, helmet(), 2.86f, -64.64f),
        new Part("helm-brim", 0xFF7C8A9C, 0x00000000, 0f, helm_brim(), 2.86f, -54f),
        new Part("nose-guard", 0xFF7C8A9C, 0x00000000, 0f, nose_guard(), 2.86f, -50.73f),
        new Part("plume", 0xFFE14A4A, 0x00000000, 0f, plume(), -2.86f, -72f),
        new Part("eye-white", 0xFFFFFFFF, 0x00000000, 0f, eye_white(), -1.64f, -51.3f),
        new Part("pupil", 0xFF2A2118, 0x00000000, 0f, pupil(), -0.33f, -49.25f),
        new Part("eye-glint", 0xFFFFFFFF, 0x00000000, 0f, eye_glint(), -0.98f, -49.09f),
        new Part("eye2-white", 0xFFFFFFFF, 0x00000000, 0f, eye2_white(), 7.36f, -51.3f),
        new Part("eye2-pupil", 0xFF2A2118, 0x00000000, 0f, eye2_pupil(), 8.67f, -49.25f),
        new Part("eye2-glint", 0xFFFFFFFF, 0x00000000, 0f, eye2_glint(), 8.02f, -49.09f),
        new Part("brow", 0xFF4A2E1D, 0x00000000, 0f, brow(), -1.64f, -51.14f),
        new Part("brow2", 0xFF4A2E1D, 0x00000000, 0f, brow2(), 7.36f, -51.14f),
        new Part("mouth", 0xFF8A4A26, 0x00000000, 0f, mouth(), 2.86f, -44.67f),
        new Part("cheek", 0xFFF2A98A, 0x00000000, 0f, cheek(), -4.5f, -47.45f),
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

    private static Path scarf() {
        Path p = new Path();
        p.moveTo(-3.68f, -38.45f);
        p.cubicTo(-10.23f, -38.45f, -15.95f, -34.36f, -20.05f, -28.64f);
        p.cubicTo(-15.14f, -29.45f, -11.86f, -29.45f, -8.59f, -31.09f);
        p.cubicTo(-11.05f, -26.18f, -12.68f, -22.09f, -13.5f, -18f);
        p.cubicTo(-6.95f, -22.91f, -2.05f, -30.27f, 0.41f, -35.18f);
        p.close();
        return p;
    }

    private static Path leg_left() {
        Path p = new Path();
        p.moveTo(-3.68f, -22.09f);
        p.lineTo(2.05f, -22.09f);
        p.lineTo(2.05f, -9f);
        p.quadTo(2.05f, -6.55f, -0.41f, -6.55f);
        p.lineTo(-3.68f, -6.55f);
        p.close();
        return p;
    }

    private static Path leg_right() {
        Path p = new Path();
        p.moveTo(3.68f, -22.09f);
        p.lineTo(9.41f, -22.09f);
        p.lineTo(9.41f, -9f);
        p.quadTo(9.41f, -6.55f, 6.95f, -6.55f);
        p.lineTo(3.68f, -6.55f);
        p.close();
        return p;
    }

    private static Path shoe_left() {
        Path p = new Path();
        p.moveTo(-6.14f, -9f);
        p.lineTo(2.86f, -9f);
        p.quadTo(5.32f, -9f, 5.32f, -5.73f);
        p.lineTo(5.32f, -2.45f);
        p.quadTo(5.32f, 0f, 2.86f, 0f);
        p.lineTo(-6.14f, 0f);
        p.quadTo(-8.59f, 0f, -8.59f, -2.45f);
        p.lineTo(-8.59f, -5.73f);
        p.quadTo(-8.59f, -9f, -6.14f, -9f);
        p.close();
        return p;
    }

    private static Path shoe_right() {
        Path p = new Path();
        p.moveTo(2.86f, -9f);
        p.lineTo(11.86f, -9f);
        p.quadTo(14.32f, -9f, 14.32f, -5.73f);
        p.lineTo(14.32f, -2.45f);
        p.quadTo(14.32f, 0f, 11.86f, 0f);
        p.lineTo(2.86f, 0f);
        p.quadTo(0.41f, 0f, 0.41f, -2.45f);
        p.lineTo(0.41f, -5.73f);
        p.quadTo(0.41f, -9f, 2.86f, -9f);
        p.close();
        return p;
    }

    private static Path body() {
        Path p = new Path();
        p.moveTo(-6.95f, -40.09f);
        p.quadTo(2.86f, -46.64f, 12.68f, -40.09f);
        p.lineTo(14.32f, -23.73f);
        p.quadTo(14.32f, -18f, 8.59f, -18f);
        p.lineTo(-2.86f, -18f);
        p.quadTo(-8.59f, -18f, -8.59f, -23.73f);
        p.close();
        return p;
    }

    private static Path chest() {
        Path p = new Path();
        p.moveTo(-3.68f, -38.45f);
        p.quadTo(2.86f, -42.55f, 9.41f, -38.45f);
        p.lineTo(10.23f, -30.27f);
        p.lineTo(-4.5f, -30.27f);
        p.close();
        return p;
    }

    private static Path flank() {
        Path p = new Path();
        p.moveTo(8.59f, -38.45f);
        p.lineTo(12.68f, -40.09f);
        p.lineTo(14.32f, -23.73f);
        p.quadTo(14.32f, -18f, 8.59f, -18f);
        p.lineTo(4.5f, -18f);
        p.lineTo(8.59f, -38.45f);
        p.close();
        return p;
    }

    private static Path belt() {
        Path p = new Path();
        p.moveTo(-6.14f, -27f);
        p.lineTo(11.86f, -27f);
        p.cubicTo(12.77f, -27f, 13.5f, -26.27f, 13.5f, -25.36f);
        p.lineTo(13.5f, -23.73f);
        p.cubicTo(13.5f, -22.82f, 12.77f, -22.09f, 11.86f, -22.09f);
        p.lineTo(-6.14f, -22.09f);
        p.cubicTo(-7.04f, -22.09f, -7.77f, -22.82f, -7.77f, -23.73f);
        p.lineTo(-7.77f, -25.36f);
        p.cubicTo(-7.77f, -26.27f, -7.04f, -27f, -6.14f, -27f);
        p.close();
        return p;
    }

    private static Path buckle() {
        Path p = new Path();
        p.moveTo(1.23f, -27f);
        p.lineTo(4.5f, -27f);
        p.cubicTo(4.95f, -27f, 5.32f, -26.63f, 5.32f, -26.18f);
        p.lineTo(5.32f, -22.91f);
        p.cubicTo(5.32f, -22.46f, 4.95f, -22.09f, 4.5f, -22.09f);
        p.lineTo(1.23f, -22.09f);
        p.cubicTo(0.78f, -22.09f, 0.41f, -22.46f, 0.41f, -22.91f);
        p.lineTo(0.41f, -26.18f);
        p.cubicTo(0.41f, -26.63f, 0.78f, -27f, 1.23f, -27f);
        p.close();
        return p;
    }

    private static Path arm_back() {
        Path p = new Path();
        p.moveTo(-10.23f, -40.09f);
        p.quadTo(-14.32f, -37.64f, -14.32f, -32.73f);
        p.lineTo(-14.32f, -25.36f);
        p.quadTo(-14.32f, -22.09f, -11.05f, -22.09f);
        p.lineTo(-7.77f, -22.09f);
        p.lineTo(-7.77f, -40.09f);
        p.close();
        return p;
    }

    private static Path arm_front() {
        Path p = new Path();
        p.moveTo(15.95f, -40.09f);
        p.quadTo(20.05f, -37.64f, 20.05f, -32.73f);
        p.lineTo(20.05f, -25.36f);
        p.quadTo(20.05f, -22.09f, 16.77f, -22.09f);
        p.lineTo(13.5f, -22.09f);
        p.lineTo(13.5f, -40.09f);
        p.close();
        return p;
    }

    private static Path hand_back() {
        Path p = new Path();
        p.moveTo(-7.77f, -21.27f);
        p.cubicTo(-7.77f, -19.47f, -9.24f, -18f, -11.05f, -18f);
        p.cubicTo(-12.85f, -18f, -14.32f, -19.47f, -14.32f, -21.27f);
        p.cubicTo(-14.32f, -23.08f, -12.85f, -24.55f, -11.05f, -24.55f);
        p.cubicTo(-9.24f, -24.55f, -7.77f, -23.08f, -7.77f, -21.27f);
        p.close();
        return p;
    }

    private static Path hand_front() {
        Path p = new Path();
        p.moveTo(20.05f, -21.27f);
        p.cubicTo(20.05f, -19.47f, 18.58f, -18f, 16.77f, -18f);
        p.cubicTo(14.97f, -18f, 13.5f, -19.47f, 13.5f, -21.27f);
        p.cubicTo(13.5f, -23.08f, 14.97f, -24.55f, 16.77f, -24.55f);
        p.cubicTo(18.58f, -24.55f, 20.05f, -23.08f, 20.05f, -21.27f);
        p.close();
        return p;
    }

    private static Path head() {
        Path p = new Path();
        p.moveTo(13.5f, -52.36f);
        p.cubicTo(13.5f, -46.49f, 8.74f, -41.73f, 2.86f, -41.73f);
        p.cubicTo(-3.01f, -41.73f, -7.77f, -46.49f, -7.77f, -52.36f);
        p.cubicTo(-7.77f, -58.24f, -3.01f, -63f, 2.86f, -63f);
        p.cubicTo(8.74f, -63f, 13.5f, -58.24f, 13.5f, -52.36f);
        p.close();
        return p;
    }

    private static Path ear() {
        Path p = new Path();
        p.moveTo(-6.95f, -50.73f);
        p.quadTo(-9.41f, -49.91f, -8.59f, -47.45f);
        p.quadTo(-6.95f, -45.82f, -5.32f, -47.45f);
        p.close();
        return p;
    }

    private static Path helmet() {
        Path p = new Path();
        p.moveTo(-7.77f, -54f);
        p.cubicTo(-7.77f, -59.87f, -3.01f, -64.64f, 2.86f, -64.64f);
        p.cubicTo(8.74f, -64.64f, 13.5f, -59.87f, 13.5f, -54f);
        p.lineTo(13.5f, -51.55f);
        p.lineTo(-7.77f, -51.55f);
        p.close();
        return p;
    }

    private static Path helm_brim() {
        Path p = new Path();
        p.moveTo(-7.77f, -54f);
        p.lineTo(13.5f, -54f);
        p.cubicTo(14.4f, -54f, 15.14f, -53.27f, 15.14f, -52.36f);
        p.lineTo(15.14f, -52.36f);
        p.cubicTo(15.14f, -51.46f, 14.4f, -50.73f, 13.5f, -50.73f);
        p.lineTo(-7.77f, -50.73f);
        p.cubicTo(-8.68f, -50.73f, -9.41f, -51.46f, -9.41f, -52.36f);
        p.lineTo(-9.41f, -52.36f);
        p.cubicTo(-9.41f, -53.27f, -8.68f, -54f, -7.77f, -54f);
        p.close();
        return p;
    }

    private static Path nose_guard() {
        Path p = new Path();
        p.moveTo(2.86f, -50.73f);
        p.lineTo(2.86f, -50.73f);
        p.cubicTo(3.54f, -50.73f, 4.09f, -50.18f, 4.09f, -49.5f);
        p.lineTo(4.09f, -48.68f);
        p.cubicTo(4.09f, -48f, 3.54f, -47.45f, 2.86f, -47.45f);
        p.lineTo(2.86f, -47.45f);
        p.cubicTo(2.19f, -47.45f, 1.64f, -48f, 1.64f, -48.68f);
        p.lineTo(1.64f, -49.5f);
        p.cubicTo(1.64f, -50.18f, 2.19f, -50.73f, 2.86f, -50.73f);
        p.close();
        return p;
    }

    private static Path plume() {
        Path p = new Path();
        p.moveTo(3.68f, -63f);
        p.cubicTo(2.05f, -68.73f, -3.68f, -72f, -9.41f, -70.36f);
        p.cubicTo(-4.5f, -68.73f, -1.23f, -65.45f, 0.41f, -61.36f);
        p.close();
        return p;
    }

    private static Path eye_white() {
        Path p = new Path();
        p.moveTo(1.8f, -47.86f);
        p.cubicTo(1.8f, -45.97f, 0.26f, -44.43f, -1.64f, -44.43f);
        p.cubicTo(-3.53f, -44.43f, -5.07f, -45.97f, -5.07f, -47.86f);
        p.cubicTo(-5.07f, -49.76f, -3.53f, -51.3f, -1.64f, -51.3f);
        p.cubicTo(0.26f, -51.3f, 1.8f, -49.76f, 1.8f, -47.86f);
        p.close();
        return p;
    }

    private static Path pupil() {
        Path p = new Path();
        p.moveTo(1.39f, -47.54f);
        p.cubicTo(1.39f, -46.59f, 0.62f, -45.82f, -0.33f, -45.82f);
        p.cubicTo(-1.28f, -45.82f, -2.05f, -46.59f, -2.05f, -47.54f);
        p.cubicTo(-2.05f, -48.49f, -1.28f, -49.25f, -0.33f, -49.25f);
        p.cubicTo(0.62f, -49.25f, 1.39f, -48.49f, 1.39f, -47.54f);
        p.close();
        return p;
    }

    private static Path eye_glint() {
        Path p = new Path();
        p.moveTo(-0.25f, -48.35f);
        p.cubicTo(-0.25f, -47.95f, -0.58f, -47.62f, -0.98f, -47.62f);
        p.cubicTo(-1.39f, -47.62f, -1.72f, -47.95f, -1.72f, -48.35f);
        p.cubicTo(-1.72f, -48.76f, -1.39f, -49.09f, -0.98f, -49.09f);
        p.cubicTo(-0.58f, -49.09f, -0.25f, -48.76f, -0.25f, -48.35f);
        p.close();
        return p;
    }

    private static Path eye2_white() {
        Path p = new Path();
        p.moveTo(10.8f, -47.86f);
        p.cubicTo(10.8f, -45.97f, 9.26f, -44.43f, 7.36f, -44.43f);
        p.cubicTo(5.47f, -44.43f, 3.93f, -45.97f, 3.93f, -47.86f);
        p.cubicTo(3.93f, -49.76f, 5.47f, -51.3f, 7.36f, -51.3f);
        p.cubicTo(9.26f, -51.3f, 10.8f, -49.76f, 10.8f, -47.86f);
        p.close();
        return p;
    }

    private static Path eye2_pupil() {
        Path p = new Path();
        p.moveTo(10.39f, -47.54f);
        p.cubicTo(10.39f, -46.59f, 9.62f, -45.82f, 8.67f, -45.82f);
        p.cubicTo(7.72f, -45.82f, 6.95f, -46.59f, 6.95f, -47.54f);
        p.cubicTo(6.95f, -48.49f, 7.72f, -49.25f, 8.67f, -49.25f);
        p.cubicTo(9.62f, -49.25f, 10.39f, -48.49f, 10.39f, -47.54f);
        p.close();
        return p;
    }

    private static Path eye2_glint() {
        Path p = new Path();
        p.moveTo(8.75f, -48.35f);
        p.cubicTo(8.75f, -47.95f, 8.42f, -47.62f, 8.02f, -47.62f);
        p.cubicTo(7.61f, -47.62f, 7.28f, -47.95f, 7.28f, -48.35f);
        p.cubicTo(7.28f, -48.76f, 7.61f, -49.09f, 8.02f, -49.09f);
        p.cubicTo(8.42f, -49.09f, 8.75f, -48.76f, 8.75f, -48.35f);
        p.close();
        return p;
    }

    private static Path brow() {
        Path p = new Path();
        p.moveTo(-4.5f, -50.32f);
        p.lineTo(1.23f, -51.14f);
        p.lineTo(1.23f, -49.91f);
        p.lineTo(-4.5f, -49.09f);
        p.close();
        return p;
    }

    private static Path brow2() {
        Path p = new Path();
        p.moveTo(4.5f, -51.14f);
        p.lineTo(10.23f, -50.32f);
        p.lineTo(10.23f, -49.09f);
        p.lineTo(4.5f, -49.91f);
        p.close();
        return p;
    }

    private static Path mouth() {
        Path p = new Path();
        p.moveTo(-1.64f, -44.67f);
        p.quadTo(2.86f, -40.58f, 7.36f, -44.67f);
        p.quadTo(2.86f, -43.04f, -1.64f, -44.67f);
        p.close();
        return p;
    }

    private static Path cheek() {
        Path p = new Path();
        p.moveTo(-2.86f, -45.82f);
        p.cubicTo(-2.86f, -44.91f, -3.6f, -44.18f, -4.5f, -44.18f);
        p.cubicTo(-5.4f, -44.18f, -6.14f, -44.91f, -6.14f, -45.82f);
        p.cubicTo(-6.14f, -46.72f, -5.4f, -47.45f, -4.5f, -47.45f);
        p.cubicTo(-3.6f, -47.45f, -2.86f, -46.72f, -2.86f, -45.82f);
        p.close();
        return p;
    }

}
