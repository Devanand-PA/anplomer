package com.example.anplomer;

/**
 * Headless self-test: compiles only the pure-Java half of the game (no Android
 * classes) and drives each level with a simple rightward-playing bot, then
 * exercises the systems that the bot cannot reach (careers, the store,
 * power-ups and persistence).
 *
 * It answers the questions that matter before touching a device:
 *   1. does the simulation run for tens of thousands of frames without
 *      throwing,
 *   2. is every level actually completable (bot reaches the goal with lives
 *      to spare),
 *   3. do power-ups, upgrades, the coin wallet and the career save rules do
 *      exactly what the UI implies?
 *
 * Run from the repo root:  tools/run_headless_test.sh [level ...]
 * Set ANPLOMER_TRACE=1 to print every jump decision.
 */
public final class HeadlessTest {

    private static final float DT = 1f / 60f;
    private static final int MAX_FRAMES = 60 * 240;   // 4 simulated minutes
    private static final boolean TRACE = System.getenv("ANPLOMER_TRACE") != null;

    private static boolean failures;

    private static final class Silent implements SoundSink {
        @Override
        public void play(String name) {
        }

        @Override
        public void setEnabled(boolean enabled) {
        }

        @Override
        public boolean isEnabled() {
            return false;
        }
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            for (String a : args) {
                if (!runLevel(Integer.parseInt(a) - 1)) {
                    failures = true;
                }
            }
        } else {
            for (int level = 0; level < Levels.TEXT.length; level++) {
                if (!runLevel(level)) {
                    failures = true;
                }
            }
            if (!smokeFlow()) {
                failures = true;
            }
            if (!saveFlow()) {
                failures = true;
            }
            if (!coinCollectorFlow()) {
                failures = true;
            }
            if (!powerUpFlow()) {
                failures = true;
            }
            if (!careerFlow()) {
                failures = true;
            }
            if (!storeFlow()) {
                failures = true;
            }
            if (!walletFlow()) {
                failures = true;
            }
        }
        System.out.println(failures ? "HEADLESS TEST FAILED" : "HEADLESS TEST PASSED");
        System.exit(failures ? 1 : 0);
    }

    // ------------------------------------------------------------------
    // level completion
    // ------------------------------------------------------------------

    private static boolean runLevel(int level) {
        Game game = new Game(new Silent());
        game.newGame();               // state = PLAYING on level 0
        if (level > 0) {
            game.loadLevel(level);
        }
        game.lives = 3;

        Bot bot = new Bot(game);
        int frames = 0;
        boolean prevGround = true;
        float lastX = game.player.x;
        float stuckFor = 0f;
        int deaths = 0;
        int traceFrames = 0;
        int maxLives = 3;
        boolean wasAlive = true;

        while (frames < MAX_FRAMES) {
            bot.think();
            game.update(DT);
            frames++;
            if (TRACE && frames % 1 == 0 && bot.pressed) {
                traceFrames = 34;
            }
            if (traceFrames > 0 && game.state == Game.State.PLAYING) {
                traceFrames--;
                System.out.printf("      f col=%.2f y=%.1f vy=%.0f g=%b hold=%d%n",
                        game.player.centerX() / Constants.TILE, game.player.y,
                        game.player.vy, game.player.onGround, bot.jumpHold);
            }
            if (TRACE && game.state == Game.State.PLAYING && !game.player.dead) {
                boolean g = game.player.onGround;
                if (prevGround && !g) {
                    System.out.printf("   air  col=%.1f y=%.0f vy=%.0f pressed=%b above='%s' box=[%.0f..%.0f]%n",
                            game.player.centerX() / Constants.TILE, game.player.y, game.player.vy,
                            bot.pressed,
                            game.level.tileAtWorld(game.player.centerX(), game.player.y - 4f),
                            game.player.x, game.player.x + game.player.w);
                } else if (!prevGround && g) {
                    System.out.printf("   land col=%.1f y=%.0f%n",
                            game.player.centerX() / Constants.TILE, game.player.y);
                }
                prevGround = g;
            }

            if (TRACE && game.state == Game.State.PLAYING && !game.player.dead) {
                for (Enemy e : game.enemies) {
                    if (e.dead || e.type != Enemy.Type.WALKER) {
                        continue;
                    }
                    float dx = e.centerX() - game.player.centerX();
                    if (Math.abs(dx) < 220f) {
                        System.out.printf("   near walker dx=%.0f col=%.2f why=%s cd=%d%n",
                                dx, game.player.centerX() / Constants.TILE, bot.why,
                                bot.jumpCooldown);
                    }
                }
            }

            if (game.state == Game.State.LEVEL_CLEARED || game.state == Game.State.WIN) {
                System.out.printf("level %d OK  (%.1fs sim, %d frames, score %d, coins %d/%d, deaths %d)%n",
                        level + 1, frames * DT, frames, game.score, game.coinsTaken,
                        game.level.coinCount(), deaths);
                return true;
            }
            if (game.state == Game.State.GAME_OVER) {
                System.out.printf("level %d FAILED: game over after %.1fs (x=%.0f)%n",
                        level + 1, frames * DT, game.player.x);
                return false;
            }
            if (game.lives < maxLives) {
                deaths += maxLives - game.lives;
                maxLives = game.lives;
            }
            if (wasAlive && game.player.dead) {
                wasAlive = false;
                if (TRACE) {
                    describeDeath(game);
                }
            } else if (!game.player.dead && game.state == Game.State.PLAYING) {
                wasAlive = true;
            }

            float x = game.player.x;
            if (x > lastX + 1f) {
                stuckFor = 0f;
                lastX = x;
            } else if (x < lastX - 96f) {
                stuckFor = 0f;      // respawned (or backtracked): re-anchor
                lastX = x;
            } else {
                stuckFor += DT;
                if (stuckFor > 25f) {
                    dumpStuck(game, level, stuckFor);
                    return false;
                }
            }
        }
        System.out.printf("level %d FAILED: did not finish within %ds%n", level + 1, MAX_FRAMES / 60);
        return false;
    }

    private static void describeDeath(Game game) {
        Player p = game.player;
        float cx = p.centerX();
        float feet = p.y + p.h;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("   death at x=%.0f y=%.0f (col %d row %d)", cx, feet,
                (int) (cx / Constants.TILE), (int) (feet / Constants.TILE)));
        sb.append(" tile-below='").append(game.level.tileAtWorld(cx, feet + 6f)).append("'");
        sb.append(" tile-head='").append(game.level.tileAtWorld(cx, feet - 40f)).append("'");
        for (Enemy e : game.enemies) {
            if (!e.dead && Math.abs(e.centerX() - cx) < 140f) {
                sb.append(e.type == Enemy.Type.FLYER ? " flyer@" : " walker@")
                        .append((int) (e.centerX() / Constants.TILE));
            }
        }
        for (MovingPlatform mp : game.platforms) {
            if (cx > mp.x - 70f && cx < mp.x + mp.w + 70f) {
                sb.append(String.format(" plat@%d..%d y=%.0f",
                        (int) (mp.x / Constants.TILE),
                        (int) ((mp.x + mp.w) / Constants.TILE), mp.y));
            }
        }
        System.out.println(sb);
    }

    private static void dumpStuck(Game game, int level, float stuckFor) {
        Player p = game.player;
        System.out.printf("level %d FAILED: stuck at x=%.0f for %.0fs (state=%s)%n",
                level + 1, p.x, stuckFor, game.state);
        System.out.printf("   pos=(%.0f,%.0f) v=(%.0f,%.0f) onGround=%s dead=%s%n",
                p.x, p.y, p.vx, p.vy, p.onGround, p.dead);
        int col = (int) (p.centerX() / Constants.TILE);
        int row = (int) ((p.y + p.h) / Constants.TILE);
        System.out.println("   map window around col " + col + ", row " + row);
        for (int r = Math.max(0, row - 5); r <= Math.min(Constants.LEVEL_ROWS - 1, row + 1); r++) {
            StringBuilder line = new StringBuilder("   ");
            for (int c = Math.max(0, col - 6); c <= Math.min(game.level.w - 1, col + 8); c++) {
                line.append(game.level.tileAt(c, r));
            }
            System.out.println(line);
        }
    }

    // ------------------------------------------------------------------
    // shared helpers
    // ------------------------------------------------------------------

    private static final class MemStore implements SaveStore {
        String blob;

        @Override
        public String load() {
            return blob;
        }

        @Override
        public void store(String b) {
            blob = b;
        }
    }

    private static Game.UiButton findButton(Iterable<Game.UiButton> buttons, int id) {
        for (Game.UiButton b : buttons) {
            if (b.id == id) {
                return b;
            }
        }
        return null;
    }

    /** Tap the centre of a menu button on the given game. */
    private static void tapButton(Game game, Game.UiButton b) {
        game.input.pointerDown(1, b.x + b.w / 2f, b.y + b.h / 2f);
        game.update(DT);
        game.input.pointerUp(1);
        game.update(DT);
    }

    /** Teleports the player into the goal rect (same formula as Game). */
    private static void teleportToGoal(Game g) {
        float T = Constants.TILE;
        g.player.x = g.level.goalX + 8f;
        g.player.y = g.level.goalY - T;
    }

    /** Drops a power-up exactly where the player is standing. */
    private static void addPower(Game g, PowerUp.Type type) {
        g.powerUps.add(new PowerUp(type, g.player.centerX(), g.player.centerY(), 9999, 0f));
    }

    private static boolean fail(String label, String why) {
        System.out.println(label + " FAILED: " + why);
        return false;
    }

    /** Advances a few frames so respawn/overlay timers can run out. */
    private static void tick(Game g, int frames) {
        for (int i = 0; i < frames; i++) {
            g.update(DT);
        }
    }

    // ------------------------------------------------------------------
    // smoke: menu -> play -> pause -> resume -> resize
    // ------------------------------------------------------------------

    private static boolean smokeFlow() {
        Game game = new Game(new Silent());
        if (game.state != Game.State.MENU) {
            return fail("smoke", "expected MENU, got " + game.state);
        }
        Game.UiButton start = findButton(game.menuButtons, Game.BTN_START);
        if (start == null) {
            return fail("smoke", "no START button on the menu");
        }
        game.input.pointerDown(1, start.x + start.w / 2f, start.y + start.h / 2f);
        game.update(DT);
        game.input.pointerUp(1);
        game.update(DT);
        if (game.state != Game.State.PLAYING) {
            return fail("smoke", "tapping START did not begin play (" + game.state + ")");
        }
        game.input.key(InputController.KEY_RIGHT, true);
        for (int i = 0; i < 300; i++) {
            game.update(DT);
        }
        if (game.player.x <= game.level.startX) {
            return fail("smoke", "player never moved right");
        }
        game.input.key(InputController.KEY_PAUSE, true);
        game.update(DT);
        if (game.state != Game.State.PAUSED) {
            return fail("smoke", "pause key ignored (" + game.state + ")");
        }
        float pausedX = game.player.x;
        tick(game, 60);
        if (Math.abs(game.player.x - pausedX) > 0.01f) {
            return fail("smoke", "world kept ticking while paused");
        }
        game.input.key(InputController.KEY_BACK, true);
        game.update(DT);
        if (game.state != Game.State.PLAYING) {
            return fail("smoke", "back did not resume (" + game.state + ")");
        }
        game.resize(2400, 1080);
        game.resize(1080, 2400);
        game.resize(2400, 1080);
        game.update(DT);
        System.out.println("smoke OK: menu -> play -> pause -> resume -> resize");
        return true;
    }

    // ------------------------------------------------------------------
    // save: autosave, continue, retry-at-saved-level, v3 round trip
    // ------------------------------------------------------------------

    private static boolean saveFlow() {
        MemStore store = new MemStore();
        Game game = new Game(new Silent(), store);

        if (findButton(game.menuButtons, Game.BTN_CONTINUE) != null) {
            return fail("save", "CONTINUE offered with an empty save");
        }
        game.newGame();
        teleportToGoal(game);
        game.update(DT);
        if (game.state != Game.State.LEVEL_CLEARED) {
            return fail("save", "reaching the goal did not clear level 1 ("
                    + game.state + ")");
        }
        tick(game, 40);                        // overlay ignores taps for 0.45 s
        game.input.pointerDown(1, 400, 240);
        game.update(DT);
        game.input.pointerUp(1);
        game.update(DT);
        if (game.levelIndex != 1) {
            return fail("save", "level did not advance (index " + game.levelIndex + ")");
        }
        if (store.blob == null || !store.blob.startsWith(SaveState.VERSION + "|")) {
            return fail("save", "store does not hold a v" + SaveState.VERSION
                    + " blob: " + store.blob);
        }
        SaveState saved = SaveState.decode(store.blob);
        if (saved.profiles.size() != 1 || saved.profile().levelIndex != 1
                || saved.profile().unlocked < 2) {
            return fail("save", "blob does not record one career at level 2: " + store.blob);
        }

        // new session: CONTINUE appears and resumes the saved level
        Game g2 = new Game(new Silent(), store);
        Game.UiButton cont = findButton(g2.menuButtons, Game.BTN_CONTINUE);
        if (cont == null) {
            return fail("save", "CONTINUE missing after progress");
        }
        if (!cont.label.contains("2")) {
            return fail("save", "CONTINUE label wrong: " + cont.label);
        }
        tapButton(g2, cont);
        if (g2.state != Game.State.PLAYING || g2.levelIndex != 1) {
            return fail("save", "continue did not resume level 2 ("
                    + g2.state + ", index " + g2.levelIndex + ")");
        }

        // drain all lives -> game over -> retry lands on the SAVED level
        float bottom = 15f * Constants.TILE + 400f;
        for (int i = 0; i < 900 && g2.state != Game.State.GAME_OVER; i++) {
            if (!g2.player.dead) {
                g2.player.y = bottom;
            }
            g2.update(DT);
        }
        if (g2.state != Game.State.GAME_OVER) {
            return fail("save", "could not reach game over (state " + g2.state + ")");
        }
        tick(g2, 40);
        g2.input.pointerDown(1, 400, 240);
        g2.update(DT);
        g2.input.pointerUp(1);
        g2.update(DT);
        if (g2.state != Game.State.PLAYING || g2.levelIndex != 1) {
            return fail("save", "retry did not reset to saved level 2 ("
                    + g2.state + ", index " + g2.levelIndex + ")");
        }
        if (g2.lives != Constants.START_LIVES) {
            return fail("save", "retry did not restore lives (" + g2.lives + ")");
        }

        // career + settings round trip through the v3 encoding
        SaveState s = SaveState.defaults();
        Profile p = s.profile();
        p.name = "ZOE";
        p.coins = 42;
        p.levelIndex = 1;
        p.unlocked = 2;
        p.bestScore = 1234;
        p.upgrades[Upgrades.SPEED] = 3;
        s.mode = 1;
        s.sound = false;
        s.music = 55;
        s.buttons = 120;
        Profile second = new Profile("MAX", 2);
        second.coins = 7;
        second.levelIndex = 0;
        second.upgrades[Upgrades.SHIELD] = 1;
        s.addProfile(second);
        SaveState back = SaveState.decode(s.encode());
        if (back.profiles.size() != 2 || back.active != 1
                || !back.profile().name.equals("MAX")
                || back.profile().coins != 7
                || back.profile().upgradeLevel(Upgrades.SHIELD) != 1
                || !back.profiles.get(0).name.equals("ZOE")
                || back.profiles.get(0).coins != 42
                || back.profiles.get(0).levelIndex != 1
                || back.profiles.get(0).unlocked != 2
                || back.profiles.get(0).bestScore != 1234
                || back.profiles.get(0).upgradeLevel(Upgrades.SPEED) != 3
                || back.mode != 1 || back.sound || back.music != 55
                || back.buttons != 120) {
            return fail("save", "SaveState v3 round trip broken: " + s.encode()
                    + " -> " + back.encode());
        }

        // a v2 blob keeps its level, mode and settings as one career
        SaveState legacy = SaveState.decode("2|1|2|0|1|70|100");
        if (legacy.profiles.size() != 1 || legacy.profile().levelIndex != 1
                || legacy.profile().unlocked != 2 || legacy.mode != 0
                || !legacy.sound || legacy.music != 70 || legacy.buttons != 100) {
            return fail("save", "v2 migration broken: " + legacy.encode());
        }
        // ... and a v1 blob still loads its progress
        SaveState ancient = SaveState.decode("1|2|3|1|0");
        if (ancient.profile().levelIndex != 2 || ancient.profile().unlocked != 3
                || ancient.mode != 1 || ancient.sound) {
            return fail("save", "v1 migration broken: " + ancient.encode());
        }
        SaveState garbage = SaveState.decode("garbage");
        if (garbage.profiles.size() != 1 || garbage.profile().levelIndex != 0) {
            return fail("save", "malformed blob did not fall back to defaults");
        }
        System.out.println("save OK: v3 autosave, continue, retry-at-saved-level, "
                + "career round trip, v1/v2 migration");
        return true;
    }

    // ------------------------------------------------------------------
    // coin collector gate
    // ------------------------------------------------------------------

    private static boolean coinCollectorFlow() {
        MemStore store = new MemStore();
        Game game = new Game(new Silent(), store);
        Game.UiButton mode = findButton(game.menuButtons, Game.BTN_MODE);
        if (mode == null) {
            return fail("coinrush", "no MODE button");
        }
        tapButton(game, mode);
        if (game.mode != Game.Mode.COIN_COLLECTOR) {
            return fail("coinrush", "MODE button did not switch mode");
        }
        SaveState mid = SaveState.decode(store.blob);
        if (mid.mode != Game.Mode.COIN_COLLECTOR.ordinal()) {
            return fail("coinrush", "mode not persisted: " + store.blob);
        }
        tapButton(game, findButton(game.menuButtons, Game.BTN_START));
        if (game.state != Game.State.PLAYING || game.mode != Game.Mode.COIN_COLLECTOR) {
            return fail("coinrush", "start did not enter coin collector");
        }
        if (!game.goalLocked() || game.coinGoalRequired() <= 0) {
            return fail("coinrush", "goal not locked at level start");
        }
        teleportToGoal(game);
        game.update(DT);
        if (game.state != Game.State.PLAYING) {
            return fail("coinrush", "locked goal still cleared the level");
        }
        for (int i = 0; i < game.coinGoalRequired(); i++) {
            game.level.takeCoin(i);
        }
        if (game.goalLocked()) {
            return fail("coinrush", "goal still locked after collecting enough");
        }
        teleportToGoal(game);
        game.update(DT);
        if (game.state != Game.State.LEVEL_CLEARED) {
            return fail("coinrush", "open goal did not clear the level");
        }
        System.out.println("coinrush OK: gate locked, then opens at the threshold");
        return true;
    }

    // ------------------------------------------------------------------
    // power-ups
    // ------------------------------------------------------------------

    private static boolean powerUpFlow() {
        Game g = new Game(new Silent());
        g.newGame();
        if (g.state != Game.State.PLAYING) {
            return fail("powerups", "game did not start");
        }

        addPower(g, PowerUp.Type.SPEED);
        g.update(DT);
        if (g.speedBoostTimer <= 0f) {
            return fail("powerups", "SPEED pickup did not start its timer");
        }
        g.update(DT);                      // multipliers apply from the next frame
        if (g.player.speedMul <= 1f) {
            return fail("powerups", "SPEED pickup did not raise the run multiplier ("
                    + g.player.speedMul + ")");
        }

        addPower(g, PowerUp.Type.SHIELD);
        g.update(DT);
        if (g.shieldCharges != 1) {
            return fail("powerups", "SHIELD pickup did not add a charge ("
                    + g.shieldCharges + ")");
        }

        addPower(g, PowerUp.Type.MAGNET);
        g.update(DT);
        if (g.magnetTimer <= 0f) {
            return fail("powerups", "MAGNET pickup did not start its timer");
        }

        addPower(g, PowerUp.Type.DOUBLE_JUMP);
        g.update(DT);
        if (g.doubleJumpTimer <= 0f || !g.player.doubleJumpEnabled) {
            return fail("powerups", "DOUBLE_JUMP pickup did not enable an air jump");
        }

        int livesBefore = g.lives;
        addPower(g, PowerUp.Type.EXTRA_LIFE);
        g.update(DT);
        if (g.lives != livesBefore + 1) {
            return fail("powerups", "EXTRA_LIFE pickup did not add a life ("
                    + livesBefore + " -> " + g.lives + ")");
        }

        // timers run out
        g.speedBoostTimer = 0.02f;
        g.magnetTimer = 0.02f;
        g.doubleJumpTimer = 0.02f;
        g.update(0.05f);
        if (g.speedBoostTimer != 0f || g.magnetTimer != 0f || g.doubleJumpTimer != 0f) {
            return fail("powerups", "timed power-ups did not expire");
        }

        // a shield absorbs exactly one hit, then the next one kills
        Game h = new Game(new Silent());
        h.newGame();
        int lives = h.lives;
        h.shieldCharges = 1;
        h.invulnTimer = 0f;
        h.hurtPlayer();
        if (h.player.dead || h.shieldCharges != 0 || h.invulnTimer <= 0f) {
            return fail("powerups", "shield did not absorb the first hit");
        }
        h.invulnTimer = 0f;
        h.hurtPlayer();
        if (!h.player.dead) {
            return fail("powerups", "second hit did not kill through the spent shield");
        }
        h.player.deadTimer = 0f;
        h.update(DT);
        if (h.lives != lives - 1) {
            return fail("powerups", "death did not cost exactly one life ("
                    + lives + " -> " + h.lives + ")");
        }

        // magnet collects coins the player never touches
        Game m = new Game(new Silent());
        m.newGame();
        Coin coin = m.coins.get(0);
        m.player.x = coin.x - 200f;
        m.player.y = coin.y - 10f;
        int takenBefore = m.coinsTaken;
        m.magnetTimer = 5f;
        m.update(DT);
        if (m.player.dead) {
            return fail("powerups", "magnet test setup killed the player");
        }
        if (m.coinsTaken <= takenBefore) {
            return fail("powerups", "magnet did not pull a coin from 200 px");
        }

        // picked-up power-ups stay taken across a respawn
        int withPower = -1;
        for (int i = 0; i < Levels.TEXT.length; i++) {
            Game probe = new Game(new Silent());
            probe.loadLevel(i);
            if (probe.level.powerUpCount() > 0) {
                withPower = i;
                break;
            }
        }
        if (withPower >= 0) {
            Game p = new Game(new Silent());
            p.loadLevel(withPower);
            p.state = Game.State.PLAYING;
            p.lives = 3;
            PowerUp pu = p.powerUps.get(0);
            int idx = pu.index;
            p.player.x = pu.x - p.player.w / 2f;
            p.player.y = pu.y - p.player.h / 2f;
            p.update(DT);
            if (!p.level.powerUpTaken(idx)) {
                return fail("powerups", "power-up pickup was not recorded on the level");
            }
            p.killPlayer();
            p.player.deadTimer = 0f;
            p.update(DT);
            if (!p.level.powerUpTaken(idx)) {
                return fail("powerups", "power-up was forgotten across a respawn");
            }
            for (int i = 0; i < p.powerUps.size(); i++) {
                if (p.powerUps.get(i).index == idx) {
                    return fail("powerups", "collected power-up respawned after death");
                }
            }
        }
        System.out.println("powerups OK: all five pickups, timer expiry, one-hit shield, "
                + "magnet, persistence across respawn"
                + (withPower >= 0 ? "" : " (no level placeable yet)"));
        return true;
    }

    // ------------------------------------------------------------------
    // careers
    // ------------------------------------------------------------------

    private static boolean careerFlow() {
        MemStore store = new MemStore();
        Game g = new Game(new Silent(), store);
        if (g.profiles.size() != 1 || g.profile == null || g.activeProfile != 0) {
            return fail("career", "fresh save did not start with one active career");
        }
        g.profile.coins = 50;
        g.newProfile();
        if (g.profiles.size() != 2 || g.activeProfile != 1) {
            return fail("career", "newProfile did not create and activate a career");
        }
        if (g.profile.coins != 0 || !g.profile.name.equals("PLAYER 2")) {
            return fail("career", "new career inherited the old wallet or name: "
                    + g.profile.name + " / " + g.profile.coins);
        }

        int levelA = Math.min(2, Levels.TEXT.length - 1);
        int levelB = 0;
        g.profiles.get(0).levelIndex = levelA;
        g.profiles.get(1).levelIndex = levelB;
        g.selectProfile(0);
        if (g.levelIndex != levelA) {
            return fail("career", "switching career did not load its saved level ("
                    + g.levelIndex + " != " + levelA + ")");
        }
        g.selectProfile(1);
        if (g.levelIndex != levelB) {
            return fail("career", "second career kept the wrong level ("
                    + g.levelIndex + " != " + levelB + ")");
        }
        if (g.profiles.get(0).coins != 50 || g.profiles.get(1).coins != 0) {
            return fail("career", "wallets are not per-career");
        }

        // active career and its wallet survive a new session
        Game g2 = new Game(new Silent(), store);
        if (g2.profiles.size() != 2 || g2.activeProfile != 1
                || !g2.profile.name.equals("PLAYER 2")) {
            return fail("career", "active career did not survive save/load (active="
                    + g2.activeProfile + ", " + g2.profile.name + ")");
        }
        g2.profiles.get(0).coins = 77;
        g2.selectProfile(0);
        Game g3 = new Game(new Silent(), store);
        if (g3.activeProfile != 0 || g3.profile.coins != 77) {
            return fail("career", "selected career wallet did not persist");
        }

        // delete is two-tap and never removes the last career
        int before = g3.profiles.size();
        g3.deleteProfileTap();
        if (g3.profiles.size() != before) {
            return fail("career", "one tap deleted a career");
        }
        g3.deleteProfileTap();
        if (g3.profiles.size() != before - 1) {
            return fail("career", "confirmed delete did not remove the active career");
        }
        g3.deleteProfileTap();
        g3.deleteProfileTap();
        if (g3.profiles.size() != 1) {
            return fail("career", "the last career was deleted");
        }
        System.out.println("career OK: create/select/two-tap delete, per-career wallets "
                + "and levels, active career persisted");
        return true;
    }

    // ------------------------------------------------------------------
    // store
    // ------------------------------------------------------------------

    private static boolean storeFlow() {
        Game g = new Game(new Silent());
        Profile p = g.profile;
        p.coins = 1000;

        int price = p.nextPrice(Upgrades.SPEED);
        int coinsBefore = p.coins;
        int levelBefore = p.upgradeLevel(Upgrades.SPEED);
        g.buyUpgrade(Upgrades.SPEED);
        if (p.upgradeLevel(Upgrades.SPEED) != levelBefore + 1
                || p.coins != coinsBefore - price) {
            return fail("store", "purchase did not raise the level or deduct the price");
        }
        if (!g.storeMsg.contains("PURCHASED")) {
            return fail("store", "no purchase feedback");
        }
        if (!(p.nextPrice(Upgrades.SPEED) > price)) {
            return fail("store", "price did not rise with the level");
        }

        // not enough coins: refused, nothing changes
        p.coins = 0;
        int jumpLevel = p.upgradeLevel(Upgrades.JUMP);
        g.buyUpgrade(Upgrades.JUMP);
        if (p.upgradeLevel(Upgrades.JUMP) != jumpLevel || p.coins != 0
                || !g.storeMsg.contains("NOT ENOUGH")) {
            return fail("store", "purchase without coins was not refused");
        }

        // max level: refused, nothing changes
        p.coins = 1000000;
        while (p.nextPrice(Upgrades.SHIELD) >= 0) {
            g.buyUpgrade(Upgrades.SHIELD);
        }
        if (p.upgradeLevel(Upgrades.SHIELD) != Upgrades.MAX[Upgrades.SHIELD]) {
            return fail("store", "could not buy the upgrade to max");
        }
        int coinsAtMax = p.coins;
        g.buyUpgrade(Upgrades.SHIELD);
        if (!g.storeMsg.contains("MAX") || p.coins != coinsAtMax) {
            return fail("store", "max-level purchase was not refused ("
                    + g.storeMsg + ", " + p.coins + ")");
        }

        // purchased effects reach the simulation
        p.upgrades[Upgrades.SPEED] = 2;
        p.upgrades[Upgrades.JUMP] = 2;
        p.upgrades[Upgrades.LIVES] = 1;
        p.upgrades[Upgrades.SHIELD] = 1;
        p.upgrades[Upgrades.POWER] = 2;
        p.upgrades[Upgrades.MAGNET] = 1;
        if (!(p.runSpeedMul() > 1f) || !(p.jumpMul() > 1f)) {
            return fail("store", "derived multipliers do not reflect the upgrades");
        }
        g.newGame();
        if (g.state != Game.State.PLAYING
                || g.lives != Constants.START_LIVES + 1) {
            return fail("store", "LIVES upgrade did not raise starting lives ("
                    + g.lives + ")");
        }
        if (g.shieldCharges != 1) {
            return fail("store", "STARTER SHIELD upgrade did not grant a charge ("
                    + g.shieldCharges + ")");
        }
        g.update(DT);
        if (!(g.player.speedMul > 1f)) {
            return fail("store", "SPEED upgrade did not reach the player ("
                    + g.player.speedMul + ")");
        }
        addPower(g, PowerUp.Type.SPEED);
        g.update(DT);
        if (!(g.speedBoostTimer > Constants.POWERUP_TIME)) {
            return fail("store", "POWER upgrade did not lengthen the power-up ("
                    + g.speedBoostTimer + ")");
        }
        addPower(g, PowerUp.Type.MAGNET);
        g.update(DT);
        float magnetBase = p.magnetRadius();
        if (magnetBase <= 0f) {
            return fail("store", "MAGNET upgrade radius is zero");
        }
        Game mag = new Game(new Silent());
        mag.profile.upgrades[Upgrades.MAGNET] = 1;
        mag.newGame();
        Coin coin = mag.coins.get(0);
        mag.player.x = coin.x - (magnetBase - 12f);
        mag.player.y = coin.y - 10f;
        int taken = mag.coinsTaken;
        mag.update(DT);
        if (!mag.player.dead && mag.coinsTaken <= taken) {
            return fail("store", "MAGNET upgrade did not attract a nearby coin");
        }
        System.out.println("store OK: prices, deduction, refusal, max clamp, and every "
                + "upgrade effect reaches the simulation");
        return true;
    }

    // ------------------------------------------------------------------
    // wallet payout on level clear
    // ------------------------------------------------------------------

    private static boolean walletFlow() {
        Game g = new Game(new Silent());
        g.newGame();
        int before = g.profile.coins;
        teleportToGoal(g);
        g.update(DT);
        if (g.state != Game.State.LEVEL_CLEARED) {
            return fail("wallet", "goal did not clear the level");
        }
        if (g.rewardCoins != Constants.COINS_LEVEL_CLEAR) {
            return fail("wallet", "clean-sheet clear paid the wrong reward ("
                    + g.rewardCoins + ")");
        }
        if (g.profile.coins < before + g.rewardCoins) {
            return fail("wallet", "reward did not reach the career wallet ("
                    + before + " + " + g.rewardCoins + " != " + g.profile.coins + ")");
        }
        int after = g.profile.coins;
        tick(g, 10);
        if (g.profile.coins != after) {
            return fail("wallet", "clear reward was paid more than once");
        }
        if (g.profile.levelsCleared < 1) {
            return fail("wallet", "career clear count not recorded");
        }
        System.out.println("wallet OK: clear pays " + Constants.COINS_LEVEL_CLEAR
                + " coins once, into the active career");
        return true;
    }

    // ------------------------------------------------------------------

    /** A crude but deterministic rightward-playing bot. */
    private static final class Bot {
        private final Game game;
        private final InputController in;
        int jumpCooldown;
        int jumpHold;
        private String why = "";
        private float landingDy;
        private boolean shortHop;
        boolean pressed;

        Bot(Game game) {
            this.game = game;
            this.in = game.input;
        }

        void think() {
            Player p = game.player;
            if (p.dead) {
                in.key(InputController.KEY_RIGHT, false);
                in.key(InputController.KEY_LEFT, false);
                in.key(InputController.KEY_JUMP, false);
                return;
            }
            if (jumpCooldown > 0) {
                jumpCooldown--;
            }
            why = "";
            shortHop = false;
            pressed = false;

            float feet = p.y + p.h;
            float cx = p.centerX();
            boolean wantJump = false;

            // 1. riding a platform: hop off once real ground is in reach
            if (p.onGround && onPlatform()) {
                float d = staticLanding(cx, feet);
                if (d >= 0f && d <= 290f) {
                    wantJump = true;
                    why = "plat-exit" + (int) d;
                } else {
                    if (p.onGround) {
                        hold();
                    } else {
                        in.key(InputController.KEY_RIGHT, true);
                        in.key(InputController.KEY_LEFT, false);
                    }
                    return;
                }
            }

            // 2. hazards, walls and enemies directly ahead
            if (!wantJump) {
                if (game.level.solidOverlap(cx + 44f, feet - 72f, 18f, 64f)) {
                    wantJump = true;
                    why = "wall";
                } else if (game.level.hazardOverlap(cx + 30f, feet - 34f, 90f, 44f)) {
                    wantJump = true;
                    why = "hazard";
                } else {
                    for (Enemy e : game.enemies) {
                        if (e.dead) {
                            continue;
                        }
                        float dx = e.centerX() - cx;
                        if (dx > -30f && dx < 165f && Math.abs(e.centerY() - p.centerY()) < 90f) {
                            if (!p.onGround) {
                                break;    // mid-air: no control decisions, keep momentum
                            }
                            // a full jump always covers ~299px: never make it if
                            // that would land in a void — wait, then hop over.
                            boolean safe = staticSupport(cx + 270f, feet + 6f)
                                    && staticSupport(cx + 300f, feet + 6f)
                                    && !game.level.hazardOverlap(cx + 240f, feet - 56f, 140f, 64f);
                            if (safe) {
                                wantJump = true;
                                why = "enemy";
                            } else if (dx < 165f) {
                                // short hop travels ~200px: only when the
                                // landing area is actually solid
                                boolean hopSafe = staticSupport(cx + 180f, feet + 6f)
                                        && staticSupport(cx + 240f, feet + 6f)
                                        && !game.level.hazardOverlap(cx + 160f, feet - 56f, 120f, 64f);
                                if (hopSafe) {
                                    wantJump = true;
                                    shortHop = true;
                                    why = "enemy-hop";
                                } else {
                                    hold();
                                    return;
                                }
                            } else {
                                hold();
                                return;
                            }
                            break;
                        }
                    }
                }
            }

            // 3. gaps: jump plain ones at the ledge, wait for a ferry otherwise
            if (!wantJump && p.onGround) {
                float edge = ledgeDistance(cx, feet);
                if (edge >= 0f) {
                    float landing = staticLanding(cx + edge + 20f, feet);
                    float dy = landingDy;
                    float reach = edge + 20f + Math.max(0f, landing);
                    if (landing >= 0f && landing <= 250f) {
                        // a lower surface just ahead: walking off is safer than
                        // a full-speed jump that would sail over it
                        // the player keeps its run speed while falling: check that
                        // the actual landing spot (not just 20px ahead) has ground
                        float fallX = cx + Constants.RUN_SPEED
                                * (float) Math.sqrt(2f * Math.max(dy, 1f) / Constants.GRAVITY);
                        boolean walkOff = dy > 24f
                                && staticSupport(fallX, feet + dy + 6f)
                                && staticSupport(fallX + 40f, feet + dy + 6f)
                                && !game.level.hazardOverlap(fallX - 20f, feet + dy - 56f, 40f, 64f)
                                && !game.level.hazardOverlap(fallX + 20f, feet + dy - 56f, 40f, 64f);
                        if (!walkOff && edge <= 16f) {
                            if (walkerIn(cx + edge + 10f,
                                    cx + edge + landing + 120f, feet)) {
                                // A walker owns the landing strip. Step back
                                // only when there is solid ground behind the
                                // heels; otherwise just stand still.
                                in.key(InputController.KEY_RIGHT, false);
                                if (staticSupport(cx - 70f, feet + 6f)) {
                                    in.key(InputController.KEY_LEFT, true);
                                } else {
                                    in.key(InputController.KEY_LEFT, false);
                                }
                                why = "wait-walker";
                                return;
                            }
                            wantJump = true;
                            why = "gap" + (int) landing;
                        }
                    } else {
                        MovingPlatform near = nearestPlatform(cx, feet);
                        float runFrames = Math.max(0f, edge - 20f) / Constants.RUN_SPEED * 60f;
                        float target = cx + edge + 279f;
                        boolean go = near != null && platformCovers(near, target, runFrames + 47f, feet);
                        if (!go) {
                            if (edge <= 200f) {
                                hold();
                                return;
                            }
                        } else if (edge <= 24f) {
                            wantJump = true;
                            why = "plat-edge";
                        }
                    }
                }
            }

            in.key(InputController.KEY_LEFT, false);
            in.key(InputController.KEY_RIGHT, true);
            if (jumpHold > 0) {
                jumpHold--;
                in.key(InputController.KEY_JUMP, true);      // hold through ascent
            } else if (wantJump && jumpCooldown == 0 && p.onGround) {
                jumpHold = shortHop ? 11 : 26;
                jumpCooldown = shortHop ? 14 : 22;
                in.key(InputController.KEY_JUMP, true);
                pressed = true;
                if (TRACE) {
                    System.out.printf("  jump col=%.1f why=%s feet=%.0f onPlat=%s%n",
                            cx / Constants.TILE, why, feet, onPlatform());
                }
            } else {
                in.key(InputController.KEY_JUMP, false);
            }
        }

        /** Stop and hold position (used while waiting for a ferry). */
        private void hold() {
            Player p = game.player;
            in.key(InputController.KEY_JUMP, false);
            in.key(InputController.KEY_RIGHT, false);
            in.key(InputController.KEY_LEFT, p.vx > 30f);
        }

        /** Distance to the first spot ahead with no static ground, or -1. */
        private float ledgeDistance(float cx, float feet) {
            for (float d = 0f; d <= 700f; d += 6f) {
                if (!staticSupport(cx + d, feet + 6f)) {
                    return d;
                }
            }
            return -1f;
        }

        /**
         * True when a live walker patrols the landing strip {@code x0..x1} at
         * roughly this ground level. The bot uses it to wait for a patrol to
         * walk clear instead of leaping on top of it, which is how a human
         * would play a fair gap.
         */
        private boolean walkerIn(float x0, float x1, float feet) {
            for (Enemy e : game.enemies) {
                if (e.dead || e.type != Enemy.Type.WALKER) {
                    continue;
                }
                float ex = e.centerX();
                if (ex > x0 && ex < x1 && Math.abs((e.y + e.h) - feet) < 160f) {
                    return true;
                }
            }
            return false;
        }

        private boolean staticSupport(float x, float y) {
            return game.level.solidOverlap(x - 5f, y - 4f, 10f, 8f);
        }

        /** Distance from cx to the next static landing, or -1 when none. */
        private float staticLanding(float cx, float feet) {
            landingDy = 0f;
            for (float d = 0f; d <= 700f; d += 6f) {
                float x = cx + d;
                for (float dy = -210f; dy <= 420f; dy += 16f) {
                    if (staticSupport(x, feet + dy)) {
                        landingDy = dy;
                        return d;
                    }
                }
            }
            return -1f;
        }

        private MovingPlatform nearestPlatform(float cx, float feet) {
            MovingPlatform best = null;
            float bestD = 1e9f;
            for (MovingPlatform p : game.platforms) {
                float d = p.x - cx;
                if (d < -40f || d > 760f) {
                    continue;
                }
                if (p.y < feet - 260f || p.y > feet + 200f) {
                    continue;
                }
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
            return best;
        }

        /** Where will the platform be in {@code frames} frames, and will it cover x? */
        private boolean platformCovers(MovingPlatform p, float x, float frames, float feet) {
            float px = p.x + p.dx * frames;
            float py = p.y + p.dy * frames;
            return x > px - 14f && x < px + p.w + 14f
                    && py < feet + 170f && py > feet - 260f;
        }

        private boolean onPlatform() {
            for (MovingPlatform p : game.platforms) {
                if (p.supports(game.player.x, game.player.w, game.player.y + game.player.h)) {
                    return true;
                }
            }
            return false;
        }
    }
}
