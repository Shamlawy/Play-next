# Play next as an Android app: the plan

Written Oct 22 2026 (UAE time) with the owner, before any building. Nothing here is built yet. Building starts after the fixes-only pause (Oct 23 or later) and only when the owner says go.

## What we're building

- A real Android app for the owner's phone only (Galaxy Z Fold 8 Ultra), written in Kotlin with Jetpack Compose (the owner picked it). Not in any store.
- Why: in Chrome, anything the app's own code draws is held to 60 frames a second (v263). An Android app has no such limit, so everything can run at 120.
- The web app stays: the friend's iPhone keeps using it, and it's the owner's safety net until switch day.
- Same app, same look, same Nexi. The rebuild doesn't add anything new: each screen comes over as it is today. New ideas wait until the app is complete.

## How it reaches the phone

- GitHub builds the app on every merge and puts the app file (APK) on a Releases page.
- The first install is by hand once: open the link, allow "install unknown apps" for Chrome, tap Install.
- After that the app checks for a new version by itself and downloads it. Android still asks for one tap on "Install" each time. That's Android's rule for apps outside the store, and nothing can skip it.
- One-time setup: a signing key (every update must be signed with the same key, or Android refuses it). It's kept as a GitHub secret, never in the repo or the chat. I'll make this as close to zero steps as GitHub allows.

## Your games come with you

- The app reads the same cloud backup the web app makes (restore code), so every game, score, note, list and setting moves over.
- **Until switch day the app only reads.** You keep using the web app, and the app loads your latest backup each time it opens, so your data never ends up split across two places.
- **On switch day the app starts writing backups** in the same format. Going back to the web app stays possible at any time.
- The helper (prices, reviews, hours, music, AI, Steam, calendar) stays as it is: the app calls the same helper routes.

## How we keep it free of lag and glitches

The web app's lags came from stacking features and from browser limits. The app gets these gates from day one, and a screen counts as done only when it passes all of them:

1. **Frame budget on every merge.** GitHub runs the app on an Android emulator and times every scroll, open, swipe and page turn (Macrobenchmark). A frame over budget fails the merge, like `kit.mjs perf` today.
2. **The phone reports its own slow frames.** JankStats watches every frame on your phone and files a problem report like Nexi does now (same issue flow, `auto-bug`). The emulator isn't your phone; your phone is the judge.
3. **Pictures of every screen on every merge.** Screenshot tests at folded and unfolded widths, compared with the approved ones. I look at every changed picture before merging (like `kit.mjs look`).
4. **The house rules become checks:** no press animation, no touch glow, black background with the one still gradient, the `--u-*` colours, no emoji icons, the open Queue card's rules, and so on (CLAUDE.md's Look section, rule by rule).
5. **One screen at a time.** A screen is finished, measured and approved before the next one starts. No half-built features stacked on each other.

I can't promise zero glitches in a rebuild this big. What these gates do is catch most of them before they reach you, and report the rest by themselves.

## What doesn't come over (browser-only, not needed in an app)

- The service worker, quirks-mode rules, and the Playwright test kit (replaced by the Android checks above).
- Most of Nexi's web-only lag detectors (GPU layers, hidden-screen layout reads, scroll holds). They existed to work around Chrome; JankStats replaces them.
- The "updated" reload. The app updates itself instead, and the what's-new tour stays.

## The phases

Rough sizes. A "session" = one chat, until it hands off by itself at ~300k tokens. The real number of sessions can be smaller or larger.

| # | Phase | What you get at the end | Sessions |
|---|---|---|---|
| 0 | Foundation | An app on your phone: black theme, fonts, the tab bar, your games read from the backup, self-update, frame and picture checks running | 2 |
| 1 | Queue | Rows, open card + back, the bar (tabs, Order, Shuffle, Boss), Character select | 3 |
| 2 | Game page | Pictures, prices, reviews, hours, news, notes, story so far, Edit | 3 |
| 3 | Home + rail | Home, the unfolded rail, folded/unfolded layouts | 2 |
| 4 | Add a game | Search, art pickers (SteamGridDB, stores) | 2 |
| 5 | Played, Library, Plan | Shelves, Top board, badges, the calendar | 2 |
| 6 | Taste | Insights (Shattered Sky), the skills book (page curl, music, the sealed words) | 3 |
| 7 | Settings | The Settings rail and every Customise | 2 |
| 8 | Nexi | Face, menu, chat, personalities, tour, problem reports | 3 |
| 9 | Runs by itself | Price pings, release days, notifications, cloud backup, Steam sync, Finished it? | 2 |
| 10 | Switch day | Full review, data switch, the app becomes the main one | 1–2 |

Total: about 25 build sessions, plus two ultracode runs per phase (see below). With the ultracode runs, about 8–10 weeks at a steady pace.

## Planning sessions and tokens

- I can't see your plan's limits. The usage popup in the Claude app shows them; check it before starting a long phase.
- **A normal session builds.** Use one session (or two) per phase step. One a day leaves room for your work.
- **Ultracode on every phase** (the owner, Oct 22: "I want to use ultra code to be absolutely sure it's perfect"). Each phase gets two ultracode runs around its normal build sessions:
  1. **Map, before building.** Helper Claudes read everything the web app does on that screen: every behaviour, every look rule in CLAUDE.md, and every "never bring back" line in docs/LOG.md. They turn it into a checklist (`android/parity/<phase>.md`), so nothing is lost or changed by accident in the rebuild.
  2. **Perfect, after building.** Like `/perfect` today, rewritten for Android (Phase 0 makes it): review lenses (bugs, frame times, look vs the web app, touch, folded/unfolded, data safety). Each finding is proved by a skeptic, the checklist is ticked line by line, and then a normal session fixes what was proved.
  - A phase counts as done only when its checklist is fully ticked, its Perfect run's proven findings are fixed, and the frame and picture checks pass.
  - Switch day gets one more whole-app run.
- **Cost:** one ultracode run is roughly the cost of several normal sessions (it runs about a dozen helper Claudes at high effort). With 22 runs plus one, the whole rebuild costs about 2–3 times the tokens of building alone, and takes about twice as many days. A steady rhythm that leaves room for work: one day build, the next day that phase's ultracode run, never two ultracode runs in one day. These are estimates; the first phase will show the real numbers, and this file gets updated with them.
- **Ultracode can't make it certain.** It makes it very likely: many independent eyes, each finding proven, nothing ticked off without evidence. Your phone's own frame reports stay the final judge.
- **The daily fixer also uses tokens every night.** It fixes the web app (still needed for the friend, and for you until switch day). To save tokens during the rebuild, it could fix only hand-written reports and crashes (see the open questions).
- Each session starts by reading this file and ends by ticking its phase here, so you can start a session from your phone or your PC at any time and it knows where things stand.

## Things only the owner can do (few, one time)

- [ ] Allow `dl.google.com` in the cloud environment's network access (Settings of the environment → Network access → Allowed domains). Without it I can't build or picture the app in the chat; GitHub still can, it's just slower.
- [ ] First install of the app (one link, two taps).
- [ ] The signing key secret (Phase 0 says exactly how).
- [ ] Approve each finished phase on the phone.

## Open questions (decide before Phase 0)

- The daily fixer during the rebuild: everything as now, or only hand-written reports + crashes?
- Should the friend ever get the app too? It changes nothing now (Kotlin is Android only; an iPhone app needs a Mac and a paid Apple account).

## Progress

- [ ] Phase 0 · [ ] 1 · [ ] 2 · [ ] 3 · [ ] 4 · [ ] 5 · [ ] 6 · [ ] 7 · [ ] 8 · [ ] 9 · [ ] 10
