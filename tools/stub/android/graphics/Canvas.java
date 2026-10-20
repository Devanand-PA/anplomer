package android.graphics;

import java.awt.geom.AffineTransform;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Minimal JVM stand-in for android.graphics.Canvas used by the renderer smoke
 * test. Records calls and REJECTS bad geometry (non-finite numbers, negative
 * radii/sizes) so a rendering bug cannot pass silently.
 */
public class Canvas {

    public int width, height;
    public int drawCount;
    public int saveCount = 1;

    /**
     * Alpha of the first full-screen fill drawn since {@link #beginFrame()},
     * or -1 if none was drawn. The sky gradient is the frame's backdrop, so it
     * must be opaque: a shader is modulated by paint alpha, and a leaked alpha
     * makes the sky translucent (ghost trails behind moving sprites, and a dark
     * sky when the buffer underneath is not the previous frame).
     */
    public int skyAlpha = -1;

    public void beginFrame() { skyAlpha = -1; }

    /** Current transform, plus the save/restore stack. */
    protected final AffineTransform transform = new AffineTransform();
    protected final Deque<AffineTransform> stack = new ArrayDeque<>();

    public Canvas(int w, int h) {
        this.width = w;
        this.height = h;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    private void chk(float... v) {
        for (float f : v) {
            if (!Float.isFinite(f)) {
                throw new IllegalStateException("non-finite coordinate in Canvas: " + f);
            }
        }
    }

    private void radius(float r) {
        if (!(r >= 0f) || !Float.isFinite(r)) {
            throw new IllegalStateException("bad radius in Canvas: " + r);
        }
    }

    public int save() {
        drawCount++;
        saveCount++;
        stack.push(new AffineTransform(transform));
        return saveCount;
    }

    public void restore() {
        saveCount = Math.max(0, saveCount - 1);
        AffineTransform prev = stack.poll();
        if (prev != null) {
            transform.setTransform(prev);
        }
    }

    public void translate(float dx, float dy) {
        chk(dx, dy);
        transform.translate(dx, dy);
    }

    public void scale(float sx, float sy) {
        chk(sx, sy);
        transform.scale(sx, sy);
    }

    public void rotate(float deg) {
        chk(deg);
        transform.rotate(Math.toRadians(deg));
    }

    public void drawRect(float l, float t, float r, float b, Paint p) {
        chk(l, t, r, b);
        if (skyAlpha < 0
                && l <= 0.5f && t <= 0.5f
                && r >= width - 0.5f && b >= height - 0.5f) {
            skyAlpha = p.getAlpha();
        }
        if (r < l || b < t) { throw new IllegalStateException("inverted rect " + l + " " + t + " " + r + " " + b); }
        drawCount++;
    }
    public void drawCircle(float cx, float cy, float r, Paint p) { chk(cx, cy); radius(r); drawCount++; }
    public void drawLine(float x1, float y1, float x2, float y2, Paint p) { chk(x1, y1, x2, y2); drawCount++; }
    public void drawRect(RectF rect, Paint p) {
        chk(rect.left, rect.top, rect.right, rect.bottom);
        if (rect.right < rect.left || rect.bottom < rect.top) {
            throw new IllegalStateException("inverted rectf " + rect.left + " " + rect.top + " " + rect.right + " " + rect.bottom);
        }
        drawCount++;
    }
    public void drawOval(RectF rect, Paint p) { chk(rect.left, rect.top, rect.right, rect.bottom); drawCount++; }
    public void drawPath(Path p, Paint paint) { drawCount++; }
    public void drawArc(RectF oval, float start, float sweep, boolean useCenter, Paint p) {
        chk(oval.left, oval.top, oval.right, oval.bottom); chk(start, sweep); drawCount++;
    }
    public void drawRoundRect(RectF rect, float rx, float ry, Paint p) {
        chk(rect.left, rect.top, rect.right, rect.bottom, rx, ry);
        if (rx < 0f || ry < 0f) { throw new IllegalStateException("negative corner radius"); }
        drawCount++;
    }
    public void drawText(String s, float x, float y, Paint p) { chk(x, y); drawCount++; }
    public void drawPoint(float x, float y, Paint p) { chk(x, y); drawCount++; }
}
