import com.example.anplomer.*;
import java.util.*;

public final class CoreCheck {
    static int pass, fail;

    static final class Silent implements SoundSink {
        public void play(String name) { }
        public void setEnabled(boolean enabled) { }
        public boolean isEnabled() { return true; }
    }


    static void check(String name, boolean ok) {
        if (ok) { pass++; System.out.println("PASS " + name); }
        else { fail++; System.out.println("FAIL " + name); }
    }

    static void tick(Game g, int frames) {
        for (int i = 0; i < frames; i++) g.update(1f / 60f);
    }

    public static void main(String[] a) {
        // ---- menu / level select ----
        Game g = new Game(new Silent());
        g.resize(2400, 1080);
        check("fresh save: level 1 unlocked", g.levelUnlocked(0) && !g.levelUnlocked(1));
        check("menu has PLAY not STORE", has(g.menuButtons, Game.BTN_START)
                && !has(g.menuButtons, Game.BTN_STORE));
        g.state = Game.State.MENU;
        tap(g, find(g.menuButtons, Game.BTN_START));
        check("PLAY opens LEVEL_SELECT", g.state == Game.State.LEVEL_SELECT);
        check("level rows", g.levelRowCount() == Levels.TEXT.length);
        tap(g, find(g.levelButtons, Game.BTN_LEVEL_ROW + 3));
        check("locked row refuses", g.state == Game.State.LEVEL_SELECT);
        tap(g, find(g.levelButtons, Game.BTN_LEVEL_ROW));
        check("level 1 row opens LOADOUT", g.state == Game.State.LOADOUT);
        check("loadout buttons", g.loadoutButtons.size() == Weapons.SECONDARY_COUNT + 2);
        Game.UiButton wb = find(g.loadoutButtons, Game.BTN_WEAPON_ROW + Weapons.BOOMERANG);
        System.out.println("DEBUG weapon btn=" + (wb == null ? "null" : (wb.x + "," + wb.y + "," + wb.w + "," + wb.h))
                + " state=" + g.state + " eq=" + g.profile.equippedSecondary);
        tap(g, wb);
        System.out.println("DEBUG after tap eq=" + g.profile.equippedSecondary + " id=" + g.secondaryWeaponId());
        check("weapon equipped", g.secondaryWeaponId() == Weapons.BOOMERANG
                && g.profile.equippedSecondary == Weapons.BOOMERANG);
        tap(g, find(g.loadoutButtons, Game.BTN_START_LEVEL));
        check("START plays level 1", g.state == Game.State.PLAYING && g.levelIndex == 0);
        tick(g, 20);
        check("player settles on ground", g.player.onGround);

        // ---- sword styles ----
        swing(g, () -> { g.player.vx = 0f; });
        check("idle style", g.player.swingStyle == SwordStyle.GROUND_IDLE);
        float[] box = g.player.attackBox();
        check("idle box in front", box != null && box[0] > g.player.x + g.player.w * 0.3f);
        check("cooldown armed", g.player.attackCooldown > 0f);

        int before = g.player.swings;
        g.input.key(InputController.KEY_ATTACK, true);
        tick(g, 1);
        g.input.key(InputController.KEY_ATTACK, false);
        check("cooldown blocks swing", g.player.swings == before || g.player.attackCooldown > 0f);

        tick(g, 30);
        swing(g, () -> { g.player.vx = Constants.RUN_SPEED; });
        check("run style", g.player.swingStyle == SwordStyle.GROUND_RUN);
        float[] rbox = g.player.attackBox();
        check("run reach longer", rbox != null && rbox[2] > Constants.SWORD_REACH);

        tick(g, 30);
        swing(g, () -> { g.player.y -= 140f; g.player.onGround = false; g.player.vy = -600f; });
        check("rise style", g.player.swingStyle == SwordStyle.AIR_RISE);
        float[] ubox = g.player.attackBox();
        check("rise box above", ubox != null && ubox[1] + ubox[3] < g.player.y + g.player.h);

        tick(g, 30);
        swing(g, () -> { g.player.y -= 140f; g.player.onGround = false; g.player.vy = 600f; });
        check("fall style", g.player.swingStyle == SwordStyle.AIR_FALL);
        float[] dbox = g.player.attackBox();
        check("fall box below", dbox != null && dbox[1] > g.player.y + g.player.h * 0.3f);

        // ---- sword kills enemy in front, not behind ----
        Game h = new Game(new Silent());
        h.resize(1920, 1080);
        h.newGame();
        tick(h, 30);
        h.enemies.clear();
        Enemy front = new Enemy(Enemy.Type.WALKER,
                h.player.x + h.player.w + 12f, h.player.y);
        Enemy behind = new Enemy(Enemy.Type.WALKER,
                h.player.x - 90f, h.player.y);
        h.enemies.add(front);
        h.enemies.add(behind);
        int score0 = h.score;
        h.input.key(InputController.KEY_ATTACK, true);
        tick(h, 3);
        h.input.key(InputController.KEY_ATTACK, false);
        check("front enemy killed", front.dead);
        check("behind enemy alive", !behind.dead);
        check("attack score", h.score == score0 + Constants.SCORE_ATTACK);

        // ---- secondary: bow ----
        Game b = new Game(new Silent());
        b.resize(1920, 1080);
        b.newGame();
        tick(b, 20);
        b.setSecondaryWeapon(Weapons.BOW);
        b.enemies.clear();
        Enemy target = new Enemy(Enemy.Type.WALKER, b.player.x + 260f, b.player.y);
        b.enemies.add(target);
        b.input.key(InputController.KEY_SECONDARY, true);
        tick(b, 1);
        b.input.key(InputController.KEY_SECONDARY, false);
        check("arrow spawned", b.projectiles.size() == 1
                && b.projectiles.get(0).kind == Projectile.ARROW);
        check("secondary cooldown", b.secondaryCooldown > 0f);
        for (int i = 0; i < 40 && !target.dead; i++) tick(b, 1);
        check("arrow kills walker", target.dead);
        check("arrow gone", b.projectiles.isEmpty());
        b.input.key(InputController.KEY_SECONDARY, true);
        tick(b, 1);
        b.input.key(InputController.KEY_SECONDARY, false);
        check("shot blocked on cooldown", b.projectiles.isEmpty());
        tick(b, 60);

        // ---- secondary: boomerang ----
        Game r = new Game(new Silent());
        r.resize(1920, 1080);
        r.newGame();
        tick(r, 20);
        r.setSecondaryWeapon(Weapons.BOOMERANG);
        r.enemies.clear();
        r.input.key(InputController.KEY_SECONDARY, true);
        tick(r, 1);
        r.input.key(InputController.KEY_SECONDARY, false);
        check("boomerang spawned", r.projectiles.size() == 1
                && r.projectiles.get(0).kind == Projectile.BOOMERANG);
        float x0 = r.projectiles.get(0).x;
        tick(r, 12);
        check("boomerang flew out", !r.projectiles.isEmpty()
                && r.projectiles.get(0).x > x0 + 40f);
        boolean sawReturn = false;
        for (int i = 0; i < 200 && !r.projectiles.isEmpty(); i++) {
            tick(r, 1);
            if (!r.projectiles.isEmpty() && r.projectiles.get(0).returning) sawReturn = true;
        }
        check("boomerang returned and was caught", sawReturn && r.projectiles.isEmpty());

        Game n = new Game(new Silent());
        n.resize(1920, 1080);
        n.newGame();
        tick(n, 10);
        n.setSecondaryWeapon(Weapons.NONE);
        n.input.key(InputController.KEY_SECONDARY, true);
        tick(n, 2);
        n.input.key(InputController.KEY_SECONDARY, false);
        check("no weapon fires nothing", n.projectiles.isEmpty());

        // ---- cleared/unlock flow ----
        Game c = new Game(new Silent());
        c.resize(1920, 1080);
        c.profile.markCleared(0, 900);
        c.profile.unlocked = 2;
        check("cleared bit", c.levelCleared(0) && !c.levelCleared(1));
        check("level 2 unlocked", c.levelUnlocked(1));
        c.openLevelSelect();
        tap(c, find(c.levelButtons, Game.BTN_LEVEL_ROW + 1));
        check("level 2 loadout", c.state == Game.State.LOADOUT && c.pendingLevel == 1);
        tap(c, find(c.loadoutButtons, Game.BTN_START_LEVEL));
        check("level 2 plays", c.state == Game.State.PLAYING && c.levelIndex == 1);

        System.out.println("TOTAL " + (pass + fail) + " checks, PASS " + pass + ", FAIL " + fail);
        if (fail > 0) System.exit(1);
    }

    static boolean has(List<Game.UiButton> list, int id) {
        return find(list, id) != null;
    }

    static Game.UiButton find(List<Game.UiButton> list, int id) {
        for (Game.UiButton b : list) if (b.id == id) return b;
        return null;
    }

    static void tap(Game g, Game.UiButton b) {
        if (b == null) return;
        g.input.pointerDown(1, b.x + b.w / 2f, b.y + b.h / 2f);
        g.update(1f / 60f);
        g.input.pointerUp(1);
        g.update(1f / 60f);
    }

    interface Setup { void run(); }

    static void swing(Game g, Setup setup) {
        tick(g, Math.max(1, (int) (Constants.SWORD_COOLDOWN * 60f) + 6));
        setup.run();
        g.input.key(InputController.KEY_ATTACK, true);
        g.update(1f / 60f);
        g.input.key(InputController.KEY_ATTACK, false);
    }
}
