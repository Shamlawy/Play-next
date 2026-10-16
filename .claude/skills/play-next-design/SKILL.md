---
name: play-next-design
description: Play next's house style for any screen, sheet, card, button or other UI change in index.html, and for turning a reference design the owner shares into our look. Use before designing or restyling anything visible in the app ("make it look better", "copy this design", a new screen, a new card, a Settings group), and run its check before a UI change ships. Our rules win over the outside design and motion skills.
---

# Play next house style

Every visible change follows this page, so every chat designs screens the same way. The rules come from CLAUDE.md (Look, Speed rule, Hard-won rules) and docs/LOG.md; the version after each rule is where its story is (`grep -n "v188" docs/LOG.md`). If this page and CLAUDE.md ever disagree, CLAUDE.md wins: fix this page in the same change.

## 1. Before you design

- Grep docs/LOG.md for the screen's or feature's name: the owner has often said what they like or hate about it already.
- Two phones, three widths: the owner's Galaxy Z Fold 8 Ultra in Chrome (folded ~412, unfolded ~859, high refresh) and the friend's iPhone 11 in Safari (414, notch, home bar). The kit also checks 360. Between 620 and 1099 px Nexi's rail sits on one side and `.wrap` gets padding for it (v188).
- One smart action beats a row of buttons. The owner on a busy screen (v233): "too many buttons and stuff, make it better and smarter". Show the one action that fits the game's state and tuck the rest away.
- Prefer the version that works on its own (an automatic refresh, a reminder that just arrives) over one that needs a tap.

## 2. A reference design from the owner

Copy its layout and style, never its colours (v174, v259). Before building, write a short brief in the reply:

1. Layout: the blocks from top to bottom, what sits side by side, how the picture is cropped, where the one main action is.
2. Style: corner sizes, hairlines vs filled surfaces, type sizes and weights, spacing rhythm.
3. Our colours: which parts become `--u-pan` / `--u-card` / `--u-card2`, which become the accent (`var(--volt)`), what stays white (`var(--ice)`) or muted (`var(--mute)`).
4. What changes for us: anything that breaks a rule below (a press bounce, a light background, all-caps titles, a looping blur, moving lights) is left out, and the brief says so.
5. Both widths: how it sits folded (412) and unfolded (859).

## 3. Colour

- Black / near-black only. The page background is one still layer and nothing else: `html` = `--bgb` (`S.bgBase`, black by default) and `html.paper::before` = a soft `--bgt` glow (`S.bgTint`) at `S.bgGrad` % (v276/v277). Never add another background to html/body or their ::before/::after, and never bring back moving lights, drifting art or specks (v273). `kit.mjs syntax` fails on it.
- Surfaces: `var(--u-pan)` (panels, sheets), `var(--u-card)` (cards, rows), `var(--u-card2)` (a raised card on a card). Hairlines: `var(--u-line)`, stronger `var(--u-line2)`.
- Accent: `var(--volt)`, `var(--volt2)`, `rgba(var(--vrgb), a)`, `rgba(var(--v2rgb), a)`. Never a hex accent in CSS (v181): themes recolour through these. Text: `var(--ice)`, muted `var(--mute)`, danger `var(--hot)`. A game's own colour comes in as `--accent` on its element.
- Hex is fine only for scene or status colours that must not follow the theme (a sky, a green tick) and in JS palettes. New hex colours count against the audit budget (`hex` in `tools/audit-budget.txt`).
- White text must stay readable: anything that can sit behind text is dark enough for it (v277).

## 4. Shape and type

- New UI is "Clean" style (v188): every rule scoped to `html.ui2`. Reuse what is there before making new: `.btn` (`.ghost`, `.danger`), `.chip` (pressed = `aria-pressed="true"`, accent fill), `.row`, `.stat`, `.empty`, `details.grp` for Settings groups, the fields' focus ring.
- Corners: `var(--u-r)` (20px, cards and sheets) and `var(--u-rs)` (14px, small things). Pills for chips and search boxes.
- Surfaces: one dark surface + a hairline. No thick accent edge on rows (an open card may keep a small accent bar), no 6px accent top on sheets (they get a grab handle).
- Type: Poppins (`font-family: "Poppins", system-ui, sans-serif`), weights 500 / 600 / 700 only. Headings upright, sentence case, weight 700, letter-spacing about -.015em. Never italic or uppercase headings (v188); em/i and the celebration screens (wrapped, level-up) are the only italics. Screen titles 26px, 30px from 620px; Settings group heads 14.5px / 600; labels 12.5px / 600 in `var(--mute)`.
- No native date or time inputs (v180): use the app's own picker.
- Class names are unique: grep `\.name\b` before adding one (clashes: .aw v164, .dash v195, .pl v204, .plat v228). Give a new feature its own prefix (`dx*`, `rm*`, `gd-*`).
- Respect the notch and home bar on the iPhone: fixed things at the edges use `env(safe-area-inset-*)`.

## 5. Touch and motion

- No press animation (#309, v246): nothing shrinks, lifts or fades when touched. No `:active` rule moves anything.
- No touch effects (v280): nothing spreads, glows or appears under the finger. Every `:hover` look lives in `@media (hover: hover) and (pointer: fine)`, because phones keep hover on the last thing touched.
- Outside skills (Emil Kowalski's, apple-design, mobile-native) suggest a scale on press: skip that advice here. Their light/dark `theme-color` advice too: we're always black.
- Animate only `transform` and `opacity`. No looping blur, no filter / clip-path / mask on spinning layers (v167), no stacked effects, no `zIndex` in keyframes (v168). A `fill: "forwards"` animation is cancelled on every path that ends it (v215). Easing: `var(--ease)`.
- Endless loops are allowed only as transform/opacity, and they keep running while a finger moves (v282): never pause them on scroll.
- Big pictures and many layers cost the GPU on the Fold (v264, v287): keep layers few, pictures at the size they're shown.

## 6. Where the code goes

- New CSS goes in the last `<style>` block, new code in the last `<script>` (v198/v216/v237). Add to existing functions with `extend(...)` and screen changes with `onNav` / `onSettings`, never a new wrap.
- Never read layout right after an innerHTML redraw: read it in requestAnimationFrame or an observer (v206).
- Each screen's Customise shows only that screen's settings.
- A user-facing change gets an `APP_V` bump and a `WHATS_NEW` entry (`v`, `go`, `at`, `say`, `tryIt`); its first `say` is the update notification, so make it a good one-line pitch.

## 7. The design check (run before a UI change ships)

1. `node tools/design-check.mjs` reads what the branch adds to index.html (against `origin/main`; `--base=<ref>` for another) and lists every line that breaks a rule above: hex accents, transitions on anything but transform/opacity, press effects, `:hover` outside the hover media query, italic/uppercase, native date inputs, a font other than Poppins, a class name that already exists elsewhere, layout read after an innerHTML redraw. Exit 1 when something must change; notes are for you to judge.
2. Look at it: screenshots at 412 and 859 (`node tools/kit.mjs tour --shots dir` for the new tour step, or a scenario in `tools/scenarios/`). Read them back and compare with the brief from step 2.
3. Touch: `node tools/kit.mjs run tools/scenarios/touch-empty.mjs --widths 412,859` (nothing appears under the finger).
4. Speed: `node tools/kit.mjs perf` (with `--file old.html` for the before), then `node tools/kit.mjs all` as for every PR.
5. Say in the reply what the check found and what you did about it. Taste questions (a colour, spacing, which picture) go to the owner as a short choice, not a guess.
