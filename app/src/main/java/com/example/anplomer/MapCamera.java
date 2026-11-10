package com.example.anplomer;

/**
 * Pan/zoom camera for the world map shown on the LEVEL_SELECT screen.
 * The map has its own coordinate system (world units), independent of the
 * in-level camera: screen = (world - center) * zoom + screenCenter.
 */
public final class MapCamera {

    public static final float MIN_ZOOM = 0.45f;
    public static final float MAX_ZOOM = 3.5f;

    /** Viewport centre in world coordinates. */
    public float x, y;
    /** Zoom factor: 1 = default, larger zooms in. */
    public float zoom = 1f;

    /** World bounds of the drawn landmass. */
    public float worldLeft = -1400f;
    public float worldTop = -950f;
    public float worldRight = 1550f;
    public float worldBottom = 1050f;

    private float sw = 1080f;
    private float sh = 480f;

    // pinch state
    private boolean pinching;
    private float pinchStartDist;
    private float pinchStartZoom;
    private float anchorWX, anchorWY;

    public MapCamera() {
        x = 0f;
        y = 0f;
    }

    /** Screen size, needed for coordinate conversion and clamping. */
    public void setScreenSize(float w, float h) {
        sw = w;
        sh = h;
        clamp();
    }

    public float screenToWorldX(float sx) {
        return (sx - sw / 2f) / zoom + x;
    }

    public float screenToWorldY(float sy) {
        return (sy - sh / 2f) / zoom + y;
    }

    public float worldToScreenX(float wx) {
        return (wx - x) * zoom + sw / 2f;
    }

    public float worldToScreenY(float wy) {
        return (wy - y) * zoom + sh / 2f;
    }

    /** Drag the map by a screen-space delta (finger drags content with it). */
    public void panBy(float dxScreen, float dyScreen) {
        x -= dxScreen / zoom;
        y -= dyScreen / zoom;
        clamp();
    }

    /** Begin a two-finger pinch; the world point under the midpoint stays put. */
    public void beginPinch(float dist, float midX, float midY) {
        pinching = true;
        pinchStartDist = Math.max(1f, dist);
        pinchStartZoom = zoom;
        anchorWX = screenToWorldX(midX);
        anchorWY = screenToWorldY(midY);
    }

    /** Update the pinch: zoom by dist/startDist around the anchored point. */
    public void updatePinch(float dist, float startDist, float midX, float midY) {
        if (!pinching) {
            return;
        }
        zoom = clampZoom(pinchStartZoom * dist / Math.max(1f, startDist));
        x = anchorWX - (midX - sw / 2f) / zoom;
        y = anchorWY - (midY - sh / 2f) / zoom;
        clamp();
    }

    public void endPinch() {
        pinching = false;
    }

    /** Centre the view on a world point at a given zoom. */
    public void focusOn(float wx, float wy, float z) {
        x = wx;
        y = wy;
        zoom = clampZoom(z);
        clamp();
    }

    public void reset() {
        x = 0f;
        y = 0f;
        zoom = 1f;
        clamp();
    }

    private static float clampZoom(float z) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
    }

    /** Keep the viewport inside the world bounds (centre when it is larger). */
    private void clamp() {
        float hw = sw / (2f * zoom);
        float hh = sh / (2f * zoom);
        if (worldRight - worldLeft > hw * 2f) {
            x = Math.max(worldLeft + hw, Math.min(worldRight - hw, x));
        } else {
            x = (worldLeft + worldRight) / 2f;
        }
        if (worldBottom - worldTop > hh * 2f) {
            y = Math.max(worldTop + hh, Math.min(worldBottom - hh, y));
        } else {
            y = (worldTop + worldBottom) / 2f;
        }
    }
}
