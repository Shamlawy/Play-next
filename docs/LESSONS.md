# Lessons

What Claude learned working on this repo: mistakes (and what to do instead), missed opportunities, and ideas. The learn mod
(`.claude/skills/learn`) asks for a lesson when a turn had a correction, a failed check, a guard stop or a release, and shows
each lesson again when the code it's about is touched. Written with `node tools/learn.mjs` (see its header); hand edits are
fine if the line format stays. Public repo: no game titles, scores or personal data here.

## Mistakes

- **L1** · 2026-10-11 · v267 · `bugLayoutSoon`, `tools/lib.mjs` — A sweep that moves to the next screen 1.5s after switching misses the layout check (it runs 1.6s after a screen change, when idle): wait 2.6s per screen; a planted cut-off button went from 1 to 11 screens reported
- **L2** · 2026-10-11 · v267 · `.claude/skills/release-guard`, `claude plugin test` — release-guard's merge test had been failing unnoticed since a Claude Code update changed what test hooks on session.append must return (call next): after a Claude Code update, run claude plugin test on every mod in .claude/skills, not only the one being changed

## Missed opportunities

- [ ] **M2** · 2026-10-11 · v267 · `.github/scripts/webkitcheck.mjs`, `tools/lib.mjs` — webkitcheck.mjs and tools/lib.mjs each have their own seed and walk: make the WebKit check import tools/lib.mjs so both stay in step (it already runs on GitHub with a local Playwright)
- [ ] **M3** · 2026-10-11 · v267 · `tools/lib.mjs`, `worker/helper.js` — Sweeps block every outside host, so screens that need the helper (reviews, hours to beat, pictures, news) show their empty state: a fake helper in tools/lib.mjs open({ fake }) with real-shaped answers would let sweeps cover those cards too
- [ ] **M4** · 2026-10-11 · v267 · `tools/pn.mjs`, `.github/workflows` — Run node tools/pn.mjs sweep and tour on GitHub for every pull request (Chromium on the runner, like webkit-check.yml), so a layout or tour regression is caught before the merge even when a chat forgets to test
- [x] **M1** · 2026-10-11 · v267 · `tools/pn.mjs`, `tools/lib.mjs` — Test harnesses were rebuilt in the scratchpad for about 30 releases and lost with each chat: keep reusable checks in tools/ and one-offs in tools/scenarios/ (done: tools/ kit, this chat)

## Promoted to checks

- **L3** → `release-guard test: the committed test kit counts as a browser test` · 2026-10-11 · v267 · `TESTED`, `tools/pn.mjs`, `.claude/skills/release-guard` — A new way to run tests (tools/pn.mjs) would have been invisible to release-guard's test-before-done check, which matched only playwright/chromium in the command: when adding a new way to do something a guard watches for, update the guard's pattern in the same change and give it a test
