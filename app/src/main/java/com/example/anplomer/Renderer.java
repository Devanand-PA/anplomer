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
    // palettes per level: 1 meadow day, 2 sunset ruins, 3 night peak,
    // 4 crystal caverns, 5 ember forge, 6 windy bluffs, 7 frozen spire,
    // 8 starfall summit. The first three are the original colours.
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
            // 1 — Emerald Meadow: bright blue day
            new Pal(0xFF2E6BD6, 0xFF5AA7F0, 0xFFBFE4FF, 0xFFFFFFFF,
                    0xFF8FA6DE, 0xFF4F9E5E, 0xFF2F7346,
                    0xFF59C24C, 0xFF3E9E3A, 0xFF9A6236, 0xFF7A4A28,
                    0xFF9AA3AE, 0xFF737C88, false),
            // 2 — Sunset Ruins: warm dusk
            new Pal(0xFF2A2A66, 0xFFB8547A, 0xFFFFC178, 0xFFFFE3C6,
                    0xFF6A4C8C, 0xFF7A5070, 0xFF43305A,
                    0xFF84B84A, 0xFF5F9438, 0xFF8E5A34, 0xFF6F421F,
                    0xFFA29898, 0xFF7A7070, false),
            // 3 — Night Peak: deep blue night with a moon
            new Pal(0xFF0A1034, 0xFF1B2A6B, 0xFF3E56A6, 0xFFC8D6FF,
                    0xFF3E4E8E, 0xFF24406B, 0xFF152A46,
                    0xFF3FA36A, 0xFF2C7C50, 0xFF5D4433, 0xFF43301F,
                    0xFF8089A8, 0xFF5C6480, true),
            // 4 — Crystal Caverns: violet underground, crystal "stars"
            new Pal(0xFF140B2E, 0xFF35215E, 0xFF6E4C9E, 0xFFE0D2FF,
                    0xFF2C2148, 0xFF3E2F5E, 0xFF261B3C,
                    0xFF46D6C0, 0xFF2A9E90, 0xFF4A3A5E, 0xFF342944,
                    0xFF8B79B8, 0xFF61538A, true),
            // 5 — Ember Forge: ash sky over molten rock
            new Pal(0xFF2B0E10, 0xFF7A2412, 0xFFE07A2A, 0xFFFFD2A0,
                    0xFF4A1E1C, 0xFF6E2A1E, 0xFF3A1410,
                    0xFFE08A3C, 0xFFB05E24, 0xFF5A2E22, 0xFF3E1E16,
                    0xFF8A6A5C, 0xFF64483C, false),
            // 6 — Windy Bluffs: high bright sky, long flyer arcs
            new Pal(0xFF1E7FC4, 0xFF68C6E8, 0xFFDFF6FF, 0xFFFFFFFF,
                    0xFF9FB8C8, 0xFF5FA86E, 0xFF3F8A5A,
                    0xFF8FD44A, 0xFF62A832, 0xFFB98A4A, 0xFF8E6632,
                    0xFFA8B4BE, 0xFF7C8892, false),
            // 7 — Frozen Spire: icy twilight with a cold moon
            new Pal(0xFF101A3A, 0xFF2A4E86, 0xFF9CC4EC, 0xFFE8F4FF,
                    0xFF6A8AB0, 0xFF4A6E96, 0xFF33506E,
                    0xFFBFE8FF, 0xFF8FC0E0, 0xFF6E7C8E, 0xFF505C6C,
                    0xFF9FB0C4, 0xFF74849A, true),
            // 8 — Starfall Summit: cosmic finale
            new Pal(0xFF07030F, 0xFF241048, 0xFF5A2A7A, 0xFFC9A8FF,
                    0xFF3A2A5C, 0xFF2A1E46, 0xFF1A1230,
                    0xFF54E0B0, 0xFF34A884, 0xFF4A3A62, 0xFF342848,
                    0xFF8E7EC4, 0xFF665A96, true),
    };

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    // ------------------------------------------------------------------
    // precomputed tables: the frame path used to call Math.sin for every
    // star twinkle, coin spin, lava ripple and h1() hash. Looking the value
    // up in a table is visually identical and keeps the per-frame trig off
    // the hot path (and the hash table reproduces h1 bit-for-bit in range,
    // so the generated scenery looks exactly the same).
    // ------------------------------------------------------------------

    /** Sin table over one full turn (2048 steps is well under a pixel of error). */
    private static final int SIN_BITS = 11;
    private static final int SIN_SIZE = 1 << SIN_BITS;
    private static final int SIN_MASK = SIN_SIZE - 1;
    private static final float SIN_SCALE = SIN_SIZE / (float) (Math.PI * 2);
    private static final float[] SIN = new float[SIN_SIZE];
    private static final int SIN_QUARTER = SIN_SIZE / 4;

    /** h1() memo: exact values for the integer range the scenery/tiles use. */
    private static final int HASH_OFF = 4096;
    private static final float[] HASH = new float[16384];

    static {
        for (int i = 0; i < SIN_SIZE; i++) {
            SIN[i] = (float) Math.sin(i * (Math.PI * 2 / SIN_SIZE));
        }
        for (int i = 0; i < HASH.length; i++) {
            int n = i - HASH_OFF;
            float x = (float) Math.sin(n * 12.9898f + 78.233f) * 43758.5453f;
            HASH[i] = x - (float) Math.floor(x);
        }
    }

    /** Table sine for cosmetic animation (radians, any magnitude). */
    private static float sinf(float radians) {
        return SIN[((int) (radians * SIN_SCALE)) & SIN_MASK];
    }

    private static float cosf(float radians) {
        return SIN[((int) (radians * SIN_SCALE) + SIN_QUARTER) & SIN_MASK];
    }

    /** Deterministic hash in [0,1) — same values as the old Math.sin version. */
    private static float h1(int n) {
        int i = n + HASH_OFF;
        if (i >= 0 && i < HASH.length) {
            return HASH[i];
        }
        float x = (float) Math.sin(n * 12.9898f + 78.233f) * 43758.5453f;
        return x - (float) Math.floor(x);
    }

    // ------------------------------------------------------------------
    // caches: nothing in draw() may allocate once values settle. The sky
    // gradient is rebuilt only when the palette or the surface changes;
    // HUD/overlay strings are rebuilt only when their value changes.
    // ------------------------------------------------------------------

    private LinearGradient skyShader;
    private float skyW = -1f;
    private float skyH = -1f;
    private int skyPal = -1;
    /** Palette currently drawn (index into {@link #PALS}); set at frame start. */
    private int palIndex;

    private float lblScale = -1f;
    private String scoreStr = "000000";
    private int scoreKey = Integer.MIN_VALUE;
    private float scoreW;
    private String coinStr = "0/0";
    private int coinKey = Integer.MIN_VALUE;
    private int coinTotalKey = -1;
    private float coinW0;
    private String walletStr = "0";
    private int walletKey = Integer.MIN_VALUE;
    private float walletW0;
    private String goalStr = "";
    private int goalKey = Integer.MIN_VALUE;
    private boolean goalLockedKey;
    private float goalW0;

    /** "12.3s" style labels, built once per tenth-of-a-second value ever seen. */
    private static final String[] TENTH_LABELS = new String[512];

    /** Small non-negative integers as strings, built at most once each. */
    private static final String[] NUM_LABELS = new String[1024];

    /** Career avatar tints (Profile.colorIndex indexes this list). */
    private static final int[] AVATAR = {
            0xFFFF8A65, 0xFF4FC3F7, 0xFF9CCC65,
            0xFFFFD54F, 0xFFBA68C8, 0xFF4DD0E1,
    };

    /** HUD power-up chips: type ordinal + remaining tenths. */
    private final int[] chipKey = {-1, -1, -1};
    private final String[] chipStr = new String[3];

    /** Locked-goal flash label (raised in PLAYING, so cached). */
    private int needKey = -1;
    private String needStr = "";

    /** Active-career badge measurements. */
    private int badgeKey = Integer.MIN_VALUE;
    private float badgeScale = -1f;
    private float badgeNameW, badgeCoinsW, badgeLvlW;

    /** Store row labels, rebuilt when the upgrade level changes. */
    private final int[] storeKey = {-1, -1, -1, -1, -1, -1};
    private final String[] storeEff = new String[Upgrades.COUNT];
    private final String[] storePrice = new String[Upgrades.COUNT];

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
        int pi = Math.min(g.levelIndex, PALS.length - 1);
        Pal p = PALS[pi];
        palIndex = pi;

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
        drawPowerUps(c, g);
        drawEnemies(c, g);
        if (!(g.state == Game.State.MENU && g.player.dead)) {
            drawPlayer(c, g);
        }
        drawProjectiles(c, g);
        drawSwing(c, g);
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
        // The gradient only depends on (palette, surface size): rebuild it on a
        // resize or palette change instead of allocating one every frame.
        if (skyShader == null || sw != skyW || sh != skyH || palIndex != skyPal) {
            skyShader = new LinearGradient(0f, 0f, 0f, sh,
                    new int[]{p.skyTop, p.skyMid, p.skyBot},
                    new float[]{0f, 0.58f, 1f}, Shader.TileMode.CLAMP);
            skyW = sw;
            skyH = sh;
            skyPal = palIndex;
        }
        fill.setShader(skyShader);
        c.drawRect(0f, 0f, sw, sh, fill);
        fill.setShader(null);

        if (p.night) {
            // stars
            float tw = g.worldTime * 2.4f;
            for (int i = 0; i < 90; i++) {
                float sx = h1(i * 3 + 1) * sw;
                float sy = h1(i * 3 + 2) * sh * 0.62f;
                float k = 0.45f + 0.55f * (0.5f + 0.5f * sinf(tw + i));
                fill.setColor(Color.argb((int) (190 * k), 255, 255, 255));
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
            // sun with glow; a low, big ember sun for the forge palette
            float sx = sw * sunXFrac();
            float sy = sh * sunYFrac();
            fill.setColor(sunGlow());
            c.drawCircle(sx, sy, palIndex == 4 ? 190f : 130f, fill);
            fill.setColor(sunCore());
            c.drawCircle(sx, sy, palIndex == 4 ? 84f : 62f, fill);
        }

        // clouds
        float scroll = g.camera.x * 0.18f;
        float spacing = 460f;
        int first = (int) Math.floor(scroll / spacing) - 1;
        for (int i = first; i <= first + (int) (sw / spacing) + 2; i++) {
            float cx = i * spacing + h1(i * 13) * 200f - scroll;
            float cy = sh * (0.10f + h1(i * 13 + 5) * 0.30f);
            float scale = 0.7f + h1(i * 13 + 9) * 0.8f;
            float drift = sinf(g.worldTime * 0.35f + i) * 14f;
            drawCloud(c, cx + drift, cy, scale, p.cloud);
        }
    }

    /** Sun position per palette (kept out of Pal: layout is a renderer concern). */
    private float sunXFrac() {
        switch (palIndex) {
            case 1:
                return 0.30f;
            case 4:
                return 0.22f;
            case 6:
                return 0.68f;
            default:
                return 0.80f;
        }
    }

    private float sunYFrac() {
        switch (palIndex) {
            case 1:
                return 0.40f;
            case 4:
                return 0.52f;
            case 6:
                return 0.24f;
            default:
                return 0.18f;
        }
    }

    private int sunGlow() {
        switch (palIndex) {
            case 1:
                return 0x55FFD9A0;
            case 4:
                return 0x55FF9A3C;
            case 6:
                return 0x44CFE6FF;
            default:
                return 0x44FFF6C0;
        }
    }

    private int sunCore() {
        switch (palIndex) {
            case 1:
                return 0xFFFFD08A;
            case 4:
                return 0xFFFFC56A;
            case 6:
                return 0xFFEAF4FF;
            default:
                return 0xFFFFF3C4;
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

        // Solid bodies are batched into horizontal runs: one body rect (one
        // outline/crack/highlight path for stone) per run instead of one set
        // of primitives per 64 px tile. Per-tile detail (speckles, grass
        // blades) is only emitted where it can actually be seen — the exposed
        // surface row and the exposed side columns of a run.
        for (int ty = ty0; ty <= ty1; ty++) {
            int tx = tx0;
            while (tx <= tx1) {
                char ch = lvl.tileAt(tx, ty);
                if (ch == Level.GROUND) {
                    boolean topOpen = !lvl.isSolid(tx, ty - 1);
                    int end = tx;
                    while (end < tx1 && lvl.tileAt(end + 1, ty) == Level.GROUND
                            && (!lvl.isSolid(end + 1, ty - 1)) == topOpen) {
                        end++;
                    }
                    drawGroundRun(c, lvl, p, ty, tx, end, topOpen);
                    tx = end + 1;
                } else if (ch == Level.STONE) {
                    int end = tx;
                    while (end < tx1 && lvl.tileAt(end + 1, ty) == Level.STONE) {
                        end++;
                    }
                    drawStoneRun(c, p, ty, tx, end);
                    tx = end + 1;
                } else if (ch == Level.LAVA) {
                    int end = tx;
                    while (end < tx1 && lvl.tileAt(end + 1, ty) == Level.LAVA) {
                        end++;
                    }
                    drawLavaRun(c, p, ty, tx, end, t);
                    tx = end + 1;
                } else if (ch == Level.SPIKE) {
                    drawSpikeTile(c, ty, tx);
                    tx++;
                } else {
                    tx++;
                }
            }
        }
    }

    private void drawGroundRun(Canvas c, Level lvl, Pal p, int ty, int x0, int x1, boolean topOpen) {
        final float T = Constants.TILE;
        float x = x0 * T;
        float y = ty * T;
        float w = (x1 - x0 + 1) * T;

        // body
        fill.setColor(p.dirt);
        c.drawRect(x, y, x + w, y + T, fill);

        // speckles: surface row and exposed side columns only — interior bulk
        // is covered by its neighbours and would be invisible detail anyway
        fill.setColor(p.dirtDark);
        boolean sideOpenL = !lvl.isSolid(x0 - 1, ty);
        boolean sideOpenR = !lvl.isSolid(x1 + 1, ty);
        for (int tx = x0; tx <= x1; tx++) {
            boolean exposed = topOpen || (tx == x0 && sideOpenL) || (tx == x1 && sideOpenR);
            if (!exposed) {
                continue;
            }
            float bx = tx * T;
            for (int i = 0; i < 3; i++) {
                float sx = bx + h1(tx * 31 + ty * 7 + i) * (T - 14f) + 7f;
                float sy = y + h1(tx * 17 + ty * 13 + i * 3) * (T - 14f) + 7f;
                c.drawCircle(sx, sy, 3f + h1(tx + ty + i) * 3f, fill);
            }
        }

        if (topOpen) {
            // grass cap over the whole run, then all blades in one path
            fill.setColor(p.grass);
            c.drawRect(x, y, x + w, y + 20f, fill);
            fill.setColor(p.grassDark);
            c.drawRect(x, y + 16f, x + w, y + 22f, fill);
            fill.setColor(p.grass);
            path.reset();
            for (int tx = x0; tx <= x1; tx++) {
                float bx = tx * T;
                for (int i = 0; i < 4; i++) {
                    float gx = bx + 8f + i * 15f;
                    float bh = 7f + h1(tx * 5 + ty * 3 + i) * 8f;
                    path.moveTo(gx, y + 4f);
                    path.lineTo(gx + 4f, y + 4f - bh);
                    path.lineTo(gx + 8f, y + 4f);
                    path.close();
                }
            }
            c.drawPath(path, fill);
        }

        // side shading
        fill.setColor(0x33000000);
        if (sideOpenL) {
            c.drawRect(x, y, x + 9f, y + T, fill);
        }
        if (sideOpenR) {
            c.drawRect(x + w - 9f, y, x + w, y + T, fill);
        }
    }

    private void drawStoneRun(Canvas c, Pal p, int ty, int x0, int x1) {
        final float T = Constants.TILE;
        float x = x0 * T;
        float y = ty * T;
        float w = (x1 - x0 + 1) * T;

        fill.setColor(p.stone);
        c.drawRect(x + 2f, y + 2f, x + w - 2f, y + T - 2f, fill);

        // per-tile borders, cracks and top highlights batched into three paths
        stroke.setColor(p.stoneDark);
        stroke.setStrokeWidth(3f);
        path.reset();
        for (int tx = x0; tx <= x1; tx++) {
            float bx = tx * T;
            path.moveTo(bx + 2f, y + 2f);
            path.lineTo(bx + T - 2f, y + 2f);
            path.lineTo(bx + T - 2f, y + T - 2f);
            path.lineTo(bx + 2f, y + T - 2f);
            path.close();
        }
        c.drawPath(path, stroke);

        stroke.setStrokeWidth(2.5f);
        stroke.setColor(0x55000000);
        path.reset();
        for (int tx = x0; tx <= x1; tx++) {
            float bx = tx * T;
            float ox = h1(tx * 3 + ty) * 20f;
            path.moveTo(bx + 14f + ox, y + 10f);
            path.lineTo(bx + 30f + ox, y + 34f);
            path.lineTo(bx + 20f + ox, y + T - 10f);
        }
        c.drawPath(path, stroke);

        fill.setColor(0x22FFFFFF);
        path.reset();
        for (int tx = x0; tx <= x1; tx++) {
            float bx = tx * T;
            path.moveTo(bx + 4f, y + 4f);
            path.lineTo(bx + T - 4f, y + 4f);
            path.lineTo(bx + T - 4f, y + 11f);
            path.lineTo(bx + 4f, y + 11f);
            path.close();
        }
        c.drawPath(path, fill);
    }

    private void drawLavaRun(Canvas c, Pal p, int ty, int x0, int x1, float t) {
        final float T = Constants.TILE;
        float x = x0 * T;
        float y = ty * T;
        float w = (x1 - x0 + 1) * T;

        fill.setColor(0xFFD33A16);
        c.drawRect(x, y, x + w, y + T, fill);
        fill.setColor(0xFFFF8A1E);
        c.drawRect(x, y + 8f, x + w, y + T, fill);

        // animated surface: every tile's wave strip in one path
        fill.setColor(0xFFFFD24A);
        path.reset();
        for (int tx = x0; tx <= x1; tx++) {
            float bx = tx * T;
            path.moveTo(bx, y + 14f);
            for (int i = 0; i <= 8; i++) {
                float px = bx + i * (T / 8f);
                float py = y + 14f + sinf(t * 3.2f + tx * 1.1f + i * 0.7f) * 6f;
                path.lineTo(px, py);
            }
            path.lineTo(bx + T, y + 26f);
            path.lineTo(bx, y + 26f);
            path.close();
        }
        c.drawPath(path, fill);

        // bubbles are few and per-tile: keep them as circles
        fill.setColor(0x80FFE9A0);
        for (int tx = x0; tx <= x1; tx++) {
            float bx = tx * T;
            float b = h1(tx * 9 + ty * 5);
            float bt = (t * 0.6f + b) % 1f;
            if (bt < 0f) {
                bt = 0f;
            }
            c.drawCircle(bx + b * (T - 20f) + 10f, y + T - bt * T * 0.7f, 4f + b * 5f, fill);
        }
    }

    private void drawSpikeTile(Canvas c, int ty, int tx) {
        final float T = Constants.TILE;
        float x = tx * T;
        float y = ty * T;
        float wSeg = T / 3f;
        fill.setColor(0xFFB9C2CC);
        stroke.setColor(0xFF6E7885);
        stroke.setStrokeWidth(3f);
        path.reset();
        for (int i = 0; i < 3; i++) {
            path.moveTo(x + i * wSeg + 3f, y + T);
            path.lineTo(x + i * wSeg + wSeg / 2f, y + T * 0.42f);
            path.lineTo(x + i * wSeg + wSeg - 3f, y + T);
            path.close();
        }
        c.drawPath(path, fill);
        c.drawPath(path, stroke);
        fill.setColor(0x55FFFFFF);
        path.reset();
        for (int i = 0; i < 3; i++) {
            path.moveTo(x + i * wSeg + wSeg / 2f, y + T * 0.42f);
            path.lineTo(x + i * wSeg + wSeg / 2f + 4f, y + T * 0.55f);
            path.lineTo(x + i * wSeg + wSeg / 2f + 6f, y + T * 0.62f);
            path.close();
        }
        c.drawPath(path, fill);
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
                float bob = sinf(g.worldTime * 4f) * 6f;
                path.reset();
                path.moveTo(p.x + p.w / 2f - 9f, p.y - 12f + bob);
                path.lineTo(p.x + p.w / 2f + 9f, p.y - 12f + bob);
                path.lineTo(p.x + p.w / 2f, p.y - 24f + bob);
                path.close();
                c.drawPath(path, fill);
            }
        }
    }

    /** Accent colour per power-up type (also used for the HUD chips). */
    private static int powerColor(PowerUp.Type t) {
        switch (t) {
            case SPEED:
                return 0xFF2BD9A5;
            case SHIELD:
                return 0xFF4AA8FF;
            case MAGNET:
                return 0xFFE0483C;
            case DOUBLE_JUMP:
                return 0xFFFFC93C;
            case EXTRA_LIFE:
                return 0xFFFF6B8B;
            default:
                return 0xFFFFFFFF;
        }
    }

    private void drawPowerUps(Canvas c, Game g) {
        for (PowerUp pu : g.powerUps) {
            if (pu.index >= 0 && g.level.powerUpTaken(pu.index)) {
                continue;
            }
            float s = Constants.POWERUP_SIZE;
            if (!visible(g, pu.x - s, pu.y - s, s * 2f, s * 2f)) {
                continue;
            }
            float bob = sinf(g.worldTime * 2.2f + pu.phase) * 5f;
            float spin = cosf(g.worldTime * 2.6f + pu.phase);
            float sx = Math.max(0.34f, Math.abs(spin));
            float r = s * 0.5f;
            int col = powerColor(pu.type);
            float cy = pu.y + bob;

            // shadow on the ground below the pickup
            fill.setColor(0x33000000);
            c.drawOval(rect(pu.x - r * 0.7f, pu.y + r + 10f,
                    pu.x + r * 0.7f, pu.y + r + 16f), fill);

            // soft glow, pulsing with the bob
            float pulse = 0.5f + 0.5f * sinf(g.worldTime * 3.1f + pu.phase);
            fill.setColor((col & 0x00FFFFFF) | ((int) (40 + 50 * pulse) << 24));
            c.drawCircle(pu.x, cy, r * (1.35f + 0.12f * pulse), fill);

            c.save();
            c.translate(pu.x, cy);
            c.scale(sx, 1f);
            // chip
            fill.setColor(0xE01B2340);
            c.drawCircle(0f, 0f, r, fill);
            fill.setColor((col & 0x00FFFFFF) | 0x55000000);
            c.drawCircle(0f, 0f, r * 0.86f, fill);
            fill.setColor(col);
            c.drawCircle(0f, 0f, r * 0.78f, fill);
            stroke.setColor(0xAAFFFFFF);
            stroke.setStrokeWidth(Math.max(1f, s * 0.055f));
            c.drawCircle(0f, 0f, r, stroke);
            drawPowerIcon(c, pu.type, r * 1.05f);
            c.restore();

            // sparkle while the chip faces the camera
            if (Math.abs(spin) > 0.82f) {
                fill.setColor(0xCCFFFFFF);
                c.drawCircle(pu.x + r * 0.62f, cy - r * 0.62f, Math.max(1f, s * 0.075f), fill);
            }
        }
    }

    /**
     * White glyph on the power-up chip, centred at the current origin.
     * {@code size} is roughly the icon's diameter.
     */
    private void drawPowerIcon(Canvas c, PowerUp.Type type, float size) {
        float h = size * 0.5f;
        fill.setColor(0xFFFFFFFF);
        switch (type) {
            case SPEED: {
                // boot: sole + shaft
                path.reset();
                path.moveTo(-h * 0.52f, -h * 0.62f);
                path.lineTo(-h * 0.02f, -h * 0.62f);
                path.lineTo(-h * 0.02f, h * 0.10f);
                path.lineTo(h * 0.60f, h * 0.10f);
                path.lineTo(h * 0.60f, h * 0.42f);
                path.lineTo(-h * 0.52f, h * 0.42f);
                path.close();
                c.drawPath(path, fill);
                fill.setColor(0x66000000);
                c.drawRect(rect(-h * 0.52f, h * 0.24f, h * 0.60f, h * 0.42f), fill);
                break;
            }
            case SHIELD: {
                path.reset();
                path.moveTo(0f, -h * 0.72f);
                path.lineTo(h * 0.58f, -h * 0.42f);
                path.lineTo(h * 0.58f, h * 0.06f);
                path.quadTo(h * 0.48f, h * 0.62f, 0f, h * 0.78f);
                path.quadTo(-h * 0.48f, h * 0.62f, -h * 0.58f, h * 0.06f);
                path.lineTo(-h * 0.58f, -h * 0.42f);
                path.close();
                c.drawPath(path, fill);
                fill.setColor(0x66000000);
                path.reset();
                path.moveTo(-h * 0.20f, -h * 0.52f);
                path.lineTo(h * 0.20f, -h * 0.52f);
                path.lineTo(h * 0.20f, h * 0.48f);
                path.quadTo(0f, h * 0.58f, -h * 0.20f, h * 0.48f);
                path.close();
                c.drawPath(path, fill);
                break;
            }
            case MAGNET: {
                // U magnet with two poles
                stroke.setColor(0xFFFFFFFF);
                stroke.setStrokeWidth(Math.max(1f, size * 0.20f));
                c.drawArc(rect(-h * 0.55f, -h * 0.62f, h * 0.55f, h * 0.62f), 0f, 180f, false, stroke);
                stroke.setStrokeWidth(Math.max(1f, size * 0.20f));
                c.drawLine(-h * 0.55f, 0f, -h * 0.55f, h * 0.58f, stroke);
                c.drawLine(h * 0.55f, 0f, h * 0.55f, h * 0.58f, stroke);
                fill.setColor(0xFFFFFFFF);
                c.drawRect(rect(-h * 0.72f, h * 0.34f, -h * 0.38f, h * 0.64f), fill);
                c.drawRect(rect(h * 0.38f, h * 0.34f, h * 0.72f, h * 0.64f), fill);
                break;
            }
            case DOUBLE_JUMP: {
                // two stacked up chevrons
                for (int i = 0; i < 2; i++) {
                    float oy = h * (i == 0 ? -0.42f : 0.16f);
                    path.reset();
                    path.moveTo(0f, oy - h * 0.34f);
                    path.lineTo(h * 0.58f, oy + h * 0.22f);
                    path.lineTo(h * 0.58f, oy + h * 0.52f);
                    path.lineTo(0f, oy - h * 0.04f);
                    path.lineTo(-h * 0.58f, oy + h * 0.52f);
                    path.lineTo(-h * 0.58f, oy + h * 0.22f);
                    path.close();
                    c.drawPath(path, fill);
                }
                break;
            }
            case EXTRA_LIFE:
            default: {
                path.reset();
                path.moveTo(0f, h * 0.70f);
                path.cubicTo(-h * 1.10f, -h * 0.10f, -h * 0.62f, -h * 0.86f, 0f, -h * 0.28f);
                path.cubicTo(h * 0.62f, -h * 0.86f, h * 1.10f, -h * 0.10f, 0f, h * 0.70f);
                path.close();
                c.drawPath(path, fill);
                break;
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
            float spin = cosf(g.worldTime * 4.2f + coin.spinPhase);
            float sx = Math.max(0.18f, Math.abs(spin));
            float bob = sinf(g.worldTime * 2.6f + coin.spinPhase) * 6f;
            float r = coin.size / 2f;
            float ccx = coin.x;
            float ccy = coin.y + bob;

            fill.setColor(0x33000000);
            c.drawOval(rect(ccx - r * 0.8f, coin.y + r + 6f + bob,
                    ccx + r * 0.8f, coin.y + r + 12f + bob), fill);

            // The spin is a horizontal squeeze; drawing ellipses instead of
            // save/scale/circle keeps the same picture with two fewer calls
            // per coin (the canvas save/restore pair is not free on device).
            fill.setColor(0xFFFFC93C);
            c.drawOval(rect(ccx - r * sx, ccy - r, ccx + r * sx, ccy + r), fill);
            fill.setColor(0xFFFFE58A);
            float ix = ccx - r * 0.18f * sx;
            float iy = ccy - r * 0.18f;
            float ir = r * 0.62f;
            c.drawOval(rect(ix - ir * sx, iy - ir, ix + ir * sx, iy + ir), fill);
            stroke.setColor(0xFFD89A17);
            stroke.setStrokeWidth(3f);
            float rr = r - 1.5f;
            c.drawOval(rect(ccx - rr * sx, ccy - rr, ccx + rr * sx, ccy + rr), stroke);
            // embossed cross (two bars, horizontally squeezed with the spin)
            fill.setColor(0xFFE8A81C);
            c.drawRect(rect(ccx - r * 0.16f * sx, ccy - r * 0.44f,
                    ccx + r * 0.16f * sx, ccy + r * 0.44f), fill);
            c.drawRect(rect(ccx - r * 0.36f * sx, ccy - r * 0.14f,
                    ccx + r * 0.36f * sx, ccy + r * 0.14f), fill);

            // glint
            float gl = sinf(g.worldTime * 3f + coin.spinPhase * 2f);
            if (gl > 0.7f) {
                fill.setColor(0xB3FFFFFF);
                c.drawCircle(ccx + r * 0.55f, coin.y - r * 0.55f + bob, 5f, fill);
            }
        }
    }

    /** Entity box height / fitted art height, so baked art fills the box. */
    private float artScale(Enemy e, String entity) {
        float artH = ArtBindings.height(entity);
        return artH > 0f ? e.h / artH : 1f;
    }

    // ---- baked-art limb animation --------------------------------------
    // Parts are animated by their SVG layer name (the convention lives in
    // art/README.md): leg/foot/shoe -> limbs, arm/hand -> arms, wing -> wings;
    // "left"/"front" take phase A, "right"/"back" phase B. Everything else is
    // static. Roles and pivots are resolved once per entity and cached.
    private static final int ROLE_STATIC = 0;
    private static final int ROLE_LIMB_A = 1;
    private static final int ROLE_LIMB_B = 2;
    private static final int ROLE_ARM_A = 3;
    private static final int ROLE_ARM_B = 4;
    private static final int ROLE_WING_A = 5;
    private static final int ROLE_WING_B = 6;

    private final java.util.HashMap<String, int[]> artRoles = new java.util.HashMap<>();
    private final java.util.HashMap<String, float[]> artPivots = new java.util.HashMap<>();

    private static int classifyArtPart(String name) {
        String n = name.toLowerCase();
        boolean sideA = n.contains("left") || n.contains("front");
        boolean sideB = n.contains("right") || n.contains("back");
        if (n.contains("wing")) {
            return sideB ? ROLE_WING_B : ROLE_WING_A;
        }
        if (n.contains("arm") || n.contains("hand")) {
            return sideB ? ROLE_ARM_B : ROLE_ARM_A;
        }
        if (n.contains("leg") || n.contains("foot") || n.contains("shoe")
                || n.contains("boot")) {
            return sideB ? ROLE_LIMB_B : ROLE_LIMB_A;
        }
        return ROLE_STATIC;
    }

    /**
     * Draws a bound entity's artwork part by part, rotating the animated roles
     * about their pivots. Z-order follows the SVG document order.
     *
     * @param phase     walk-cycle phase (radians) -- legs swing with it
     * @param moving    on the ground and actually walking
     * @param airborne  airborne pose (tucked limbs, raised arms) instead
     * @param scale     entity box height / artwork height
     */
    private void drawBakedArt(String entity, Canvas c, float phase,
                              boolean moving, boolean airborne, float scale) {
        int[] roles = artRoles.get(entity);
        float[] pivots = artPivots.get(entity);
        if (roles == null) {
            int n = ArtBindings.partCount(entity);
            roles = new int[n];
            pivots = new float[n * 2];
            for (int i = 0; i < n; i++) {
                roles[i] = classifyArtPart(ArtBindings.partName(entity, i));
                pivots[i * 2] = ArtBindings.partPivotX(entity, i);
                pivots[i * 2 + 1] = ArtBindings.partPivotY(entity, i);
            }
            artRoles.put(entity, roles);
            artPivots.put(entity, pivots);
        }
        float swing = sinf(phase);
        for (int i = 0; i < roles.length; i++) {
            float angle;
            switch (roles[i]) {
                case ROLE_LIMB_A:
                    angle = airborne ? 18f : moving ? swing * 26f : 0f;
                    break;
                case ROLE_LIMB_B:
                    angle = airborne ? -26f : moving ? -swing * 26f : 0f;
                    break;
                case ROLE_ARM_A:
                    angle = airborne ? -32f : moving ? -swing * 16f : 0f;
                    break;
                case ROLE_ARM_B:
                    angle = airborne ? 10f : moving ? swing * 16f : 0f;
                    break;
                case ROLE_WING_A:
                    angle = swing * 24f;
                    break;
                case ROLE_WING_B:
                    angle = -swing * 24f;
                    break;
                default:
                    angle = 0f;
                    break;
            }
            if (angle != 0f) {
                float px = pivots[i * 2] * scale;
                float py = pivots[i * 2 + 1] * scale;
                c.save();
                c.translate(px, py);
                c.rotate(angle);
                c.translate(-px, -py);
            }
            ArtBindings.drawPart(entity, i, c, fill, stroke, 0f, 0f, scale);
            if (angle != 0f) {
                c.restore();
            }
        }
    }

    private void drawEnemyHpBar(Canvas c, Game g, Enemy e) {
        // Always visible (the designer makes maxHp a real tuning knob now);
        // only the dead skip it. Flyers sit their bar higher so the wing
        // tips never poke through it.
        if (e.dead || e.maxHp <= 0) {
            return;
        }
        float bw = Math.max(30f, Math.min(e.w * 1.15f, 80f));
        float bh = Constants.HP_BAR_H;
        float bx = e.x + (e.w - bw) / 2f;
        float by = e.y - (e.type == Enemy.Type.FLYER
                ? 26f : Constants.HP_BAR_GAP) - bh;
        float frac = e.hpFraction();
        fill.setColor(0xB0000000);
        c.drawRoundRect(rect(bx - 1.5f, by - 1.5f, bx + bw + 1.5f, by + bh + 1.5f),
                4f, 4f, fill);
        if (frac > 0f) {
            fill.setColor(frac > 0.5f ? 0xFFE4453A
                    : frac > 0.25f ? 0xFFFF7A3D : 0xFFFFC857);
            c.drawRoundRect(rect(bx, by, bx + bw * frac, by + bh), 3f, 3f, fill);
        }
        stroke.setColor(0x99FFFFFF);
        stroke.setStrokeWidth(1.4f);
        c.drawRoundRect(rect(bx, by, bx + bw, by + bh), 4f, 4f, stroke);
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
            drawEnemyHpBar(c, g, e);
        }
    }

    private void drawWalker(Canvas c, Game g, Enemy e) {
        float squashY = e.dead ? 0.30f : 1f;
        float sx = e.facing;
        if (e.hitFlash > 0f && !e.dead) {
            fill.setColor(0xCCFFFFFF);
            c.drawRoundRect(rect(e.x, e.y, e.x + e.w, e.y + e.h), 10f, 10f, fill);
            return;
        }
        if (ArtBindings.has("walker")) {
            // Baked artwork: feet anchor, death squash and a facing flip so a
            // side-authored sprite reads correctly from both directions.
            c.save();
            c.translate(e.x + e.w / 2f, e.y + e.h);
            float wide = Math.min(1.7f, 0.7f / Math.max(squashY, 0.3f) + 0.3f);
            c.scale(wide, squashY);
            c.scale(sx, 1f);
            drawBakedArt("walker", c, e.animTime * 10f, !e.dead, false,
                    artScale(e, "walker"));
            c.restore();
            return;
        }
        c.save();
        c.translate(e.x + e.w / 2f, e.y + e.h);
        float wide = Math.min(1.7f, 0.7f / Math.max(squashY, 0.3f) + 0.3f);
        c.scale(wide, squashY);

        // shadow
        fill.setColor(0x30000000);
        c.drawOval(rect(-e.w * 0.45f, -6f, e.w * 0.45f, 6f), fill);

        // feet
        if (!e.dead) {
            float step = sinf(e.animTime * 12f) * 6f;
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
        if (e.hitFlash > 0f && !e.dead) {
            fill.setColor(0xCCFFFFFF);
            c.drawOval(rect(e.x, e.y, e.x + e.w, e.y + e.h), fill);
            return;
        }
        if (ArtBindings.has("flyer")) {
            // Baked artwork: centre anchor (bind with --anchor center), a
            // gentle hover wobble and a facing flip keep a static sprite alive.
            c.save();
            c.translate(e.x + e.w / 2f, e.y + e.h / 2f);
            if (!e.dead) {
                c.rotate(sinf(g.worldTime * 3f + e.animTime) * 8f);
                c.scale(e.facing < 0f ? -1f : 1f, 1f);
            }
            drawBakedArt("flyer", c, g.worldTime * 14f + e.animTime * 2f,
                    false, false, artScale(e, "flyer"));
            c.restore();
            return;
        }
        float flap = sinf(g.worldTime * 14f + e.animTime * 2f);
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

        if (ArtBindings.has("player")) {
            // Baked artwork (bound in art/art.json by the designer). The game
            // supplies every motion: facing flip, landing squash, the death
            // tumble and a small airborne lean; the SVG is just the body.
            c.save();
            c.translate(p.x + w / 2f, p.y + h);
            c.scale(sx, 1f);
            float sy = p.squash;
            float sxw = 1f / Math.max(sy, 0.5f) * 0.6f + 0.4f;
            c.scale(sxw, sy);
            if (p.dead) {
                c.rotate(-90f * Math.min(1f,
                        (Constants.RESPAWN_DELAY - p.deadTimer) * 2.2f));
            } else if (!p.onGround) {
                c.rotate(Math.max(-8f, Math.min(8f, p.vy * 0.006f)));
            }
            float artH = ArtBindings.height("player");
            float artScale = artH > 0f ? h / artH : 1f;
            boolean moving = Math.abs(p.vx) > 40f && p.onGround;
            drawBakedArt("player", c, p.runCycle, moving,
                    !p.onGround && !p.dead, artScale);
            c.restore();
        } else {
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
            float swing = sinf(run) * 9f;
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
            float swing = sinf(run) * 9f;
            c.drawRoundRect(rect(-w * 0.40f + swing, -7f, w * 0.0f + swing, -1f), 4f, 4f, fill);
            c.drawRoundRect(rect(w * 0.0f - swing, -7f, w * 0.40f - swing, -1f), 4f, 4f, fill);
        } else {
            c.drawRoundRect(rect(-w * 0.40f, -7f, -w * 0.02f, -1f), 4f, 4f, fill);
            c.drawRoundRect(rect(w * 0.02f, -7f, w * 0.40f, -1f), 4f, 4f, fill);
        }

        // scarf tail (behind body, flutters with speed)
        float flutter = sinf(g.worldTime * 16f) * 5f;
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
        float armSwing = moving ? sinf(run + 3.1415927f) * 8f : (p.onGround ? 0f : -6f);
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
        }

        // invulnerable/landing sparkle
        if (p.landFlash > 0.5f) {
            fill.setColor(Color.argb((int) (120 * (p.landFlash - 0.5f) * 2f), 255, 255, 255));
            c.drawOval(rect(p.x - 6f, p.y - 6f, p.x + p.w + 6f, p.y + p.h + 6f), fill);
        }
    }

    // ------------------------------------------------------------------
    // combat art: swing arcs and projectiles
    // ------------------------------------------------------------------

    /**
     * The active sword arc, drawn in world space. The trail sweep follows the
     * swing's progress so each style reads as one motion, mirrored by facing.
     */
    private void drawSwing(Canvas c, Game g) {
        Player p = g.player;
        if (p.swingTimer <= 0f || p.dead) {
            return;
        }
        float prog = 1f - p.swingTimer / Constants.SWORD_TIME;   // 0..1
        float dir = p.facingRight ? 1f : -1f;
        float cx = p.centerX();
        float cy = p.centerY();

        // Angles are screen-space (y grows down): 0 = forward, -90 = up,
        // +90 = down, 180 = behind. Each style sweeps the arc's leading edge
        // through the space its hitbox covers.
        float start;
        float sweep;
        float arcR;
        float ax;
        float ay;
        switch (p.swingStyle) {
            case GROUND_RUN:
                // long, low forward slash through 1:30 -> 3 -> 4:30
                start = 315f;
                sweep = 95f;
                arcR = Constants.SWORD_REACH_RUN;
                ax = cx + dir * 6f;
                ay = p.y + p.h * 0.60f;
                break;
            case AIR_RISE:
                // upward arc above the head: behind -> up -> forward
                start = 180f;
                sweep = 180f;
                arcR = Constants.SWORD_REACH * 0.95f;
                ax = cx;
                ay = p.y - 6f;
                break;
            case AIR_FALL:
                // plunge below the feet: forward -> down -> behind
                start = 0f;
                sweep = 180f;
                arcR = Constants.SWORD_REACH * 0.95f;
                ax = cx;
                ay = p.y + p.h + 6f;
                break;
            default:
                // standing chop: overhead, sweeping down in front
                start = 270f;
                sweep = 135f;
                arcR = Constants.SWORD_REACH * 0.95f;
                ax = cx + dir * 10f;
                ay = cy + 2f;
                break;
        }
        // mirror for the left-facing swing (reflects 0 <-> 180, up stays up)
        if (dir < 0f) {
            start = 180f - start - sweep;
        }

        // the blade itself, riding the current sweep position
        float bladeAng = start + sweep * Math.min(1f, prog * 1.35f);
        float rad = (float) Math.toRadians(bladeAng);
        float tipX = ax + (float) Math.cos(rad) * arcR;
        float tipY = ay + (float) Math.sin(rad) * arcR;
        float midX = ax + (float) Math.cos(rad) * arcR * 0.55f;
        float midY = ay + (float) Math.sin(rad) * arcR * 0.55f;

        // translucent trail: a filled wedge from the arc's start to the blade
        fill.setColor(0x38FFFFFF);
        float cur = start + sweep * Math.min(1f, prog * 1.35f);
        float span = Math.abs(cur - start);
        c.drawArc(rect(ax - arcR, ay - arcR, ax + arcR, ay + arcR),
                Math.min(start, cur), span, true, fill);
        fill.setColor(0x55EAF2FF);
        c.drawArc(rect(ax - arcR * 0.80f, ay - arcR * 0.80f, ax + arcR * 0.80f, ay + arcR * 0.80f),
                Math.min(start, cur), span * 0.86f, true, fill);

        // blade: a tapered quad from the hand through the arc edge
        float nx = (tipX - ax);
        float ny = (tipY - ay);
        float len = (float) Math.sqrt(nx * nx + ny * ny);
        if (len > 0.001f) {
            nx /= len;
            ny /= len;
        }
        float px = -ny * 4.5f;
        float py = nx * 4.5f;
        fill.setColor(0xFFF4F7FF);
        path.reset();
        path.moveTo(midX - px, midY - py);
        path.lineTo(tipX, tipY);
        path.lineTo(midX + px, midY + py);
        path.close();
        c.drawPath(path, fill);
        // faint streak behind the blade
        stroke.setColor(0x88FFFFFF);
        stroke.setStrokeWidth(3f);
        c.drawLine(midX, midY,
                ax + (tipX - ax) * 0.85f, ay + (tipY - ay) * 0.85f, stroke);
    }

    /** Arrows and boomerangs, culled with the standard visibility helper. */
    private void drawProjectiles(Canvas c, Game g) {
        for (int i = 0; i < g.projectiles.size(); i++) {
            Projectile pr = g.projectiles.get(i);
            if (pr.dead) {
                continue;
            }
            if (!visible(g, pr.x - 40f, pr.y - 40f, 80f, 80f)) {
                continue;
            }
            if (pr.kind == Projectile.ARROW) {
                drawArrow(c, pr);
            } else {
                drawBoomerang(c, pr);
            }
        }
    }

    private void drawArrow(Canvas c, Projectile pr) {
        float vx = pr.vx;
        float vy = pr.vy;
        float ang = (float) Math.toDegrees(Math.atan2(vy, vx));
        c.save();
        c.translate(pr.x, pr.y);
        c.rotate(ang);
        float half = pr.w / 2f;
        // shaft
        fill.setColor(0xFFD8C08A);
        c.drawRoundRect(rect(-half, -2f, half * 0.35f, 2f), 2f, 2f, fill);
        // head
        fill.setColor(0xFFEFF4FF);
        path.reset();
        path.moveTo(half * 0.3f, -4f);
        path.lineTo(half, 0f);
        path.lineTo(half * 0.3f, 4f);
        path.close();
        c.drawPath(path, fill);
        // fletching
        fill.setColor(0xFFE86A5A);
        path.reset();
        path.moveTo(-half, 0f);
        path.lineTo(-half - 8f, -5f);
        path.lineTo(-half * 0.62f, 0f);
        path.lineTo(-half - 8f, 5f);
        path.close();
        c.drawPath(path, fill);
        c.restore();
    }

    private void drawBoomerang(Canvas c, Projectile pr) {
        c.save();
        c.translate(pr.x, pr.y);
        c.rotate((float) Math.toDegrees(pr.phase));
        float s = pr.w / 2f;
        // a soft glow while returning so the homing leg reads
        if (pr.returning) {
            fill.setColor(0x33FFFFFF);
            c.drawCircle(0f, 0f, s * 1.7f, fill);
        }
        fill.setColor(pr.returning ? 0xFFDFFFB4 : 0xFFBFE08A);
        // two thick bent arms (an L rotated 45 degrees)
        c.save();
        c.rotate(45f);
        c.drawRoundRect(rect(-s, -s * 0.32f, s, s * 0.32f), s * 0.3f, s * 0.3f, fill);
        c.drawRoundRect(rect(s * 0.36f, -s, s, s), s * 0.3f, s * 0.3f, fill);
        c.restore();
        stroke.setColor(0x884F6A2E);
        stroke.setStrokeWidth(2f);
        c.save();
        c.rotate(45f);
        c.drawRoundRect(rect(-s, -s * 0.32f, s, s * 0.32f), s * 0.3f, s * 0.3f, stroke);
        c.drawRoundRect(rect(s * 0.36f, -s, s, s), s * 0.3f, s * 0.3f, stroke);
        c.restore();
        c.restore();
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
            float py = top + 6f + fh * t * 0.55f + sinf(wave - t * 3f) * 7f;
            path.lineTo(px, py);
        }
        for (int i = 8; i >= 0; i--) {
            float t = i / 8f;
            float px = x + 4f + fw * t;
            float py = top + 6f + fh * 0.62f + fh * t * 0.55f + sinf(wave - t * 3f) * 7f;
            path.lineTo(px, py);
        }
        path.close();
        c.drawPath(path, fill);
        // star emblem
        fill.setColor(cleared ? 0xFFB8791A : 0xFFFFD34D);
        float scx = x + 4f + fw * 0.5f;
        float scy = top + 6f + fh * 0.55f;
        drawStar(c, scx, scy + sinf(wave - 1.5f) * 7f, 14f, 7f);

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
                        top + T * 0.10f + sinf(wave - t * 3f) * 5f);
            }
            path.lineTo(x, top + T * 0.52f);
            path.close();
            c.drawPath(path, fill);
            if (active) {
                fill.setColor(0x44FFFFFF);
                float pulse = 0.5f + 0.5f * sinf(g.worldTime * 4f);
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
            float a = -1.5707964f + i * 0.62831855f;
            float px = cx + cosf(a) * r;
            float py = cy + sinf(a) * r;
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
        // Gameplay-only: the modal overlays (paused, results, settings) show
        // their own score/coin lines, and on short screens the HUD collided
        // with the big centred titles through the scrim.
        if (g.state != Game.State.PLAYING) {
            return;
        }
        float pad = 18f;
        float scale = Math.min(sw, sh) / 480f;
        float boxH = 78f * scale;
        int coinCount = g.level == null ? 0 : g.level.coinCount();

        // --- score panel ------------------------------------------------
        // Every label below is cached: the HUD used to rebuild "%06d" and the
        // coin string (and re-measure them) on every single frame.
        updateHudLabels(g, scale, coinCount);
        float boxW = Math.max(200f * scale, 32f * scale + scoreW);

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
        c.drawText(scoreStr, pad + 16f * scale, pad + 66f * scale, text);

        // --- coin chip (level coins) ------------------------------------
        float cx0 = pad + boxW + 12f * scale;
        float coinW = Math.max(140f * scale, 62f * scale + coinW0 + 18f * scale);
        fill.setColor(0x99000000);
        c.drawRoundRect(rect(cx0, pad, cx0 + coinW, pad + boxH), 16f, 16f, fill);
        fill.setColor(0x99000000);
        c.drawRoundRect(rect(cx0, pad, cx0 + coinW, pad + boxH), 16f, 16f, stroke);
        float icX = cx0 + 34f * scale;
        float icY = pad + boxH / 2f;
        drawCoinGlyph(c, icX, icY, 20f * scale);
        text.setTextSize(34f * scale);
        text.setColor(0xFFFFE08A);
        text.setShadowLayer(5f, 0f, 3f, 0xCC000000);
        c.drawText(coinStr, cx0 + 62f * scale, pad + boxH / 2f + 13f * scale, text);

        // --- career wallet chip -----------------------------------------
        // "next to the level panel": right of the level/time panel when the
        // screen has room, otherwise tucked under the score cluster.
        float leftEnd = cx0 + coinW;
        float muteLeft = g.controls.muteX - g.controls.muteHitR;
        float cw = 300f * scale;
        float cx = Math.max(sw / 2f, leftEnd + cw / 2f + 14f * scale);
        cx = Math.min(cx, muteLeft - cw / 2f - 14f * scale);
        float walletW = Math.max(128f * scale, 56f * scale + walletW0 + 18f * scale);
        boolean walletInline = cx + cw / 2f + 12f * scale + walletW <= muteLeft - 6f * scale;
        if (walletInline) {
            float wx0 = cx + cw / 2f + 12f * scale;
            drawWalletChip(c, g, wx0, pad, walletW, boxH, scale);
        }

        // --- level + time (top centre) ----------------------------------
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

        // --- lives (hearts) under the score panel -----------------------
        float hy = pad + boxH + 14f * scale;
        int hearts = Math.min(5, Math.max(3, g.lives));
        for (int i = 0; i < hearts; i++) {
            drawHeart(c, pad + 26f * scale + i * 46f * scale, hy + 22f * scale, 20f * scale,
                    i < g.lives ? 0xFFE4453A : 0x55FFFFFF);
        }
        if (g.lives > hearts) {
            text.setTextSize(26f * scale);
            text.setTextAlign(Paint.Align.LEFT);
            text.setColor(0xFFFFFFFF);
            text.setShadowLayer(4f, 0f, 2f, 0xCC000000);
            c.drawText("+" + (g.lives - hearts),
                    pad + 26f * scale + hearts * 46f * scale, hy + 30f * scale, text);
        }

        // --- player HP bar: its own band under the hearts -----------------
        // The left column is a stack of fixed bands so nothing ever overlaps:
        //   panels row | hearts (+overflow) | HP bar | power chips | shield
        //   pips | wallet fallback. All offsets are multiples of `scale` off
        //   `hy`, the hearts-row origin.
        float barY = hy + 50f * scale;
        float barH = 9f * scale;
        float barW = 118f * scale;
        float barX = pad + 16f * scale;
        float frac = 0f;
        if (g.player != null && g.player.maxHp > 0) {
            frac = Math.max(0f, Math.min(1f, (float) g.player.hp / g.player.maxHp));
        }
        fill.setColor(0xB0000000);
        c.drawRoundRect(rect(barX - 1.5f, barY - 1.5f,
                barX + barW + 1.5f, barY + barH + 1.5f), 6f, 6f, fill);
        if (frac > 0f) {
            int hpCol = frac > 0.5f ? 0xFF3DDC84 : (frac > 0.25f ? 0xFFFFC857 : 0xFFE4453A);
            fill.setColor(hpCol);
            c.drawRoundRect(rect(barX, barY, barX + barW * frac, barY + barH),
                    5f, 5f, fill);
        }
        stroke.setColor(0x88FFFFFF);
        stroke.setStrokeWidth(1.6f * scale);
        c.drawRoundRect(rect(barX, barY, barX + barW, barY + barH), 6f, 6f, stroke);

        // --- active power-ups: chips in the next band --------------------
        float chipY = hy + 80f * scale;
        float chipX = pad + 6f * scale;
        chipX = drawPowerChip(c, g, PowerUp.Type.SPEED, g.speedBoostTimer, chipX, chipY, scale);
        chipX = drawPowerChip(c, g, PowerUp.Type.MAGNET, g.magnetTimer, chipX, chipY, scale);
        chipX = drawPowerChip(c, g, PowerUp.Type.DOUBLE_JUMP, g.doubleJumpTimer, chipX, chipY, scale);

        // --- equipped secondary chip (what the small disc fires) ---------
        int weapon = g.secondaryWeaponId();
        if (Weapons.isRanged(weapon)) {
            float wchipH = 24f * scale;
            String wname = Weapons.secondaryName(weapon);
            float wnameW = textWidth(wname, 15f * scale);
            float wchipW = wchipH + wnameW + 20f * scale;
            float wcx = chipX;
            fill.setColor(0x99000000);
            c.drawRoundRect(rect(wcx, chipY - wchipH + 6f * scale,
                    wcx + wchipW, chipY + 6f * scale), 10f, 10f, fill);
            float iconR = wchipH * 0.34f;
            drawSecondaryGlyph(c, weapon, wcx + 6f * scale + iconR,
                    chipY - wchipH / 2f + 6f * scale, iconR);
            text.setTextAlign(Paint.Align.LEFT);
            text.setTextSize(15f * scale);
            text.setColor(0xFFDFFFD4);
            text.setShadowLayer(3f, 0f, 2f, 0xCC000000);
            c.drawText(wname, wcx + 12f * scale + iconR * 2f,
                    chipY - wchipH / 2f + 11f * scale, text);
            text.setTextAlign(Paint.Align.CENTER);
        }

        // --- shield pips: band below the chips ----------------------------
        if (g.shieldCharges > 0) {
            float shY = chipY + 30f * scale;
            int pips = Math.min(6, g.shieldCharges);
            for (int i = 0; i < pips; i++) {
                drawShieldPip(c, pad + 14f * scale + i * 30f * scale, shY, 12f * scale);
            }
        }

        // --- wallet fallback: bottom band of the left column --------------
        // (narrow screens: inline placement beside the level panel did not
        // fit, so the wallet drops below the whole stack instead of fighting
        // the hearts row for space)
        if (!walletInline) {
            drawWalletChip(c, g, pad, hy + 112f * scale,
                    walletW, boxH * 0.62f, scale);
        }

        // --- coin-collector status chip under the level panel -----------
        if (g.mode == Game.Mode.COIN_COLLECTOR && coinCount > 0) {
            boolean locked = g.goalLocked();
            text.setTextSize(24f * scale);
            text.setTextAlign(Paint.Align.CENTER);
            text.setShadowLayer(4f, 0f, 2f, 0xCC000000);
            text.setColor(locked ? 0xFFFF9A3D : 0xFF9BE89B);
            float chipY2 = pad + boxH + 34f * scale;
            fill.setColor(0x99000000);
            float chipW2 = goalW0 + 30f * scale;
            c.drawRoundRect(rect(cx - chipW2 / 2f, chipY2 - 22f * scale,
                    cx + chipW2 / 2f, chipY2 + 10f * scale), 12f, 12f, fill);
            c.drawText(goalStr, cx, chipY2, text);
        }

        // --- invulnerability flash (shield just soaked a hit) -----------
        if (g.invulnTimer > 0f) {
            float k = 0.5f + 0.5f * sinf(g.worldTime * 18f);
            int a = (int) (110 * Math.min(1f, g.invulnTimer / Math.max(0.01f, Constants.INVULN_TIME)) * k);
            stroke.setColor(Color.argb(Math.max(0, a), 150, 220, 255));
            stroke.setStrokeWidth(Math.max(2f, 6f * scale));
            float inset = 5f * scale;
            c.drawRoundRect(rect(inset, inset, Math.max(inset, sw - inset),
                    Math.max(inset, sh - inset)), 18f * scale, 18f * scale, stroke);
        }

        // --- pause + mute buttons ---------------------------------------
        // Drawn at the same radii used for hit tests.
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

    /**
     * Refreshes the HUD label cache. Strings and their measured widths are
     * rebuilt only when the underlying number (or the HUD scale) changes, so
     * a steady frame does no string work and no measureText churn.
     */
    private void updateHudLabels(Game g, float scale, int coinCount) {
        if (lblScale != scale) {
            lblScale = scale;
            scoreKey = Integer.MIN_VALUE;
            coinKey = Integer.MIN_VALUE;
            walletKey = Integer.MIN_VALUE;
            goalKey = Integer.MIN_VALUE;
        }
        if (scoreKey != g.score) {
            scoreKey = g.score;
            scoreStr = pad6(g.score);
            scoreW = textWidth(scoreStr, 38f * scale);
        }
        if (coinKey != g.coinsTaken || coinTotalKey != coinCount) {
            coinKey = g.coinsTaken;
            coinTotalKey = coinCount;
            coinStr = g.coinsTaken + "/" + coinCount;
            coinW0 = textWidth(coinStr, 34f * scale);
        }
        if (walletKey != g.profile.coins) {
            walletKey = g.profile.coins;
            walletStr = Integer.toString(g.profile.coins);
            walletW0 = textWidth(walletStr, 30f * scale);
        }
        if (goalKey != g.coinGoalRequired() || goalLockedKey != g.goalLocked()) {
            goalKey = g.coinGoalRequired();
            goalLockedKey = g.goalLocked();
            goalStr = goalLockedKey
                    ? "GOAL LOCKED — COLLECT " + goalKey + " COINS"
                    : "GOAL OPEN!";
            goalW0 = textWidth(goalStr, 24f * scale);
        }
    }

    private static String pad6(int v) {
        char[] buf = new char[6];
        int n = Math.max(0, Math.min(999999, v));
        for (int i = 5; i >= 0; i--) {
            buf[i] = (char) ('0' + n % 10);
            n /= 10;
        }
        return new String(buf);
    }

    /** Draws a HUD power-up chip when the timer is running; returns the next x. */
    private float drawPowerChip(Canvas c, Game g, PowerUp.Type type, float timer,
                                float x, float y, float scale) {
        if (timer <= 0f) {
            return x;
        }
        int idx = type.ordinal() % 3;
        int tenths = Math.max(0, (int) (timer * 10f));
        if (chipKey[idx] != tenths) {
            chipKey[idx] = tenths;
            chipStr[idx] = tenthLabel(tenths);
        }
        float h = 34f * scale;
        float iconR = 13f * scale;
        float w = 14f * scale + iconR * 2f + 8f * scale + textWidth(chipStr[idx], 24f * scale) + 12f * scale;
        int col = powerColor(type);
        fill.setColor(0xCC101830);
        c.drawRoundRect(rect(x, y - h / 2f, x + w, y + h / 2f), h / 2f, h / 2f, fill);
        stroke.setColor((col & 0x00FFFFFF) | 0x99000000);
        stroke.setStrokeWidth(Math.max(1.5f, 2f * scale));
        c.drawRoundRect(rect(x, y - h / 2f, x + w, y + h / 2f), h / 2f, h / 2f, stroke);
        // mini chip + icon
        float icx = x + 14f * scale + iconR;
        c.save();
        c.translate(icx, y);
        c.scale(1f, 1f);
        fill.setColor(col);
        c.drawCircle(0f, 0f, iconR, fill);
        drawPowerIcon(c, type, iconR * 1.7f);
        c.restore();
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(0xFFFFFFFF);
        text.setShadowLayer(3f, 0f, 2f, 0xCC000000);
        c.drawText(chipStr[idx], icx + iconR + 8f * scale, y + 8f * scale, text);
        return x + w + 10f * scale;
    }

    /** "12.3s" label for a whole number of tenths, built at most once each. */
    private static String tenthLabel(int tenths) {
        int i = Math.min(tenths, TENTH_LABELS.length - 1);
        String s = TENTH_LABELS[i];
        if (s == null) {
            s = (i / 10) + "." + (i % 10) + "s";
            TENTH_LABELS[i] = s;
        }
        return s;
    }

    /**
     * Width of {@code s} at the paint's current text size, with slack for the
     * difference between real font metrics and the stub's
     * {@code length * size * 0.5} approximation: layouts sized on
     * {@code Paint.measureText} alone can clip on a device.
     */
    private float textWidth(String s, float size) {
        if (s == null || s.isEmpty()) {
            return 0f;
        }
        return Math.max(text.measureText(s), s.length() * size * 0.62f);
    }

    /**
     * Largest text size, at most {@code desired}, that keeps {@code s} inside
     * {@code maxW} pixels. Boxed labels go through this so a long string shrinks
     * to fit its button, chip or row instead of spilling over the border: the
     * frame layout only knows the rect, it cannot know the font's advance
     * widths, and real metrics differ from the stub's approximation.
     */
    private float fitSize(String s, float maxW, float desired) {
        float size = Math.max(1f, desired);
        if (s == null || s.isEmpty() || maxW <= 1f) {
            return size;
        }
        text.setTextSize(size);
        float w = textWidth(s, size);
        if (w > maxW) {
            size = Math.max(1f, size * (maxW / w));
            text.setTextSize(size);
        }
        return size;
    }

    /** Centred overlay line that shrinks until it fits {@code maxW}. */
    private void drawCenteredFit(Canvas c, String s, float cx, float y, float maxW, float size) {
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(fitSize(s, maxW, size));
        c.drawText(s, cx, y, text);
    }

    /** Decimal label for a small non-negative number, built at most once each. */
    private static String num(int v) {
        if (v >= 0 && v < NUM_LABELS.length) {
            String s = NUM_LABELS[v];
            if (s == null) {
                s = Integer.toString(v);
                NUM_LABELS[v] = s;
            }
            return s;
        }
        return Integer.toString(v);
    }

    private void drawShieldPip(Canvas c, float cx, float cy, float r) {
        fill.setColor(0xFF4AA8FF);
        path.reset();
        path.moveTo(cx, cy - r);
        path.lineTo(cx + r * 0.82f, cy - r * 0.55f);
        path.lineTo(cx + r * 0.82f, cy + r * 0.1f);
        path.quadTo(cx + r * 0.7f, cy + r * 0.85f, cx, cy + r * 1.1f);
        path.quadTo(cx - r * 0.7f, cy + r * 0.85f, cx - r * 0.82f, cy + r * 0.1f);
        path.lineTo(cx - r * 0.82f, cy - r * 0.55f);
        path.close();
        c.drawPath(path, fill);
        fill.setColor(0x88FFFFFF);
        c.drawCircle(cx - r * 0.2f, cy - r * 0.25f, Math.max(0.5f, r * 0.22f), fill);
    }

    private void drawWalletChip(Canvas c, Game g, float x, float y, float w, float h, float scale) {
        fill.setColor(0x99000000);
        c.drawRoundRect(rect(x, y, x + w, y + h), 16f, 16f, fill);
        stroke.setColor(0x88FFFFFF);
        stroke.setStrokeWidth(2.5f * scale);
        c.drawRoundRect(rect(x, y, x + w, y + h), 16f, 16f, stroke);
        float r = Math.max(4f, h * 0.20f);
        float icx = x + 20f * scale + r;
        drawCoinGlyph(c, icx, y + h / 2f, r);
        float textX = icx + r + 8f * scale;
        float textMax = Math.max(10f, x + w - textX - 8f * scale);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(fitSize(walletStr, textMax, 30f * scale));
        text.setColor(0xFFFFE08A);
        text.setShadowLayer(4f, 0f, 3f, 0xCC000000);
        c.drawText(walletStr, textX, y + h / 2f + 11f * scale, text);
        // tiny "career wallet" hint under the number on tall enough chips
        if (h > 40f * scale) {
            text.setTextSize(fitSize("WALLET", textMax, 15f * scale));
            text.setColor(0xCCBFD4FF);
            text.setShadowLayer(2f, 0f, 1f, 0xCC000000);
            c.drawText("WALLET", textX, y + h / 2f + 27f * scale, text);
        }
    }

    private void drawCoinGlyph(Canvas c, float cx, float cy, float r) {
        fill.setColor(0xFFFFC93C);
        c.drawCircle(cx, cy, r, fill);
        fill.setColor(0xFFFFE58A);
        c.drawCircle(cx - r * 0.25f, cy - r * 0.25f, r * 0.5f, fill);
        fill.setColor(0xFFE8A81C);
        float s = r * 0.2f;
        c.drawRect(rect(cx - s * 0.5f, cy - r * 0.62f, cx + s * 0.5f, cy + r * 0.62f), fill);
        c.drawRect(rect(cx - r * 0.5f, cy - s * 0.5f, cx + r * 0.5f, cy + s * 0.5f), fill);
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

        // Everything is drawn at the hit radius, so a finger can never land on
        // an invisible target (or miss a visible one).
        float pr = ctl.padHitR;
        float padX = g.input.padX;
        float padY = g.input.padY;
        boolean padOn = g.input.padActive;

        // ---- movement pad: outer ring, inner well, knob --------------------
        fill.setColor(paused ? 0x22000000 : 0x44000000);
        c.drawCircle(ctl.padCX, ctl.padCY, pr, fill);
        stroke.setColor(paused ? 0x33FFFFFF : 0x88FFFFFF);
        stroke.setStrokeWidth(3f);
        c.drawCircle(ctl.padCX, ctl.padCY, pr, stroke);
        fill.setColor(paused ? 0x14000000 : 0x22000000);
        c.drawCircle(ctl.padCX, ctl.padCY, ctl.padR * 0.74f, fill);
        stroke.setColor(paused ? 0x22FFFFFF : 0x44FFFFFF);
        stroke.setStrokeWidth(2f);
        c.drawCircle(ctl.padCX, ctl.padCY, ctl.padR * 0.74f, stroke);

        // Direction hints: up doubles as the jump hint, down as the jump-cut
        // hint. They brighten as the thumb approaches them, so the pad teaches
        // its own gestures without a tutorial card.
        float chev = ctl.padR * 0.20f;
        drawPadChevron(c, ctl.padCX, ctl.padCY - ctl.padR * 0.60f, chev, 0f,
                padOn && padY < -0.18f ? 0.95f : 0.34f);
        drawPadChevron(c, ctl.padCX, ctl.padCY + ctl.padR * 0.60f, chev, 180f,
                padOn && padY > 0.18f ? 0.95f : 0.28f);
        drawPadChevron(c, ctl.padCX - ctl.padR * 0.62f, ctl.padCY, chev, 270f,
                padOn && padX < -0.18f ? 0.95f : 0.34f);
        drawPadChevron(c, ctl.padCX + ctl.padR * 0.62f, ctl.padCY, chev, 90f,
                padOn && padX > 0.18f ? 0.95f : 0.34f);

        // the knob follows the thumb, as a fraction of the ring
        float kx = ctl.padCX + padX * ctl.padR * 0.74f;
        float ky = ctl.padCY + padY * ctl.padR * 0.74f;
        fill.setColor(padOn ? 0xBBFFFFFF : 0x55FFFFFF);
        c.drawCircle(kx, ky, ctl.padKnobR, fill);
        stroke.setColor(padOn ? 0xFFFFFFFF : 0x99FFFFFF);
        stroke.setStrokeWidth(2.5f);
        c.drawCircle(kx, ky, ctl.padKnobR, stroke);

        // ---- attack disc (the old jump disc's spot: the sword) -------------
        float ar = ctl.attackHitR;
        boolean swinging = g.player.attacking();
        fill.setColor(paused ? 0x22000000 : 0x44000000);
        c.drawCircle(ctl.attackX, ctl.attackY, ar, fill);
        stroke.setColor(swinging ? 0xFFFFD34D : paused ? 0x33FFFFFF : 0x88FFFFFF);
        stroke.setStrokeWidth(3f);
        c.drawCircle(ctl.attackX, ctl.attackY, ar, stroke);
        drawSwordGlyph(c, ctl.attackX, ctl.attackY, ar * 0.52f, swinging);
        if (g.input.attackHeld) {
            fill.setColor(0x33FFFFFF);
            c.drawCircle(ctl.attackX, ctl.attackY, ar * 0.82f, fill);
        }
        // thin cooldown ring while the next swing is not ready yet
        if (g.player.attackCooldown > 0f && !paused) {
            float frac = Math.min(1f, g.player.attackCooldown / Constants.SWORD_COOLDOWN);
            stroke.setColor(0x88FFD34D);
            stroke.setStrokeWidth(3.5f);
            c.drawArc(rect(ctl.attackX - ar * 0.92f, ctl.attackY - ar * 0.92f,
                    ctl.attackX + ar * 0.92f, ctl.attackY + ar * 0.92f),
                    270f, -360f * frac, false, stroke);
        }

        // ---- secondary weapon disc (bow / boomerang) -----------------------
        int weapon = g.secondaryWeaponId();
        float sr = ctl.secHitR;
        fill.setColor(paused ? 0x22000000
                : weapon == Weapons.NONE ? 0x22000000 : 0x3D000000);
        c.drawCircle(ctl.secX, ctl.secY, sr, fill);
        stroke.setColor(paused ? 0x33FFFFFF
                : weapon == Weapons.NONE ? 0x44FFFFFF : 0x99FFFFFF);
        stroke.setStrokeWidth(2.5f);
        c.drawCircle(ctl.secX, ctl.secY, sr, stroke);
        drawSecondaryGlyph(c, weapon, ctl.secX, ctl.secY, sr * 0.62f);
        if (weapon != Weapons.NONE && g.secondaryCooldown <= 0f && g.input.secondaryHeld) {
            fill.setColor(0x33FFFFFF);
            c.drawCircle(ctl.secX, ctl.secY, sr * 0.82f, fill);
        }
        // cooldown wedge: the disc refills as the next shot becomes ready
        if (weapon != Weapons.NONE && g.secondaryCooldown > 0f && !paused) {
            float cd = Weapons.cooldown(weapon);
            float frac = cd <= 0f ? 0f : Math.min(1f, g.secondaryCooldown / cd);
            stroke.setColor(0xA0FFFFFF);
            stroke.setStrokeWidth(3f);
            c.drawArc(rect(ctl.secX - sr * 0.94f, ctl.secY - sr * 0.94f,
                    ctl.secX + sr * 0.94f, ctl.secY + sr * 0.94f),
                    270f, -360f * frac, false, stroke);
        }
    }

    /** Sword glyph for the attack disc: blade, crossguard and grip. */
    private void drawSwordGlyph(Canvas c, float cx, float cy, float size, boolean active) {
        // grip (lower-left) -> tip (upper-right), angled like a ready swing
        c.save();
        c.translate(cx, cy);
        c.rotate(-38f);
        fill.setColor(active ? 0xFFFFD34D : 0xCCFFFFFF);
        c.drawRoundRect(rect(-size * 0.12f, -size * 1.05f, size * 0.12f, size * 0.30f),
                size * 0.12f, size * 0.12f, fill);
        // crossguard
        fill.setColor(active ? 0xFFE0A62E : 0xFFB98A2C);
        c.drawRoundRect(rect(-size * 0.42f, size * 0.26f, size * 0.42f, size * 0.44f),
                size * 0.10f, size * 0.10f, fill);
        // grip + pommel
        fill.setColor(0xFF7A4A22);
        c.drawRoundRect(rect(-size * 0.09f, size * 0.44f, size * 0.09f, size * 0.82f),
                size * 0.08f, size * 0.08f, fill);
        fill.setColor(0xFFB98A2C);
        c.drawCircle(0f, size * 0.90f, size * 0.13f, fill);
        c.restore();
    }

    /** Glyph for the secondary disc: per equipped weapon, or a disabled dash. */
    private void drawSecondaryGlyph(Canvas c, int weapon, float cx, float cy, float size) {
        switch (weapon) {
            case Weapons.BOW: {
                // bow arc + string + nocked arrow, opening towards the enemy
                stroke.setColor(0xFFE0A62E);
                stroke.setStrokeWidth(size * 0.24f);
                c.drawArc(rect(cx - size, cy - size, cx + size, cy + size),
                        128f, 104f, false, stroke);
                stroke.setColor(0xDDFFFFFF);
                stroke.setStrokeWidth(size * 0.10f);
                float bx = (float) Math.cos(Math.toRadians(128f + 52f)) * size;
                float sx = cx + bx, ex = cx - bx;
                c.drawLine(sx, cy - size * 0.788f, ex, cy + size * 0.788f, stroke);
                fill.setColor(0xFFFFFFFF);
                c.drawCircle(ex + size * 0.02f, cy, size * 0.13f, fill);
                break;
            }
            case Weapons.BOOMERANG: {
                c.save();
                c.translate(cx, cy);
                fill.setColor(0xFFC8E4A0);
                float arm = size * 0.95f;
                float th = size * 0.34f;
                path.reset();
                path.moveTo(-arm * 0.95f, -arm * 0.15f);
                path.lineTo(-arm * 0.15f, -arm * 0.95f);
                path.lineTo(arm * 0.19f, -arm * 0.95f);
                path.lineTo(arm * 0.19f, arm * 0.19f);
                path.lineTo(-arm * 0.15f, arm * 0.19f);
                path.lineTo(-arm * 0.15f, -arm * 0.51f);
                path.lineTo(-arm * 0.95f, -arm * 0.51f);
                path.close();
                c.drawPath(path, fill);
                c.restore();
                break;
            }
            default:
                // NO WEAPON: a short dash so the disc still reads as a slot
                fill.setColor(0x66FFFFFF);
                c.drawRoundRect(rect(cx - size * 0.6f, cy - size * 0.10f,
                        cx + size * 0.6f, cy + size * 0.10f), size * 0.1f, size * 0.1f, fill);
                break;
        }
    }

    /** One triangular direction hint around the pad, {@code deg} clockwise from up. */
    private void drawPadChevron(Canvas c, float cx, float cy, float size, float deg, float alpha) {
        int a = (int) (255 * Math.max(0f, Math.min(1f, alpha)));
        fill.setColor(Color.argb(a, 255, 255, 255));
        path.reset();
        // a chevron pointing in +deg (0 = up, 90 = right, 180 = down, 270 = left)
        float rad = (float) Math.toRadians(deg);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float[][] pts = {{0f, -1.1f}, {0.95f, 0.15f}, {0.45f, 0.15f}, {0f, -0.35f},
                {-0.45f, 0.15f}, {-0.95f, 0.15f}};
        for (int i = 0; i < pts.length; i++) {
            float px = pts[i][0] * size;
            float py = pts[i][1] * size;
            float rx = px * cos - py * sin;
            float ry = px * sin + py * cos;
            if (i == 0) {
                path.moveTo(cx + rx, cy + ry);
            } else {
                path.lineTo(cx + rx, cy + ry);
            }
        }
        path.close();
        c.drawPath(path, fill);
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
                // Stack geometry mirrors Game.layoutPauseButtons so the title
                // sits above the buttons and the hint below them, whatever
                // the screen size.
                float pbh = Math.max(48f, sh * 0.095f);
                float pgap = pbh * 0.35f;
                float ptotal = pbh * g.pauseButtons.length + pgap * (g.pauseButtons.length - 1);
                float ptop = (sh - ptotal) / 2f;
                text.setTextSize(64f * scale);
                text.setColor(0xFFFFFFFF);
                text.setShadowLayer(8f, 0f, 5f, 0xAA000000);
                c.drawText("PAUSED", sw / 2f, Math.max(ptop - 26f * scale, 54f * scale), text);
                text.setShadowLayer(6f, 0f, 4f, 0x99000000);
                for (Game.UiButton b : g.pauseButtons) {
                    fill.setColor(0xFF2F4B8C);
                    c.drawRoundRect(rect(b.x, b.y, b.x + b.w, b.y + b.h), 18f, 18f, fill);
                    fill.setColor(0x33FFFFFF);
                    c.drawRoundRect(rect(b.x, b.y, b.x + b.w, b.y + b.h * 0.5f), 18f, 18f, fill);
                    stroke.setColor(0xAAFFFFFF);
                    stroke.setStrokeWidth(3f);
                    c.drawRoundRect(rect(b.x, b.y, b.x + b.w, b.y + b.h), 18f, 18f, stroke);
                    text.setTextSize(fitSize(b.label, b.w - 26f * scale,
                            Math.max(26f * scale, Math.min(36f * scale, b.h * 0.52f))));
                    text.setColor(0xFFFFFFFF);
                    text.setShadowLayer(4f, 0f, 3f, 0x99000000);
                    c.drawText(b.label, b.x + b.w / 2f, b.y + b.h * 0.66f, text);
                    text.setShadowLayer(6f, 0f, 4f, 0x99000000);
                }
                text.setColor(0xCCFFFFFF);
                text.setShadowLayer(4f, 0f, 3f, 0x99000000);
                drawCenteredFit(c, "R restart  •  back to resume  •  M sound", sw / 2f,
                        Math.min(ptop + ptotal + 32f * scale, sh - 12f * scale),
                        sw * 0.9f, 22f * scale);
                break;
            case LEVEL_CLEARED:
                scrim(c, sw, sh, 0.62f);
                float clrPanelW = Math.min(sw * 0.7f, 760f * scale);
                float clrW = clrPanelW - 56f * scale;
                drawPanel(c, sw / 2f, sh / 2f, clrPanelW, 430f * scale);
                text.setColor(0xFFFFD34D);
                text.setShadowLayer(8f, 0f, 5f, 0xAA000000);
                drawCenteredFit(c, "LEVEL CLEAR!", sw / 2f, sh / 2f - 152f * scale,
                        clrW, 54f * scale);
                text.setColor(0xFFFFFFFF);
                drawCenteredFit(c, "Time  " + g.timeLabel(), sw / 2f,
                        sh / 2f - 92f * scale, clrW, 30f * scale);
                text.setColor(0xFFBFD4FF);
                drawCenteredFit(c, "Coins  " + g.coinsTaken + "/" + g.level.coinCount(),
                        sw / 2f, sh / 2f - 48f * scale, clrW, 30f * scale);
                text.setColor(0xFF9BE89B);
                drawCenteredFit(c, "Time bonus  +" + g.bonusTime, sw / 2f,
                        sh / 2f - 4f * scale, clrW, 30f * scale);
                if (g.bonusCoins > 0) {
                    text.setColor(0xFFFFE08A);
                    drawCenteredFit(c, "All coins  +" + g.bonusCoins, sw / 2f,
                            sh / 2f + 40f * scale, clrW, 30f * scale);
                } else {
                    text.setColor(0xFFBFD4FF);
                    drawCenteredFit(c, "Score  " + g.score, sw / 2f,
                            sh / 2f + 40f * scale, clrW, 30f * scale);
                }
                text.setColor(0xFFFFFFFF);
                drawCenteredFit(c, "TOTAL  " + g.score, sw / 2f, sh / 2f + 92f * scale,
                        clrW, 38f * scale);
                // career wallet line: what this clear banked for the profile
                text.setColor(0xFF8FE3A8);
                text.setShadowLayer(5f, 0f, 3f, 0xAA000000);
                drawCenteredFit(c, "CAREER  +" + g.rewardCoins + " coins   (wallet "
                        + g.profile.coins + ")", sw / 2f, sh / 2f + 134f * scale,
                        clrW, 26f * scale);
                blinkText(c, "TAP TO CONTINUE", sw / 2f, sh / 2f + 188f * scale, 26f * scale, g);
                break;
            case GAME_OVER:
                scrim(c, sw, sh, 0.68f);
                text.setTextAlign(Paint.Align.CENTER);
                // Laid out top-down with gaps larger than every glyph, so no
                // two lines can ever overlap (the old fixed fractions put
                // "TAP TO RETRY" on top of the coins line).
                float oy = Math.max(sh * 0.30f, 110f * scale);
                text.setTextSize(78f * scale);
                text.setColor(0xFFE4453A);
                text.setShadowLayer(10f, 0f, 6f, 0xAA000000);
                c.drawText("GAME OVER", sw / 2f, oy, text);
                oy += 92f * scale;
                text.setColor(0xFFFFFFFF);
                text.setShadowLayer(6f, 0f, 4f, 0x99000000);
                drawCenteredFit(c, "Final score  " + g.score, sw / 2f, oy,
                        sw * 0.86f, 34f * scale);
                oy += 50f * scale;
                text.setColor(0xFFBFD4FF);
                drawCenteredFit(c, "Coins collected  " + g.coinsTaken, sw / 2f, oy,
                        sw * 0.86f, 34f * scale);
                oy += 62f * scale;
                blinkText(c, "TAP TO RETRY  •  " + g.levelLabel(), sw / 2f, oy,
                        fitSize("TAP TO RETRY  •  " + g.levelLabel(), sw * 0.86f,
                                32f * scale), g);
                oy += 46f * scale;
                text.setTextSize(22f * scale);
                text.setColor(0xCCFFFFFF);
                text.setShadowLayer(4f, 0f, 3f, 0x99000000);
                c.drawText("your progress auto-saves at each level", sw / 2f, oy, text);
                break;
            case WIN:
                scrim(c, sw, sh, 0.60f);
                text.setTextAlign(Paint.Align.CENTER);
                // Same top-down layout rule as GAME_OVER.
                float wy = Math.max(sh * 0.28f, 104f * scale);
                text.setTextSize(84f * scale);
                text.setColor(0xFFFFD34D);
                text.setShadowLayer(12f, 0f, 7f, 0xAA000000);
                c.drawText("YOU WIN!", sw / 2f, wy, text);
                wy += 98f * scale;
                text.setColor(0xFFFFFFFF);
                text.setShadowLayer(6f, 0f, 4f, 0x99000000);
                drawCenteredFit(c, "All " + Levels.TEXT.length + " peaks conquered",
                        sw / 2f, wy, sw * 0.86f, 34f * scale);
                wy += 56f * scale;
                text.setColor(0xFF9BE89B);
                drawCenteredFit(c, "Final score  " + g.score, sw / 2f, wy,
                        sw * 0.86f, 34f * scale);
                wy += 68f * scale;
                blinkText(c, "TAP FOR TITLE", sw / 2f, wy, 30f * scale, g);
                break;
            case SETTINGS:
                drawSettings(c, g, sw, sh, scale);
                break;
            case CAREER:
                drawCareer(c, g, sw, sh, scale);
                break;
            case STORE:
                drawStore(c, g, sw, sh, scale);
                break;
            case HELP:
                drawHelp(c, g, sw, sh, scale);
                break;
            case LEVEL_SELECT:
                drawLevelSelect(c, g, sw, sh, scale);
                break;
            case LOADOUT:
                drawLoadout(c, g, sw, sh, scale);
                break;
            case PLAYING:
            default:
                // feedback flash when the player bumps a locked goal
                if (g.goalLockedTimer > 0f && g.mode == Game.Mode.COIN_COLLECTOR) {
                    // cached: this is the PLAYING path and must not build strings
                    int need = Math.max(1, g.coinGoalRequired() - g.level.coinsTakenFast());
                    if (need != needKey) {
                        needKey = need;
                        needStr = "GOAL LOCKED — NEED " + need + " MORE COINS";
                    }
                    text.setTextAlign(Paint.Align.CENTER);
                    text.setTextSize(30f * scale);
                    text.setColor(0xFFFF9A3D);
                    text.setShadowLayer(6f, 0f, 4f, 0xAA000000);
                    c.drawText(needStr, sw / 2f, sh * 0.2f, text);
                }
                break;
        }
    }

    private void drawMenu(Canvas c, Game g, float sw, float sh, float scale) {
        scrim(c, sw, sh, 0.45f);
        text.setTextAlign(Paint.Align.CENTER);

        float bob = sinf(g.worldTime * 2f) * 6f;
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

        // active career badge (top-left, clear of the centred title)
        drawCareerBadge(c, g, sw, scale);

        // side art, pushed to the edges so it never fights the buttons
        float y = sh * 0.52f;
        drawMenuHero(c, sw * 0.13f, y, scale * 0.9f, g);
        drawMenuEnemy(c, sw * 0.87f, y, scale * 0.9f, g);
        drawMenuCoin(c, sw * 0.87f - 130f * scale, y - 70f * scale, scale * 0.8f, g);

        // Every menu button is drawn from the rect Game laid out; styling only
        // depends on which group the id belongs to, so drawing and hit testing
        // can never disagree.
        for (Game.UiButton b : g.menuButtons) {
            boolean small = b.id == Game.BTN_SOUND || b.id == Game.BTN_RESET;
            boolean row = b.id == Game.BTN_CAREER || b.id == Game.BTN_SETTINGS;
            drawMenuButton(c, b.label, b.x, b.y, b.w, b.h, scale, small, row);
        }

        String hint = "auto-saves the level you reach  •  losing all lives returns here";
        float smallTop = sh;
        for (Game.UiButton b : g.menuButtons) {
            if (b.id == Game.BTN_SOUND || b.id == Game.BTN_RESET) {
                smallTop = Math.min(smallTop, b.y);
            }
        }
        float hintY = Math.min(sh * 0.885f, smallTop - 14f * scale);
        text.setTextSize(fitSize(hint, sw * 0.92f, 20f * scale));
        text.setColor(0xCCFFFFFF);
        text.setShadowLayer(4f, 0f, 3f, 0x99000000);
        c.drawText(hint, sw / 2f, hintY, text);
    }

    /** One home-screen button; {@code small}/{@code row} pick the styling group. */
    private void drawMenuButton(Canvas c, String label, float x, float y, float w, float h,
                                float scale, boolean small, boolean row) {
        float r = Math.max(2f, (small ? 12f : 18f) * Math.max(scale, 0.4f));
        fill.setColor(small ? 0x99203A66 : row ? 0xE61E3A6E : 0xF21E3A6E);
        c.drawRoundRect(rect(x, y, x + w, y + h), r, r, fill);
        stroke.setColor(small ? 0x66FFFFFF : 0xAAFFFFFF);
        stroke.setStrokeWidth(Math.max(1f, (small ? 2f : 3f) * Math.max(scale, 0.4f)));
        c.drawRoundRect(rect(x, y, x + w, y + h), r, r, stroke);
        if (!small) {
            fill.setColor(0x2EFFFFFF);
            c.drawRoundRect(rect(x, y, x + w, y + h * 0.5f), r, r, fill);
        }
        float ts = small ? Math.max(16f, 20f * scale)
                : row ? Math.max(20f, 26f * scale) : Math.max(24f, 30f * scale);
        ts = Math.min(ts, h * 0.52f);
        // never let a long label run outside its own button
        ts = fitSize(label, w - 24f * scale, ts);
        text.setTextSize(Math.max(0f, ts));
        text.setColor(small ? 0xCCFFFFFF : 0xFFFFFFFF);
        text.setShadowLayer(small ? 3f : 4f, 0f, 3f, 0x99000000);
        c.drawText(label == null ? "" : label, x + w / 2f, y + h * 0.68f, text);
    }

    /** Active-career line: avatar tint, name, wallet and continue level. */
    private void drawCareerBadge(Canvas c, Game g, float sw, float scale) {
        Profile p = g.profile;
        if (p == null) {
            return;
        }
        boolean compact = sw < 760f;
        float pad = Math.max(5f, 12f * scale);
        float h = Math.max(20f, 44f * scale);
        float dot = h * 0.36f;
        float x = pad;
        float y = pad;
        text.setTextSize(Math.max(6f, 22f * scale));
        int key = (p.name.hashCode() * 31 + p.coins) * 31 + p.levelIndex;
        if (badgeKey != key || badgeScale != scale) {
            badgeKey = key;
            badgeScale = scale;
            badgeNameW = compact ? 0f : textWidth(p.name, Math.max(6f, 22f * scale));
            badgeCoinsW = textWidth(num(p.coins), Math.max(6f, 20f * scale));
            badgeLvlW = textWidth("LVL " + (p.levelIndex + 1), Math.max(6f, 20f * scale));
        }
        float gap = Math.max(3f, 10f * scale);
        float cluster = dot * 1.6f + gap + badgeCoinsW + gap + badgeLvlW;
        float nameW = compact ? 0f : badgeNameW;
        float w = pad * 2f + dot * 2f + gap + nameW + (compact ? 0f : gap) + cluster;
        // Keep the badge clear of the title: the title is centred and huge, so
        // measure it instead of guessing. A long name shrinks first, and is
        // dropped only when the space left cannot carry it legibly.
        text.setTextSize(88f * scale);
        float maxBadgeW = Math.min(sw * 0.38f,
                sw / 2f - textWidth("ANPLOMER", 88f * scale) / 2f - 18f * scale);
        if (maxBadgeW < pad * 2f + dot * 2f + gap + cluster) {
            // no room beside the title (small phone, landscape): the title wins
            return;
        }
        if (!compact && w > maxBadgeW) {
            float room = maxBadgeW - (pad * 2f + dot * 2f + gap + cluster + gap);
            if (room > 34f * scale) {
                badgeNameW = room;
                nameW = room;
            } else {
                compact = true;
                nameW = 0f;
            }
            w = pad * 2f + dot * 2f + gap + (compact ? 0f : nameW + gap) + cluster;
        }
        if (w > maxBadgeW) {
            w = Math.max(dot * 4f + pad * 2f, maxBadgeW);
        }
        fill.setColor(0xD9151F38);
        c.drawRoundRect(rect(x, y, x + w, y + h), h / 2f, h / 2f, fill);
        stroke.setColor(0x77FFFFFF);
        stroke.setStrokeWidth(Math.max(1f, 2f * scale));
        c.drawRoundRect(rect(x, y, x + w, y + h), h / 2f, h / 2f, stroke);
        // avatar tint dot
        float ax = x + pad + dot;
        fill.setColor(avatarColor(p.colorIndex));
        c.drawCircle(ax, y + h / 2f, dot, fill);
        stroke.setColor(0xAAFFFFFF);
        stroke.setStrokeWidth(Math.max(1f, 2f * scale));
        c.drawCircle(ax, y + h / 2f, dot, stroke);
        float tx = ax + dot + gap;
        text.setTextAlign(Paint.Align.LEFT);
        text.setShadowLayer(3f, 0f, 2f, 0xCC000000);
        if (!compact && nameW > 0f) {
            text.setTextSize(fitSize(p.name, nameW, Math.max(6f, 22f * scale)));
            text.setColor(0xFFFFFFFF);
            c.drawText(p.name, tx, y + h / 2f + 8f * scale, text);
            tx += nameW + gap;
        }
        // coin wallet
        float cr = dot * 0.8f;
        drawCoinGlyph(c, tx + cr, y + h / 2f, cr);
        tx += cr * 2f + gap;
        text.setTextSize(fitSize(num(p.coins), Math.max(16f, x + w - tx - 30f * scale),
                Math.max(6f, 20f * scale)));
        text.setColor(0xFFFFE08A);
        c.drawText(num(p.coins), tx, y + h / 2f + 7f * scale, text);
        tx += badgeCoinsW + gap;
        text.setColor(0xFFBFD4FF);
        String lvlStr = "LVL " + (p.levelIndex + 1);
        text.setTextSize(fitSize(lvlStr, Math.max(20f, x + w - tx - 6f * scale),
                Math.max(6f, 20f * scale)));
        c.drawText(lvlStr, tx, y + h / 2f + 7f * scale, text);
        text.setTextAlign(Paint.Align.CENTER);
    }

    // ------------------------------------------------------------------
    // career + store screens (rects and panel sizes come from Game)
    // ------------------------------------------------------------------

    private void drawCareer(Canvas c, Game g, float sw, float sh, float scale) {
        scrim(c, sw, sh, 0.66f);
        float pw = g.careerPanelW();
        float ph = g.careerPanelH();
        drawPanel(c, sw / 2f, sh / 2f, pw, ph);
        float left = (sw - pw) / 2f;
        float top = (sh - ph) / 2f;

        // Header sits in the band above the rows Game laid out (rowsTop is
        // top + panelH * 0.16), so nothing here can overlap a row.
        float headerY = top + Math.min(52f * scale, ph * 0.075f);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(Math.max(8f, 38f * scale));
        text.setColor(0xFFFFD34D);
        text.setShadowLayer(7f, 0f, 4f, 0xAA000000);
        c.drawText("CAREERS", left + pw * 0.06f, headerY, text);
        text.setTextAlign(Paint.Align.RIGHT);
        String countStr = g.profiles.size()
                + (g.profiles.size() == 1 ? " CAREER" : " CAREERS");
        text.setTextSize(fitSize(countStr, pw * 0.40f, Math.max(6f, 20f * scale)));
        text.setColor(0xFFBFD4FF);
        text.setShadowLayer(3f, 0f, 2f, 0x99000000);
        c.drawText(countStr,
                left + pw * 0.94f, headerY, text);
        text.setTextAlign(Paint.Align.CENTER);

        // profile rows: one per Game.careerButtons BTN_PROFILE_ROW rect
        float rowsBottom = top + ph * 0.16f;
        for (Game.UiButton b : g.careerButtons) {
            int id = b.id - Game.BTN_PROFILE_ROW;
            if (id < 0 || id >= g.profiles.size() || b.id >= Game.BTN_UPGRADE_ROW) {
                continue;
            }
            drawCareerRow(c, g.profiles.get(id), id == g.activeProfile, b, scale);
            rowsBottom = Math.max(rowsBottom, b.y + b.h);
        }
        if (g.careerRowCount() == 0) {
            // Game has not laid the rows out (state forced without the tap flow):
            // still say something useful instead of an empty panel.
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(Math.max(6f, 24f * scale));
            text.setColor(0xCCFFFFFF);
            c.drawText(g.profiles.isEmpty() ? "no careers yet" : "careers load on open",
                    sw / 2f, top + ph * 0.5f, text);
        }

        // NEW / CONFIRM / BACK
        for (Game.UiButton b : g.careerButtons) {
            if (b.id < Game.BTN_PROFILE_ROW) {
                drawOverlayButton(c, b, scale, b.id == Game.BTN_DELETE_PROFILE);
            }
        }

        float cMsgY = Math.max(rowsBottom + 26f * scale,
                footerTop(g.careerButtons, top + ph) - 10f * scale);
        drawMessage(c, g.careerMsg, g.careerMsgTimer, sw / 2f,
                Math.min(cMsgY, footerTop(g.careerButtons, top + ph) - 4f), scale);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(Math.max(5f, 17f * scale));
        text.setColor(0x99FFFFFF);
        text.setShadowLayer(3f, 0f, 2f, 0x99000000);
        String foot = "each career keeps its own coins, upgrades and progress";
        float footY = Math.min(footerBottom(g.careerButtons, top + ph) + 18f * scale,
                top + ph - 10f * scale);
        footY = Math.min(footY, sh - 8f * scale);
        text.setTextSize(fitSize(foot, pw * 0.94f, Math.max(5f, 17f * scale)));
        c.drawText(foot, sw / 2f, footY, text);
    }

    private void drawCareerRow(Canvas c, Profile p, boolean active, Game.UiButton b, float scale) {
        float r = Math.max(2f, 14f * scale);
        fill.setColor(active ? 0xE62C5AA8 : 0xB4101826);
        c.drawRoundRect(rect(b.x, b.y, b.x + b.w, b.y + b.h), r, r, fill);
        stroke.setColor(active ? 0xFFFFD34D : 0x55FFFFFF);
        stroke.setStrokeWidth(Math.max(1f, (active ? 3f : 2f) * scale));
        c.drawRoundRect(rect(b.x, b.y, b.x + b.w, b.y + b.h), r, r, stroke);

        float dot = Math.min(b.h * 0.28f, 22f * scale);
        float ax = b.x + dot * 2.2f;
        fill.setColor(avatarColor(p.colorIndex));
        c.drawCircle(ax, b.y + b.h / 2f, Math.max(1f, dot), fill);
        stroke.setColor(0xAAFFFFFF);
        stroke.setStrokeWidth(Math.max(1f, 1.5f * scale));
        c.drawCircle(ax, b.y + b.h / 2f, Math.max(1f, dot), stroke);

        float tx = ax + dot * 1.6f;
        float nameSize = Math.min(b.h * 0.34f, 30f * scale);
        float subSize = Math.min(b.h * 0.26f, 20f * scale);
        // reserve the right-hand BEST/ACTIVE column so a long name cannot
        // run underneath it
        float bestSize = Math.max(4f, subSize);
        float rightW = textWidth("BEST " + p.bestScore, bestSize) + 22f * scale;
        if (active) {
            rightW = Math.max(rightW,
                    textWidth("ACTIVE", Math.max(4f, subSize * 0.92f)) + 22f * scale);
        }
        float nameMax = Math.max(24f, b.w - (tx - b.x) - rightW - 12f * scale);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(0xFFFFFFFF);
        text.setShadowLayer(3f, 0f, 2f, 0xCC000000);
        text.setTextSize(fitSize(p.name, nameMax, Math.max(5f, nameSize)));
        c.drawText(p.name, tx, b.y + b.h * (b.h > 34f ? 0.44f : 0.62f), text);
        if (b.h > 34f) {
            String sub = "LEVEL " + (p.levelIndex + 1) + "  •  COINS " + p.coins
                    + "  •  " + Weapons.secondaryName(p.equippedSecondary);
            text.setTextSize(fitSize(sub, nameMax, Math.max(4f, subSize)));
            text.setColor(0xFFBFD4FF);
            c.drawText(sub, tx, b.y + b.h * 0.78f, text);
        }
        // right side: best score / active marker
        text.setTextAlign(Paint.Align.RIGHT);
        text.setTextSize(Math.max(4f, subSize));
        text.setColor(0xFFFFE08A);
        c.drawText("BEST " + p.bestScore, b.x + b.w - 18f * scale, b.y + b.h * 0.62f, text);
        if (active) {
            text.setTextSize(Math.max(4f, subSize * 0.92f));
            text.setColor(0xFF9BE89B);
            c.drawText("ACTIVE", b.x + b.w - 18f * scale, b.y + b.h * 0.34f, text);
        }
        text.setTextAlign(Paint.Align.CENTER);
    }

    private void drawStore(Canvas c, Game g, float sw, float sh, float scale) {
        scrim(c, sw, sh, 0.66f);
        float pw = g.storePanelW();
        float ph = g.storePanelH();
        float left = (sw - pw) / 2f;
        float top = (sh - ph) / 2f;
        drawPanel(c, sw / 2f, sh / 2f, pw, ph);

        // Header band above the rows (rowsTop is top + panelH * 0.17):
        // title on the left, wallet on the right.
        float headerY = top + Math.min(52f * scale, ph * 0.075f);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(Math.max(8f, 38f * scale));
        text.setColor(0xFFFFD34D);
        text.setShadowLayer(7f, 0f, 4f, 0xAA000000);
        c.drawText("STORE", left + pw * 0.06f, headerY, text);

        String walletLine = "WALLET " + num(g.profile.coins);
        float wSize = fitSize(walletLine, pw * 0.40f, Math.max(6f, 24f * scale));
        float walletTextW = textWidth(walletLine, wSize);
        float wRight = left + pw * 0.94f;
        float wGlyphR = Math.max(4f, 12f * scale);
        drawCoinGlyph(c, wRight - walletTextW - 12f * scale - wGlyphR, headerY - 9f * scale, wGlyphR);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setTextSize(wSize);
        text.setColor(0xFFFFE08A);
        text.setShadowLayer(3f, 0f, 3f, 0xCC000000);
        c.drawText(walletLine, wRight, headerY, text);
        text.setTextAlign(Paint.Align.CENTER);

        float rowsBottom = top + ph * 0.17f;
        for (Game.UiButton b : g.storeButtons) {
            int id = b.id - Game.BTN_UPGRADE_ROW;
            if (id >= 0 && id < Upgrades.COUNT && b.id >= Game.BTN_UPGRADE_ROW) {
                drawStoreRow(c, g, id, b, scale);
                rowsBottom = Math.max(rowsBottom, b.y + b.h);
            }
        }
        if (g.storeButtons.isEmpty()) {
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(Math.max(6f, 24f * scale));
            text.setColor(0xCCFFFFFF);
            c.drawText("upgrades load on open", sw / 2f, top + ph * 0.55f, text);
        }
        for (Game.UiButton b : g.storeButtons) {
            if (b.id == Game.BTN_BACK) {
                drawOverlayButton(c, b, scale, false);
            }
        }

        float sMsgY = Math.max(rowsBottom + 26f * scale,
                footerTop(g.storeButtons, top + ph) - 10f * scale);
        drawMessage(c, g.storeMsg, g.storeMsgTimer, sw / 2f,
                Math.min(sMsgY, footerTop(g.storeButtons, top + ph) - 4f), scale);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(Math.max(5f, 17f * scale));
        text.setColor(0x99FFFFFF);
        text.setShadowLayer(3f, 0f, 2f, 0x99000000);
        String foot = "upgrades are permanent and shared by this career only";
        float footY = Math.min(footerBottom(g.storeButtons, top + ph) + 18f * scale,
                top + ph - 10f * scale);
        footY = Math.min(footY, sh - 8f * scale);
        text.setTextSize(fitSize(foot, pw * 0.94f, Math.max(5f, 17f * scale)));
        c.drawText(foot, sw / 2f, footY, text);
    }

    private void drawStoreRow(Canvas c, Game g, int id, Game.UiButton b, float scale) {
        int level = g.profile.upgradeLevel(id);
        boolean maxed = level >= Upgrades.MAX[id];
        boolean afford = !maxed && g.profile.canAfford(id);
        // cache the row's strings: they only change when the level changes
        if (storeKey[id] != level) {
            storeKey[id] = level;
            storeEff[id] = maxed ? "MAX LEVEL" : Upgrades.effect(id, level + 1);
            storePrice[id] = maxed ? "MAX" : Integer.toString(g.profile.nextPrice(id));
        }
        float r = Math.max(2f, 14f * scale);
        fill.setColor(afford ? 0xD9203A66 : 0x99101826);
        c.drawRoundRect(rect(b.x, b.y, b.x + b.w, b.y + b.h), r, r, fill);
        stroke.setColor(afford ? 0xFF9BE89B : 0x44FFFFFF);
        stroke.setStrokeWidth(Math.max(1f, 2f * scale));
        c.drawRoundRect(rect(b.x, b.y, b.x + b.w, b.y + b.h), r, r, stroke);

        text.setTextAlign(Paint.Align.LEFT);
        float nameSize = Math.min(b.h * 0.30f, 26f * scale);
        float effSize = Math.max(4f, Math.min(b.h * 0.22f, 18f * scale));
        // reserve the right-hand pips/price column before sizing the text
        float rightBlock = Math.max(Upgrades.MAX[id] * (Math.max(2f, Math.min(b.h * 0.14f, 11f * scale)) * 2.1f),
                maxed ? textWidth("MAX", effSize) : textWidth(storePrice[id], effSize) * 1.1f);
        float leftMax = Math.max(30f, b.w - 16f * scale - rightBlock - 26f * scale);
        text.setColor(maxed ? 0xFFBFD4FF : afford ? 0xFFFFFFFF : 0x99FFFFFF);
        text.setShadowLayer(3f, 0f, 2f, 0xCC000000);
        text.setTextSize(fitSize(Upgrades.NAME[id], leftMax, Math.max(5f, nameSize)));
        c.drawText(Upgrades.NAME[id], b.x + 16f * scale, b.y + b.h * 0.44f, text);
        text.setTextSize(fitSize(storeEff[id], leftMax, effSize));
        text.setColor(afford ? 0xFFBFD4FF : 0x88FFFFFF);
        c.drawText(storeEff[id], b.x + 16f * scale, b.y + b.h * 0.80f, text);

        // level pips: filled squares for owned levels
        float pipS = Math.max(2f, Math.min(b.h * 0.14f, 11f * scale));
        float pipsW = Upgrades.MAX[id] * (pipS * 2.1f);
        float px = b.x + b.w - 18f * scale - pipsW;
        for (int i = 0; i < Upgrades.MAX[id]; i++) {
            fill.setColor(i < level ? 0xFFFFD34D : 0x44FFFFFF);
            c.drawRoundRect(rect(px + i * pipS * 2.1f, b.y + b.h * 0.30f,
                    px + i * pipS * 2.1f + pipS, b.y + b.h * 0.30f + pipS), 2f, 2f, fill);
        }
        float priceSize = Math.max(5f, Math.min(b.h * 0.28f, 24f * scale));
        String price = maxed ? "MAX" : storePrice[id];
        float priceW = textWidth(price, priceSize);
        float pRight = b.x + b.w - 18f * scale;
        if (!maxed) {
            drawCoinGlyph(c, pRight - priceW - 10f * scale, b.y + b.h * 0.64f,
                    Math.max(2f, priceSize * 0.44f));
        }
        text.setTextAlign(Paint.Align.RIGHT);
        text.setTextSize(priceSize);
        text.setColor(maxed ? 0xFF9BE89B : afford ? 0xFFFFE08A : 0x88FFFFFF);
        c.drawText(price, pRight, b.y + b.h * 0.74f, text);
        text.setTextAlign(Paint.Align.CENTER);
    }

    /** Generic footer button for the career/store screens (Back/New/Delete). */
    private void drawOverlayButton(Canvas c, Game.UiButton b, float scale, boolean danger) {
        float r = Math.max(2f, 14f * scale);
        fill.setColor(danger ? 0xE68C2C2C : 0xF21E3A6E);
        c.drawRoundRect(rect(b.x, b.y, b.x + b.w, b.y + b.h), r, r, fill);
        stroke.setColor(danger ? 0xFFFF9A8A : 0xAAFFFFFF);
        stroke.setStrokeWidth(Math.max(1f, 3f * scale));
        c.drawRoundRect(rect(b.x, b.y, b.x + b.w, b.y + b.h), r, r, stroke);
        String label = b.label == null ? "" : b.label;
        float ts = fitSize(label, b.w - 24f * scale,
                Math.min(Math.max(18f, 26f * scale), b.h * 0.5f));
        text.setTextSize(Math.max(0f, ts));
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(0xFFFFFFFF);
        text.setShadowLayer(3f, 0f, 2f, 0x99000000);
        c.drawText(label, b.x + b.w / 2f, b.y + b.h * 0.68f, text);
    }

    /** Transient confirmation line (store purchase / career delete). */
    private void drawMessage(Canvas c, String msg, float timer, float cx, float y, float scale) {
        if (timer <= 0f || msg == null || msg.isEmpty()) {
            return;
        }
        float a = Math.min(1f, timer * 2.5f);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(Math.max(6f, 22f * scale));
        text.setColor(Color.argb((int) (255 * a), 155, 232, 155));
        text.setShadowLayer(4f, 0f, 3f, 0xAA000000);
        c.drawText(msg, cx, y, text);
    }

    /** Top edge of the footer button row (Back / New / Delete), or a fallback. */
    private static float footerTop(java.util.List<Game.UiButton> buttons, float fallback) {
        float top = Float.MAX_VALUE;
        for (Game.UiButton b : buttons) {
            if (b.id == Game.BTN_BACK || b.id == Game.BTN_NEW_PROFILE
                    || b.id == Game.BTN_DELETE_PROFILE) {
                top = Math.min(top, b.y);
            }
        }
        return top == Float.MAX_VALUE ? fallback : top;
    }

    /** Bottom edge of the footer button row (Back / New / Delete), or a fallback. */
    private static float footerBottom(java.util.List<Game.UiButton> buttons, float fallback) {
        float bottom = -Float.MAX_VALUE;
        for (Game.UiButton b : buttons) {
            if (b.id == Game.BTN_BACK || b.id == Game.BTN_NEW_PROFILE
                    || b.id == Game.BTN_DELETE_PROFILE) {
                bottom = Math.max(bottom, b.y + b.h);
            }
        }
        return bottom == -Float.MAX_VALUE ? fallback : bottom;
    }

    private static int avatarColor(int colorIndex) {
        int i = Math.floorMod(colorIndex, AVATAR.length);
        return AVATAR[i];
    }

    private void drawMenuHero(Canvas c, float x, float y, float scale, Game g) {
        c.save();
        c.translate(x, y + sinf(g.worldTime * 3f) * 6f);
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
        path.lineTo(-34f, -6f + sinf(g.worldTime * 6f) * 5f);
        path.lineTo(-6f, 2f);
        path.close();
        c.drawPath(path, fill);
        c.restore();
    }

    private void drawMenuEnemy(Canvas c, float x, float y, float scale, Game g) {
        c.save();
        c.translate(x, y + sinf(g.worldTime * 3f + 1f) * 6f);
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
        c.translate(x, y + sinf(g.worldTime * 3f + 2f) * 6f);
        float spin = Math.max(0.2f, Math.abs(cosf(g.worldTime * 3f)));
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

    /** Rows of the in-game tutorial: label + one-line explanation. */
    private static final String[][] HELP_ROWS = {
            {"MOVE", "drag the left pad  /  A  D"},
            {"JUMP", "drag the pad up"},
            {"SHORT HOP", "drag the pad down mid-jump"},
            {"ATTACK", "right disc / J swings the sword"},
            {"SWORD ARCS", "arc changes as you move"},
            {"  STAND", "tall overhead chop"},
            {"  RUN", "longer, lower forward slash"},
            {"  RISE / FALL", "upward arc  /  plunge below"},
            {"SECONDARY", "left disc / K: bow or boomerang"},
            {"LOADOUT", "pick a level, then a weapon"},
            {"PAUSE", "pause button  /  P"},
            {"COINS", "+100 score, +1 career coin"},
            {"ENEMIES", "sword, arrows or a stomp kill"},
            {"HAZARDS", "spikes, lava and pits cost a life"},
            {"CHECKPOINT", "a flag saves your respawn"},
            {"GOAL", "reach the flag to clear the level"},
            {"CAREERS", "own wallet, unlocks and build"},
            {"STORE", "inside the career menu"},
            {"COLLECTOR", "60% of the coins opens the goal"},
            {"AUTO-SAVE", "progress saves as you reach it"},
            {"KEYBOARD", "W A S D, J K, space, P, M"},
    };

    /** Power-up legend shown at the bottom of the tutorial. */
    private static final PowerUp.Type[] HELP_POWERS = {
            PowerUp.Type.SPEED, PowerUp.Type.SHIELD, PowerUp.Type.MAGNET,
            PowerUp.Type.DOUBLE_JUMP, PowerUp.Type.EXTRA_LIFE
    };

    /** The "HOW TO PLAY" overlay: one screen, every component explained. */
    private void drawHelp(Canvas c, Game g, float sw, float sh, float scale) {
        scrim(c, sw, sh, 0.72f);
        float pw = g.helpPanelW();
        float ph = g.helpPanelH();
        float left = (sw - pw) / 2f;
        float top = (sh - ph) / 2f;
        drawPanel(c, sw / 2f, sh / 2f, pw, ph);

        text.setShadowLayer(7f, 0f, 4f, 0xAA000000);
        text.setColor(0xFFFFD34D);
        drawCenteredFit(c, "HOW TO PLAY", sw / 2f, top + Math.min(56f * scale, ph * 0.11f),
                pw - 48f * scale, 40f * scale);
        text.setShadowLayer(3f, 0f, 2f, 0x99000000);
        text.setColor(0xCCBFD4FF);
        drawCenteredFit(c, "everything in one screen  •  tap BACK when you are done",
                sw / 2f, top + Math.min(88f * scale, ph * 0.175f),
                pw - 48f * scale, 17f * scale);

        Game.UiButton back = g.helpButtons.isEmpty() ? null : g.helpButtons.get(0);
        float backTop = back == null ? top + ph - 60f * scale : back.y;
        boolean twoCol = pw >= ph * 1.15f;
        int rows = HELP_ROWS.length;
        int perCol = twoCol ? (rows + 1) / 2 : rows;
        int cols = twoCol ? 2 : 1;
        float contentTop = top + Math.min(112f * scale, ph * 0.22f);
        // Reserve room for the power-up legend when the panel is tall enough.
        // The height is built from its parts so the caption can never land on
        // the BACK button.
        float step = Math.min(96f * scale, (pw - 80f * scale) / HELP_POWERS.length);
        float iconR = Math.min(14f * scale, step * 0.18f);
        float letterSize = Math.max(6f, Math.min(16f * scale, step * 0.22f));
        float capSize = Math.max(5f, Math.min(14f * scale, step * 0.18f));
        float strip = 2f * iconR + letterSize + capSize + 16f * scale;
        boolean showStrip = ph > 380f * scale;
        float contentBottom = backTop - (showStrip ? strip : 0f) - 10f * scale;
        float rowH = Math.max(12f, Math.min(48f * scale,
                (contentBottom - contentTop) / Math.max(1, perCol)));
        float colW = (pw - 56f * scale) / cols;
        float x0 = left + 28f * scale;
        float labelW = colW * 0.34f;
        for (int i = 0; i < rows; i++) {
            int col = twoCol ? i / perCol : 0;
            int row = twoCol ? i % perCol : i;
            float x = x0 + col * colW;
            float y = contentTop + (row + 1) * rowH;
            float size = Math.min(22f * scale, rowH * 0.56f);
            String label = HELP_ROWS[i][0];
            text.setTextAlign(Paint.Align.LEFT);
            text.setTextSize(fitSize(label, labelW - 8f * scale, size));
            text.setColor(0xFFFFE08A);
            text.setShadowLayer(3f, 0f, 2f, 0xCC000000);
            c.drawText(label, x, y, text);
            String value = HELP_ROWS[i][1];
            text.setTextSize(fitSize(value, colW - labelW - 6f * scale, size));
            text.setColor(0xFFFFFFFF);
            c.drawText(value, x + labelW, y, text);
        }

        // power-up legend: icon + glyph letter for each of the five pickups
        if (showStrip) {
            float stripTop = contentBottom + 6f * scale;
            float iconY = stripTop + iconR;
            float letterBase = stripTop + 2f * iconR + letterSize + 2f * scale;
            float capBase = letterBase + capSize + 4f * scale;
            for (int i = 0; i < HELP_POWERS.length; i++) {
                float cx = sw / 2f + (i - (HELP_POWERS.length - 1) / 2f) * step;
                c.save();
                c.translate(cx, iconY);
                fill.setColor(0xFF1B2A48);
                c.drawCircle(0f, 0f, iconR, fill);
                stroke.setColor(0x66FFFFFF);
                stroke.setStrokeWidth(Math.max(1f, 1.4f * scale));
                c.drawCircle(0f, 0f, iconR, stroke);
                drawPowerIcon(c, HELP_POWERS[i], iconR * 1.7f);
                c.restore();
                text.setTextAlign(Paint.Align.CENTER);
                text.setTextSize(letterSize);
                text.setColor(0xFFFFFFFF);
                text.setShadowLayer(3f, 0f, 2f, 0xCC000000);
                c.drawText(String.valueOf(PowerUp.glyph(HELP_POWERS[i])), cx, letterBase, text);
            }
            text.setColor(0xCCBFD4FF);
            drawCenteredFit(c, "speed  •  shield  •  magnet  •  double jump  •  extra life",
                    sw / 2f, capBase, pw - 60f * scale, capSize);
        }

        if (back != null) {
            drawOverlayButton(c, back, scale, false);
        }
        text.setTextAlign(Paint.Align.CENTER);
    }

    /** "SELECT LEVEL": two-column grid of the career's unlocked levels. */
    private void drawLevelSelect(Canvas c, Game g, float sw, float sh, float scale) {
        scrim(c, sw, sh, 0.62f);
        float pw = g.levelSelectPanelW();
        float ph = g.levelSelectPanelH();
        float left = (sw - pw) / 2f;
        float top = (sh - ph) / 2f;
        drawPanel(c, sw / 2f, sh / 2f, pw, ph);

        float headerY = top + Math.min(52f * scale, ph * 0.075f);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(Math.max(8f, 38f * scale));
        text.setColor(0xFFFFD34D);
        text.setShadowLayer(7f, 0f, 4f, 0xAA000000);
        c.drawText("SELECT LEVEL", left + pw * 0.06f, headerY, text);

        // career line on the right: who is playing and what they carry
        String career = g.profile.name + "  •  COINS " + num(g.profile.coins)
                + "  •  " + Weapons.secondaryName(g.secondaryWeaponId());
        text.setTextAlign(Paint.Align.RIGHT);
        text.setTextSize(fitSize(career, pw * 0.46f, Math.max(6f, 19f * scale)));
        text.setColor(0xFFBFD4FF);
        text.setShadowLayer(3f, 0f, 2f, 0x99000000);
        c.drawText(career, left + pw * 0.94f, headerY, text);
        text.setTextAlign(Paint.Align.CENTER);

        // rows: Game laid them out in a two-column grid; enrich each cell
        for (Game.UiButton b : g.levelButtons) {
            int idx = b.id - Game.BTN_LEVEL_ROW;
            if (idx < 0 || idx >= Levels.TEXT.length || b.w <= 0f || b.h <= 0f) {
                continue;
            }
            boolean unlocked = g.levelUnlocked(idx);
            boolean cleared = g.levelCleared(idx);
            float r = Math.max(2f, 12f * scale);
            fill.setColor(unlocked ? 0xD61E3A6E : 0x66101826);
            c.drawRoundRect(rect(b.x, b.y, b.x + b.w, b.y + b.h), r, r, fill);
            stroke.setColor(cleared ? 0xFF9BE89B : unlocked ? 0x88FFFFFF : 0x33FFFFFF);
            stroke.setStrokeWidth(Math.max(1f, (cleared ? 2.5f : 2f) * scale));
            c.drawRoundRect(rect(b.x, b.y, b.x + b.w, b.y + b.h), r, r, stroke);

            float pad = 12f * scale;
            float leftX = b.x + pad;
            // number chip
            float chip = Math.min(b.h * 0.52f, 30f * scale);
            fill.setColor(unlocked ? 0xFF0F2547 : 0xFF181E2C);
            c.drawCircle(leftX + chip, b.y + b.h / 2f, chip, fill);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(chip * 1.05f);
            text.setColor(unlocked ? 0xFFFFD34D : 0x66FFFFFF);
            c.drawText(String.valueOf(idx + 1), leftX + chip, b.y + b.h * 0.68f, text);

            // name (dimmed when locked)
            float nameX = leftX + chip * 2.4f;
            float nameSize = Math.max(6f, Math.min(b.h * 0.40f, 27f * scale));
            text.setTextAlign(Paint.Align.LEFT);
            text.setColor(unlocked ? 0xFFFFFFFF : 0x55FFFFFF);
            text.setShadowLayer(3f, 0f, 2f, 0xCC000000);
            text.setTextSize(fitSize(g.levelName(idx),
                    Math.max(20f, b.w - (nameX - b.x) - pad - 90f * scale), nameSize));
            c.drawText(g.levelName(idx), nameX, b.y + b.h * (b.h > 40f ? 0.42f : 0.64f), text);

            // status line under the name, or centred when the row is short
            String status;
            if (!unlocked) {
                status = "LOCKED";
            } else if (cleared) {
                status = "CLEARED  •  BEST " + num(g.profile.levelBestScore(idx));
            } else {
                status = "NEW";
            }
            float subSize = Math.max(4f, Math.min(b.h * 0.26f, 17f * scale));
            text.setTextSize(fitSize(status,
                    Math.max(20f, b.w - (nameX - b.x) - pad), subSize));
            text.setColor(!unlocked ? 0x55FFFFFF
                    : cleared ? 0xFF9BE89B : 0xFFFFE08A);
            if (b.h > 40f) {
                c.drawText(status, nameX, b.y + b.h * 0.76f, text);
            } else {
                text.setTextAlign(Paint.Align.RIGHT);
                c.drawText(status, b.x + b.w - pad, b.y + b.h * 0.64f, text);
                text.setTextAlign(Paint.Align.LEFT);
            }
        }

        for (Game.UiButton b : g.levelButtons) {
            if (b.id == Game.BTN_BACK) {
                drawOverlayButton(c, b, scale, false);
            }
        }

        // feedback line (locked-row refusals) above the footer
        float msgY = footerTop(g.levelButtons, top + ph) - 12f * scale;
        drawMessage(c, g.levelMsg, g.levelMsgTimer, sw / 2f, msgY, scale);

        text.setTextAlign(Paint.Align.CENTER);
        String foot = "cleared levels stay replayable  •  picking a level opens your loadout";
        float footY = Math.min(footerBottom(g.levelButtons, top + ph) + 18f * scale,
                top + ph - 8f * scale);
        text.setTextSize(fitSize(foot, pw * 0.94f, Math.max(5f, 17f * scale)));
        text.setColor(0x99FFFFFF);
        c.drawText(foot, sw / 2f, Math.min(footY, sh - 8f * scale), text);
    }

    /** Pre-level loadout: pick the secondary weapon, then START. */
    private void drawLoadout(Canvas c, Game g, float sw, float sh, float scale) {
        scrim(c, sw, sh, 0.62f);
        float pw = g.loadoutPanelW();
        float ph = g.loadoutPanelH();
        float left = (sw - pw) / 2f;
        float top = (sh - ph) / 2f;
        drawPanel(c, sw / 2f, sh / 2f, pw, ph);

        String title = "LEVEL " + (g.pendingLevel + 1) + "  •  " + g.levelName(g.pendingLevel);
        float headerY = top + Math.min(52f * scale, ph * 0.075f);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(fitSize(title, pw * 0.52f, Math.max(8f, 34f * scale)));
        text.setColor(0xFFFFD34D);
        text.setShadowLayer(7f, 0f, 4f, 0xAA000000);
        c.drawText(title, left + pw * 0.06f, headerY, text);

        String wallet = g.profile.name + "  •  COINS " + num(g.profile.coins);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setTextSize(fitSize(wallet, pw * 0.40f, Math.max(6f, 19f * scale)));
        text.setColor(0xFFBFD4FF);
        text.setShadowLayer(3f, 0f, 2f, 0x99000000);
        c.drawText(wallet, left + pw * 0.94f, headerY, text);
        text.setTextAlign(Paint.Align.CENTER);

        String hint = "the sword is always equipped  •  choose the secondary weapon for this run";
        text.setTextSize(fitSize(hint, pw * 0.88f, Math.max(5f, 18f * scale)));
        text.setColor(0xCCFFFFFF);
        c.drawText(hint, sw / 2f, top + ph * 0.135f, text);

        int equipped = g.secondaryWeaponId();
        for (Game.UiButton b : g.loadoutButtons) {
            int id = b.id - Game.BTN_WEAPON_ROW;
            if (id < 0 || id >= Weapons.SECONDARY_COUNT || b.w <= 0f) {
                continue;
            }
            boolean active = id == equipped;
            boolean ranged = Weapons.isRanged(id);
            float r = Math.max(2f, 12f * scale);
            fill.setColor(active ? 0xE62C5AA8 : 0xB4101826);
            c.drawRoundRect(rect(b.x, b.y, b.x + b.w, b.y + b.h), r, r, fill);
            stroke.setColor(active ? 0xFFFFD34D : 0x55FFFFFF);
            stroke.setStrokeWidth(Math.max(1f, (active ? 3f : 2f) * scale));
            c.drawRoundRect(rect(b.x, b.y, b.x + b.w, b.y + b.h), r, r, stroke);

            // icon on the left, matching the disc glyphs
            float icon = b.h * 0.26f;
            drawSecondaryGlyph(c, id, b.x + icon * 2.4f, b.y + b.h / 2f, icon);

            float nameX = b.x + icon * 4.4f;
            float nameSize = Math.min(b.h * 0.34f, 26f * scale);
            float subSize = Math.min(b.h * 0.24f, 17f * scale);
            float rightW = 0f;
            if (active) {
                rightW = textWidth("EQUIPPED", Math.max(4f, subSize)) + 26f * scale;
            }
            text.setTextAlign(Paint.Align.LEFT);
            text.setColor(ranged ? 0xFFFFFFFF : 0xBBFFFFFF);
            text.setShadowLayer(3f, 0f, 2f, 0xCC000000);
            text.setTextSize(fitSize(Weapons.secondaryName(id),
                    Math.max(24f, b.w - (nameX - b.x) - rightW - 14f * scale),
                    Math.max(5f, nameSize)));
            c.drawText(Weapons.secondaryName(id), nameX, b.y + b.h * (b.h > 38f ? 0.42f : 0.62f), text);
            if (b.h > 38f) {
                text.setTextSize(fitSize(Weapons.secondaryDesc(id),
                        Math.max(24f, b.w - (nameX - b.x) - rightW - 14f * scale),
                        Math.max(4f, subSize)));
                text.setColor(0xFFBFD4FF);
                c.drawText(Weapons.secondaryDesc(id), nameX, b.y + b.h * 0.76f, text);
            }
            if (active) {
                text.setTextAlign(Paint.Align.RIGHT);
                text.setTextSize(Math.max(4f, subSize));
                text.setColor(0xFF9BE89B);
                c.drawText("EQUIPPED", b.x + b.w - 16f * scale, b.y + b.h * 0.42f, text);
                text.setTextAlign(Paint.Align.LEFT);
            }
        }

        for (Game.UiButton b : g.loadoutButtons) {
            if (b.id == Game.BTN_START_LEVEL) {
                drawOverlayButton(c, b, scale, false);
            } else if (b.id == Game.BTN_BACK) {
                drawOverlayButton(c, b, scale, false);
            }
        }

        text.setTextAlign(Paint.Align.CENTER);
        String foot = "your pick is saved to this career";
        float footY = top + ph - 8f * scale;
        text.setTextSize(fitSize(foot, pw * 0.6f, Math.max(5f, 15f * scale)));
        text.setColor(0x88FFFFFF);
        c.drawText(foot, sw / 2f, Math.min(footY, sh - 8f * scale), text);
    }

    private void drawSettings(Canvas c, Game g, float sw, float sh, float scale) {
        scrim(c, sw, sh, 0.62f);
        float pw = g.settingsPanelW();
        float ph = g.settingsPanelH();
        float left = (sw - pw) / 2f;
        float top = (sh - ph) / 2f;
        drawPanel(c, sw / 2f, sh / 2f, pw, ph);

        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(40f * scale);
        text.setColor(0xFFFFD34D);
        text.setShadowLayer(6f, 0f, 4f, 0xAA000000);
        c.drawText("SETTINGS", sw / 2f, top + 64f * scale, text);

        drawSlider(c, g, g.sliders[Game.SLIDER_MUSIC], "MUSIC VOLUME",
                g.musicVolume + "%", scale);
        drawSlider(c, g, g.sliders[Game.SLIDER_BUTTONS], "MOVE BUTTON SIZE",
                g.moveScalePct + "%", scale);

        // back button, styled like the pause overlay buttons
        for (Game.UiButton b : g.settingsButtons) {
            fill.setColor(0xFF2F4B8C);
            c.drawRoundRect(rect(b.x, b.y, b.x + b.w, b.y + b.h), 18f, 18f, fill);
            fill.setColor(0x33FFFFFF);
            c.drawRoundRect(rect(b.x, b.y, b.x + b.w, b.y + b.h * 0.5f), 18f, 18f, fill);
            stroke.setColor(0xAAFFFFFF);
            stroke.setStrokeWidth(3f);
            c.drawRoundRect(rect(b.x, b.y, b.x + b.w, b.y + b.h), 18f, 18f, stroke);
            text.setTextSize(fitSize(b.label, b.w - 26f * scale,
                    Math.max(24f * scale, Math.min(34f * scale, b.h * 0.52f))));
            text.setColor(0xFFFFFFFF);
            text.setShadowLayer(4f, 0f, 3f, 0x99000000);
            c.drawText(b.label, b.x + b.w / 2f, b.y + b.h * 0.66f, text);
        }

        text.setTextSize(20f * scale);
        text.setColor(0xCCFFFFFF);
        text.setShadowLayer(4f, 0f, 3f, 0x99000000);
        // between the second slider and the back button, so it never collides
        c.drawText("drag a slider  •  changes apply immediately",
                sw / 2f, top + ph * 0.74f, text);
    }

    private void drawSlider(Canvas c, Game g, Game.UiSlider s, String label, String value, float scale) {
        // label above-left of the track, current value above-right
        float valSize = Math.max(6f, 24f * scale);
        float valW = textWidth(value, valSize);
        text.setTextSize(fitSize(label, Math.max(30f, s.w - valW - 18f * scale), valSize));
        text.setShadowLayer(4f, 0f, 3f, 0x99000000);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(0xFFFFFFFF);
        c.drawText(label, s.x, s.y - 34f * scale, text);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setColor(0xFFFFE08A);
        c.drawText(value, s.x + s.w, s.y - 34f * scale, text);
        text.setTextAlign(Paint.Align.CENTER);

        // knob position straight from the current setting, so the bar always
        // matches the label even if a drag is in flight
        float frac;
        if (s.id == Game.SLIDER_BUTTONS) {
            frac = (g.moveScalePct - SaveState.BUTTONS_MIN)
                    / (float) (SaveState.BUTTONS_MAX - SaveState.BUTTONS_MIN);
        } else {
            frac = g.musicVolume / (float) SaveState.MUSIC_MAX;
        }
        frac = Math.max(0f, Math.min(1f, frac));
        float knobX = s.x + s.w * frac;

        float trackH = 16f * scale;
        fill.setColor(0x66000000);
        c.drawRoundRect(rect(s.x, s.y - trackH / 2f, s.x + s.w, s.y + trackH / 2f),
                trackH / 2f, trackH / 2f, fill);
        fill.setColor(0xFFFFC93C);
        c.drawRoundRect(rect(s.x, s.y - trackH / 2f, knobX, s.y + trackH / 2f),
                trackH / 2f, trackH / 2f, fill);

        float knobR = 17f * scale;
        fill.setColor(0xFFFFFFFF);
        c.drawCircle(knobX, s.y, knobR, fill);
        stroke.setColor(0xFFD89A17);
        stroke.setStrokeWidth(3f * scale);
        c.drawCircle(knobX, s.y, knobR, stroke);
    }

    private void blinkText(Canvas c, String s, float x, float y, float size, Game g) {
        float a = 0.55f + 0.45f * sinf(g.worldTime * 4f);
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
}
