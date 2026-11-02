Mapped at web APP_V 327 (index.html line numbers are for that version; `docs/LOG.md` entries by version).

# Phase 1 · piece 1 (and piece 3's list gestures) · the Queue's LIST

Scope: closed rows (v301 Strap + v290 logo slot), the bar `qdDeck`, On deck (v303), Character select (`S.qLook`), the Dusty mark, the gestures on rows, ordering and which games show where, empty states and edge cases. The open card (`qkFront`/`qkBack`, 8620-8740), the Game Shuffler deck (`openShuffle`, 15915), the Boss overlay (`bossScreen`, 13925) and the lists sheet (`openLists`, 12872) are other helpers' / later phases; only where they start is noted.

Not ported (owner, Nov 2, `1-brief.md`): Move up / Move down arrows, Score it / Duel it in the card's ⋯. Phase 1 writes nothing to the backup (Android is read-only until switch day): everything below that says "writes S.x / g.ord" is a local, in-memory or app-local copy on Android ("practice copy: resets when the app reopens" for delete; assumed the same for reorder).

Widths: "folded" = `max-width: 500px` (412 dp phone), "unfolded" = 501-1099 (859 with Nexi's rail leaves ~740 for the panel). Style = Clean (`html.ui2`, Poppins, `--u-*` tokens) only; Arcade is not ported (Phase 0 answer 2).

## 0. Where everything is (index.html, APP_V 327)

| What | Lines | Notes |
|---|---|---|
| `renderQueue` (whole screen: header, bar, On deck, rows, handlers) | 8855-9054 | extended by hooks at 11585 (glide + `qKeep`), 21088 (`dustApply`, `lfApply`), 22130 (size watch) |
| `row(g, n)` (a closed row) | 8923-8941 | |
| `big(g, n)` (the open card in the list) | 8892-8898 | maps in piece 2 |
| `qkStat` (headline/ticket/word per game) | 8605-8620 | shared by row, card and Character select |
| `qsPic`, `qsProg` | 8758-8768 | |
| `qdDeck` (bar), `QD_IC`, `QD_TAB`, `qdTabName`, `QD_GROUPS` | 8769-8800 | |
| `qdShufArm`, `qdShuffle` | 8802-8823 | hold -> `openShuffle()` (15915) |
| `qfPanel`, `qfGrid`, `qfTap` (Character select) | 8824-8854 | |
| `qPatch` (swap only changed rows) | 8742-8756 | web speed trick; Compose does this by keys |
| CSS: closed rows, bar, Character select | 27366-27546 | read these, they are short |
| `isOutNow`, `qNow`, `qWaiting`, `qCounts`, `queueList`, `sortGames`, `SORTS`, `QFILTERS`, `QICON` | 7323-7402 | `qCounts`/`queueList` are extended for `S:` (13566) and `G:` (22030) tabs |
| `startDrag`, `shiftRows`, `overDrag`, `endDrag`, `move` | 7410-7495 | long-press reorder |
| swipe-to-delete (v171) | 15070-15142 | its own IIFE + CSS 15071-15080 |
| On deck: `onDeckScores` 13260, `onDeck` 13281, `deckWhy` 13286, `deckShown` 13299, `deckHead` 13304, `onDeckStrip` 13308, photo cards 24611, CSS 24517-24533, `dzMeta` 24586 | | |
| Logo fit: `lfMeasure` 20961, `lfRule` 20985, `lfSheet` 21003, `lfApply` 21013, `lfRO` 21040; v290 checks `lfLook` 26762 | | |
| Dust: `dustDays` 21061, `dustLvl` 21071, `dustApply` 21076 | | |
| Boss HP: `bossNow` 13912, `window.bossHP` 13916 | | |
| Lists: `listNew` 12862, `openLists` 12872, `gtagFilter` 22021, `simFilter` 13560 | | |

## 1. State the list reads and writes

| Key | Where | Default | Read by | Written by |
|---|---|---|---|---|
| `S.qFilter` | S (backup) | `"all"`; valid: `all`, `out`, `soon`, `done`, `L:<listId>`, `G:<genre>`, `S:<gameId>`; anything unknown reads as `all` (`qNow`, 7351) | tabs, `queueList`, On deck | tab tap, genre chip on a game page (`gtagFilter`), "Like X" (`simFilter`), Shuffle when the pick is hidden (sets `all`), list deleted |
| `S.sort` | S | `"manual"` (absent) | `sortGames`, bar, `qdDeck(manual)` | Order menu, drag-drop (forces `manual`), "Make this my order", "Order my list by hype" |
| `g.ord` | game | missing = 0 | manual order | drag-drop, the two menu actions |
| `S.showPlayed` | S | derived: `qNow() === "all"` is written at the top of every `renderQueue` (side effect; `liveGames()` uses it) | | |
| `S.lists` `[{id, n, e}]`, `g.lists` `[id]` | S / game | none | list tabs + counts | lists sheet |
| `S.qLook` | S | absent = Rows; `"select"` = Character select | `renderQueue`, `qdShuffle` | Settings › Queue cards › Closed games (`#s-qlook`) |
| `S.deckOff` | S | on | `onDeckStrip` | Settings › On deck |
| `S.dzX.deck` | S | absent = new photo cards; truthy = "Classic" `.deck-g` strip | | Settings › On deck cards |
| `S.curPlat` | S | `"Switch"` (`any` allowed) | On deck score (`onPlat`) | Settings › On deck |
| `S.cardBg` | S | `"hero"`; `"plain"` = no picture on rows/cards | `qsPic` | Settings |
| `g.bgOff`, `g.bgX`, `g.bgY` | game | `bgX` 100, `bgY` 50 (0-100) | row picture position | Edit |
| `S.rowArt` | S | on; `=== false` hides every logo | `row()` | Settings |
| `S.rowArtW` (-> `--raw`), `S.rowArtOp` (-> `--rao`) | S | 150 px, 95 % | logo slot width cap `raw x 1.4`; slot opacity | Settings (sliders `#s-raw`, `#s-rao`) |
| `S.dustOff`, `g.dustOff` (ms timestamp = "blown off") | S / game | on | `dustDays` | Settings, game page |
| `S.grum` | S | off | Boss shows HP only when on | Settings |
| `S.fxBossMax` | S | 0 | Boss bar max | `bossMax` on every save |
| `S.motion === false`, `calm()` | S | | no entrance/glide/swap animations | |
| `S.rowAnim` | S | off | a 300 ms `rowin` slide-in per row (`animation-delay: min(n,6) x 28 ms`) | not worth porting: off by default |

Game fields used: `id title colour(#RRGGBB; falls back to PALETTE[0]) art(logo URL) img(box art; else drawn cover) hero steamId bg bgOff bgX bgY imgX imgY state(upcoming|backlog|playing|played|dropped; missing = upcoming) date(YYYY-MM-DD) hours hoursX hours100 logged hype ord comp lists added dustOff`. `S.played` records `{id, runs, real, z, dk, t}`; `playedRec(g)`; `S.duels`.

Web persists filter/sort in `S` (so the cloud backup changes). Android must keep `qFilter`/`sort`/`qLook` as app-local bookkeeping (Store prefs, "a separate local store for bookkeeping") seeded from the backup's values, never written back.

## 2. Which games show where (rules, exactly)

Helpers: `isPlayed(g)` = `S.played` has a record with `g.id` (3501). `isDropped` = `g.state === "dropped"`. `qWaiting()` = not played and not dropped. `isOutNow(g)` (7343): `state` (default `upcoming`) is `backlog` or `playing`, OR `g.date` set and `new Date(g.date) < new Date(new Date().toDateString())` = date (UTC midnight) before today's LOCAL midnight. In UAE (UTC+4) a game dated today is not "out" until tomorrow; Android has `JsDate.utcMidnight` and `JsDate.today0` for exactly this pair (Rank.kt 261). A date JS can't parse is never out.

| Tab (`data-qfilter`) | Name (`qdTabName`) | Games (`queueList`, 7362) | Count (`qCounts`, 7353) |
|---|---|---|---|
| `all` | All | every game, played and dropped included | `games.length` |
| `out` | Out | waiting AND `isOutNow` (includes playing) | same filter |
| `soon` | Coming | waiting AND NOT `isOutNow` | same |
| `done` | Played | `isPlayed` | `S.played`-backed games |
| `L:<id>` | list name | every game (any state) whose `g.lists` has the id | same |
| `G:<genre>` | the genre | every game whose genre set has the key (`gdGenreSet`/`gdKey`, game-page code) | same |
| `S:<gameId>` | "Like <title>" | `simLocal(g, 99)` games (up to 99) | same |

- Dropped games appear only under All, a list, `G:` or `S:`. Comp (competitive) games appear in all tabs like any other (only `hrsOf` returns 0 for them and On deck/Shadow leave them out).
- `QFILTERS` order = All, Out, Coming, Played, then the dynamic `S:`/`G:` entry (pushed when picked from a game page; only one of each kind exists; restored on reload for the current `S.qFilter`), then one tab per list, then the lists button (`data-lists`). Tab labels in the data: "Everything", "Out, not played", "Upcoming", "Played" are the Settings/menu names; the bar uses `QD_TAB`: **All / Out / Coming / Played** (8777).
- The `G:`/`S:` tabs come from the game page (Phase 2): the Queue bar just has to show one extra tab when `qFilter` is one of them (icon `QD_TAGIC`, 22018: a drawn tag; `S:` uses the emoji 🔍, which breaks the v297 "no emoji as icons" rule: draw one).
- The "spot" number on a row = its index in the SORTED and FILTERED list + 1, `padStart(2, "0")` ("01"... "100"). It is not stored.
- The kicker "Up next" = row 1 of whatever is shown, unless that game is playing or played (then its word). Only unfolded; folded hides the kicker.
- Header text `.tcount`: `<waiting> waiting · <S.played.length> played` (waiting = not played, not dropped; the second number counts RECORDS, so a replayed game counts twice). Empty string when there are no games.

### Orders (`sortGames`, 7375; `SORTS` 7323; `QD_GROUPS` 8781)

Menu groups, in order (a key not in `SORTS` is skipped, `manual` always listed):

| Group | Keys and names |
|---|---|
| Order | `manual` "My order", `date` "Soonest", `hype` "Most hyped", `ondeck` "On deck (my pick)", `shortest` "Shortest", `longest` "Longest" |
| Your scores | `overall` "My rating", `expect` "What I'd expect", `duel` "Duel order" |
| One area | `story` "Story", `music` "Music", `gameplay` "Gameplay", `replay` "Replayability", `visuals` "Visuals", `world` "World", `chars` "Characters", `pace` "Pacing" |

- `manual`: ascending `(g.ord || 0)`; ties keep the array order (stable).
- Number sorts are descending by f(g), ties by ascending `ord`: `hype` -> `g.hype||0`; `expect` -> `expected(g)` else `(hype||0) - 0.01` (needs `hypeBias`/`genrePattern`, 8382: not in Android yet); `duel` -> Elo (default 1500; Android `Rank.elo`); `ondeck` -> `onDeckScores()[id]` else -1; `overall` -> mean of the positive RATINGS (-1 none); an area key -> `g.mine[key]` if > 0 else -1; `longest` -> `hrsOf||0`; `shortest` -> `hrsOf ? -hrsOf : -99999` (no hours last).
- `date`: tiers: future-or-today dated ascending (0), undated (1, keep order), past dated descending (2).
- Sorted lists never show the Move arrows and drag is off (long press opens the game page instead).
- With a score/hype sort, each row carries a small pill `sortTag` (`.sortv`) = the sort's name + the value: areas/overall/hype/expect rounded to 1 decimal (`Math.round(v*10)/10`), "—" if none; `duel` shows Elo rounded; date/hours/ondeck/manual: no pill. Folded: only the value (the name is hidden under 560 px).
- "Make this my order" (`#qkeep`, shown only when NOT manual): `sortGames(liveGames())` then `ord = k + 1` for each, `S.sort = manual`, toast "Your order is now <sort name lowercased>" (e.g. "Your order is now most hyped"), `beep("tap")`. "Order my list by hype" (`#byhype`, shown only when manual): live games re-sorted by `hype||0` descending (stable on current order) -> `ord = k+1`, toast "Reordered by hype". `liveGames()` = all games when the tab is All (via `S.showPlayed`), else only not-played-not-dropped. Both number only the games they cover, so games outside the current tab keep old `ord` values and can tie (see §9 gotchas).

## 3. A closed row (`row()`, 8923; CSS 27368-27440)

HTML, in layers (bottom to top): optional `.qsbg` picture (z0) -> `.qsstrap` (z1) -> optional `.rowart` logo slot (z2) -> `.rowin` content grid (z2) -> `.qspg` progress line (z2) -> Dusty `::after` (z3) -> Shuffle ring `::before` (z4). Custom properties on the row: `--accent` = `g.colour`, `--qp` = progress 0-1, `--qsc` = the state colour. Classes: `qs`, `qs-<k>` where k = `played | playing | dropped | coming | out` (the `qkStat` kind), `artrow` when `g.art && S.rowArt !== false`.

### State colours `--qsc` (status dot, progress line, ring)

| Kind | `--qsc` | Extra |
|---|---|---|
| out (default) | `var(--volt)` (the theme accent) | |
| playing | `rgb(63 217 164)` | row ring `inset 0 0 0 1.5px color-mix(srgb, accent 80%, var(--ice))` + outer glow `0 14px 34px -18px accent` |
| coming | `rgb(111 214 255)` | progress line dashed |
| played | `rgb(199 163 255)` | picture overlay is darker (below) |
| dropped | `var(--mute)` | |

These three literals have no token; Android needs named tokens for them (colours only from tokens, gate 6).

### Row shell

| Property | Unfolded (>500) | Folded (<=500) |
|---|---|---|
| min-height / measured height | 112 min; measured **138** for every row (LOG v310: name track `minmax(48px, auto)`) | 104 min; measured **104** every row |
| margin-bottom | 12 | 12 |
| radius | 22 | 20 |
| border | none; `inset 0 0 0 1px color-mix(srgb, accent 34%, transparent)` ring (playing: see above) | same |
| base fill | `var(--u-pan)` (#0a0e1b); transparent when `artrow` (a logo row has `background:none !important`) | same |
| clip | `overflow:hidden`, `isolation:isolate`, `transform:none`, no filter | same |

The test (`tools/scenarios/queue-rows.mjs`) fails if closed rows differ in height; keep all rows one height per width class. The 138 comes from the LOG, not from my arithmetic (the CSS sums to ~115 + one name line): put the web parity sheet at 859 next to the app's before trusting the number.

### Picture behind (`.qsbg`, `qsPic` 8758)

- Source, first that exists: `g.hero`; `STEAM_CDN + g.steamId + "/library_hero.jpg"`; `g.bg`; `cover(g)` (box art, else the drawn cover). None at all when `S.cardBg === "plain"` or `g.bgOff` (then no `.qsbg` element). A failed load leaves the dark ground (`var(--void)`), not a fallback to another picture. This is the OPEN CARD's picture too (never the logo).
- Box: `left: 28%` (folded 34%), top/bottom/right 0, `background-size: cover`, `background-position: bgX% bgY%` (default 100% 50%), colour `var(--void)`.
- Overlay `::before`, left edge pushed back to the row's left edge (`left: -38.9%`, folded `-51.5%` of the box = 28 % / 34 % of the row), so it spans the whole row; a 90deg gradient over the whole row width:
  - normal: `var(--void) 22%`, `color-mix(srgb, accent 34%, rgba(7,10,20,.9)) 40%`, `color-mix(srgb, accent 14%, rgba(7,10,20,.62)) 64%`, `rgba(7,10,20,.5) 78%`, `rgba(4,6,14,.66)` (100%).
  - played: `var(--void) 22%`, `color-mix(srgb, accent 18%, rgba(7,10,20,.93)) 40%`, `rgba(7,10,20,.74) 66%`, `rgba(7,10,20,.62)`.
  So the left 22 % of the row is solid near-black whatever the picture, and the picture only shows from ~40 % across.

### Strap (`.qsstrap` + `::before`/`::after`, 27382-27386, 27422-27424)

| | Unfolded | Folded |
|---|---|---|
| box (before skew) | `left -44px`, width 220, full row height | `left -30px`, width 140 |
| skew | `skewX(-15deg)`, `transform-origin: 0 0` (top-left: the bottom edge leans LEFT, so edges look like `/`) | `skewX(-9deg)` |
| fill | `linear-gradient(100deg, color-mix(oklab, accent 60%, black) 20%, color-mix(oklab, accent 88%, black) 92%)` | same |
| lighter stripe (`::after`) | 6 px wide, `right: 3px` of the strap, same skew, `color-mix(oklab, accent 55%, white)`, opacity .85 | 4 px wide |

"Darkened" = the oklab mix with black above. In Compose, `androidx.compose.ui.graphics.lerp(a, b, f)` interpolates in Oklab, which is exactly `color-mix(in oklab ...)`; the `color-mix(in srgb ...)` ones are plain RGB lerps **with alpha** (the existing `mix()` in Tokens.kt forces alpha 1: do not reuse it for the transparent ones). Draw the slant with a `drawBehind` path or `graphicsLayer` (the web keeps it on the strap's pseudo-elements only; the cover, spot and text stay straight: a v301 rule).

### Content grid (`.rowin`, 27388-27412, folded 27421-27433)

| Property | Unfolded | Folded |
|---|---|---|
| columns | `44px 58px 30px minmax(0,1fr) auto` (rank, cover, empty gutter, text, ticket), gap 12 | `26px 46px 18px minmax(0,1fr)`, gap 8 |
| rows | `auto minmax(48px,auto) auto` (kicker, name, meta) | `auto auto auto` |
| padding | top 10, bottom 12, left 20, right 16 (right `rslot + 22px` on logo rows) | top 9, bottom 10, left 12, right same rule |
| align | items center, content center | |
| spot | col 1, spans all 3 rows, 30px / 700, letter-spacing -.04em, `--ice`, shadow `0 2px 8px rgba(0,0,0,.45)`, tabular numbers | 19px |
| box art | col 2, spans rows, 58 x 86, radius 10, `box-shadow 0 0 0 2px rgba(255,255,255,.22)`, cover-fit with `imgX/imgY` focus (`coverPos`), STRAIGHT | 46 x 68, radius 8 |
| kicker | col 4 row 1, 12.5px / 600, colour `color-mix(oklab, accent 45%, white)`, 1 line, ellipsis. Text = "Up next" (row 1, not playing/played) else the word (Out now / Coming / Playing / Played / Dropped) | hidden |
| name | col 4 row 2, margin-top 2, 19px / 700, ls -.015em, line-height 1.2, `--ice`, max 2 lines (ellipsis) | 15.5px, rows 1-2, `align-self:end`, 2 lines |
| meta line | col 4 row 3, flex wrap, gap 3px 10px, margin-top 6, 14px / 600 `--ice` | 12.5px, gap 2px 8px, margin-top 4 |
| in meta | status dot (8px, `--qsc`, 3px ring `color-mix(srgb, --qsc 24%, transparent)`) + the headline (`nowrap`, gap 8); the sort pill if any. Date and hours are hidden | + short date (`shortDate`, 12.5px / 600, `rgba(234,242,255,.72)`, nowrap; struck through if the game is still `upcoming` and its date is already past) |
| ticket | col 5, rows 1-3, 62 px high, margin-left 6 (below) | hidden |

Sort pill (`.sortv`): inline-flex, baseline, gap 4, padding 2px 9px, pill radius, fill `rgba(var(--vrgb), .18)`; value 13px / 700; name 11.5px / 600 (not shown folded).

### Ticket (`.qstk`, `qkStat().stub/body`, unfolded only)

Two cells side by side, each: padding 0 13, min-width 72, column, centred, fill `color-mix(srgb, accent 26%, rgba(5,8,16,.88))`, 1px border `color-mix(srgb, accent 55%, transparent)`. Value `b` 16px / 700 `--ice` nowrap tabular; label `small` 11.5px / 600 `rgba(234,242,255,.62)`.
- Stub (left): radius `12 0 0 12`, no right border. Body (right): radius `0 12 12 0`, left border 2px DASHED `color-mix(srgb, accent 50%, rgba(255,255,255,.35))` (the perforation).
- Torn (`snap`, set for out / playing / played / dropped; NOT for coming, so a coming game's ticket stays whole): the stub is `translate(-5px, 4px) rotate(-6deg)`, origin top-right, gets a 1px right border and radius `12 2 4 12`. Static (no animation on the row).

### Progress line (`.qspg`, `qsProg` 8762)

Bottom edge, full width, 4 px tall, track `rgba(255,255,255,.07)`, fill = `scaleX(--qp)` from the left, gradient 90deg `color-mix(oklab, accent 70%, white)` -> `--qsc`. Coming rows (`qswait`): the fill is dashed instead: `repeating-linear-gradient(90deg, --qsc 0 8px, transparent 8px 13px)`.

| Kind | `--qp` |
|---|---|
| played | 1 |
| playing, dropped | `hrs ? min(1, logged/hrs) : 0` (3 decimals) |
| coming with a date | `max(.04, min(1, 1 - (dateUTC - now) / (182 days)))` (half a year out = the 4 % minimum) |
| coming TBA, out | 0 |

### Headline, stub and body per game (`qkStat`, 8605; `n` = spot, `hrs = hrsOf(g)`, `lg = g.logged`, `hb = [hrs ? hrs+" h" : "—", "to beat"]`)

Evaluated in this order (first match wins; so a backlog game with a FUTURE date shows as Coming):

| Kind | `word` | `head` (the row's headline) | stub `[value, label]` | body | snap |
|---|---|---|---|---|---|
| played (`isPlayed`) | Played | runs 1 "Finished", 2 "Played twice", else "Played N times" (`runs` = record's, 1-99) | `[score or "—", "my score"]`; score = `myScore(g)` (mean of positive RATINGS x 10, rounded) else `round(rec.real x 10)` if `scored(rec)` | `[runs+"×", "played"]` | yes |
| `state === "playing"` | Playing | hrs ? (left ? `"<left> h left"` : "Nearly done") : "Playing now"; `left = max(0, round(hrs - lg))` | hrs ? `[pc+"%", "done"]` (`pc = min(99, round(lg/hrs x 100))`) : `[round(lg)+" h", "played"]` | hrs ? `[left+" h", "left"]` : hb | yes |
| `dropped` | Dropped | "Set aside" | `[round(lg)+" h", "played"]` | hb | yes |
| future date (`cd.v` number, `cd = ceil((dateUTC - now)/864e5) > 0`) | Coming | n==1 "Out tomorrow", else "Out in N days" | `[shortDate without the year, 4-digit year]` | `[N + " day"/" days", "to go"]` | no |
| no date (`cd.v === "TBA"`) | Coming | "No date yet" | `["TBA", "date"]` | hb | no |
| otherwise (out) | Out now | `isFresh(g)` ? "Just out" : "Ready to play" | date ? `[shortDate without year, year]` : `["Out", "now"]` | hb | yes |

`shortDate` (7916): `toLocaleDateString("en-GB", {day:"numeric", month:"short"})`, plus `year:"2-digit"` when the year differs from the current year ("13 Sep 27"); the stub strips the trailing ` \d+`. Use `DateTimeFormatter("d MMM[ yy]", Locale.UK)` and check September ("Sept" in current ICU/CLDR for en-GB) against a Playwright golden before trusting it. The date is `new Date(g.date)` (UTC midnight) shown in the device zone.

JS prints whole numbers without ".0" (`12 h`, `"6.5 h"`); Kotlin's Double does not. Format every hours / score number with a JS-style formatter.

### Logo slot and optical sizing (v290; `.rowart` CSS 1422-1431, qs overrides 27414-27415, 27433; `lfRule` 20985)

Slot (only when `artrow`): `position:absolute; right:14px; top:8px; bottom:8px; width: --rslot`; `--rslot` = `min(28%, 1.4 x S.rowArtW(150px))` unfolded, `min(27%, ...)` folded (% of the row width => ~196 px at 700 wide, ~103 at 380 wide); `opacity: S.rowArtOp/100` (.95); `overflow:hidden`; flex, `justify-content:flex-end; align-items:flex-start`; no touches (`pointer-events:none`); sits ABOVE the text layer's z but the text never goes under it because `.rowin` has `padding-right: rslot + 22px`.

Per logo, measured once and cached by URL (`localStorage gq:logofit`; v290 entries have 6 numbers; older ones are re-measured). `lfMeasure` (20961): load the image (CORS), downscale so the long side is 200 px, read alpha: ink box = bbox of pixels with alpha > 24 -> `[aspect(w/h, 3 decimals), x0, y0, x1, y1 (fractions, 3 decimals), lum]`; `lum` = mean of `0.2126R+0.7152G+0.0722B` over pixels with alpha > 128 (rounded; -1 unknown/blocked). No ink at all -> box = whole picture. A host that blocks pixel reads gets `[aspect, 0, 0, 1, 1, -1]`. Measured one at a time in idle time.

`lfRule(id, [a, x0, y0, x1, y1, lum])` with the slot's size `W x H` (measured by `lfRO` from the first slot; all rows share one size per width class):

```
cw = max(.05, x1-x0); ch = max(.05, y1-y0); ca = a*cw/ch          // ink aspect
A  = W*H*.26;  Hm = H*.86;  Wm = W
hc = max(sqrt(A/ca), H*.3);  wc = ca*hc                             // ink height / width
if (hc > Hm) { hc = Hm; wc = ca*hc }  if (wc > Wm) { wc = Wm; hc = wc/ca }
ih = hc/ch;  iw = ih*a                                              // size of the WHOLE picture
guard g = .02: X0=max(0,x0-g) Y0=max(0,y0-g) X1=min(1,x1+g) Y1=min(1,y1+g)
draw the sub-rectangle [X0,Y0,X1,Y1] of the image at ((X1-X0)*iw) x ((Y1-Y0)*ih),
right edge on the slot's right edge, centred vertically: top = (H - (Y1-Y0)*ih)/2
```
(Chrome path above; Safari draws the whole picture moved to put the ink in the same place.) Every logo gets the same ink AREA (26 % of the slot), a very wide one keeps at least 30 % of the slot height, nothing passes 86 % of the height or the slot width. Dark logos (`0 <= lum < 50`) are drawn as a light silhouette: `filter: brightness(0) invert(.9)` = every non-transparent pixel becomes RGB 0.9 (230/255), alpha kept, and then NO pale edge. Other logos keep a faint pale 1.5 px edge `drop-shadow(0 0 1.5px rgba(226,238,255,.8))` (also shown for a logo not measured yet, drawn `contain`, right-aligned). Android: Coil `Transformation`/decode on a background thread, `ColorFilter.colorMatrix` for the silhouette, srcRect crop, cache in the app-local store. Web checks to mirror as Android tests: `lfLook` (26762: spills, off screen, cut by the slot, on the words, still near-black, 5x size difference, name squeezed under 60 px, squashed cover, wrapped date) and `tools/scenarios/logo-look.mjs`.

Rule from the owner (v290): never let a logo hang off its row, tilt, or run past the screen edge.

## 4. The bar under the header (`qdDeck`, 8785; CSS 27439-27520)

Layout: one flex row `.qdeck` (margin `6px 0 16px`, gap 8): `[tab strip] [Order] [Shuffle] [Boss]`. Folded: wraps; the strip takes its own full line (`flex: 1 1 100%`), then Order (grows) + Shuffle + Boss share line 2; gap 6. Never a side scroller (a scroller got its own GPU layer and doubled the layer count: kit `layers` budget); extra tabs wrap onto more lines.

Tab strip (`.qdtabs`): flex wrap, gap 4, padding 4, radius 18, fill `--u-pan`, 1px `--u-line2`.

| | Unfolded | Folded |
|---|---|---|
| tab height / padding / gap | 44 / 0 14 (0 10 at 501-1099) / 7 (6) | 40 / 0 9 / 5 |
| text | 14.5px / 600 `--mute`, radius 14, nowrap, grows (`flex: 1 0 auto`) | 13.5px |
| icon | 17 px drawn (`QICON[v]`, 4 svgs 7335-7341; `fill: currentColor`) | |
| count | `small` 12.5px / 700 `--mute`, tabular | |
| label | always | **only the selected tab shows its name**; the others show icon + count |
| selected (`aria-pressed`) | text `--ice`; fill 180deg `rgba(vrgb,.3)` -> `rgba(vrgb,.16)`; inset 1px `rgba(vrgb,.55)`; icon + count `--volt` | same |
| list tab | the list's emoji `l.e` (15px, user data: stays an emoji), name, count | |
| lists button | `qdlist`: icon only (24x24 `QD_IC.list`, 17px), `flex: 0 0 auto`, padding 0 11; label "Make a list" / "Manage lists" (aria); tap -> `beep("tap")` then the lists sheet (`openLists(null)`) | |

Tap on a tab: `S.qFilter = v; focusId = null; save(); renderQueue(); beep("tap")`. The open card stays closed/open per `collapsed` (§7). aria-label "<name>, <count>".

Order button (`#sortby`, `.qdsort`): height 54 (folded 48), padding 0 14 (0 12), radius 18 (16), `--u-pan` + 1px `--u-line2`; icon `QD_IC.order` 20px (`--volt` when sorted, i.e. `aria-pressed = !manual`; border becomes `rgba(vrgb,.6)` then); two-line label: small "Order" (11.5px / 600 `--mute`) over bold name (15px / 700, ellipsis: "My order" or the sort's `SORTS` name, "Sorted" fallback); caret `QD_IC.car` 16px `--mute`, rotates 180deg (.25s `--ease`) while open. `min-width` 168 (148 at 501-1099, 0 folded where it grows). `flex: none` (v303: it shrank under its min-width and ran over Shuffle).

Order menu (`#sortpop`, `.qdpop`): hidden until opened; absolute under the button (`top: 100% + 8px`), right-aligned to the bar (folded: left-aligned), width `min(470px, 100vw - 32px)`, max-height 66vh, padding 14, radius 20, fill `--u-card`, 1px `--u-line2`, shadow `0 24px 60px rgba(0,0,0,.7)`. Per group: label (12.5px / 600 `--mute`, margin `2px 4px 8px`), then a wrapping row (gap 6, margin-bottom 12) of option buttons: height 38, padding 0 13, radius 12, `--u-card2`, 1px `--u-line`, 14px / 600 `--ice`; chosen = fill `--volt`, text `--void`, no border. Last: the action button full width (padding 11 14, radius 14, fill `rgba(vrgb,.12)`, text `--volt`, 14px / 700, left-aligned): "Order my list by hype" when manual else "Make this my order". Behaviour: the button toggles it (`beep("tap")`); a tap anywhere else closes it; picking an order: `S.sort = k; focusId = null; save(); renderQueue()` (no sound) and the whole bar redraws closed.

Shuffle (`[data-qshuf]`, `.qdshuf`): icon `QD_IC.shuf` (`--volt`), the word "Shuffle" only at >= 1100 px (never on the phone); aria "Shuffle: pick tonight's game (hold for the shuffler)". **Tap** (`qdShuffle`, 8810): `live` = every game not played and not dropped (comp included); `out` = those `isOutNow` (includes playing); `pool = out.length ? out : live` minus the last pick (`qdLast`, memory only, not saved), unless the pool would be empty; none -> toast "Nothing to pick from yet". Random pick; if the game isn't in the shown tab, `S.qFilter = "all"` (saved). In Character select it also sets `qfSel = pick`, `collapsed = true`, `focusId = null`. Redraw, `beep("nudge")`, then on the next frame the row (or cover) `scrollIntoView({block:"center", behavior: calm ? "auto" : "smooth"})` and gets a ring for 2.4 s (§ below), toast `"Tonight: <title>"`. **Hold** 480 ms (pointerdown timer; cancelled by up/cancel/leave; the click after a hold is swallowed): `beep("tap")` and `openShuffle()` (15915, another helper's deck). Context menu suppressed.

Shuffle ring (`.qslit`, 27417-27419): on a row `::before`, on a Character-select cover `::after`: `inset 0` overlay, radius inherit, `box-shadow: inset 0 0 0 2px var(--ice), inset 0 0 0 6px color-mix(srgb, accent 60%, transparent)`, animation 2.3 s `--ease`: opacity 0 -> 1 at 12 %, hold to 75 %, -> 0 at 100 %. Removed at 2.4 s.

Boss (`#fx-boss`, `.qdboss`): icon `QD_IC.boss` 24px `--hot`; border `color-mix(srgb, --hot 38%, transparent)`; fill 180deg `hot 13%` -> `hot 4%` over `--u-pan`; same height/radius as Order. Text block (min-width 70; 60 folded; 58 at 501-1099): when `S.grum` is on: bold HP number (15px / 700) + "HP" (11px / 700 `--mute`) over a 6 px bar (track `rgba(255,255,255,.08)`, fill `scaleX(--hp)` from the left, gradient 90deg `--hot` -> `color-mix(srgb, hot 55%, white)`); when Grumble is off (default): the word "Boss" only (v302). Data (`bossNow`, 13912): sum over games that are not comp, not played, not dropped, and (`backlog` | `playing` | `isOutNow`) of `+g.hours || 20` hours; `max = max(hp, S.fxBossMax)`; `--hp = (hp/max).toFixed(3)`. aria "Boss: the Shadow of the Backlog, N HP". Tap -> `bossScreen()` (13925, Phase 9).

## 5. On deck (v259 photo cards, v303; 13299-13320, 24611, CSS 24517-24533)

- Shown above the rows, under the bar. Gone when `S.deckOff`, when it has no games, or under Played (nothing left to play).
- Games (`deckShown(3)`): All -> `onDeck(3)` = top 3 of `onDeckScores()` (live = not played, not dropped, not comp; Android `Rank.onDeck`). Any other tab -> the top 3 of the same scores among `queueList()` ids (NEW in Android: `deckShown`, a filter over `Rank`'s score map; keep the JS key-order tie rule).
- Heading `deckHead()`: "On deck", plus " · <name>" for a list / genre / tab other than All. Style: `.deck-h` 11px, `--mute`, weight 600, letter-spacing .08em, uppercase (base rule; ui2 only changes weight/spacing/colour: confirm on the parity sheet).
- Look "New" (`S.dzX.deck` absent): `.deck.dzd` -> `.dzd-row` flex, gap 12, padding `8px 2px 12px`, a **horizontal scroller with snap** (`scroll-snap-type: x mandatory`; the touch guard must treat it as a side scroller: Android `SwipeGuard.inScroller`). Card `.dzd-s`: width `min(76%, 300px)` (>= 700px: the three share the row, `flex: 1 1 0`), height 410, radius 30, padding 8, fill `--u-pan`, 1px `rgba(255,255,255,.06)`, shadow `0 18px 40px -20px rgba(0,0,0,.85)`, column.
  - Picture block: height 50 %, radius 24, fill `rgb(20,26,46)`, image cover. Picture = `bgSource(g) || cover(g)` = (`bgOff` ? none : `hero` | steam hero | `g.bg` | `g.art` | cover(g)); note it can fall to the logo `g.art` before the box art (web behaviour).
  - Text block: padding `14px 8px 0`, column gap 4: title 26px / 600, lh 1.05, ls -.02em, max 2 lines; `why` 13px opacity .72, 1 line = `deckWhy(g)` or the first two genres joined " · "; meta chips (`dzMeta`): "≈ 12h" (hours), "on <first platform>", "out 13 Sep" or "Out now" — 13px / 500, gap 14, wrap, `em` words at .65 opacity.
  - Foot row: pill button 46 high (radius 23, fill `--volt`, text `rgb(6,10,22)`, 14px / 600) saying "Play this next" for #1 and "Open" for #2/#3, and a 46px circle with 1px `rgba(255,255,255,.18)` showing "#1".."#3" (14px / 700). The whole card is one button (`data-opg`) -> `beep("open")` and the game page (`openFull`).
  - `--accent` on the card is `g.colour` but this look does not paint with it.
- Look "Classic" (`S.dzX.deck` truthy): the older 3-cell grid `.deck-g` (34px cover + bold title + 2-line why); one column folded. Phase 0 left old looks out; ask the owner or skip it (Settings is Phase 7).
- `deckWhy` (13286): " · "-joined bits: "like your #N <title>" (first of your top 6 sharing a genre), "on <platform>" (when On deck's machine isn't Any and the game has it), "you've started it" (playing), else "out <shortDate>" / "not out yet" (unless backlog or fresh). Needs `rankedGames`/`rankOf` (Rank has the table; `deckWhy` itself isn't ported).

## 6. Character select (`S.qLook === "select"`; `renderQueue` 8953; `qfPanel` 8824, `qfGrid` 8839, `qfTap` 8845; CSS 27475-27520)

Instead of rows: one `.qfwrap` = [the open card OR the panel] + [cover grid]. State: `qfSel` (memory only; defaults to the first shown game), plus the same `collapsed` / `focusId` as rows. The open card shown is the picked/active game's (§7). Swipe-to-delete does not apply here (it only reads direct children of `#p-queue`).

Panel (`.qfsel`, when no card is open): height 300 (236 folded), margin-bottom 14, radius 24 (20), ground `--void`, ring `inset 0 0 0 1px color-mix(srgb, accent 42%, transparent)`, a button (tap = `qfTap(qfSel)` = open its card; the small cover button opens the game page).
- Picture: same source as the row (`qsPic`), cover, `bgX/bgY`; overlay gradients: 90deg `var(--void) 18%`, `color-mix(srgb, accent 30%, rgba(7,10,20,.8)) 46%`, `rgba(7,10,20,.22) 80%`, plus 0deg `rgba(4,6,12,.85)` -> transparent at 42 %.
- Left column `.qfin`: left 26 (16), top/bottom 22 (16), width 52 % (60 %), column.
  - kicker: chip with the spot `NN` (padding 3px 10px, radius 8, fill `color-mix(oklab, accent 75%, black)`, bold tabular), the status dot, `"<word> · <head>"` (13px / 600 `--ice`, 1 line).
  - logo: height 92 (62), margin-top 14 (10), contain, left-aligned, `g.art`; near-black (the same `lum < 50`) -> `brightness(0) invert(.9)`.
  - title: 15px / 600 `rgba(234,242,255,.82)` (13px folded), margin-top 8 (4), 1 line.
  - three segmented bars at the foot (`margin-top:auto`, grid `auto 1fr auto`, gap `7px 12px`, folded `5px 8px`, max-width 380): label 12.5px / 600 `--mute` (11 folded), value 13px / 700 `--ice` right-aligned (11 folded); each bar = 10 slanted segments (flex 1, gap 3, height 10 (8), radius 2, `skewX(-14deg)`), lit when `k < round(v x 10)`: lit fill `color-mix(oklab, accent 70%, white 20%)` (only 90 % opaque), unlit `rgba(255,255,255,.1)`. Bars: **Hype** `hype/10`, text "7/10" or "—"; **Length** `min(1, hrs/100)`, text "N h" or "—"; third by kind: played "Score" `score/100`, text score or "—"; coming "Release" `qsProg`, text `stub[0]`; playing/dropped "Done" `qsProg`, text `N%`; out "Expect" `expected(g)/10`, text `expected` (1 decimal) or "—".
- Cover (`.qfcv`): right 26 (16), bottom 22 (16), 132 x 190 (88 x 128), radius 14, shadow `0 16px 36px rgba(0,0,0,.6), 0 0 0 2px rgba(255,255,255,.22)`; tap = the game's own page.
- Swap animation when another cover is picked (not calm, not when a card opens): `.qfin` .42s from opacity 0 + `translateX(-18px)`; cover from opacity 0 + `translateY(14px)`; class lives 500 ms.

Grid (`.qfgrid`): `repeat(auto-fill, minmax(86px, 1fr))`, gap `16px 10px`, padding-top 8. Cell (`.qfc`, one button per shown game, in list order): aspect 2:3, radius 14, `--u-pan`, ring `0 0 0 1px --u-line2`; cover filling it; foot gradient 180deg transparent 55 % -> `rgba(4,6,12,.85)`; spot `NN` bottom-left (left 7, bottom 6, 13px / 700 tabular); status dot (8px) at right 8, bottom 10. Dot colours = the same `--qsc` set but **dropped and out cells both use `--volt`** (no rule for them; the row uses `--mute` for dropped: web inconsistency, copy or ask). Played cells at opacity .62. The picked cell (`on`): opacity 1, ring `0 0 0 3px color-mix(oklab, accent 70%, white 25%)` + glow `0 10px 26px -8px accent`, and the "P1" tag above it (top -12, centred, padding 2px 8px, radius 7, 11.5px / 700, fill `color-mix(oklab, accent 70%, white 20%)`, text `--void`).

`qfTap(id)`: let `open` = the shown card's id (or null). `open === id` -> close it (`collapsed = true`, `beep("back")`); `open` (another card is open) or `id === qfSel` (the panel's own game) -> open this game's card (`collapsed = false; focusId = id`); else just move the panel (`beep("tap")`). `qfSel = id`, redraw. If a card appeared: entrance (`heroin`, 2 s class unless calm/motion off) + `sfx("qkopen", torn)`; else the panel swap animation above. A tap on the panel itself calls `qfTap(qfSel)` (opens its card).

## 7. Taps, opening and closing a card from the list

- Row tap (`r.onclick`, 8992): only one card is open at a time. Tap the open card -> `collapsed = true; focusId = null; beep("back")`. Tap a closed row -> `collapsed = false; focusId = id`; the old card closes in the same redraw; entrance animation (2 s class, skipped when `S.motion === false`, `S.vt === false` or calm); sound `sfx("qkopen", torn stub?)` (`beep("open")` if the card had no `.qkin`).
- The open card is chosen at draw time: `activeId = (collapsed || !sorted.length) ? null : (sorted.some(g => g.id === focusId) ? focusId : sorted[0].id)`. App start has `collapsed = false, focusId = null`, so **the first game of the shown list is OPEN when the Queue first appears**; tab/sort changes set `focusId = null` but leave `collapsed` as it was (collapsed stays collapsed; otherwise the top game opens again). Deleting or filtering away the focused game falls back to the top.
- Keep the tapped row under the finger (v307, `qKeep`, 11585-11605): closing the card above moved everything up ~440 px; after the redraw, in the next frame, scroll so the tapped row's top is where it was (if it started on screen); an opened card above the screen scrolls into view (`top < 0` -> `scrollBy(top - 70)`); one running under the tab bar (`bottom > viewport - 90`) slides up by `min(over, top - 70)`. Smooth, instant when calm. In a LazyColumn use `scrollToItem(index, offset)` after the swap.
- Order changes glide (11585-11628): when the order of rows changed (reorder, sort, filter), each row that existed before animates from its old top to its new one, 560 ms `cubic-bezier(.2,.9,.25,1)` (translate only); skipped when calm, when opening/closing a card, after a drag drop, for rows off screen both times, or moves over 2 viewports. `animateItem()` on the LazyColumn items covers it.
- Rows keep `touch-action: pan-y` (vertical scroll only) so sideways swipes reach the delete handler.

## 8. Dusty backlog's mark (only the mark; `dustLvl` 21071, CSS built by `dustApply` 21076)

Level from days of dust: `d >= 240` -> 3, `>= 120` -> 2, `>= 60` -> 1, else none. `dustDays(g)` (21061) is 0 unless the game is not comp / played / dropped / playing, `S.dustOff` is off, and it is out (`state === "backlog"` or a date <= local today string); otherwise `floor((now - since) / 86400000)` with `since = max(added+"T12:00:00" local, date+"T12:00:00" local if the date is today or past, last Steam session `sgLast(g)` (Phase 9: use 0 now), g.dustOff)`; 0 if none of those exist.
Mark: an overlay on the whole row (also the open card), `inset 0`, `border-radius: inherit`, z-index above the content, no touches. Levels 1-3 differ only in `opacity .5 / .8 / 1`. Its look (DUST_BASE, 21096): two dot grids and a veil: `radial-gradient(rgba(214,206,190,.55) .8px, transparent 1.3px)` tiled 23 x 19 at (3px, 5px); `radial-gradient(rgba(214,206,190,.4) .6px, transparent 1.1px)` tiled 13 x 17 at (9px, 2px); `linear-gradient(180deg, rgba(190,180,160,.10), rgba(190,180,160,.04))`. Level 3 also draws a cobweb at the top-right (15px, padding `2px 10px`); the web uses the 🕸️ emoji, so draw an icon instead (v297). No Character-select cover gets dust (only `[data-row]`).

## 9. Gestures on rows

### Long press -> drag to reorder (`renderQueue` handlers 8965-8990; `startDrag` 7417)
- Every `[data-row]` (closed rows and the open card). A finger down arms a **380 ms** timer. Cancelled by: finger up, a vertical move > 10 px (a scroll), a touch cancel, or horizontal intent (the swipe handler sends a synthetic touchcancel once `|dx| > 1.3 x |dy|` after 10 px). It does not cancel on a horizontal-only wiggle under that.
- After 380 ms: in **My order** (`S.sort` manual, any tab) -> the drag starts. In any other order -> `openFull(id)` (the game page) instead, no lift. Right-click does the same on desktop; ignore.
- Start: `beep("nudge")` + `navigator.vibrate(14)` = `Buzz.ms(14)`; the row gets the `dragging` class: `pointer-events:none`, z 5, **2px `--volt` outline** (rounded), no transition. The `.qs` row's inset ring beats the old lift shadow, so there is no lift, scale, tilt or shadow growth (#309/v246/v280: nothing shrinks or lifts when touched): the row just follows the finger 1:1 (`translate`).
- While dragging, the page is held still (touchmove is blocked; there is NO autoscroll on the web: a row can only travel within the screen per grab). Android: consume the pointer so the list doesn't scroll; autoscroll at the edges is optional and not parity (ask).
- Slot maths: rects of ALL data-row siblings are taken at start; `h = heldHeight + 7` (stale since v301: the real pitch is row height + 12 margin; **use the true pitch on Android**). The held row's midpoint = its original midpoint + dy. `to` = the lowest index above `from` whose midpoint is below the held midpoint, or the highest index below `from` whose midpoint is above it, else `from`. Other rows between `from` and `to` shift by `-h` (when moving down) or `+h` (up), `translate .17s cubic-bezier(.2,.9,.25,1)` (no transition when Animations are off). Each new slot: `beep("swipe")` (no buzz). One update per frame.
- Drop: the order is the shown rows' order with the held one moved; `g.ord = index + 1` for **only the rows that were shown**, `S.sort = "manual"`, `beep("tap")`, redraw with no entrance/glide (the row snaps into its slot; no settle animation). In a filtered tab the numbering collides with hidden games' old `ord` values (ties then fall back to array order); in All every game gets a unique ord. Port as it is, report as a web quirk (an owner question), do not "fix" silently.
- Practice copy on Android: keep the new order in memory (per backup), reset on reopen; nothing saved.

### Swipe left -> Delete (v171; `15070-15142`)
- Targets: direct children `#p-queue > [data-row]` (closed rows and the open card; not Character select covers). Touch-action pan-y.
- Intent after 10 px: horizontal if `|dx| > 1.3 x |dy|`; only then the row follows: offset = `clamp(base + dx, -138, 0)` where `base = -92` if this row is already open else 0 (right swipes only close). Only the row's CHILDREN slide (translate); a red button waits under them, flush right: `.qdel` 92 px wide, full height, fill `#D93A4A` (a literal red; Android needs a token), white text "Delete" 14px / 800, `letter-spacing .04em`, uppercase (web style; the 2-look rule bans uppercase headings, this is a button label: ask or use sentence case), radius `0 13 13 0` clipped by the row's own 22 radius.
- Release: offset < -46 (half of 92) -> snaps to -92 and stays open (`translate .24s cubic-bezier(.2,.9,.25,1)`); else animates back to 0. Only one row open at a time: touching another row, tapping elsewhere (the tap is swallowed) or scrolling away closes it. A swipe's trailing click is eaten for 400 ms. The swipe must not also walk the Games views (web stops propagation; Android: claim the gesture, `SwipeGuard`).
- Tap on Delete: native `confirm("Delete <title>? Its scores, duels and diary go with it.")`. Yes -> remove the game, its `S.played` records, every duel it is in (`a` or `b`), its AI "why" answer, clear `focusId` if it was focused; save; redraw all; `beep("back")`; toast `Deleted <title>`. No -> the row closes. **There is no Undo on the web.** The Android plan adds undo toasts after deleting or moving a game (plan, Oct 26, owner-approved): a few seconds of "Undo" after the delete (practice copy, in memory). Sounds on Android: `Sfx.Back`; buzz none.

## 10. Empty states, edge cases

| Case | Web behaviour |
|---|---|
| no games at all | the whole screen is only `<p class="empty">Queue is empty. Open Add and search for the first game.</p>` (no header, bar or On deck: early return at 8861) |
| a tab with no games | header + bar + On deck stay; below them `<p class="empty">Nothing here right now. Try another filter.</p>` (`.empty` in Clean: radius `--u-r`, 1px `--u-line`, fill `--u-pan`) |
| Played tab | On deck hidden |
| very long name | row: 2 lines then ellipsis (19 / 15.5px), row height unchanged; kicker 1 line; Character-select title 1 line; On deck title 2 lines |
| no logo (`!g.art`) or `S.rowArt === false` | no `.rowart`, no `artrow`: the row keeps `--u-pan` as ground and its text runs to `padding-right: 16px` |
| logo not measured yet | drawn contain, right-aligned in the slot with the pale edge, until `lfMeasure` fills the cache, then the sheet is rewritten (no row redraw needed) |
| no picture / `cardBg plain` / `bgOff` | no `.qsbg`: on a logo row the ground is transparent (page background shows) under the strap; on a plain row `--u-pan`. A picture that fails to load: dark `--void` ground + the left gradient |
| no `colour` | `Game.colour` falls back to `PALETTE[0]` |
| no cover (`img`) | the drawn cover (`DrawnCover`) |
| many platforms | rows show none (only the open card and the back do) |
| game with a date in the past but still `upcoming` | headline says "Ready to play", date struck through (folded), Out tab includes it |
| `n >= 100` | spot shows 3 digits ("100") in a 44 px column at 30 px: it will overflow/clip; check on the parity sheet with 100+ games |
| a list / genre / "like" tab whose source vanished | `qNow` falls back to `all` for unknown `L:` ids; unknown `G:`/`S:` tabs are not in `QFILTERS` so also `all` |

## 11. Rules from CLAUDE.md / LOG.md that apply (keep every one)

- No press animation (#309, v246, v269): nothing shrinks, lifts or fades on touch; the lint check `kit.mjs syntax` fails on any movement in `:active`. Android: no `indication`, no ripple, no `animateFloatAsState` on press (gate 6 lint).
- No touch effects (v280): nothing glows or appears under the finger; the row must not light when touched on an empty spot. Check `touch-empty.mjs`. Hover styles do not exist on Android.
- v301 (closed rows + bar): the strap, the spot and straight box art on it, headline = the open card's `qkStat`, the ticket hidden folded where the date sits under the name, never bring back the five loose chips or a filter menu; the slant lives only on the strap's layers.
- v310: all closed rows one height per width class. v303: a tab never says "undefined"; On deck's three cards share one shape and follow the filter; Order never runs over Shuffle (`flex: none`).
- v290: logos upright in their own slot, same visual size, near-black ones drawn light; never hang off the row or past the screen.
- v299/v300/v306 (card): one open card, `sfx("qkopen")` on open; old reveal ticks are gone: never play them. (Card is piece 2.)
- v313 (`qcold`/`qw`): the rows' picture is the expensive part; the web draws far rows lazily. In Compose, LazyColumn + keys + stable params; gate 3 (Macrobenchmark + redraw counts) starts here.
- v307: opening a card must not throw the tapped row off screen; a tap lands on the row under the finger.
- v308: no scroll-linked motion on list rows (rows simply appear).
- v297/v299: drawn icons only (24x24, fill `currentColor`, one part at `.35` opacity); no emoji icons (`S:` tab's 🔍 and the cobweb need drawn replacements; list emoji are the owner's data and stay).
- Background stays a still gradient (v273/v276); loops keep running while scrolling (v282): nothing in the list pauses for a scroll.
- Colours only from tokens; `#D93A4A`, `rgb(63 217 164)`, `rgb(111 214 255)`, `rgb(199 163 255)`, `rgba(7,10,20,x)`, `rgba(4,6,14,.66)`, `rgba(5,8,16,.88)`, `rgba(234,242,255,.62/.72/.82)`, `rgb(20,26,46)`, `rgb(6,10,22)` appear as literals in the web CSS: name them in one `ui/QueueTokens.kt`-style object with the source line in a comment.
- Android additions already approved (plan Oct 26): picture placeholders (a soft blur in the game's colour while a picture loads), undo toast after delete/move, balanced headlines for game names, 48 dp touch targets (the 40-44 px tabs need a 48 dp hit area), interruptible animations.
- Text sizes at font scale 1.3 (`TextStyle.fixed()`), shots at 320/412/859/915x360.

## 12. What Android already has (searched `android/app/src/main`)

| Need | Android today |
|---|---|
| Queue screen | `ui/Pages.kt` `QueueSoon` (208): a placeholder card per state from `Library.byState` (Group of games sorted by `ord` then title). Nothing of the rows, bar, deck, select or gestures |
| Header above the list | `ui/HeaderCard.kt` + `PlainTitle`; `ViewPill`/`ViewChips` (`ui/Pill.kt`); `SwipeGuard` (Pill.kt 59) already stops the view walk for a side scroller |
| Game data | `data/Library.kt` `Game` (id, title, rawState/state, date, ord, colour, comp, `isUpcoming`, `isFresh`; `get(key)` for the rest) + `Library.off(key)` |
| Played / Elo / On deck / hours | `data/Rank.kt`: `isPlayed`, `playedRec`, `isDropped`, `elo`, `rankTable`, `onDeck(n)` (unfiltered), `hrsOf`, `platList`, `onPlat`, `tierOf`. Missing: `Played.runs`, `deckShown`, `deckWhy`, `expected`/`hypeBias`/`genrePattern`, `myScore`/`avgScore`, `rankedGames`/`rankOf` if used |
| Dates | `data/Backup.kt` `JsDate` (`utcMidnight`, `todayLocal`, `days`) + `JsDate.today0/localMidnight` in Rank.kt. Missing: `shortDate`, `isOutNow`, `countdown`, a JS-style number formatter |
| Pictures | `ui/Cover.kt` `PicBox`, `DrawnCover`, `picUrl` (Coil 3); `Web.STEAM_CDN`, `Web.PALETTE`, `Web.STATES` generated. Missing: `qsPic` order, hero background with a void ground, logo measure/crop/silhouette (no `gq:logofit` equivalent) |
| Tokens | `ui/Tokens.kt` `Pn` (pan, card, card2, line, line2, ice, mute, hot, volt, void, shade, `r` 20, `rs` 14), `Look`/`LocalLook` (theme accent), `mix()` (sRGB, alpha forced 1: not for the transparent mixes) |
| Fonts / type | `ui/Type.kt` Poppins 400-700 bundled, `Type.title/name/note/kicker`, `TextStyle.fixed()`, `Icons`/`PnIcon` (generated 24x24 drawn icons; the Queue's `QD_IC` (8769) and `QICON` (7335) are NOT in it yet) |
| Sounds / buzz | `kit/Sounds.kt` `Sounds.play(Sfx.X)` gated by Settings › Sounds: `Tap`, `Open`, `Swipe`, `Nudge`, `Back` are the web's `beep` kinds 1:1; `CardOpen`/`CardTear` for `qkopen`. `kit/Buzz.kt` `Buzz.ms(14)` for the drag start (the web calls `navigator.vibrate(14)` directly; `Buzz.swipes` gates only the cover ticks) |
| Motion | `ui/Motion.kt` `Ease`, `LocalCalm` (Animations off / reduce motion / battery saver), `Prefs`; `ui/Wipe.kt` |
| Local bookkeeping | `Store.prefs` (SharedPreferences "pn") for `qFilter`, `sort`, `qLook`, the logo-fit cache, practice reorder/delete |
| Tests | `app/src/test` has Roborazzi screenshots at 412/859 (`ui/ScreensTest`), golden file (`tools/golden.mjs`) for `days`, `isUpcoming`, `isFresh`; the golden should grow `isOutNow`, `queueList` per tab, `sortGames` per key, `qkStat`, `qsProg`, `shortDate`, `lfRule` (Playwright runs the web functions) |
| Web oracles to reuse | `tools/scenarios/queue-rows.mjs`, `logo-look.mjs`, `genre-filter.mjs`, `queue-open-place.mjs`, `swipe-games.mjs`, `soak-queue.mjs`; web parity shots at 412/859 |

## 13. The three things most likely to be ported wrong

1. **The "out" and countdown rules use two different dates.** `isOutNow` and `countdown` compare the date's UTC midnight with local midnight / now; `shortDate`/`dustDays` use other forms (`dustDays` local noon). Use the existing `JsDate` helpers per call site, and test around midnight like the golden does. A wrong tab for a game dated today is the likely visible bug.
2. **Opening state and the one-open-card rule.** The first shown game starts open (`collapsed = false`), tab/sort changes keep `collapsed`, a tap on a row swaps cards in one redraw, and the list must keep the tapped row under the finger. A closed-only list will look right in a screenshot and fail the real use (v307's bug).
3. **Colour mixing and the slanted strap.** `color-mix(in oklab ...)` (strap, kicker, bar fills) vs `color-mix(in srgb ...)` with transparency (rings, overlay, ticket fill) are different maths; the existing `mix()` forces alpha 1. The skew is on the strap only (cover, spot and text stay straight), and the two width classes differ in skew, size and which parts hide.

Also easy to miss: JS number formatting (no ".0"), drag uses the stale `+7` pitch on the web (use the real 12), drag/ord numbers only the shown rows, the lifted row has only an outline (no lift), no Undo on web delete (Android adds one).

## 14. Not found / uncertain

- The exact unfolded row height (138) is from LOG v310 and the scenario, not from running the page here (no Playwright in this container); take it from a parity shot.
- Whether Settings › Queue cards still offers "Row logo opacity/width" sliders is certain (lines 6323-6327); whether `S.rowArtGlow` (`html.rowglow`) is reachable is not checked: skipped.
- The uppercase of `.deck-h` under Clean is inferred from the CSS cascade (no `text-transform` override found), unverified visually.
- Reorder is assumed practice-only on Android like delete (the Brief names only delete).
- `deckWhy` (13286-13296) is fully read and quoted in §5; the `rankOf`/`rankedGames` it calls are in the rank code, not mapped here.
- Android's `STEAM_CDN` generated constant exists; I did not open the generated file to confirm its value.
