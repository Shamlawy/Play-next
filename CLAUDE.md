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
- The worker's code is NOT in this repo. Changes to it must be given to the owner as a complete file to paste, with Workers AI binding named `AI`.

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
