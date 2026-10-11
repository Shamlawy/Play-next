# tools/

Shared checks for Play next, used by every chat and by GitHub. Run the commands from the repo root. Each one exits 1 when something fails.

| command | what it does | time |
|---|---|---|
| `node tools/kit.mjs syntax` | Checks that every `<script>` block in index.html (and sw.js) compiles, and that no `:active` rule has a press animation | 1 s |
| `node tools/kit.mjs sw` | Checks that sw.js shrinks pictures to the right sizes (`.github/scripts/swcheck.mjs`, run in Chromium) | 5 s |
| `node tools/kit.mjs sweep` | Walks every screen, 3 game pages, Settings, Edit and chat at 412 / 859 / 360 in Chromium, with the app's own problem checks listening. Fails on a script error or a real report. Timing notes are printed only, because headless has no GPU | 3 min |
| `node tools/kit.mjs tour` | Runs the what's-new tour from the previous version to `APP_V` at 412 and 859, and checks that every step opens and finds its target | 40 s |
| `node tools/kit.mjs perf` | Measures main-thread ms per step (script / layout / style) with a 4x slower CPU. Use it to compare before and after a change | 30 s |
| `node tools/kit.mjs all` | Runs syntax + sw + sweep + tour. GitHub runs this on every PR (`.github/workflows/app-check.yml`) | 4 min |
| `node tools/map.mjs` | Rewrites `docs/MAP.md`: every block, every wrapped function with its layers, and the onNav hooks | 1 s |
| `node tools/map.mjs --dead` | Lists functions, consts, CSS classes and ids that look unused. These are candidates only: some names are built from strings | 1 s |

Options for kit.mjs:
- `--file old.html` tests another copy, for example `git show origin/main:index.html > old.html` to get the "before". Delete the copy afterwards and never commit it.
- `--widths 412,900` sets the screen widths.
- `--cpu 4` sets how much `perf` slows the CPU.
- `--set '{"bugOff":true}'` adds saved settings to every test phone.
- `--shots dir` makes `tour` save a screenshot of each step.

The test phones use made-up games from `tools/seed.mjs`. Their pictures are files already in the repo, and every other site is blocked. Problem reports (`/bug`) are caught by the kit and never sent.

Friend's devices (iPhone 11 / iPad Safari): `.github/workflows/webkit-check.yml` runs `.github/scripts/webkitcheck.mjs` and `swcheck.mjs` in WebKit on GitHub, because this container only has Chromium.
