# Play next — notes for Claude

## The app
- "Play next": a single-file web app, `index.html` (must stay lowercase — GitHub Pages is case-sensitive).
- Hosted on GitHub Pages: https://shamlawy.github.io/Play-next/ (served from `main`).
- Other files: `sw.js` (service worker), `manifest.webmanifest`, icons.

## Users — every change must work on both
- Owner: Samsung Galaxy Z Fold, Chrome, installed to home screen (folded + unfolded widths).
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
