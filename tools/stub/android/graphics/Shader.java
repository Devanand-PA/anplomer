package android.graphics;

public class Shader {

    public enum TileMode { CLAMP, REPEAT, MIRROR }

    protected Shader() { }

    public static Shader createLinearGradient(float x0, float y0, float x1, float y1,
                                              int[] colors, float[] positions, TileMode mode) {
        if (colors == null || colors.length == 0) {
            throw new IllegalArgumentException("no colors");
        }
        if (positions != null && positions.length != colors.length) {
            throw new IllegalArgumentException("positions.length != colors.length");
        }
        if (positions != null) {
            for (float p : positions) {
                if (!Float.isFinite(p)) { throw new IllegalArgumentException("non-finite position"); }
            }
        }
        return new Shader();
    }

    public void setLocalMatrix(Matrix m) { }
}
