#!/usr/bin/env bash
# Compiles the pure-Java simulation half of Anplomer plus the headless bot
# test, then runs it. No Android SDK or device required.
set -euo pipefail
cd "$(dirname "$0")/.."

SRC=app/src/main/java/com/example/anplomer
OUT="${TMPDIR:-/tmp}/anplomer-headless"
rm -rf "$OUT"
mkdir -p "$OUT"

PURE_SOURCES=(
  "$SRC/Constants.java"
  "$SRC/Level.java"
  "$SRC/Levels.java"
  "$SRC/Controls.java"
  "$SRC/InputController.java"
  "$SRC/Player.java"
  "$SRC/Enemy.java"
  "$SRC/MovingPlatform.java"
  "$SRC/Coin.java"
  "$SRC/Camera.java"
  "$SRC/Particles.java"
  "$SRC/SoundSink.java"
  "$SRC/SaveState.java"
  "$SRC/SaveStore.java"
  "$SRC/Game.java"
  "$SRC/Renderer.java"
  "tools/HeadlessTest.java"
  "tools/RendererSmoke.java"
)

if [ -n "${JAVA_HOME:-}" ]; then
  JAVAC="$JAVA_HOME/bin/javac"; JAVA="$JAVA_HOME/bin/java"
else
  JAVAC=javac; JAVA=java
fi

echo "compiling ${#PURE_SOURCES[@]} sources + android.graphics stub -> $OUT"
# Renderer needs android.graphics; the stub (tools/stub) supplies a recording
# Canvas that rejects non-finite geometry and negative radii, so the real
# drawing code runs on a plain JVM without a device.
STUB_SOURCES=(tools/stub/android/graphics/*.java)
"$JAVAC" -nowarn -d "$OUT" -sourcepath "$SRC:tools/stub" \
    "${PURE_SOURCES[@]}" "${STUB_SOURCES[@]}"

echo "running RendererSmoke"
# headless: the rasterising Canvas uses java.awt, which must not touch an X server
"$JAVA" -Djava.awt.headless=true -cp "$OUT" com.example.anplomer.RendererSmoke

echo "running HeadlessTest"
"$JAVA" -cp "$OUT" com.example.anplomer.HeadlessTest "$@"
