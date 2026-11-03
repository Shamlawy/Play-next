# Phase 1 parity sheet: the Queue (gate 4)

`node tools/parity-sheet.mjs` → `tools/.cache/parity/queue-412.png`, `queue-859.png` (web | app | difference). Same made-up
games (ScreensTest's library), 2026-10-27 08:00 UTC, On deck off. First run Nov 3 (main after android-v11): 26.5% of pixels
differ at 412, 31.9% at 859, most of it the web's open card. android-v12 (the Customise chip): 26.6% and 31.8%; the
chip lines up on both. android-v13 (your lists, the wrapping tabs, M71's header labels): 26.1% and 31.3%; the
header numbers' labels sit 29 px under the numbers on both (6 dp lower in the app before `cssLine`, the owner's Fold
showed the same).

## Expected (later pieces)

- The web's first game starts open as the Queue card (piece 2): the app shows it as a closed row.
- The bar's Shuffle and Boss buttons (piece 3), the lists button (+, M2 in piece 2).
- Unfolded, the web's rail carries Nexi, the Queue's mini cards (Up next, #3, #5…) and the dice: the app's rail has the tabs
  only (Nexi is piece 3; the mini cards and dice belong with them).

## Found (not a missing feature)

- **Fixed in android-v13: the header card's labels sat 7 px lower** ("waiting", "to beat", "out now"; everything under them
  followed). A 23 sp Poppins line with `lineHeight = 23.sp` laid out 32 dp tall (the font's own 1.4em; the web's
  `font: 700 23px/1` box is 23), the phone showed the same (the owner's Fold, Nov 3: ~7-8 dp). `Modifier.cssLine(style)`
  (ui/Type.kt) puts the first baseline where CSS centres it and reports the web's box; HeaderCardTest checks baseline to
  baseline (18.7 dp, web 18.9).