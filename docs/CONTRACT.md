# Anplomer feature round — API contract

This file is the coordination contract for the performance / power-up /
career-profile round. It is the source of truth for names that cross file
ownership. If a signature here has to change, message the Lead instead of
inventing a parallel name.

## File ownership (do not edit outside your scope)

| Owner | Files |
| --- | --- |
| Lead | `Constants.java`, `Level.java`, `PowerUp.java`, `Profile.java`, `Upgrades.java`, `SaveState.java`, `Game.java`, `Player.java`, `GameView.java`, `SoundSink.java`, `SoundManager.java`, `MainActivity.java`, `tools/run_headless_test.sh`, `README.md`, `docs/CONTRACT.md` |
| render-smith | `Renderer.java`, `tools/Bench.java`, `tools/run_bench.sh` |
| levels-smith | `tools/make_levels.py`, `Levels.java` (generated) |
| test-warden | `tools/HeadlessTest.java`, `tools/RendererSmoke.java` |

`Levels.java` is generated from `tools/make_levels.py`; never hand-edit it.

## 1. Levels: new glyphs

`Level.java` recognises these additional characters, all spawning a `PowerUp`
at the tile centre. They sit in **air** (the generator enforces that), exactly
like coins.

| glyph | `PowerUp.Type` | meaning |
| --- | --- | --- |
| `S` | `SPEED` | speed boots, temporary run-speed multiplier |
| `H` | `SHIELD` | shield charge, absorbs one hit |
| `A` | `MAGNET` | coin magnet, pulls pickups in a radius |
| `J` | `DOUBLE_JUMP` | allows one extra mid-air jump |
| `L` | `EXTRA_LIFE` | +1 life immediately |

Existing glyphs are unchanged: `# = ^ ~ o E B M V P C G .`

`Level` API added by the Lead:

```java
public final List<float[]> powerUpSpawns;   // {x, y, typeOrdinal}
public int powerUpCount();
public boolean powerUpTaken(int index);     // like coinTaken, survives respawn
public void takePowerUp(int index);
```

`PowerUp.Type.values()` order is fixed and is what `typeOrdinal` means:
`SPEED, SHIELD, MAGNET, DOUBLE_JUMP, EXTRA_LIFE`.

## 2. Power-up entity

```java
public final class PowerUp {
    public enum Type { SPEED, SHIELD, MAGNET, DOUBLE_JUMP, EXTRA_LIFE }
    public final Type type;
    public float x, y;          // world centre
    public final int index;     // index into Level.powerUpSpawns
    public float phase;         // renderer animation phase
    public PowerUp(Type type, float x, float y, int index, float phase);
    public boolean overlaps(float px, float py, float pw, float ph);
}
```

`Game.powerUps` is the live list (collected ones are removed from it; the
`Level` flag is the persistent record), mirroring how `Game.coins` works.

## 3. Career profiles

```java
public final class Profile {
    public String name;          // <= 12 chars, sanitised
    public int colorIndex;       // 0..(Profile.COLORS-1), avatar tint
    public int coins;            // wallet balance, spendable in the store
    public int levelIndex;       // continue level (0-based)
    public int unlocked;         // 1-based count of unlocked levels

    // career stats
    public int levelsCleared, coinsCollected, bestScore, stomps, deaths;
    public float playSeconds;

    // upgrade levels, indexed by Upgrades.SPEED..Upgrades.SHIELD
    public final int[] upgrades; // values clammped to Upgrades.MAX[id]

    public Profile(String name, int colorIndex);
    public int upgradeLevel(int id);
    public int nextPrice(int id);       // -1 at max
    public boolean canAfford(int id);   // nextPrice >= 0 && coins >= price

    // derived effects used by Game/Player
    public float runSpeedMul();         // 1 + 0.04 * speed level
    public float jumpMul();             // 1 + 0.03 * jump level
    public int extraLives();            // lives level
    public float powerTimeMul();        // 1 + 0.15 * power level
    public float magnetRadius();        // 0 when no upgrade/magnet active
    public int startingShields();       // shield upgrade level
}
```

```java
public final class Upgrades {
    public static final int SPEED = 0, JUMP = 1, LIVES = 2, MAGNET = 3, POWER = 4, SHIELD = 5;
    public static final int COUNT = 6;
    public static final String[] NAME;      // "SPEED BOOTS", ... (for the store UI)
    public static final String[] DESC;      // one short line each
    public static final int[] MAX;          // max level per upgrade
    public static String effect(int id, int level); // e.g. "+8% RUN"
    public static int price(int id, int level);     // price to buy `level+1`
}
```

`SaveState.VERSION` becomes 3. The blob stays a single pipe-separated string:

```
3|sound|music|buttons|mode|activeProfile|profile;profile;...
profile = name,color,coins,level,unlocked,cleared,coinsCollected,bestScore,stomps,deaths,playMillis,u0,u1,u2,u3,u4,u5
```

Names are sanitised (no `|`, `;`, `,`; <= 12 chars). v1/v2 blobs load as a
single profile ("PLAYER 1") carrying the old level/mode/sound settings, so an
existing install keeps its progress.

## 4. Game API used by the renderer

```java
public enum State { MENU, PLAYING, PAUSED, LEVEL_CLEARED, GAME_OVER, WIN, SETTINGS, CAREER, STORE }

// alive state
public Profile profile;                 // active profile, never null
public final List<Profile> profiles;    // save.profiles, live
public int activeProfile;               // index into profiles
public final List<PowerUp> powerUps;
public float speedBoostTimer, magnetTimer, doubleJumpTimer, invulnTimer;
public int shieldCharges;
public int runCoins;                    // coins earned in this run/session

// home screen ids
public static final int BTN_CAREER = 10, BTN_STORE = 11;
public static final int BTN_NEW_PROFILE = 12, BTN_DELETE_PROFILE = 13;
// BTN_BACK (9) is reused for both new screens' back buttons.

// career screen: one button per profile row (index == profile index),
// plus NEW / DELETE / BACK. Rows are laid out by Game.
public final List<UiButton> careerButtons;
public final int careerRowCount();       // rows among careerButtons

// store screen: one button per upgrade id, plus BACK.
public final List<UiButton> storeButtons;
public float storeMsgTimer;             // >0 while the feedback line shows
public String storeMsg;                 // "PURCHASED", "NOT ENOUGH COINS", "MAX LEVEL"
```

Layouts are computed in `Game` so drawing and hit-testing can never disagree
(the same rule the pause/settings screens follow).

`Renderer.draw(Game, Canvas, w, h)` keeps its signature. Rendering
expectations for the new states:

* `MENU` — show the active profile's name and coin balance above the button
  stack (the button column stays `START GAME`, `CONTINUE`, `MODE`; then a row
  of `CAREER`, `STORE`, `SETTINGS`; then small `SOUND` and `RESET SAVE`
  buttons at the bottom).
* `CAREER` — panel with one row per profile (avatar dot, name, `LEVEL n`,
  coin balance, best score), the active row highlighted; `NEW PROFILE`,
  `DELETE` (two-tap label) and `BACK`.
* `STORE` — panel with one row per upgrade (`Upgrades.NAME[id]`, effect text,
  level pips, next price or `MAX`), unaffordable rows dimmed; wallet in the
  header; `BACK`; the `storeMsg` line when `storeMsgTimer > 0`.
* `PLAYING` HUD — coin **wallet** chip next to the level/time panel, active
  power-up chips (icon + seconds left) under the hearts, shield pips.
* `LEVEL_CLEARED` — the coins earned line.

## 5. Performance targets (render-smith)

Baseline measured on this machine with `tools/run_bench.sh 800` before any
change (recorded here for comparison):

```
update only                      0.000 ms/frame      0.0 draws/frame       81 B/frame
2400x1080 L1 meadow              0.059 ms/frame    679.0 draws/frame    26041 B/frame
1920x1080 L1 meadow              0.038 ms/frame    589.0 draws/frame    22553 B/frame
1280x720 L1 meadow               0.038 ms/frame    555.0 draws/frame    21582 B/frame
2400x1080 L2 sunset              0.075 ms/frame    690.0 draws/frame    30924 B/frame
2400x1080 L3 night               0.088 ms/frame    775.0 draws/frame    25242 B/frame
2400x1080 menu overlay           0.064 ms/frame    693.0 draws/frame    22401 B/frame
```

Goals: fewer draw calls, no per-frame allocation in steady state, and the
same picture (the smoke test's sky-opacity and geometry guards must stay
green). `Bench` numbers are JVM/stub numbers — use them for relative change,
not as device FPS.

## 6. Tests

`tools/run_headless_test.sh` compiles the pure-Java half plus both suites.
Keep the list of sources there in sync when files are added (Lead does this).

The suite must cover, in addition to what it covers today:

* every level completable by the bot (now 8),
* power-ups: each type activates, timers expire, shield absorbs exactly one hit,
  magnet collects coins without touching them, extra life raises lives,
* career: create/select/delete profile, per-profile coins and progress are
  independent, active profile survives save/load,
* store: purchase deducts coins and raises the level, max-level clamp, not
  enough coins is refused, effect actually reaches the player,
* `RendererSmoke` draws every state × every level palette × every size and the
  new CAREER/STORE screens at all sizes.
