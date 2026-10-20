package android.graphics;

public class LinearGradient extends Shader {

    public final float x0, y0, x1, y1;
    public final int[] colors;
    public final float[] positions;
    public final TileMode tileMode;

    public LinearGradient(float x0, float y0, float x1, float y1,
                          int[] colors, float[] positions, TileMode tileMode) {
        if (colors == null || colors.length < 2) {
            throw new IllegalArgumentException("LinearGradient needs >= 2 colors");
        }
        if (positions != null && positions.length != colors.length) {
            throw new IllegalArgumentException("positions.length != colors.length");
        }
        for (float p : positions) {
            if (!Float.isFinite(p)) { throw new IllegalArgumentException("non-finite position"); }
        }
        if (x0 < -1e6f || y0 < -1e6f || x1 > 1e9f || y1 > 1e9f) {
            throw new IllegalArgumentException("gradient endpoints out of range");
        }
        this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
        this.colors = colors.clone();
        this.positions = positions == null ? null : positions.clone();
        this.tileMode = tileMode;
    }
}
