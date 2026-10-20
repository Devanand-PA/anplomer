package com.example.anplomer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * Everything visible: parallax scenery, tiles, entities, HUD and overlays.
 * Drawing is split into a screen-space layer (sky, scenery, UI) and a
 * world-space layer (tiles and bodies) transformed by the camera.
 */
public final class Renderer {

    // ------------------------------------------------------------------
    // palettes per level: 0 = meadow day, 1 = sunset ruins, 2 = night peak
    // ------------------------------------------------------------------
    private static final class Pal {
        final int skyTop, skyMid, skyBot;
        final int cloud;
        final int far, mid, near;
        final int grass, grassDark, dirt, dirtDark;
        final int stone, stoneDark;
        final boolean night;

        Pal(int skyTop, int skyMid, int skyBot, int cloud, int far, int mid, int near,
            int grass, int grassDark, int dirt, int dirtDark, int stone, int stoneDark, boolean night) {
            this.skyTop = skyTop;
            this.skyMid = skyMid;
            this.skyBot = skyBot;
            this.cloud = cloud;
            this.far = far;
            this.mid = mid;
            this.near = near;
            this.grass = grass;
            this.grassDark = grassDark;
            this.dirt = dirt;
            this.dirtDark = dirtDark;
            this.stone = stone;
            this.stoneDark = stoneDark;
            this.night = night;
        }
    }

    private static final Pal[] PALS = {
            new Pal(0xFF2E6BD6, 0xFF5AA7F0, 0xFFBFE4FF, 0xFFFFFFFF,
                    0xFF8FA6DE, 0xFF4F9E5E, 0xFF2F7346,
                    0xFF59C24C, 0xFF3E9E3A, 0xFF9A6236, 0xFF7A4A28,
                    0xFF9AA3AE, 0xFF737C88, false),
            new Pal(0xFF2A2A66, 0xFFB8547A, 0xFFFFC178, 0xFFFFE3C6,
                    0xFF6A4C8C, 0xFF7A5070, 0xFF43305A,
                    0xFF84B84A, 0xFF5F9438, 0xFF8E5A34, 0xFF6F421F,
                    0xFFA29898, 0xFF7A7070, false),
            new Pal(0xFF0A1034, 0xFF1B2A6B, 0xFF3E56A6, 0xFFC8D6FF,
                    0xFF3E4E8E, 0xFF24406B, 0xFF152A46,
                    0xFF3FA36A, 0xFF2C7C50, 0xFF5D4433, 0xFF43301F,
                    0xFF8089A8, 0xFF5C6480, true),
    };

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    public Renderer() {
        stroke.setStyle(Paint.Style.STROKE);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setShadowLayer(6f, 0f, 4f, 0x99000000);
    }

    // ------------------------------------------------------------------
    // entry point
    // ------------------------------------------------------------------

    public void draw(Game g, Canvas c, float sw, float sh) {
        // No state mutation here: the game loop owns Game and applies surface
        // size changes on its own thread before calling draw().
        Pal p = PALS[Math.min(g.levelIndex, PALS.length - 1)];

        drawSky(c, sw, sh, p, g);
        drawScenery(c, sw, sh, p, g);

        // ---- world space ----
        float z = g.camera.zoom;
        c.save();
        c.translate((-g.camera.x - g.camera.offsetX()) * z, (-g.camera.y - g.camera.offsetY()) * z);
        c.scale(z, z);

        drawTiles(c, g, p);
        drawCheckpoints(c, g);
        drawGoal(c, g);
        drawPlatforms(c, g);
        drawCoins(c, g);
        drawEnemies(c, g);
        if (!(g.state == Game.State.MENU && g.player.dead)) {
            drawPlayer(c, g);
        }
        drawParticles(c, g);

        c.restore();

        // ---- screen space UI ----
        drawHud(c, g, sw, sh);
        if (g.state == Game.State.PLAYING || g.state == Game.State.PAUSED) {
            drawControls(c, g);
        }
        drawOverlay(c, g, sw, sh);
    }

    // ------------------------------------------------------------------
    // sky + parallax scenery
    // ------------------------------------------------------------------

    private void drawSky(Canvas c, float sw, float sh, Pal p, Game g) {
        // A shader is MODULATED by the paint's alpha, and `fill` is shared by
        // the whole renderer. While a run pad or jump button is held,
        // drawControls ends the frame with the 0x33 pressed-pad highlight in
        // `fill`, so without this reset the NEXT frame's sky rendered at 20%
        // alpha: over an unwiped lockCanvas buffer the previous frame showed
        // through as ghost trails behind the player and coins, and over a
        // black-wiped buffer the bright blue collapsed to dark navy — hence
        // "the sky turns black while moving". The sky is the backdrop for the
        // entire frame and must always be opaque.
        fill.setAlpha(255);
        fill.setShader(new LinearGradient(0f, 0f, 0f, sh,
                new int[]{p.skyTop, p.skyMid, p.skyBot},
                new float[]{0f, 0.58f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0f, 0f, sw, sh, fill);
        fill.setShader(null);

        if (p.night) {
            // stars
            text.setColor(0xFFFFFFFF);
            for (int i = 0; i < 90; i++) {
                float sx = h1(i * 3 + 1) * sw;
                float sy = h1(i * 3 + 2) * sh * 0.62f;
                float tw = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(g.worldTime * 2.4f + i));
                fill.setColor(Color.argb((int) (190 * tw), 255, 255, 255));
                float r = 1.1f + h1(i * 7) * 1.9f;
                c.drawCircle(sx, sy, r, fill);
            }
            // moon
            float mx = sw * 0.78f;
            float my = sh * 0.17f;
            fill.setColor(0x30FFFFB0);
            c.drawCircle(mx, my, 78f, fill);
            fill.setColor(0xFFFFF6D0);
            c.drawCircle(mx, my, 46f, fill);
            fill.setColor(0xFFE8DFB4);
            c.drawCircle(mx - 14f, my - 8f, 9f, fill);
            c.drawCircle(mx + 12f, my + 14f, 6f, fill);
            c.drawCircle(mx + 4f, my - 18f, 4f, fill);
        } else {
            // sun with glow
            float sx = sw * (p == PALS[1] ? 0.30f : 0.80f);
            float sy = sh * (p == PALS[1] ? 0.40f : 0.18f);
            int glow = p == PALS[1] ? 0x55FFD9A0 : 0x44FFF6C0;
            fill.setColor(glow);
            c.drawCircle(sx, sy, 130f, fill);
            fill.setColor(p == PALS[1] ? 0xFFFFD08A : 0xFFFFF3C4);
            c.drawCircle(sx, sy, 62f, fill);
        }

        // clouds
        float scroll = g.camera.x * 0.18f;
        float spacing = 460f;
        int first = (int) Math.floor(scroll / spacing) - 1;
        for (int i = first; i <= first + (int) (sw / spacing) + 2; i++) {
            float cx = i * spacing + h1(i * 13) * 200f - scroll;
            float cy = sh * (0.10f + h1(i * 13 + 5) * 0.30f);
            float scale = 0.7f + h1(i * 13 + 9) * 0.8f;
            float drift = (float) Math.sin(g.worldTime * 0.35f + i) * 14f;
            drawCloud(c, cx + drift, cy, scale, p.cloud);
        }
    }

    private void drawCloud(Canvas c, float cx, float cy, float scale, int color) {
        fill.setColor(color);
        fill.setAlpha(215);
        c.drawOval(rect(cx - 70 * scale, cy - 16 * scale, cx + 70 * scale, cy + 16 * scale), fill);
        c.drawCircle(cx - 30 * scale, cy - 12 * scale, 26 * scale, fill);
        c.drawCircle(cx + 14 * scale, cy - 18 * scale, 33 * scale, fill);
        c.drawCircle(cx + 46 * scale, cy - 6 * scale, 21 * scale, fill);
        fill.setAlpha(255);
    }

    private void drawScenery(Canvas c, float sw, float sh, Pal p, Game g) {
        float horizon = sh * 0.86f - g.camera.y * g.camera.zoom * 0.12f;

        // far mountains
        float scrollFar = g.camera.x * g.camera.zoom * 0.22f;
        float spacing = 520f;
        int first = (int) Math.floor(scrollFar / spacing) - 1;
        fill.setColor(p.far);
        for (int i = first; i <= first + (int) (sw / spacing) + 2; i++) {
            float cx = i * spacing + h1(i * 5) * 160f - scrollFar;
            float hgt = 180f + h1(i * 5 + 1) * 230f;
            float halfW = 300f + h1(i * 5 + 2) * 130f;
            path.reset();
            path.moveTo(cx - halfW, horizon + 40f);
            path.lineTo(cx, horizon - hgt);
            path.lineTo(cx + halfW, horizon + 40f);
            path.close();
            c.drawPath(path, fill);
            if (hgt > 320f) {   // snow cap
                fill.setColor(Color.argb(70, 255, 255, 255));
                path.reset();
                path.moveTo(cx - halfW * 0.30f, horizon - hgt * 0.70f);
                path.lineTo(cx, horizon - hgt);
                path.lineTo(cx + halfW * 0.30f, horizon - hgt * 0.70f);
                path.lineTo(cx + halfW * 0.16f, horizon - hgt * 0.76f);
                path.lineTo(cx, horizon - hgt * 0.70f);
                path.lineTo(cx - halfW * 0.16f, horizon - hgt * 0.78f);
                path.close();
                c.drawPath(path, fill);
                fill.setColor(p.far);
            }
        }

        // mid hills
        float scrollMid = g.camera.x * g.camera.zoom * 0.45f;
        float sp2 = 360f;
        int first2 = (int) Math.floor(scrollMid / sp2) - 1;
        for (int i = first2; i <= first2 + (int) (sw / sp2) + 2; i++) {
            float cx = i * sp2 + h1(i * 11 + 3) * 120f - scrollMid;
            float hgt = 110f + h1(i * 11 + 4) * 120f;
            float halfW = 250f + h1(i * 11 + 6) * 90f;
            fill.setColor(p.mid);
            c.drawOval(rect(cx - halfW, horizon + 40f - hgt, cx + halfW, horizon + 160f), fill);
            // little trees on the hill crest
            drawTree(c, cx - halfW * 0.35f, horizon + 40f - hgt * 0.72f, 0.9f, p.near);
            drawTree(c, cx + halfW * 0.28f, horizon + 40f - hgt * 0.86f, 0.7f, p.near);
        }

        // near bushes strip
        float scrollNear = g.camera.x * g.camera.zoom * 0.72f;
        float sp3 = 210f;
        int first3 = (int) Math.floor(scrollNear / sp3) - 1;
        for (int i = first3; i <= first3 + (int) (sw / sp3) + 2; i++) {
            float cx = i * sp3 + h1(i * 7 + 2) * 70f - scrollNear;
            float r = 34f + h1(i * 7 + 8) * 30f;
            fill.setColor(p.near);
            c.drawOval(rect(cx - r, horizon + 60f - r * 1.4f, cx + r, horizon + 90f), fill);
        }
    }

    private void drawTree(Canvas c, float x, float y, float scale, int color) {
        fill.setColor(0xFF6B4326);
        rect(x - 4 * scale, y, x + 4 * scale, y + 34 * scale);
        c.drawRect(rect, fill);
        fill.setColor(color);
        c.drawCircle(x, y - 6 * scale, 20 * scale, fill);
        c.drawCircle(x - 13 * scale, y + 3 * scale, 14 * scale, fill);
        c.drawCircle(x + 13 * scale, y + 2 * scale, 15 * scale, fill);
    }

    // ------------------------------------------------------------------
    // world: tiles
    // ------------------------------------------------------------------

    private void drawTiles(Canvas c, Game g, Pal p) {
        Level lvl = g.level;
        float t = g.worldTime;
        int tx0 = Math.max(0, (int) Math.floor(g.camera.x / Constants.TILE) - 1);
        int tx1 = Math.min(lvl.w - 1, (int) Math.floor((g.camera.x + g.camera.viewW) / Constants.TILE) + 1);
        int ty0 = Math.max(0, (int) Math.floor(g.camera.y / Constants.TILE) - 1);
        int ty1 = Math.min(lvl.h - 1, (int) Math.floor((g.camera.y + g.camera.viewH) / Constants.TILE) + 1);

        float T = Constants.TILE;
        for (int ty = ty0; ty <= ty1; ty++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                char ch = lvl.tileAt(tx, ty);
                if (ch == Level.EMPTY) {
                    continue;
                }
                float x = tx * T;
                float y = ty * T;
                switch (ch) {
                    case Level.GROUND: {
                        boolean topOpen = !lvl.isSolid(tx, ty - 1);
                        fill.setColor(p.dirt);
                        c.drawRect(x, y, x + T, y + T, fill);
                        // speckles
                        fill.setColor(p.dirtDark);
                        for (int i = 0; i < 3; i++) {
                            float sx = x + h1(tx * 31 + ty * 7 + i) * (T - 14f) + 7f;
                            float sy = y + h1(tx * 17 + ty * 13 + i * 3) * (T - 14f) + 7f;
                            c.drawCircle(sx, sy, 3f + h1(tx + ty + i) * 3f, fill);
                        }
                        if (topOpen) {
                            // grass cap
                            fill.setColor(p.grass);
                            c.drawRect(x, y, x + T, y + 20f, fill);
                            fill.setColor(p.grassDark);
                            c.drawRect(x, y + 16f, x + T, y + 22f, fill);
                            // blades
                            fill.setColor(p.grass);
                            for (int i = 0; i < 4; i++) {
                                float bx = x + 8f + i * 15f;
                                float bh = 7f + h1(tx * 5 + ty * 3 + i) * 8f;
                                path.reset();
                                path.moveTo(bx, y + 4f);
                                path.lineTo(bx + 4f, y + 4f - bh);
                                path.lineTo(bx + 8f, y + 4f);
                                path.close();
                                c.drawPath(path, fill);
                            }
                        }
                        // side shading
                        if (!lvl.isSolid(tx - 1, ty)) {
                            fill.setColor(0x33000000);
                            c.drawRect(x, y, x + 9f, y + T, fill);
                        }
                        if (!lvl.isSolid(tx + 1, ty)) {
                            fill.setColor(0x33000000);
                            c.drawRect(x + T - 9f, y, x + T, y + T, fill);
                        }
                        break;
                    }
                    case Level.STONE: {
                        fill.setColor(p.stone);
                        c.drawRect(x + 2f, y + 2f, x + T - 2f, y + T - 2f, fill);
                        fill.setColor(p.stoneDark);
                        stroke.setColor(p.stoneDark);
                        stroke.setStrokeWidth(3f);
                        c.drawRect(x + 2f, y + 2f, x + T - 2f, y + T - 2f, stroke);
                        // cracks
                        stroke.setStrokeWidth(2.5f);
                        stroke.setColor(0x55000000);
                        float ox = h1(tx * 3 + ty) * 20f;
                        c.drawLine(x + 14f + ox, y + 10f, x + 30f + ox, y + 34f, stroke);
                        c.drawLine(x + 30f + ox, y + 34f, x + 20f + ox, y + T - 10f, stroke);
                        fill.setColor(0x22FFFFFF);
                        c.drawRect(x + 4f, y + 4f, x + T - 4f, y + 11f, fill);
                        break;
                    }
                    case Level.SPIKE: {
                        fill.setColor(0xFFB9C2CC);
                        stroke.setColor(0xFF6E7885);
                        stroke.setStrokeWidth(3f);
                        int n = 3;
                        float wSeg = T / n;
                        for (int i = 0; i < n; i++) {
                            path.reset();
                            path.moveTo(x + i * wSeg + 3f, y + T);
                            path.lineTo(x + i * wSeg + wSeg / 2f, y + T * 0.42f);
                            path.lineTo(x + i * wSeg + wSeg - 3f, y + T);
                            path.close();
                            c.drawPath(path, fill);
                            c.drawPath(path, stroke);
                        }
                        fill.setColor(0x55FFFFFF);
                        for (int i = 0; i < n; i++) {
                            path.reset();
                            path.moveTo(x + i * wSeg + wSeg / 2f, y + T * 0.42f);
                            path.lineTo(x + i * wSeg + wSeg / 2f + 4f, y + T * 0.55f);
                            path.lineTo(x + i * wSeg + wSeg / 2f + 6f, y + T * 0.62f);
                            path.close();
                            c.drawPath(path, fill);
                        }
                        break;
                    }
                    case Level.LAVA: {
                        fill.setColor(0xFFD33A16);
                        c.drawRect(x, y, x + T, y + T, fill);
                        fill.setColor(0xFFFF8A1E);
                        c.drawRect(x, y + 8f, x + T, y + T, fill);
                        // animated surface
                        fill.setColor(0xFFFFD24A);
                        path.reset();
                        path.moveTo(x, y + 14f);
                        for (int i = 0; i <= 8; i++) {
                            float px = x + i * (T / 8f);
                            float py = y + 14f + (float) Math.sin(t * 3.2f + tx * 1.1f + i * 0.7f) * 6f;
                            path.lineTo(px, py);
                        }
                        path.lineTo(x + T, y + 26f);
                        path.lineTo(x, y + 26f);
                        path.close();
                        c.drawPath(path, fill);
                        // bubbles
                        fill.setColor(0x80FFE9A0);
                        float b = h1(tx * 9 + ty * 5);
                        float bt = (t * 0.6f + b) % 1f;
                        c.drawCircle(x + b * (T - 20f) + 10f, y + T - bt * T * 0.7f, 4f + b * 5f, fill);
                        break;
                    }
                    case Level.CHECKPOINT:
                    case Level.START:
                    case Level.GOAL:
                    case Level.COIN:
                    case Level.WALKER:
                    case Level.FLYER:
                    case Level.MOVER_H:
                    case Level.MOVER_V:
                        break;
                    default:
                        break;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // world: objects
    // ------------------------------------------------------------------

    private void drawPlatforms(Canvas c, Game g) {
        float T = Constants.TILE;
        for (MovingPlatform p : g.platforms) {
            if (!visible(g, p.x, p.y, p.w, p.h)) {
                continue;
            }
            fill.setColor(0x40000000);
            c.drawRoundRect(rect(p.x + 6f, p.y + 10f, p.x + p.w, p.y + p.h + 8f), 12f, 12f, fill);
            fill.setColor(0xFF9A6438);
            c.drawRoundRect(rect(p.x, p.y, p.x + p.w, p.y + p.h), 10f, 10f, fill);
            fill.setColor(0xFFB87F4A);
            c.drawRoundRect(rect(p.x + 3f, p.y + 3f, p.x + p.w - 3f, p.y + p.h * 0.55f), 8f, 8f, fill);
            stroke.setColor(0xFF6E431F);
            stroke.setStrokeWidth(3f);
            for (int i = 1; i < 3; i++) {
                float lx = p.x + i * (p.w / 3f);
                c.drawLine(lx, p.y + 4f, lx, p.y + p.h - 4f, stroke);
            }
            fill.setColor(0xFFD9D2C6);
            c.drawCircle(p.x + 14f, p.y + p.h / 2f, 5f, fill);
            c.drawCircle(p.x + p.w - 14f, p.y + p.h / 2f, 5f, fill);
            // subtle motion marker for vertical movers
            if (p.vertical) {
                fill.setColor(0x66FFFFFF);
                float bob = (float) Math.sin(g.worldTime * 4f) * 6f;
                path.reset();
                path.moveTo(p.x + p.w / 2f - 9f, p.y - 12f + bob);
                path.lineTo(p.x + p.w / 2f + 9f, p.y - 12f + bob);
                path.lineTo(p.x + p.w / 2f, p.y - 24f + bob);
                path.close();
                c.drawPath(path, fill);
            }
        }
    }

    private void drawCoins(Canvas c, Game g) {
        for (Coin coin : g.coins) {
            if (g.level.coinTaken(coin.index)) {
                continue;
            }
            if (!visible(g, coin.x - 30f, coin.y - 30f, 60f, 60f)) {
                continue;
            }
            float spin = (float) Math.cos(g.worldTime * 4.2f + coin.spinPhase);
            float sx = Math.max(0.18f, Math.abs(spin));
            float bob = (float) Math.sin(g.worldTime * 2.6f + coin.spinPhase) * 6f;
            float r = coin.size / 2f;

            fill.setColor(0x33000000);
            c.drawOval(rect(coin.x - r * 0.8f, coin.y + r + 6f + bob,
                    coin.x + r * 0.8f, coin.y + r + 12f + bob), fill);

            c.save();
            c.translate(coin.x, coin.y + bob);
            c.scale(sx, 1f);
            fill.setColor(0xFFFFC93C);
            c.drawCircle(0f, 0f, r, fill);
            fill.setColor(0xFFFFE58A);
            c.drawCircle(-r * 0.18f, -r * 0.18f, r * 0.62f, fill);
            stroke.setColor(0xFFD89A17);
            stroke.setStrokeWidth(3f);
            c.drawCircle(0f, 0f, r - 1.5f, stroke);
            fill.setColor(0xFFE8A81C);
            rect(-r * 0.16f, -r * 0.44f, r * 0.16f, r * 0.44f);
            c.drawRect(rect, fill);
            rect(-r * 0.36f, -r * 0.14f, r * 0.36f, r * 0.14f);
            c.drawRect(rect, fill);
            c.restore();

            // glint
            float gl = (float) Math.sin(g.worldTime * 3f + coin.spinPhase * 2f);
            if (gl > 0.7f) {
                fill.setColor(0xB3FFFFFF);
                c.drawCircle(coin.x + r * 0.55f, coin.y - r * 0.55f + bob, 5f, fill);
            }
        }
    }

    private void drawEnemies(Canvas c, Game g) {
        for (Enemy e : g.enemies) {
            if (e.dead && e.deadTimer <= 0f) {
                continue;
            }
            if (!visible(g, e.x - 40f, e.y - 40f, e.w + 80f, e.h + 80f)) {
                continue;
            }
            if (e.type == Enemy.Type.WALKER) {
                drawWalker(c, g, e);
            } else {
                drawFlyer(c, g, e);
            }
        }
    }

    private void drawWalker(Canvas c, Game g, Enemy e) {
        float squashY = e.dead ? 0.30f : 1f;
        float sx = e.facing;
        c.save();
        c.translate(e.x + e.w / 2f, e.y + e.h);
        float wide = Math.min(1.7f, 0.7f / Math.max(squashY, 0.3f) + 0.3f);
        c.scale(wide, squashY);

        // shadow
        fill.setColor(0x30000000);
        c.drawOval(rect(-e.w * 0.45f, -6f, e.w * 0.45f, 6f), fill);

        // feet
        if (!e.dead) {
            float step = (float) Math.sin(e.animTime * 12f) * 6f;
            fill.setColor(0xFF7E2C22);
            c.drawRoundRect(rect(-e.w * 0.34f, -8f + step, -e.w * 0.04f, 0f), 5f, 5f, fill);
            c.drawRoundRect(rect(e.w * 0.04f, -8f - step, e.w * 0.34f, 0f), 5f, 5f, fill);
        }

        // body
        fill.setColor(0xFFC74B3E);
        c.drawRoundRect(rect(-e.w / 2f, -e.h, e.w / 2f, -4f), 18f, 18f, fill);
        fill.setColor(0xFFE0665A);
        c.drawRoundRect(rect(-e.w / 2f + 4f, -e.h + 4f, e.w / 2f - 4f, -e.h * 0.55f), 14f, 14f, fill);
        fill.setColor(0xFF9E3227);
        c.drawRoundRect(rect(-e.w / 2f + 3f, -e.h * 0.42f, e.w / 2f - 3f, -5f), 14f, 14f, fill);

        // horns
        fill.setColor(0xFFF2E2C2);
        path.reset();
        path.moveTo(-e.w * 0.34f, -e.h + 6f);
        path.lineTo(-e.w * 0.46f, -e.h - 14f);
        path.lineTo(-e.w * 0.18f, -e.h + 2f);
        path.close();
        c.drawPath(path, fill);
        path.reset();
        path.moveTo(e.w * 0.34f, -e.h + 6f);
        path.lineTo(e.w * 0.46f, -e.h - 14f);
        path.lineTo(e.w * 0.18f, -e.h + 2f);
        path.close();
        c.drawPath(path, fill);

        // face (looking toward movement)
        if (e.dead) {
            stroke.setColor(0xFF3A1512);
            stroke.setStrokeWidth(4f);
            float ex = 0f;
            c.drawLine(ex - 14f, -e.h * 0.62f, ex - 4f, -e.h * 0.46f, stroke);
            c.drawLine(ex - 14f, -e.h * 0.46f, ex - 4f, -e.h * 0.62f, stroke);
            c.drawLine(ex + 4f, -e.h * 0.62f, ex + 14f, -e.h * 0.46f, stroke);
            c.drawLine(ex + 4f, -e.h * 0.46f, ex + 14f, -e.h * 0.62f, stroke);
        } else {
            float eye = 7f;
            float off = sx * 4f;
            fill.setColor(0xFFFFFFFF);
            c.drawOval(rect(-e.w * 0.26f + off, -e.h * 0.78f, -e.w * 0.26f + off + eye * 1.5f, -e.h * 0.78f + eye * 1.7f), fill);
            c.drawOval(rect(e.w * 0.04f + off, -e.h * 0.78f, e.w * 0.04f + off + eye * 1.5f, -e.h * 0.78f + eye * 1.7f), fill);
            fill.setColor(0xFF2A1210);
            c.drawCircle(-e.w * 0.20f + off + eye * 0.3f, -e.h * 0.78f + eye * 0.9f, 3.4f, fill);
            c.drawCircle(e.w * 0.10f + off + eye * 0.3f, -e.h * 0.78f + eye * 0.9f, 3.4f, fill);
            // angry brows
            stroke.setColor(0xFF3A1512);
            stroke.setStrokeWidth(4.5f);
            c.drawLine(-e.w * 0.30f + off, -e.h * 0.86f, -e.w * 0.06f + off, -e.h * 0.74f, stroke);
            c.drawLine(e.w * 0.02f + off, -e.h * 0.74f, e.w * 0.24f + off, -e.h * 0.86f, stroke);
            // frown
            stroke.setStrokeWidth(4f);
            float mouth = -e.h * 0.30f;
            path.reset();
            path.moveTo(-e.w * 0.14f, mouth + 5f);
            path.quadTo(0f, mouth - 5f, e.w * 0.14f, mouth + 5f);
            c.drawPath(path, stroke);
        }
        c.restore();
    }

    private void drawFlyer(Canvas c, Game g, Enemy e) {
        float flap = (float) Math.sin(g.worldTime * 14f + e.animTime * 2f);
        c.save();
        c.translate(e.x + e.w / 2f, e.y + e.h / 2f);

        // wings
        fill.setColor(0xFF7B4FBF);
        float wy = flap * 14f;
        path.reset();
        path.moveTo(-6f, 0f);
        path.lineTo(-e.w * 0.95f, -16f - wy);
        path.lineTo(-e.w * 0.7f, 10f - wy);
        path.close();
        c.drawPath(path, fill);
        path.reset();
        path.moveTo(6f, 0f);
        path.lineTo(e.w * 0.95f, -16f - wy);
        path.lineTo(e.w * 0.7f, 10f - wy);
        path.close();
        c.drawPath(path, fill);

        if (e.dead) {
            fill.setColor(0xFF6A43A6);
            c.drawOval(rect(-e.w / 2f, -e.h / 2f, e.w / 2f, e.h / 2f), fill);
            stroke.setColor(0xFF2C1A4A);
            stroke.setStrokeWidth(4f);
            c.drawLine(-14f, -6f, -5f, 4f, stroke);
            c.drawLine(-14f, 4f, -5f, -6f, stroke);
            c.drawLine(5f, -6f, 14f, 4f, stroke);
            c.drawLine(5f, 4f, 14f, -6f, stroke);
            c.restore();
            return;
        }

        // body
        fill.setColor(0xFF8B5BD6);
        c.drawOval(rect(-e.w / 2f, -e.h / 2f, e.w / 2f, e.h / 2f), fill);
        fill.setColor(0xFFA87BEA);
        c.drawOval(rect(-e.w / 2f + 4f, -e.h / 2f + 3f, e.w / 2f - 6f, 0f), fill);
        // ears
        fill.setColor(0xFF6A43A6);
        path.reset();
        path.moveTo(-e.w * 0.30f, -e.h * 0.42f);
        path.lineTo(-e.w * 0.42f, -e.h * 0.78f);
        path.lineTo(-e.w * 0.08f, -e.h * 0.5f);
        path.close();
        c.drawPath(path, fill);
        path.reset();
        path.moveTo(e.w * 0.30f, -e.h * 0.42f);
        path.lineTo(e.w * 0.42f, -e.h * 0.78f);
        path.lineTo(e.w * 0.08f, -e.h * 0.5f);
        path.close();
        c.drawPath(path, fill);

        // eyes
        float off = e.facing * 3f;
        fill.setColor(0xFFFFE27A);
        c.drawCircle(-e.w * 0.16f + off, -2f, 7.5f, fill);
        c.drawCircle(e.w * 0.16f + off, -2f, 7.5f, fill);
        fill.setColor(0xFF241238);
        c.drawCircle(-e.w * 0.16f + off + e.facing * 2f, -2f, 4f, fill);
        c.drawCircle(e.w * 0.16f + off + e.facing * 2f, -2f, 4f, fill);
        // tiny fangs
        fill.setColor(0xFFFFFFFF);
        path.reset();
        path.moveTo(-7f, e.h * 0.18f);
        path.lineTo(-3f, e.h * 0.36f);
        path.lineTo(-1f, e.h * 0.18f);
        path.close();
        c.drawPath(path, fill);
        path.reset();
        path.moveTo(3f, e.h * 0.18f);
        path.lineTo(7f, e.h * 0.36f);
        path.lineTo(9f, e.h * 0.18f);
        path.close();
        c.drawPath(path, fill);
        c.restore();
    }

    // ------------------------------------------------------------------
    // world: player, goal, checkpoints, particles
    // ------------------------------------------------------------------

    private void drawPlayer(Canvas c, Game g) {
        Player p = g.player;
        float w = p.w;
        float h = p.h;
        float sx = p.facingRight ? 1f : -1f;

        // ground shadow
        if (!p.dead) {
            fill.setColor(0x33000000);
            c.drawOval(rect(p.x + w * 0.14f, p.y + h - 5f, p.x + w * 0.86f, p.y + h + 8f), fill);
        }

        c.save();
        c.translate(p.x + w / 2f, p.y + h);
        c.scale(sx, 1f);
        float sy = p.squash;
        float sxw = 1f / Math.max(sy, 0.5f) * 0.6f + 0.4f;
        c.scale(sxw, sy);
        if (p.dead) {
            c.rotate(-90f * Math.min(1f, (Constants.RESPAWN_DELAY - p.deadTimer) * 2.2f));
        }

        float run = p.onGround ? p.runCycle : 0f;
        boolean moving = Math.abs(p.vx) > 40f && p.onGround;

        // legs
        fill.setColor(0xFF2A3F7A);
        if (!p.onGround) {
            c.drawRoundRect(rect(-w * 0.36f, -16f, -w * 0.02f, -2f), 5f, 5f, fill);
            c.drawRoundRect(rect(w * 0.04f, -20f, w * 0.34f, -8f), 5f, 5f, fill);
        } else if (moving) {
            float swing = (float) Math.sin(run) * 9f;
            c.drawRoundRect(rect(-w * 0.34f + swing, -17f, -w * 0.02f + swing, -1f), 5f, 5f, fill);
            c.drawRoundRect(rect(w * 0.02f - swing, -17f, w * 0.34f - swing, -1f), 5f, 5f, fill);
        } else {
            c.drawRoundRect(rect(-w * 0.34f, -17f, -w * 0.03f, -1f), 5f, 5f, fill);
            c.drawRoundRect(rect(w * 0.03f, -17f, w * 0.34f, -1f), 5f, 5f, fill);
        }
        // shoes
        fill.setColor(0xFF1B243F);
        if (!p.onGround) {
            c.drawRoundRect(rect(-w * 0.40f, -7f, w * 0.0f, -1f), 4f, 4f, fill);
            c.drawRoundRect(rect(w * 0.02f, -11f, w * 0.40f, -5f), 4f, 4f, fill);
        } else if (moving) {
            float swing = (float) Math.sin(run) * 9f;
            c.drawRoundRect(rect(-w * 0.40f + swing, -7f, w * 0.0f + swing, -1f), 4f, 4f, fill);
            c.drawRoundRect(rect(w * 0.0f - swing, -7f, w * 0.40f - swing, -1f), 4f, 4f, fill);
        } else {
            c.drawRoundRect(rect(-w * 0.40f, -7f, -w * 0.02f, -1f), 4f, 4f, fill);
            c.drawRoundRect(rect(w * 0.02f, -7f, w * 0.40f, -1f), 4f, 4f, fill);
        }

        // scarf tail (behind body, flutters with speed)
        float flutter = (float) Math.sin(g.worldTime * 16f) * 5f;
        float tail = 6f + Math.min(20f, Math.abs(p.vx) * 0.04f);
        fill.setColor(0xFFE14A4A);
        path.reset();
        path.moveTo(-w * 0.10f, -h * 0.60f);
        path.lineTo(-w * 0.10f - tail - flutter, -h * 0.50f);
        path.lineTo(-w * 0.10f, -h * 0.42f);
        path.close();
        c.drawPath(path, fill);

        // body
        fill.setColor(0xFF3F6BE0);
        c.drawRoundRect(rect(-w * 0.44f, -h * 0.66f, w * 0.44f, -h * 0.20f), 12f, 12f, fill);
        fill.setColor(0xFF5A85F5);
        c.drawRoundRect(rect(-w * 0.40f, -h * 0.63f, w * 0.40f, -h * 0.44f), 10f, 10f, fill);
        // belt
        fill.setColor(0xFF243458);
        c.drawRoundRect(rect(-w * 0.44f, -h * 0.30f, w * 0.44f, -h * 0.22f), 5f, 5f, fill);
        fill.setColor(0xFFFFCB3D);
        c.drawRect(rect(-w * 0.08f, -h * 0.30f, w * 0.08f, -h * 0.22f), fill);

        // arms
        float armSwing = moving ? (float) Math.sin(run + Math.PI) * 8f : (p.onGround ? 0f : -6f);
        fill.setColor(0xFF3560D2);
        c.drawRoundRect(rect(-w * 0.56f, -h * 0.62f + armSwing, -w * 0.38f, -h * 0.34f + armSwing), 7f, 7f, fill);
        c.drawRoundRect(rect(w * 0.38f, -h * 0.62f - armSwing, w * 0.56f, -h * 0.34f - armSwing), 7f, 7f, fill);
        // hands
        fill.setColor(0xFFF0BE92);
        c.drawCircle(-w * 0.47f, -h * 0.32f + armSwing, 6.5f, fill);
        c.drawCircle(w * 0.47f, -h * 0.32f - armSwing, 6.5f, fill);

        // head
        fill.setColor(0xFFF3C79A);
        c.drawRoundRect(rect(-w * 0.46f, -h * 1.02f, w * 0.46f, -h * 0.60f), 15f, 15f, fill);
        // hair
        fill.setColor(0xFF4A2E1D);
        path.reset();
        path.moveTo(-w * 0.46f, -h * 0.84f);
        path.lineTo(-w * 0.46f, -h * 1.00f);
        path.quadTo(0f, -h * 1.12f, w * 0.46f, -h * 1.00f);
        path.lineTo(w * 0.46f, -h * 0.88f);
        path.lineTo(w * 0.28f, -h * 0.94f);
        path.lineTo(w * 0.10f, -h * 0.86f);
        path.lineTo(-w * 0.08f, -h * 0.95f);
        path.lineTo(-w * 0.26f, -h * 0.87f);
        path.close();
        c.drawPath(path, fill);

        if (p.dead) {
            // X eyes
            stroke.setColor(0xFF3A2418);
            stroke.setStrokeWidth(3.6f);
            float ey = -h * 0.78f;
            c.drawLine(-16f, ey - 6f, -6f, ey + 6f, stroke);
            c.drawLine(-16f, ey + 6f, -6f, ey - 6f, stroke);
            c.drawLine(4f, ey - 6f, 14f, ey + 6f, stroke);
            c.drawLine(4f, ey + 6f, 14f, ey - 6f, stroke);
        } else {
            // eye
            fill.setColor(0xFFFFFFFF);
            c.drawOval(rect(2f, -h * 0.85f, 20f, -h * 0.71f), fill);
            fill.setColor(0xFF2A2118);
            float look = Math.max(-3f, Math.min(3f, p.vx * 0.012f));
            c.drawCircle(13f + look, -h * 0.78f, 5.2f, fill);
            fill.setColor(0xFFFFFFFF);
            c.drawCircle(14.5f + look, -h * 0.80f, 1.8f, fill);
            // brow
            stroke.setColor(0xFF4A2E1D);
            stroke.setStrokeWidth(3.4f);
            c.drawLine(1f, -h * 0.89f, 20f, -h * 0.92f, stroke);
            // smile
            stroke.setColor(0xFFB3754B);
            stroke.setStrokeWidth(3f);
            path.reset();
            path.moveTo(6f, -h * 0.68f);
            path.quadTo(13f, -h * 0.63f, 20f, -h * 0.69f);
            c.drawPath(path, stroke);
        }
        c.restore();

        // invulnerable/landing sparkle
        if (p.landFlash > 0.5f) {
            fill.setColor(Color.argb((int) (120 * (p.landFlash - 0.5f) * 2f), 255, 255, 255));
            c.drawOval(rect(p.x - 6f, p.y - 6f, p.x + p.w + 6f, p.y + p.h + 6f), fill);
        }
    }

    private void drawGoal(Canvas c, Game g) {
        Level lvl = g.level;
        float T = Constants.TILE;
        float x = lvl.goalX + T / 2f;
        float baseY = (lvl.goalY + 1f) * T;   // ground line under the flag tile
        float top = lvl.goalY * T - T * 1.2f;
        if (!visible(g, x - 60f, top - 40f, 120f, baseY - top + 60f)) {
            return;
        }
        // base
        fill.setColor(0xFF7B8494);
        c.drawRoundRect(rect(x - 26f, baseY - 14f, x + 26f, baseY), 6f, 6f, fill);
        // pole
        stroke.setColor(0xFFD8DEE8);
        stroke.setStrokeWidth(9f);
        c.drawLine(x, baseY - 10f, x, top, stroke);
        stroke.setColor(0xFF9AA3B2);
        stroke.setStrokeWidth(3f);
        c.drawLine(x - 3f, baseY - 10f, x - 3f, top, stroke);
        // ball on top
        fill.setColor(0xFFFFCB3D);
        c.drawCircle(x, top - 4f, 11f, fill);
        fill.setColor(0xFFFFE58A);
        c.drawCircle(x - 3f, top - 7f, 5f, fill);

        // waving flag
        float wave = g.worldTime * 3.4f;
        float fw = T * 1.15f;
        float fh = T * 0.78f;
        boolean cleared = g.state == Game.State.LEVEL_CLEARED
                || g.state == Game.State.WIN
                || (g.levelIndex == Levels.TEXT.length - 1 && g.state == Game.State.LEVEL_CLEARED);
        fill.setColor(cleared ? 0xFFFFD34D : 0xFF3F6BE0);
        path.reset();
        path.moveTo(x + 4f, top + 6f);
        for (int i = 0; i <= 8; i++) {
            float t = i / 8f;
            float px = x + 4f + fw * t;
            float py = top + 6f + fh * t * 0.55f + (float) Math.sin(wave - t * 3f) * 7f;
            path.lineTo(px, py);
        }
        for (int i = 8; i >= 0; i--) {
            float t = i / 8f;
            float px = x + 4f + fw * t;
            float py = top + 6f + fh * 0.62f + fh * t * 0.55f + (float) Math.sin(wave - t * 3f) * 7f;
            path.lineTo(px, py);
        }
        path.close();
        c.drawPath(path, fill);
        // star emblem
        fill.setColor(cleared ? 0xFFB8791A : 0xFFFFD34D);
        float scx = x + 4f + fw * 0.5f;
        float scy = top + 6f + fh * 0.55f;
        drawStar(c, scx, scy + (float) Math.sin(wave - 1.5f) * 7f, 14f, 7f);

        // coin collector: a padlock hangs on the pole while the goal is shut
        if (g.goalLocked()) {
            float lx = x + 4f + fw * 0.5f;
            float ly = top + fh + 24f;
            float ls = T * 0.17f;
            stroke.setColor(0xFFB9C2CF);
            stroke.setStrokeWidth(ls * 0.42f);
            c.drawCircle(lx, ly - ls * 0.55f, ls * 0.52f, stroke);
            fill.setColor(0xFF3C424B);
            c.drawRoundRect(rect(lx - ls * 0.7f, ly - ls * 0.5f, lx + ls * 0.7f, ly + ls * 0.9f),
                    ls * 0.28f, ls * 0.28f, fill);
            fill.setColor(0xFFFFCB3D);
            c.drawCircle(lx, ly + ls * 0.08f, ls * 0.2f, fill);
        }
    }

    private void drawCheckpoints(Canvas c, Game g) {
        float T = Constants.TILE;
        for (float[] cp : g.level.checkpointSpawns) {
            float x = cp[0] + T / 2f;
            float baseY = (cp[1] + 1f) * T;
            float top = cp[1] * T - T * 0.6f;
            if (!visible(g, x - 50f, top - 30f, 100f, baseY - top + 40f)) {
                continue;
            }
            boolean active = g.checkpointActive;
            fill.setColor(0xFF6E7480);
            c.drawRoundRect(rect(x - 18f, baseY - 10f, x + 18f, baseY), 5f, 5f, fill);
            stroke.setColor(active ? 0xFF57E0C4 : 0xFF9AA3B2);
            stroke.setStrokeWidth(7f);
            c.drawLine(x, baseY - 6f, x, top, stroke);
            fill.setColor(active ? 0xFF3ECFB2 : 0xFF6E7480);
            float wave = g.worldTime * 4f;
            path.reset();
            path.moveTo(x, top);
            for (int i = 0; i <= 6; i++) {
                float t = i / 6f;
                path.lineTo(x + T * 0.62f * t,
                        top + T * 0.10f + (float) Math.sin(wave - t * 3f) * 5f);
            }
            path.lineTo(x, top + T * 0.52f);
            path.close();
            c.drawPath(path, fill);
            if (active) {
                fill.setColor(0x44FFFFFF);
                float pulse = 0.5f + 0.5f * (float) Math.sin(g.worldTime * 4f);
                c.drawCircle(x, top + T * 0.26f, 16f + pulse * 8f, fill);
            }
        }
    }

    private void drawParticles(Canvas c, Game g) {
        Particles ps = g.particles;
        for (int i = 0; i < ps.size(); i++) {
            float life = ps.life(i);
            float max = ps.maxLife(i);
            if (life <= 0f) {
                continue;
            }
            float a = Math.max(0f, Math.min(1f, life / max));
            int argb = ps.color(i);
            int alpha = (int) ((argb >>> 24) * a);
            fill.setColor(Color.argb(alpha, (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF));
            float s = ps.size(i) * (0.5f + 0.5f * a);
            c.drawCircle(ps.x(i), ps.y(i), s, fill);
        }
        // Do not leak a faded alpha into the rest of the frame (or the next
        // frame's sky, which would render translucent).
        if (ps.size() > 0) {
            fill.setAlpha(255);
        }
    }

    private void drawStar(Canvas c, float cx, float cy, float outer, float inner) {
        path.reset();
        for (int i = 0; i < 10; i++) {
            float r = (i % 2 == 0) ? outer : inner;
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float px = cx + (float) Math.cos(a) * r;
            float py = cy + (float) Math.sin(a) * r;
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        path.close();
        c.drawPath(path, fill);
    }

    // ------------------------------------------------------------------
    // HUD
    // ------------------------------------------------------------------

    private void drawHud(Canvas c, Game g, float sw, float sh) {
        if (g.state == Game.State.MENU) {
            return;
        }
        float pad = 18f;
        float scale = Math.min(sw, sh) / 480f;
        float boxH = 78f * scale;
        float boxW = 200f * scale;      // score panel
        float coinW = 140f * scale;     // coin chip

        // score panel: dark backing plus a light border so it reads on any
        // backdrop; values are large, white, hard-shadowed.
        fill.setColor(0x99000000);
        c.drawRoundRect(rect(pad, pad, pad + boxW, pad + boxH), 16f, 16f, fill);
        stroke.setColor(0x88FFFFFF);
        stroke.setStrokeWidth(2.5f * scale);
        c.drawRoundRect(rect(pad, pad, pad + boxW, pad + boxH), 16f, 16f, stroke);
        text.setTextSize(20f * scale);
        text.setColor(0xFFCFE0FF);
        text.setTextAlign(Paint.Align.LEFT);
        text.setShadowLayer(3f, 0f, 2f, 0xCC000000);
        c.drawText("SCORE", pad + 16f * scale, pad + 28f * scale, text);
        text.setTextSize(38f * scale);
        text.setColor(0xFFFFFFFF);
        text.setShadowLayer(5f, 0f, 3f, 0xCC000000);
        c.drawText(String.format(java.util.Locale.US, "%06d", g.score),
                pad + 16f * scale, pad + 66f * scale, text);

        // coin chip
        float cx0 = pad + boxW + 12f * scale;
        fill.setColor(0x99000000);
        c.drawRoundRect(rect(cx0, pad, cx0 + coinW, pad + boxH), 16f, 16f, fill);
        c.drawRoundRect(rect(cx0, pad, cx0 + coinW, pad + boxH), 16f, 16f, stroke);
        float icX = cx0 + 34f * scale;
        float icY = pad + boxH / 2f;
        fill.setColor(0xFFFFC93C);
        c.drawCircle(icX, icY, 20f * scale, fill);
        fill.setColor(0xFFFFE58A);
        c.drawCircle(icX - 5f * scale, icY - 5f * scale, 10f * scale, fill);
        text.setTextSize(34f * scale);
        text.setColor(0xFFFFE08A);
        text.setShadowLayer(5f, 0f, 3f, 0xCC000000);
        c.drawText(g.coinsTaken + "/" + g.level.coinCount(),
                cx0 + 62f * scale, pad + boxH / 2f + 13f * scale, text);

        // lives (hearts) under the score panel
        float hy = pad + boxH + 14f * scale;
        for (int i = 0; i < 3; i++) {
            drawHeart(c, pad + 26f + i * 46f * scale, hy + 22f * scale, 20f * scale,
                    i < g.lives ? 0xFFE4453A : 0x55FFFFFF);
        }

        // level + time (top centre), clamped so it can never collide with the
        // left cluster or the pause/mute buttons.
        float leftEnd = cx0 + coinW;
        float muteLeft = g.controls.muteX - g.controls.muteHitR;
        float cw = 300f * scale;
        float cx = Math.max(sw / 2f, leftEnd + cw / 2f + 14f * scale);
        cx = Math.min(cx, muteLeft - cw / 2f - 14f * scale);
        fill.setColor(0x99000000);
        c.drawRoundRect(rect(cx - cw / 2f, pad, cx + cw / 2f, pad + boxH), 16f, 16f, fill);
        c.drawRoundRect(rect(cx - cw / 2f, pad, cx + cw / 2f, pad + boxH), 16f, 16f, stroke);
        text.setTextSize(22f * scale);
        text.setColor(0xFFFFE08A);
        text.setTextAlign(Paint.Align.CENTER);
        text.setShadowLayer(3f, 0f, 2f, 0xCC000000);
        c.drawText(g.levelLabel(), cx, pad + 28f * scale, text);
        text.setTextSize(34f * scale);
        text.setColor(0xFFFFFFFF);
        text.setShadowLayer(5f, 0f, 3f, 0xCC000000);
        c.drawText(g.timeLabel(), cx, pad + 64f * scale, text);

        // coin-collector status chip under the level panel
        if (g.mode == Game.Mode.COIN_COLLECTOR && g.level.coinCount() > 0) {
            boolean locked = g.goalLocked();
            String s = locked
                    ? "GOAL LOCKED — COLLECT " + g.coinGoalRequired() + " COINS"
                    : "GOAL OPEN!";
            text.setTextSize(24f * scale);
            text.setTextAlign(Paint.Align.CENTER);
            text.setShadowLayer(4f, 0f, 2f, 0xCC000000);
            text.setColor(locked ? 0xFFFF9A3D : 0xFF9BE89B);
            float chipY = pad + boxH + 34f * scale;
            fill.setColor(0x99000000);
            float chipW = text.measureText(s) + 30f * scale;
            c.drawRoundRect(rect(cx - chipW / 2f, chipY - 22f * scale,
                    cx + chipW / 2f, chipY + 10f * scale), 12f, 12f, fill);
            c.drawText(s, cx, chipY, text);
        }

        // pause + mute buttons: dark disc plus light ring, drawn at the same
        // radii used for hit testing.
        Controls ctl = g.controls;
        fill.setColor(0x99000000);
        c.drawCircle(ctl.pauseX, ctl.pauseY, ctl.pauseHitR, fill);
        c.drawCircle(ctl.muteX, ctl.muteY, ctl.muteHitR, fill);
        stroke.setColor(0x88FFFFFF);
        stroke.setStrokeWidth(2.5f * scale);
        c.drawCircle(ctl.pauseX, ctl.pauseY, ctl.pauseHitR, stroke);
        c.drawCircle(ctl.muteX, ctl.muteY, ctl.muteHitR, stroke);
        // pause glyph
        fill.setColor(0xFFFFFFFF);
        float bw = ctl.pauseHitR * 0.26f;
        float bh = ctl.pauseHitR * 0.86f;
        c.drawRoundRect(rect(ctl.pauseX - bw * 1.6f, ctl.pauseY - bh / 2f,
                ctl.pauseX - bw * 0.3f, ctl.pauseY + bh / 2f), 4f, 4f, fill);
        c.drawRoundRect(rect(ctl.pauseX + bw * 0.3f, ctl.pauseY - bh / 2f,
                ctl.pauseX + bw * 1.6f, ctl.pauseY + bh / 2f), 4f, 4f, fill);
        // speaker glyph
        drawSpeaker(c, ctl.muteX, ctl.muteY, ctl.muteHitR, g.sound.isEnabled());
    }

    private void drawSpeaker(Canvas c, float cx, float cy, float r, boolean on) {
        fill.setColor(0xFFFFFFFF);
        path.reset();
        path.moveTo(cx - r * 0.55f, cy - r * 0.28f);
        path.lineTo(cx - r * 0.20f, cy - r * 0.28f);
        path.lineTo(cx + r * 0.16f, cy - r * 0.58f);
        path.lineTo(cx + r * 0.16f, cy + r * 0.58f);
        path.lineTo(cx - r * 0.20f, cy + r * 0.28f);
        path.lineTo(cx - r * 0.55f, cy + r * 0.28f);
        path.close();
        c.drawPath(path, fill);
        stroke.setColor(0xFFFFFFFF);
        stroke.setStrokeWidth(r * 0.16f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        if (on) {
            c.drawArc(rect(cx - r * 0.28f, cy - r * 0.55f, cx + r * 0.55f, cy + r * 0.55f),
                    -52f, 104f, false, stroke);
            c.drawArc(rect(cx - r * 0.05f, cy - r * 0.92f, cx + r * 0.92f, cy + r * 0.92f),
                    -50f, 100f, false, stroke);
        } else {
            stroke.setColor(0xFFFF6B6B);
            c.drawLine(cx - r * 0.75f, cy - r * 0.75f, cx + r * 0.75f, cy + r * 0.75f, stroke);
            c.drawLine(cx - r * 0.75f, cy + r * 0.75f, cx + r * 0.75f, cy - r * 0.75f, stroke);
        }
        stroke.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawHeart(Canvas c, float cx, float cy, float size, int color) {
        fill.setColor(color);
        path.reset();
        path.moveTo(cx, cy + size * 0.85f);
        path.cubicTo(cx - size * 1.35f, cy - size * 0.15f,
                cx - size * 0.75f, cy - size * 1.05f,
                cx, cy - size * 0.35f);
        path.cubicTo(cx + size * 0.75f, cy - size * 1.05f,
                cx + size * 1.35f, cy - size * 0.15f,
                cx, cy + size * 0.85f);
        path.close();
        c.drawPath(path, fill);
        fill.setColor(0x55FFFFFF);
        path.reset();
        path.moveTo(cx - size * 0.45f, cy - size * 0.45f);
        path.cubicTo(cx - size * 0.75f, cy - size * 0.1f,
                cx - size * 0.5f, cy + size * 0.25f,
                cx - size * 0.15f, cy + size * 0.45f);
        path.cubicTo(cx - size * 0.4f, cy + size * 0.1f,
                cx - size * 0.35f, cy - size * 0.25f,
                cx - size * 0.45f, cy - size * 0.45f);
        path.close();
        c.drawPath(path, fill);
    }

    // ------------------------------------------------------------------
    // touch controls
    // ------------------------------------------------------------------

    private void drawControls(Canvas c, Game g) {
        Controls ctl = g.controls;
        boolean paused = g.state == Game.State.PAUSED;

        // Draw with the touch radii, never with a different size.
        float ar = ctl.leftHitR;
        float jr = ctl.jumpHitR;

        fill.setColor(paused ? 0x22000000 : 0x44000000);
        c.drawCircle(ctl.leftX, ctl.leftY, ar, fill);
        c.drawCircle(ctl.rightX, ctl.rightY, ctl.rightHitR, fill);
        c.drawCircle(ctl.jumpX, ctl.jumpY, jr, fill);

        stroke.setColor(paused ? 0x33FFFFFF : 0x88FFFFFF);
        stroke.setStrokeWidth(3f);
        c.drawCircle(ctl.leftX, ctl.leftY, ar, stroke);
        c.drawCircle(ctl.rightX, ctl.rightY, ctl.rightHitR, stroke);
        c.drawCircle(ctl.jumpX, ctl.jumpY, jr, stroke);

        int hi = 0xFFFFFFFF;
        fill.setColor(hi);
        // arrows
        path.reset();
        path.moveTo(ctl.leftX + ar * 0.30f, ctl.leftY - ar * 0.42f);
        path.lineTo(ctl.leftX - ar * 0.34f, ctl.leftY);
        path.lineTo(ctl.leftX + ar * 0.30f, ctl.leftY + ar * 0.42f);
        path.close();
        c.drawPath(path, fill);
        path.reset();
        path.moveTo(ctl.rightX - ctl.rightHitR * 0.30f, ctl.rightY - ctl.rightHitR * 0.42f);
        path.lineTo(ctl.rightX + ctl.rightHitR * 0.34f, ctl.rightY);
        path.lineTo(ctl.rightX - ctl.rightHitR * 0.30f, ctl.rightY + ctl.rightHitR * 0.42f);
        path.close();
        c.drawPath(path, fill);
        // jump arrow
        path.reset();
        path.moveTo(ctl.jumpX, ctl.jumpY - jr * 0.46f);
        path.lineTo(ctl.jumpX + jr * 0.40f, ctl.jumpY + jr * 0.04f);
        path.lineTo(ctl.jumpX + jr * 0.15f, ctl.jumpY + jr * 0.04f);
        path.lineTo(ctl.jumpX + jr * 0.15f, ctl.jumpY + jr * 0.44f);
        path.lineTo(ctl.jumpX - jr * 0.15f, ctl.jumpY + jr * 0.44f);
        path.lineTo(ctl.jumpX - jr * 0.15f, ctl.jumpY + jr * 0.04f);
        path.lineTo(ctl.jumpX - jr * 0.40f, ctl.jumpY + jr * 0.04f);
        path.close();
        c.drawPath(path, fill);

        if (g.input.left || g.input.right) {
            fill.setColor(0x33FFFFFF);
            c.drawCircle(g.input.left ? ctl.leftX : ctl.rightX,
                    g.input.left ? ctl.leftY : ctl.rightY, ar * 0.82f, fill);
        }
        if (g.input.jumpHeld) {
            fill.setColor(0x33FFFFFF);
            c.drawCircle(ctl.jumpX, ctl.jumpY, jr * 0.82f, fill);
        }
    }

    // ------------------------------------------------------------------
    // overlays
    // ------------------------------------------------------------------

    private void drawOverlay(Canvas c, Game g, float sw, float sh) {
        float scale = Math.min(sw, sh) / 480f;
        switch (g.state) {
            case MENU:
                drawMenu(c, g, sw, sh, scale);
                break;
            case PAUSED:
                scrim(c, sw, sh, 0.55f);
                text.setTextAlign(Paint.Align.CENTER);
                text.setTextSize(64f * scale);
                text.setColor(0xFFFFFFFF);
                text.setShadowLayer(8f, 0f, 5f, 0xAA000000);
                c.drawText("PAUSED", sw / 2f, sh * 0.5f - 3.4f * 74f * scale * 0.5f, text);
                text.setShadowLayer(6f, 0f, 4f, 0x99000000);
                for (Game.UiButton b : g.pauseButtons) {
                    fill.setColor(0xFF2F4B8C);
                    c.drawRoundRect(rect(b.x, b.y, b.x + b.w, b.y + b.h), 18f, 18f, fill);
                    fill.setColor(0x33FFFFFF);
                    c.drawRoundRect(rect(b.x, b.y, b.x + b.w, b.y + b.h * 0.5f), 18f, 18f, fill);
                    stroke.setColor(0xAAFFFFFF);
                    stroke.setStrokeWidth(3f);
                    c.drawRoundRect(rect(b.x, b.y, b.x + b.w, b.y + b.h), 18f, 18f, stroke);
                    text.setTextSize(Math.max(26f * scale, Math.min(36f * scale, b.h * 0.52f)));
                    text.setColor(0xFFFFFFFF);
                    text.setShadowLayer(4f, 0f, 3f, 0x99000000);
                    c.drawText(b.label, b.x + b.w / 2f, b.y + b.h * 0.66f, text);
                    text.setShadowLayer(6f, 0f, 4f, 0x99000000);
                }
                text.setTextSize(22f * scale);
                text.setColor(0xCCFFFFFF);
                text.setShadowLayer(4f, 0f, 3f, 0x99000000);
                c.drawText("R restart  •  back to resume  •  M sound",
                        sw / 2f, sh * 0.5f + 3.4f * 74f * scale * 0.5f + 34f * scale, text);
                break;
            case LEVEL_CLEARED:
                scrim(c, sw, sh, 0.62f);
                drawPanel(c, sw / 2f, sh / 2f, Math.min(sw * 0.7f, 720f * scale), 400f * scale);
                text.setTextAlign(Paint.Align.CENTER);
                text.setTextSize(54f * scale);
                text.setColor(0xFFFFD34D);
                text.setShadowLayer(8f, 0f, 5f, 0xAA000000);
                c.drawText("LEVEL CLEAR!", sw / 2f, sh / 2f - 130f * scale, text);
                text.setTextSize(30f * scale);
                text.setColor(0xFFFFFFFF);
                c.drawText("Time  " + g.timeLabel(), sw / 2f, sh / 2f - 66f * scale, text);
                text.setColor(0xFFBFD4FF);
                c.drawText("Coins  " + g.coinsTaken + "/" + g.level.coinCount(),
                        sw / 2f, sh / 2f - 20f * scale, text);
                text.setColor(0xFF9BE89B);
                c.drawText("Time bonus  +" + g.bonusTime, sw / 2f, sh / 2f + 26f * scale, text);
                if (g.bonusCoins > 0) {
                    text.setColor(0xFFFFE08A);
                    c.drawText("All coins  +" + g.bonusCoins, sw / 2f, sh / 2f + 66f * scale, text);
                } else {
                    text.setColor(0xFFBFD4FF);
                    c.drawText("Score  " + g.score, sw / 2f, sh / 2f + 66f * scale, text);
                }
                text.setTextSize(38f * scale);
                text.setColor(0xFFFFFFFF);
                c.drawText("TOTAL  " + g.score, sw / 2f, sh / 2f + 118f * scale, text);
                blinkText(c, "TAP TO CONTINUE", sw / 2f, sh / 2f + 175f * scale, 26f * scale, g);
                break;
            case GAME_OVER:
                scrim(c, sw, sh, 0.68f);
                text.setTextAlign(Paint.Align.CENTER);
                text.setTextSize(78f * scale);
                text.setColor(0xFFE4453A);
                text.setShadowLayer(10f, 0f, 6f, 0xAA000000);
                c.drawText("GAME OVER", sw / 2f, sh * 0.4f, text);
                text.setTextSize(34f * scale);
                text.setColor(0xFFFFFFFF);
                c.drawText("Final score  " + g.score, sw / 2f, sh * 0.4f + 70f * scale, text);
                text.setColor(0xFFBFD4FF);
                c.drawText("Coins collected  " + g.coinsTaken, sw / 2f, sh * 0.4f + 118f * scale, text);
                blinkText(c, "TAP TO RETRY  •  " + g.levelLabel(), sw / 2f, sh * 0.62f, 32f * scale, g);
                text.setTextSize(22f * scale);
                text.setColor(0xCCFFFFFF);
                text.setShadowLayer(4f, 0f, 3f, 0x99000000);
                c.drawText("your progress auto-saves at each level",
                        sw / 2f, sh * 0.62f + 46f * scale, text);
                break;
            case WIN:
                scrim(c, sw, sh, 0.60f);
                text.setTextAlign(Paint.Align.CENTER);
                text.setTextSize(84f * scale);
                text.setColor(0xFFFFD34D);
                text.setShadowLayer(12f, 0f, 7f, 0xAA000000);
                c.drawText("YOU WIN!", sw / 2f, sh * 0.36f, text);
                text.setTextSize(34f * scale);
                text.setColor(0xFFFFFFFF);
                c.drawText("All three peaks conquered", sw / 2f, sh * 0.36f + 62f * scale, text);
                text.setColor(0xFF9BE89B);
                c.drawText("Final score  " + g.score, sw / 2f, sh * 0.36f + 112f * scale, text);
                blinkText(c, "TAP FOR TITLE", sw / 2f, sh * 0.62f, 30f * scale, g);
                break;
            case PLAYING:
            default:
                // feedback flash when the player bumps a locked goal
                if (g.goalLockedTimer > 0f && g.mode == Game.Mode.COIN_COLLECTOR) {
                    int need = Math.max(1, g.coinGoalRequired() - g.level.coinsTaken());
                    text.setTextAlign(Paint.Align.CENTER);
                    text.setTextSize(30f * scale);
                    text.setColor(0xFFFF9A3D);
                    text.setShadowLayer(6f, 0f, 4f, 0xAA000000);
                    c.drawText("GOAL LOCKED — NEED " + need + " MORE COINS",
                            sw / 2f, sh * 0.2f, text);
                }
                break;
        }
    }

    private void drawMenu(Canvas c, Game g, float sw, float sh, float scale) {
        scrim(c, sw, sh, 0.45f);
        text.setTextAlign(Paint.Align.CENTER);

        float bob = (float) Math.sin(g.worldTime * 2f) * 6f;
        // title
        text.setTextSize(88f * scale);
        text.setColor(0xAA000000);
        text.setShadowLayer(0f, 0f, 0f, 0);
        c.drawText("ANPLOMER", sw / 2f + 5f, sh * 0.185f + 6f + bob * 0.3f, text);
        text.setColor(0xFFFFD34D);
        text.setShadowLayer(10f, 0f, 6f, 0xCC000000);
        c.drawText("ANPLOMER", sw / 2f, sh * 0.185f + bob * 0.3f, text);

        text.setTextSize(24f * scale);
        text.setColor(0xFFFFFFFF);
        text.setShadowLayer(4f, 0f, 3f, 0x99000000);
        c.drawText("A TINY PLATFORM ADVENTURE", sw / 2f, sh * 0.185f + 40f * scale, text);

        // side art, pushed to the edges so it never fights the buttons
        float y = sh * 0.52f;
        drawMenuHero(c, sw * 0.13f, y, scale * 0.9f, g);
        drawMenuEnemy(c, sw * 0.87f, y, scale * 0.9f, g);
        drawMenuCoin(c, sw * 0.87f - 130f * scale, y - 70f * scale, scale * 0.8f, g);

        // menu buttons (START / CONTINUE / MODE / SOUND / small RESET)
        for (Game.UiButton b : g.menuButtons) {
            boolean small = b.id == Game.BTN_RESET;
            float r = small ? 12f : 18f;
            fill.setColor(small ? 0x99203A66 : 0xF21E3A6E);
            c.drawRoundRect(rect(b.x, b.y, b.x + b.w, b.y + b.h), r, r, fill);
            stroke.setColor(small ? 0x66FFFFFF : 0xAAFFFFFF);
            stroke.setStrokeWidth(small ? 2f : 3f);
            c.drawRoundRect(rect(b.x, b.y, b.x + b.w, b.y + b.h), r, r, stroke);
            if (!small) {
                fill.setColor(0x2EFFFFFF);
                c.drawRoundRect(rect(b.x, b.y, b.x + b.w, b.y + b.h * 0.5f), r, r, fill);
            }
            float ts = small ? Math.max(16f, 20f * scale) : Math.max(24f, 30f * scale);
            text.setTextSize(Math.min(ts, b.h * 0.5f));
            text.setColor(small ? 0xCCFFFFFF : 0xFFFFFFFF);
            text.setShadowLayer(4f, 0f, 3f, 0x99000000);
            c.drawText(b.label, b.x + b.w / 2f, b.y + b.h * 0.68f, text);
        }

        text.setTextSize(20f * scale);
        text.setColor(0xCCFFFFFF);
        text.setShadowLayer(4f, 0f, 3f, 0x99000000);
        c.drawText("auto-saves the level you reach  •  losing all lives returns here",
                sw / 2f, sh * 0.885f, text);
    }

    private void drawMenuHero(Canvas c, float x, float y, float scale, Game g) {
        c.save();
        c.translate(x, y + (float) Math.sin(g.worldTime * 3f) * 6f);
        c.scale(2.1f * scale, 2.1f * scale);
        fill.setColor(0x66000000);
        c.drawOval(rect(-24f, 44f, 24f, 58f), fill);
        fill.setColor(0xFF2A3F7A);
        c.drawRoundRect(rect(-16f, 26f, -2f, 52f), 5f, 5f, fill);
        c.drawRoundRect(rect(2f, 26f, 16f, 52f), 5f, 5f, fill);
        fill.setColor(0xFF3F6BE0);
        c.drawRoundRect(rect(-22f, -14f, 22f, 32f), 12f, 12f, fill);
        fill.setColor(0xFF5A85F5);
        c.drawRoundRect(rect(-18f, -11f, 18f, 6f), 9f, 9f, fill);
        fill.setColor(0xFFF3C79A);
        c.drawRoundRect(rect(-22f, -54f, 22f, -24f), 14f, 14f, fill);
        fill.setColor(0xFF4A2E1D);
        path.reset();
        path.moveTo(-22f, -38f);
        path.lineTo(-22f, -53f);
        path.quadTo(0f, -64f, 22f, -53f);
        path.lineTo(22f, -44f);
        path.lineTo(8f, -49f);
        path.lineTo(-6f, -42f);
        path.lineTo(-16f, -48f);
        path.close();
        c.drawPath(path, fill);
        fill.setColor(0xFFFFFFFF);
        c.drawOval(rect(2f, -47f, 16f, -34f), fill);
        fill.setColor(0xFF2A2118);
        c.drawCircle(11f, -40f, 5f, fill);
        fill.setColor(0xFFE14A4A);
        path.reset();
        path.moveTo(-6f, -16f);
        path.lineTo(-34f, -6f + (float) Math.sin(g.worldTime * 6f) * 5f);
        path.lineTo(-6f, 2f);
        path.close();
        c.drawPath(path, fill);
        c.restore();
    }

    private void drawMenuEnemy(Canvas c, float x, float y, float scale, Game g) {
        c.save();
        c.translate(x, y + (float) Math.sin(g.worldTime * 3f + 1f) * 6f);
        c.scale(2.0f * scale, 2.0f * scale);
        fill.setColor(0x66000000);
        c.drawOval(rect(-26f, 42f, 26f, 56f), fill);
        fill.setColor(0xFF9E3227);
        c.drawRoundRect(rect(-24f, -8f, 24f, 46f), 16f, 16f, fill);
        fill.setColor(0xFFC74B3E);
        c.drawRoundRect(rect(-24f, -8f, 24f, 26f), 16f, 16f, fill);
        fill.setColor(0xFFF2E2C2);
        path.reset();
        path.moveTo(-14f, -4f);
        path.lineTo(-22f, -24f);
        path.lineTo(-4f, -8f);
        path.close();
        c.drawPath(path, fill);
        path.reset();
        path.moveTo(14f, -4f);
        path.lineTo(22f, -24f);
        path.lineTo(4f, -8f);
        path.close();
        c.drawPath(path, fill);
        fill.setColor(0xFFFFFFFF);
        c.drawOval(rect(-16f, 4f, -4f, 18f), fill);
        c.drawOval(rect(4f, 4f, 16f, 18f), fill);
        fill.setColor(0xFF2A1210);
        c.drawCircle(-8f, 12f, 4f, fill);
        c.drawCircle(10f, 12f, 4f, fill);
        stroke.setColor(0xFF3A1512);
        stroke.setStrokeWidth(4f);
        c.drawLine(-18f, 0f, -3f, 8f, stroke);
        c.drawLine(3f, 8f, 18f, 0f, stroke);
        c.restore();
    }

    private void drawMenuCoin(Canvas c, float x, float y, float scale, Game g) {
        c.save();
        c.translate(x, y + (float) Math.sin(g.worldTime * 3f + 2f) * 6f);
        float spin = Math.max(0.2f, Math.abs((float) Math.cos(g.worldTime * 3f)));
        c.scale(spin * 2.0f * scale, 2.0f * scale);
        fill.setColor(0xFFFFC93C);
        c.drawCircle(0f, 0f, 26f, fill);
        fill.setColor(0xFFFFE58A);
        c.drawCircle(-5f, -5f, 16f, fill);
        stroke.setColor(0xFFD89A17);
        stroke.setStrokeWidth(3f);
        c.drawCircle(0f, 0f, 24f, stroke);
        fill.setColor(0xFFE8A81C);
        c.drawRect(rect(-4f, -12f, 4f, 12f), fill);
        c.drawRect(rect(-10f, -4f, 10f, 4f), fill);
        c.restore();
    }

    private void blinkText(Canvas c, String s, float x, float y, float size, Game g) {
        float a = 0.55f + 0.45f * (float) Math.sin(g.worldTime * 4f);
        text.setTextSize(size);
        text.setTextAlign(Paint.Align.CENTER);
        text.setShadowLayer(8f, 0f, 5f, 0xAA000000);
        text.setColor(Color.argb((int) (255 * a), 255, 255, 255));
        c.drawText(s, x, y, text);
        text.setShadowLayer(6f, 0f, 4f, 0x99000000);
    }

    private void scrim(Canvas c, float sw, float sh, float alpha) {
        fill.setColor(Color.argb((int) (255 * alpha), 0, 0, 0));
        c.drawRect(0f, 0f, sw, sh, fill);
    }

    private void drawPanel(Canvas c, float cx, float cy, float w, float h) {
        fill.setColor(0xE6171C2E);
        c.drawRoundRect(rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f), 26f, 26f, fill);
        stroke.setColor(0x66FFFFFF);
        stroke.setStrokeWidth(3f);
        c.drawRoundRect(rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f), 26f, 26f, stroke);
    }

    // ------------------------------------------------------------------

    private boolean visible(Game g, float x, float y, float w, float h) {
        return x + w > g.camera.x - 60f
                && x < g.camera.x + g.camera.viewW + 60f
                && y + h > g.camera.y - 60f
                && y < g.camera.y + g.camera.viewH + 60f;
    }

    private RectF rect(float l, float t, float r, float b) {
        rect.set(l, t, r, b);
        return rect;
    }

    private static float h1(int n) {
        float x = (float) Math.sin(n * 12.9898f + 78.233f) * 43758.5453f;
        return x - (float) Math.floor(x);
    }
}
