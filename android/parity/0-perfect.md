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
