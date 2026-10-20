#!/bin/bash
# Capture consecutive frames rendered by the game itself.
#
# adb's `screencap` freezes the app's game loop while it holds the display, so
# remotely captured frames are all the same static image. This arms an in-app
# dumper instead, which writes real consecutive frames to app-private storage.
#
# Usage: tools/dump_frames.sh [seconds-to-record]
set -e
SECS=${1:-8}
DEV=${DEVICE:-$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')}
[ -n "$DEV" ] || { echo "no device"; exit 1; }
PKG=com.example.anplomer
echo "device: $DEV  recording ${SECS}s"

adb -s "$DEV" shell cmd statusbar collapse >/dev/null 2>&1 || true
adb -s "$DEV" shell am force-stop $PKG >/dev/null 2>&1
adb -s "$DEV" shell "run-as $PKG rm -rf files/frames files/dumpframes" 2>/dev/null || true
adb -s "$DEV" shell am start -n $PKG/.MainActivity >/dev/null 2>&1
sleep 5
adb -s "$DEV" shell input tap 802 360 >/dev/null 2>&1   # start level
sleep 3

# arm, then drive the player so the frames contain real motion
adb -s "$DEV" shell "run-as $PKG touch files/dumpframes"
echo "armed; moving player"
END=$((SECONDS+SECS))
while [ $SECONDS -lt $END ]; do
  adb -s "$DEV" shell input swipe 150 620 150 620 400 >/dev/null 2>&1
  adb -s "$DEV" shell input swipe 1450 600 1450 600 60 >/dev/null 2>&1
done
adb -s "$DEV" shell "run-as $PKG rm -f files/dumpframes"

OUT=${OUT:-/tmp/gameframes}
rm -rf "$OUT"; mkdir -p "$OUT"
adb -s "$DEV" shell "run-as $PKG tar -c files/frames" > /tmp/frames.tar 2>/dev/null
tar -xf /tmp/frames.tar -C "$OUT" 2>/dev/null || true
n=$(find "$OUT" -name '*.png' | wc -l)
echo "pulled $n frames into $OUT"
