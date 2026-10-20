package android.graphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal JVM stand-in for android.graphics.Path: records the segments so the
 * renderer smoke test can validate them and (optionally) rasterise them.
 */
public class Path {

    /** Each entry is {"M"|"L"|"Q"|"C", x, y, ...}. */
    public final List<float[]> segments = new ArrayList<>();
    private float lastX, lastY;
    private boolean closed;

    public void moveTo(float x, float y) { chk(x, y); segments.add(new float[]{'M', x, y}); lastX = x; lastY = y; }
    public void lineTo(float x, float y) { chk(x, y); segments.add(new float[]{'L', x, y}); lastX = x; lastY = y; }
    public void quadTo(float x1, float y1, float x2, float y2) {
        chk(x1, y1, x2, y2);
        segments.add(new float[]{'Q', lastX, lastY, x1, y1, x2, y2});
        lastX = x2; lastY = y2;
    }
    public void cubicTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        chk(x1, y1, x2, y2, x3, y3);
        segments.add(new float[]{'C', lastX, lastY, x1, y1, x2, y2, x3, y3});
        lastX = x3; lastY = y3;
    }
    public void close() { closed = true; segments.add(new float[]{'Z'}); }
    public void reset() { segments.clear(); closed = false; }
    public void rewind() { segments.clear(); closed = false; }
    public void set(Path p) { segments.clear(); segments.addAll(p.segments); closed = p.closed; }
    public boolean isEmpty() { return segments.isEmpty(); }
    public boolean isClosed() { return closed; }

    private void chk(float... v) {
        for (float f : v) {
            if (!Float.isFinite(f)) {
                throw new IllegalStateException("non-finite path coordinate: " + f);
            }
        }
    }
}
