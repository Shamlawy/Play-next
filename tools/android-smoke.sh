#!/usr/bin/env bash
# The Android app's walk on a CI emulator: android-release.yml runs it on the signed APK before publishing, android-check.yml
# on every PR's release build (R8, debug-signed), so a walk that would block a release fails the PR first.
# It installs the APK, sets the made-up backup's switch (the app's Demo: a global setting only the shell can write, read
# only on an emulator), opens the app and walks Home, every tab, a one-finger view swipe, a game page (a header card
# cover) and Settings, checks the tab and view come back after Android closes the app in the background, then opens the
# 120 Hz test and checks the app is alive 20 s later with no crash. A picture of each screen, the screens' nodes, the
# logcat and StrictMode's findings (strictmode.txt: a warning, not a failure) go to the logs folder. "probe" as the third
# argument skips the walk (a rollback to an app from before the made-up backup, android-v10, shows only the restore screen).
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
# a bigger log, so StrictMode's lines from the start are still there at the end
adb logcat -G 16M > /dev/null 2>&1 || true
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
  # a hint only: the emulator draws in software and its timings are noisy (the phone's own start time is the judge)
  cold=$(sed -n 's/^TotalTime: *//p' "$LOGS/am-start.txt" | head -n 1)
  echo "Cold start to the first frame: ${cold:-?} ms (emulator, a hint)"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then echo "Cold start on the emulator: **${cold:-?} ms** (a hint)" >> "$GITHUB_STEP_SUMMARY"; fi

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

    # Android closing the app in the background (Samsung does it often): the tab and view come back as you left them
    tap content-desc Games || true
    shot before-kill 1
    was=$(lit)
    adb shell input keyevent KEYCODE_HOME
    sleep 2
    adb shell am kill "$PKG" || true
    sleep 2
    if [ -n "$(adb shell pidof "$PKG" 2> /dev/null | tr -d '[:space:]' || true)" ]; then
      echo "::warning::am kill didn't stop the app in the background, so the reopen check didn't run."
    else
      adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
      if wait_for content-desc Games 30 > /dev/null; then
        shot reopened
        now=$(lit)
        echo "Lit before Android closed the app: $was after reopening: $now"
        if [ "$was" != "$now" ]; then err "After Android closed the app in the background it came back on '$now', not '$was'."; fi
      else
        err "The app didn't come back after Android closed it in the background."
        shot reopened-missing 0
      fi
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
# StrictMode (on in this build only because of the made-up backup's switch, the app's Strict object): main-thread disk and
# network work and leaks, one line per kind and the first frame in the app's own code (R8 keeps the names)
awk -v pkg="$PKG" '
  function flush() { if (v != "") print v " | " (f == "" ? "(no app frame)" : f); v = ""; f = "" }
  /StrictMode: StrictMode (policy|VmPolicy) violation/ { flush(); v = "?"; if (match($0, /android\.os\.strictmode\.[A-Za-z]+/)) v = substr($0, RSTART + 22, RLENGTH - 22); next }
  /StrictMode:[[:space:]]+at / { if (v != "" && f == "" && index($0, pkg)) { f = $0; sub(/.*[[:space:]]at /, "", f) } next }
  /StrictMode:/ { next }
  { flush() }
  END { flush() }' "$LOGS/logcat.txt" | sort | uniq -c | sort -rn > "$LOGS/strictmode.txt" || true
mine=$(grep -vc '(no app frame)' "$LOGS/strictmode.txt" || true)
echo "StrictMode on the walk: $(wc -l < "$LOGS/strictmode.txt") kinds, ${mine:-0} with a frame in the app's code (strictmode.txt):"
head -n 40 "$LOGS/strictmode.txt" || true
if [ "${mine:-0}" -gt 0 ]; then echo "::warning::StrictMode saw ${mine} kinds of main-thread work or leaks in the app's own code during the walk (strictmode.txt in the logs)."; fi
if grep -q "ANR in $PKG" "$LOGS/logcat.txt"; then echo "::warning::An ANR for $PKG during the walk (the emulator draws in software; check the logcat in the logs)."; fi
if [ -n "$fail" ]; then exit 1; fi
if [ "$WALK" = walk ]; then echo "Smoke test passed: Home, every tab, a view swipe, a game page, Settings and the 120 Hz test"; else echo "Smoke test passed: the 120 Hz test"; fi
