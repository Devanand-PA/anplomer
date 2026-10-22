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

    /**
     * Advance width in the same units the rasteriser draws with (the JVM's
     * default sans is Helvetica-metric), so a dump shows exactly the layout the
     * renderer computed. The old {@code length * size * 0.5f} under-measured
     * wide strings, which is why a dump could show a label spilling out of a
     * button that was sized from {@code measureText}.
     */
    public float measureText(String s) {
        if (s == null || s.isEmpty()) {
            return 0f;
        }
        int perMille = 0;
        for (int i = 0; i < s.length(); i++) {
            perMille += glyphPerMille(s.charAt(i));
        }
        return perMille * textSize / 1000f;
    }

    /** Helvetica advance widths (units per 1000 em) for the printable range. */
    private static int glyphPerMille(char c) {
        if (c >= '0' && c <= '9') {
            return 556;
        }
        if (c >= 'a' && c <= 'z') {
            switch (c) {
                case 'i': case 'j': case 'l': return 222;
                case 'f': case 't': return 278;
                case 'r': return 333;
                case 'm': return 833;
                case 'c': case 'k': case 's': case 'v': case 'x': case 'y': case 'z':
                    return 500;
                case 'w': return 722;
                default: return 556;
            }
        }
        if (c >= 'A' && c <= 'Z') {
            switch (c) {
                case 'I': return 278;
                case 'J': return 500;
                case 'L': return 556;
                case 'F': case 'P': case 'S': case 'T': case 'Z': return 611;
                case 'M': case 'W': return c == 'M' ? 833 : 944;
                case 'A': case 'B': case 'E': case 'K': case 'V': case 'X': case 'Y':
                    return 667;
                case 'C': case 'D': case 'H': case 'N': case 'R': case 'U': return 722;
                case 'G': case 'O': case 'Q': return 778;
                default: return 667;
            }
        }
        switch (c) {
            case ' ': return 278;
            case '\'': return 191;
            case '"': return 355;
            case ',': case '.': case ':': case ';': case '!': case '|': return 278;
            case '/': case '\\': return 278;
            case '(': case ')': case '[': case ']': case '-': case '`': return 333;
            case '{': case '}': return 334;
            case '+': case '<': case '=': case '>': case '~': return 584;
            case '#': case '$': case '_': return 556;
            case '%': return 889;
            case '&': return 667;
            case '*': return 389;
            case '@': return 1015;
            case '^': return 469;
            case '\u2022': case '\u00b7': return 350;   // bullet / middot used in hints
            case '\u2192': case '\u2190': return 1000;   // arrows in the key hints
            default: return 556;
        }
    }
    public float getTextWidth(String s) { return measureText(s); }
    public float getTextHeight() { return textSize; }
    public void getTextBounds(String s, int start, int end, RectF out) {
        float w = s == null ? 0f : s.length() * textSize * 0.5f;
        out.set(0f, -textSize, w, 0f);
    }
}
