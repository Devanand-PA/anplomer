package com.example.anplomer;

/**
 * Smooth follow camera with world clamping and screen shake.
 * Holds the top-left corner of the view in world units plus the zoom factor
 * that maps world units to screen pixels.
 */
public final class Camera {

    public float x, y;
    public float zoom = 1f;
    public float viewW, viewH;

    private float shakeTime;
    private float shakeMag;
    private float shakeOffsetX, shakeOffsetY;

    public void resize(float screenW, float screenH) {
        // show about 10.5 tiles of vertical world, but never less than 1250
        // units of horizontal world (keeps phones/tablets sane)
        zoom = screenH / (Constants.TILE * 10.5f);
        float minViewW = 1250f;
        if (screenW / zoom < minViewW) {
            zoom = screenW / minViewW;
        }
        viewW = screenW / zoom;
        viewH = screenH / zoom;
    }

    public void snapTo(float tx, float ty, Level lvl) {
        x = desiredX(tx, lvl);
        y = desiredY(ty, lvl);
    }

    public void update(float dt, float tx, float ty, Level lvl) {
        float lerp = Math.min(1f, dt * 7f);
        x += (desiredX(tx, lvl) - x) * lerp;
        y += (desiredY(ty, lvl) - y) * lerp;

        if (shakeTime > 0f) {
            shakeTime -= dt;
            float t = shakeTime * 45f;
            shakeOffsetX = (float) Math.sin(t * 1.7f) * shakeMag;
            shakeOffsetY = (float) Math.cos(t * 2.3f) * shakeMag * 0.7f;
            shakeMag *= Math.pow(0.02f, dt);   // fast decay
            if (shakeTime <= 0f) {
                shakeOffsetX = 0f;
                shakeOffsetY = 0f;
            }
        } else {
            shakeOffsetX = 0f;
            shakeOffsetY = 0f;
        }
    }

    public void shake(float magnitude, float seconds) {
        shakeMag = Math.max(shakeMag, magnitude);
        shakeTime = Math.max(shakeTime, seconds);
    }

    public float offsetX() {
        return shakeOffsetX;
    }

    public float offsetY() {
        return shakeOffsetY;
    }

    /**
     * Camera position that would centre {@code (tx, ty)} while staying inside
     * the level. The TARGET is clamped, never the lerped position: clamping the
     * position instead makes the camera collapse onto a level edge and stick
     * there whenever the follow target sits outside the viewable range (e.g.
     * standing on the floor of a level that is taller than the screen).
     */
    private float desiredX(float tx, Level lvl) {
        float worldW = lvl.w * Constants.TILE;
        if (worldW <= viewW) {
            return (worldW - viewW) / 2f;
        }
        return Math.max(0f, Math.min(tx - viewW / 2f, worldW - viewW));
    }

    private float desiredY(float ty, Level lvl) {
        float worldH = lvl.h * Constants.TILE;
        if (worldH <= viewH) {
            return (worldH - viewH) / 2f;
        }
        return Math.max(0f, Math.min(ty - viewH / 2f, worldH - viewH));
    }

    public float worldToScreenX(float wx) {
        return (wx - x - shakeOffsetX) * zoom;
    }

    public float worldToScreenY(float wy) {
        return (wy - y - shakeOffsetY) * zoom;
    }
}
