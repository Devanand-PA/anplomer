package com.example.anplomer;

/**
 * Tiny fixed-size particle system (dust, sparkles, confetti, smoke puffs).
 * Plain parallel arrays so it stays free of Android types.
 */
public final class Particles {

    public static final int MAX = 520;

    private final float[] px = new float[MAX];
    private final float[] py = new float[MAX];
    private final float[] vx = new float[MAX];
    private final float[] vy = new float[MAX];
    private final float[] life = new float[MAX];
    private final float[] maxLife = new float[MAX];
    private final float[] size = new float[MAX];
    private final float[] grav = new float[MAX];
    private final int[] color = new int[MAX];
    private int count;
    private int cursor;

    public int size() {
        return count;
    }

    public float x(int i) {
        return px[i];
    }

    public float y(int i) {
        return py[i];
    }

    public float life(int i) {
        return life[i];
    }

    public float maxLife(int i) {
        return maxLife[i];
    }

    public float size(int i) {
        return size[i];
    }

    public int color(int i) {
        return color[i];
    }

    public void clear() {
        count = 0;
        cursor = 0;
    }

    private void add(float x, float y, float vx, float vy, float ttl, float s, float g, int argb) {
        int i;
        if (count < MAX) {
            i = count++;
        } else {
            i = cursor;
            cursor = (cursor + 1) % MAX;
        }
        px[i] = x;
        py[i] = y;
        this.vx[i] = vx;
        this.vy[i] = vy;
        life[i] = ttl;
        maxLife[i] = ttl;
        size[i] = s;
        grav[i] = g;
        color[i] = argb;
    }

    public void update(float dt) {
        for (int i = 0; i < count; i++) {
            life[i] -= dt;
            if (life[i] <= 0f) {
                continue;
            }
            vy[i] += grav[i] * dt;
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;
            vx[i] *= 1f - Math.min(1f, dt * 2.2f);
        }
    }

    // ---- emitters -----------------------------------------------------

    private static final int DUST = 0xFFD8C7A6;
    private static final int DUST_DARK = 0xFFB49B78;
    private static final int GOLD = 0xFFFFD34D;
    private static final int GOLD_LIGHT = 0xFFFFF0A8;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int RED = 0xFFE4453A;
    private static final int GREEN = 0xFF57C84D;
    private static final int BLUE = 0xFF4FA8FF;

    public void dust(float x, float y, int n, float dirX) {
        for (int i = 0; i < n; i++) {
            float a = (float) Math.random();
            add(x + (float) (Math.random() - 0.5f) * 26f,
                    y + (float) (Math.random() - 0.5f) * 8f,
                    -dirX * (40f + a * 90f),
                    -20f - a * 60f,
                    0.30f + a * 0.25f,
                    7f + a * 9f,
                    140f,
                    i % 2 == 0 ? DUST : DUST_DARK);
        }
    }

    public void landPuff(float x, float y) {
        for (int i = 0; i < 8; i++) {
            float a = (float) Math.random();
            float side = i % 2 == 0 ? 1f : -1f;
            add(x + side * 14f, y, side * (70f + a * 120f), -30f - a * 50f,
                    0.32f + a * 0.2f, 8f + a * 10f, 220f, DUST);
        }
    }

    public void sparkle(float x, float y, int n) {
        for (int i = 0; i < n; i++) {
            float a = (float) Math.random() * (float) Math.PI * 2f;
            float sp = 90f + (float) Math.random() * 180f;
            add(x, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp,
                    0.35f + (float) Math.random() * 0.3f,
                    6f + (float) Math.random() * 7f,
                    60f,
                    i % 3 == 0 ? WHITE : GOLD);
        }
    }

    public void poof(float x, float y, int n, int argb) {
        for (int i = 0; i < n; i++) {
            float a = (float) Math.random() * (float) Math.PI * 2f;
            float sp = 60f + (float) Math.random() * 150f;
            add(x, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp - 40f,
                    0.4f + (float) Math.random() * 0.35f,
                    9f + (float) Math.random() * 12f,
                    260f,
                    argb);
        }
    }

    public void confetti(float x, float y, int n) {
        int[] palette = {RED, GREEN, BLUE, GOLD, WHITE};
        for (int i = 0; i < n; i++) {
            float a = (float) Math.random() * (float) Math.PI * 2f;
            float sp = 160f + (float) Math.random() * 380f;
            add(x, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp - 220f,
                    1.1f + (float) Math.random() * 0.9f,
                    8f + (float) Math.random() * 8f,
                    700f,
                    palette[i % palette.length]);
        }
    }

    public void respawnBurst(float x, float y, int n) {
        for (int i = 0; i < n; i++) {
            float a = (float) (i / (double) n) * (float) Math.PI * 2f;
            add(x, y, (float) Math.cos(a) * 160f, (float) Math.sin(a) * 160f,
                    0.5f, 8f, 0f, WHITE);
        }
    }
}
