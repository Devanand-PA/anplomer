# Anplomer combat / build / level-select round — API contract

Coordination contract for the sword-combat / secondary-weapon / level-select
round. It is the source of truth for names that cross file ownership: if a
signature here has to change, message the Lead instead of inventing a parallel
name.

## File ownership (do not edit outside your scope)

| Owner | Files |
| --- | --- |
| Lead | `Constants.java`, `Weapons.java`, `SwordStyle.java`, `Projectile.java`, `Player.java`, `Controls.java`, `InputController.java`, `Game.java`, `GameView.java`, `MainActivity.java`, `SoundSink.java`, `SoundManager.java`, `tools/run_headless_test.sh`, `docs/CONTRACT.md` |
| save-smith | `Profile.java`, `SaveState.java` |
| render-smith | `Renderer.java`, `tools/Bench.java`, `tools/run_bench.sh` |
| test-warden | `tools/HeadlessTest.java`, `tools/RendererSmoke.java` |
| docs-smith | `README.md`, `docs/TUTORIAL.md` |

`Levels.java` is generated from `tools/make_levels.py`; never hand-edit it.
`Level.java` is unchanged this round.

## 1. What changes for the player

* The right-hand **jump disc becomes an attack disc**: the sword swing. Jumping
  stays on the movement pad (drag up), so nothing is lost.
* A smaller **secondary disc** sits to the left of the attack disc and fires the
  career's equipped secondary weapon (`BOW` or `BOOMERANG`; `NO WEAPON` when
  none is equipped).
* The **swing pattern depends on how the body is moving**:
  | body state | style |
  | --- | --- |
  | grounded, |vx| < 90 | `GROUND_IDLE` — tall overhead chop in front |
  | grounded, |vx| >= 90 | `GROUND_RUN` — longer, lower forward slash |
  | airborne, rising (vy < 0) | `AIR_RISE` — upward arc above the head |
  | airborne, falling | `AIR_FALL` — plunge arc below the feet |
* One weapon hit always kills an enemy (like a stomp). Enemies stay in place
  until their squash animation ends, exactly as stomped ones do.
* The home screen's `START GAME` becomes `PLAY` and opens **level select**; only
  levels the active career has unlocked are selectable, cleared levels are
  marked. Picking a level opens the **loadout** screen (choose secondary weapon,
  then `START`).
* **Store lives inside the career menu** (career → `STORE` → back to career), and
  each career keeps its own equipped secondary weapon next to its upgrades.

## 2. Weapons catalogue (`Weapons.java`, Lead)

```java
public final class Weapons {
    public static final int NONE = 0, BOW = 1, BOOMERANG = 2;
    public static final int SECONDARY_COUNT = 3;
    public static final String[] SECONDARY_NAME = {"NO WEAPON", "BOW", "BOOMERANG"};
    public static final String[] SECONDARY_DESC = { ... }; // one short line each
    public static String secondaryName(int id);
    public static String secondaryDesc(int id);
    public static int clampSecondary(int id);   // out-of-range -> NONE
}
```

## 3. Sword styles (`SwordStyle.java`, Lead)

```java
public enum SwordStyle { GROUND_IDLE, GROUND_RUN, AIR_RISE, AIR_FALL }
```

## 4. Player combat API (Lead)

```java
public SwordStyle swingStyle;    // style of the current/last swing
public float swingTimer;         // seconds left in the active swing window (>0 => swinging)
public float attackCooldown;     // seconds until another swing may start
public int swings;               // swings made this life (tests/renderer)

public boolean attacking();      // swingTimer > 0
public float[] attackBox();      // {x, y, w, h} world-space hitbox, or null when idle
```

`Player.Listener` gains `void onSwing(SwordStyle style);` (Game plays the sound
and dust). Attack input is consumed inside `Player.update`, and the style is
chosen there from `onGround`, `vx` and `vy` at the moment the swing starts.

## 5. Projectiles (`Projectile.java`, Lead)

```java
public final class Projectile {
    public static final int ARROW = 0, BOOMERANG = 1;
    public final int kind;
    public float x, y;          // centre
    public float vx, vy;
    public final float w, h;
    public float life, phase;   // phase drives renderer spin
    public boolean returning, dead;

    public Projectile(int kind, float cx, float cy, int facing); // facing -1 / +1
    public boolean overlaps(float px, float py, float pw, float ph);
    public void update(float dt, Player owner, Level lvl);
}
```

Arrow: straight fast shot with slight gravity, dies on solid tiles, its
lifetime or the first enemy. Boomerang: flies out, then homes back to the owner
and dies when it reaches them (or on lifetime). `Game.projectiles` is the live
list; it is cleared on level load, respawn and death.

## 6. Input and controls (Lead)

`Controls` (screen pixels):

```java
public float attackX, attackY, attackR, attackHitR;  // replaces the jump disc
public float secX, secY, secR, secHitR;              // secondary disc, left of attack
public boolean inAttack(float px, float py);
public boolean inSecondary(float px, float py);
```

`InputController`:

```java
public static final int KEY_ATTACK = 8, KEY_SECONDARY = 9;
public boolean attackHeld, secondaryHeld;            // held state (auto-repeat)
public boolean consumeAttack();                      // edge: attack pressed
public boolean consumeSecondary();                   // edge: secondary pressed
```

The pad still owns jumping (`PAD_JUMP_ON/OFF`); there is no jump disc any more,
so `jumpHeld = keyJump || padJumpLatch`. Keyboard: `J`/`X` = attack,
`K`/`C` = secondary (`GameView`).

## 7. Game API used by the renderer / tests (Lead)

```java
public enum State {
    MENU, PLAYING, PAUSED, LEVEL_CLEARED, GAME_OVER, WIN, SETTINGS, CAREER, STORE, HELP,
    LEVEL_SELECT, LOADOUT
}

public final List<Projectile> projectiles;
public float secondaryCooldown;        // > 0 => the secondary is on cooldown
public int swingSerial;                // bumped on every new swing
public int pendingLevel;               // level picked in LEVEL_SELECT, started by LOADOUT

// secondary weapon of the active career
public int secondaryWeaponId();        // Weapons id, clamped
public void setSecondaryWeapon(int id);// persists and relabels the loadout rows

// level select / loadout
public final List<UiButton> levelButtons;    // one row per level + BACK
public final List<UiButton> loadoutButtons;  // one row per weapon + START + BACK
public static final int BTN_LEVEL_ROW = 300;      // + level index
public static final int BTN_WEAPON_ROW = 400;     // + Weapons id
public static final int BTN_START_LEVEL = 15;
public void openLevelSelect();
public void openLoadout(int levelIndex);
public boolean levelUnlocked(int index);
public boolean levelCleared(int index);
public String levelName(int index);               // e.g. "EMERALD MEADOW"
public float levelSelectPanelW();                 // panel geometry, like career/story
public float levelSelectPanelH();
public float loadoutPanelW();
public float loadoutPanelH();
public int levelRowCount();                       // rows among levelButtons
```

Career screen: `careerButtons` now holds the profile rows (`BTN_PROFILE_ROW + i`)
and the footer buttons `NEW CAREER` (`BTN_NEW_PROFILE`), `DELETE`
(`BTN_DELETE_PROFILE`), `STORE` (`BTN_STORE`) and `BACK` (`BTN_BACK`). The store
is a child screen: its `BACK` returns to `CAREER`, not `MENU`. `menuButtons` no
longer contains `BTN_STORE`.

## 8. Career / save additions (save-smith)

`Profile` gains, with these exact names:

```java
public int equippedSecondary;                 // Weapons.NONE/BOW/BOOMERANG
public int clearedBits;                       // bit i set => level i cleared
public final int[] levelBest;                 // best score per level, Levels.TEXT.length
public boolean isCleared(int levelIndex);
public int levelBestScore(int levelIndex);
public void markCleared(int levelIndex, int score);  // set bit, raise best,
                                                     // unlocked = max(unlocked, index+2) clamped
```

`Profile.copyOf` copies all of them. `SaveState.VERSION` becomes **4** with the
blob

```
4|sound|music|buttons|mode|active|profile;profile;...
profile = name,color,coins,level,unlocked,cleared,coinsCollected,bestScore,
          stomps,deaths,playMillis,u0..u5,equipped,clearedBits,b0..b7
```

v3 blobs (written by the previous encoder, which emitted the sound flag twice:
`3|sound|sound|music|buttons|mode|active|...`) must migrate without losing
progress; v1/v2 migrations keep working. Malformed input still falls back to
defaults. A wrong-length profile record is dropped, never fatal.

## 9. Rendering expectations (render-smith)

* `PLAYING`: attack disc with a sword glyph (replacing the jump arrow), a
  smaller secondary disc to its left with a per-weapon glyph and a cooldown
  wedge while `secondaryCooldown > 0`; the swing arc/spark drawn per
  `player.swingStyle`; arrows and boomerangs from `g.projectiles`; a small
  equipped-secondary chip near the hearts is welcome.
* `MENU`: `PLAY` / `CONTINUE` / `MODE`, row `CAREER` / `SETTINGS`, small
  `HOW TO PLAY` / `SOUND` / `RESET SAVE` (no `STORE` home button).
* `LEVEL_SELECT`: panel, one row per level (`1  EMERALD MEADOW`) showing
  `CLEARED` + best score, `LOCKED` rows dimmed and untappable, `BACK`.
* `LOADOUT`: panel, level title, career wallet, one row per secondary weapon
  (name + description + equipped marker), `START` and `BACK`.
* `CAREER`: rows show `LEVEL n • COINS x • <weapon>`; footer `NEW CAREER`,
  `DELETE`, `STORE`, `BACK`.
* Every label stays inside its rect via `Renderer.fitSize`.

## 10. Tests (test-warden)

`tools/run_headless_test.sh` (Lead) compiles the pure-Java half plus both
suites; new suites must cover:

* swing styles: idle / run / rise / fall pick the four `SwordStyle`s and their
  hitboxes sit in front, above and below as described; cooldown blocks mashers;
  one swing kills an enemy in front once and never one behind;
* secondary weapons: bow arrow spawns, travels, kills a walker and dies on a
  wall; boomerang goes out, returns, kills on both legs and is caught; cooldown
  gating; `NO WEAPON` fires nothing; the equipped weapon comes from the career;
* level select: cleared bits drive the `CLEARED` marker, locked rows cannot be
  started, a cleared level starts in that level, `CONTINUE` still resumes the
  saved level;
* loadout: picking a weapon persists per career and survives save/load;
* save v4 round trip plus v3 (buggy encoder layout) and v1/v2 migration;
* `RendererSmoke` draws `LEVEL_SELECT` and `LOADOUT` at every size, plus swings
  (all four styles) and both projectile kinds.
