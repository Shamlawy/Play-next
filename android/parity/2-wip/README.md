# Prices (piece 4, slice 3): work in progress, not built yet

Written Nov 15, not compiled or tested yet. Move each file to its place, then build:
- `Prices.kt` → `android/app/src/main/java/io/github/shamlawy/playnext/data/Prices.kt`
- `PriceCards.kt` → `.../ui/PriceCards.kt` (HelperPair, PricesSec, PxChip, PriceSheet)
- `reviewsShow.kt` → paste into `.../ui/HelperCards.kt` (needs `import io.github.shamlawy.playnext.data.Page` etc. already there)
- `PricesTest.kt` → `android/app/src/test/java/io/github/shamlawy/playnext/data/PricesTest.kt`
- `tools/android-gen.mjs` on this branch already makes the tables, chip colours, `px:ps|st|ns` logos and `ui:price`.

Still to wire: `EditActs.prices` (EditSheet.kt) + a `prices:<id>` step in Shell.kt (like `hours:`; Root.kt's screen name);
`HBtn` in HoursSheet.kt from private to internal; GameBody's `ReviewsSec(...)` call → `HelperPair(g, page, lib, big, <same modifier>)`.
Then delete this folder.
