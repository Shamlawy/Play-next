#!/usr/bin/env bash
# The Android app's walk on a CI emulator: android-release.yml runs it on the signed APK before publishing, android-check.yml
# on every PR's release build (R8, debug-signed), so a walk that would block a release fails the PR first.
# It installs the APK, sets the made-up backup's switch (the app's Demo: a global setting only the shell can write, read
# only on an emulator), opens the app and walks Home, every tab, a one-finger view swipe, a game page (a header card
# cover) and Settings, then the 120 Hz test, and checks the app is alive 20 s later with no crash. A picture of each
# screen, the screens' nodes and the logcat go to the logs folder. "probe" as the third argument skips the walk (a
# rollback to an app from before the made-up backup, android-v10, would only show the restore screen).
#   tools/android-smoke.sh <apk> <logs dir> [walk|probe]
set -euo pipefail
PKG=io.github.shamlawy.playnext
APK=$1
LOGS=$2
WALK=${3:-walk}
mkdir -p "$LOGS"
fail=""
# the made-up backup's titles start with these words (android/app/src/main/java/io/github/shamlawy/playnext/data/Demo.kt)
DEMO='(Starfall|Hollow|Copper|Ninefold|Quiet|Paper|Lumen|Ember|Glass|Velvet|Iron|Moss) [A-Za-z0-9 ]+'

adb wait-for-device
adb install -r "$APK" 2>&1 | tee "$LOGS/install.txt"
adb shell settings put global playnext_demo 1
adb logcat -c || true

# The screen's nodes (uiautomator's dump: Compose's content descriptions, texts, selected and bounds), retried while it moves.
dump() {
  local i
  for i in 1 2 3 4 5; do
    if adb shell uiautomator dump /sdcard/pn-ui.xml > /dev/null 2>&1 \
      && adb exec-out cat /sdcard/pn-ui.xml > "$LOGS/ui.xml" 2> /dev/null && grep -q '<hierarchy' "$LOGS/ui.xml"; then
      return 0
    fi
    sleep 1
  done
  echo "uiautomator couldn't read the screen" >&2
  return 1
}
# "x y": the centre of the first node whose $1 (content-desc or text) matches the pattern $2 exactly; empty when none.
at() {
  local m
  m=$(grep -oE "$1=\"$2\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" "$LOGS/ui.xml" | head -n 1) || true
  [ -n "$m" ] || return 0
  echo "$m" | grep -oE '[0-9]+' | tail -n 4 | tr '\n' ' ' | awk 'NF == 4 { printf "%d %d", ($1 + $3) / 2, ($2 + $4) / 2 }'
}
# Waits up to $3 s for a node ($1 = content-desc or text, $2 = pattern); prints where it is.
wait_for() {
  local end=$((SECONDS + $3)) xy
  while :; do
    if dump; then
      xy=$(at "$1" "$2")
      if [ -n "$xy" ]; then echo "$xy"; return 0; fi
    fi
    [ "$SECONDS" -ge "$end" ] && return 1
    sleep 1
  done
}
# Taps a node once it shows; false when it never does.
tap() {
  local xy
  xy=$(wait_for "$1" "$2" "${3:-15}") || return 1
  # shellcheck disable=SC2086 # "x y" is two arguments
  adb shell input tap $xy
}
shot() {
  sleep "${2:-1.5}"
  adb exec-out screencap -p > "$LOGS/$1.png" 2> /dev/null || true
  dump && cp "$LOGS/ui.xml" "$LOGS/$1.xml" || true
}
err() { echo "::error::$1"; fail=1; }
# what's lit: the selected nodes' names (the tab, the view)
lit() { { grep -oE 'content-desc="[^"]*"[^>]*selected="true"' "$LOGS/ui.xml" || true; } | sed -E 's/content-desc="([^"]*)".*/\1/' | sort | tr '\n' ',' ; }

if [ "$WALK" = walk ]; then
  { adb shell am start -W -n "$PKG/.MainActivity" 2>&1 || true; } | tr -d '\r' | tee "$LOGS/am-start.txt"
  if grep -q '^Error' "$LOGS/am-start.txt"; then err "The app didn't open."; fi

  # Home: the tabs show, and not the restore screen (the made-up backup opened in this build)
  if wait_for content-desc Games 40 > /dev/null && [ -z "$(at content-desc 'Restore code')" ]; then
    shot home
  else
    shot home-missing 0
    if [ -n "$(at content-desc 'Restore code')" ]; then err "The restore screen showed: the made-up backup (playnext_demo) didn't open."; else err "Home never showed its tabs."; fi
  fi

  if [ -z "$fail" ]; then
    # every tab, Home last (unfolded, the rail names Add "Add a game")
    for t in Games Plan Taste Add Home; do
      if tap content-desc "$t( a game)?"; then shot "tab-$(echo "$t" | tr 'A-Z' 'a-z')"; else err "No $t tab to tap."; fi
    done

    # a one-finger swipe on Games walks its views (lower on the page, away from the header card's covers)
    tap content-desc Games || err "No Games tab to tap."
    shot games 1.5
    before=$(lit)
    read -r W H < <(adb shell wm size | tr -d '\r' | grep -oE '[0-9]+x[0-9]+' | tail -n 1 | tr 'x' ' ') || true
    W=${W:-1080} H=${H:-2400}
    adb shell input swipe $((W * 85 / 100)) $((H * 70 / 100)) $((W * 15 / 100)) $((H * 70 / 100)) 220
    shot swipe 1.5
    after=$(lit)
    echo "Lit before the swipe: $before after: $after"
    if [ "$before" = "$after" ]; then echo "::warning::The view swipe on Games didn't change the view (lit: $after); see swipe.png in the logs."; fi

    # a game page from a header card cover, then its Back
    tap content-desc Games || true
    if tap content-desc "$DEMO" 20; then
      if wait_for content-desc Back 15 > /dev/null; then
        shot game
        tap content-desc Back || adb shell input keyevent KEYCODE_BACK
        shot game-closed
      else
        err "A cover's tap didn't open the game page."
        shot game-missing 0
      fi
    else
      err "No made-up game's cover on Games."
    fi

    # Settings, then back closes it
    if tap content-desc Settings; then
      if wait_for text Settings 15 > /dev/null; then
        shot settings
        adb shell input keyevent KEYCODE_BACK
        shot settings-closed
        if [ -n "$(at text Settings)" ]; then err "Back didn't close Settings."; fi
      else
        err "Settings didn't open."
      fi
    else
      err "No Settings button to tap."
    fi
  fi
fi

# the 120 Hz test (it flings for ~15 s), then the app must still be alive
{ adb shell am start -W -a android.intent.action.VIEW -d "playnext://open?nudge=probe" "$PKG" 2>&1 || true; } | tr -d '\r' | tee "$LOGS/am-start-probe.txt"
if grep -q '^Error' "$LOGS/am-start-probe.txt"; then err "The probe deep link didn't open the app."; fi
sleep 20
adb logcat -d -v threadtime > "$LOGS/logcat.txt" 2>&1 || true
adb logcat -d -b crash -v threadtime > "$LOGS/crash.txt" 2>&1 || true
adb exec-out screencap -p > "$LOGS/screen.png" 2> /dev/null || true
pid=$(adb shell pidof "$PKG" 2> /dev/null | tr -d '[:space:]' || true)
if [ -z "$pid" ]; then err "The app's process is gone 20 s after the probe opened."; else echo "Still running after 20 s (pid $pid)"; fi
if awk -v p="Process: $PKG" '/FATAL EXCEPTION/ { w = 4; next } w > 0 { w--; if (index($0, p)) f = 1 } END { exit (f ? 0 : 1) }' "$LOGS/logcat.txt" "$LOGS/crash.txt"; then
  err "logcat has a FATAL EXCEPTION in $PKG."
  { grep -A 25 'FATAL EXCEPTION' "$LOGS/crash.txt" || true; } | head -n 80 || true
fi
if grep -q "ANR in $PKG" "$LOGS/logcat.txt"; then echo "::warning::An ANR for $PKG during the walk (the emulator draws in software; check the logcat in the logs)."; fi
if [ -n "$fail" ]; then exit 1; fi
if [ "$WALK" = walk ]; then echo "Smoke test passed: Home, every tab, a view swipe, a game page, Settings and the 120 Hz test"; else echo "Smoke test passed: the 120 Hz test"; fi
