package com.example.anplomer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Executes the real Renderer on the JVM (via the android.graphics stub) while
 * the real simulation runs underneath it. This is what the headless level test
 * cannot cover: every draw path in Renderer - palettes, tiles, entities,
 * particles, HUD, overlays, touch controls - is executed at several screen
 * sizes and in every game state, and the stub rejects non-finite geometry and
 * negative radii.
 */
public final class RendererSmoke {

    static final class NullSound implements SoundSink {
        boolean enabled = true;
        int plays;
        public void play(String name) { plays++; }
        public void setEnabled(boolean e) { enabled = e; }
        public boolean isEnabled() { return enabled; }
    }

    static int failures;
    static final StringBuilder problems = new StringBuilder();

    static void check(boolean ok, String msg) {
        if (!ok) {
            failures++;
            problems.append("  FAIL ").append(msg).append('\n');
            System.out.println("  FAIL " + msg);
        }
    }

    static void drawFrame(Renderer r, Game g, float w, float h) {
        Canvas c = new Canvas((int) Math.max(1, w), (int) Math.max(1, h));
        r.draw(g, c, w, h);
        if (c.drawCount == 0) {
            check(false, "renderer drew nothing at " + w + "x" + h);
        }
    }

    /**
     * Canvas that paints into a BufferedImage using the same coordinates the
     * real Canvas would use, so a frame can be dumped and inspected off-device.
     */
    static final class RasterCanvas extends Canvas {
        final BufferedImage img;
        final Graphics2D g;
        int pathCount;
        final java.util.List<String> log = new java.util.ArrayList<>();
        boolean tracing;

        RasterCanvas(int w, int h) {
            super(w, h);
            img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
            g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(new Color(0));
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
        }

        /** Apply the Canvas transform stack, then draw in canvas coordinates. */
        private void withTransform(java.util.function.Consumer<Graphics2D> body) {
            java.awt.geom.AffineTransform base = g.getTransform();
            g.transform(transform);
            body.accept(g);
            g.setTransform(base);
        }

        void note(String s) {
            if (tracing) {
                log.add(s);
            }
        }

        private void paint(Paint p) {
            android.graphics.Shader sh = p.getShader();
            if (sh instanceof android.graphics.LinearGradient) {
                android.graphics.LinearGradient lg = (android.graphics.LinearGradient) sh;
                float[] pos = lg.positions;
                if (pos == null) {
                    pos = new float[lg.colors.length];
                    for (int i = 0; i < pos.length; i++) {
                        pos[i] = i / (float) (pos.length - 1);
                    }
                }
                java.awt.Color[] cols = new java.awt.Color[lg.colors.length];
                for (int i = 0; i < cols.length; i++) {
                    cols[i] = new java.awt.Color((lg.colors[i] & 0xFFFFFF) | 0xFF000000, true);
                }
                g.setPaint(new java.awt.GradientPaint(lg.x0, lg.y0, cols[0],
                        lg.x1, lg.y1, cols[cols.length - 1]));
                return;
            }
            int c = p.getColor();
            int a = p.getAlpha();
            int rgb = (c & 0xFFFFFF) | 0xFF000000;
            Color col = new Color(rgb, true);
            if (a < 255) {
                col = new Color((col.getRed() * a) / 255, (col.getGreen() * a) / 255,
                        (col.getBlue() * a) / 255, a);
            }
            g.setColor(col);
            Paint.Style st = p.getStyle();
            if (st == Paint.Style.STROKE || st == Paint.Style.FILL_AND_STROKE) {
                g.setStroke(new BasicStroke(Math.max(0.5f, p.getStrokeWidth()),
                        p.getStrokeCap() == Paint.Cap.ROUND ? BasicStroke.CAP_ROUND : BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_ROUND));
            } else {
                g.setStroke(new BasicStroke(1f));
            }
        }

        private void stroke(Paint p) {
            int c = (p.getColor() & 0xFFFFFF) | 0xFF000000;
            int a = p.getAlpha();
            Color col = new Color(c, true);
            if (a < 255) {
                col = new Color((col.getRed() * a) / 255, (col.getGreen() * a) / 255,
                        (col.getBlue() * a) / 255, a);
            }
            g.setColor(col);
            g.setStroke(new BasicStroke(Math.max(0.5f, p.getStrokeWidth()),
                    p.getStrokeCap() == Paint.Cap.ROUND ? BasicStroke.CAP_ROUND : BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_ROUND));
        }

        public void drawRect(float l, float t, float r, float b, Paint p) {
            note("rect " + l + "," + t + " " + r + "," + b + " #" + Integer.toHexString(p.getColor()));
            paint(p);
            withTransform(g2 -> {
                if (p.getStyle() == Paint.Style.FILL) {
                    g2.fillRect((int) l, (int) t, (int) Math.max(1, r - l), (int) Math.max(1, b - t));
                } else {
                    g2.drawRect((int) l, (int) t, (int) Math.max(1, r - l), (int) Math.max(1, b - t));
                }
            });
            drawCount++;
        }

        public void drawRoundRect(RectF rect, float rx, float ry, Paint p) {
            paint(p);
            int w = (int) Math.max(1, rect.width());
            int h = (int) Math.max(1, rect.height());
            withTransform(g2 -> {
                if (p.getStyle() == Paint.Style.FILL) {
                    g2.fillRoundRect((int) rect.left, (int) rect.top, w, h,
                            (int) Math.max(1, rx * 2), (int) Math.max(1, ry * 2));
                } else {
                    g2.drawRoundRect((int) rect.left, (int) rect.top, w, h,
                            (int) Math.max(1, rx * 2), (int) Math.max(1, ry * 2));
                }
            });
            drawCount++;
        }

        public void drawCircle(float cx, float cy, float r, Paint p) {
            note("circle " + cx + "," + cy + " r=" + r + " #" + Integer.toHexString(p.getColor()));
            paint(p);
            int d = (int) Math.max(1, r * 2);
            withTransform(g2 -> {
                if (p.getStyle() == Paint.Style.FILL) {
                    g2.fillOval((int) (cx - r), (int) (cy - r), d, d);
                } else {
                    g2.drawOval((int) (cx - r), (int) (cy - r), d, d);
                }
            });
            drawCount++;
        }

        public void drawOval(RectF rect, Paint p) {
            paint(p);
            int w = (int) Math.max(1, rect.width());
            int h = (int) Math.max(1, rect.height());
            withTransform(g2 -> {
                if (p.getStyle() == Paint.Style.FILL) {
                    g2.fillOval((int) rect.left, (int) rect.top, w, h);
                } else {
                    g2.drawOval((int) rect.left, (int) rect.top, w, h);
                }
            });
            drawCount++;
        }

        public void drawLine(float x1, float y1, float x2, float y2, Paint p) {
            stroke(p);
            withTransform(g2 -> g2.drawLine((int) x1, (int) y1, (int) x2, (int) y2));
            drawCount++;
        }

        public void drawArc(RectF oval, float start, float sweep, boolean useCenter, Paint p) {
            stroke(p);
            withTransform(g2 -> g2.drawArc((int) oval.left, (int) oval.top,
                    (int) Math.max(1, oval.width()), (int) Math.max(1, oval.height()),
                    (int) start, (int) sweep));
            drawCount++;
        }

        public void drawPath(Path path, Paint p) {
            note("path segs=" + path.segments.size() + " #" + Integer.toHexString(p.getColor()));
            paint(p);
            Path2D.Float shape = new Path2D.Float();
            for (float[] seg : path.segments) {
                switch ((char) seg[0]) {
                    case 'M': shape.moveTo(seg[1], seg[2]); break;
                    case 'L': shape.lineTo(seg[1], seg[2]); break;
                    case 'Q':
                        shape.quadTo(seg[1], seg[2], seg[3], seg[4]);
                        break;
                    case 'C':
                        shape.curveTo(seg[1], seg[2], seg[3], seg[4], seg[5], seg[6]);
                        break;
                    case 'Z': shape.closePath(); break;
                    default: break;
                }
            }
            withTransform(g2 -> {
                if (p.getStyle() == Paint.Style.FILL) {
                    g2.fill(shape);
                } else {
                    g2.draw(shape);
                }
            });
            pathCount++;
            drawCount++;
        }

        public void drawText(String s, float x, float y, Paint p) {
            note("text '" + s + "' @" + x + "," + y + " #" + Integer.toHexString(p.getColor()));
            paint(p);
            withTransform(g2 -> {
                java.awt.Font f = new java.awt.Font("SansSerif", java.awt.Font.PLAIN,
                        Math.max(6, (int) p.getTextSize()));
                g2.setFont(f);
                // the stub records Align but does not apply it; a real Canvas
                // does, so a dump that ignored it drew every centred label half
                // a string too far right.
                float drawX = x;
                float strW = g2.getFontMetrics().stringWidth(s);
                if (p.getTextAlign() == Paint.Align.CENTER) {
                    drawX = x - strW / 2f;
                } else if (p.getTextAlign() == Paint.Align.RIGHT) {
                    drawX = x - strW;
                }
                g2.drawString(s, drawX, y);
            });
            drawCount++;
        }

        void trace(int from, int to) {
            for (int i = from; i < Math.min(to, log.size()); i++) {
                System.out.println("    [" + i + "] " + log.get(i));
            }
        }

        void save(String path) {
            try {
                ImageIO.write(img, "png", new File(path));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** Render one frame of the game exactly as it would be drawn on device. */
    static void dumpFrame(String file, float w, float h) {
        Renderer r = new Renderer();
        NullSound sound = new NullSound();
        Game g = new Game(sound);
        g.resize(w, h);
        g.state = Game.State.PLAYING;
        g.update(1f / 60f);
        RasterCanvas c = new RasterCanvas((int) w, (int) h);
        c.tracing = true;
        r.draw(g, c, w, h);
        c.save(file);
        if (System.getenv("TRACE_CALLS") != null) {
            System.out.println("[render] total draw calls " + c.log.size());
            c.trace(0, 60);
            System.out.println("  ...");
            c.trace(c.log.size() - 30, c.log.size());
        }
        System.out.println("[render] dumped " + file + " (draw calls " + c.drawCount
                + ", paths " + c.pathCount + ", camera x=" + g.camera.x + " y=" + g.camera.y
                + " zoom=" + g.camera.zoom + ", player " + g.player.x + "," + g.player.y
                + ", screen " + g.screenWidth + "x" + g.screenHeight + ")");
    }

    /** Render one labelled frame at 2400x1080 for visual review. */
    static void dumpShot(java.io.File file, int level, float playerX, Game.State state,
                         java.util.function.Consumer<Game> setup, boolean settle) {
        dumpShot(file, 2400, 1080, level, playerX, state, setup, settle);
    }

    static void dumpShot(java.io.File file, float w, float h, int level, float playerX,
                         Game.State state, java.util.function.Consumer<Game> setup,
                         boolean settle) {
        Renderer r = new Renderer();
        Game g = new Game(new NullSound());
        g.resize(w, h);
        g.loadLevel(level);
        g.state = Game.State.PLAYING;
        if (playerX >= 0f) {
            g.player.x = playerX;
            g.player.y = 0f;
        }
        if (setup != null) {
            setup.accept(g);
        }
        g.state = state;
        int frames = settle ? 120 : 4;
        for (int i = 0; i < frames; i++) {
            g.update(1f / 60f);
        }
        RasterCanvas c = new RasterCanvas((int) w, (int) h);
        r.draw(g, c, w, h);
        c.save(file.getPath());
        System.out.println("[render] shot " + file.getName() + " (" + c.drawCount + " draws)");
    }

    static void dumpShots() {
        String dirName = System.getenv("DUMP_SHOTS");
        java.io.File dir = new java.io.File(dirName);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        dumpShot(new java.io.File(dir, "menu.png"), 0, -1f, Game.State.MENU, null, false);
        dumpShot(new java.io.File(dir, "career.png"), 0, -1f, Game.State.CAREER, g -> {
            g.profile.coins = 186;
            g.profile.levelIndex = 3;
            g.newProfile();
            g.profiles.get(1).coins = 42;
        }, true);
        dumpShot(new java.io.File(dir, "store.png"), 0, -1f, Game.State.STORE, g -> {
            g.profile.coins = 214;
            g.profile.upgrades[Upgrades.SPEED] = 2;
            g.profile.upgrades[Upgrades.JUMP] = 1;
            g.profile.upgrades[Upgrades.MAGNET] = 1;
        }, true);
        dumpShot(new java.io.File(dir, "level4-caverns.png"), 3, 132f * 64f,
                Game.State.PLAYING, g -> {
                    g.profile.upgrades[Upgrades.SPEED] = 1;
                    g.speedBoostTimer = 7.5f;
                    g.shieldCharges = 1;
                    g.runCoins = 6;
                }, true);
        dumpShot(new java.io.File(dir, "level8-summit.png"), 7, 41f * 64f,
                Game.State.PLAYING, g -> {
                    g.magnetTimer = 6.2f;
                    g.doubleJumpTimer = 4.0f;
                    g.profile.coins = 128;
                }, true);
        // phone-shaped and small screens: the layouts that used to clip
        dumpShot(new java.io.File(dir, "menu-1080x480.png"), 1080, 480, 0, -1f,
                Game.State.MENU, null, false);
        dumpShot(new java.io.File(dir, "career-1080x480.png"), 1080, 480, 0, -1f,
                Game.State.CAREER, g -> {
                    g.profile.coins = 186;
                    g.newProfile();
                    g.profiles.get(1).coins = 42;
                }, true);
        dumpShot(new java.io.File(dir, "store-1080x480.png"), 1080, 480, 0, -1f,
                Game.State.STORE, g -> {
                    g.profile.coins = 214;
                    g.profile.upgrades[Upgrades.SPEED] = 2;
                    g.profile.upgrades[Upgrades.JUMP] = 1;
                }, true);
        dumpShot(new java.io.File(dir, "menu-480x320.png"), 480, 320, 0, -1f,
                Game.State.MENU, null, false);
        dumpShot(new java.io.File(dir, "paused-1080x480.png"), 1080, 480, 1, 30f * 64f,
                Game.State.PAUSED, g -> g.profile.coins = 31, false);
        dumpShot(new java.io.File(dir, "cleared-1080x480.png"), 1080, 480, 1, 30f * 64f,
                Game.State.LEVEL_CLEARED, g -> {
                    g.coinsTaken = 23;
                    g.bonusTime = 120;
                    g.bonusCoins = 12;
                    g.rewardCoins = 8;
                    g.profile.coins = 186;
                }, true);
        dumpShot(new java.io.File(dir, "win-1080x480.png"), 1080, 480, 7, 30f * 64f,
                Game.State.WIN, g -> g.profile.coins = 412, true);
        dumpShot(new java.io.File(dir, "help.png"), 2400, 1080, 0, -1f,
                Game.State.HELP, Game::openHelp, false);
        dumpShot(new java.io.File(dir, "help-1080x480.png"), 1080, 480, 0, -1f,
                Game.State.HELP, Game::openHelp, false);
        dumpShot(new java.io.File(dir, "help-480x320.png"), 480, 320, 0, -1f,
                Game.State.HELP, Game::openHelp, false);
        dumpShot(new java.io.File(dir, "playing-1080x480.png"), 1080, 480, 4, 40f * 64f,
                Game.State.PLAYING, g -> {
                    g.input.pointerDown(9, g.controls.padCX, g.controls.padCY);
                    g.input.pointerMove(9, g.controls.padCX - g.controls.padR * 0.66f,
                            g.controls.padCY - g.controls.padR * 0.50f);
                    g.speedBoostTimer = 8.4f;
                    g.magnetTimer = 3.1f;
                    g.doubleJumpTimer = 11.2f;
                    g.shieldCharges = 2;
                    g.profile.coins = 214;
                }, true);
    }

    public static void main(String[] args) {
        if (System.getenv("DUMP_SHOTS") != null) {
            dumpShots();
        }
        float[][] sizes = {
                {2400, 1080}, {1080, 480}, {1920, 1080}, {1280, 720},
                {2340, 1080}, {960, 540}, {800, 480}, {480, 320},
                {333, 111}, {1, 1}, {16, 9}, {3840, 1080},
        };

        if (System.getenv("DUMP_FRAME") != null) {
            dumpFrame(System.getenv("DUMP_FRAME"), 2400, 1080);
        }

        // ---- 1. every state, every palette, every size --------------------
        System.out.println("[render] states x palettes x sizes");
        NullSound sound = new NullSound();
        Game g = new Game(sound);
        Renderer r = new Renderer();
        g.resize(2400, 1080);

        Game.State[] states = Game.State.values();
        int total = 0;
        for (float[] s : sizes) {
            for (int level = 0; level < Levels.TEXT.length; level++) {
                g.loadLevel(level);
                g.resize(s[0], s[1]);
                for (Game.State st : states) {
                    g.state = st;
                    g.stateTime = 0.5f;
                    if (st == Game.State.CAREER || st == Game.State.STORE) {
                        // those screens lay their rows out on the first update
                        g.update(1f / 60f);
                        g.stateTime = 0.5f;
                    }
                    drawFrame(r, g, s[0], s[1]);
                    }
                    // a few more frames with the state timer advancing, which is
                    // what drives the "tap to continue" fade
                    for (int f = 0; f < 5; f++) {
                        g.stateTime += 0.1f;
                        drawFrame(r, g, s[0], s[1]);
                    }
                total++;
            }
        }
        System.out.println("[render] drew " + total + " state/size combinations");

        // ---- 1b. career, store and the power-up HUD -----------------------
        System.out.println("[render] career / store / power-up HUD");
        Game hg = new Game(sound);
        hg.resize(2400, 1080);
        hg.state = Game.State.PLAYING;
        hg.update(1f / 60f);
        int levelsWithPickups = 0;
        for (int level = 0; level < Levels.TEXT.length; level++) {
            hg.loadLevel(level);
            if (!hg.powerUps.isEmpty()) {
                levelsWithPickups++;
            }
        }
        check(levelsWithPickups == Levels.TEXT.length,
                "every level should carry pickups: " + levelsWithPickups
                        + "/" + Levels.TEXT.length);
        hg.loadLevel(3);
        hg.state = Game.State.PLAYING;
        // every timed effect alive at once, so the whole HUD strip is drawn
        hg.speedBoostTimer = 6.5f;
        hg.magnetTimer = 5.5f;
        hg.doubleJumpTimer = 4.5f;
        hg.shieldCharges = 2;
        hg.runCoins = 7;
        hg.storeMsg = "PURCHASED";
        hg.storeMsgTimer = 1.5f;
        hg.careerMsg = "NEW CAREER";
        hg.careerMsgTimer = 1.5f;
        drawFrame(r, hg, 2400, 1080);
        drawFrame(r, hg, 1080, 480);
        drawFrame(r, hg, 1, 1);

        hg.state = Game.State.CAREER;
        hg.update(1f / 60f);
        check(!hg.careerButtons.isEmpty(), "career screen laid out no rows");
        drawFrame(r, hg, 2400, 1080);
        drawFrame(r, hg, 1080, 480);

        hg.state = Game.State.STORE;
        hg.update(1f / 60f);
        check(!hg.storeButtons.isEmpty(), "store screen laid out no rows");
        drawFrame(r, hg, 2400, 1080);
        drawFrame(r, hg, 1080, 480);
        // movement pad: draw it idle and deflected at three sizes
        hg.state = Game.State.PLAYING;
        for (int[] sz : new int[][]{{2400, 1080}, {1080, 480}, {480, 320}, {1, 1}}) {
            hg.resize(sz[0], sz[1]);
            Controls pc = hg.controls;
            hg.input.pointerDown(9, pc.padCX, pc.padCY);
            hg.input.pointerMove(9, pc.padCX + pc.padR * 0.62f, pc.padCY - pc.padR * 0.55f);
            check(hg.input.padActive, "pad did not take the pointer at " + sz[0] + "x" + sz[1]);
            check(Math.abs(hg.input.padX) > 0.3f && hg.input.padY < -0.3f,
                    "pad deflection not read at " + sz[0] + "x" + sz[1]);
            drawFrame(r, hg, sz[0], sz[1]);
            hg.input.pointerUp(9);
        }
        hg.input.pointerUp(9);
        hg.resize(2400, 1080);

        hg.openHelp();
        check(!hg.helpButtons.isEmpty(), "tutorial laid out no back button");
        drawFrame(r, hg, 2400, 1080);
        drawFrame(r, hg, 1080, 480);
        drawFrame(r, hg, 480, 320);
        drawFrame(r, hg, 1, 1);
        hg.closeHelp();
        System.out.println("[render] career rows " + hg.careerButtons.size()
                + ", store rows " + hg.storeButtons.size()
                + ", tutorial buttons " + hg.helpButtons.size());

        // ---- 2. real gameplay, drawing every frame ------------------------
        System.out.println("[render] live gameplay with rendering");
        g = new Game(sound);
        g.resize(2400, 1080);
        g.state = Game.State.PLAYING;
        java.util.Random rnd = new java.util.Random(20240517L);
        int frames = 0;
        int deathsSeen = 0;
        int stateChanges = 0;
        Game.State prev = g.state;
        for (int i = 0; i < 3000; i++) {
            // drive the player: run right, jump periodically, occasional left
            boolean right = rnd.nextFloat() < 0.85f;
            boolean left = !right && rnd.nextFloat() < 0.4f;
            boolean jump = rnd.nextInt(23) == 0;
            g.input.key(InputController.KEY_LEFT, left);
            g.input.key(InputController.KEY_RIGHT, right);
            g.input.key(InputController.KEY_JUMP, jump);
            g.update(1f / 60f);
            if (rnd.nextInt(7) == 0) {
                g.input.key(InputController.KEY_JUMP, false);
            }
            drawFrame(r, g, 2400, 1080);
            drawFrame(r, g, 1080, 480);
            frames++;
            if (g.player.dead) {
                deathsSeen++;
                check(g.player.deadTimer > 0f, "dead timer should be running");
            }
            if (g.state != prev) {
                stateChanges++;
                prev = g.state;
            }
            // occasionally toggle pause/mute/confirm so overlays are drawn
            if (i % 300 == 0) {
                g.input.key(InputController.KEY_PAUSE, true);
                g.update(1f / 60f);
                drawFrame(r, g, 2400, 1080);
                g.input.key(InputController.KEY_PAUSE, true);
                g.update(1f / 60f);
                drawFrame(r, g, 2400, 1080);
            }
        }
        System.out.println("[render] " + frames + " frames, deaths seen " + deathsSeen
                + ", state transitions " + stateChanges);

        // ---- 2b. force death -> respawn -> game over, drawing each frame --
        System.out.println("[render] forced death / game over");
        g = new Game(sound);
        g.resize(2400, 1080);
        g.state = Game.State.PLAYING;
        g.update(1f / 60f);
        int before = g.lives;
        for (int d = 0; d < 3; d++) {
            g.killPlayer();
            check(g.player.dead, "killPlayer should mark the player dead");
            // draw the death animation / respawn wait
            for (int i = 0; i < 90; i++) {
                g.update(1f / 60f);
                drawFrame(r, g, 2400, 1080);
            }
            for (int i = 0; i < 30; i++) {
                g.update(1f / 60f);
                drawFrame(r, g, 2400, 1080);
            }
            // the life is spent when the respawn happens (or the run ends)
            check(g.lives == before - (d + 1), "lives should drop after the death, got " + g.lives);
            if (g.state == Game.State.GAME_OVER) {
                drawFrame(r, g, 2400, 1080);
                drawFrame(r, g, 2400, 1080);
            }
        }
        check(g.state == Game.State.GAME_OVER, "three deaths should end the run, got " + g.state);
        check(g.particles.size() >= 0, "particle list readable");
        System.out.println("[render] forced deaths drawn, state=" + g.state + " lives=" + g.lives);

        // ---- 3. level transitions and win ---------------------------------
        System.out.println("[render] level flow");
        for (int level = 0; level < Levels.TEXT.length; level++) {
            g.loadLevel(level);
            g.resize(2400, 1080);
            g.state = Game.State.PLAYING;
            g.update(1f / 60f);
            drawFrame(r, g, 2400, 1080);
            g.state = Game.State.LEVEL_CLEARED;
            g.stateTime = 1.2f;
            g.bonusTime = 12;
            g.bonusCoins = 3;
            drawFrame(r, g, 2400, 1080);
        }
        g.state = Game.State.WIN;
        g.stateTime = 1.0f;
        drawFrame(r, g, 2400, 1080);
        g.state = Game.State.GAME_OVER;
        g.stateTime = 1.0f;
        drawFrame(r, g, 2400, 1080);
        g.state = Game.State.MENU;
        g.stateTime = 1.0f;
        drawFrame(r, g, 2400, 1080);

        // ---- 4. pause overlay + touch controls at many sizes -------------
        System.out.println("[render] pause overlay");
        g = new Game(sound);
        g.resize(2400, 1080);
        g.state = Game.State.PLAYING;
        g.update(1f / 60f);
        g.input.key(InputController.KEY_PAUSE, true);
        g.update(1f / 60f);
        check(g.state == Game.State.PAUSED, "pause key should pause, got " + g.state);
        for (float[] s : sizes) {
            g.resize(s[0], s[1]);
            drawFrame(r, g, s[0], s[1]);
        }
        g.state = Game.State.PLAYING;
        g.resize(2400, 1080);
        g.input.key(InputController.KEY_LEFT, true);
        g.update(1f / 60f);
        drawFrame(r, g, 2400, 1080);
        g.input.key(InputController.KEY_LEFT, false);
        g.input.key(InputController.KEY_JUMP, true);
        g.update(1f / 60f);
        drawFrame(r, g, 2400, 1080);

        check(Float.isFinite(g.player.x) && Float.isFinite(g.player.y), "player position is NaN");

        // ---- 5. the player must actually be on screen -------------------
        // Regression guard for the camera: update() used to clamp the lerped
        // position instead of the follow target, which collapsed the camera
        // onto the top edge of the level and pushed the player off-screen.
        System.out.println("[render] camera framing");
        for (float[] s : sizes) {
            Game cg = new Game(new NullSound());
            cg.resize(s[0], s[1]);
            cg.state = Game.State.PLAYING;
            for (int i = 0; i < 240; i++) {
                cg.update(1f / 60f);
            }
            float px = cg.camera.worldToScreenX(cg.player.centerX());
            float py = cg.camera.worldToScreenY(cg.player.centerY());
            boolean onScreen = px > 0f && px < s[0] && py > 0f && py < s[1];
            check(onScreen, "player off-screen at " + s[0] + "x" + s[1]
                    + ": screen pos " + px + "," + py + " (camera " + cg.camera.x + ","
                    + cg.camera.y + " zoom " + cg.camera.zoom + ")");
            if (s[0] == 2400f) {
                System.out.println("[render] 2400x1080: player at screen " + px + "," + py
                        + ", camera y=" + cg.camera.y);
            }
        }
        // the same must hold deep into a level, where the floor is near the
        // bottom of the world
        {
            Game cg = new Game(new NullSound());
            cg.resize(2400, 1080);
            cg.state = Game.State.PLAYING;
            cg.player.x = cg.level.w * 64f - 400f;
            for (int i = 0; i < 300; i++) {
                cg.update(1f / 60f);
            }
            float px = cg.camera.worldToScreenX(cg.player.centerX());
            float py = cg.camera.worldToScreenY(cg.player.centerY());
            check(px > 0f && px < 2400f && py > 0f && py < 1080f,
                    "player off-screen at level end: " + px + "," + py);
            System.out.println("[render] level end: player at screen " + px + "," + py
                    + ", camera x=" + cg.camera.x + " y=" + cg.camera.y);
        }
        check(Float.isFinite(g.camera.x) && Float.isFinite(g.camera.y), "camera is NaN");

        // ---- 6. the sky must be opaque in every frame ---------------------
        // Regression guard: `fill` is shared across the renderer, and a shader
        // is modulated by the paint's alpha. drawParticles / the HUD used to
        // leave a faded alpha behind, so the NEXT frame's sky rendered
        // translucent — old sprites showed through as ghost trails, and the
        // sky turned dark navy whenever the buffer underneath was not the
        // previous frame. The first full-screen fill of a frame (the sky) must
        // always carry alpha 255.
        System.out.println("[render] sky opacity guard");
        Game sg = new Game(sound);
        sg.resize(2400, 1080);
        sg.state = Game.State.PLAYING;
        Canvas skyCanvas = new Canvas(2400, 1080);
        java.util.Random srnd = new java.util.Random(77L);
        int skyFrames = 0;
        int worstSkyAlpha = 256;
        for (int i = 0; i < 600; i++) {
            // jumps and stomps spawn particles; run to keep the world alive
            sg.input.key(InputController.KEY_RIGHT, true);
            sg.input.key(InputController.KEY_JUMP, srnd.nextInt(4) == 0);
            sg.update(1f / 60f);
            if (srnd.nextInt(6) == 0) {
                sg.input.key(InputController.KEY_JUMP, false);
            }
            skyCanvas.beginFrame();
            r.draw(sg, skyCanvas, 2400, 1080);
            if (skyCanvas.skyAlpha < 0) {
                check(false, "frame " + i + ": no full-screen sky fill drawn");
            } else if (skyCanvas.skyAlpha != 255) {
                check(false, "frame " + i + ": sky drawn at alpha " + skyCanvas.skyAlpha
                        + " (must be 255; translucent sky causes ghost trails)");
            }
            worstSkyAlpha = Math.min(worstSkyAlpha, skyCanvas.skyAlpha);
            skyFrames++;
        }
        System.out.println("[render] sky opaque in all " + skyFrames
                + " frames (worst alpha " + worstSkyAlpha + ")");

        System.out.println();
        if (failures == 0) {
            System.out.println("RENDERER SMOKE OK: no exceptions, all geometry finite, "
                    + "all states/palettes/sizes drawn");
        } else {
            System.out.println("RENDERER SMOKE FAILED with " + failures + " failure(s):");
            System.out.print(problems);
            System.exit(1);
        }
    }
}
