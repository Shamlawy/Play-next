# Lessons

What Claude learned working on this repo: mistakes (and what to do instead), missed opportunities, and ideas. The learn mod
(`.claude/skills/learn`) asks for a lesson when a turn had a correction, a failed check, a guard stop or a release, and shows
each lesson again when the code it's about is touched. Written with `node tools/learn.mjs` (see its header); hand edits are
fine if the line format stays. Public repo: no game titles, scores or personal data here.

## Mistakes

- **L1** ×2 · 2026-10-11 · v267 · `bugLayoutSoon`, `tools/kit.mjs` — A sweep that moves to the next screen 1.5s after switching misses the layout check (it runs 1.6s after a screen change, when idle): wait 2.6s per screen; a planted cut-off button went from 1 to 11 screens reported
- **L2** · 2026-10-11 · v267 · `.claude/skills/release-guard`, `claude plugin test` — release-guard's merge test had been failing unnoticed since a Claude Code update changed what test hooks on session.append must return (call next): after a Claude Code update, run claude plugin test on every mod in .claude/skills, not only the one being changed
- **L4** · 2026-10-12 · v269 · `tools/kit.mjs`, `--file` — kit.mjs --file resolves the path against the repo root, so a scratchpad path fails with ENOENT: write the old copy as old.html in the repo root (git show origin/main:index.html > old.html), run, then rm it (never commit it)
- **L5** · 2026-10-12 · v269 · `tools/where.mjs`, `acorn` — A fresh container has no acorn, so tools/where.mjs throws 'acorn not found': run npm i -g acorn first
- **L6** · 2026-10-12 · v269 · `issue comments`, `bugPath` — Wrote an issue comment guessing what an element id was (#dxq called a queue tile; it was the search box, which explained the whole report): grep the id in index.html before describing or diagnosing a report

## Missed opportunities

- [ ] **M2** · 2026-10-11 · v267 · `.github/scripts/webkitcheck.mjs`, `tools/kit.mjs`, `tools/seed.mjs` — webkitcheck.mjs still has its own seed and walk: make it use tools/seed.mjs and kit.mjs's phone and walk (with WebKit) so the iPhone/iPad check and the Chromium sweep stay in step
- [ ] **M3** · 2026-10-11 · v267 · `tools/kit.mjs`, `worker/helper.js` — Sweeps block every outside host, so screens that need the helper (reviews, hours to beat, pictures, news) show their empty state: a fake helper in kit.mjs's phone() with real-shaped answers would let sweeps cover those cards too
- [ ] **M5** · 2026-10-11 · v269 · `tools/kit.mjs`, `--plant` — A sweep can't tell whether a detector still works: keep a small set of plants in tools/plants/ (one per detector: cut-off button, thrown error, broken tour step, blurry picture) and have app-check.yml run sweep --plant --expect for each, so a detector that silently stops working fails the PR
- [x] **M4** · 2026-10-11 · v267 · `tools/kit.mjs`, `.github/workflows` — Run the sweep and tour on GitHub for every pull request (Chromium on the runner, like webkit-check.yml), so a layout or tour regression is caught before the merge even when a chat forgets to test (done: app-check.yml runs kit.mjs all on every PR, v269)
- [x] **M1** · 2026-10-11 · v267 · `tools/kit.mjs` — Test harnesses were rebuilt in the scratchpad for about 30 releases and lost with each chat: keep reusable checks in tools/ and one-offs in tools/scenarios/ (done: tools/kit.mjs, v269 + kit.mjs run)

## Promoted to checks

- **L3** → `release-guard test: the committed test kit counts as a browser test` · 2026-10-11 · v267 · `TESTED`, `tools/kit.mjs`, `.claude/skills/release-guard` — A new way to run tests (the tools/ kit) would have been invisible to release-guard's test-before-done check, which matched only playwright/chromium in the command: when adding a new way to do something a guard watches for, update the guard's pattern in the same change and give it a test
