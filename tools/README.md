# Test kit

Every release used to rebuild these harnesses in the scratchpad. The scratchpad is lost with each chat (docs/LOG.md names dozens of rebuilt `h.cjs` / `sweep.cjs` / `tour.cjs` copies). They live here now. Chromium comes from `/opt/pw-browsers`; never run `playwright install`.

| Command | What it checks | Fails when |
| --- | --- | --- |
| `node tools/pn.mjs sweep` | Walks every screen, a game page (top/middle/bottom), Settings, Edit and chat at 412/859/360, one width at a time with no screenshots. Prints the problem reports the app files about itself, caught at its `/bug` call. | Any script error or problem report |
| `node tools/pn.mjs sweep --plant=x.js --expect` | Proves a new check catches a planted copy of the problem. | The plant is **not** reported |
| `node tools/pn.mjs tour` | Runs the update tour from the previous APP_V at 412/900 with real taps on Next. Every step must light its target, keep the card and Nexi on screen, and file no `tour` report. Every entry needs `go` and `tryIt`. | Any step fails |
| `node tools/pn.mjs tour --all` | Replays every WHATS_NEW entry ("Show me what's new"). | Same |
| `node tools/pn.mjs bench --ab=origin/main` | Times screen changes at 4x CPU: main-thread ms, worst frame, frames over 33ms. Old build vs this one, median of `--runs`. | Never: read the table |
| `node tools/pn.mjs shots --w=412` | Screenshots of every screen, written to `tools/.out/`. | Never |
| `node tools/pn.mjs run tools/scenarios/x.mjs` | A one-off scenario built from `tools/lib.mjs`. | The scenario returns `false` |

Shared options: `--w=412,859` picks the widths. `--big` uses 3840×1240 heroes.

`tools/lib.mjs` holds the reusable pieces:
- `open()`: seeded storage written once per tab, every outside host blocked, `/bug` caught. `fake: { "/reviews": req => ({...}) }` mocks a helper route.
- `seed()`: games in every state, five scored Played games, and one game with scores on its back.
- `pictures()`: canvas-drawn covers, heroes, key art, character art and logos, cached in `tools/.cache/`.
- Touch input through CDP: `tap`, `tapEl`, `swipe`, `scroll`.
- `walk()` and `flush()`.
- Timing: `frames()` + `measure()`.

When a one-off check could be useful again, save it in `tools/scenarios/` instead of the scratchpad.

Things that took a release to learn (from docs/LOG.md):
- Screenshots force a full repaint that the lag check reports as a stutter, so sweeps take none.
- Parallel runs starve the CPU, so run one at a time.
- Problem checks are off while the tour runs, so sweeps seed `lastV` = APP_V.
- The layout check runs 1.6s after a screen change. A walk that moves on sooner misses most of it: with a 1.5s wait, a planted cut-off button was reported on 1 screen, and with 2.6s on 11.
- Headless Chromium has no GPU. Treat "slow" reports and frame times as hints: compare A/B, never against a number. For a real-phone check use the in-app smoothness self-test.
- Friend reports (iPhone/iPad Safari) need WebKit, which this container lacks. Use `webkit-check.yml`.
