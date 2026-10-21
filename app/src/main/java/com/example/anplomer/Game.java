package com.example.anplomer;

import java.util.ArrayList;
import java.util.List;

/**
 * The headless-friendly heart of the game: state machine, entity bookkeeping,
 * scoring and all gameplay rules. Rendering lives in {@link Renderer}, the
 * Android shell in {@code MainActivity} / {@code GameView}.
 */
public final class Game implements Player.Listener {

    public enum State {
        MENU, PLAYING, PAUSED, LEVEL_CLEARED, GAME_OVER, WIN, SETTINGS
    }

    /**
     * Game modes. CLASSIC is the plain run-to-the-goal game. COIN_COLLECTOR
     * keeps the goal locked until a share of the level's coins is collected.
     */
    public enum Mode {
        CLASSIC("CLASSIC"), COIN_COLLECTOR("COIN COLLECTOR");

        public final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    /** Share of a level's coins required to open the goal in coin collector. */
    public static final float COIN_GOAL_SHARE = 0.6f;

    /** Simple screen-space button used by the overlays. */
    public static final class UiButton {
        /** Mutable: the reset button rewrites its label when armed. */
        public String label;
        public final int id;
        public float x, y, w, h;

        public UiButton(String label, int id) {
            this.label = label;
            this.id = id;
        }

        public boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    /**
     * Horizontal slider used by the settings overlay. ({@code x, w} span the
     * track; {@code y} is the track's vertical centre; {@code h} is the touch
     * band height.) Value mapping lives with the owner: 0 at {@code x}, 1 at
     * {@code x + w}.
     */
    public static final class UiSlider {
        public final int id;
        public float x, y, w, h;

        public UiSlider(int id) {
            this.id = id;
        }

        /** Fraction (unclamped) the given screen x maps to on the track. */
        public float fractionAt(float px) {
            return w <= 0f ? 0f : (px - x) / w;
        }

        /** Generous touch band around the whole track. */
        public boolean containsBand(float px, float py) {
            return px >= x - h * 0.6f && px <= x + w + h * 0.6f
                    && Math.abs(py - y) <= h * 0.85f;
        }
    }

    public static final int BTN_RESUME = 0;
    public static final int BTN_RESTART = 1;
    public static final int BTN_MENU = 2;

    // home-screen button ids
    public static final int BTN_START = 3;
    public static final int BTN_CONTINUE = 4;
    public static final int BTN_MODE = 5;
    public static final int BTN_SOUND = 6;
    public static final int BTN_RESET = 7;
    public static final int BTN_SETTINGS = 8;
    public static final int BTN_BACK = 9;   // settings back button

    // settings slider ids
    public static final int SLIDER_MUSIC = 0;
    public static final int SLIDER_BUTTONS = 1;

    public final InputController input = new InputController();
    public final Camera camera = new Camera();
    public final Particles particles = new Particles();
    public final Player player = new Player();

    public final List<Enemy> enemies = new ArrayList<>();
    public final List<MovingPlatform> platforms = new ArrayList<>();
    public final List<Coin> coins = new ArrayList<>();

    public SoundSink sound;
    public State state = State.MENU;

    public Level level;
    public int levelIndex;

    public int score;
    public int coinsTaken;
    public int lives = 3;
    public float levelTime;
    public float stateTime;
    public float worldTime;

    public int bonusTime;
    public int bonusCoins;

    public float screenWidth;
    public float screenHeight;
    public Controls controls;
    public final UiButton[] pauseButtons = new UiButton[]{
            new UiButton("RESUME", BTN_RESUME),
            new UiButton("RESTART", BTN_RESTART),
            new UiButton("SETTINGS", BTN_SETTINGS),
            new UiButton("MENU", BTN_MENU)};

    /** Settings overlay: two sliders plus a back button. */
    public final UiSlider[] sliders = {
            new UiSlider(SLIDER_MUSIC),
            new UiSlider(SLIDER_BUTTONS)};
    public final UiButton[] settingsButtons = {new UiButton("BACK", BTN_BACK)};

    /** Music volume percent (0..100); mirrors {@link SaveState#music}. */
    public int musicVolume = 70;
    /** Movement button size percent (50..150); mirrors {@link SaveState#buttons}. */
    public int moveScalePct = 100;

    /** Home-screen buttons; CONTINUE is only present when a save exists. */
    public final List<UiButton> menuButtons = new ArrayList<>();

    /** Selected game mode. */
    public Mode mode = Mode.CLASSIC;

    // ---- persistence ----------------------------------------------------
    private final SaveStore store;
    /** Loaded at construction; written back whenever something changes. */
    private SaveState save = SaveState.defaults();
    /** Arms the two-tap confirmation for resetting the save. */
    private boolean resetArmed;
    private float resetArmTimer;
    /** Seconds left to flash the "goal locked" message in coin collector. */
    public float goalLockedTimer;

    /** State to return to when the settings overlay closes. */
    private State settingsReturn = State.MENU;
    /** Scratch buffer for reading touch positions while dragging sliders. */
    private final float[] pointerBuf = new float[8];

    public boolean checkpointActive;
    private float checkpointX, checkpointY;
    private final float[] tap = new float[2];

    public Game(SoundSink sound) {
        this(sound, null);
    }

    /**
     * @param store persistence boundary; null disables saving (headless tests).
     */
    public Game(SoundSink sound, SaveStore store) {
        this.sound = sound;
        this.store = store;
        if (store != null) {
            save = SaveState.decode(store.load());
            mode = Mode.values()[save.mode];
        }
        musicVolume = save.music;
        moveScalePct = save.buttons;
        if (sound != null) {
            sound.setEnabled(save.sound);
            sound.setMusicVolume(save.music / 100f);
        }
        // Default canvas so menu/pause buttons are laid out even before the
        // first resize arrives; the loop thread resizes on the real surface.
        screenWidth = 1080f;
        screenHeight = 480f;
        controls = Controls.compute(screenWidth, screenHeight, moveScalePct / 100f);
        rebuildMenuButtons();
        layoutMenuButtons();
        layoutPauseButtons();
        layoutSettings();
        loadLevel(Math.min(save.levelIndex, Levels.TEXT.length - 1));
    }

    // ---- sizing -------------------------------------------------------

    public void resize(float w, float h) {
        if (w <= 0f || h <= 0f) {
            return;
        }
        boolean first = screenWidth <= 0f;
        screenWidth = w;
        screenHeight = h;
        controls = Controls.compute(w, h, moveScalePct / 100f);
        input.setScreen(w, h, moveScalePct / 100f);
        camera.resize(w, h);
        if (first || level == null) {
            camera.snapTo(player.centerX(), player.centerY(), level);
        }
        layoutPauseButtons();
        layoutMenuButtons();
        layoutSettings();
    }

    private void layoutPauseButtons() {
        float bw = Math.min(screenWidth * 0.42f, 520f);
        // Four buttons now: slightly slimmer so the stack still fits with a
        // title above and a hint line below on short screens.
        float bh = Math.max(48f, screenHeight * 0.095f);
        float gap = bh * 0.35f;
        float x = (screenWidth - bw) / 2f;
        float total = bh * pauseButtons.length + gap * (pauseButtons.length - 1);
        float y = (screenHeight - total) / 2f;
        for (UiButton b : pauseButtons) {
            b.x = x;
            b.y = y;
            b.w = bw;
            b.h = bh;
            y += bh + gap;
        }
    }

    /** Rebuilds the home-screen button set for the current save state. */
    private void rebuildMenuButtons() {
        menuButtons.clear();
        menuButtons.add(new UiButton("START GAME", BTN_START));
        if (save.levelIndex > 0) {
            menuButtons.add(new UiButton("CONTINUE  •  LEVEL " + (save.levelIndex + 1), BTN_CONTINUE));
        }
        menuButtons.add(new UiButton("MODE:  " + mode.label, BTN_MODE));
        menuButtons.add(new UiButton("SOUND:  " + (save.sound ? "ON" : "OFF"), BTN_SOUND));
        menuButtons.add(new UiButton("SETTINGS", BTN_SETTINGS));
        menuButtons.add(new UiButton(resetArmed ? "TAP AGAIN TO CONFIRM" : "RESET SAVE", BTN_RESET));
        layoutMenuButtons();
    }

    private void layoutMenuButtons() {
        if (menuButtons.isEmpty() || screenWidth <= 0f || screenHeight <= 0f) {
            return;
        }
        float bw = Math.min(screenWidth * 0.42f, 560f);
        // Five buttons in the worst case: keep them compact so the stack fits
        // between the subtitle and the reset button on short screens.
        float bh = Math.max(42f, screenHeight * 0.08f);
        float gap = bh * 0.26f;
        float x = (screenWidth - bw) / 2f;
        float top = screenHeight * 0.29f;
        for (UiButton b : menuButtons) {
            if (b.id == BTN_RESET) {
                continue;   // laid out separately below
            }
            b.x = x;
            b.y = top;
            b.w = bw;
            b.h = bh;
            top += bh + gap;
        }
        for (UiButton b : menuButtons) {
            if (b.id != BTN_RESET) {
                continue;
            }
            b.w = bw * 0.52f;
            b.h = Math.max(30f, bh * 0.42f);
            b.x = (screenWidth - b.w) / 2f;
            b.y = screenHeight * 0.93f - b.h / 2f;
        }
    }

    /** Serialises and stores the current save state (no-op without a store). */
    private void persist() {
        save.mode = mode.ordinal();
        save.sound = sound != null && sound.isEnabled();
        save.music = musicVolume;
        save.buttons = moveScalePct;
        if (store != null) {
            store.store(save.encode());
        }
    }

    /** Recomputes control geometry after the movement-button size changes. */
    private void applyControlScale() {
        float scale = moveScalePct / 100f;
        controls = Controls.compute(screenWidth, screenHeight, scale);
        input.setScreen(screenWidth, screenHeight, scale);
    }

    // ---- settings overlay ----------------------------------------------

    /** Opens the settings overlay, remembering where to return. */
    private void openSettings() {
        settingsReturn = state == State.PAUSED ? State.PAUSED : State.MENU;
        layoutSettings();
        setState(State.SETTINGS);
        sound.play(SoundSink.SELECT);
    }

    private void closeSettings() {
        persist();
        setState(settingsReturn);
        if (settingsReturn == State.MENU) {
            rebuildMenuButtons();   // keep SOUND/CONTINUE labels honest
        }
        sound.play(SoundSink.SELECT);
    }

    /** Panel geometry, shared with the renderer so drawing and hit tests agree. */
    public float settingsPanelW() {
        float scale = Math.min(screenWidth, screenHeight) / 480f;
        return Math.min(screenWidth * 0.74f, 760f * scale);
    }

    public float settingsPanelH() {
        float scale = Math.min(screenWidth, screenHeight) / 480f;
        return Math.min(screenHeight * 0.8f, 500f * scale);
    }

    private void layoutSettings() {
        float scale = Math.min(screenWidth, screenHeight) / 480f;
        float pw = settingsPanelW();
        float ph = settingsPanelH();
        float left = (screenWidth - pw) / 2f;
        float top = (screenHeight - ph) / 2f;

        float trackW = Math.min(pw * 0.66f, 500f * scale);
        float trackX = (screenWidth - trackW) / 2f;
        sliders[SLIDER_MUSIC].x = trackX;
        sliders[SLIDER_MUSIC].y = top + ph * 0.34f;
        sliders[SLIDER_MUSIC].w = trackW;
        sliders[SLIDER_MUSIC].h = 48f * scale;
        sliders[SLIDER_BUTTONS].x = trackX;
        sliders[SLIDER_BUTTONS].y = top + ph * 0.60f;
        sliders[SLIDER_BUTTONS].w = trackW;
        sliders[SLIDER_BUTTONS].h = 48f * scale;

        float bw = Math.min(pw * 0.44f, 360f * scale);
        float bh = Math.max(42f, Math.min(ph * 0.15f, 74f * scale));
        UiButton back = settingsButtons[0];
        back.w = bw;
        back.h = bh;
        back.x = (screenWidth - bw) / 2f;
        back.y = top + ph * 0.9f - bh / 2f;
    }

    private void updateSettings() {
        if (input.consumeBack()) {
            closeSettings();
            return;
        }
        // While a finger is anywhere in a slider's band it owns that slider:
        // this covers taps on the track and continuous drags alike.
        int n = input.pointerPositions(pointerBuf);
        for (int i = 0; i < n; i++) {
            float px = pointerBuf[i * 2];
            float py = pointerBuf[i * 2 + 1];
            for (UiSlider s : sliders) {
                if (s.containsBand(px, py)) {
                    applySlider(s.id, s.fractionAt(px));
                }
            }
        }
        if (input.consumeTap(tap)) {
            for (UiButton b : settingsButtons) {
                if (b.contains(tap[0], tap[1])) {
                    closeSettings();
                    return;
                }
            }
        }
    }

    private void applySlider(int id, float fraction) {
        float f = Math.max(0f, Math.min(1f, fraction));
        if (id == SLIDER_MUSIC) {
            int v = Math.round(f * (SaveState.MUSIC_MAX - SaveState.MUSIC_MIN))
                    + SaveState.MUSIC_MIN;
            if (v != musicVolume) {
                musicVolume = v;
                save.music = v;
                sound.setMusicVolume(v / 100f);
                persist();
            }
        } else if (id == SLIDER_BUTTONS) {
            int pct = Math.round(f * (SaveState.BUTTONS_MAX - SaveState.BUTTONS_MIN))
                    + SaveState.BUTTONS_MIN;
            if (pct != moveScalePct) {
                moveScalePct = pct;
                save.buttons = pct;
                persist();
                applyControlScale();
            }
        }
    }

    /** Continue on the last saved level with a fresh run. */
    private void continueGame() {
        score = 0;
        coinsTaken = 0;
        lives = 3;
        input.consumePause();   // swallow edges armed on other screens
        input.consumeMute();
        loadLevel(Math.min(save.levelIndex, Levels.TEXT.length - 1));
        setState(State.PLAYING);
        sound.play(SoundSink.SELECT);
    }

    private void toggleMode() {
        mode = mode == Mode.CLASSIC ? Mode.COIN_COLLECTOR : Mode.CLASSIC;
        save.mode = mode.ordinal();
        persist();
        rebuildMenuButtons();
        sound.play(SoundSink.SELECT);
    }

    private void toggleSound() {
        boolean on = !(sound != null && sound.isEnabled());
        sound.setEnabled(on);
        save.sound = on;
        persist();
        rebuildMenuButtons();
        if (on) {
            sound.play(SoundSink.SELECT);
        }
    }

    /** Two-tap confirmation, then wipe progress back to defaults. */
    private void resetSaveTap() {
        UiButton reset = null;
        for (UiButton b : menuButtons) {
            if (b.id == BTN_RESET) {
                reset = b;
                break;
            }
        }
        if (!resetArmed) {
            resetArmed = true;
            resetArmTimer = 3f;
            if (reset != null) {
                reset.label = "TAP AGAIN TO CONFIRM";
            }
            sound.play(SoundSink.SELECT);
            return;
        }
        resetArmed = false;
        if (reset != null) {
            reset.label = "RESET SAVE";
        }
        save = SaveState.defaults();
        persist();
        mode = Mode.CLASSIC;
        musicVolume = save.music;
        moveScalePct = save.buttons;
        if (sound != null) {
            sound.setEnabled(save.sound);
            sound.setMusicVolume(save.music / 100f);
        }
        applyControlScale();
        rebuildMenuButtons();
        sound.play(SoundSink.SELECT);
    }

    // ---- flow ---------------------------------------------------------

    public void loadLevel(int index) {
        levelIndex = Math.max(0, Math.min(index, Levels.TEXT.length - 1));
        level = new Level(levelRows(levelIndex), Levels.PAR[levelIndex]);
        levelTime = 0f;
        checkpointActive = false;
        goalLockedTimer = 0f;
        spawnEntities();
        player.respawn(level.startX, level.startY);
        camera.resize(Math.max(screenWidth, 1f), Math.max(screenHeight, 1f));
        camera.snapTo(player.centerX(), player.centerY(), level);
        // Auto-save: the level just entered becomes the continue point, so
        // losing all lives restarts here instead of at the first level.
        save.levelIndex = levelIndex;
        save.unlocked = Math.max(save.unlocked, Math.min(levelIndex + 1, Levels.TEXT.length));
        persist();
    }

    /** Rebuild enemies/platforms/coins; collected coins stay collected. */
    private void spawnEntities() {
        enemies.clear();
        platforms.clear();
        coins.clear();

        int idx = 0;
        for (float[] s : level.coinSpawns) {
            if (!level.coinTaken(idx)) {
                coins.add(new Coin(s[0], s[1], idx, (float) Math.random() * 6.28f));
            }
            idx++;
        }
        for (float[] s : level.walkerSpawns) {
            enemies.add(new Enemy(Enemy.Type.WALKER, s[0], s[1]));
        }
        for (float[] s : level.flyerSpawns) {
            enemies.add(new Enemy(Enemy.Type.FLYER, s[0], s[1]));
        }
        for (float[] s : level.moverSpawns) {
            boolean vertical = s[2] > 0.5f;
            float tx = s[0] / Constants.TILE;
            float ty = s[1] / Constants.TILE;
            float bx = tx * Constants.TILE + Constants.TILE / 2f - Constants.PLATFORM_W / 2f;
            float by = vertical
                    ? ty * Constants.TILE + Constants.TILE / 2f - Constants.PLATFORM_H / 2f
                    : ty * Constants.TILE + Constants.TILE - Constants.PLATFORM_H;
            platforms.add(new MovingPlatform(bx, by, vertical, tx * 1.7f + ty * 0.9f));
        }
    }

    public void newGame() {
        score = 0;
        coinsTaken = 0;
        lives = 3;
        input.consumePause();   // swallow edges armed on other screens
        input.consumeMute();
        loadLevel(0);
        setState(State.PLAYING);
        sound.play(SoundSink.SELECT);
    }

    /** Called when the app is backgrounded so the run does not keep ticking. */
    public void forcePause() {
        if (state == State.PLAYING) {
            setState(State.PAUSED);
        }
    }

    public void quitToMenu() {
        persist();
        rebuildMenuButtons();
        setState(State.MENU);
        particles.clear();
    }

    private void setState(State s) {
        state = s;
        stateTime = 0f;
    }

    public void restartLevel() {
        int keepScore = Math.max(0, score - 0);
        level = new Level(levelRows(levelIndex), Levels.PAR[levelIndex]);
        levelTime = 0f;
        checkpointActive = false;
        coinsTaken = level.coinsTaken();
        score = keepScore;
        spawnEntities();
        player.respawn(level.startX, level.startY);
        camera.snapTo(player.centerX(), player.centerY(), level);
        setState(State.PLAYING);
        sound.play(SoundSink.SELECT);
    }

    // ---- main update --------------------------------------------------

    public void update(float dt) {
        worldTime += dt;
        stateTime += dt;

        if (resetArmTimer > 0f) {
            resetArmTimer -= dt;
            if (resetArmTimer <= 0f) {
                resetArmed = false;
                rebuildMenuButtons();
            }
        }
        if (goalLockedTimer > 0f) {
            goalLockedTimer -= dt;
        }

        if (input.consumeMute()) {
            sound.setEnabled(!sound.isEnabled());
            save.sound = sound.isEnabled();
            persist();
            if (state == State.MENU) {
                rebuildMenuButtons();   // keep the SOUND button label honest
            }
            if (sound.isEnabled()) {
                sound.play(SoundSink.SELECT);
            }
        }

        switch (state) {
            case MENU:
                updateMenu();
                break;
            case PLAYING:
                updatePlaying(dt);
                break;
            case PAUSED:
                updatePaused();
                break;
            case LEVEL_CLEARED:
            case GAME_OVER:
            case WIN:
                updateResult();
                break;
            case SETTINGS:
                updateSettings();
                break;
            default:
                break;
        }

        particles.update(dt);
        input.clearTap();
    }

    private void updateMenu() {
        // Keyboard confirm starts a fresh run; touch uses the buttons.
        if (input.consumeConfirm()) {
            newGame();
            return;
        }
        if (input.consumeTap(tap)) {
            for (UiButton b : menuButtons) {
                if (!b.contains(tap[0], tap[1])) {
                    continue;
                }
                switch (b.id) {
                    case BTN_START:
                        newGame();
                        break;
                    case BTN_CONTINUE:
                        continueGame();
                        break;
                    case BTN_MODE:
                        toggleMode();
                        break;
                    case BTN_SOUND:
                        toggleSound();
                        break;
                    case BTN_SETTINGS:
                        openSettings();
                        break;
                    case BTN_RESET:
                        resetSaveTap();
                        break;
                    default:
                        break;
                }
                return;
            }
        }
    }

    private void updatePaused() {
        if (input.consumeConfirm() || input.consumeBack()) {
            setState(State.PLAYING);
            sound.play(SoundSink.SELECT);
            return;
        }
        if (input.consumeRestart()) {
            restartLevel();
            return;
        }
        if (input.consumeTap(tap)) {
            for (UiButton b : pauseButtons) {
                if (b.contains(tap[0], tap[1])) {
                    switch (b.id) {
                        case BTN_RESUME:
                            setState(State.PLAYING);
                            sound.play(SoundSink.SELECT);
                            break;
                        case BTN_RESTART:
                            restartLevel();
                            break;
                        case BTN_SETTINGS:
                            openSettings();
                            break;
                        case BTN_MENU:
                            quitToMenu();
                            sound.play(SoundSink.SELECT);
                            break;
                        default:
                            break;
                    }
                    return;
                }
            }
        }
    }

    private void updateResult() {
        if (stateTime < 0.45f) {
            return;
        }
        if (!input.consumeConfirm() && !input.consumeTap(tap)) {
            return;
        }
        sound.play(SoundSink.SELECT);
        if (state == State.LEVEL_CLEARED) {
            int next = levelIndex + 1;
            if (next >= Levels.TEXT.length) {
                setState(State.WIN);
                sound.play(SoundSink.WIN);
            } else {
                loadLevel(next);
                setState(State.PLAYING);
            }
        } else if (state == State.GAME_OVER) {
            // Losing all lives resets to the last saved level, not level 1.
            continueGame();
        } else {
            setState(State.MENU);
        }
    }

    // ---- playing ------------------------------------------------------

    private void updatePlaying(float dt) {
        input.consumeConfirm();   // discard menu confirms so they cannot leak
        if (input.consumePause() || input.consumeBack()) {
            setState(State.PAUSED);
            sound.play(SoundSink.PAUSE);
            return;
        }
        if (input.consumeRestart()) {
            restartLevel();
            return;
        }

        levelTime += dt;

        // 1. platforms move, carrying anyone standing on them
        for (MovingPlatform p : platforms) {
            boolean riding = !player.dead && player.onGround
                    && p.supports(player.x, player.w, player.y + player.h);
            p.update(dt);
            if (riding) {
                player.x += p.dx;
                player.y += p.dy;
            }
        }
        player.separateFrom(platforms);

        // 2. enemies
        for (Enemy e : enemies) {
            e.update(dt, level);
        }

        // 3. player physics
        player.update(dt, input, level, platforms, this);

        // 4. player vs enemies
        if (!player.dead) {
            for (Enemy e : enemies) {
                if (e.dead) {
                    continue;
                }
                if (!Level.rectsOverlap(player.x, player.y, player.w, player.h, e.x, e.y, e.w, e.h)) {
                    continue;
                }
                boolean stomping = player.vy > 0f && player.prevBottom <= e.y + e.h * 0.5f;
                if (stomping) {
                    e.dead = true;
                    e.deadTimer = Constants.SQUASH_TIME;
                    player.vy = -Constants.STOMP_BOUNCE;
                    player.squash = 1.32f;
                    score += Constants.SCORE_STOMP;
                    camera.shake(7f, 0.18f);
                    sound.play(SoundSink.STOMP);
                    particles.poof(e.centerX(), e.y + 8f, 10, 0xFF8BD0FF);
                    particles.dust(e.centerX(), e.y + e.h, 5, e.facing);
                } else {
                    killPlayer();
                    break;
                }
            }
        }

        // 5. hazards & pits
        if (!player.dead) {
            boolean hit = level.hazardOverlap(player.x + 7f, player.y + 10f, player.w - 14f, player.h - 14f);
            boolean fell = player.y > (level.h + 1) * Constants.TILE;
            if (hit || fell) {
                killPlayer();
            }
        }

        // 6. coins
        if (!player.dead) {
            for (int i = 0; i < coins.size(); i++) {
                Coin c = coins.get(i);
                if (level.coinTaken(c.index)) {
                    continue;
                }
                if (c.overlaps(player.x, player.y, player.w, player.h)) {
                    level.takeCoin(c.index);
                    coinsTaken++;
                    score += Constants.SCORE_COIN;
                    sound.play(SoundSink.COIN);
                    particles.sparkle(c.x, c.y, 9);
                }
            }
        }

        // 7. checkpoints
        if (!player.dead && !checkpointActive) {
            for (float[] cp : level.checkpointSpawns) {
                if (Level.rectsOverlap(player.x, player.y, player.w, player.h,
                        cp[0], cp[1], Constants.TILE, Constants.TILE * 1.6f)) {
                    checkpointActive = true;
                    checkpointX = cp[0] + (Constants.TILE - player.w) / 2f;
                    checkpointY = cp[1] + Constants.TILE - player.h;
                    sound.play(SoundSink.CHECKPOINT);
                    particles.sparkle(cp[0] + Constants.TILE / 2f, cp[1] + 20f, 14);
                    break;
                }
            }
        }

        // 8. goal
        if (!player.dead) {
            float gx = level.goalX + 8f;
            float gy = level.goalY - Constants.TILE;
            float gw = Constants.TILE - 16f;
            float gh = Constants.TILE * 2f;
            if (Level.rectsOverlap(player.x, player.y, player.w, player.h, gx, gy, gw, gh)) {
                if (goalLocked()) {
                    // Coin collector: the gate is shut until enough coins are
                    // banked. Flash the hint; the player is not blocked here,
                    // the gate simply does not open.
                    goalLockedTimer = 1.2f;
                } else {
                    levelCleared();
                    return;
                }
            }
        }

        // 9. death bookkeeping
        if (player.dead && player.deadTimer <= 0f) {
            lives--;
            if (lives <= 0) {
                lives = 0;
                setState(State.GAME_OVER);
                sound.play(SoundSink.GAME_OVER);
            } else {
                respawnPlayer();
            }
        }

        // 10. camera
        float lookX = player.facingRight ? 78f : -78f;
        float tx = player.centerX() + lookX;
        float ty = player.centerY() - 46f;
        camera.update(dt, tx, ty, level);
    }

    private void levelCleared() {
        int left = Math.max(0, level.parTime - (int) levelTime);
        bonusTime = left * Constants.SCORE_TIME_UNIT;
        boolean allCoins = level.coinCount() > 0 && level.coinsTaken() >= level.coinCount();
        bonusCoins = allCoins ? Constants.SCORE_ALL_COINS : 0;
        score += bonusTime + bonusCoins;
        setState(State.LEVEL_CLEARED);
        sound.play(SoundSink.CLEAR);
        camera.shake(6f, 0.25f);
        particles.confetti(player.centerX(), player.centerY() - 30f, 44);
    }

    public void killPlayer() {
        if (player.dead) {
            return;
        }
        player.dead = true;
        player.deadTimer = Constants.RESPAWN_DELAY;
        player.vy = -Constants.DEATH_POP;
        player.vx = 0f;
        sound.play(SoundSink.DIE);
        camera.shake(12f, 0.35f);
        particles.poof(player.centerX(), player.centerY(), 16, 0xFFF4F4F4);
        particles.poof(player.centerX(), player.centerY(), 8, 0xFFE4453A);
    }

    private void respawnPlayer() {
        float rx = checkpointActive ? checkpointX : level.startX;
        float ry = checkpointActive ? checkpointY : level.startY;
        spawnEntities();
        player.respawn(rx, ry);
        camera.snapTo(player.centerX(), player.centerY(), level);
        particles.respawnBurst(player.centerX(), player.centerY(), 14);
    }

    // ---- Player.Listener ---------------------------------------------

    @Override
    public void onJump() {
        sound.play(SoundSink.JUMP);
        particles.dust(player.x + player.w / 2f, player.y + player.h, 4, player.facingRight ? 1f : -1f);
        player.squash = 1.3f;
    }

    @Override
    public void onLand(float impactSpeed) {
        if (impactSpeed > 700f) {
            camera.shake(Math.min(7f, impactSpeed / 240f), 0.14f);
            sound.play(SoundSink.SELECT);
        }
        particles.landPuff(player.x + player.w / 2f, player.y + player.h);
    }

    @Override
    public void onStep() {
        particles.dust(player.x + player.w / 2f - (player.facingRight ? 12f : -12f),
                player.y + player.h, 1, player.facingRight ? 1f : -1f);
    }

    // ---- helpers used by the renderer --------------------------------

    /** Level grids are stored as one multi-line string; split and trim them. */
    private static String[] levelRows(int index) {
        String[] all = Levels.TEXT[index].split("\n", -1);
        int n = all.length;
        while (n > 0 && all[n - 1].trim().isEmpty()) {
            n--;
        }
        return java.util.Arrays.copyOf(all, n);
    }

    public String levelLabel() {
        return "LEVEL " + (levelIndex + 1);
    }

    /** True while the coin-collector gate is still shut. */
    public boolean goalLocked() {
        return mode == Mode.COIN_COLLECTOR && level.coinCount() > 0
                && level.coinsTaken() < coinGoalRequired();
    }

    /** Coins needed to open the goal in coin-collector mode. */
    public int coinGoalRequired() {
        if (level.coinCount() == 0) {
            return 0;
        }
        return Math.max(1, Math.round(level.coinCount() * COIN_GOAL_SHARE));
    }

    public String timeLabel() {
        int t = (int) levelTime;
        return String.format(java.util.Locale.US, "%d:%02d", t / 60, t % 60);
    }
}
