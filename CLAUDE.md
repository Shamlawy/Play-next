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
- Worker code lives in `worker/helper.js` (routes: `/sgdb/...`, `POST /why`, `POST /chat`, `POST /chat/memory`, `GET /nudge/key`, `POST /nudge`, `POST /nudge/test`, `/vault/put|list|get|del` (cloud backup), `POST /prices` (price alerts: Steam, PS Store, Nintendo eShop), `POST /recap` (story so far), `POST /steam` (Steam tags + more like this), `POST /reviews` (Steam player reviews), `POST /hours` (hours to beat from HowLongToBeat), `POST /art` (game pictures from Steam, Xbox, PlayStation, Nintendo), `/fx` (exchange rates), plus a `scheduled` cron handler). It needs a Workers AI binding named `AI`, a KV binding named `NUDGE` (push subscriptions + the VAPID key it makes itself + `appv`, the last app version it announced) and a cron trigger `*/15 * * * *`. This container can't reach Cloudflare: `.github/workflows/deploy-helper.yml` deploys it on every push to main that touches `worker/**` (or by hand from the Actions tab). It needs the repo secret `CLOUDFLARE_API_TOKEN` (an Account API token; the account id is read from `wrangler whoami`, a `CLOUDFLARE_ACCOUNT_ID` secret overrides it; never put tokens in files or chat), finds or makes the KV namespace (title containing "nudge") and deploys with `wrangler.deploy.json` (the id filled in). Bump `HELPER_V` when the app starts relying on a new helper feature; `helperCheck()` in Settings → Nexi says whether the live helper is current.

## Files
- Never commit backups: `.gitignore` blocks `*.json` (backups hold private data).
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

## Problem reports: Nexi learns from every manual report (required)
- When fixing a "Reported" (🐞) issue, also ask: could the app have caught this by itself? If yes, teach it: add or extend a check in the problem-report code (bugLayout / bugLook / the lag observers, last scripts in `index.html`) so the same kind of problem is reported automatically next time. Prove it with a planted copy of the problem (reported) and a full sweep of every screen at 412/859/360 (0 false reports). Say so in the fix's WHATS_NEW line and the issue comment ("I've learned to spot this myself now").
- Use the report's "What was at the spot" block (from 📍 Show me where: element path, size, scroll vs client size, styles, what clips it, picture load state) to reproduce it exactly.
- If it can't be detected (a matter of taste: colours, spacing, a wrong picture), say so in the issue comment.

## What the owner loves
- Automatic things that work on their own (problem reports Nexi files himself, updates that just arrive, cloud backup, price pings). Fresh automatic features that other apps don't have are their favourite: when choosing what to build, prefer the version that runs by itself over one that needs a tap.

## Future selling plan
- Credit RAWG with a link.
- Users bring their own Steam / SteamGridDB keys.
- No PlayStation Store.
- No logos or brand names in store listings.
- Nexi's "beta" lines must change.

## Hard-won rules (details and the story behind each are in docs/LOG.md)
- Many functions are wrapped by later scripts (`renderHome`, `openFull`, `save`, `gdMarkup`…): never read state off a function (`fn.x`, v198), extend a `const` instead of reassigning it (v216), and don't call a function that lives inside another script's scope (v237). New code goes in the last `<script>`/`<style>`.
- Never read layout (rects, scrollTop, computed style) right after an innerHTML redraw: do it in requestAnimationFrame or let an observer report it (v206).
- Give classes unique names: grep `\.name\b` before adding one (clashes: .aw v164, .dash v195, .pl v204, .plat v228, shx-<k> v210).
- A `fill: "forwards"` animation must be cancelled on every path that ends it (v215). No `zIndex` in keyframes (v168); no filter/clip-path/mask on spinning layers (v167).
- Don't debounce idle work on "any DOM change": filter the mutations or cap the wait (v202).
- The page has no doctype (quirks mode): `$("#id")` uses getElementById; don't add a doctype without a full layout sweep (v219).
- Colours: `var(--volt)` / `rgba(var(--vrgb), a)`, never hex (v181); new UI uses the `--u-*` tokens, no italic/uppercase headings (v188). No native date inputs (v180).
- Bookkeeping that changes on every open/exit goes in its own localStorage key, not `S` (else the cloud backup re-uploads, v211/v212). `save()` is deferred; use `saveNow()` when the result matters (v169).
- Copying CSS: never filter lines (multi-line rules break); share a selector with `:is()` (v218).
- `worker/helper.js`: before pushing, copy it to a `.mjs`, run `node --check` AND import it in Node, and exercise changed routes with a fake `env` (v208 fix, helper v16).
- This container can't reach Steam / PS Store / eShop / HowLongToBeat / Cloudflare: use `price-probe.yml`, `hltb-probe.yml` (and their inputs) and read the output with get_job_logs.
- Testing: real touch via CDP `Input.dispatchTouchEvent`, not mouse (v207); detector sweeps without screenshots, one at a time, with `S.lastV` = current so no tour runs (checks are off while touring, v219). Reports from the friend (iPhone 11 / iPad Safari) → reproduce with `webkit-check.yml`, not Chromium (v229). Test harnesses live in the scratchpad and are lost between chats; docs/LOG.md describes how each was built.
- Each release's first `WHATS_NEW` `say` line becomes the update notification: make it a good one-line pitch (v191). Detector-only fixes ship without an APP_V bump.
- Closing an issue: write "Fixed in vN" in the comment, or bugsync reopens it on a later sighting (v231). Never put game titles, scores or personal data in commits/issues (the repo is public).

## Automation
- Daily bug fixer: Routine "Play next: daily bug fixes" (`trig_01RcRcYQJqZPDuBGMhvJmZZG`, 03:46 UTC) wakes session `session_01UrvHBmr3w5i37FFfMfe9a8`; it runs bugs.yml, fixes open `auto-bug` issues, ships one release. If that session is archived, make a new one with create_session and point the trigger at it.
- Workflows: deploy-helper.yml (worker/** on main), announce.yml (update pings), bugs.yml (problem reports → issues, `.github/scripts/bugsync.mjs`), price-probe.yml, hltb-probe.yml, webkit-check.yml.

## Claude mod: usage-guard
- `.claude/skills/usage-guard/` is a Claude Code mod that loads by itself in every chat on this repo. Each message carries a hidden usage note (5-hour + weekly limits, chat size); past 200k tokens it asks Claude to suggest a fresh chat (again every 50k), past 300k on every reply; a limit passing 50/75/90% is mentioned once. When the owner types "handoff" (or `/handoff`), Claude makes sure everything is pushed, writes a handoff note and starts a new cloud session with it (create_session). `/meter` shows the numbers.
- Test it with `claude plugin test .claude/skills/usage-guard` and `claude plugin validate …`. `.gitignore` lets its two `.json` manifests through.

## Log
- Moved to `docs/LOG.md` (every release, newest last). Add a line there for every release and no-version fix. Grep it for a feature's name before changing that feature.
