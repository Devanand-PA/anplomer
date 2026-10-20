package android.graphics;

public class Paint {

    public static final int ANTI_ALIAS_FLAG = 1;
    public static final int LINEAR_TEXT_FLAG = 2;
    public static final int SUBPIXEL_TEXT_FLAG = 4;

    public enum Style { FILL, STROKE, FILL_AND_STROKE }
    public enum Align { LEFT, CENTER, RIGHT }
    public enum Cap { BUTT, ROUND, SQUARE }
    public enum Join { MITER, ROUND, BEVEL }

    private int color = 0xFF000000;
    private Style style = Style.FILL;
    private float strokeWidth = 1f;
    private Cap cap = Cap.BUTT;
    private Align align = Align.LEFT;
    private float textSize = 12f;
    private Shader shader;
    private Typeface typeface;
    private float shadowRadius;
    private int shadowColor;
    private int flags;

    public Paint() { }
    public Paint(int flags) { this.flags = flags; }

    // Alpha lives in the top byte of `color`, exactly like android.graphics.Paint:
    // setColor() replaces all four bytes, setAlpha() rewrites only the alpha byte,
    // and getAlpha() reports the current alpha byte. The previous stub kept a
    // separate always-255 alpha and forced colors opaque, which hid a whole class
    // of real-device bugs — e.g. a translucent colour (0x33 pad highlight) leaking
    // into the next frame's shader-modulated sky gradient.
    public void setColor(int c) {
        color = c;
    }
    public int getColor() { return color; }
    public void setAlpha(int a) {
        color = (color & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }
    public int getAlpha() { return color >>> 24; }
    public void setStyle(Style s) { style = s; }
    public Style getStyle() { return style; }
    public void setStrokeWidth(float w) { if (w < 0f) { throw new IllegalStateException("negative stroke width " + w); } strokeWidth = w; }
    public float getStrokeWidth() { return strokeWidth; }
    public void setStrokeCap(Cap c) { cap = c; }
    public Cap getStrokeCap() { return cap; }
    public void setStrokeJoin(Join j) { }
    public void setTextAlign(Align a) { align = a; }
    public Align getTextAlign() { return align; }
    public void setTextSize(float s) { if (!(s >= 0f) || !Float.isFinite(s)) { throw new IllegalStateException("bad text size " + s); } textSize = s; }
    public float getTextSize() { return textSize; }
    public void setShader(Shader s) { shader = s; }
    public Shader getShader() { return shader; }
    public void setTypeface(Typeface t) { typeface = t; }
    public Typeface getTypeface() { return typeface; }
    public void setShadowLayer(float r, float dx, float dy, int c) { if (r < 0f) { throw new IllegalStateException("negative shadow radius"); } shadowRadius = r; shadowColor = c; }
    public void clearShadowLayer() { shadowRadius = 0f; }
    public void setFlags(int f) { flags = f; }
    public int getFlags() { return flags; }

    public float measureText(String s) { return s == null ? 0f : s.length() * textSize * 0.5f; }
    public float getTextWidth(String s) { return measureText(s); }
    public float getTextHeight() { return textSize; }
    public void getTextBounds(String s, int start, int end, RectF out) {
        float w = s == null ? 0f : s.length() * textSize * 0.5f;
        out.set(0f, -textSize, w, 0f);
    }
}
