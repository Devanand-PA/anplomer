package com.example.anplomer;

import java.io.File;
import java.util.function.Consumer;

/**
 * Renders close-up PNGs of each art asset (player poses, mobs, weapons,
 * title screen, world map) into a directory, headlessly via AWT, so the
 * designs can be reviewed and rated off-device.
 *
 * Usage: java -cp <classes> com.example.anplomer.ArtShot [outdir]
 */
public final class ArtShot {

    public static void main(String[] args) {
        File dir = new File(args.length > 0 ? args[0] : "shots/art");
        dir.mkdirs();

        // ---- player poses (level 0 start area, camera zoomed on the hero) ----
        shot(dir, "player-idle.png", 700, 520, 7f, 0, Game.State.PLAYING, g -> {
        }, g -> {
            g.player.vx = 0f;
            g.player.onGround = true;
            g.player.runCycle = 0f;
            g.player.facingRight = true;
        });
        shot(dir, "player-run.png", 700, 520, 7f, 0, Game.State.PLAYING, g -> {
        }, g -> {
            g.player.vx = 260f;
            g.player.onGround = true;
            g.player.runCycle = 2.4f;
            g.player.facingRight = true;
        });
        shot(dir, "player-jump.png", 700, 520, 7f, 0, Game.State.PLAYING, g -> {
        }, g -> {
            g.player.onGround = false;
            g.player.vy = -520f;
            g.player.vx = 180f;
            g.player.facingRight = true;
        });

        // ---- mobs standing on the ground near the start ----
        shot(dir, "mob-walker.png", 700, 520, 7f, 1, Game.State.PLAYING, g -> {
            g.enemies.clear();
            float feet = 12f * Constants.TILE;
            Enemy w = new Enemy(Enemy.Type.WALKER, g.level.startX + 260f, feet);
            w.y = feet - w.h;
            w.facing = 1f;
            w.animTime = 0.6f;
            g.enemies.add(w);
        }, g -> {
            Enemy w = g.enemies.get(0);
            g.camera.snapTo(w.x + w.w / 2f, w.y + w.h / 2f - 40f, g.level);
        });
        shot(dir, "mob-flyer.png", 700, 520, 7f, 1, Game.State.PLAYING, g -> {
            g.enemies.clear();
            Enemy f = new Enemy(Enemy.Type.FLYER, g.level.startX + 260f, 7f * Constants.TILE);
            f.facing = -1f;
            g.enemies.add(f);
        }, g -> {
            Enemy f = g.enemies.get(0);
            g.camera.snapTo(f.x + f.w / 2f, f.y + f.h / 2f, g.level);
        });

        // ---- weapons: sword mid-swing, arrow, boomerang ----
        shot(dir, "weapon-swing.png", 900, 560, 6f, 0, Game.State.PLAYING, g -> {
        }, g -> {
            g.player.vx = 260f;
            g.player.onGround = true;
            g.player.runCycle = 2.4f;
            g.player.swingStyle = SwordStyle.GROUND_RUN;
            g.player.swingTimer = Constants.SWORD_TIME * 0.5f;
            g.player.facingRight = true;
            g.camera.snapTo(g.player.centerX() + 90f, g.player.centerY(), g.level);
        });
        shot(dir, "weapon-arrow.png", 700, 400, 6f, 1, Game.State.PLAYING, g -> {
            g.projectiles.clear();
            Projectile a = new Projectile(Projectile.ARROW, g.level.startX + 300f,
                    8f * Constants.TILE, 1);
            a.vx = Constants.ARROW_SPEED;
            g.projectiles.add(a);
        }, g -> {
            Projectile a = g.projectiles.get(0);
            g.camera.snapTo(a.x, a.y, g.level);
        });
        shot(dir, "weapon-boomerang.png", 700, 400, 6f, 1, Game.State.PLAYING, g -> {
            g.projectiles.clear();
            Projectile b = new Projectile(Projectile.BOOMERANG, g.level.startX + 300f,
                    8f * Constants.TILE, 1);
            b.vx = -Constants.ARROW_SPEED * 0.4f;
            b.phase = 0.9f;
            g.projectiles.add(b);
        }, g -> {
            Projectile b = g.projectiles.get(0);
            g.camera.snapTo(b.x, b.y, g.level);
        });

        // ---- title screen ----
        shot(dir, "title.png", 1600, 900, 0f, 0, Game.State.MENU, g -> {
        }, null);

        // ---- world map, zoomed out to show the geography ----
        shot(dir, "map.png", 2400, 1080, -1f, 0, Game.State.LEVEL_SELECT, g -> {
            g.profile.unlocked = 8;
            g.profile.markCleared(0, 900);
            g.profile.markCleared(1, 1200);
            g.profile.markCleared(2, 800);
            g.openLevelSelect();
            g.mapCamera.focusOn(75f, 50f, 0.55f);
        }, null);

        System.out.println("done -> " + dir.getAbsolutePath());
    }

    /**
     * Render one frame. {@code zoom > 0} focuses the in-level camera on the
     * player (pose 0) or pose 1's override; {@code zoom == -1} leaves the
     * map camera alone.
     */
    private static void shot(File dir, String name, float w, float h, float zoom, int pose,
                             Game.State state, Consumer<Game> setup, Consumer<Game> post) {
        Renderer r = new Renderer();
        Game g = new Game(new RendererSmoke.NullSound());
        g.resize(w, h);
        g.loadLevel(0);
        if (setup != null) {
            setup.accept(g);
        }
        g.state = state;
        for (int i = 0; i < 5; i++) {
            g.update(1f / 60f);
        }
        if (zoom > 0f) {
            g.camera.resize(w, h);
            g.camera.zoom = zoom;
            g.camera.viewW = w / zoom;
            g.camera.viewH = h / zoom;
            if (pose == 0) {
                g.camera.snapTo(g.player.centerX(), g.player.centerY(), g.level);
            }
        }
        if (post != null) {
            post.accept(g);
        }
        RendererSmoke.RasterCanvas c = new RendererSmoke.RasterCanvas((int) w, (int) h);
        r.draw(g, c, w, h);
        c.save(new File(dir, name).getPath());
        System.out.println("wrote " + name + " (" + c.drawCount + " draws)");
    }
}
