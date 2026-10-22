#!/usr/bin/env bash
# Compiles the pure-Java simulation + renderer with the android.graphics stub
# and runs the headless performance benchmark. No Android SDK or device needed.
#
#   tools/run_bench.sh            # default 1200 measured frames per scenario
#   tools/run_bench.sh 4000       # longer run, less noise
set -euo pipefail
cd "$(dirname "$0")/.."

SRC=app/src/main/java/com/example/anplomer
OUT="${TMPDIR:-/tmp}/anplomer-bench"
rm -rf "$OUT"
mkdir -p "$OUT"

SOURCES=(
  "$SRC/Constants.java"
  "$SRC/Level.java"
  "$SRC/Levels.java"
  "$SRC/Controls.java"
  "$SRC/InputController.java"
  "$SRC/Player.java"
  "$SRC/Enemy.java"
  "$SRC/MovingPlatform.java"
  "$SRC/Coin.java"
  "$SRC/PowerUp.java"
  "$SRC/Camera.java"
  "$SRC/Particles.java"
  "$SRC/SoundSink.java"
  "$SRC/SaveState.java"
  "$SRC/SaveStore.java"
  "$SRC/Profile.java"
  "$SRC/Upgrades.java"
  "$SRC/Game.java"
  "$SRC/Renderer.java"
  "tools/Bench.java"
)

if [ -n "${JAVA_HOME:-}" ]; then
  JAVAC="$JAVA_HOME/bin/javac"; JAVA="$JAVA_HOME/bin/java"
else
  JAVAC=javac; JAVA=java
fi

# Keep files that are not present yet out of the compile list, so this script
# also works on a tree that predates the career/power-up split.
PRESENT=()
for f in "${SOURCES[@]}"; do
  [ -f "$f" ] && PRESENT+=("$f")
done

"$JAVAC" -nowarn -d "$OUT" "${PRESENT[@]}" tools/stub/android/graphics/*.java
"$JAVA" -Djava.awt.headless=true -cp "$OUT" com.example.anplomer.Bench "$@"
