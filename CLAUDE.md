# Play next — notes for Claude

## The app
- "Play next": a single-file web app, `index.html` (must stay lowercase — GitHub Pages is case-sensitive).
- Hosted on GitHub Pages: https://shamlawy.github.io/Play-next/ (served from `main`).
- Other files: `sw.js` (service worker), `manifest.webmanifest`, icons.

## Users — every change must work on both
- Owner: Samsung Galaxy Z Fold 8 Ultra (new, high refresh), Chrome, installed to home screen (folded + unfolded widths).
- Friend: iPhone 11, Safari, installed to home screen.

## Helper
- Cloudflare Worker: https://playnext-helper.hussamnabil48.workers.dev
- Proxies SteamGridDB; key is the worker secret `SGDB_KEY`.
- Worker code lives in `worker/helper.js` (routes: `/sgdb/...` and `POST /why`). It needs a Workers AI binding named `AI`. It is not auto-deployed: this container can't reach Cloudflare.

## Files
- Never delete image/files in the repo root, even if `index.html` doesn't mention them: the owner's saved games (in the phone's storage) can link to them directly (e.g. `fefw-key-art.jpg` = Fire Emblem Fortune's Weave art).

## Look
- Black / near-black theme.
- When the owner shares a reference design: copy its layout and style, not its colours.

## Speed rule
- Must feel smooth at high refresh rate.
- Animate only `transform` and `opacity`. No looping blur. No stacked effects.
- Test performance in a browser (Playwright + Chromium at /opt/pw-browsers) before saying it's done.

## Settings
- Grumble is hidden by default (Settings toggle).
- Each screen's Customise shows only that screen's settings.

## Updates
- Never give the owner install or upload steps; handle updates yourself (commit + push).
- How updates reach the phone: `sw.js` loads the page network-first (bypassing HTTP cache), so a push shows on the next open. The app also re-checks when you come back to it after 10+ min and reloads if `APP_V` changed.
- Bump `APP_V` in `index.html` on every release (this shows the "updated" popup and triggers the auto-reload).
- Bump `SHELL` in `sw.js` only if the cache must be wiped.
- The old in-app "Load a new version" / "Feed me a new version" upload was removed (v161): a hand-loaded copy used to override GitHub updates forever.

## What's new tour (required on every release)
- After an update, Nexi runs a tour (`nxTour`) of every `WHATS_NEW` entry newer than the version the phone had.
- Every user-facing change MUST add a `WHATS_NEW` entry (in the v172 script near the end of `index.html`) with: `v` = the new `APP_V`, `go` (open the right screen: `nxOpenGame()`, `nxQueue()`, `nxHome()`, `nxOpenSettings(sel)` or custom), `at` (selector or function returning the element to point at, or null), `say` (what it does, can use <b>), `tryIt` (what to try).
- Test the tour in Playwright (set `S.lastV` to the previous version and reload) at 412 and 900 widths.
- Settings → This build → "Show me what's new" replays every entry.

## Future selling plan
- Credit RAWG with a link.
- Users bring their own Steam / SteamGridDB keys.
- No PlayStation Store.
- No logos or brand names in store listings.
- Nexi's "beta" lines must change.

## Log
- v161: renamed Index.html → index.html (site root was broken); removed hand-loaded build override; auto-update on return.
- v162: cleanup. Removed dead code (pnSpeak, 34 unused CSS rules), unused files (key art, duplicate icons). Image sweep now runs only when new elements appear (was every 1.2s forever). Countdown and Grumble timers pause when hidden/off. Idle main-thread work per 10s: 31ms → 15ms; style recalcs 26 → 0.
- v163: "Why you'll love it" button on each game page. Sends your rated games (3+ needed) to the helper's `/why`, which asks free Workers AI (Llama 3.1 8B). Answer cached per game in `S.aiWhy` until ratings change.
- v164: "Why" sheet used class `.aw`, which clashed with an existing layout class (broke on wide screens) → renamed `.awc`. Sheet shows the helper's error text. Helper: tries several AI models, returns errors with CORS, and `GET /why` is a health check (open it in a browser).
- v164 fix: restored the image files removed in v162 (Fortune's Weave cover was one of them).
- v165: lag fix for owner's older phone. With Speed boost (html.fast, on by default) only the first 4 pieces of a screen cascade in; the rest don't animate (~24% less style/layout/paint per tab switch). Nexi's power-up sparks now stop by themselves after 6s instead of running until a tap. Perf harness: Playwright, 4x CPU throttle, trace RunTask/Paint/Layerize per tab switch.
- v166: "↺ Reset scores" on each game page (clears rating, hype, category/sub scores, why-tags, its duels, AI answer; game stays played). Settings → Your data → "Reset all scores" (all of the above for every game + all duels + badges; games/art/hours/diary kept; double confirm). Game-page buttons now wrap and stay clear of the side rail on wide screens.
- v167: Nexi's glow (`#pnbub .aura`, a spinning conic disc) sometimes showed as a square. Removed the stacked blur/clip-path/mask fixes; now plain `border-radius:50%` + painted box-shadow. Don't put filter/clip-path/mask on spinning layers.
- v168: queue card close lag. Row glide (FLIP after renderQueue) had `zIndex` in its keyframes, which kept it off the GPU and repainted every row each frame → z-index now set on the element during the animation; off-screen rows skip the glide. Image-loading shimmer animated background-position (repaint per frame) → opacity pulse. Close: paint ~70ms → ~25ms at 4x throttle.
- v169 (perf round 1): cached Intl formatters globally (Date/Number toLocale* patched at top of first script; ~37x faster, same output). Converted main-thread animations to transform/opacity: "Out now" tag shine, lx progress bar, card-flip shine, boot bar, boss blink/glint; removed looping HP-bar mask/background loops; home ring blob lost its looping blur. Bench harness: scratchpad h/bench.js (60 games, 4x throttle, every tab/scroll/queue card/game/settings/edit).
- v169 (perf round 2): save() is now deferred (1.2s + requestIdleCallback) and coalesced; flushes on visibilitychange/pagehide; use saveNow() when the result matters. Big uploaded images (data: URIs) render via cached blob: URLs (esc/cssUrl) so redraws don't re-parse MBs; form inputs that get saved back use escRaw. Duel-row MutationObserver throttled to once per frame. Measured (4x throttle, 25 uploaded covers): total work -23%, worst frame 233→150ms, queue card close 576→249.
- v170 (perf round 3): queue background (#bgart/#bgart2) had a live CSS blur that the compositor re-ran every frame (queue idle: 57/66 frames dropped). With Speed boost it's now pre-blurred once into a canvas (bgBake; ctx.filter on Chrome, smaller canvas on Safari), redrawn on resize. Dropped near-invisible backdrop-filter blurs on ~90%-opaque panels. Shelf covers no longer transition filter. Queue idle: 0-1 dropped frames.
- v170 (perf round 4): renderQueue uses qPatch(): if the header and row order are unchanged, only rows whose HTML changed are swapped (template → replaceWith); otherwise full innerHTML. Row listener binding guarded with r._qb so kept rows aren't double-bound. Card open click task 275→78ms (4x).
- v170 (perf round 5): #fx sparks canvas is display:none unless sparks are flying (was an always-on full-screen layer). Final bench (4x throttle, 60 games, 25 uploaded covers) vs pre-perf build: worst frame 500→167ms folded, 483→183ms unfolded; dropped frames 290→114 folded, 868→467 unfolded. Bench tip: set S.fxSeen/S.fxCal when seeding or the "Out now" overlay covers the app.
- v171: swipe a queue card (row or open hero) left → red Delete button (confirm; removes game, played entry, its duels, AI answer). Only children slide (`translate` via --sx); `.qdel` sits at z-index -1 inside the row. Horizontal swipe cancels the long-press reorder (synthetic touchcancel) and stops touchend propagation so the sub-tab swipe doesn't fire. Rows have touch-action: pan-y.
- v172: Nexi's what's-new tour (WHATS_NEW + nxTour, card #nxtour, Nexi points via nxPoint without covering the target). First run shows v163–v172 features. Replay button #s-whatsnew.
- v173: tour fixes. Nexi showed as a blank circle during the tour when the rail was open (or on a phone): the bubble is only dressed by pnBubShow, which runs when the rail is hidden → nxDress() calls pnBubFace() before nxPoint/nxFlyTo. Skipped updates stack: tour covers everything since `S.tourFrom` (last finished tour), with a "You missed N updates" intro when >1 version; `S.tourFrom` is only set on Done/Skip, and boot resumes an unfinished tour. Steps with no target park Nexi beside the card. The "Updated" toast is dismissed when the tour starts (it covered the card on phones). The step you're on is saved (`S.tourAt` = {from, n, i}, saveNow on each Next), so reopening resumes at that step ("Picking up where we left off"). One-time `S.tourRedo`: every existing user gets the full tour again from v163 (owner missed the first one); fresh installs skip it.
- v174: Home merged with the owner's Dribbble "Game Dashboard" reference (layout/style only, our black + blue colours). `dxHome()` builds one panel (`.dxw`, all classes `dx*`): tabs + search (`#dxq`, results open the game) + pills; the pick as a player card (`.dxhero`, play button, dice = pick one, ⚔ = duels); Up next list (`.dxr`); queue count with floating covers (`.dxq`); "Playing now" tall cards with progress (`.dxc`); queue-hours donut (`.dxstat`, SVG); next release as a tilted ticket with live countdown (`.dxtick`). Other tiles live in the two swipe stacks under "More for you" (`.dash.dxmore`). Phone (<620, not rail/dock) nav is an icon row; on Home the open tab sits in a notch cut into the panel. 620px+ is two zones (donut + ticket on the bottom row). New Home toggles: "queue", "playing". Library layouts Arc and Wheel removed (saved arc/wheel → Swipe). Perf (4x throttle) vs v173: equal tab switch, fewer dropped frames (412: 59→45, 900: 128→108).
- v175: the old Queue hours glow (`.ring .blob`: wobbly conic disc spinning 9s + breathing radial glow, same recipe as Nexi's aura) is back as `.dxorb` behind the queue count in "Your queue" on Home. Transform/opacity only; 0 dropped frames idle at 4x throttle. Stops under html.still / reduced motion.
