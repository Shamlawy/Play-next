# Play next as an Android app: the plan

## Start here (every session reads this first and updates it last)

- **Status:** not started. Building starts **Monday Oct 26 2026, 12 PM UAE time** (the owner: "app building starts on Monday 12 pm uae time this is when my weekly reset happens", they had 20% of their weekly limit left on Oct 22). A scheduled task (`trig_011SeTVQUtaJNsxY4dLbXkCi`, one-shot, 08:00 UTC) wakes this plan's Oct 22 chat then to start Phase 0. Don't start build work before it.
- **Next step:** Phase 0's Map run, on Monday Oct 26, 12 PM UAE time. Every open question is answered.
- **How to start:** the owner says "start Phase 0" (or "next phase") in any chat. That chat reads this file and does the next step.
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
- Fixed from the start: the friend's iPhone stays on the web app. The rebuild copies the app as it is: no new features, new ideas go to `learn.mjs add --kind=idea`.

## What we're building

- A native Android app in `android/` in this repo. Same screens, same look, same Nexi as the web app at the time each phase is mapped.
- The web app (index.html) keeps working for the friend for good, and for the owner until switch day.

## How it reaches the phone

- **Builds:** GitHub builds the app on every PR that touches `android/**`. It publishes a release only when a merge changes `android/**`: at most one a day unless it fixes a crash, tagged `android-vN`, versionCode = the workflow's run number (always goes up). The phone only ever gets a release build (R8, not debuggable) with a Baseline Profile made in CI, so it's compiled ahead and doesn't stutter on first use.
- **Signing key, with no owner step:** a one-time workflow makes the key on GitHub's machine with a random password. It stores it in its own Cloudflare KV namespace through the existing Cloudflare token (not the helper's NUDGE, no expiry, with a second copy). It refuses to run if a key already exists. Only release builds on main fetch it; PR builds use a throwaway key. The key never appears in an artifact, a log or the repo (public repo). If the key were ever lost, updates couldn't install over the app, so before any reinstall Claude first checks the cloud backup is current.
- **First install, once:** open the release link. If Samsung says "Auto Blocker" blocked it: Settings › Security and privacy › Auto Blocker › off, and leave it off (no turn-back-on timer). Allow Chrome to install apps, then tap Install. The first time the app updates itself, allow Play next to install apps (asked once).
- **Updates after that, by themselves:** the app checks GitHub's releases, downloads the new version and installs it itself, with no tap where Android allows it (Android 12+, an app updating itself). It installs only while the app is in the background or at the next open, never mid-use. If the phone does ask, it's one tap. Phase 0 proves on the real phone which of the two happens.
- **Never stuck on a bad version:** the update check runs first at start, before any screen loads. Two crashes within a minute of start put the app in safe mode: a plain screen that only checks for an update, installs it and files the crash report. A release reaches the updater only after the CI emulator has started it and opened every finished screen without a crash. Rollback = a "rollback-android" workflow re-releases the last good commit with a higher versionCode (Android refuses a lower one).
- **A blocked install is never silent:** if Auto Blocker comes back on, or Android refuses an install, the app shows one plain line saying which switch to change and files a problem report.
- **Google's developer verification** (expected in the UAE in 2027) may refuse unregistered apps. Before then: either a free limited-distribution registration (an owner step: it needs their identity) or the phone's one-time advanced-install setup. The app reports a refusal. Phase 0 fixes the app id and the signing key for good, because neither can change later without a reinstall.

## Your games come with you

- **The helper stays as it is.** It only accepts calls that come from the web app's address, so the app's one network client sends `Origin: https://shamlawy.github.io` on every call. A Phase 0 test calls the live helper and fails on a refusal. (Notifications need one helper change, see Phase 9.)
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

Nothing can make it certain. These gates catch most problems before they reach you, and your phone reports the rest by itself.

## What doesn't come over (browser workarounds the app doesn't need)

- The service worker and its picture shrinking, `liteUp` / wsrv copies (replaced by Coil sizing and its disk cache), quirks-mode rules and the Playwright kit (replaced by the Android checks).
- Nexi's browser-only lag detectors (GPU layers, hidden-screen layout reads), rest mode and hidden-loop pausing, Speed boost, the iPhone vibrate and sound-unlock shims, and the Safari keyboard fix.
- The update reload: the app updates itself instead, and the what's-new tour stays.
- Nexi's design checks (cut text, overlap, faint text, blank screen, the design eye) do come over, as on-phone layout checks (Phase 8b).

## Problem reports from the app

- They carry `app: "android"` and the app's own version. bugsync files them under their own `android-bug` label, with their own version and proof logic, so web and app fixes never mix. A case for this goes in `tools/bugsync-check.mjs`, in the Phase 0 PR, before the first report is sent.
- Every report passes a title scrubber (game titles and list names are replaced), tested with a planted title.
- Crashes and freezes are saved and sent at the next start. A plain "Report a problem" exists from Phase 0.
- `android-bug` reports are worked by the build sessions, not left open by light mode.

## The phases

Each phase: **Map** (ultracode) → build sessions → **Perfect** (ultracode) → a fix session → you approve it on the phone. At the end of each phase Claude opens a GitHub issue "Approve Phase N on your phone" with 3–5 things to try. Your approval words go into the Decisions list.

Each phase's parity checklist (`android/parity/<phase>.md`) starts with "Mapped at web vN". Every web release that touches a ported screen adds a line under "Web changes since", and each Perfect run checks them.

| # | Phase | What you get | Build sessions |
|---|---|---|---|
| 0 | Foundation | The app on your phone as a practice copy: restore code, your games from the backup, black theme, fonts (Poppins, Playfair Display, Archivo bundled), the tab bar, folded/unfolded layouts (window size classes, the hinge, fold mid-scroll keeps state), back gesture and edge-to-edge, self-update + safe mode, signing, release builds + Baseline Profile, the 120 Hz proof, every gate above, the shared kit (sounds ported from the web recipes, haptics, the header card, the view pill, calm mode, the cover-fly transition, share sheet, read-aloud), app shortcuts and deep links | 3 |
| 1 | Queue | Rows, open card + back, the bar (tabs, Order, Shuffle, Boss HP), Character select, drag to reorder, swipe to delete, peek, On deck, and Nexi's seat (face, blink, bubble, his place on Home and in the rail) | 3 |
| 2 | Game page | Pictures, prices, reviews, hours, news, notes, Where was I, story so far, Similar, Edit, the calling card | 4–5 |
| 3 | Home + rail | Home, the unfolded rail and its live strip, the opening reel | 3 |
| 4 | Add a game | Search, Discover, art pickers (SteamGridDB, stores, 4K), share-to-add | 2 |
| 5 | Played, Library, Replay, Plan | Shelves, Top board, badges, Replay, Month/Timeline/Plan/Timer, play sessions + heatmap, wrapped + monthly recap, Radar, your lists, dropped, Game room (or you decide at its Map run whether it comes over) | 3–4 |
| 6 | Taste | Insights (Shattered Sky + Psychology/Patterns/Analytics), Duels + ranking + duel badges, Friend (its sync worker isn't in this repo: read its rules first; the friend's web app must keep working), the skills book (page curl, music: Apple clips natively, YouTube in its own player at least 200px and on screen) | 4 |
| 7 | Settings | The Settings shell (rail, "On this page", search, Most used, live preview) and the app-wide groups (look, sounds, data/backup, Nexi, This build, Steam, notifications) | 3 |
| 8a | Nexi, the character | Personalities and faces, his menu, XP/hats/lore/journal, tea break, Grumble/Shadow rules, celebrations | 3 |
| 8b | Nexi, the brain | Chat and memory, his lines, the notch tour and what's-new, problem reports and the on-phone design checks | 2–3 |
| 9 | Runs by itself | Notifications (see the open questions); release-date watcher + deal radar, price pings, buy advice / shopping plan / clean-up, colour from art, calendar feed, screenshot prefetch, HowLongToBeat fill, Steam tags/news refresh, Steam sync, Finished it?, while you were away, time capsule, seasons, dusty backlog, crystal ball, on this day / week in review, usage counts (the Map run reads LOG v217, v219–v221, v238, v241, v248, v249, v279, v281, v294) | 3 |
| 10 | Switch day | The handover above, a whole-app ultracode run, the app becomes "Play next" | 2 |

Before each phase's Map run, Claude lists the alternate looks and switches on that screen that your backup and the usage issue say you don't use. You tick which can stay web-only (nothing is removed from the web app). The rest are ported, and anything left out is marked "not ported (owner, date)" in the checklist.

## Time and tokens

- **Count:** about 35–40 build sessions, about 11 fix sessions and 23 ultracode runs (2 per phase + switch day). That's about 60 working days: roughly 3 months at 5 days a week, re-estimated after Phase 1 from the real pace.
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
