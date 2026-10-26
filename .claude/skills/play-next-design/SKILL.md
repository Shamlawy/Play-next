---
name: play-next-design
description: Play next's house style for any screen, sheet, card, button or other UI change in index.html or in the native Android app's Jetpack Compose screens (android/, Kotlin), and for turning a reference design the owner shares into our look. Use before designing or restyling anything visible in either app ("make it look better", "copy this design", a new screen, a new card, a Settings group, a new composable), and run its checks before a UI change ships (web: design-check.mjs and the design eye `kit.mjs eye`; Android: the Gradle lint and screenshot tests). Also use it whenever the owner picks a look, calls something ugly or shares a reference: record it in the taste log (tools/taste.mjs) so the skill keeps learning. Our rules win over the outside design and motion skills.
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
- Nexi's menu (v296, the owner: "remove the emoji, and the commands shorten them and organise them better"): double tap / hold opens one small card beside him (`pnBubWheel`, `#bubwheel .nxm`) with a few named groups (Play, Nexi, More) of short plain-word buttons. Never bring back rings of emoji buttons or his face buttons. Unfolded, he sits in the rail on every screen including Home (a tap on him hides the rail); Home's own face steps aside so there's one Nexi; seated he keeps only his blink at rest. `node tools/kit.mjs run tools/scenarios/nexi-wheel.mjs --widths 412,859` checks it.
- Settings rail (v297, look B of four demos): Settings and Customise are one scroll of open groups beside a sticky icon rail; the group under the title is lit (bar + tile, its name pops out a moment). Check `tools/scenarios/settings-rail.mjs`.
- No emoji as icons (v297, the owner hates "default ugly icons"): draw them in the v247 menu style, 24x24, fill-only `currentColor`, one part at 35% opacity (`gIcon(name)` / `GICON`; a new Settings group needs its own drawing).
- Open Queue card (v299, the owner approved round 9 "Sweep": "Perfect", after hating v298's fixed blue slab, its round stamp that read as a score, the tilted cover and the logo moving about): the picture fills the whole card, rounded corners everywhere; the box art sits top-left, straight, on a skewed strap in the game's own colour (`g.colour`, darkened so white words read) holding a status dot + queue spot, one plain headline (Ready to play / Out in N days / N h left / Played twice), platform logos (a version like 4·Vita sits in its logo's own box, raised; a long list wraps inside the strap, v306) and genres; a ticket bottom-left holds the key numbers (a released game's stub has torn off at the perforation, a coming game's stays whole); the logo in a fixed bottom-right slot. No buttons on the front: the box art opens the game page, one tap closes the card into its row, two quick taps turn it (WAAPI, always the same way, straight over in 420ms: v306, the owner: "slow animation and shift", so never the slow swing past and back again); Edit, ⋯ and the rest live on the back. Its sounds go through `sfx()` (Settings › Sounds); the old hype/rating ticks (`revealSound`, Settings › Opening a card) are gone (v306), never play them on open again. Never bring back the stamp, a tilted cover or a fixed-colour slab. The back (v300, round 13 "Controller", the owner: "A it is"): your score fills a controller in the game's colour and sits on its little screen, each of the 8 areas fills one of its round buttons with the number inside (Nexi's guess, dashed, until you've scored it), the four numbers under it, drawn icons for move / Edit / ⋯; never radar charts or potions ("a game app"). `node tools/kit.mjs run tools/scenarios/queue-card.mjs --widths 412,859`.
- Closed Queue rows + the bar (v301, the owner picked round 2 A "Strap" and the new bar, with F "Character select" as a second option: "A and the new bar, make it and F too"): each closed row wears the open card's language: the spot and box art (straight) on a slanted strap in the game's colour with a lighter stripe, the game's picture behind, a kicker (Up next / Playing / Coming…), the name (two lines at most), the open card's own headline (`qkStat`), its ticket (a released game's stub hangs torn; hidden folded, where the date sits under the name), a progress line along the foot (played full, playing how far, coming how close the release is, dashed) and the logo in its v290 slot. The slant lives on the strap's `::before`/`::after` (html.flat's `.slab > * { transform: none !important }`). Under the header one bar (`qdDeck`): All / Out / Coming / Played tabs with counts (+ lists, + a lists button), one Order button whose menu groups the orders (Order / Your scores / One area; sorted → "Make this my order"), Shuffle (a tap picks a game that's out and lights its row, a hold opens the shuffler) and Boss as the Shadow's live HP (`window.bossHP`). Never bring back the five loose chips or a filter menu. Settings › Queue cards › Closed games: Rows or Character select (`S.qLook`): the picked game's panel (logo, colour, three segmented bars) or its open card over a grid of covers with a P1 marker. `node tools/kit.mjs run tools/scenarios/queue-rows.mjs --widths 412,859`.
- On deck + genre tabs (v303, the owner: "on deck some games have a tall image and some wide so fix"; a genre picked on a game page said "undefined" in the bar): On deck's three cards share one shape (picture on top, same size and place; #1 differs only by "Play this next"); never a full-card picture next to picture-on-top ones. On deck follows the Queue's filter ("On deck · Action", gone under Played). Every tab in the bar names itself (`qdTabName`), drawn icons only. Nexi reports a label saying undefined, a button running over its neighbour and a short button word cut with "…". `node tools/kit.mjs run tools/scenarios/genre-filter.mjs --widths 412,859,360`.
- Insights top (v316, the owner picked "Shattered Sky" from four rounds of demos, T51): the top three ranked games as slanted shards in one picture with the player type on an accent slab, the type's line + hype-gap stamp under it, the tarot table as "Your arcana", then the S-tier shelf (slanted box art, big rank numbers), Your sky (genre diamonds sized by S-tier games, untried genres dashed, one button for the queued game that lights one) and the backlog's finish months. Poppins and sentence case even here (the demos' condensed caps were left out). Classic in Customise › Top of Insights. `node tools/kit.mjs run tools/scenarios/insights-shattered.mjs --widths 412,859,360`.
- Short things stay whole: a date, "16h", a score chip or a word like "S-tier" never breaks over two lines (`white-space: nowrap`, or a non-breaking hyphen `\u2011`). The design eye flags it (v292: Played's top board chips).

## 5. Touch and motion

- No press animation (#309, v246): nothing shrinks, lifts or fades when touched. No `:active` rule moves anything.
- No touch effects (v280): nothing spreads, glows or appears under the finger. Every `:hover` look lives in `@media (hover: hover) and (pointer: fine)`, because phones keep hover on the last thing touched.
- Outside skills (Emil Kowalski's, apple-design, mobile-native) suggest a scale on press: skip that advice here. Their light/dark `theme-color` advice too: we're always black.
- Animate only `transform` and `opacity`. No looping blur, no filter / clip-path / mask on spinning layers (v167), no stacked effects, no `zIndex` in keyframes (v168). A `fill: "forwards"` animation is cancelled on every path that ends it (v215). Easing: `var(--ease)`.
- Endless loops are allowed only as transform/opacity, and they keep running while a finger moves (v282): never pause them on scroll.
- Big pictures and many layers cost the GPU on the Fold (v264, v287): keep layers few, pictures at the size they're shown.
- No scroll-linked motion on list rows (v308, #469 item 6): a `view()`/`scroll()` timeline keeps its own GPU layer the whole time it exists, moving or not (Played's rows had four each: 9 screens of layers, now 4). Rows in a long list simply appear; one small piece (a badge shelf) may keep it, near the screen only.

- The book's pages (v321, T53): words never float over a game's picture; they sit on a slanted strap in the game's own colour (the closed Queue row's strap, laid along the foot of the page, a lighter stripe on its edge) with the box art standing straight over the edge and a ticket for the numbers. Lists of games (the contents) are small closed Queue rows.
- Paper that bends (v318, the skills book): a page that turns is a chain of strips whose small `rotateY` angles add up, and each face is shaded by opacity ramps between its edge angles (one flat value per strip shows as stripes). Strips exist only while the page moves (4 by hand, 3 in a riffle, a stiff cover is 1); at rest the pages are flat, layer-free copies. Nexi's checks wait while it moves (`skbBusy`).

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

## 10. Android (Compose)

The native app in `android/` (Kotlin + Jetpack Compose, the owner's Fold only) wears the same look: sections 1-5 and the taste log apply as written. What changes is how the rules are kept in Compose (docs/ANDROID-PLAN.md gate 6 makes each one a named lint check where code can check it):

- Colours only from the token list generated from index.html (the `:root` / `html.ui2` tokens, `PALETTE`, `BG_BASES`), never a `Color(0x…)` literal in a screen. No Material You dynamic colour (`dynamicDarkColorScheme` / `dynamicLightColorScheme`): we're always black.
- No ripple and no touch effect: the root `LocalIndication` is none, so nothing draws under the finger; never pass a ripple or Material indication to `clickable` / `combinedClickable`. No press scale, lift or fade (#309): nothing reacts to a press but the action itself.
- Animate only `graphicsLayer` translation, scale, alpha and rotation, and read the animated value in the draw/layer phase (the lambda `Modifier.graphicsLayer { … }` / `Modifier.offset { … }` forms), never in composition or layout. No animated size, padding, colour of a large surface, blur or shader.
- One still background layer (the base colour + the soft gradient, section 3), nothing moving behind the screens. No overscroll stretch or glow (`LocalOverscrollFactory` provides null).
- Icons are drawn (`GICON`, 24x24, one part at 35%), never emoji, and every icon that carries meaning has a TalkBack `contentDescription` (null only when a text label beside it already says it).
- Touch targets are at least 48dp, even when the drawn thing is smaller.
- Font scale is capped at 1.3 for fixed-size labels (tab labels, header stats, straps, tickets); body text follows the phone's setting. Short things stay whole (`softWrap = false, maxLines = 1`, section 4).
- Headlines are balanced: `TextStyle(lineBreak = LineBreak.Heading)`.
