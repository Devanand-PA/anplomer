package android.graphics;

public class RectF {

    public float left, top, right, bottom;

    public RectF() { }
    public RectF(float l, float t, float r, float b) { set(l, t, r, b); }

    public void set(float l, float t, float r, float b) {
        if (!Float.isFinite(l) || !Float.isFinite(t) || !Float.isFinite(r) || !Float.isFinite(b)) {
            throw new IllegalStateException("non-finite rect");
        }
        left = l; top = t; right = r; bottom = b;
    }
    public float width() { return right - left; }
    public float height() { return bottom - top; }
    public float centerX() { return (left + right) / 2f; }
    public float centerY() { return (top + bottom) / 2f; }
    public boolean isEmpty() { return right <= left || bottom <= top; }
}
