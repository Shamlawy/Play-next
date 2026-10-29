# Perfect run · fix helper "numbers" (I, T midnight, O header card, the 320 and font-size fits)

Branch `worktree-agent-a52fa782161d46a29`, code head `4995b70` (claude/android-v10-fixes 62fe8d2 merged in); this file is the commit after it. Not merged, no PR.

Files touched: data/Rank.kt, data/Shx.kt, data/Games.kt (build, State.Ready, new day code; one-line calls in start, onOpen, forget; the two `State.Ready(lib, t)` sites now `ready(lib, t)`), ui/HeaderCard.kt. Outside my files: ui/Pages.kt, one line (`HeaderCard(..., ready.cards)`). New tests: data/HeaderNumbersTest, data/DayTest, ui/HeaderCardTest.

## I · header numbers off Main, one Rank (speed 2)
- Rank: Played and Duel read their fields once (vals, not JSON getters); games and played records are looked up by id (first wins, as the old search); the rank table's 90 rounds run on plain arrays (same sums in the same order: a game listed twice still moves twice); the two Regexes are compiled once; `playStyle` read once.
- `Shx.all(lib, now, zone)` works out all seven cards with one shared Rank (the table only when a card reads it; `Shx.of` stays for one card). `Shx.Cards` holds them.
- Games.build() makes them with your games (it runs on IO in openLocal and on Default in fetch, so never on Main, the restore screen's path included), and `State.Ready.cards` carries them. HeaderCard draws from `cards`; only a Ready made by hand (the tests) has none and works them out once per copy and day as before.
- Proof: `HeaderNumbersTest.sameNumbersAsBefore`: a seeded made-up library at 60/200/400 games (40/300/1000 duels) at noon, 11:30 PM and 12:30 AM Dubai: the digest of every card (covers, numbers, line, genres, mix bar, chart, backdrop) equals the old code's (recorded on the old code first), for `Shx.all` and `Shx.of` alike. GoldenTest.headerCardsMatchAtEveryClock passes. JVM time for all seven at 400/1000: 108-361 ms before, 13-17 ms after (200/300: 62-141 → 7-24 ms).
- `HeaderCardTest.drawingTheCardsDoesNoMaths`: a counter in Shx (`Shx.built`) doesn't move while every card's screen is drawn (412, all seven); its control `withoutTheNumbersDrawingDoesTheMaths` shows the old way (no cards) does move it.

## T · midnight rollover (basics 10)
- The opened copy stays in memory (`Day.applied`, before load()'s changes); `rollover()` rebuilds Library + cards from it off Main (never from the disk) when the day changed: at the next flip (the first of the phone's midnight and UTC midnight: the web reads a release date as UTC midnight for out/coming, so in Dubai that's 4 AM), on coming back to the app (`onOpen` → `catchUp()`: the timer's delay doesn't run while the phone sleeps), and on ACTION_TIME_CHANGED / ACTION_TIMEZONE_CHANGED / ACTION_DATE_CHANGED (one receiver, application context, registered from `Games.start`). The state keeps copyAt/checking/note; Reporter.lib follows. `forget` drops the day.
- Proof: `DayTest` (fake clock via `Games.clock`/`Games.zone`): a coming game is "upcoming" at 11:30 PM and "backlog" at 12:30 AM with Plan "1d" → "Today" and Queue's out-now 1 → 2 (`aNewDayRebuildsFromTheCopyInMemory`), the timer fires at midnight by itself, `onOpen` catches up after a sleep, a zone-change broadcast rebuilds at once, and `nextFlip` picks the earlier midnight. New seam, so it couldn't fail before; before, nothing rebuilt until a new backup or a restart (code trace).

## O · header card tap targets (touch 8) and the fits you added
- Spotlight line: refuted, no change. It's 26dp tall and Compose widens it to 48 for a finger: `HeaderCardTest.theSpotlightLineTakesATapAroundIt` opens it from 8dp above and 8dp below, and nothing from 24dp above.
- Fan's back covers: not changed. Each cover is 60x80 (so Compose doesn't widen it), and folded the two back covers show strips of 34dp and 42dp (measured) between their neighbours; a tap anywhere on a strip opens that cover (`eachBackCoverOpensFromItsStrip`). A wider strip means a wider fan (a look change) or taking taps from the cover in front: the owner's call if ever. Unfolded the strips are ~53dp.
- 320 wide: below 360 the numbers sit 6 apart with 19sp numbers and 9.5sp labels, and ShrinkRow never squeezes an item below its longest word or its number (an item at that floor stays, the others give the room; when nothing hits its floor the split is exactly the old one). "waiting", "to beat", "out now" and "74h" stay whole on one line at 320/340/359; longer labels ("average score", "in your library") may wrap at a space, never mid-word; nothing runs under the fan (`theNumbersStayWholeAt320/340/359`).
- `fixed()` on the numbers, their labels, the genre chips (name and count), the spotlight line and the chart's caption; the unit letter is sized from the capped number, in sp (an em size drew a pixel apart). At font 2.0 on 412 the Queue's numbers stay whole (`theNumbersStayWholeWithTheBiggestFont`). Not capped: the screen title and the mix bar's legend (its dots are sp placeholders; it ends in "…" when long).
- Pictures: I recorded ScreensTest with the base (f7de8c9) and with my code and compared the files: only narrow-320 and font20-412 differ (meant); every other picture is byte-identical. The committed screenshots are untouched.

## For the coordinator
- Re-record narrow-320 and font20-412 (and whatever your own changes moved).
- Root.kt's link route (`Route.Game` with no id) still builds a Rank on Main for On deck's first game; `ready.cards["queue"]?.line?.g?.id` is the same game, already worked out.
- `Shx.built` is an internal test counter; `Games.clock`/`Games.zone` are internal for DayTest.
- Merge: Games.kt conflicted with the backup helper in onOpen and forget; both kept. Their `keep(file, bytes)` and my day setter would have overloaded one name, so mine is `setDay`.

## Final run
`tools/gradle-retry.sh :app:testDebugUnitTest :app:lintRelease` on 4995b70: 167 tests, 0 failed, 0 skipped (GoldenTest ran with `node tools/golden.mjs`'s file), lintRelease 0 errors.
