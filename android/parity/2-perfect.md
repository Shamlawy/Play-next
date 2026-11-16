# Phase 2 · Check (Nov 16 2026)

A lighter check than Phase 1's, to keep the pace (the owner: "hurry up to start phase 3"): two read-only review helpers
on the Phase 2 code (the game page and everything off it), no skeptics; each finding was read in the code before fixing.
A: bugs + touch + back (opus). B: speed + look + Android basics (sonnet). Neither found anything high.

## Fixed in android-v45 (same release)

1. **A1 medium** The story so far's ask (`StoryCard`, ui/StoryCards.kt) restarted on `Recap.seen`, a counter every game's
   answer moves; the pager composes the neighbour pages too, so one page's answer cancelled the next page's ask mid-call.
   Now it restarts only on its own game's re-ask (`Recap.asksOf`).
2. **A3 low** HowLongToBeat's ask (`Tiles`, GamePage.kt) also ran for the neighbour pages, and its auto-fill writes a
   practice copy: a game you never opened got "Practice copy". Now only the page showing asks (`LocalPageShown`).
3. **A2 low** A screenshot fetch cancelled with its page (a swipe past) kept `tried`, so coming back within a minute showed
   none. Cancelled → `tried` cleared (ScreenshotsTest `aFetchCancelledMidwayIsAskedAgainAtOnce`).
4. **A4 low** The calling card's make swallowed its own cancellation (a Reshuffle mid-make), so "Couldn't make the card"
   could flash. Rethrown now.
5. **B1 medium** The calling card's Share wrote its PNG on the main thread (100 ms+). `Share.image` writes on IO now.
6. **B2/B4 low** `priceText`'s and `Share`'s Regex built per call → one each; the page's screenshot strip has keys.

## Left, low (for a later fix chat or Phase 3's pieces)

- B3: the price chip's gradient brush is rebuilt when the card recomposes (rare: keyed on its tick).
- B5: Reshuffle doesn't survive a fold/unfold (plain `remember`); the card is remade from the title's seed.
- B6, unsure: text marks "✕" (sheet close in ScoreSheet, NoteSheet, HoursSheet), "✓" (HoursSheet, the PLAYED stamp) and
  "›" (Prices) come from the font, not drawn icons; android-rules.mjs allows "✕". Not emoji, but a drawn close/check icon
  would match the house style better. A taste call for the owner.
- B7, unsure: the genre chip on the page is a 32dp-high button (under the 48dp target) unless a wrapper pads it.

## Checked and fine

Back closes its own step by index everywhere, no invisible layer is left after a sheet closes, nothing writes to the
helper or the backup (practice copies only), per-game busy sets keep answers from overwriting each other, ScoreSheet
guards a double tap; animated values read in graphicsLayer/drawBehind, calm stops the story bars' pulse, no BoxWithConstraints
in repeated rows, no Main disk/network, no raw hex, every icon button labelled with a 48dp box, Share survives no receiver.
