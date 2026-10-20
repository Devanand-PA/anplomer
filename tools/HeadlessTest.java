package com.example.anplomer;

import java.util.List;

/**
 * Headless self-test: compiles only the pure-Java half of the game (no Android
 * classes) and drives each level with a simple rightward-playing bot.
 *
 * It answers two questions that matter before touching a device:
 *   1. does the simulation run for tens of thousands of frames without
 *      throwing, and
 *   2. is every level actually completable (bot reaches the goal with lives
 *      to spare)?
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
        }
        System.out.println(failures ? "HEADLESS TEST FAILED" : "HEADLESS TEST PASSED");
        System.exit(failures ? 1 : 0);
    }

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

    private static boolean smokeFlow() {
        Game game = new Game(new Silent());
        if (game.state != Game.State.MENU) {
            System.out.println("smoke FAILED: expected MENU, got " + game.state);
            return false;
        }
        Game.UiButton start = null;
        for (Game.UiButton b : game.menuButtons) {
            if (b.id == Game.BTN_START) {
                start = b;
                break;
            }
        }
        if (start == null) {
            System.out.println("smoke FAILED: no START button on the menu");
            return false;
        }
        game.input.pointerDown(1, start.x + start.w / 2f, start.y + start.h / 2f);
        game.update(DT);
        game.input.pointerUp(1);
        game.update(DT);
        if (game.state != Game.State.PLAYING) {
            System.out.println("smoke FAILED: tapping START did not begin play (" + game.state + ")");
            return false;
        }
        game.input.key(InputController.KEY_RIGHT, true);
        for (int i = 0; i < 300; i++) {
            game.update(DT);
        }
        if (game.player.x <= game.level.startX) {
            System.out.println("smoke FAILED: player never moved right");
            return false;
        }
        game.input.key(InputController.KEY_PAUSE, true);
        game.update(DT);
        if (game.state != Game.State.PAUSED) {
            System.out.println("smoke FAILED: pause key ignored (" + game.state + ")");
            return false;
        }
        float pausedX = game.player.x;
        for (int i = 0; i < 60; i++) {
            game.update(DT);
        }
        if (Math.abs(game.player.x - pausedX) > 0.01f) {
            System.out.println("smoke FAILED: world kept ticking while paused");
            return false;
        }
        game.input.key(InputController.KEY_BACK, true);
        game.update(DT);
        if (game.state != Game.State.PLAYING) {
            System.out.println("smoke FAILED: back did not resume (" + game.state + ")");
            return false;
        }
        game.resize(2400, 1080);
        game.resize(1080, 2400);
        game.resize(2400, 1080);
        game.update(DT);
        System.out.println("smoke OK: menu -> play -> pause -> resume -> resize");
        return true;
    }

    /** In-memory SaveStore for the persistence tests. */
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

    /**
     * Save system: clear level 1 -> saved as level 2; new session offers
     * CONTINUE; continuing starts level 2; draining all lives and retrying
     * returns to the SAVED level, not level 1.
     */
    private static boolean saveFlow() {
        MemStore store = new MemStore();
        Game game = new Game(new Silent(), store);

        // fresh save: no CONTINUE on the menu
        if (findButton(game.menuButtons, Game.BTN_CONTINUE) != null) {
            System.out.println("save FAILED: CONTINUE offered with an empty save");
            return false;
        }
        // clear level 1 (CLASSIC: goal always open)
        game.newGame();
        teleportToGoal(game);
        game.update(DT);
        if (game.state != Game.State.LEVEL_CLEARED) {
            System.out.println("save FAILED: reaching the goal did not clear level 1 ("
                    + game.state + ")");
            return false;
        }
        // overlays ignore taps for the first 0.45 s (anti double-tap)
        for (int i = 0; i < 40; i++) {
            game.update(DT);
        }
        game.input.pointerDown(1, 400, 240);
        game.update(DT);
        game.input.pointerUp(1);
        game.update(DT);
        if (game.levelIndex != 1) {
            System.out.println("save FAILED: level did not advance (index " + game.levelIndex + ")");
            return false;
        }
        if (store.blob == null || !store.blob.startsWith("1|1|")) {
            System.out.println("save FAILED: store does not record level 2: " + store.blob);
            return false;
        }

        // new session: CONTINUE appears and resumes the saved level
        Game g2 = new Game(new Silent(), store);
        Game.UiButton cont = findButton(g2.menuButtons, Game.BTN_CONTINUE);
        if (cont == null) {
            System.out.println("save FAILED: CONTINUE missing after progress");
            return false;
        }
        if (!cont.label.contains("2")) {
            System.out.println("save FAILED: CONTINUE label wrong: " + cont.label);
            return false;
        }
        tapButton(g2, cont);
        if (g2.state != Game.State.PLAYING || g2.levelIndex != 1) {
            System.out.println("save FAILED: continue did not resume level 2 ("
                    + g2.state + ", index " + g2.levelIndex + ")");
            return false;
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
            System.out.println("save FAILED: could not reach game over (state " + g2.state + ")");
            return false;
        }
        for (int i = 0; i < 40; i++) {
            g2.update(DT);
        }
        g2.input.pointerDown(1, 400, 240);
        g2.update(DT);
        g2.input.pointerUp(1);
        g2.update(DT);
        if (g2.state != Game.State.PLAYING || g2.levelIndex != 1) {
            System.out.println("save FAILED: retry did not reset to saved level 2 ("
                    + g2.state + ", index " + g2.levelIndex + ")");
            return false;
        }
        if (g2.lives != 3) {
            System.out.println("save FAILED: retry did not restore lives");
            return false;
        }

        // encode/decode round trip
        SaveState s = SaveState.defaults();
        s.levelIndex = 2;
        s.unlocked = 3;
        s.mode = 1;
        s.sound = false;
        SaveState back = SaveState.decode(s.encode());
        if (back.levelIndex != 2 || back.unlocked != 3 || back.mode != 1 || back.sound) {
            System.out.println("save FAILED: SaveState round trip broken");
            return false;
        }
        if (SaveState.decode("garbage").levelIndex != 0) {
            System.out.println("save FAILED: malformed blob did not fall back to defaults");
            return false;
        }
        System.out.println("save OK: autosave, continue, and retry-at-saved-level");
        return true;
    }

    /**
     * Coin collector: the goal stays shut below the coin threshold and opens
     * once the mode is satisfied.
     */
    private static boolean coinCollectorFlow() {
        MemStore store = new MemStore();
        Game game = new Game(new Silent(), store);
        Game.UiButton mode = findButton(game.menuButtons, Game.BTN_MODE);
        if (mode == null) {
            System.out.println("coinrush FAILED: no MODE button");
            return false;
        }
        tapButton(game, mode);
        if (game.mode != Game.Mode.COIN_COLLECTOR) {
            System.out.println("coinrush FAILED: MODE button did not switch mode");
            return false;
        }
        if (store.blob == null || !store.blob.contains("|1|")) {
            System.out.println("coinrush FAILED: mode not persisted: " + store.blob);
            return false;
        }
        // start via START (a fresh run keeps the selected mode)
        tapButton(game, findButton(game.menuButtons, Game.BTN_START));
        if (game.state != Game.State.PLAYING || game.mode != Game.Mode.COIN_COLLECTOR) {
            System.out.println("coinrush FAILED: start did not enter coin collector");
            return false;
        }
        if (!game.goalLocked() || game.coinGoalRequired() <= 0) {
            System.out.println("coinrush FAILED: goal not locked at level start");
            return false;
        }
        // run into the goal with no coins: it must NOT clear
        teleportToGoal(game);
        game.update(DT);
        if (game.state != Game.State.PLAYING) {
            System.out.println("coinrush FAILED: locked goal still cleared the level");
            return false;
        }
        // satisfy the requirement, then the goal opens
        for (int i = 0; i < game.coinGoalRequired(); i++) {
            game.level.takeCoin(i);
        }
        if (game.goalLocked()) {
            System.out.println("coinrush FAILED: goal still locked after collecting enough");
            return false;
        }
        teleportToGoal(game);
        game.update(DT);
        if (game.state != Game.State.LEVEL_CLEARED) {
            System.out.println("coinrush FAILED: open goal did not clear the level");
            return false;
        }
        System.out.println("coinrush OK: gate locked, then opens at the threshold");
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
