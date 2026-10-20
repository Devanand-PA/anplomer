package com.example.anplomer;

/** A floating coin. World position is the coin centre. */
public final class Coin {

    public final float x, y;
    public final int index;
    public final float size = Constants.COIN_SIZE;
    public float spinPhase;

    public Coin(float x, float y, int index, float spinPhase) {
        this.x = x;
        this.y = y;
        this.index = index;
        this.spinPhase = spinPhase;
    }

    public boolean overlaps(float px, float py, float pw, float ph) {
        // slightly generous pick-up radius feels better than a strict box
        float r = size / 2f + 4f;
        float cx = Math.max(px, Math.min(x, px + pw));
        float cy = Math.max(py, Math.min(y, py + ph));
        float dx = x - cx;
        float dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }
}
