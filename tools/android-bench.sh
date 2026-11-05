#!/usr/bin/env bash
# Gate 3's speed check on a CI emulator (android-bench.yml): main's app and the PR's app take turns A/B/A/B under the same
# Macrobenchmark class (android/baselineprofile/.../SpeedBenchmark.kt: a cold start, the Queue flung), with the made-up
# backup. The test APK runs itself and only names the app's package, so the PR's test APK drives both builds. Each app is
# uninstalled before the other goes in (their version numbers differ). Every run's JSON lands in <out>/<side>-<round>.json;
# tools/android-bench.mjs compares them.
#   tools/android-bench.sh <main app apk> <pr app apk> <benchmark test apk> <out dir> [iterations]
set -euo pipefail
PKG=io.github.shamlawy.playnext
TPKG=$PKG.baselineprofile
MAIN=$1 PR=$2 TEST=$3 OUT=$4 ITER=${5:-5}
DIR=/sdcard/Android/media/$TPKG
mkdir -p "$OUT"
adb wait-for-device
adb shell settings put global playnext_demo 1
adb install -r "$TEST" > /dev/null
RUNNER=$(adb shell pm list instrumentation | tr -d '\r' | sed -n "s/^instrumentation:\($TPKG\/[^ ]*\) .*/\1/p" | head -n 1)
[ -n "$RUNNER" ] || { echo "::error::The benchmark APK's instrumentation isn't installed"; exit 1; }
for round in 1 2; do
  for side in main pr; do
    apk=$MAIN; [ "$side" = pr ] && apk=$PR
    echo "== round $round: $side =="
    adb uninstall "$PKG" > /dev/null 2>&1 || true
    adb install "$apk" > /dev/null
    adb shell rm -rf "$DIR" || true
    adb shell mkdir -p "$DIR"
    adb shell am instrument -w -r -e class "$TPKG.SpeedBenchmark" -e pn.iter "$ITER" \
      -e androidx.benchmark.suppressErrors EMULATOR -e androidx.benchmark.output.enable true \
      -e additionalTestOutputDir "$DIR" -e no-isolated-storage true "$RUNNER" | tr -d '\r' > "$OUT/$side-$round.log"
    if grep -qE 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_CODE: 0|Process crashed' "$OUT/$side-$round.log"; then
      tail -n 40 "$OUT/$side-$round.log"
      echo "::error::The benchmark failed on the $side build (round $round)"
      exit 1
    fi
    json=$(adb shell ls "$DIR" | tr -d '\r' | grep 'benchmarkData.json$' | head -n 1)
    [ -n "$json" ] || { echo "::error::No benchmark JSON from the $side build (round $round)"; adb shell ls "$DIR"; exit 1; }
    adb pull "$DIR/$json" "$OUT/$side-$round.json" > /dev/null
  done
done
echo "All four runs done."
