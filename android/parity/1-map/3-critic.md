Checked at web APP_V 327 (index.html, Nov 2 2026). Read-only: the two maps (`1-list.md`, `2-card.md`), the Brief, the plan's Decisions and Android basics, `0.md`, and index.html by grep/ranges. Line numbers are index.html at 327 unless a file is named.

# 1. Spot checks

## Confirmed (16)

| # | Claim (map) | Where it holds |
|---|---|---|
| C1 | `qkStat` order and words: played → playing → dropped → future date → TBA → out; "Out tomorrow" when `cd.v === 1`; `stub` drops the trailing year; `snap` on every kind but coming (both maps) | 8605-8620 |
| C2 | Two different state palettes: rows `--qsc` out `--volt`, playing `rgb(63 217 164)`, coming `rgb(111 214 255)`, played `rgb(199 163 255)`, dropped `--mute` (1-list); card kicker dot coming `--ice`, out `rgb(79,209,139)`, playing `rgb(140,196,255)`, played `rgb(243,210,122)` (2-card). Both right | 27369-27374, 27190 |
| C3 | Torn stub: row `translate(-5px,4px) rotate(-6deg)`, origin `100% 0`, radius `12 2 4 12`; card `rotate(8deg)`, origin `0 100%`, body `translateX(4px)` | 27407, 27226-27227 |
| C4 | `isOutNow`: backlog/playing, or the date's UTC midnight before today's LOCAL midnight | 7343-7346 |
| C5 | Long press 380 ms, cancelled by >10 px vertical; manual → `startDrag`, else `openFull`; `h = height + 7`; `beep("nudge")` + `vibrate(14)`; `beep("swipe")` per new slot; drop numbers only the shown rows `ord = k+1`, `S.sort = "manual"`, `beep("tap")`, redraw with `noenter` | 8968-8990, 7417-7475 |
| C6 | Swipe delete: OPEN 92, clamp `-138..0`, snap open past -46, `.24s cubic-bezier(.2,.9,.25,1)`, trailing click eaten 400 ms, native `confirm("Delete X? Its scores, duels and diary go with it.")`, removes played records / duels / `aiWhy`, `beep("back")`, toast, no undo | 15083-15140 |
| C7 | `qkTap` 320 ms from `e.timeStamp`; `qkTurn` 420 ms `cubic-bezier(.3,0,.15,1)`, `+180deg` both plates, `vibrate(8)`, `dataset.busy` guard; `qkShut` `sfx("qkshut")` + 220 ms fold, instant when calm | 10773-10804, 27248 |
| C8 | Shuffle hold 480 ms (pointer timer, click after hold swallowed); tap pool = out else live, minus `qdLast` unless < 2; filter → All if hidden; `qslit` 2.4 s; toast "Tonight: X" | 8802-8823 |
| C9 | Progress line: played 1, playing/dropped `logged/hrs`, coming `max(.04, 1 - days/182)`, else 0 | 8762-8768 |
| C10 | Boss HP = Σ `+g.hours || 20` over not comp/played/dropped and backlog/playing/out; HP shown only when `S.grum`, else the word "Boss" | 13912-13916, 8787 |
| C11 | Dust: 60/120/240 days, `since` at local noon of `added`/`date`, `sgLast`, `dustOff`; level 3 = 🕸️ emoji | 21061-21071, 21096-21097 |
| C12 | Back's evenings `S.evening || 2` vs `num(S.evening, 4, …)` everywhere else | 8671 vs 8457, 9257… |
| C13 | `lfRule`: ink area 26 % of the slot, min 30 % height, max 86 % height / slot width, 2 % guard, `lum < 50` → `brightness(0) invert(.9)` | 20985-21000 |
| C14 | Rows: folded = `max-width: 500px`; covers 58x86 / 46x68; skew -15 / -9 deg; `--rslot: min(28% / 27%, raw x 1.4)`; date + hours hidden unfolded, date shown folded, ticket hidden folded | 27366-27436 |
| C15 | Start state `collapsed = false, focusId = null` → the top shown game's card is open; tab/sort set `focusId = null` only | 3794-3795, 8863-8864, 9032-9037 |
| C16 | On deck photo cards: 410 high, `min(76%, 300px)`, three share the row from 700 px, `deckShown` filters `onDeckScores` by `queueList` ids, gone under Played | 24517-24533, 24611-24619, 13299-13303 |

## Wrong or misleading (6)

| # | Map says | What the code does |
|---|---|---|
| W1 | 1-list §7: "Tap the open card → `collapsed = true; focusId = null; beep("back")`" | Unreachable for the open card. The capture click handler (10755-10758) sends every tap on `.hflip.qkf` (front and back) to `qkTap` and stops propagation, so the row `onclick` (8992) never sees it: one tap = 320 ms wait, `sfx("qkshut")`, 220 ms fold. `beep("back")` on closing exists only in Character select's `qfTap` (8847, tapping the open game's cover). |
| W2 | 2-card §2.1: "`sfx("qkopen", snap)` plays whenever the card opens (also with motion off)" | Row tap (9001-9003): `h` is false when `calm()`, `S.motion === false` or `S.vt === false`, and then it plays `beep("open")` (Android `Sfx.Open`), not the card's sound. Only Character select's `qfTap` (8852) plays `sfx("qkopen")` whatever calm says. |
| W3 | 1-list §2 (`date` order): "future-or-today dated ascending (0)" | Tier 0 is `new Date(g.date).getTime() >= Date.now()` (7393): the date's UTC midnight still ahead. In UAE a game dated today drops to tier 2 (past, newest first) from 4:00 AM. |
| W4 | 1-list §2: header `.tcount` "N waiting · M played" as part of the screen | Hidden in Clean whenever the header card is on (`html.ui2 .tabbar.shx .tcount { display: none }`, 16687), which is the default. It shows only with the header card off (`S.shxOff`); Android's `PlainTitle` (HeaderCard.kt 267) has neither the count nor the Customise chip. See M1/M9. |
| W5 | 1-list §9: an open Delete closes when "scrolling away" | There's no scroll listener. It closes on the next touchstart anywhere in `#p-queue` outside that row (15095) or on a click (15123). A scroll that starts on the open row leaves it open. |
| W6 | 2-card §3: "going to the back, the back's `[data-count]` numbers are set at once" (plus `countUp`, 8871) | `qkBack` (8665-8697) has no `[data-count]` element at 327: both are dead code. Port nothing. |

# 2. Missing (needed by Phase 1, in neither map)

**M1 · The Queue's Customise (piece 1).** Plan rule 9: "Each screen comes with its own Customise groups and honours every setting it has, from its own phase". 0.md held back the header card's Customise chip "for each screen's phase". 1-list says "Settings is Phase 7", which is only the shell. The chip is `data-settings="games"` in the `.tabbar` (8946). Four groups have `data-scope="games" data-sub="queue"` (6230-6330):
- These still change the v299/v301 looks: Closed games `#s-qlook` (Rows / Character select); Card background `#s-cardbg` (only Plain differs, `qsPic` 8758); On deck show/hide `#s-deck`; platform `#s-curplat` (`curPlat()` defaults to "Switch", 13257); On deck cards `#s-dzx-deck` (New / Classic); row logo `#s-rowart`; size `#s-raw`; strength `#s-rao`; halo `#s-rag` (`html.rowglow`, 17868, which also carries Punch through `--apun`, 1452).
- These do nothing on the v299/v301 looks:
  - `#s-cardlayout`: `html.cl-*` styles `.hero .art`, which `qkFront` doesn't have (1336-1340). Only the size check's key reads it (22125).
  - `#s-bgfade`: `--bgmask` masks only the old `.cardbg`/`cardLayers` (149, 838).
  - `#s-cardbgstr`, and Card background's "With the title" / "Title only".
  - The float knobs `#s-artfix`, `#s-asize`, `#s-ah`, `#s-afloat`, `#s-aspeed`, `#s-aglow`, which style `.hero .art`.
  - `#s-rowtint`: `html.rowtint .row:not(.artrow)` (1406) may show a sliver behind a non-logo row between the strap and the picture. Check it on a parity sheet.
- The `demoCard()` sample in the group: check which look it draws.
- Proposal: port the live ones and the Customise chip on the header card and on PlainTitle. Leave out the dead ones on the precedent of Phase 0 answer 2, and add them to the web's code-check issue as "remove?".

**M2 · The lists sheet (piece 1).** The bar's lists button opens `openLists(null)` (12872-12905). It makes a list (name ≤ 32 characters, emoji from `LIST_EMO`, default "My list"/⭐, `listNew` 12862), renames one, deletes one (`listDrop` 12867 also resets `qFilter` from `L:id` to `all`), and has "show in queue" (`data-lshow` → `S.qFilter = "L:"+id`). It shows the empty state "No lists yet. Make your first one below." 1-list only points at it. The per-game mode `openLists(gid)` belongs to Phase 2. Changes stay in memory (practice copy). The sheet is a `dyBox` sheet, so swipe-down closes it (plan, Oct 26).

**M3 · The back's "Like this" (piece 2).** `simBack` (13570) shows 3 local look-alikes from `simLocal(g, 3)` (13465: TF-IDF over `simTags`), each opening the game page (`data-opg`). Its chips: "More like it" opens the `openSimilar` sheet (13532: library matches with "Show these in the queue" → the `S:` tab, plus RAWG ideas through the helper, read-only) and "Trailer" opens a YouTube search (13577; `sigAdd` writes a signal: keep it in memory). The `S:` tab is reachable from inside the Queue through this, not only from the game page as 1-list says. 2-card left `simLocal` unread. Phase 1 needs `simLocal` (with a golden entry) for the row of 3 and for the `S:` tab's list and count (13566-13567).

**M4 · The back's own picture (piece 2).** `.qbbg` (8669, CSS 27286-27287): the same source as the front, `background-position: 50% 30%`, under `linear-gradient(180deg, mix(accent 26%, rgba(5,7,15,.93)) 0%, rgba(5,7,15,.96) 46%, rgba(5,7,15,.98))`.

**M5 · Ways into the Queue from other screens (piece 1, an API now).**
- "Show in queue" on the game page (9488) and in the peek (10037) sets `focusId = id; collapsed = false`, then `goTab("queue")`.
- Add opens the Queue on the new game (5167-5171).
- `simFilter` (13560), `gtagFilter` (22021) and a list's "show in queue" set `qFilter`, then `goTab`.

Android needs `Queue.show(id)` and `Queue.filter(key)` that later phases call. Opening the card scrolls it into view (v307).

**M6 · Release-day clocks (piece 1, golden).**
- `countdown` (8885) is `ceil((date UTC midnight - now) / day)`, so "Out in N days" changes at 4:00 AM UAE, not at midnight.
- `isOutNow` flips at the local midnight after release day.
- So from 4 AM to midnight on release day a game counts and sits under Coming, while its row and card say "Out now · Ready to play" with a torn ticket.
- Copy this as it is. Add 3:30 AM and 4:30 AM clocks to `tools/golden.mjs` (today: noon, 11:30 PM, 12:30 AM).
- The plan's basics want the "Out in N days" words to roll over while the app is open (the web only changes them on a redraw): recompute at both boundaries.

**M7 · Plan basics that touch the Queue (piece 1/2).**
- Japanese/Arabic titles: a fallback font and right-to-left in rows and straps.
- Overscroll stretch off on the list.
- "Nothing lost when Samsung closes the app": the open card, its side (`flipId`), the scroll spot and the Character-select pick (`qfSel`) come back after a background kill.
- TalkBack labels from the web's aria words: a tab is "<name>, <count>", Shuffle "Shuffle: pick tonight's game (hold for the shuffler)", Boss "Boss: the Shadow of the Backlog, N HP", the lists button "Make a list" / "Manage lists".
- 48 dp hit areas for the 40-44 px tabs and the 34 px back buttons.

**M8 · Quiet starts (piece 1/2).** When the Queue first shows, after a tab or order change, or after Shuffle, the open card appears without its entrance and without a sound: only a row tap or a `qfTap` runs `heroin` + the open sound. Neither map says so.

**M9 · Left out on purpose (parity sheet).** `nudgeBar()` (4361, at the top of the Queue: "N changes since your last backup", Download backup / Later) only exists because browser storage can be lost, and the practice copy writes nothing. The tuck code in `goTab` (4587-4589) has no `<header>` left to act on: dead.

**M10 · Every button has an answer (piece 2/3).** In Phase 1 these lead to later phases: Boss → `bossScreen` (Phase 9), Edit (Phase 2), Diary (later), "More like it" (M3), the On deck cards and long press in a sorted order → `openFull` (the Phase 0 placeholder page). Give each a placeholder, never a dead tap.
- A practice delete that empties the backlog would fire the onSave HP pop-up and CLEAR! (13917-13921, Phase 9): keep it off in Phase 1.
- Competitive in ⋯ swaps `hours` ↔ `hoursWas` (`compSet` 10806), which changes the Boss HP and "to beat": keep both in memory.

**M11 · No Nexi on the folded Queue (piece 3).** `#pnbub` is `display: none` and is shown only at 620 px and wider (2302-2304). On the 412 phone the only Nexi is Home's `.nxh-face` (13319), which is Home's line (Phase 3). So on the folded Queue piece 3 has nothing to show.

**M12 · Web checks that should become Android tests.**

| Web | Android test |
|---|---|
| `queue-rows.mjs` | closed rows one height per width class; strap in the game's colour; cover not squashed or tilted; headline + progress line present; name at least 60 px wide; nothing past the row; ticket only unfolded; folded date under the name |
| `queue-card.mjs` | no button on the front but the box art; strap/logo/kicker/headline present; nothing off the card; ticket clear of the logo; torn only when released; two taps turn and never close; back has Edit, ⋯, controller, 8 buttons, 4 numbers; no back label on two lines |
| `card-turn.mjs` | turn ≤ 450 ms; the card's box and the scroll don't move during the turn; platform logos within the strap's text column, no overlaps, versions on one line; opening plays one sound only, never the old ticks |
| `queue-open-place.mjs` | tap rows at several heights: the opened card's top on screen, not under the tab bar, not moved down |
| `genre-filter.mjs` | `G:` tab named, lit, drawn icon; no "undefined/NaN/null"; bar items don't overlap or cut; rows = the genre's games; On deck follows and is titled "On deck · <genre>" |
| `logo-look.mjs` | logo inside its slot and row, on screen, off the words, not near-black, biggest/smallest < 5x, at least 16 px or 15 % of the row tall |
| `soak-queue.mjs` | a Macrobenchmark journey (open, turn, turn back, close, box art → game page, back, scroll; x10): memory and open time flat (gate 3) |
| `touch-empty.mjs`, `kit.mjs smooth` | nothing changes under a finger on an empty spot; loops keep running mid-swipe |
| `rail-nexi.mjs`, `calm-nexi.mjs`, `nexi-wheel.mjs` | one Nexi per screen; seated he only blinks at rest; calm stops his loops |

- The golden should grow: `isOutNow`, `queueList` + `qCounts` per tab (`L:`/`G:`/`S:` too), `sortGames` per key, `qkStat` per state, `countdown`, `qsProg`, `shortDate`, `lfRule`, `dustDays`/`dustLvl`, `bossNow`, `deckShown`, `simLocal`, `qbAreas` (Nexi's guess), `myScore`.
- The phone-side twins of `lfLook` (26762), "Open queue cards are different sizes" (22117) and "card ended up off the screen" (27817) are Phase 8b ("on-phone layout checks"): only tests in Phase 1.

**M13 · Header card, not counted twice.** The Queue's header card is Phase 0's (`shxDress` dresses `.tabbar`, 16998). Phase 1 adds only the Customise chip (M1), the plain-title count when the header card is off (W4), the view pill (already built), then the bar, On deck and the rows.

# 3. Contradictions to settle

| # | Conflict | Suggested resolution |
|---|---|---|
| X1 | The plan's "interruptible animations (a tap mid-animation reverses it smoothly: card turn …)" vs the web ignoring taps mid-turn (`busy`) and v306 "always the same way, never a swing past and back" | A double tap mid-turn sends the card back from its current angle the way it came: no overshoot and no second half-turn, so v306 holds. Single taps mid-turn do nothing. A tap during the 1.9 s entrance closes it from its current pose (the web lets it close then too). Show it on the phone (owner Q3). |
| X2 | Undo toast after delete/move (plan, Oct 26) vs the web's `confirm()` and no undo | Owner Q1. With undo, the delete keeps a snapshot of the game, its played records, duels and `aiWhy` for the toast's few seconds. Undo after a drag restores every game's old `ord` from a snapshot, not a swap back, because the drop renumbers only the shown rows. |
| X3 | Two state palettes (C2) look like one bug to a porter | Copy both as named tokens (`QueueTokens.row*`, `QueueTokens.card*`, with source lines). Add one bullet to the web code-check issue; don't unify unasked. |
| X4 | Breakpoints differ: rows 500, card 390/620, shell + Nexi 620, On deck 700, Shuffle's word 1100 (all viewport CSS px, rail included) | Use the window width in dp for each one, one constant per breakpoint, never the content width or Compose size classes. Shots at 360/412/500/501/620/859 cover the bands. |
| X5 | Phase 0 put "hiding the rail" in Phase 3 (Shell.kt 386-389), but seated Nexi's one tap is `railHideNow()` → floating Nexi (2-card §6.3) | Bring rail hide + floating Nexi (drag, edge snap, `S.bubPos`/`S.railSide` kept locally) into piece 3, because his seat's one tap needs it. The dice and live queue stay in Phase 3. |
| X6 | "Edits reset when the app reopens" vs "nothing lost when Samsung closes the app"; 1-list keeps `qFilter`/`sort`/`qLook` as local prefs | Practice edits (reorder, delete, lists, Competitive, Play now, Tempted lists) and the open card/side/scroll live in saved instance state: they survive a system kill and clear on a real cold start. Tab, order and look are view choices: local prefs seeded from the backup, kept, never written back. Show the practice line after the first edit. |
| X7 | 2-card §0 trap 3 uses `S.evening || 2`; Shuffler/elsewhere use 4 (C12) | Copy per call site (the back says 2 when unset); one code-check bullet for the web. |
| X8 | W2: with calm, opening from a row plays `Sfx.Open`; Character select plays `CardOpen/CardTear` | Copy as is (parity); note it in the golden of sounds per path. |
| X9 | 1-list asks whether to add drag autoscroll | No autoscroll (the web has none; "copies the app as it is"). Note it as an idea with `learn.mjs add --kind=idea`. |
| X10 | Emoji and old styles left in the Queue: `S:` tab 🔍 (13563), `QD_TAGIC` 16x16 at .45 (22020, not v297's 24x24 at .35), dust 🕸️, Shuffler emoji (moot: the deck takes the Queue card look), Delete in uppercase on literal `#D93A4A` | Draw v297 icons for all of them. List emoji stay (owner data). Delete in sentence case from a token (the gate 6 lint fails otherwise). |
| X11 | The Brief puts "Nexi's seat … Home" in Phase 1; Home is Phase 3 and the folded phone has no Nexi on the Queue (M11) | Piece 3 builds the face once (blink, bubble) and seats it in the rail on every tab, Home included. Home's own face and line come with Phase 3. |

# 4. Owner questions (each sent with a 412 web screenshot first, L212)

1. **Delete:** keep the web's "Delete X? Its scores, duels and diary go with it." question *and* the approved Undo, or delete at once with only Undo? *Suggest Undo only*: one tap fewer, and Undo was approved for exactly this.
2. **Game Shuffler deck:** answered Nov 2 ("yeah queue card is better"): the deck's cards are drawn like the open Queue card (1-brief.md).
3. **Card turn mid-way:** a double tap during the 420 ms turn sends it back the way it came (X1). OK, or keep the web's "taps wait until it lands"? *Suggest sending it back*: it follows your Oct 26 rule without a swing past.
4. **Leave out** On deck's "Classic" cards and the dead Customise controls (M1)? Answered Nov 2 ("do as you like"): yes, the suggestion; the dead ones go to the web's code-check issue.
