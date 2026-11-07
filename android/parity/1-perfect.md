# Phase 1 · Perfect run (Nov 7 2026)

Run as 3 review helpers in the chat (Time and tokens: two at a time, then the third; no skeptics, the fix reproduces each
finding with a test first, L198): A bugs + touch + back (opus), B speed + look (sonnet), C Android basics + updates +
parity (sonnet). Plus the known lists: M78 (Character select), android-v26's review leftovers, 2-card.md's not-ported list.

## B · Speed + look (sonnet, 20 tool calls; nothing high)

Fine: row keys `"q:"+id`, `shown`/`Queue.stat` remembered, translation/fold/drag read in `graphicsLayer {}`, swipe via
`derivedStateOf`, no hex `Color(0x…)` in Phase 1 UI, Nexi's loops read in layer lambdas, the blink a timed Animatable,
calm stops every loop, logos decoded off Main (software bitmaps for InkCrop), no Main disk/network.

1. **B1 medium speed** Three subcompositions per closed row: `BoxWithConstraints` in the row (ui/Queue.kt ~1233), in
   `LogoSlot` (~1537) and `WholeName` (~1637); CharSelect.kt ~338 too. Pass the widths down from the row's one.
2. **B2 medium speed** The Dusty mark (Queue.kt ~1427-1450) loops and calls `toPx()` per dot each draw; SelBar
   (CharSelect.kt ~314) makes 10 Paths per draw; the progress dashes (~1315) too. `drawWithCache`.
3. **B3 low speed** The row's gradient brush and `Backup.num(g["bgX"])`/`g["imgX"]` built per recomposition
   (Queue.kt ~1237-1271). `remember`.
4. **B4 low speed** `Queue.stat` builds `Regex(" \\d+$")` per call (data/Queue.kt ~207), called per cover in CharSelect.
5. **B5** Nexi's ring/glow/shadows: fine.
6. **B6 low look, unsure** The filter strip cuts the 4th chip at 412 (queue-412.png); a scroller's cut edge.
7. **B7 low look, unsure** Character select's kicker "Out now · Ready to pl…" ellipsised at 412 (CharSelect.kt ~239).
- No `contentType` on the lazy lists (free tweak).
- Known, confirmed: badge hours summed per call (data/Nexi.kt ~34); the dizzy spin read in drawBehind (ui/Nexi.kt ~595,
  615, 642); M78's Kotlin mixes (CharSelect.kt ~217, 236, 309, 360, 385).

## C · Android basics + updates + parity (sonnet, 21 tool calls)

Fine: configChanges cover fold/rotate (no recreation), nothing writes to the helper or the backup (vault put blocked,
practice edits in memory; Nexi's place and day marks are local prefs), `.fixed()` caps text growth, 48dp targets, sounds
behind their switch, the lists field has imePadding.

1. **C1 medium basics** The Queue's practice state (`QueuePick`: tab, sort, open card, side, Character select's pick,
   drag order, deletes) is plain `remember` (ui/Shell.kt ~193, ui/Queue.kt ~197-291): a background kill resets it.
   3-critic.md M7/X6 promised the open card, its side, the scroll spot and the pick come back. `Saver` + `rememberSaveable`.
2. **C2 medium basics** The lists sheet saves `adding`/`addingName` but the new list lives in `Games.practice` (not
   saved): after a kill the Add games sheet reopens for a list that's gone (ui/Lists.kt ~83-108).
3. **C3 medium parity** The card back's "Like this" row + "More like it"/"Trailer" chips (3-critic.md M3, 2-card.md
   ~149) aren't built and nothing defers them.
4. **C4 medium parity** The Queue's entry points for later phases (`Queue.show(id)`, `Queue.filter(key)`, the `G:` genre
   and `S:` similar tabs; 3-critic.md M5, X10) aren't built and nothing defers them.
5. **C5 low parity** No Arabic/Japanese title check (3-critic.md M7); TalkBack words for Shuffle/Boss/lists changed with
   v21's Sort menu (fine).
6. **C6 low leftover** 2-card.md §9's table still says "Nothing yet … QueueSoon"; ui/Queue.kt ~168-170's header says
   Shuffle, Boss, drag, swipe and Character select "come next".
7. **C7 low updates** whatsnew v=13/14 ("the square button at the end of the tabs"), v=20 ("crossed arrows next to
   Order", "red Boss button") and v=25 ("tap Shuffle") name controls v21 moved into the Sort menu.
- Unsure: `Buzz.ms(14)` on drag start (ui/Queue.kt ~764) with no switch of its own; StartGate and the midnight
  rollover not checked.

## A · Bugs + touch + back (opus, 44 tool calls)

Fine: Nav's sheet/game-page back order, Sort's menu closes on back (focusable Popup), a second back while a game page
shrinks waits, 48dp targets on chips/Sort/Undo/Delete/deck/lists, a double tap on a closed row opens then closes (web too).
Back tests need `createAndroidComposeRule<ComponentActivity>()` + `rule.activity.onBackPressedDispatcher.onBackPressed()`
(as TouchTest.backAfterADifferentRestoreCode); the Queue tests' `createComposeRule` has no back dispatcher.

1. **A1 medium back** Back doesn't close the Queue's own open things (a tapped card, a swiped row's Delete, a card in
   Character select): no BackHandler in Queue/QueueCard/CharSelect, so `moveTaskToBack` leaves with it open (Oct 28
   rule). Fix in Shell's Queue branch: `BackHandler(enabled = nav.overlay == null && (qpick.swiped != null ||
   qpick.focus != null && !qpick.collapsed)) { swiped = null, else close(focus) }` (gating on focus: the card open at
   start doesn't eat the first back; ask the owner with a picture whether it should).
2. **A2 medium bug** Character select: the open card's pending one-tap close (320 ms, QueueCard.kt ~180, scope made once
   ~128) survives a tap on another cover (same "q-sel" item, no `key(g.id)`, CharSelect.kt ~113-118): the new card snaps
   shut. Fix: `key(g.id)` round the QueueCard call, or cancel the pending job on g.id change.
3. **A3 medium touch** A two-finger tab swipe starting on a row slides the row's Delete (swipeDelete, Queue.kt ~848-893,
   follows only down.id and sets guard.inScroller). Web: qdel's touchstart drops 2+ touches (index.html ~15093). Fix:
   break when `e.changes.count { it.pressed } > 1` before sliding; same in hold's 380 ms wait (~739).
4. **A4 medium basics** = C1, and also: Root takes the whole Shell out of composition for the 120 Hz test / restore-code
   screen (Root.kt ~188-208), so coming back resets tab, order, drag, deletes, side, pick (and the saved LazyListState
   lands at the old index in a different list). Fix: hold QueuePick above the `when` in Root, or the saveable state.
5. **A5 low-medium bug** The Undo bar and Shuffle's "Tonight" line come back minutes later (L243 again): their timers
   run in a LaunchedEffect only while Games › Queue shows (Queue.kt ~465, ~508). Give QueueGone/QueueLit an expiry time.
6. **A6 low-medium back** Your lists: back from Add games / Rename / the delete question closes the whole sheet
   (Lists.kt ~83-95). `BackHandler(enabled = adding != null) { adding = null }` etc. inside ListsSheet.
7. **A7 low parity** A tab or order change keeps the open card (web 9044/9048 set focusId = null): Queue.kt ~956, ~1044,
   Shell.kt ~323 need `focus = null`.
8. **A8 low unsure** A cancelled touch may count as a lift (hold's drop at Queue.kt ~771 → the order changes; QueueCard
   ~173's waitForUpOrCancellation). Check with `performTouchInput { down; advanceEventTime(500); moveBy; cancel() }`.

## Fix status (branch claude/bold-meitner-v7rray, not yet released)

Done in this branch (compiled; tests not yet re-run after the last three):
- v26 leftovers: Nexi's badge worked out off Main (data/Nexi.kt `count`/`hours`); `WindowInsets.steady` (bars + cutout,
  never the keyboard, ui/Edges.kt) for Nexi's placing, the rail's foot and the Sort menu; the hidden rail
  `clearAndSetSemantics`; his drag sets `NexiState.grabAt` (no coroutine per move, `at` = grabAt or pos); the dizzy eye
  spins by its layer's rotationZ (eye() no longer takes a spin).
- M78: android-gen reads `QF_CV_EDGE` (.qfcv's 2px edge) and the ring / P1 / lit segment's white mixes
  (`QF_RING_MIX/ALPHA`, `QF_P1_*`, `QF_SEG_*` via `cssMix`); the panel's swap plays only on a tap that leaves the panel
  showing (`QueuePick.selSwap`/`selSwapSeen`, also on a card closed into it, as `qfTap`); `QueuePick.saved()`/`restore()`
  written (not wired yet).
Left: wire saved()/restore() (Shell: `LocalSaveableStateRegistry.registerProvider("qpick") { qpicks[0]?.saved() }` and
`consumeRestored` where the pick is made; also fixes A4 if the pick moves above Root's `when`); Character select's sel
reset when it leaves the tab (web 8954) + `swiped = null` on a switch to it (LaunchedEffect in Shell's Queue branch);
the 40dp-vs-14 gap under an open card in Character select (web `.qfwrap > .hero { margin-bottom: 14px }`); A1-A3, A5-A7,
C2, C6, C7; B2-B4; defer C3 (Like this row) and C4 (Queue entry points) to Phase 2 in writing; tests for each.
