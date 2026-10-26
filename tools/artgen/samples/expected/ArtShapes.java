// GENERATED FILE - DO NOT EDIT BY HAND.
// Source:      tools/artgen/samples/shapes.svg
// Generator:   tools/artgen/svg2java.py
// Regenerate:  python3 tools/artgen/svg2java.py tools/artgen/samples/shapes.svg --class ArtShapes
//
// Segments are baked to absolute M/L/Q/C/Z in game units (y down);
// `--fit` moved the artwork's anchor to the origin, so drawing needs
// no extra scaling or offset.
package com.example.anplomer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

public final class ArtShapes {

    /** Artwork extents in game units (informational, not the entity size). */
    public static final float WIDTH = 104f;
    public static final float HEIGHT = 84f;

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
        new Part("all-commands", 0xFF3F6BE0, 0x00000000, 0f, all_commands(), 44.5f, 2f),
        new Part("relative", 0xFFE14A4A, 0x00000000, 0f, relative(), 89f, 14f),
        new Part("rounded", 0xFFFFCB3D, 0x00000000, 0f, rounded(), 25f, 40f),
        new Part("plain", 0xFF8BC34A, 0x00000000, 0f, plain(), 52f, 40f),
        new Part("disc", 0xFF102030, 0x00000000, 0f, disc(), 70f, 41f),
        new Part("oval", 0xFF9C27B0, 0xFF222222, 2f, oval(), 92f, 44f),
        new Part("rule", 0xFF000000, 0xFF555555, 1.5f, rule(), 35f, 66f),
        new Part("zig", 0x00000000, 0xFF00897B, 2f, zig(), 75f, 60f),
        new Part("tri", 0xFFFF7043, 0x00000000, 0f, tri(), 104f, 58f),
        new Part("donut", 0xFF263238, 0x00000000, 0f, donut(), 26f, 72f),
    };

    private ArtShapes() { }

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

    private static Path all_commands() {
        Path p = new Path();
        p.moveTo(14f, 12f);
        p.lineTo(24f, 12f);
        p.lineTo(29f, 12f);
        p.lineTo(29f, 22f);
        p.cubicTo(34f, 22f, 34f, 32f, 39f, 32f);
        p.cubicTo(44f, 32f, 49f, 22f, 49f, 12f);
        p.quadTo(54f, 2f, 59f, 12f);
        p.quadTo(64f, 22f, 69f, 12f);
        p.cubicTo(72.31f, 12f, 75f, 14.69f, 75f, 18f);
        p.close();
        return p;
    }

    private static Path relative() {
        Path p = new Path();
        p.moveTo(84f, 14f);
        p.lineTo(94f, 14f);
        p.lineTo(94f, 22f);
        p.lineTo(84f, 22f);
        p.close();
        return p;
    }

    private static Path rounded() {
        Path p = new Path();
        p.moveTo(15f, 40f);
        p.lineTo(35f, 40f);
        p.cubicTo(37.76f, 40f, 40f, 41.79f, 40f, 44f);
        p.lineTo(40f, 56f);
        p.cubicTo(40f, 58.21f, 37.76f, 60f, 35f, 60f);
        p.lineTo(15f, 60f);
        p.cubicTo(12.24f, 60f, 10f, 58.21f, 10f, 56f);
        p.lineTo(10f, 44f);
        p.cubicTo(10f, 41.79f, 12.24f, 40f, 15f, 40f);
        p.close();
        return p;
    }

    private static Path plain() {
        Path p = new Path();
        p.moveTo(46f, 40f);
        p.lineTo(58f, 40f);
        p.lineTo(58f, 52f);
        p.lineTo(46f, 52f);
        p.close();
        return p;
    }

    private static Path disc() {
        Path p = new Path();
        p.moveTo(79f, 50f);
        p.cubicTo(79f, 54.97f, 74.97f, 59f, 70f, 59f);
        p.cubicTo(65.03f, 59f, 61f, 54.97f, 61f, 50f);
        p.cubicTo(61f, 45.03f, 65.03f, 41f, 70f, 41f);
        p.cubicTo(74.97f, 41f, 79f, 45.03f, 79f, 50f);
        p.close();
        return p;
    }

    private static Path oval() {
        Path p = new Path();
        p.moveTo(104f, 50f);
        p.cubicTo(104f, 53.31f, 98.63f, 56f, 92f, 56f);
        p.cubicTo(85.37f, 56f, 80f, 53.31f, 80f, 50f);
        p.cubicTo(80f, 46.69f, 85.37f, 44f, 92f, 44f);
        p.cubicTo(98.63f, 44f, 104f, 46.69f, 104f, 50f);
        p.close();
        return p;
    }

    private static Path rule() {
        Path p = new Path();
        p.moveTo(10f, 66f);
        p.lineTo(60f, 66f);
        return p;
    }

    private static Path zig() {
        Path p = new Path();
        p.moveTo(66f, 66f);
        p.lineTo(72f, 60f);
        p.lineTo(78f, 66f);
        p.lineTo(84f, 60f);
        return p;
    }

    private static Path tri() {
        Path p = new Path();
        p.moveTo(96f, 66f);
        p.lineTo(104f, 58f);
        p.lineTo(112f, 66f);
        p.close();
        return p;
    }

    private static Path donut() {
        Path p = new Path();
        p.setFillType(Path.FillType.EVEN_ODD);
        p.moveTo(20f, 78f);
        p.cubicTo(20f, 81.31f, 22.69f, 84f, 26f, 84f);
        p.cubicTo(29.31f, 84f, 32f, 81.31f, 32f, 78f);
        p.cubicTo(32f, 74.69f, 29.31f, 72f, 26f, 72f);
        p.cubicTo(22.69f, 72f, 20f, 74.69f, 20f, 78f);
        p.close();
        p.moveTo(23f, 78f);
        p.cubicTo(23f, 76.34f, 24.34f, 75f, 26f, 75f);
        p.cubicTo(27.66f, 75f, 29f, 76.34f, 29f, 78f);
        p.cubicTo(29f, 79.66f, 27.66f, 81f, 26f, 81f);
        p.cubicTo(24.34f, 81f, 23f, 79.66f, 23f, 78f);
        p.close();
        return p;
    }

}
