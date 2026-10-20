package android.graphics;

public class Matrix {

    public void set(float[] v) {
        if (v == null || v.length < 9) { throw new IllegalArgumentException("matrix needs 9 values"); }
        for (float f : v) { if (!Float.isFinite(f)) { throw new IllegalArgumentException("non-finite matrix"); } }
    }
}
