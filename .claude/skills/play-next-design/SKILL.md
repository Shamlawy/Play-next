---
name: play-next-design
description: Play next's house style for any screen, sheet, card, button or other UI change in index.html, and for turning a reference design the owner shares into our look. Use before designing or restyling anything visible in the app ("make it look better", "copy this design", a new screen, a new card, a Settings group), and run its checks (design-check.mjs, the design eye `kit.mjs eye`) before a UI change ships. Also use it whenever the owner picks a look, calls something ugly or shares a reference: record it in the taste log (tools/taste.mjs) so the skill keeps learning. Our rules win over the outside design and motion skills.
---

# Play next house style

Every visible change follows this page, so every chat designs screens the same way. The rules come from CLAUDE.md (Look, Speed rule, Hard-won rules) and docs/LOG.md; the version after each rule is where its story is (`grep -n "v188" docs/LOG.md`). If this page and CLAUDE.md ever disagree, CLAUDE.md wins: fix this page in the same change.

## 1. Before you design

- Read what the owner already said about the screen: `node tools/taste.mjs list --screen=<Queue|Home|Played|…>` (taste.md, every pick and "I hate it" with the rule it means), then grep docs/LOG.md for the feature's name.
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

Then keep the pair: `node tools/taste.mjs pair <name> --before=<ours before> --after=<ours after> --ref=<link> --brief="<the 5 points>"` writes `references/<name>/` (pictures as phone-width JPEGs: `convert x.png -resize 412x -quality 78 x.jpg`). The owner's reference picture itself goes in only when they say so (the repo is public; a link is enough). `references/` is the library of what "copy the layout, not the colours" looked like before: read the nearest pair before a new brief.

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
- Queue row logos (v290, the owner picked look A of four demos): upright in their own slot inside the row, every logo the same visual size (lfRule's optical sizing), near-black ones drawn light, folded rows on two lines. Never let a logo hang off its row, tilt or run past the screen edge (`tools/scenarios/logo-look.mjs`, Nexi's `lfLook`).
- Settings rail (v296, look B of four demos): Settings and Customise are one scroll of open groups beside a sticky icon rail; the group under the title is lit (bar + tile, its name pops out a moment). Check `tools/scenarios/settings-rail.mjs`.
- No emoji as icons (v296, the owner hates "default ugly icons"): draw them in the v247 menu style, 24x24, fill-only `currentColor`, one part at 35% opacity (`gIcon(name)` / `GICON`; a new Settings group needs its own drawing).
- Short things stay whole: a date, "16h", a score chip or a word like "S-tier" never breaks over two lines (`white-space: nowrap`, or a non-breaking hyphen `\u2011`). The design eye flags it (v292: Played's top board chips).

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
2. The design eye: `node tools/kit.mjs eye` (every screen, 3 game pages and Settings at 412 / 859 / 360; in `kit.mjs all`, so GitHub runs it on every PR). One screen: `SCREENS=queue node tools/kit.mjs run tools/scenarios/design-eye.mjs --widths 412,859` (`SHOTS=dir` saves what it looked at).
3. Look at it yourself: screenshots at 412 and 859 (`node tools/kit.mjs tour --shots dir` for the new tour step, or a scenario in `tools/scenarios/`). Read them back and compare with the brief from step 2.
4. Touch: `node tools/kit.mjs run tools/scenarios/touch-empty.mjs --widths 412,859` (nothing appears under the finger).
5. Speed: `node tools/kit.mjs perf` (with `--file old.html` for the before), then `node tools/kit.mjs all` as for every PR.
6. Say in the reply what the check found and what you did about it. Taste questions (a colour, spacing, which picture) go to the owner as a short choice, not a guess: show the choices as demos in the real screen at 412 and 859 (T15).

## 8. The design eye (tools/eye.mjs)

It looks at a screen the way a person would. It finds the repeated things by itself (rows, cards, tiles, chips: 3+ visible siblings of one kind) and names:

| kind | what it means | how |
|---|---|---|
| edge | runs past the side of the screen (not in a side scroller) | boxes + pixels |
| overlap | sits on its neighbour | boxes |
| spill | a picture or words drawn outside its item, unclipped | boxes + pixels |
| words | a logo-like picture (see-through parts) sitting on the item's words | pixels |
| squeezed | words cut off in a box under 60px | boxes |
| broken | a date, "16h", a tag or one word broken over 2+ lines | the text's own line boxes |
| squashed | a picture stretched out of shape, or squashed to a sliver | boxes vs the picture's own size |
| uneven | pictures side by side 2.5x+ apart in size | boxes |
| faint | words under 2:1 against what's under them, a logo under 1.4:1 on screen | colours / pixels |

The pixel part is v290's logo checker made general: a screenshot with every item's picture hidden, then one per picture; the pixels that changed are where the picture REALLY landed, after rotation, filters and clipping. It waits for entrances and fades to finish and hides floating layers first (L53).

- Something meant to look that way (the tabs' fanned covers): a line in `tools/eye-ok.txt`, `<kind> <part of the path> — <why>`. A real problem gets fixed instead.
- The eye can't go blind unnoticed: `tools/plants/eye-broken.css` puts three old breakages back and app-check fails unless they are flagged.

## 9. The taste log (taste.md) and how this skill keeps learning

`taste.md` holds every look call the owner made, oldest first: their words, the rule, what checks it, a screenshot when there is one. `node tools/design-check.mjs` prints the calls about the screens a change touches.

Whenever the owner picks a look, calls something ugly, or says "I hate how it looks":
1. Record it the same turn: `node tools/taste.mjs add --v=<APP_V> --screen=<screen> --kind=pick|hate|rule --said="<their words, no game titles>" --rule="<what to do, in one sentence>" --check="<what holds it, or none>" --shot=<seed screenshot>`.
2. Can a measurement catch it? Teach the eye (a new kind in `boxEye` or the pixel pass), add the old look to `tools/plants/eye-broken.css`, prove it: the plant is flagged, `kit.mjs eye` is clean at 412 / 859 / 360. Then set the entry's `check:` to `kit.mjs eye (<kind>)`.
3. A look rule goes into this page and CLAUDE.md in the same change.
4. `node tools/taste.mjs gaps` lists the rules nothing checks yet: the next things to teach the eye when there's time (open cards of equal height, one main action on a game page, an effect too subtle to spot…).

Ways to grow it next (offer, don't start unasked): a "same height" kind for cards meant to match (T8); a "one main action" count on the game page (T10); an "effect visible" pixel diff for game-page colour effects (T18); before/after pairs for every look the owner picks from demos.
