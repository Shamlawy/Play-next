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
