Checked at web APP_V 327 (index.html, Nov 10 2026). Read-only: the two maps (`1-page.md`, `2-edit.md`), the Brief (`2-brief.md`), the plan's Decisions (Nov 10), "Nothing forgotten" and "Your games come with you", Phase 1's critic for the format, `ui/GamePage.kt`, `ui/Queue.kt`, `net/Routes.kt`, and index.html by grep/ranges. Line numbers are index.html at 327 unless a file is named.

# 1. Spot checks

## Confirmed (22)

| # | Claim (map) | Where it holds |
|---|---|---|
| C1 | `openFull` → `hubMarkup` (`fullStyleOf()` "dash" returns `gdMarkup`) → the live `gdMarkup = function` at 22054; 17156 is a dead declaration; the only extend is the sticky bar's `map` (both maps) | 9767, 9503, 22054, 26143 |
| C2 | Kicker: played "Finished · Mon YYYY", dropped "Dropped", else `days()` = "N day(s) away" / "Out today" / "Out now", no date "No date yet"; `ceil((new Date(d) - now)/864e5)` (UTC midnight) (1-page §2) | 7314-7318, 22065 |
| C3 | Four tiles: TBA/"no date yet"; this year "D Mon" + release/released, else the year + "D Mon"; `h + "h"` / "N evenings" always plural, `S.evening` default 4; score chain your score → competitive → likely score → critics → not scored | 21936-21947 |
| C4 | `gdNext`'s six branches and labels (comp "Leave a note"; played Score/⭐score + soft Duel; playing "I finished it"; dropped "Pick it up again"; backlog "Start playing"; upcoming "Watch the trailer") | 22045-22052 |
| C5 | ⋯ order = `hubActs` minus Edit, minus what the main row used, minus Score/Duel without a played record; Prices always present | 9482-9499, 22058-22060 |
| C6 | Score sheet: 11 wholes on top, 10 halves "N½" between them (22-column grid), the caption, "Don't remember", closes after 260 ms, redraw at 280 ms, rank toast at 3000 ms (400 calm); it is ONE number | 13142-13175 |
| C7 | `scoreWrite`: dk → real 0 + dk 1 − z; real 0 → z 1; `scored()` | 13164-13168, 3504 |
| C8 | `fnDone`: state played, record date local en-CA, page redraw, Score sheet 1200 ms later; `syncPlayed`'s own record date is UTC ISO (so Score-it-on-an-unplayed-game dates by UTC) | 20359-20366, 3552-3556 |
| C9 | Live swipe: axis chosen at 10 px with 1.2×, `1 - .12p` / `1 - .45p`, neighbour `.88 + .12p` / `.55 + .45p`, commit `> .22 W` or `> 30 px` at `> .45 px/ms`, 360 ms settle, `vibrate(9)` with `S.buzz`; stepped fallback 42 px / 55 px; order `sortGames(allGames())` | 9885-9997, 9800 |
| C10 | Grow 300 ms scale .93 / opacity .35 from the tap point, shrink 220 ms to .9 with `cubic-bezier(.4,0,1,1)`, fill forwards cancelled on every path; cover flight 380 ms, keyframes to 1.06/-6° at .72 then -4°, skipped for calm / hubAnim off / tour / a page already on | 11820-11847, 21264-21295 |
| C11 | Where was I: 240 chars, `WI_MAX` 20, `wiAgo` words, "Welcome back" after 2 days | 19274-19296 |
| C12 | Finish forecast pace order (your pace after 3 days → daily plan → `ev × 4/7`), `pace > .05`; clash = hype ≥ 7, dated, not TBA, ≤ 60 days, before the forecast; the ≤ 5 h/day fix line | 20774-20797 |
| C13 | Drifting: ≥ 3 play days, `gapNow ≥ 14` → none, `max(4, 2.2 × typ)`, gaps ≥ 1.8 × typ, drop point within .15, look-alike Jaccard ≥ .34 | 23969-23990 |
| C14 | Edit has 15 groups: Game 6, Scores 4 (Played only with a record), Look 5 (incl. Hub page figure) | 5385-5560 |
| C15 | Edit's tab chips are Game, Look, Scores; the panels sit Game, Scores, Look in the markup (2-edit §1) | 5378, panels after |
| C16 | One group open at a time ("‹ Back to all" is a CSS `::before` on the open group) | 15799-15814, 15855 |
| C17 | `editSums` "N of 4 rated" with 8 categories (a web quirk) | 5186, 5201 |
| C18 | Play style is `S.playStyle`, Settings › Plan › Your evening; Edit only prints the note under the hours | 6478-6484, 5401 |
| C19 | "Move back to the queue" and "Remove" act at once with no confirm and no Save; Rate mode writes `S.rateMode` at once | 5849-5853, 6010-6013, 5705-5708 |
| C20 | Reviews window `-RV_SOON..RV_MAX` = -3..+60 days; `PX_GAP` 12 h, `PX_RELOOK` 14 d; `/hours` sends `src:"hltb"`; `simLocal` weight `ln((N+1)/(df+.5))`, keep `> .22`; `COVER_W` 320/720/1100; `PALETTE` 32 | 20431-20434, 19421, 23385, 13465-13475, 4930, 3783 |
| C21 | `gtagFilter`: one `G:` tab, toast "N games tagged X · tap All…", count by `gdGenreSet` (genres + Steam tags) | 22021-22029, 21991 |
| C22 | Calling card 1080x1920, title seed `reduce(a*31+code)` from 7, the three notes, signature fallback "The Play Next crew" | 8238-8320 |

## Wrong or misleading (10)

| # | Map says | What the code does |
|---|---|---|
| W1 | 1-page §6: Prices is "inserted just before Where was I? … played and competitive games" | For a competitive game Where was I is on the main row, so `findIndex` is -1 and `Math.max(0, -1)` puts **Prices first** in the drawer (22061). Played games: before Where was I, as said. |
| W2 | 1-page §9: the sticky bar shows at `scroll >= heroHeight - 56dp - statusBar` | `.gdsb` is inserted after `.gd-hero` (26148), `top: 0`, so it sticks when its own top (the hero's foot) reaches the top: `scroll >= heroHeight`. The -56 px margin only pulls the content below it up (26115). |
| W3 | 1-page §1.1: the lit page dot is "in the game's colour" | `#hubdots` is appended to `body` (9436), outside `.hub`'s `--accent`; `var(--accent, var(--volt))` (696) resolves to **volt**. |
| W4 | 1-page §2.3: the length tile is tappable "only when that card applies" | `extend("gdGlance")` (23443-23446) makes it a button for **every non-competitive game**; with `S.hbOff` the sheet opens and says hours are off. |
| W5 | 1-page §5.7: "Start it" sets "`g.dwBack` = this game"; "after any answer the card is removed" | `drifting.dwBack = shortGame.id` (24021), read back by the `syncPlayed` hook when the short one is finished (23945-23948). "I'm into it" writes `S.dwAsked`/`g.sgOk`, but `dwRisk` (23969) reads neither: the card is gone only from the open page and is **back on the next open**. Copy it (tests), or ask; don't "fix" silently. |
| W6 | 1-page §5.2: after Reset scores the game "reads as Played, not scored yet" | `wipe` sets `real = 0` and leaves `z` (14996): a game scored a real **0** (`z: 1`) stays scored 0; only `dk` and the rest read as unscored. |
| W7 | 2-edit §2 / §9: the release date on Android is "a Material date picker dialog" | The house style (`.claude/skills/play-next-design/SKILL.md` 43, both apps): "No native date or time inputs (v180): use the app's own picker". Build our own (see a/b below). |
| W8 | 2-edit §7: page sections are "About, Reviews, Prices, … Your progress" | `gdMarkup` (22084-22087): Your progress, About, Screenshots, then the Reviews/Prices pair. 1-page §2.6 is right. |
| W9 | 2-edit §4: Logo "Which side / Width / Height / Across / Up", Backdrop "push", "how loud on the card", "Follow the app setting" as page controls | They move only Edit's own preview: `artBlock` (8707) and `cardLayers` (8692) are called only by `paintPrev` (5633-5635), which draws the pre-v299 card. The page logo (`.gd-logo`), the v290 row slot and the open card ignore them. Live in Look: cover/backdrop pictures, colour, cover framing (`coverPos`, Queue card), backdrop framing `bgX/bgY` (page), backdrop off. See Q2. |
| W10 | 1-page line numbers | `gdTags` 21999 (not 22013), `gtagFilter` 22021 (22036), `gdNext` 22045 (22046), `scoreWrite` 13164 (13171), `qScoreSet` 13169 (13176), `wiSheet` 19297 (19298). 2-edit's are right; `simLocal` is 13465 (2-edit says 13462). |

## The mappers' open points, settled
- **(a) 16 groups.** 15 at 327, and 15 at the plan's own commit (76f3bd9, the shallow root): the "16" was a miscount. Nothing extends `openEdit` with a group (12974, 15816, 20582, 23514, 27151 add a count, a hint, an HLTB line, a rail reset). The nearest 16th is the page's own Customise group "A game's own page" (6403), a Settings group (M1). Android: **14** (no Hub page figure), 13 on a game without a played record. Plan fix: "Edit has 15 groups (14 without the hub figure)".
- **(b) Release date.** Really a native `<input type="date">` (5392). v180 (docs/LOG.md line 28) replaced only the finish date ("could not change it on the Fold") and said to avoid native date inputs for new UI. Android draws its own: the `finPick` look (year ‹ ›, 12 months) plus a day grid, a Clear button, the "No date — shows as TBA" line, the tba/confirmed rules of 2-edit §2. No question needed: the house rule decides.
- **(c) Tabs.** Game | Look | Scores confirmed (C15). Copy the chip order.
- **(d) Play style.** Not an Edit control (C18). Plan's Phase 2 bullet should read "the play-style note under the hours (the switch is Phase 5's, Settings › Your evening)"; Phase 5 already owns the switch. Android reads `S.playStyle` from the backup for `hrsOf`, the note and the HLTB sheet's highlight.
- **(e) Score it.** Confirmed one number (C6). The 8 areas are Edit › Scores › My rating (Simple/Advanced, `g.mine`/`g.subs`) and the card back shows them. So the Brief's piece 3 "your score and the 8 areas" splits: the sheet in piece 3, the 8 areas with Edit in piece 2 (X1).
- **(f) Page dots.** Not in the Brief or "Nothing forgotten". 9 px per game in one unwrapped row: past ~44 games it runs off a 412 screen both sides; the lit dot is volt (W3). Ask (Q1), suggested: leave out.
- **(g) Duel.** `data-qduel` opens `qDuel` (13025, 13103), Phase 6. No button that does nothing: hide the soft Duel button until Phase 6 and let Score take the row (Q3).
- **(h) Drifting.** `dwDays` (23952-23958) reads `S.sessions`, `g.lgH`, `g.wasI` and `g.plAt`, all already in the backup (the web writes them). Phase 2 ports `dwRisk` as a pure function over the backup plus the practice copy's new notes; no Phase 5 data is needed to show it. Its Park / Start it / I'm into it are practice writes; Nexi's daily ask (`dwCheck`) stays Phase 9. Golden tests for each sign.

# 2. Missing (in neither map)

**M1 · The page's own Customise (piece 1).** Plan rule 9: each screen brings its Customise. "A game's own page" (6403-6454, scope games, sub "queue lib", so it shows in the Queue's and the Library's Customise) has two live controls for New: **Motion on this page** `#s-hubanim` and **Platforms** Logos/Names `#s-plattext`. The rest (Wallpaper, the figure's shadow and glow, Giant title, Style, Character art on the New page) is Showcase/figure only: not ported. Android's Queue Customise (android-v12, `ui/Customise.kt`) doesn't have the group yet. Note the switch's promise, "Off freezes everything on a game's page": the web only kills CSS motion there (`#full.stillhub`, 1510) and the live swipe/flight, while the WAAPI grow, drawer, Read more, tag unfold and track draw still run (X4).

**M2 · The finish moment (piece 3).** `syncPlayed` to played fires, 150 ms later, `pnStampShow` (3545: "PLAYED ✓" + title, the page shakes ±6 px over 240 ms at 330 ms, `vibrate([30,40,20])` without checking `S.buzz`, gone at 1.7 s) and `celebrate` (11791 via the hook at 11802: 28 bits in the game's colour, ~1.9 s). Both are skipped when calm. Reached from "I finished it", Score it on an unplayed game, Edit's state → Played. The plan puts "celebrations" in 8a (Q4).

**M3 · Your progress draws itself (piece 1, v271).** When a game moves on (Start playing, I finished it, a drop) and its page redraws or opens again in the same session, it runs the line from the old step to the new one (`scaleX` over `300 + 140 × steps` ms, `cubic-bezier(.65,0,.35,1)`), each new dot pops (`scale .4 → 1.3 → 1`, 380 ms, `cubic-bezier(.34,1.4,.64,1)`), a drop pops the last dot. Off when calm. `seen` is per session (25765-25795).

**M4 · Buy advice and "I own it" in the Prices card (piece 4 / Phase 9).** `extend("pxCard")` (23884-23889) adds `smLine` (v249: "Buy now / Wait for the sale / No rush…", `smBuy` 23844, gated by Extra features `#s-xbt` 6658) and replaces the card for an owned game (`g.own`) with "✓ You own it · no price checks. Tap if you don't."; `pxItem` stops pricing owned games. The plan's Phase 9 lists buy advice / shopping plan. Suggested split: Phase 2 honours `g.own` (the line, no price calls); the advice line, the "I own it" button and the Shopping plan sheet (`smPlan`) wait for Phase 9. 2-edit §7b should say so.

**M5 · Screenshots are fetched (piece 1 strip, piece 4 fetch).** `shotsFetch` (22604-22621): 700 ms after a New page opens, and when `gdShots` draws a game with none (22758, so the swipe neighbours too): `POST /art` (`artStores`) + RAWG `games/<rawgId>/screenshots?page_size=6`, `gpPickShots`, up to 5, `g.shotsAt` / `g.shotsNo`, again after 30 days. Read-only routes, allowed now; keep the answers in memory. 1-page's "show nothing until `g.shots` exists" would leave every unfetched game without a strip. The daily sweep (22624-22628) and "a game you add" (22796) are Phase 9 / Phase 4.

**M6 · Edit's chrome (piece 2).** `calmSheet(false)` after `openEdit` (15753-15770, 15816): each group gets its drawn icon `gIcon(title)` (CLAUDE.md v297: Edit groups use `gIcon`; draw any missing in the v247 style), and a group with more than 7 direct label/h5 children shows 5 and folds the rest behind "More options (N)" / "Fewer options". Count which Edit groups fold on a 412 run before building.

**M7 · One practice copy for every screen (piece 1, before the first write).** Phase 1's practice state lives in the Queue (`ui/Queue.kt` 189-320: deletes and order only). Phase 2 writes `g.*`, `S.played`, `S.duels` (reset), `S.aiWhy`, `S.dwAsked` and the caches (`g.rv/px/hl/shots/wiStory`), and the Brief's tries need them to show everywhere at once (tiles, Queue rows and headline, card back, header card numbers, the swipe order). Build one in-memory overlay over the backup that every screen reads, fold Phase 1's deletes and order into it, and add the plan's thin line "Practice copy: changes reset when the app reopens" (Your games come with you; no such line in `android/` yet).

**M8 · The Queue card back's Edit and ⋯ (piece 2).** The plan (Next step) says they wait for Phase 2; neither map covers the back's ⋯ (`qkBack` 8680-8681): Diary (Phase 5) and **Competitive** (`data-comp` → `compSet(g, !g.comp)`, toast "<title> is competitive — immune ∞" / "<title> counts again", 10815-10818). Suggested: Edit and the Competitive toggle come with piece 2 as practice writes; competitive's rules on every screen stay Phase 5.

**M9 · Back on Android (piece 1).** `GamePage.kt` uses `BackHandler` (136); the manifest has `enableOnBackInvokedCallback="true"`. Order: open sheet (Score, Where was I, Lists, Similar, Prices, HLTB, Why, the calling-card overlay, the screenshot viewer) → the open Edit group (Back to all) → Edit (back to the page, `hubReturn`) → the ⋯ drawer → the page. Suggested: `PredictiveBackHandler` on the page whose progress drives the same 1 → .9 / 1 → 0 shrink (transform/opacity only), a cancelled gesture springs back, calm = plain close.

**M10 · The placeholder's numbers vs the web (piece 1, 412 and 859).** `GamePage.kt` 187-189: body `widthIn(max = 880.dp)`, `offset(y = -56.dp)`, side padding 20; web `.gd-body` (17098, 17143): `max-width 980px`, `margin-top -30px`, padding 16 folded / 28 from 760 px, bottom 90, rail 104 right / 110 left (17149-17150). The hero's fade is `.gd-hero::after` (17091: top shade + accent radial), not a plain void gradient; the kicker is `--accent` with `#04060E` words and date words (1-page §9 says fix it). Fold/unfold mid-page keeps the game, the scroll and an open sheet (Phase 0 rule).

**M11 · The cover flight onto the real page (piece 1).** Phase 0's `FlyingCover` measures the page cover after one frame (`at[2]`). On the rebuilt page the cover sits in a pager page: target only the current page's cover, never a neighbour's, never on a swipe or a page already open (`opened`), and keep the page cover hidden until it lands (web fallback 1200 ms, 21267).

**M12 · The web's page checks become tests (piece 1).** "A number in a tile is cut off" (22037-22043), "The game page swiped behind an open sheet" (22650-22653), "A game page never got its screenshots" (22786-22790), `lagWatch` "Opening/Closing a game page" (24184-24185). Phase 8b brings on-phone checks; Phase 2 adds Compose tests: tiles never clip at 360/412/859 (text scales down), a sheet blocks the pager, the strip fills after a fetch; JankStats covers the open/close.

# 3. Contradictions to settle

| # | Conflict | Suggested resolution |
|---|---|---|
| X1 | Brief piece 3 "Score it (your score and the 8 areas)" vs 1-page (one number) and 2-edit (8 areas in Edit, piece 2) | Piece 2 builds My rating (8 areas, Simple/Advanced) inside Edit; piece 3 builds the one-number sheet. The Brief's tries still hold. |
| X2 | 1-page §9 "the one in-memory overlay Phase 1 uses" vs 2-edit §9 "one `GameDraft` … applied to the in-memory game list" | Neither exists as a shared store yet: M7. Edit's draft stays local to Edit until Save, then writes into the shared overlay. |
| X3 | Edit's Remove (no confirm; removes the record but leaves duels and `aiWhy`) vs Phase 1's Android delete (practice, with Undo, android-v17; the web's swipe delete also drops duels and `aiWhy`, 15083-15140) | Edit's Remove calls the same practice delete with its Undo toast, no confirm (the web never asks). Add the orphaned duels/`aiWhy` to the web code-check issue. |
| X4 | Customise's "Motion on this page: Off freezes everything" vs the web (CSS only, M1) vs Android's `GamePage` (grows even with it off) | Android does what the switch says: off = the page opens, closes, unfolds and swipes without motion (as calm), only on the page. |
| X5 | 2-edit §9(c) copies "Move back to the queue" as is, but it leaves `g.state = "played"` with no record (5849-5853): the page's state chip and Edit's Where it stands then say "Played it" on a game that reads as unplayed | Android also sets the state (out → backlog, future date → upcoming) and the web gets a code-check bullet. Copy the toast and the jump to the Queue. |
| X6 | Section order (W8) and the length tile (W4) differ between the maps | 1-page's order; 2-edit's tile rule (every non-competitive game). |

# 4. Owner questions (each sent with a 412 web screenshot first, L212)

1. **The dots under the page** (one per game, the current one longer): bring them? Suggested: leave them out; with your library they run off the screen and the swipe already shows the next game.
2. **Edit's preview-only sliders** (logo side, width, height, across, up; backdrop push and "how loud on the card"): they only move Edit's own small preview of an old card look, nothing you see elsewhere. Suggested: leave them out and make Look's preview the real top of the game's page (backdrop, logo, cover); the web keeps them.
3. **Duel on a finished game's page** before Duels arrive (Phase 6): Suggested: hide it until then, so the page never has a button that does nothing.
4. **The "PLAYED ✓" stamp and the burst** when you finish a game: with the game page now, or with Nexi's celebrations in Phase 8a? Suggested: now (they're the moment "I finished it" lands); Nexi's words wait for 8a.

# 5. Build order (the Brief's 5 pieces)

1. **The page** ← 1-page §0-4, §7-9; M7 (the shared practice copy + its line, first), M1 (Customise group), M3 (track draw), M9 (back), M10 (numbers), M11 (flight), M12 (tests), the strip from the backup's `g.shots`; 2-edit §8 (`Queue.filter` with the `G:` tab for a genre tap, `Queue.show`). The main row's Start/Finish write here; "I finished it" opens the Score sheet once piece 3 lands (until then it only marks played). Duel per Q3.
2. **Edit** ← 2-edit §1-6, §9; (a)-(d) above, M6, M8, X3, X5, Q2; My rating's 8 areas (X1).
3. **Score and notes** ← 1-page §5, split Nov 12: **3a** the Score sheet, M2 per Q4 (the finish moment), Where was I and Then & now's snapshot; **3b** `/recap`, forecast, the Then & now card, Drifting per (h). Reset scores moves to piece 5 with the ⋯ drawer.
4. **From the helper** ← 2-edit §7 (Reviews, Prices with `g.own` per M4, HLTB, Why, Similar + `S:` tab, Trailer, the card back's "Like this"), M5 (screenshot fetch). Every route it uses is already allowed in `net/Routes.kt` (`/reviews /hours /prices /steam /similar /why /art /recap /fx /sgdb/`); RAWG is a GET.
5. **The ⋯ menu and the rest** ← 1-page §6 (Show in queue, Lists per game, Calling card, Trailer intent, Diary/Sessions entries hidden unless `S.diaryOn`/`S.timerOn`, their screens Phase 5), W1's drawer order.

Move: the screenshot fetch from piece 1 to piece 4 (helper), keep the strip in piece 1; "Reset scores" moves to piece 5 with the ⋯ drawer it opens from (Nov 12; the Brief had it in piece 3).
