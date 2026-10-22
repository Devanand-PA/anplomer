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
        MENU, PLAYING, PAUSED, LEVEL_CLEARED, GAME_OVER, WIN, SETTINGS, CAREER, STORE, HELP
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
    public static final int BTN_BACK = 9;
    public static final int BTN_HELP = 14;   // settings/career/store back button
    public static final int BTN_CAREER = 10;
    public static final int BTN_STORE = 11;
    public static final int BTN_NEW_PROFILE = 12;
    public static final int BTN_DELETE_PROFILE = 13;

    /** Career rows are {@code BTN_PROFILE_ROW + profileIndex}. */
    public static final int BTN_PROFILE_ROW = 100;
    /** Store rows are {@code BTN_UPGRADE_ROW + Upgrades id}. */
    public static final int BTN_UPGRADE_ROW = 200;

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
    public final List<PowerUp> powerUps = new ArrayList<>();

    public SoundSink sound;
    public State state = State.MENU;

    public Level level;
    public int levelIndex;

    public int score;
    public int coinsTaken;
    public int lives = Constants.START_LIVES;
    public float levelTime;
    public float stateTime;
    public float worldTime;

    public int bonusTime;
    public int bonusCoins;
    /** Coins banked into the career wallet for this level clear. */
    public int rewardCoins;

    /** Coins picked up since the current run started (HUD + results screen). */
    public int runCoins;

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
    /** Career screen: one row per profile, then NEW / DELETE / BACK. */
    public final List<UiButton> careerButtons = new ArrayList<>();
    /** Buttons of the how-to-play overlay (just Back). */
    public final List<UiButton> helpButtons = new ArrayList<>();
    /** Store screen: one row per upgrade, then BACK. */
    public final List<UiButton> storeButtons = new ArrayList<>();

    /** Selected game mode. */
    public Mode mode = Mode.CLASSIC;

    // ---- careers -------------------------------------------------------
    /** Live career list (same Profile instances the save holds). */
    public final List<Profile> profiles = new ArrayList<>();
    /** Index of the active career in {@link #profiles}. */
    public int activeProfile;
    /** The active career; never null. */
    public Profile profile;

    // ---- power-up state ------------------------------------------------
    public float speedBoostTimer;
    public float magnetTimer;
    public float doubleJumpTimer;
    /** Grace period after a shield soaks a hit. */
    public float invulnTimer;
    /** Shield charges; each absorbs one hit. */
    public int shieldCharges;

    // ---- persistence ---------------------------------------------------
    private final SaveStore store;
    /** Loaded at construction; written back whenever something changes. */
    private SaveState save = SaveState.defaults();
    /** Arms the two-tap confirmation for resetting the save. */
    private boolean resetArmed;
    private float resetArmTimer;
    /** Arms the two-tap confirmation for deleting the active career. */
    private boolean deleteArmed;
    private float deleteArmTimer;
    /** Seconds left to flash the "goal locked" message in coin collector. */
    public float goalLockedTimer;

    /** Store feedback line ("PURCHASED", "NOT ENOUGH COINS", ...). */
    public String storeMsg = "";
    public float storeMsgTimer;
    /** Career feedback line ("CAREER FULL", "KEEP ONE CAREER", ...). */
    public String careerMsg = "";
    public float careerMsgTimer;

    /** State to return to when the settings overlay closes. */
    private State settingsReturn = State.MENU;
    /** Scratch buffer for reading touch positions while dragging sliders. */
    private final float[] pointerBuf = new float[8];

    public boolean checkpointActive;
    private float checkpointX, checkpointY;
    private final float[] tap = new float[2];

    // cached HUD strings (the renderer asks every frame; do not allocate)
    private int timeLabelSecond = -1;
    private String timeLabelCache = "0:00";
    private int levelLabelIndex = -1;
    private String levelLabelCache = "";

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
        } else {
            save = SaveState.defaults();
        }
        syncProfiles();
        mode = Mode.values()[Math.max(0, Math.min(save.mode, Mode.values().length - 1))];
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
        rebuildCareerButtons();
        rebuildStoreButtons();
        layoutPauseButtons();
        layoutSettings();
        lives = profile.startLives();
        loadLevel(profile.levelIndex);
    }

    // ---- careers -------------------------------------------------------

    /** Re-points the live career list at the save's list and re-selects. */
    private void syncProfiles() {
        profiles.clear();
        profiles.addAll(save.profiles);
        if (profiles.isEmpty()) {
            save.profiles.add(new Profile("PLAYER 1", 0));
            profiles.addAll(save.profiles);
        }
        activeProfile = Math.max(0, Math.min(save.active, profiles.size() - 1));
        save.active = activeProfile;
        profile = profiles.get(activeProfile);
    }

    /** Switches the active career, carrying its own progress into the menu. */
    public void selectProfile(int index) {
        if (index < 0 || index >= profiles.size()) {
            return;
        }
        activeProfile = index;
        save.active = index;
        profile = profiles.get(index);
        deleteArmed = false;
        loadLevel(profile.levelIndex);
        persist();
        rebuildMenuButtons();
        rebuildCareerButtons();
        sound.play(SoundSink.SELECT);
    }

    /** Creates a career with an auto-name and makes it active. */
    public void newProfile() {
        if (profiles.size() >= SaveState.MAX_PROFILES) {
            careerMsg = "CAREER FULL";
            careerMsgTimer = 2f;
            sound.play(SoundSink.SELECT);
            return;
        }
        String name = Profile.suggestName(profiles);
        Profile p = new Profile(name, profiles.size() % Profile.COLORS);
        save.profiles.add(p);
        save.active = save.profiles.size() - 1;
        syncProfiles();
        deleteArmed = false;
        loadLevel(profile.levelIndex);
        persist();
        rebuildMenuButtons();
        rebuildCareerButtons();
        sound.play(SoundSink.POWERUP);
    }

    /** Two-tap delete of the active career; the last one is kept. */
    public void deleteProfileTap() {
        if (profiles.size() <= 1) {
            careerMsg = "CANNOT DELETE LAST CAREER";
            careerMsgTimer = 2f;
            sound.play(SoundSink.SELECT);
            return;
        }
        if (!deleteArmed) {
            deleteArmed = true;
            deleteArmTimer = 3f;
            rebuildCareerButtons();
            sound.play(SoundSink.SELECT);
            return;
        }
        deleteArmed = false;
        save.removeProfile(activeProfile);
        syncProfiles();
        loadLevel(profile.levelIndex);
        persist();
        rebuildMenuButtons();
        rebuildCareerButtons();
        careerMsg = "CAREER DELETED";
        careerMsgTimer = 1.8f;
        sound.play(SoundSink.SELECT);
    }

    // ---- store ---------------------------------------------------------

    /** Buys the next level of an upgrade for the active career. */
    public void buyUpgrade(int id) {
        int price = profile.nextPrice(id);
        if (price < 0) {
            storeMsg = "MAX LEVEL";
            storeMsgTimer = 1.8f;
            sound.play(SoundSink.SELECT);
            return;
        }
        if (profile.coins < price) {
            storeMsg = "NOT ENOUGH COINS";
            storeMsgTimer = 1.8f;
            sound.play(SoundSink.SELECT);
            return;
        }
        profile.buy(id);
        storeMsg = "PURCHASED  " + Upgrades.NAME[id];
        storeMsgTimer = 2f;
        sound.play(SoundSink.PURCHASE);
        persist();
        rebuildStoreButtons();
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
        layoutCareerButtons();
        layoutStoreButtons();
        layoutHelp();
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
        if (profile.levelIndex > 0) {
            menuButtons.add(new UiButton("CONTINUE  •  LEVEL " + (profile.levelIndex + 1),
                    BTN_CONTINUE));
        }
        menuButtons.add(new UiButton("MODE:  " + mode.label, BTN_MODE));
        menuButtons.add(new UiButton("CAREER", BTN_CAREER));
        menuButtons.add(new UiButton("STORE", BTN_STORE));
        menuButtons.add(new UiButton("SETTINGS", BTN_SETTINGS));
        menuButtons.add(new UiButton("HOW TO PLAY", BTN_HELP));
        menuButtons.add(new UiButton("SOUND:  " + (save.sound ? "ON" : "OFF"), BTN_SOUND));
        menuButtons.add(new UiButton(resetArmed ? "TAP AGAIN TO CONFIRM" : "RESET SAVE", BTN_RESET));
        layoutMenuButtons();
    }

    /**
     * Three groups: a main column (START / CONTINUE / MODE), a row of the
     * secondary screens (CAREER / STORE / SETTINGS) and a small bottom row
     * (SOUND / RESET SAVE). Grouping keeps the stack short enough to fit on a
     * 480 px-tall landscape screen with the title above it.
     */
    private void layoutMenuButtons() {
        if (menuButtons.isEmpty() || screenWidth <= 0f || screenHeight <= 0f) {
            return;
        }
        float bw = Math.min(screenWidth * 0.42f, 560f);
        float bh = Math.max(40f, screenHeight * 0.072f);
        float gap = bh * 0.24f;
        float x = (screenWidth - bw) / 2f;
        float top = screenHeight * 0.27f;

        for (UiButton b : menuButtons) {
            if (!isMenuColumn(b.id)) {
                continue;
            }
            b.x = x;
            b.y = top;
            b.w = bw;
            b.h = bh;
            top += bh + gap;
        }

        int rowCount = 0;
        for (UiButton b : menuButtons) {
            if (isMenuRow(b.id)) {
                rowCount++;
            }
        }
        if (rowCount > 0) {
            float rowH = bh * 0.88f;
            float rw = (bw - gap * (rowCount - 1)) / rowCount;
            float rx = x;
            for (UiButton b : menuButtons) {
                if (!isMenuRow(b.id)) {
                    continue;
                }
                b.x = rx;
                b.y = top;
                b.w = rw;
                b.h = rowH;
                rx += rw + gap;
            }
            top += rowH + gap;
        }

        float smallH = Math.max(30f, bh * 0.46f);
        int smallCount = 0;
        for (UiButton b : menuButtons) {
            if (isMenuSmall(b.id)) {
                smallCount++;
            }
        }
        smallCount = Math.max(1, smallCount);
        float smallW = (bw - gap * (smallCount - 1)) / smallCount;
        float totalW = smallW * smallCount + gap * (smallCount - 1);
        float sx = (screenWidth - totalW) / 2f;
        float sy = screenHeight * 0.94f - smallH;
        int smallIndex = 0;
        for (UiButton b : menuButtons) {
            if (!isMenuSmall(b.id)) {
                continue;
            }
            b.x = sx + smallIndex * (smallW + gap);
            b.y = sy;
            b.w = smallW;
            b.h = smallH;
            smallIndex++;
        }
    }

    private static boolean isMenuColumn(int id) {
        return id == BTN_START || id == BTN_CONTINUE || id == BTN_MODE;
    }

    private static boolean isMenuRow(int id) {
        return id == BTN_CAREER || id == BTN_STORE || id == BTN_SETTINGS;
    }

    private static boolean isMenuSmall(int id) {
        return id == BTN_SOUND || id == BTN_RESET || id == BTN_HELP;
    }

    // ---- career / store layout ----------------------------------------

    public float careerPanelW() {
        float scale = Math.min(screenWidth, screenHeight) / 480f;
        return Math.min(screenWidth * 0.86f, 900f * scale);
    }

    public float careerPanelH() {
        float scale = Math.min(screenWidth, screenHeight) / 480f;
        return Math.min(screenHeight * 0.9f, 560f * scale);
    }

    public float storePanelW() {
        return careerPanelW();
    }

    public float storePanelH() {
        return careerPanelH();
    }

    private void rebuildCareerButtons() {
        careerButtons.clear();
        for (int i = 0; i < profiles.size(); i++) {
            careerButtons.add(new UiButton(profiles.get(i).name, BTN_PROFILE_ROW + i));
        }
        careerButtons.add(new UiButton("NEW CAREER", BTN_NEW_PROFILE));
        careerButtons.add(new UiButton(deleteArmed ? "CONFIRM" : "DELETE", BTN_DELETE_PROFILE));
        careerButtons.add(new UiButton("BACK", BTN_BACK));
        layoutCareerButtons();
    }

    private void layoutCareerButtons() {
        if (careerButtons.isEmpty() || screenWidth <= 0f || screenHeight <= 0f) {
            return;
        }
        float scale = Math.min(screenWidth, screenHeight) / 480f;
        float pw = careerPanelW();
        float ph = careerPanelH();
        float left = (screenWidth - pw) / 2f;
        float top = (screenHeight - ph) / 2f;

        int rows = careerRowCount();
        float rowH = Math.max(30f, Math.min(52f * scale, ph * 0.10f));
        float rowGap = rowH * 0.20f;
        float rowsTop = top + ph * 0.16f;
        float rx = left + pw * 0.06f;
        float rw = pw * 0.88f;
        int i = 0;
        for (UiButton b : careerButtons) {
            if (b.id < BTN_PROFILE_ROW) {
                continue;
            }
            b.x = rx;
            b.y = rowsTop + i * (rowH + rowGap);
            b.w = rw;
            b.h = rowH;
            i++;
        }
        float bh = Math.max(34f, Math.min(ph * 0.11f, 58f * scale));
        float by = top + ph - bh - ph * 0.07f;
        float gap = rowGap;
        float bw = (pw * 0.88f - 2f * gap) / 3f;
        UiButton newB = findCareerButton(BTN_NEW_PROFILE);
        UiButton delB = findCareerButton(BTN_DELETE_PROFILE);
        UiButton backB = findCareerButton(BTN_BACK);
        if (newB != null) {
            newB.x = rx;
            newB.y = by;
            newB.w = bw;
            newB.h = bh;
        }
        if (delB != null) {
            delB.x = rx + bw + gap;
            delB.y = by;
            delB.w = bw;
            delB.h = bh;
        }
        if (backB != null) {
            backB.x = rx + 2f * (bw + gap);
            backB.y = by;
            backB.w = bw;
            backB.h = bh;
        }
        // rows never overlap the footer: the panel scales with the screen, so
        // clamp the row height to what fits above the buttons.
        float maxRowH = Math.max(24f, (by - rowsTop - rowGap * (rows - 1)) / Math.max(rows, 1));
        if (rowH > maxRowH) {
            int k = 0;
            for (UiButton b : careerButtons) {
                if (b.id < BTN_PROFILE_ROW) {
                    continue;
                }
                b.h = maxRowH;
                b.y = rowsTop + k * (maxRowH + rowGap);
                k++;
            }
        }
    }

    private UiButton findCareerButton(int id) {
        for (UiButton b : careerButtons) {
            if (b.id == id) {
                return b;
            }
        }
        return null;
    }

    /** Number of career rows among {@link #careerButtons}. */
    public int careerRowCount() {
        int n = 0;
        for (UiButton b : careerButtons) {
            if (b.id >= BTN_PROFILE_ROW && b.id < BTN_UPGRADE_ROW) {
                n++;
            }
        }
        return n;
    }

    private void rebuildStoreButtons() {
        storeButtons.clear();
        for (int id = 0; id < Upgrades.COUNT; id++) {
            storeButtons.add(new UiButton(Upgrades.NAME[id], BTN_UPGRADE_ROW + id));
        }
        storeButtons.add(new UiButton("BACK", BTN_BACK));
        layoutStoreButtons();
    }

    private void layoutStoreButtons() {
        if (storeButtons.isEmpty() || screenWidth <= 0f || screenHeight <= 0f) {
            return;
        }
        float scale = Math.min(screenWidth, screenHeight) / 480f;
        float pw = storePanelW();
        float ph = storePanelH();
        float left = (screenWidth - pw) / 2f;
        float top = (screenHeight - ph) / 2f;
        int rows = Upgrades.COUNT;
        float rowH = Math.max(26f, Math.min(46f * scale, ph * 0.085f));
        float rowGap = rowH * 0.18f;
        float rowsTop = top + ph * 0.17f;
        float rx = left + pw * 0.06f;
        float rw = pw * 0.88f;
        int i = 0;
        for (UiButton b : storeButtons) {
            if (b.id < BTN_UPGRADE_ROW) {
                continue;
            }
            b.x = rx;
            b.y = rowsTop + i * (rowH + rowGap);
            b.w = rw;
            b.h = rowH;
            i++;
        }
        float bh = Math.max(34f, Math.min(ph * 0.11f, 58f * scale));
        float by = top + ph - bh - ph * 0.07f;
        UiButton backB = null;
        for (UiButton b : storeButtons) {
            if (b.id == BTN_BACK) {
                backB = b;
            }
        }
        if (backB != null) {
            backB.w = pw * 0.44f;
            backB.h = bh;
            backB.x = (screenWidth - backB.w) / 2f;
            backB.y = by;
        }
        float maxRowH = Math.max(22f, (by - rowsTop - rowGap * (rows - 1)) / Math.max(rows, 1));
        if (rowH > maxRowH) {
            int k = 0;
            for (UiButton b : storeButtons) {
                if (b.id < BTN_UPGRADE_ROW) {
                    continue;
                }
                b.h = maxRowH;
                b.y = rowsTop + k * (maxRowH + rowGap);
                k++;
            }
        }
    }

    /** Serialises and stores the current save state (no-op without a store). */
    private void persist() {
        save.mode = mode.ordinal();
        save.sound = sound != null && sound.isEnabled();
        save.music = musicVolume;
        save.buttons = moveScalePct;
        save.active = activeProfile;
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

    // ---- how-to-play overlay -------------------------------------------

    /** Panel width for the tutorial overlay. */
    public float helpPanelW() {
        float scale = Math.min(screenWidth, screenHeight) / 480f;
        return Math.min(screenWidth * 0.92f, 1040f * scale);
    }

    /** Panel height for the tutorial overlay. */
    public float helpPanelH() {
        float scale = Math.min(screenWidth, screenHeight) / 480f;
        return Math.min(screenHeight * 0.94f, 720f * scale);
    }

    /** Opens the in-game tutorial ("HOW TO PLAY" on the home screen). */
    public void openHelp() {
        helpButtons.clear();
        helpButtons.add(new UiButton("BACK", BTN_BACK));
        layoutHelp();
        setState(State.HELP);
        sound.play(SoundSink.SELECT);
    }

    /** Closes the tutorial and returns to the home screen. */
    public void closeHelp() {
        rebuildMenuButtons();
        setState(State.MENU);
        sound.play(SoundSink.SELECT);
    }

    /** Places the tutorial's back button inside its panel. */
    public void layoutHelp() {
        if (helpButtons.isEmpty() || screenWidth <= 0f || screenHeight <= 0f) {
            return;
        }
        float scale = Math.min(screenWidth, screenHeight) / 480f;
        float pw = helpPanelW();
        float ph = helpPanelH();
        float left = (screenWidth - pw) / 2f;
        float top = (screenHeight - ph) / 2f;
        UiButton back = helpButtons.get(0);
        back.w = Math.min(pw * 0.42f, 420f * scale);
        back.h = Math.max(30f, 46f * scale);
        back.x = left + (pw - back.w) / 2f;
        back.y = top + ph - back.h - Math.max(8f, 16f * scale);
    }

    private void updateHelp() {
        if (input.consumeBack()) {
            closeHelp();
            return;
        }
        if (!input.consumeTap(tap)) {
            return;
        }
        for (UiButton b : helpButtons) {
            if (b.contains(tap[0], tap[1])) {
                closeHelp();
                return;
            }
        }
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

    // ---- run flow -------------------------------------------------------

    /** Continue on the active career's saved level with a fresh run. */
    private void continueGame() {
        startRun(profile.levelIndex);
    }

    private void startRun(int index) {
        score = 0;
        coinsTaken = 0;
        runCoins = 0;
        lives = profile.startLives();
        speedBoostTimer = 0f;
        magnetTimer = 0f;
        doubleJumpTimer = 0f;
        invulnTimer = 0f;
        shieldCharges = profile.startingShields();
        input.consumePause();   // swallow edges armed on other screens
        input.consumeMute();
        loadLevel(index);
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
        save.copyFrom(SaveState.defaults());
        syncProfiles();
        mode = Mode.CLASSIC;
        musicVolume = save.music;
        moveScalePct = save.buttons;
        if (sound != null) {
            sound.setEnabled(save.sound);
            sound.setMusicVolume(save.music / 100f);
        }
        applyControlScale();
        lives = profile.startLives();
        loadLevel(profile.levelIndex);
        rebuildMenuButtons();
        rebuildCareerButtons();
        rebuildStoreButtons();
        sound.play(SoundSink.SELECT);
    }

    // ---- flow ---------------------------------------------------------

    public void loadLevel(int index) {
        levelIndex = Math.max(0, Math.min(index, Levels.TEXT.length - 1));
        level = new Level(levelRows(levelIndex), Levels.PAR[levelIndex]);
        levelTime = 0f;
        checkpointActive = false;
        goalLockedTimer = 0f;
        speedBoostTimer = 0f;
        magnetTimer = 0f;
        doubleJumpTimer = 0f;
        invulnTimer = 0f;
        shieldCharges = profile.startingShields();
        spawnEntities();
        player.respawn(level.startX, level.startY);
        camera.resize(Math.max(screenWidth, 1f), Math.max(screenHeight, 1f));
        camera.snapTo(player.centerX(), player.centerY(), level);
        // Auto-save: the level just entered becomes the continue point, so
        // losing all lives restarts here instead of at the first level.
        profile.levelIndex = levelIndex;
        profile.unlocked = Math.max(profile.unlocked,
                Math.min(levelIndex + 1, Levels.TEXT.length));
        save.active = activeProfile;
        persist();
        rebuildMenuButtons();
    }

    /** Rebuild enemies/platforms/coins/power-ups; collected items stay collected. */
    private void spawnEntities() {
        enemies.clear();
        platforms.clear();
        coins.clear();
        powerUps.clear();

        int idx = 0;
        for (float[] s : level.coinSpawns) {
            if (!level.coinTaken(idx)) {
                coins.add(new Coin(s[0], s[1], idx, (float) Math.random() * 6.28f));
            }
            idx++;
        }
        idx = 0;
        for (float[] s : level.powerUpSpawns) {
            if (!level.powerUpTaken(idx)) {
                PowerUp.Type type = PowerUp.Type.values()[(int) s[2]];
                powerUps.add(new PowerUp(type, s[0], s[1], idx,
                        (float) Math.random() * 6.28f));
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
        startRun(0);
    }

    /** Called when the app is backgrounded so the run does not keep ticking. */
    public void forcePause() {
        // Backgrounding is also a save point: coins picked up since the last
        // write would otherwise be lost if the process is killed.
        persist();
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
        speedBoostTimer = 0f;
        magnetTimer = 0f;
        doubleJumpTimer = 0f;
        invulnTimer = 0f;
        shieldCharges = profile.startingShields();
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
        if (deleteArmTimer > 0f) {
            deleteArmTimer -= dt;
            if (deleteArmTimer <= 0f && deleteArmed) {
                deleteArmed = false;
                rebuildCareerButtons();
            }
        }
        if (storeMsgTimer > 0f) {
            storeMsgTimer -= dt;
            if (storeMsgTimer <= 0f) {
                storeMsg = "";
            }
        }
        if (careerMsgTimer > 0f) {
            careerMsgTimer -= dt;
            if (careerMsgTimer <= 0f) {
                careerMsg = "";
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
            case CAREER:
                updateCareer();
                break;
            case STORE:
                updateStore();
                break;
            case HELP:
                updateHelp();
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
                    case BTN_CAREER:
                        openCareer();
                        break;
                    case BTN_STORE:
                        openStore();
                        break;
                    case BTN_HELP:
                        openHelp();
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

    private void openCareer() {
        deleteArmed = false;
        careerMsg = "";
        careerMsgTimer = 0f;
        rebuildCareerButtons();
        setState(State.CAREER);
        sound.play(SoundSink.SELECT);
    }

    private void closeCareer() {
        persist();
        rebuildMenuButtons();
        setState(State.MENU);
        sound.play(SoundSink.SELECT);
    }

    private void updateCareer() {
        if (input.consumeBack()) {
            closeCareer();
            return;
        }
        if (!input.consumeTap(tap)) {
            return;
        }
        for (UiButton b : careerButtons) {
            if (!b.contains(tap[0], tap[1])) {
                continue;
            }
            if (b.id >= BTN_PROFILE_ROW && b.id < BTN_UPGRADE_ROW) {
                selectProfile(b.id - BTN_PROFILE_ROW);
            } else if (b.id == BTN_NEW_PROFILE) {
                newProfile();
            } else if (b.id == BTN_DELETE_PROFILE) {
                deleteProfileTap();
            } else if (b.id == BTN_BACK) {
                closeCareer();
            }
            return;
        }
    }

    private void openStore() {
        storeMsg = "";
        storeMsgTimer = 0f;
        rebuildStoreButtons();
        setState(State.STORE);
        sound.play(SoundSink.SELECT);
    }

    private void closeStore() {
        persist();
        rebuildMenuButtons();
        setState(State.MENU);
        sound.play(SoundSink.SELECT);
    }

    private void updateStore() {
        if (input.consumeBack()) {
            closeStore();
            return;
        }
        if (!input.consumeTap(tap)) {
            return;
        }
        for (UiButton b : storeButtons) {
            if (!b.contains(tap[0], tap[1])) {
                continue;
            }
            if (b.id >= BTN_UPGRADE_ROW) {
                buyUpgrade(b.id - BTN_UPGRADE_ROW);
            } else if (b.id == BTN_BACK) {
                closeStore();
            }
            return;
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
                profile.recordScore(score);
                persist();
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
            persist();
            rebuildMenuButtons();
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
        profile.addPlaySeconds(dt);

        // ---- power-up timers ----
        if (speedBoostTimer > 0f) {
            speedBoostTimer = Math.max(0f, speedBoostTimer - dt);
        }
        if (magnetTimer > 0f) {
            magnetTimer = Math.max(0f, magnetTimer - dt);
        }
        if (doubleJumpTimer > 0f) {
            doubleJumpTimer = Math.max(0f, doubleJumpTimer - dt);
        }
        if (invulnTimer > 0f) {
            invulnTimer = Math.max(0f, invulnTimer - dt);
        }

        // ---- upgrades + power-ups feed the body ----
        player.speedMul = profile.runSpeedMul()
                * (speedBoostTimer > 0f ? Constants.SPEED_BOOST_MUL : 1f);
        player.jumpMul = profile.jumpMul();
        player.doubleJumpEnabled = doubleJumpTimer > 0f;

        // 1. platforms move, carrying anyone standing on them
        // (indexed loops on the frame path: an enhanced-for allocates an
        // iterator per loop per frame, which is pure GC pressure)
        for (int i = 0; i < platforms.size(); i++) {
            MovingPlatform p = platforms.get(i);
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
        for (int i = 0; i < enemies.size(); i++) {
            enemies.get(i).update(dt, level);
        }

        // 3. player physics
        player.update(dt, input, level, platforms, this);

        // 4. player vs enemies
        if (!player.dead) {
            for (int i = 0; i < enemies.size(); i++) {
                Enemy e = enemies.get(i);
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
                    profile.stomps++;
                    camera.shake(7f, 0.18f);
                    sound.play(SoundSink.STOMP);
                    particles.poof(e.centerX(), e.y + 8f, 10, 0xFF8BD0FF);
                    particles.dust(e.centerX(), e.y + e.h, 5, e.facing);
                } else if (invulnTimer <= 0f) {
                    hurtPlayer();
                    break;
                }
            }
        }

        // 5. hazards & pits
        if (!player.dead) {
            boolean hit = level.hazardOverlap(player.x + 7f, player.y + 10f,
                    player.w - 14f, player.h - 14f);
            boolean fell = player.y > (level.h + 1) * Constants.TILE;
            if (fell) {
                killPlayer();          // a pit is fatal even through a shield
            } else if (hit) {
                hurtPlayer();
            }
        }

        // 6. coins (+ magnet attraction while active)
        if (!player.dead) {
            float magnet = profile.magnetRadius()
                    + (magnetTimer > 0f ? Constants.MAGNET_RADIUS : 0f);
            float pcx = player.centerX();
            float pcy = player.centerY();
            for (int i = 0; i < coins.size(); i++) {
                Coin c = coins.get(i);
                if (level.coinTaken(c.index)) {
                    continue;
                }
                boolean touch = c.overlaps(player.x, player.y, player.w, player.h);
                if (!touch && magnet > 0f) {
                    float dx = c.x - pcx;
                    float dy = c.y - pcy;
                    touch = dx * dx + dy * dy <= magnet * magnet;
                }
                if (touch) {
                    collectCoin(c);
                }
            }
        }

        // 6b. power-ups
        if (!player.dead) {
            for (int i = powerUps.size() - 1; i >= 0; i--) {
                PowerUp pu = powerUps.get(i);
                if (pu.overlaps(player.x, player.y, player.w, player.h)) {
                    powerUps.remove(i);
                    collectPowerUp(pu);
                }
            }
        }

        // 7. checkpoints
        if (!player.dead && !checkpointActive) {
            for (int i = 0; i < level.checkpointSpawns.size(); i++) {
                float[] cp = level.checkpointSpawns.get(i);
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
                profile.recordScore(score);
                persist();
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

    private void collectCoin(Coin c) {
        level.takeCoin(c.index);
        coinsTaken++;
        runCoins += Constants.COINS_PER_PICKUP;
        profile.coins += Constants.COINS_PER_PICKUP;
        profile.coinsCollected += Constants.COINS_PER_PICKUP;
        score += Constants.SCORE_COIN;
        sound.play(SoundSink.COIN);
        particles.sparkle(c.x, c.y, 9);
    }

    private void collectPowerUp(PowerUp pu) {
        level.takePowerUp(pu.index);
        float mul = profile.powerTimeMul();
        switch (pu.type) {
            case SPEED:
                speedBoostTimer = Constants.POWERUP_TIME * mul;
                break;
            case SHIELD:
                shieldCharges = Math.min(3, shieldCharges + 1);
                break;
            case MAGNET:
                magnetTimer = Constants.MAGNET_TIME * mul;
                break;
            case DOUBLE_JUMP:
                doubleJumpTimer = Constants.DOUBLE_JUMP_TIME * mul;
                player.doubleJumpEnabled = true;
                player.airJumps = Math.max(player.airJumps, 1);
                break;
            case EXTRA_LIFE:
                lives = Math.min(Constants.MAX_LIVES, lives + 1);
                break;
            default:
                break;
        }
        score += Constants.SCORE_POWERUP;
        sound.play(SoundSink.POWERUP);
        camera.shake(3f, 0.12f);
        particles.sparkle(pu.x, pu.y, 14);
        particles.poof(pu.x, pu.y, 8, 0xFFB6F0FF);
    }

    private void levelCleared() {
        int left = Math.max(0, level.parTime - (int) levelTime);
        bonusTime = left * Constants.SCORE_TIME_UNIT;
        boolean allCoins = level.coinCount() > 0 && level.coinsTaken() >= level.coinCount();
        bonusCoins = allCoins ? Constants.SCORE_ALL_COINS : 0;
        score += bonusTime + bonusCoins;
        // Career wallet: every clear pays, a full-coin clear pays more.
        rewardCoins = Constants.COINS_LEVEL_CLEAR
                + (allCoins ? Constants.COINS_ALL_COINS : 0);
        profile.coins += rewardCoins;
        runCoins += rewardCoins;
        profile.levelsCleared++;
        profile.recordScore(score);
        persist();
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
        shieldCharges = 0;
        speedBoostTimer = 0f;
        magnetTimer = 0f;
        doubleJumpTimer = 0f;
        invulnTimer = 0f;
        profile.deaths++;
        sound.play(SoundSink.DIE);
        camera.shake(12f, 0.35f);
        particles.poof(player.centerX(), player.centerY(), 16, 0xFFF4F4F4);
        particles.poof(player.centerX(), player.centerY(), 8, 0xFFE4453A);
    }

    /**
     * One hit from an enemy or a hazard: a shield charge soaks it (plus a short
     * invulnerability so the same hazard cannot chain), otherwise it kills.
     */
    public void hurtPlayer() {
        if (player.dead || invulnTimer > 0f) {
            return;
        }
        if (shieldCharges > 0) {
            shieldCharges--;
            invulnTimer = Constants.INVULN_TIME;
            sound.play(SoundSink.HURT);
            camera.shake(9f, 0.28f);
            particles.poof(player.centerX(), player.centerY(), 12, 0xFF7FD8FF);
            particles.sparkle(player.centerX(), player.centerY(), 8);
            return;
        }
        killPlayer();
    }

    private void respawnPlayer() {
        float rx = checkpointActive ? checkpointX : level.startX;
        float ry = checkpointActive ? checkpointY : level.startY;
        spawnEntities();
        player.respawn(rx, ry);
        speedBoostTimer = 0f;
        magnetTimer = 0f;
        doubleJumpTimer = 0f;
        invulnTimer = 0f;
        shieldCharges = 0;
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
        if (levelLabelIndex != levelIndex) {
            levelLabelIndex = levelIndex;
            levelLabelCache = "LEVEL " + (levelIndex + 1);
        }
        return levelLabelCache;
    }

    /** True while the coin-collector gate is still shut. */
    public boolean goalLocked() {
        return mode == Mode.COIN_COLLECTOR && level.coinCount() > 0
                && level.coinsTakenFast() < coinGoalRequired();
    }

    /** Coins needed to open the goal in coin-collector mode. */
    public int coinGoalRequired() {
        if (level.coinCount() == 0) {
            return 0;
        }
        return Math.max(1, Math.round(level.coinCount() * COIN_GOAL_SHARE));
    }

    /** Elapsed level time as m:ss; recomputed only when the second changes. */
    public String timeLabel() {
        int t = (int) levelTime;
        if (t != timeLabelSecond) {
            timeLabelSecond = t;
            int m = t / 60;
            int s = t % 60;
            timeLabelCache = m + (s < 10 ? ":0" : ":") + s;
        }
        return timeLabelCache;
    }
}
