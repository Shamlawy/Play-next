# Phase 1 parity sheet: the Queue (gate 4)

`node tools/parity-sheet.mjs` → `tools/.cache/parity/queue-412.png`, `queue-859.png` (web | app | difference). Same made-up
games (ScreensTest's library), 2026-10-27 08:00 UTC, On deck off. First run Nov 3 (main after android-v11): 26.5% of pixels
differ at 412, 31.9% at 859, most of it the web's open card.

## Expected (later pieces)

- The web's first game starts open as the Queue card (piece 2): the app shows it as a closed row.
- The bar's Shuffle and Boss buttons (piece 3), the lists button (+, M2 in piece 2).
- The header card's Customise chip (M1, piece 2).
- Unfolded, the web's rail carries Nexi, the Queue's mini cards (Up next, #3, #5…) and the dice: the app's rail has the tabs
  only (Nexi is piece 3; the mini cards and dice belong with them).

## Found (not a missing feature)

- **Header card: the labels under the numbers sit 7 px lower** ("waiting", "to beat", "out now"), and everything under them
  follows (the bar, "1 playing…", the card's foot). The numbers' glyphs match to the pixel (241-258 css px at 412 on both).
  Cause, measured in Robolectric: a 23 sp Poppins number with `lineHeight = 23.sp` lays out 32 dp tall (the web's
  `font: 700 23px/1` box is 23), an 11 sp label with 13.2 sp is 15.7 dp (web 13.2). Neither `includeFontPadding = false` nor
  any `LineHeightStyle` changed it, and the default font is 27 dp, so the test renderer may not apply a line height smaller
  than the font's own. **Open:** check on the phone (a screenshot of the Queue's header next to the web app's) before
  changing every tight style; if the phone shows the same gap, size these boxes by hand (a `layout` that reports the web's
  box height) rather than through `lineHeight`.
