Checked at web APP_V 327 (index.html, Nov 17 2026). Read-only: the Brief (`3-brief.md`, every suggestion taken Nov 17), the two maps (`1-home.md`, `2-rail-reel-widgets.md`), the plan's Decisions (Nov 2, 5, 7, 10, 17 and the Phase 3 row), Phase 2's critic for the format, Android `ui/Shell.kt`, `ui/Nexi.kt`, `ui/Pages.kt`, `ui/HeaderCard.kt`, `ui/EditSheet.kt`, `ui/Settings.kt`, `ui/Customise.kt`, `data/Queue.kt`, `data/Rank.kt`, `data/Story.kt`, `data/Page.kt`, `data/Nexi.kt`, `kit/Recipes.kt`, `kit/Buzz.kt`, and index.html by grep/ranges. Line numbers are index.html at 327 unless a file is named.

# 1. Spot checks

## Confirmed (20)

| # | Claim (map) | Where it holds |
|---|---|---|
| C1 | `renderHome` is one live function with six `extend` afters (revealSetup, pnStack, Nexi's line, shxDress on Add, nxPDress, dzPlay); nothing assigns `renderHome =` (1-home §0) | 11250, 11552, 11954, 13328, 17004, 18065, 24631 |
| C2 | Home's `ranked` = live sorted by On deck score (comp −1) desc, then Elo (1500) desc, then hype; `ev = num(S.evening, 4, 1, 12)` | 11258-11274 |
| C3 | Big card: dice only with 2+ live (else a ghost spacer), play button is decoration, the duel `data-qduel` button, "N duel(s)" else "#1 of N" | 11131-11142 |
| C4 | Search: every game (played too), `includes`, sorted by `indexOf`, 6, "No game called that.", Enter opens the first, Esc clears, blur hides after 200 ms | 11228-11246 |
| C5 | Next four = `ranked.slice(1,5)`, `when()` rules, the first row lit, "Add more games and your line-up shows here." | 11146-11151, 11121 |
| C6 | Your queue: 7 SPOT covers, orb, "game(s) waiting", tap = Games tab; Playing now's title chain and first 3 cards; Classic adds the live timer, New doesn't | 11154-11176, 24620 |
| C7 | Donut: R 40, gap 15 only with 2+ segments, `max(.5, share·C − gap)`, "≈ N evenings" / "add hours to games", Plan evenings | 11178-11199 |
| C8 | Next out: `fut` = local midnight after now, else the last dated, "It's out — go play it!", d/h/m/s boxes | 11202-11215 |
| C9 | Countdown: local midnight, d unpadded, 520 ms flip; no flip with calm, `pnRest`, hidden, under a page/sheet; no work while `document.hidden` | 11618-11641 |
| C10 | `dzPlay` kickers ("Competitive" / "Playing · N%" / "Playing now" / genre / "Out now" / "Up next"), the bar only when playing + hours + not comp; New is the default (`dzOn`) | 24608-24629 |
| C11 | Customise: 4 groups, the 18 chips in that order, Yes/No tint, Show/Hide counts, New/Classic (note says "character"), standings 2..10 default 4 | 6168-6230 |
| C12 | Stacks: sideways = tonight, stand, hours, far, year; Your taste shape removed each redraw; cards new since you ordered sort first; "Everything here is hidden. Hold to edit." | 11910-11935 |
| C13 | Hold: 550 ms, 10 px cancels, `vibrate(18)`, the click after swallowed, context menu blocked | 11961-11968 |
| C14 | Stack rows 250+250 folded, one 290 row from 620 (Home's breakpoint is 620, not 760) | 15547, 15553-15575 |
| C15 | `tonight`: live + not upcoming, Elo share ×40, hype ×2.5, +26 playing, +22 fits / +12 within 3 sittings; `analysisAt` default 3 (1..20) | 3651-3670, 3586 |
| C16 | Pick for me: `ranked` minus `S.hintLast`, random of the first `min(3, n)`, fallback `pick`, writes `hintLast`, then `pnReveal` | 11507-11511 |
| C17 | Live strip tiles (play/rel/sale/next/q/bl), cap 9 before the backlog tile, "Nh" the day before, `q` = "#N" of Home's ranked (`pnLastRanked`) | 24307-24328, 11882 |
| C18 | Drift 15 px/s (`RLV_SPEED`), finger down pauses, 8 px drag scrubs both ways, a drag never taps, resumes 2 s after; refresh each minute | 24370-24400 |
| C19 | Reel off: `S.reelOff`, `S.motion === false`, reduced motion; tests skip it unless `gq:reel`; `LEN = 15`; music gain .75, picture waits 60 ms + output latency | 8139-8172, 2931 |
| C20 | Reel covers: playing first, then `sortGames(qWaiting().filter(isOutNow))`, then any; real pictures only; 9; slot order `[4,0,2,6,8,1,3,5,7]`; the tour waits 800 ms while the reel is up | 8183-8193, 26870 |

## Wrong or misleading (9)

| # | Map says | What the code does |
|---|---|---|
| W1 | 1-home §2 + Q1: the line is "`<Name>, p`lay **X** next (why)." and Nexi's face starts a duel (Phase 6) | `extend("nxHomeLine", map)` (13747-13752) rewrites the line in the chosen voice (default hype: "Play **X** next!"; butler, coach, rival, bard, nerd as 2-rail §4 lists) and swaps `data-qduel` for `data-nxchat` "Chat with Nexi": **the face opens his chat (Phase 8b)**. 2-rail §4 had it right. |
| W2 | 2-rail §1 (and the Brief's piece 4): the "Not sure? I'll pick one" pill shows when 2+ games, fixed bottom-right, works from every tab | `#hintbub` lives inside `#p-home`; `.panel { display: none }` (122) hides it on every other tab and 2204 hides it on Home: **it is never seen**. It survives only as the rail dice's click target (11866). Android: no pill. |
| W3 | 2-rail §1: the dice's `ranked` "is `Rank.onDeck` ordering on Android" | Home's `ranked` keeps competitive games (−1, at the end) and breaks ties by Elo then hype; `onDeck` drops competitive games and ties by key order. With under 3 non-competitive games the dice can pick a competitive one. Build `Home.ranked` once (X1). |
| W4 | 2-rail §3: "If everything fits (`n < 2`) it doesn't move" | `n = max(2, ceil(view/copy) + 1)` whenever there are tiles and it isn't calm (24355): **it drifts even when every tile fits**. Still only with calm, the rail hidden, or no tiles. |
| W5 | 2-rail §4: "Your top 3" = the duel ranking, "unplayed only? verify" | `rankTable` pools **played, scored, non-competitive** games (13047): "Your top 3" is your favourite finished games, not a list to play. See (a). |
| W6 | 1-home §8: `S.nxHomeOff` is a switch in Settings › Extra features | Read once (13329), written nowhere: no control exists. Honour the backup value; add no switch. |
| W7 | 1-home §7: `beep("open")` on the ticket too | Every `data-home-open` (big card, rows, pills, search, ticket's Open game) goes through `el.onclick` with **no** sound (11516); `beep("open")` only on `data-home-tab`, `data-dxtab`, the month bars; `beep("tap")` on Tonight's chips. |
| W8 | 1-home §5: "Next out (N)" always shown | Only when one or more are still ahead (`fut.length`, 11207); never behind the counts switch, right. |
| W9 | 2-rail §3: backlog "1.2k h"; 1-home §0: `dxWire` 11223 | "1.2kh" (24326, no space); `dxWire` is 11222. |

## The mappers' open points, settled
- **(a) Top 3 widget.** "Your top 3" on Home is finished favourites (W5): wrong for a "what next" widget. The owner was shown "a bigger one with the Queue's first three", and the Queue's first three are On deck's three (its own Customise calls them "your top 3", 6232). Use `Queue.deck("all", 3)`: #1 is the Play next widget's game, so the two agree. No tier badges (they belong to played games).
- **(b) Which list where.** `rk.onDeck(1)[0]`: Nexi's line and the Play next widget (and the rail's `next` tile). Home's `ranked`: the big card, the next four, the queue covers, Playing now's fill, the dice, Standings, the rail's `q` tiles. `rk.ranked.take(3)`: the "Your top N" pills only. `sortGames` (the Queue's own order): the reel's covers.
- **(c) Evening.** Both are right for their readers: `S.evening || 2` is the card back and the Settings display (8671, 6188); every "evenings"/Tonight/forecast count uses 4 (1..12) (16 places). Android already inlines 4 in `Page.kt` 76, `Story.kt` 100, `Shuffler.kt` 44: add one `Library.evening` (4, 1..12) in data/, use it there and on Home; keep `Queue.evening` for the back.
- **(d) Breakpoint.** 620 (C14) = Android `RAIL_FROM` (Shell.kt 104). 760 is other screens' rule.
- **(e) Reel with no games.** `pnreelArt` returns with an empty list (8193): the reel plays on colour cards, the page card says "This week" with 38 h (3133). Same on Android, restore screen included. No question.
- **(f) Others.** Face tap (1-home Q1): one line "Nexi's chat comes in the Nexi update" (the Brief's 4a rule). Dice before the reveal (Q2): the build order below lands the dice in piece 1 going straight to the game (the calm path), the reveal right after. "Where your hours go" (Q3): no switch, as the web. Rail dice with under 2 games: dimmed, does nothing. `etaOf` = `Forecast.of` (Story.kt 75, `Page.eta` 177) and `loggedOf` = `Story.logged` (67): both exist.

# 2. Missing (in neither map)
- **M1 · Nexi's voice.** Port the six persona lines (`NX_PERSONAS` 13334, 13749-13750) into data/Nexi.kt now: Home's line (piece 1) and the Nexi widget share them; Phase 8a only adds the picker. `S.nxPersona` default hype.
- **M2 · A Phase 2 gap.** Edit › Scores › Played shows `relationship(g, rec)` as a note under "What got you about it" (5482-5485); Android's Edit skips it (EditSheet.kt 733-735). Port `relationship` to data/ and add the line with the piece that brings the swing card.
- **M3 · Shared analytics (data/, not ui/).** New file data/Taste.kt: `hypeBias`, `categoryPull`, `genrePattern` (out of `Queue.hypeRows`), `expectedInfo` (extend `Queue.expected`), `analysisReady`, `relationship`; Phase 5's Played player read (`playerRead` 10429) and Phase 6's Insights type (`ixType` 18477) read the same. data/Year.kt: `wrData`, `sessStats` (Phase 5's wrapped/recap 12728 and timer). `genreChart` (Played 10651, Insights 18625). `tonight` is Home-only. Golden tests against the web for each.
- **M4 · "Which update" words.** `SOON` (Pages.kt 80) has no key for wrapped/recaps, Taste (Home's taps go to the Taste tab), Nexi's chat or the calendar month bar: add `wrapped`, `taste`, `chat`; the bars use `month`.
- **M5 · TalkBack.** Web labels to copy: "Search your games", "Shuffle your games", "Customise Home", "I'll pick one", "Chat with Nexi", the big card as a button, "Pick one for me" (rail), the reel's play button; the strip's repeated copies hidden from TalkBack (`aria-hidden`, 24332); stack dots as "Card N of M".
- **M6 · What's new.** Each piece's `whatsnew.txt` block points at its part; the web's own targets show which: big card, search, orb, a stack, the wrapped card, Shuffle, Nexi's line, Playing now row, the rail strip (15183-15228, 16236, 24404, 24684).
- **M7 · The reel's switch has no home yet.** Android Settings is the interim sheet (Settings.kt 88, until Phase 7) with no "This build" group: put "Opening reel: Every launch / Off" there.
- **M8 · Search text.** The web loses what you typed on any redraw (innerHTML); Android keeps it in state (don't copy the wipe).
- **M9 · Not Home.** Two of the six layers aren't Home's: `shxDress` dresses Add (17004), `nxPDress` Nexi's persona accessory (18060, Phase 8a). `HeaderCard.kt` is the screens' header card, not on Home.

# 3. Contradictions to settle
- **X1 · One ranking per job.** 2-rail §1/§4 vs 1-home §1: settled in (b). Build `Home.ranked` in data/ (never re-sort in UI).
- **X2 · The pill.** The Brief's piece 4 names the bubble; it never shows on the web (W2): not built. The Brief's "Try: tap the dice three times" stands.
- **X3 · The Nexi widget's tap.** Plan's Phase 3 row (ANDROID-PLAN.md 180) says "a tap opens chat"; the Brief (newer, answered) says it opens the app until 8b. Fix the plan row.
- **X4 · Build count.** The plan row says 4 build chats; the pieces below are 8. Update the row.
- **X5 · Customise wording.** The Playing now note promises "its character standing in the card"; the figure isn't ported (Nov 10): Android's note drops that clause.
- **X6 · 2-rail's Top 3 row** names `Rank.ranked` + tiers, its own question assumes On deck: settled in (a), On deck, no tiers.

# 4. Owner questions
None. Every open point is settled by the code, the Brief's answers or the house rules.

# 5. Build order (each its own Android release; read only the sections named)

| # | Build chat (est.) | Does | Read |
|---|---|---|---|
| 1 | Home: data + top (2-3 h) | `Home` data (ranked, pick, live, dated, fut, hours, `Library.evening`), Nexi's line in his voice + "Your top N" pills (face tap = one "chat" line), tabs + counts, search, Shuffle, Customise `home` (all 4 groups), big card (dice picks and opens the game straight away), next four, empty states, `SOON` keys, entrance + calm, TalkBack | 1-home §0-4, §8-10; 2-rail §1 "Who calls it", "How it picks"; this file (b), (c), W1, W3, W6, M1, M4, M5, M8 |
| 2 | Pick for me: the reveal (1.5-2 h) | overlay, slashes, tag, name, shake, 8 shards, the 6 sounds + buzz, calm = straight to the page, `pick:last` in Store | 2-rail §0 (reveal rows), §1; W2, X2 |
| 3 | The middle tiles (2-3 h) | Your queue orb, Playing now New + Classic, Your statistic donut, Next out ticket + one-second countdown, grid folded/unfolded | 1-home §5, §9, §10; W7, W8, X5 |
| 4 | More for you: stacks + plain cards (2-3 h) | the two pagers + dots, hold + Edit this stack, `.dxbg` pictures, Duels, Standings, Settle a duel, Needs a verdict, Year, Where your hours go (`genreChart`), Play sessions, year/month cards (`wrData`, `sessStats` in data/Year.kt), their "which update" lines | 1-home §6 (all but the taste rows), §7; M3 (Year) |
| 5 | More for you: the taste cards (2-3 h) | data/Taste.kt (`hypeBias`, `categoryPull`, `genrePattern`, `expectedInfo`, `analysisReady`, `relationship`) with golden tests; Your read, What decides it, Tonight (+ chips), Hype trap, Furthest, Genre lean, the swing card; Edit's relationship note | 1-home §6 rows read/cat/tonight/trap/swing/genre; M2, M3 (Taste) |
| 6 | The rail: live strip + dice (2-3 h) | tiles from data (`Forecast`, prices, On deck, Home.ranked), the endless drift, hold/scrub, calm, minute refresh, LIVE label, the dice (dimmed under 2), `roomy` | 2-rail §3, §1 (dice); W4, (f) |
| 7 | The opening reel (2-3 h, music can ride along or follow in ~1 h) | `frame()` on a Canvas, wait/tap/skip/exit, covers, cold launch only, Off row in the interim Settings, calm off, the music render + sync | 2-rail §2; (e), M7 |
| 8 | Widgets (2-3 h) | Glance + one bitmap per face, Play next / Countdown / Nexi (persona line) / Top 3 (On deck's three), deep links, backup / midnight / release-day updates, cover screen if allowed | 2-rail §4; (a), M1, X3 |

Order: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 (3 can swap with 2; 6 needs 1's `Home.ranked`; 8 needs 1's persona lines). Then the Perfect check in a fresh chat and "Approve Phase 3 on your phone".
