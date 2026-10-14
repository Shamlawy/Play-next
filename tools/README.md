# tools/

Shared checks for Play next, used by every chat and by GitHub. Run the commands from the repo root. Each one exits 1 when something fails.

| command | what it does | time |
|---|---|---|
| `node tools/kit.mjs syntax` | Checks that every `<script>` block in index.html (and sw.js) compiles, and that no `:active` rule has a press animation | 1 s |
| `node tools/kit.mjs sw` | Checks that sw.js shrinks pictures to the right sizes (`.github/scripts/swcheck.mjs`, run in Chromium) | 5 s |
| `node tools/kit.mjs sweep` | Walks every screen, 3 game pages, Settings, Edit and chat at 412 / 859 / 360 in Chromium, with the app's own problem checks listening. Fails on a script error or a real report. Timing notes are printed only, because headless has no GPU | 3 min |
| `node tools/kit.mjs tour` | Runs the what's-new tour from the previous version to `APP_V` at 412 and 859, and checks that every step opens and finds its target | 40 s |
| `node tools/kit.mjs perf` | Measures main-thread ms per step (script / layout / style) with a 4x slower CPU. Use it to compare before and after a change | 30 s |
| `node tools/kit.mjs smooth` | Mid-swipe on Queue, Library and a game page (412 / 859), no endless animation may still be running, and a vertical scroll must leave them running again. Every scroll frame is a main-thread frame that restyles each running loop, so the app holds them still while a finger moves (v280) | 40 s |
| `node tools/kit.mjs audit` | Code health (`tools/audit.mjs --check`): fails when anything piled up past `tools/audit-budget.txt` | 2 s |
| `node tools/kit.mjs all` | Runs syntax + audit + sw + sweep + tour + smooth. GitHub runs this on every PR (`.github/workflows/app-check.yml`) | 4 min |
| `node tools/kit.mjs sweep --plant x.js --expect` | Runs `x.js` in every test phone, then sweeps. Fails unless the app reports it: the proof that a new detector catches its problem | 1 min per width |
| `node tools/kit.mjs run tools/scenarios/x.mjs` | Runs a saved scenario: `export default async ({ p, w, fail }) => …` with `p.step`, `p.scroll`, `p.flush` and real touch (`p.tap`, `p.swipe` through CDP). Returning `false` fails it | — |
| `node tools/map.mjs` | Rewrites `docs/MAP.md`: every block, every wrapped function with its layers, and the onNav hooks | 1 s |
| `node tools/where.mjs <name>` | Where a function is defined and every later script that wraps it (acorn parse, scope-aware). `--at=<line>` says which function and script a line is in | 1 s |
| `node tools/where.mjs --check --base=HEAD` | New hazards from the hard-won rules: state on a wrapped function (v198), assigning a top-level const (v216), calling a function hidden in another script's scope (v237), wrapping goTab/goSub/openFull/openSettings instead of `onNav` / `onSettings` (v269) | 2 s |
| `node tools/learn.mjs list` | Claude's lessons (`docs/LESSONS.md`): mistakes, open ideas, lessons turned into checks. `add` / `done` / `promote` write it (see its header) | — |
| `node tools/audit.mjs` | The code health report: wrap layers, functions wrapped 4+ times, dead code and CSS, CSS that never applies, selectors spread over 4+ rules, `!important`, hex colours, timers, endless animations, whole-subtree observers, Settings keys nothing reads. Each has a budget in `tools/audit-budget.txt` that may only go down (`--lower` after a cleanup; raising one is a hand edit with a reason in the commit). `--list <measure>` shows every offender | 2 s |
| `node tools/lint.mjs` | The bug finder: ESLint's correctness rules (undefined names, unused values, duplicate keys, unreachable code, a function declared twice…) over every script block, sw.js and worker/helper.js, with every script's top-level names known. The audit counts its findings (`lint`, budget 0). Needs `npm i --no-save eslint@9.36.0 globals@16.4.0 acorn@8.15.0` | 3 s |
| `node tools/audit.mjs --fix-css` | Removes CSS declarations that can never apply (the same selector sets the same property later in the same @media). Then prove it: `node tools/kit.mjs samecss --before old.html` (the browser applies exactly the same CSS) and `node tools/audit.mjs --verify old.html` | 2 s |
| `node tools/kit.mjs run x.mjs --browser webkit` | A scenario in Safari's engine. Not in this container (Chromium only): on GitHub, run `webkit-check.yml` with its `scenario` input (e.g. `tools/scenarios/rail-nexi.mjs`, widths `414,1080`: iPhone 11, iPad sideways) | — |
| `node tools/steam-sessions.mjs` | The helper's Steam session code (`/steam/me`, `/steam/sync`, the cron) against a fake Steam profile page and push service, in Node: 17 checks | 1 s |
| `node tools/map.mjs --dead` | Lists functions, consts, CSS classes and ids that look unused. These are candidates only: some names are built from strings | 1 s |

Options for kit.mjs:
- `--file old.html` tests another copy, for example `git show origin/main:index.html > old.html` to get the "before". Delete the copy afterwards and never commit it.
- `--widths 412,900` sets the screen widths.
- `--cpu 4` sets how much `perf` slows the CPU.
- `--set '{"bugOff":true}'` adds saved settings to every test phone.
- `CHROME=<path>` (environment) runs another Chrome, e.g. a newer Chrome for Testing for features the bundled Chromium lacks (v280's scroll triggers need 145+).
- `--shots dir` makes `tour` save a screenshot of each step.

Saved scenarios live in `tools/scenarios/` (the scratchpad is lost with the chat). The sweep waits 2.6s on each screen because the app's layout check runs 1.6s after a screen change, when idle: with 1.4s, a planted cut-off button was reported on 3 screens instead of 10. The test browser has swipe-back turned off, because a right swipe on a game page otherwise "goes back" to about:blank.

The test phones use made-up games from `tools/seed.mjs`. Their pictures are files already in the repo, and every other site is blocked. Problem reports (`/bug`) are caught by the kit and never sent.

Friend's devices (iPhone 11 / iPad Safari): `.github/workflows/webkit-check.yml` runs `.github/scripts/webkitcheck.mjs` and `swcheck.mjs` in WebKit on GitHub, because this container only has Chromium.
