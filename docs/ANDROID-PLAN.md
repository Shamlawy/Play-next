# Play next as an Android app: the plan

## Start here (every session reads this first and updates it last)

- **Status:** not started. Building starts **Monday Oct 26 2026, 12 PM UAE time** (the owner: "app building starts on Monday 12 pm uae time this is when my weekly reset happens", they had 20% of their weekly limit left on Oct 22). A scheduled task (`trig_011SeTVQUtaJNsxY4dLbXkCi`, one-shot, 08:00 UTC) wakes this plan's Oct 22 chat then to start Phase 0. Don't start build work before it.
- **Next step:** finish Piece 1 on branch `android-phase0-piece1` (started Oct 26, not merged). Build from `android/parity/0.md` and the critic's 14-step order in `android/parity/0-map/6-critic.md`. The 4 owner questions are answered (Decisions, Oct 26). Steps 1-9 and 11-13 are written: the project (Gradle 9.8.2 + AGP 9.4.1), `PlayNextApp`, `MainActivity` (safe-mode check, black splash, edge to edge, `playnext://open?nudge=probe`), `ui/Root.kt` (no indication, no overscroll, the background), `ui/Home.kt` (Piece 1's home: updates + "Install unknown apps", the 120 Hz test, Report a problem), `probe/` (the 120 Hz test, step 8), crash guard + safe mode, the network write switch, reports, the updater, `assets/whatsnew.txt`, `:baselineprofile`, unit tests (`android/app/src/test`), the three workflows, bugsync `android-bug`, the helper's `app` field and the guards. Note: the helper's `/vault/list|get` read the token from `Authorization: Bearer <tok>`, not a `tok=` query, and list answers `{"list":[…]}`. On the PC: `$env:JAVA_HOME="C:\Users\PC\.pn-android\jdk"; $env:ANDROID_HOME="C:\Users\PC\.pn-android\sdk"`, then `C:/Users/PC/.pn-android/gradle-9.8.2/bin/gradle.bat :app:testDebugUnitTest :app:lintRelease :app:assembleRelease` in `android/`. Left: step 10 (the signing key, made on the PC, Decisions question 1), the PR and android-check, then step 14 (merge = release android-v1, the owner installs it, v2 tests the self-update, the probe folded and unfolded).
- **How to start:** the owner says "start Phase 0" (or "next phase") in any chat. That chat reads this file and sends that phase's Brief first; it builds nothing until the owner answers it. The Monday scheduled task's prompt still says "its Map run first" (written Oct 22, and it can only be changed from the Oct 22 chat): the Brief comes before it anyway.
- **Last session:** Oct 22 2026, 1:40 AM UAE time: an ultracode check of this plan (4 review lenses, a skeptic per lens, a completeness critic). All 49 findings were confirmed, plus 7 from the critic, and the plan was rewritten with them. Nothing is built yet.

## Decisions (date · the owner's words)

- Oct 21 · "I will make it into an app but only for me": an Android app for the owner's Galaxy Z Fold 8 Ultra only. No store.
- Oct 21 · "I don't mind rebuilding it all as long as it doesn't make new glitches or lags": a native rebuild, not a wrapped install. Why: Chrome on Android holds anything the app's code draws to 60 frames a second (docs/LOG.md v263). A native app can draw at 120.
- Oct 21 · Kotlin + Jetpack Compose (picked from Kotlin/Compose and Flutter).
- Oct 21 · "or else why wait? We can build it": the rebuild doesn't wait for the web app's lag reports to reach 0 (this replaces idea M54's condition).
- Oct 22 · "I want to use ultra code to be absolutely sure it's perfect": two ultracode runs per phase, a Map before and a Perfect after.
- Oct 22 · "Switch it to light mode": the daily web fixer works only hand-written reports and crashes until switch day (CLAUDE.md, Automation).
- Oct 22 · "1a": the back swipe closes what's open on top, then goes to the last tab, then leaves the app.
- Oct 22 · "2b": notifications through Firebase, on time. The owner makes a Firebase project once (see "Things only the owner does"); Phase 9 adds what the helper needs to send through it.
- Oct 22 · on updates: "I want it easy to update the app and fast and auto, ofc I will press install cuz that's android": one Install tap per update is fine; everything else (download, check, timing) stays automatic, as in "How it reaches the phone".
- Oct 22 · question 4: "not every feature, maybe once a week or something, we will decide on that": after switch day some features also go to the friend's web app, picked together about once a week. Not every feature, not none.
- Oct 22 · question 3: "no need for updates": (a) the web app gets fixes only until switch day.
- Oct 22 · start: Monday Oct 26 2026, 12 PM UAE time, when the owner's weekly limit resets.
- Oct 23 · "I want to test every phase we do and start a tab by tab or a phase by phase … before starting any phase you need to tell me what you will do before building cuz I could remove or change or add": every phase opens with a Brief the owner answers before anything is built, and each piece comes to the phone to test as soon as it works (see "The phases").
- Oct 23 · "also the widgets i do love them so add them if they are not added": home-screen widgets, new (the web app can't have them), built in Phase 3 next to Home. Which widgets is decided in Phase 3's Brief; the first list is there.
- Oct 23 · "Read out aloud dont need the rest are your choice": read-aloud isn't ported; sounds, share sheet, icon shortcuts and deep links stay in Phase 0 (Claude's call: small, and widgets and notifications need the deep links).
- Oct 23 · "1 2 4 5 6 7 9 10 11 13 14": the owner's pick of the 14 new features (see "Nothing forgotten"); 3 (PlayStation/Xbox sync), 8 (book music in the background) and 12 (fingerprint lock) are left out.
- Oct 23 · "if we are building it i want it to be perfect no need to build it then add stuff later": a gap check through 4 lenses (the owner's wishes, what a regular Android app needs, what other apps do, the web app vs this plan). What it found is in "Nothing forgotten" below; the new features in it wait for the owner's pick.
- Oct 26 · Phase 0 Brief: "go ultracode and keep the header stuff": Phase 0 as briefed (3 pieces), the header card with all three modes and five extras. The Workflow tool wasn't offered in that chat, so the Map ran as 5 parallel helper mappers plus a completeness critic.
- Oct 26 · a "20 UI tips" list the owner shared: "skip 18 and do what you see is good". Built in as Android rules from Phase 0 on: interruptible animations (a tap mid-animation reverses it smoothly: card turn, cover fly), swipe-down closes any sheet, optimistic updates (a change shows before the save finishes), idempotency keys on every write (a retried save never counts twice), balanced headlines (game names wrap into even lines). New looks, owner-approved: picture placeholders (a soft blur in the game's own colour while a picture loads) and undo toasts (a few seconds of "Undo" after deleting or moving a game, Phase 1). Left out: continuous/squircle corners (owner), command menu, shortcut hints, Amazon safe triangle (mouse/keyboard only), tabular numbers (keep the web app's figures). Already planned: predictive back, safe areas, code autofill, cache-then-refresh, Baseline Profile, 48dp touch targets, the cover-fly transition.
- Oct 26 · the 4 Phase 0 questions, "answer these questions on your own i trust you" (Claude's picks):
  1. **Signing key copy: yes, outside Cloudflare, with no owner step.** Instead of the password manager it's a GitHub Actions secret: the key is made once on the owner's PC (the file is deleted there right after), set as the repo secret, and copied into two Cloudflare KV namespaces. That's 3 copies at 2 companies, and nothing to paste.
  2. **Old looks: not ported** (Arcade style, Chamfered corners, How round, Screen transitions: Morph, Rail width). The web app keeps them.
  3. **Sounds off means silent:** in the app the duel, CLEAR! and "Out now" sounds follow Sounds like every other sound.
  4. **Nexi's buzz: big moments only** (CLEAR!, a duel win, the card turn, a level up), never on every line he says.
- Oct 26 · Claude's call (Piece 1): **a release happens when `android/version.properties`'s `release=N` goes up**, like APP_V on the web. versionCode = max(N, the newest `android-v` tag + 1), tag `android-vN`. A merge that doesn't raise N builds and checks but releases nothing, so "one release a day" isn't needed.
- Fixed from the start: the friend's iPhone stays on the web app. The rebuild copies the app as it is: no new features unless the owner adds one in a phase's Brief (the widgets, Oct 23, are the first); Claude's own new ideas go to `learn.mjs add --kind=idea`.

## What we're building

- A native Android app in `android/` in this repo. Same screens, same look, same Nexi as the web app at the time each phase is mapped.
- The web app (index.html) keeps working for the friend for good, and for the owner until switch day.

## How it reaches the phone

- **Builds:** GitHub builds the app on every PR that touches `android/**`. It publishes a release when a merge raises `release=N` in `android/version.properties` (Oct 26, replaces "at most one a day"), tagged `android-vN`, versionCode = max(N, the newest `android-v` tag + 1), so it always goes up. The phone only ever gets a release build (R8, not debuggable) with a Baseline Profile made in CI, so it's compiled ahead and doesn't stutter on first use.
- **Signing key, with no owner step:** made once on the owner's PC with a random password (Oct 26, question 1), then kept as the repo secret `ANDROID_SIGNING_P12_B64` + `ANDROID_SIGNING_PASSWORD` and copied by a one-time workflow into two Cloudflare KV namespaces (`playnext-android-signing` and `-copy`, not the helper's NUDGE, no expiry). The PC's file is deleted after. The copy workflow refuses to overwrite a different key. Only release builds on main use it; PR builds use a throwaway key. The key never appears in an artifact, a log or the repo (public repo). If the key were ever lost, updates couldn't install over the app, so before any reinstall Claude first checks the cloud backup is current.
- **First install, once:** open the release link. If Samsung says "Auto Blocker" blocked it: Settings › Security and privacy › Auto Blocker › off, and leave it off (no turn-back-on timer). Allow Chrome to install apps, then tap Install. The first time the app updates itself, allow Play next to install apps (asked once).
- **Updates after that, by themselves:** the app checks GitHub's releases, downloads the new version and installs it itself, with no tap where Android allows it (Android 12+, an app updating itself). It installs only while the app is in the background or at the next open, never mid-use. If the phone does ask, it's one tap. Phase 0 proves on the real phone which of the two happens.
- **Never stuck on a bad version:** the update check runs first at start, before any screen loads. Two crashes within a minute of start put the app in safe mode: a plain screen that only checks for an update, installs it and files the crash report. A release reaches the updater only after the CI emulator has started it and opened every finished screen without a crash. Rollback = a "rollback-android" workflow re-releases the last good commit with a higher versionCode (Android refuses a lower one).
- **A blocked install is never silent:** if Auto Blocker comes back on, or Android refuses an install, the app shows one plain line saying which switch to change and files a problem report.
- **Google's developer verification** (expected in the UAE in 2027) may refuse unregistered apps. Before then: either a free limited-distribution registration (an owner step: it needs their identity) or the phone's one-time advanced-install setup. The app reports a refusal. Phase 0 fixes the app id and the signing key for good, because neither can change later without a reinstall.

## Your games come with you

- **The helper barely changes.** One line in Phase 0 (Oct 26): `/bug` keeps a report's `app: "android"`, so bugsync files app reports as `android-bug`. Notifications need one more change, see Phase 9. The helper only accepts calls that come from the web app's address, so the app's one network client sends `Origin: https://shamlawy.github.io` on every helper call. A Phase 0 test calls the live helper and fails on a refusal.
- **Your restore code, once:** the app's first screen asks for it. Find it in the web app at Settings › Your data › Cloud backup; it starts with PN-, and your password manager may fill it in. The app keeps it encrypted with Android's key store. It never goes into logs, reports or the repo, and Android's own phone backup is turned off for the app.
- **Until switch day the app is a practice copy:**
  - **It reads only.** It reads your latest cloud backup at start and whenever you come back to it, and shows "Your games as of 2:05 PM", with a warning when the copy is over a day old.
  - **It writes nothing that changes your records.** That means no backups, no push sign-up, no price watching, no calendar feed, no Steam sync, no book key and no usage counts. One switch in the network client blocks every writing route, and a test fails if one gets through. Read-only routes are fine (reviews, hours, art, news, music, rates, pictures), and so are its own problem reports.
  - **Edits reset.** Changes made in it show a thin "Practice copy: changes reset when the app reopens" line.
  - **It has its own name.** Its home-screen name is "Play next ·new" with a small mark on the icon, so it isn't mixed up with the web app.
- **Nothing in the backup is lost:** the app keeps your data as the same raw JSON the web app saves. It edits only the parts it knows, so fields it doesn't know survive. A CI test opens a seed backup, changes one score, seals it the way the web app does, opens it with the web app's own code and checks that only that score changed. Picture links that point at repo files are resolved against the web app's address, as the web app does.
- **The skills book:** the app never makes or sends a book key. It opens the pages with the key that's in your backup; with no key it shows the book's empty state. The sealed words are copied from index.html when the app is built.

## Switch day, step by step (Phase 10)

1. **A web release before switch day.** Before it backs up, the web app looks at the cloud's newest copy. If that copy came from the Android app (the app tags its backups `v=a<version>`), the web app stops backing up on that phone and shows "Your games now live in the Android app", with one Restore button and nothing that writes.
2. **The app takes over.** It does a last restore, then starts writing backups (as soon as it goes to the background). The same day the web app's push sign-up on your phone is turned off, so pings don't arrive twice. The app's writing routes come on one by one as the web app's side goes off.
3. **The app gets its real name.** Its home-screen name becomes "Play next". Don't uninstall the web app's icon: Claude first checks the app's backup is current, then says whether it's safe to remove the icon or keep it in a folder.
4. **Going back is a restore.** In the web app, restore the newest copy (or one of the 7 daily ones). That writes a web-tagged copy, and the app then does the same check in reverse. The friend's backup uses a different code and is never touched.
5. **The book.** Its words get sealed again to the app's key if the app ever makes its own (book-keys.yml, then tools/book-seal.mjs).

## How we keep it free of lag and glitches

A phase counts as done only when all of these pass, and the owner approves it on the phone.

1. **Phone frame gate (the real judge).** JankStats on your phone times every frame and files slow ones as problem reports. Each report says the app version and whether the build was fully compiled. A phase is approved only after real use of its screens at 120 Hz, with frame times under budget.
2. **The 120 Hz proof, before Phase 1 starts.** A probe screen goes to your phone as a release build: the real background gradient, a full-picture card with a see-through strap, an idle animation loop and a fast fling. It records the frame rhythm and the screen's refresh rate.
   - If it doesn't hold 120, the app asks Android for it directly (the frame-rate APIs).
   - If it still can't, we stop and you decide before anything else is built. Glass, pictures and see-through layers cost drawing time natively too (LOG v264, v287), so each screen gets an overdraw budget, like `tools/layers-budget.txt` today.
3. **CI checks that give the same answer every time** (the emulator can't show 120 Hz, and its frame times are noisy):
   - Compose stability reports (no list items that redraw needlessly);
   - redraw counts per scripted scroll and open;
   - no disk or network work on the main thread;
   - the Baseline Profile present and applied.

   The emulator's Macrobenchmark runs the same journeys on main's build and the PR's build. Its numbers are hints, except a clear slowdown against main, which fails.
4. **Pictures:**
   - **Screenshot tests** at the phone's real folded and unfolded widths, compared with the approved ones.
   - **Parity sheets:** for each finished screen, CI shows the web app (`kit.mjs look`, same seed) and the app side by side with a difference overlay. Claude reads every sheet before the phase's Perfect run, and your approval message links them.
   - **Text calibration in Phase 0:** text size and font scale are measured on your Fold, so one web pixel = one app dp at your settings.
5. **The same numbers as the web app.** A golden file, made in CI by running the web app's own functions with the shared seed: game states, days left, headlines, ranks, scores, order, and Nexi's guesses. It includes edge cases such as a release at 11:30 PM and 12:30 AM UAE time, because JavaScript reads plain dates as UTC. The app's unit tests must match it exactly, with a fixed clock. Random picks take a seed.
6. **House rules as code checks:** no press animation, no touch glow or ripple, colours only from the token list, one still background layer, no emoji icons, and animations only on transform/opacity, with animated values read only where they don't redraw the screen. Each look rule in CLAUDE.md that code can check gets a named lint check. The rest stay screenshot-review items from the taste log.
7. **Content comes from index.html, never typed twice.** At build time a script reads the web app's data tables into generated files (never committed): Nexi's lines and persona, the drawn icons (GICON), the background colours, colour tokens, the what's-new entries and the sealed book. A test fails if a table the app uses disappears or changes shape.
8. **Merges are held by checks, not memory:**
   - `android-check.yml` runs on every PR touching `android/**`.
   - release-guard holds an Android merge until that check has passed on the head commit, and asks for a LOG line, a higher versionCode and an app what's-new entry.
   - second-look gets Android notes.
   - The design skill covers `android/` too.
9. **One screen at a time.** Each screen comes with its own Customise groups and honours every setting it has, from its own phase.

10. **Android basics, in Phase 0 unless named** (from the Oct 23 check):
    - Accessibility: a TalkBack label on every drawn icon, 48dp touch targets, a capped font-scale rule next to the text calibration, a screenshot check at font scale 1.3 and 2.0.
    - Nothing lost when Samsung closes the app in the background: the open card, scroll spot, drafts, tab and Settings group come back (a CI test kills the app and reopens it).
    - Back: Android 16's predictive back runs the "1a" rule per layer, with its shrinking preview turned off (#309: nothing shrinks). Also off: the stretch at a list's end and the splash icon animation. No Material You colours: the app keeps its own.
    - Data: saved safely (write a new file, then swap it in), a full phone never breaks it, a data version with a migration for each app version.
    - Opens instantly from the last copy, works with no signal, then refreshes. Network timeouts, retries and a size cap on the picture cache. Every game's pictures fetched ahead on Wi-Fi (the owner, v238: "instant and always ready") from Phase 2.
    - Cold start timed in CI and on the phone (the open-time freeze, #334, stayed open across many releases). A battery and memory budget (taste T19: "I don't want the app to be a monster of battery and RAM").
    - Self-update details: the first self-update probably asks once (Chrome installed the app); the download is checked (SHA-256) and resumes; Phase 0 tests whether updates still install with Auto Blocker back on.
    - An "updated" notification after a self-update carries the release's first what's-new line; the tour runs at the next open.
    - Crash detail: each release's R8 mapping kept (so reports read clearly), Android's exit reasons (freeze, memory kill) read at start, debug-only StrictMode and LeakCanary, no secrets in logs, cleartext traffic off.
    - Game titles in Japanese or Arabic: a fallback font and right-to-left handling in rows and straps.
    - Time: the phone's time zone vs UAE time, travel, and the midnight rollover for widgets and "Out in N days".
    - Any width: split screen, a floating window and the cover screen work, not just 412 and 859 (Android 16 won't let a large-screen app lock its size). The half-open Fold doesn't break anything.
    - The splash is black with no white flash, and stays up while the backup loads.
    - Slow-frame reports follow bugsync's rules (3 sightings or 400ms+, grouped by family), never one per hitch. The on-phone checks have a cost budget (L1).
    - Keyboard: chat and Add stay above it; the restore-code field takes autofill and is marked sensitive when copied.
    - Music (Phase 6): it pauses for calls, other music and unplugged headphones.
    - Notifications (Phase 9): one channel per Nexi kind (14, `NX_KINDS`) so each can be muted in Android's settings, in Nexi's voice, a one-colour small icon, and the app icon badge (v221).
    - CI: an app-size budget, and a check that no library breaks Android 16's 16 KB pages.
    - After switch day the daily fixer works `android-bug` the same way, "Reported" first (Phase 10).

Nothing can make it certain. These gates catch most problems before they reach you, and your phone reports the rest by itself.

## What doesn't come over (browser workarounds the app doesn't need)

- The service worker and its picture shrinking, `liteUp` / wsrv copies (replaced by Coil sizing and its disk cache), quirks-mode rules and the Playwright kit (replaced by the Android checks).
- Nexi's browser-only lag detectors (GPU layers, hidden-screen layout reads), rest mode and hidden-loop pausing, Speed boost, the iPhone vibrate and sound-unlock shims, and the Safari keyboard fix.
- The update reload: the app updates itself instead, and the what's-new tour stays.
- The smoothness self-test (`#s-stest`) and live smoothness check (`prb*`): JankStats replaces them. Shrink stored art and the storage-size notes. The old hidden `#p-export` panel. Speed boost includes its Edit group.
- Nexi's design checks (cut text, overlap, faint text, blank screen, the design eye) do come over, as on-phone layout checks (Phase 8b).

## Problem reports from the app

- They carry `app: "android"` and the app's own version. bugsync files them under their own `android-bug` label, with their own version and proof logic, so web and app fixes never mix. A case for this goes in `tools/bugsync-check.mjs`, in the Phase 0 PR, before the first report is sent.
- Every report passes a title scrubber (game titles and list names are replaced), tested with a planted title.
- Crashes and freezes are saved and sent at the next start. A plain "Report a problem" exists from Phase 0.
- `android-bug` reports are worked by the build sessions, not left open by light mode.

## The phases

The phases go tab by tab (Queue, then the game page, Home, Add, Played/Library, Taste, Settings), one phase at a time: the next one starts only after you've approved the last.

Each phase, in this order:

1. **Brief, before anything is built** (the owner, Oct 23). Claude sends a short plain list: every piece the phase will build, the looks and switches on those screens that your backup and the usage issue say you don't use (you tick which stay web-only), what you'll be able to try on the phone after each piece, and how long it will take. You remove, change or add things. Nothing is built, not even the Map run, until you say go. Your changes go into the Decisions list and the phase's checklist; anything left out is marked "not ported (owner, date)". Something you add is built in that phase and noted as a change from "copy it as it is".
2. **Map** (ultracode) of what you agreed.
3. **Build, one piece at a time.** Each piece (a tab, or a part of one like the open card) comes to your phone as its own update as soon as it passes the checks, with one line on what to try, so you test as you go. Your notes on a piece get fixed before the next piece starts.
4. **Perfect** (ultracode), then a fix session.
5. **You approve it on the phone.** Claude opens a GitHub issue "Approve Phase N on your phone" with 3–5 things to try. Your approval words go into the Decisions list.

Each phase's parity checklist (`android/parity/<phase>.md`) starts with "Mapped at web vN". Every web release that touches a ported screen adds a line under "Web changes since", and each Perfect run checks them.

| # | Phase | What you get | Build sessions |
|---|---|---|---|
| 0 | Foundation | The app on your phone as a practice copy: restore code, your games from the backup, black theme, fonts (Poppins, Playfair Display, Archivo bundled), the tab bar, folded/unfolded layouts (window size classes, the hinge, fold mid-scroll keeps state), back gesture and edge-to-edge, self-update + safe mode, signing, release builds + Baseline Profile, the 120 Hz proof, every gate above, the shared kit (sounds ported from the web recipes, haptics, the header card, the view pill, calm motion (`calm()`: Animations off, the phone's reduce-motion or battery saver), the cover-fly transition, share sheet), app shortcuts and deep links. **New (picked Oct 23):** richer vibrations for the big moments (CLEAR!, a duel win, the card turn), never on every tap | 3 |
| 1 | Queue | Rows, open card + back, the bar (tabs, Order, Shuffle, Boss HP), Character select, drag to reorder, swipe to delete, the Game Shuffler deck (a hold on Shuffle), On deck, and Nexi's seat (face, blink, bubble, his place on Home and in the rail) | 3 |
| 2 | Game page | Pictures, prices, reviews, hours, news, notes, Where was I, story so far, Similar, Edit, the calling card | 4–5 |
| 3 | Home + rail + widgets | Home, the unfolded rail and its live strip, the opening reel, and home-screen widgets (new, the owner Oct 23; also on the Fold's cover screen if Samsung allows it). First list for the Brief: **Play next** (the top game's picture, name and headline, a tap opens its card), **Countdown** (the next release, "Out in 3 days"), **Nexi** (his face and a line, a tap opens chat), **Top 3** (a bigger one with the Queue's first three). They update by themselves (a backup change, midnight, a release day) and stay read-only until switch day | 4 |
| 4 | Add a game | Search, Discover, art pickers (SteamGridDB, stores, 4K), share-to-add. **New (picked Oct 23):** add a game by pointing the camera at its box or a screenshot, or by dragging a link in from Chrome in split screen | 3 |
| 5 | Played, Library, Replay, Plan | Shelves, Top board, badges, Replay, Month (with its day card, `openPeek`)/Timeline/Plan/Timer, play sessions + heatmap, wrapped + monthly recap, Radar, your lists, dropped, Game room (or you decide at its Map run whether it comes over). **New (picked Oct 23):** the live timer on the lock screen and Now Bar (Pause / Stop), a Quick Settings tile (What should I play? / start or stop a session), a yearly finish goal with a ring (also a widget), the timer on its own on the half-open Fold | 4–5 |
| 6 | Taste | Insights (Shattered Sky + its Psychology/Patterns/Analytics sections), Duels + ranking + duel badges, Friend (its sync worker isn't in this repo: read its rules first; the friend's web app must keep working), the skills book (page curl, music: Apple clips natively, YouTube in its own player at least 200px and on screen). **New (picked Oct 23):** the half-open Fold opens the skills book across the fold like a real book | 4 |
| 7 | Settings | The Settings shell (rail, "On this page", search, Most used, live preview) and the app-wide groups (look, sounds, data/backup, Nexi, This build, Steam, notifications) | 3 |
| 8a | Nexi, the character | Personalities and faces, his menu, XP/hats/lore/journal, tea break, Grumble/Shadow rules, celebrations | 3 |
| 8b | Nexi, the brain | Chat and memory, his lines, the notch tour and what's-new, problem reports and the on-phone design checks | 2–3 |
| 9 | Runs by itself | Notifications (Firebase, on time); release-date watcher + deal radar, price pings, buy advice / shopping plan / clean-up, colour from art, calendar feed, screenshot prefetch, HowLongToBeat fill, Steam tags/news refresh, Steam sync, Finished it?, while you were away, seasons, dusty backlog, crystal ball, on this day / week in review, usage counts (the Map run reads LOG v217, v219–v221, v238, v241, v248, v249, v279, v281, v294). **New (picked Oct 23):** release day as a live countdown, buttons on notifications (Played it / Snooze / Buy) sent at the hour you usually play, release days written into the phone's calendar (replacing the .ics feed on switch day), Game Pass catalogue pings like the PS Plus ones, Nexi's weekly "what you played" recap (M38), bundle and sale-ending price alerts (M39) | 4–5 |
| 10 | Switch day | The handover above, a whole-app ultracode run, the app becomes "Play next". **New (picked Oct 23):** widgets you can tap (Played it, start a session) | 2 |

Nothing is removed from the web app when you leave something out of the Android app.

## Nothing forgotten (the Oct 23 check)

Each phase's Brief and checklist must include these. They're web app features the table above doesn't name, so a Brief made from the table alone would drop them.

- **Phase 0:** restore reads the RAWG key from the backup (search, Similar and critic scores use it). The header card is Full / Simple / Off with five extras (spotlight, top genres, 6-month chart, mix bar, backdrop). The "Pick for me" reveal comes early, because the "What should I play?" shortcut needs it.
- **Phase 1:** Dusty backlog's mark on Queue rows (its logic is Phase 9).
- **Phase 2:** Why you'll love it, reset one game's scores, the three page styles (New / Showcase / Plain), character art and the hub figure (size, float, glow, strength), swipe sideways to the next game, Then & now, Finish forecast, Drifting games. Edit has 16 groups, all of them, including the release-date and finish-month pickers and play style.
- **Phase 3:** all 18 Home tiles, More for you (hold to choose and order), the orb, search from Home, the Playing now colour cards.
- **Phase 4:** auto-pick art for new games, fill missing art for every game, refresh scores, dates and tags.
- **Phase 5:** the play diary (timer and Steam sessions open it), play style (main / extras / 100%, every hours and Plan number uses it), competitive games (never finish: a rule on every screen), Library's Grid / Swipe, columns, the four cover-corner tags and reel width.
- **Phase 7:** the groups the table leaves out: Extra features (about 17 switches), Motion, Header cards, Getting around, Games database, Price alerts (region, currency, PS Plus), Your data (file backup with "Add what's missing" vs replace, "For Claude", the resets, Your usage). "Look" means all six themes plus Now playing, Clean / Arcade, Add screen style, Pictures (HQ), corners and the background colours.
- **Phase 8a:** Grumble's launch cutscene, Hot take, Seasons' hats (Seasons itself is Phase 9).
- **Phase 9:** the release-day card ("The wait is over"), battery saver (read from Android's power saving), Reviews are in, Still playing?, Where was I? on coming back, the Now playing theme, notifications' 2-a-day plan inside your usual hours and the Ramadan shift; the helper's version-update pings become the app's own "updated" notification.

### New features from the Oct 23 check: the owner's pick

The owner picked 1, 2, 4, 5, 6, 7, 9, 10, 11, 13 and 14 ("wow claude those were fantastic ideas"); they're in their phases' rows above. 3, 8 and 12 were not picked: not ported, not built. Until switch day the picked ones that write (calendar, notifications) stay off, like every other writing route.

1. Live timer on the lock screen and the Now Bar ("Playing · 1h 12m", Pause / Stop), and release day as a live countdown (Phase 5, Phase 9).
2. A Quick Settings tile: "What should I play?" or start/stop a session (Phase 5).
3. PlayStation and Xbox play time and trophies filled in by themselves, like Steam sync (Phase 9). PlayStation has no official way in: it needs a code pasted once and can break. *Not picked.*
4. Half-open Fold layouts: the book opens across the fold like a real book, the timer stands on its own (Phases 5, 6).
5. Buttons on notifications ("Played it", "Snooze", "Buy"), sent at the hour you usually play (Phase 9).
6. Add a game by pointing the camera at its box or a screenshot, or by dragging a link in from Chrome in split screen (Phase 4).
7. A yearly finish goal with a ring ("12 of 20, 2 ahead"), also as a widget (Phase 5).
8. The book's music with lock-screen controls, playing on when you leave the app (Phase 6). The web app goes quiet instead. *Not picked.*
9. Widgets you can tap after switch day: "Played it" or start a session right from the home screen (Phase 10).
10. Release days written straight into the phone's calendar (Phase 9).
11. Richer vibrations for the big moments (CLEAR!, a duel win, the card turn), never on every tap (Phase 0).
12. A fingerprint lock and a hidden preview in Recents (Phase 7). *Not picked.*
13. Game Pass catalogue pings like the PS Plus ones (Phase 9), only if you have Game Pass.
14. Ideas M38 (Nexi's weekly "what you played" recap) and M39 (bundle and sale-ending price alerts) (Phase 9).

## Time and tokens

- **Count:** about 46–53 build sessions (after the Oct 23 check and the owner's 11 picks), about 11 fix sessions and 23 ultracode runs (2 per phase + switch day). That's about 70 working days: roughly 3½ months at 5 days a week, re-estimated after Phase 1 from the real pace.
- **Rhythm that leaves room for work:** one day build, the next day that phase's ultracode run. Never two ultracode runs in one day.
- **Cost:**
  - One ultracode run costs roughly several normal sessions. The plan check on Oct 22 used 9 helper Claudes, about 1.3 million tokens and 22 minutes.
  - Phase 0 builds the Android Map and Perfect workflows and records their real time and tokens here.
  - If limits bite, the small phases (3, 4, 5, 7) can share one combined ultracode run.
- **Limits:** Claude can't see your plan's limits. Check the usage popup in the Claude app before a long phase. Each session updates "Start here", so any chat on your phone or PC can pick up.

## Things only the owner does (all one-time)

- [ ] Allow `dl.google.com` in this cloud environment's network access (environment settings › Network access › Allowed domains, steps: https://code.claude.com/docs/en/cloud-environments#network-access). Without it, only GitHub can build the app, and Claude can't run its tests in the chat.
- [ ] Answer the open questions below.
- [ ] First install (link; Auto Blocker off if asked; allow Chrome to install apps; Install), then allow Play next to install apps when it first updates.
- [ ] Type your restore code into the app once.
- [ ] Make a Firebase project once (Phase 9; Claude sends the steps then, about 5 minutes).
- [ ] Allow notifications when the app asks (Phase 9 or switch day).
- [ ] If Android ever asks for developer verification (expected 2027): one registration or the one-time advanced setup.
- [ ] Approve each finished phase on the phone (a GitHub issue lists what to try).

These are the agreed exceptions to CLAUDE.md's "never give the owner install steps": Android won't install an app any other way.

## The owner's answers in short (Oct 22)

1. **Back swipe:** closes what's open first, then goes back a tab, then leaves the app.
2. **Notifications:** Firebase, on time. The owner makes a Firebase project once, in Phase 9.
3. **New web features during the rebuild:** none. The web app gets fixes only until switch day.
4. **After switch day:** some features also go to the friend's web app, picked together about once a week.
- **Updates:** one Install tap per update is fine. Everything else happens by itself.
- **Start:** Monday Oct 26, 12 PM UAE time.

## Progress

- [x] Plan written (Oct 21) and checked by ultracode (Oct 22)
- [x] Open questions answered (Oct 22)
- [ ] Phase 0 · [ ] 1 · [ ] 2 · [ ] 3 · [ ] 4 · [ ] 5 · [ ] 6 · [ ] 7 · [ ] 8a · [ ] 8b · [ ] 9 · [ ] 10
