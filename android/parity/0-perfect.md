# Phase 0 · Perfect run (Oct 28 2026, in progress)

Run as 7 parallel review helpers (bugs, speed, look, touch and back, Android basics, updates and safety, leftovers and parity), since the Workflow tool isn't offered in cloud chats. The usage limit stopped them twice; this file keeps what came back so the next chat can carry on.

Baseline on main (697c8cd): 94 unit tests pass, Roborazzi screenshots match, lintRelease 0 errors.

## Next step for the chat that picks this up
1. Re-run the six lenses that hadn't reported (bugs, speed, look, touch and back, Android basics, updates and safety), then one skeptic per finding (try to refute it), then a "what nobody checked" pass.
2. Fix every proven defect, release android-v10 (release=10, whatsnew v=10, LOG android-v10), then the "Approve Phase 0 on your phone" issue.

## Leftovers and parity lens (done, not yet proven by a skeptic)
No web changes since the map (APP_V still 327).

1. **high · parity** Gates 3 and 6 and the Android-basics CI checks were never built and nothing marks them deferred: no Compose stability reports, redraw counts, StrictMode/LeakCanary, Macrobenchmark main-vs-PR, cold-start timing, kill-and-reopen test, named lint rules (2-look.md §6). Real main-thread disk write found: FrameGate.kt:44 → Reporter.report → enqueue → write() (Reporter.kt:119-129) on the main looper; Pages.kt:103 reads assets/whatsnew.txt in composition. Fix: build them or list each as moved in 0.md; move Reporter.enqueue to IO.
2. **medium · parity** Release smoke test (android-release.yml:355) and Baseline Profile (BaselineProfileGenerator.kt) only open the restore screen + 120 Hz probe; tabs, rail, pill, header cards, Settings never run in the R8 build before shipping. Fix: tap "Not now", every tab, a view swipe, Settings open/close, back.
3. **medium · parity** Text calibration / accessibility: no font-scale cap, no 1.3/2.0 or 360 shots, no parity sheets; touch targets under 48dp (Pill.kt:115,170,215; HeaderCard.kt:405; Settings.kt:329).
4. **medium · parity** Settings sheet draws a grab handle but doesn't close on swipe down (Settings.kt:76-117; plan line 32).
5. **low · parity** Plan line 182 still promises the Pick reveal in Phase 0; 0.md moved it to Phase 3, Phase 3's list (185) doesn't name it.
6. **low · parity** Plan line 217: Android Map/Perfect workflows and their time/tokens not recorded.
7. **low · leftover (owner's call)** 120 Hz probe retry ladder (ProbeScreen.kt:110-121, 147, 263-277) served only the first proof.
8. **low · leftover** Dead: Library.EMPTY, ReportRules.LOG_ONLY (+ its test), mockwebserver3 and coroutines-test test deps, ui-tooling-preview, generated Web.APP_V / Web.BG_BASES.
9. **low · leftover** Plan line 236 still states the replaced back rule.
10. **low · leftover** Updater.kt:84,260 and Receivers.kt:35 comments say Home shows the install buttons (they're in Settings since android-v6).
11. **low · leftover** Two time formatters doing one job (Pages.kt asOfLine, Settings.kt atTime, Shx.kt US_SHORT).

## Look lens (done, not yet proven by a skeptic)
Matches the web: folded tab bar (to the pixel), rail, view pill, chips, header cards, background, tokens, no ripple.

1. **medium** The slash word is condensed Archivo (Type.kt:46 `FontVariation.width(62f)`, used by Wipe.kt:62-67): the web's `.pnw-slab b` (index.html 2677) is default width, ~50% wider (slash-412.png 175dp vs web 265dp). Fix: width 100 for the slash.
2. **medium** Settings sheet frame vs the web's `.sheet-in` (index.html 1561, 16440): web max 620 centred, 4vh top, scrim void .86 under a bright rail, title 22px/700, a "Close" .btn at the foot; app: max 720 minus the rail room, 12dp top, scrim .6 over the rail, title 26sp, "Done" text top right (Settings.kt:76-102).
3. **medium** Unfolded, the game page covers the rail (Shell.kt:210-214, GamePage.kt:148-214); on the web the rail stays above `#full`, the page keeps 96 clear, and "Back" sits top right (`.fullx`, index.html 2349, 16523).
4. **medium** Folded Home: the left notch corner (Shell.kt:303, 326-337) lands inside the Home panel's 28dp corner (Pages.kt:108) and ends in a thin spike (home-412.png, no-code-412.png). Fix: skip the left inner corner when the tile is within 48dp of the panel's left edge.
5. **low** Btn (Kit.kt:163-177) vs the web's `.btn` (index.html 16450-16456): ghost = u-card + 1px u-line2, primary = volt + accent glow, as wide as their words.
6. **low** settings-checked-412.png is byte-identical to settings-412.png: the Updates card is below the viewport (ScreensTest.kt:273-281 checked412), so it tests nothing. Fix: scroll to it before capturing.
7. **low** SafeModeActivity.kt:34,45-52 uses colour literals, one (0xFFB8C6E6) not a token. Fix: generated Web.* constants.

## Updates and safety lens (done; 3 proven by throwaway tests, the rest by code trace)
1. **medium** Leaving safe mode in the same process ("Try again", or the safe-mode process living 60 s) leaves the app on "Opening your games…": `PlayNextApp.onCreate` returns before `Games.start` in safe mode and nothing else starts it. Fix: one idempotent `start()` called from Application and MainActivity; calm the counter only while MainActivity is on screen.
2. **medium** A download cancelled partway (closing Settings mid "Downloading", lifecycle, WorkManager) leaves a complete `.part`; every later run asks `Range: bytes=<size>-` → 416 → Failed, silently. Also a mid-body retry reuses the old Range and duplicates bytes. Fix: part ≥ size → straight to the SHA check; Range per try; sha+swap NonCancellable; report a repeating failure.
3. **medium** Reporter: `flushSoon` cancels a flush that is already sending, so the batch is sent again (double sightings); two concurrent flushes `drop(batch.size)` and lose unsent reports. Fix: Mutex, remove by identity, cancel only the delay, rethrow CancellationException.
4. **medium** A crash report (and an offline "Report a problem") isn't sent at the next start: `adopt()` never flushes; `ranOnce` flushes only on a new version. Fix: flushSoon at the end of every start.
5. **low** Same-day releases share the "ran" report's sig (digits → #), so the second is dropped by firstToday and never retried. Fix: per-version sig; set ranV only after queueing.
6. **low** The write switch is an application interceptor, so a 307/308 re-POSTs to any path (shown with MockWebServer: /bug → /vault/put). No live path today. Fix: also addNetworkInterceptor (or followRedirects false for non-GET); a RoutesTest case.
7. **low** The release smoke test only opens the probe with no games (see leftovers 2).
8. **low** Updater.run catches only IOException: a SerializationException from ReleaseFeed.pick or a SecurityException from commit crashes the process, safe mode too; relBody is stored before it parses. Fix: catch Exception (rethrow cancellation) → Failed + report; store relBody after parsing.
9. **low** Freeze/native-crash reports name the screen "?" until you leave Home: Screen.set returns early when name == current ("home" at start). Fix: a force path / start current as "".

## Touch and back lens (done; 6 proven by throwaway Robolectric touch tests, copy at the chat's scratchpad ZzTouchTest.kt)
1. **medium** Restore screen opened from Settings ("Use a different restore code"): the first back pops the hidden Settings step and nothing changes, the next back leaves the app; "Not now" needs two taps (Root.kt:175-180). Fix: when the code step closes while games are NoCode, also set skipped = true.
2. **medium** Settings sheet ignores a swipe down (same as leftovers 4).
3. **medium** Back/Close during the 380 ms cover flight: the flying cover stays opaque over the fading page and is cut off mid-air (GamePage.kt:116-120, 152-160, 216-261). Fix: fade/scale the flying cover with the page's `shut` (or reverse the flight); keep end() until both finish.
4. **low** View pill: a second pick while the lit segment slides makes it jump ~59dp (Pill.kt:82-84, 134-138). Fix: Slide starts from the drawn spot.
5. **low** Fast double taps: unfolded, rail Settings twice = open then close (2nd tap on the scrim); folded, Done twice = close then reopen (the gear under Done).
6. **low** A game page opened by a link reuses the last cover flight and tap point (Shell.kt:137-145, 213). Fix: tie fly/tapAt to the step key they were made for.
7. **low · owner's call** Leaving the app from a tab plays Android's back-to-home shrink (manifest enableOnBackInvokedCallback=true); the plan says the shrinking preview is off.
8. **low** Tap targets under 48dp: pill buttons ~32, chips ~38, try buttons 40, spotlight line ~30, the fan's back covers.
9. **low** A drag that starts on Settings' scrim closes Settings (clickable takes a moved press as a tap).

## Speed lens (done; Compose compiler reports clean: every composable skippable; timings from a throwaway JVM test are hints)
1. **high** The Baseline Profile never reaches Home or a tab: a fresh emulator has no restore code, so it profiles the restore screen and the probe only (BaselineProfileGenerator.kt:28-36). Fix: an emulator-only made-up backup, then walk Home, each tab, a swipe, a game page, Settings.
2. **high** Header-card numbers (the whole rank table, 90 rounds through JSON getters) are worked out in composition on the main thread in the slash's first frame, once per kind (HeaderCard.kt:153 → Shx.of; Shx.kt:68,91; Rank.kt:80-122): 12-17 ms at 200 games/300 duels, 22-54 ms at 400/1000, cold 22-94 ms. Fix: all seven cards' numbers once per backup per day off Main (Games.build), one shared Rank; parse Duel/Played fields once; table only for the kinds that use it. GoldenTest keeps the numbers.
3. **medium** The updater decodes the 95 KB release list (64 ms cold) and verifies the APK on Main (Updater.run resumes on Main after Net.call), and keeps the 95 KB body in SharedPreferences (relBody). Fix: run() under Dispatchers.IO; store the picked release, not the body.
4. **medium** Cold start decrypts the restore code with a StrongBox key while the splash waits (CodeStore.kt:39-40). Fix: TEE key, migrated once; time it on the phone.
5. **medium** Screens under an open game page / Settings keep drawing every frame (tab page with shadows, pill, rail, background): HWUI has no occlusion culling. Fix: alpha 0 on what's covered once the page has fully grown; back the moment it closes.
6. **medium** Frame gate: no fold/unfold exclusion (the web skips 800 ms after a resize, v245 #305) and it judges frameDurationUiNanos only, blind to RenderThread/GPU time. Fix: skip 800 ms after a config change; use frameDurationTotalNanos, keep the UI part in the note.
7. **medium** Gate 3's checks aren't in CI (same as leftovers 1); main-thread disk: CrashGuard safeMode read twice per start, calm() 60 s in on the main looper, HomePage reads whatsnew.txt in composition on every Home visit, FrameGate.file()/Reporter.enqueue on the main handler.
8. **low** Queue placeholder regroups/resorts every game per visit and per 'checking' flip; Game.hex compiles a Regex per call (Library.kt:63,79).
9. **low** Shadows and clips inside animated layers (fan deal, pill segment, flying cover).
10. **low** The first tab switch loads the 741 KB Archivo italic font on the main thread; nothing preloads fonts.

## Bugs lens (done; by code trace)
1. **medium** Safe mode → "Try again" leaves "Opening your games…" forever (same as safety 1).
2. **low** Back after "Use a different restore code" (same as touch 1).
3. **low** "Bring my games" with a mistyped code does nothing; "That isn't a restore code" can never show (Restore.kt:61-72). Fix: show the line when !ok.
4. **low** Games' "Try again" shows no change when the same error comes back (Fetching(why) equal → StateFlow drops it); "Check for a newer copy" flashes then shows the old line, no "checked at" (L184). Fix: a checking flag on Fetching, hold the label ≥ HOLD, say when it last looked.
5. **low** Restore button stuck on "Bringing your games…" after a recreate mid-restore (`busy` is rememberSaveable, its coroutine dies). Fix: plain remember.
6. **low** Unguarded writes crash on a full phone: CrashGuard calm() on the main looper 60 s in (writes even at 0), Games.fetch's cache write, Reporter enqueue. Fix: runCatching; calm writes only when count != 0; keep the opened copy in memory.
7. **low** Two flushes at once drop or resend reports (same as safety 3).
8. **low** Shared styled text arrives empty: getStringExtra returns null for a SpannableString (Links.kt:274-277). Fix: getCharSequenceExtra()?.toString().
9. **low** A link replays an old cover flight (same as touch 6).
10. **low · owner's call** S.startTab ("Start on") is ignored; always opens Home (not in 0.md's left-out list).
