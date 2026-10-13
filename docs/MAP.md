# Code map of index.html

Made by `node tools/map.mjs` (run it again after a release; line numbers move). 25786 lines, 1915 KB, 97 scripts, 60 styles.

How the file is built: one core style (line 15) and one core script (the biggest block below), then one patch per release, oldest first. A patch adds its own code and often wraps a core function (`const _of = openFull; openFull = function () { ... _of.apply(this, arguments) ... }`), so the **last** wrap runs first. To change what a function does, read every layer listed under "Wrapped functions". New code goes in the last script/style.

## Wrapped functions (most layers first)

| function | defined | layers (line: block) |
|---|---|---|
| `save` | 4129 | 8: 12885 core, 13532 core, 13588 core, 13949 core, 14519 v150, 18697 v211, 22158 v237, 23310 v248 |
| `renderHome` | 10952 | 7: 11254 core, 11622 core, 12997 core, 16725 v194, 17648 v204, 24320 v257, 24457 v259 |
| `bugNice` | 17203 | 7: 19559 v215, 21612 v232, 21712 v232, 22112 v236, 22241 v237, 22381 v238, 22816 v239 |
| `syncPlayed` | 3234 | 6: 11457 core, 19945 v218, 20412 v220, 20469 v220, 20720 v221, 23544 v249 |
| `applyLook` | 3493 | 6: 12606 core, 14285, 16727 v194, 17455 v200, 17647 v204, 20460 v220 |
| `bugLayout` | 17048 | 6: 17441 v199, 17459 v200, 17521 v202, 17861 v204, 18478 v208, 18531 v208 |
| `paintTab` | 4381 | 5: 11471 core, 11573 core, 11623 core, 12626 core, 18268 v206 |
| `pnBubSay` | 12143 | 5: 13040 core, 14193 core, 16354 v189, 17852 v204, 21193 v229 |
| `openEdit` | 5128 | 4: 12654 core, 15533 v178, 20163 v219, 23109 v241 |
| `pnBubLines` | 12073 | 4: 13058 core, 17839 v204, 20444 v220, 20659 v221 |
| `renderQueue` | 8506 | 3: 11287 core, 20647 v221, 21709 v232 |
| `pnBubShow` | 12158 | 3: 16355 v189, 17649 v204, 20696 v221 |
| `lookDesign` | 18416 | 3: 19539 v215, 20607 v221, 21606 v232 |
| `renderTaste` | 10398 | 2: 12931 core, 18223 v205 |
| `xFeatures` | 12599 | 2: 13001 core, 13416 core |
| `qCounts` | 7218 | 2: 13237 core, 21599 v232 |
| `queueList` | 7227 | 2: 13238 core, 21600 v232 |
| `pnPowerUp` | 13745 | 2: 14196 core, 24565 v260 |
| `setSub` | 3783 | 2: 18051 v205, 20652 v221 |
| `nxContext` | 13061 | 2: 18936 v212, 21228 v229 |
| `wiAdd` | 18854 | 2: 19620 v216, 19952 v218 |
| `pxRun` | 19077 | 2: 19859 v217, 23534 v249 |
| `nxPlan` | 13302 | 2: 19955 v218, 20126 v219 |
| `gdMarkup` | 16878 | 2: 20115 v219, 20383 v220 |
| `dyShow` | 10510 | 2: 20348 v220, 23786 v249 |
| `pnBubReact` | 11746 | 2: 20446 v220, 22173 v237 |
| `tasteInsightsPage` | 18078 | 2: 20738 v221, 24478 v259 |
| `closeFull` | 9731 | 2: 20857 v225, 23784 v249 |
| `gdShots` | 22183 | 2: 22347 v238, 22378 v238 |
| `sgdbFillOne` | 4501 | 2: 22385 v238, 23017 v241 |
| `applyBg` | 3550 | 2: 24322 v257, 24534 v260 |

## Screen-change hooks (onNav / onSettings, since v269)

goTab / goSub / openFull run every `onNav("before"|"after", fn)` hook and openSettings every `onSettings(...)` hook (one wrap each, defined at line 12637, in registration order below). New code registers here; `kit.mjs syntax` fails on a new wrap of those four.

- 12652: `onNav("after", (k, a) => { if (k === "sub") try { uVisit(a[0]); } catch (e) {} });`
- 12653: `onNav("before", (k, a) => { if (k === "game" && (hubAt !== a[0] || !$("#full.on"))) uCount("o", "game"); });`
- 12655: `onSettings("before", (k, a) => { const sc = a[0]; uCount("o", "settings" + (sc && sc !== "app" ? "/" + sc : ""`
- 15532 v178: `onSettings("after", () => { $("#sheet-in").classList.remove("drill", "ssearch"); calmSheet(true); });`
- 16135 v187: `onSettings("after", () => setTimeout(helperCheck, 50));`
- 16694 v194: `onSettings("after", () => shxSetWire());`
- 17087 v196: `onNav("after", () => bugSeen());`
- 17088 v196: `onSettings("after", () => { bugCrumb("screen settings"); bugLayoutSoon(); });`
- 17548 v203: `onNav("before", k => { if (k === "game") openAt = performance.now(); });`
- 18232 v205: `onNav("after", k => { if (k === "tab" && curTab() !== "taste") ixShown.clear(); });`
- 18769 v211: `onSettings("after", () => cbSetWire());`
- 19330 v213: `onSettings("after", () => pxSetWire());`
- 19332 v213: `onNav("after", (k, a) => { if (k !== "game") return;`
- 19621 v216: `onNav("after", (k, a) => { if (k !== "game") return; const g = S.games.find(x => x.id === a[0]);`
- 20119 v219: `onNav("after", (k, a) => { if (k !== "game") return;`
- 20347 v220: `onSettings("before", () => { const el = document.getElementById("sheet"); if (el) el._flkOpen = performance.no`
- 20651 v221: `onNav("after", k => { if (k === "tab" && !lfBox) setTimeout(() => { try { lfApply(); } catch (e) {} }, 60); })`
- 20654 v221: `onNav("before", (k, a) => { if (k !== "game") return; const g = S.games.find(x => x.id === a[0]), d = g ? dust`
- 20851 v225: `onNav("before", k => { if (k !== "game") return;`
- 22209 v237: `onNav("after", (k, a) => { if (k !== "game") return;`
- 23783 v249: `onNav("after", k => { if (k === "tab") lagWatch(() => "Switching to " + lagWhere()); else if (k === "game") la`
- 23785 v249: `onSettings("after", () => lagWatch("Opening Settings"));`
- 24321 v257: `onNav("after", k => { if (k === "tab") hlvKick(); });`
- 25035 v264: `onNav("after", k => { if (k === "tab") { STACK.go = null; stackArm(3500); } });`
- 25163 v265: `onNav("before", k => prbStart(k === "game" ? "open" : "switch", 1000));`
- 25202 v265: `onNav("before", () => hold(900));`
- 25568 v267: `onNav("after", () => requestAnimationFrame(fitRetry));`
- 25611 v267: `onNav("after", k => { if (k !== "sub") picArm(4000); });`
- 25652 v267: `onNav("after", k => { if (k !== "sub") picWait(); });`
- 25758 v271: `onNav("after", (k, a) => {`

## Blocks

| lines | kind | KB | what |
|---|---|---|---|
| 15–2996 | style | 211 | **CORE** — — |
| 3001–3036 | script | 3 | v252: the card's markup, here so the launch intro can use it before the rest of the page loads. o: k (speed), ar (width / height), word, rv (reveal… |
| 3157–14245 | script | 711 | **CORE** — v169 speed: building a date/number formatter is slow, and the app formats dates for every card on every redraw. Keep one formatter per… |
| 14246–14264 | style | 1 | — |
| 14264–14304 | script | 2 | — |
| 14305–14341 | style | 3 | v147 Top board: ranked rows, big score, share as an image |
| 14341–14432 | script | 7 | v147 Top board in Played, plus a share-as-image button |
| 14433–14459 | style | 2 | v150 badges |
| 14459–14526 | script | 4 | v150 badges: worked out from what you've done, saved with the date you earned them |
| 14526–14532 | script | 0 | v151 art strength slider: live while dragging, saved when let go |
| 14532–14533 | script | 0 | — |
| 14533–14550 | style | 1 | v155 light board motion: only fade + slide, which the phone's graphics chip does for free |
| 14550–14570 | script | 2 | — |
| 14570–14596 | style | 2 | v156 scroll-driven motion: the browser ties these to your finger on its own graphics thread, so they stay at full refresh rate however fast you… |
| 14596–14606 | style | 1 | v157 the real drag: a full-screen blurred picture sat behind every screen, slowly zooming forever, so the phone re-blurred the whole screen on every… |
| 14606–14613 | script | 0 | — |
| 14614–14633 | style | 1 | v163: Why you'll love it — one sheet, fades and slides in, nothing loops |
| 14633–14703 | script | 4 | v163: "Why you'll love it" — sends your ratings and this game to your Cloudflare helper, which asks free Workers AI why it fits your taste (or… |
| 14704–14735 | script | 2 | v166: reset scores — one game from its page, or everything from Settings → Your data. Games, art, hours and diary stay; played games stay played,… |
| 14736–14746 | style | 1 | v167 Nexi's glow sometimes showed as a square box. Three stacked fixes (blur, clip-path, mask) each add a separate layer the phone can drop… |
| 14747–14787 | style | 3 | v169 speed: these animations changed colours/sizes/positions, which makes the phone redraw on every frame. Same looks, now moved or faded instead,… |
| 14788–14799 | style | 1 | v171 swipe a queue card left to delete the game. Only the card's contents slide (translate), the red button waits underneath. |
| 14799–14860 | script | 3 | — |
| 14861–14876 | style | 1 | v172 what's-new tour: a small card with Nexi's words, while Nexi points at the real button |
| 14876–15069 | script | 17 | v172: after every update Nexi shows what's new — where it is, and a nudge to try it. HOW TO ADD ONE: push an entry to WHATS_NEW with the version it… |
| 15069–15311 | style | 22 | ===== v174 Home dashboard (layout from the owner's reference, our black + blue colours) ===== |
| 15311–15397 | style | 8 | ===== v177 play sessions ===== |
| 15397–15537 | script | 10 | ===== v178 calmer settings and edit: search, most-used shortcuts, one section at a time, fewer knobs up front ===== |
| 15537–15583 | style | 4 | ===== v178 calmer settings ===== |
| 15583–15596 | style | 1 | ===== v180 finish month picker ===== |
| 15596–15604 | style | 1 | ===== v181 theme picker ===== |
| 15604–15751 | script | 13 | ===== v181 Game Shuffler: swipe through your games one at a time (right = tempted, left = not now). Styled after the owner's chess reference: a big… |
| 15751–15832 | style | 8 | ===== v181 Game Shuffler (chess reference layout, our black + theme accent) ===== |
| 15832–15858 | style | 2 | ===== v182 lists ===== |
| 15858–15895 | style | 3 | ===== v183 extras off by default, quick duel / score sheets, usage table, quick add ===== |
| 15895–15918 | script | 2 | ===== v183 what's new + settings extras ===== |
| 15918–15963 | script | 3 | ---- badges that keep going as you duel ---- |
| 15963–16040 | style | 7 | ===== v184 ranking: tiers, confidence dots, On deck, Nexi on Home, insights ===== |
| 16040–16060 | script | 2 | ===== v185 what's new ===== |
| 16060–16107 | style | 4 | ===== v185 Nexi chat, personalities, similar games ===== |
| 16107–16115 | script | 0 | ===== v186 what's new ===== |
| 16115–16142 | script | 3 | ===== v187: similar games picked by the AI and checked on RAWG; helper status in Settings → Nexi ===== |
| 16142–16260 | style | 8 | ===== v188 "Clean" style: the rest of the app speaks Home's language ===== One layer, scoped to html.ui2 (Settings → Look of the app → Style →… |
| 16260–16271 | script | 1 | ===== v188 what's new: the Clean style ===== |
| 16271–16291 | style | 2 | ===== v189 tour: Nexi drops out of the notch / camera hole, then rides on the tour card ===== |
| 16291–16392 | script | 8 | ===== v189 tour: Nexi comes out of the notch and stays with the tour card ===== |
| 16392–16548 | style | 12 | ===== v194 "Sicker everywhere": Home's look on every screen (Clean style only) ===== Each screen's header is a card like Home's: a big title, live… |
| 16548–16758 | script | 16 | ===== v194: every screen's header is a card like Home's ===== |
| 16758–16875 | style | 12 | ===== v195: the Add screen and a game's page in Home's language (both switchable) ===== |
| 16875–16925 | script | 4 | ===== v195: a game's page, New style ===== |
| 16925–16930 | style | 0 | v196: on phones under ~400px the pick card was 352px wide whatever the screen (its 220px min-height through the 16:10 ratio set a minimum width) and… |
| 16930–17175 | script | 19 | ===== v196: problem reports. The app notices when something breaks and files it (Settings → This build) ===== What it watches: script errors, a page… |
| 17175–17193 | style | 2 | — |
| 17193–17276 | script | 6 | ===== v197: Nexi says when he's spotted (and already reported) a problem, and the report sheet lists them ===== |
| 17276–17344 | script | 6 | ===== v198: problem reports catch lag, not just freezes ===== The v196 check only saw 400ms+ freezes; the stutters you feel on a 120Hz screen are… |
| 17344–17448 | script | 8 | ===== v199: Nexi's eyes see more: cut-off text, text on top of text, a blank screen ===== Runs with bugLayout (idle, once a screen settles). Only… |
| 17448–17478 | script | 3 | ===== v200: the two lags the owner felt: Queue scrolling and a game page opening ===== |
| 17478–17490 | script | 1 | ===== v201: every picture in the SteamGridDB and Steam pickers shows its real size (4K / HD / Low) before you pick; SteamGridDB lists sharpest first… |
| 17490–17541 | script | 4 | ===== v202: 4K art really shows in 4K (the swap to the sharp copy was starved by the live countdown, see the liteUp kick), RAWG screenshots as… |
| 17541–17565 | script | 2 | ===== v203: a game page is sharp as soon as it opens. warmGame (touch-down, and each neighbour for the swipe) already downloaded and decoded the… |
| 17565–17639 | style | 7 | ===== v204: each personality has its own face, idle move and a signature move when it talks. Persona rules use :where() so they beat the plain face… |
| 17639–17883 | script | 21 | ===== v204: Nexi's personalities get their own lines (context-aware, ~20 each), faces and moves; more everyday lines for everyone; double tap opens… |
| 17883–18046 | style | 17 | ===== v205: Taste → Insights. Psychology, Patterns and Analytics in one page, ordered the way attention works: who you are (identity) → how good… |
| 18046–18240 | script | 19 | ===== v205: Taste → Insights (see the style block above for the order and why) ===== |
| 18240–18289 | script | 4 | ===== v206: lag fixes from the problem reports ===== render() rebuilt all seven screens on every save, add or delete (#58: a 260ms tap on Edit →… |
| 18289–18301 | style | 1 | v208: found by Nexi's new checks on the first sweep: queue rank numbers were drawn in the border colour (1.9:1, hard to read) and the small icon… |
| 18301–18556 | script | 21 | ===== v208: Nexi's new senses. The owner hit 10–15 problems he never saw: a page that flashed back while closing (#68), a description cut short with… |
| 18556–18573 | script | 1 | ===== v209: #81 (4K art stayed low-res). The fix itself is in liteUp's go() (retries, decode() fallback, LITE_NOFULL) and warmGame; this is the tour… |
| 18573–18828 | script | 19 | ===== v211: cloud backup ===== Everything on this phone is sealed here (AES-GCM, key made from the restore code) and sent to the helper, which only… |
| 18828–18844 | style | 1 | — |
| 18844–18949 | script | 8 | ===== v212: "Where was I?" notes ===== A game keeps short notes about where you stopped (g.wasI = [{t, x}], newest last, 20 kept). The newest shows… |
| 18949–18989 | style | 4 | — |
| 18989–19379 | script | 41 | ===== v213: price alerts (Steam + PlayStation Store) ===== Games you're waiting for (not played, not dropped, not competitive) are priced through… |
| 19379–19435 | style | 4 | — |
| 19435–19447 | style | 1 | ===== v215 fixes ===== |
| 19447–19565 | script | 12 | ===== v215: the frozen game page (#99, #102, #103), Read more (#85, #100), Taste names (#86), Queue rows (#89), 4K pictures by default (#84), and… |
| 19565–19580 | style | 1 | ===== v216: the story so far ===== |
| 19580–19637 | script | 5 | ===== v216: "the story so far". Under your newest 📍 note on a game's page, Nexi writes a short recap of the plot up to where the note says you are… |
| 19637–19737 | script | 9 | ===== v216: Steam's genres and "more like this". Steam's own tags are the genres players vote on ("Souls-like", "Metroidvania", "JRPG",… |
| 19737–19756 | script | 2 | — |
| 19756–19763 | style | 0 | ===== v217: release-date watcher + deal radar ===== |
| 19763–19883 | script | 11 | ===== v217: two new things Nexi does on his own (the owner loves automatic features). 1. Release-date watcher (`rdWatch`): every game you're waiting… |
| 19883–19887 | style | 0 | ===== v218: "Finished it?" (same card as Where was I?) ===== |
| 19887–19969 | script | 7 | ===== v218: "Finished it?" — Nexi notices when a game you're playing is probably done and asks, by himself. Signals (`fnWhy(g)`, first that fits):… |
| 19969–19987 | style | 1 | ===== v219: reviews are in (+ your score, predicted) ===== |
| 19987–20230 | script | 20 | ===== v219: three more things Nexi does by himself, and smarter ones (the owner: "I need more smarter and useful auto things"). 1. Reviews are in… |
| 20230–20261 | style | 3 | ===== v220: away report, finish forecast, time capsule, seasons ===== |
| 20261–20509 | script | 23 | ===== v220: six more things that happen by themselves (the owner: "more auto, unique, rare and out of the box"). 1. While you were away (`aw*`):… |
| 20509–20530 | style | 1 | ===== v221: logo fit, Nexi in the rail, dusty backlog, crystal ball ===== |
| 20530–20767 | script | 22 | ===== v221: the owner's and their friend's reports (#118 prices stuck on "checking…", #114 "fit the logo in the queue automatically", #123 "the rail… |
| 20767–20780 | script | 1 | ===== v222: #153 tour steps that point at things only there once you've scored games. The fixes are in the v184/v215 steps (fall back to the "What… |
| 20780–20792 | script | 1 | ===== v224: prices in one line (pxCard, store logos PX_LOGO) + a tap on seated Nexi hides the rail (railHideNow) ===== |
| 20792–20798 | style | 0 | ===== v225: the cover you tap flies into the game page ===== |
| 20798–20864 | script | 5 | ===== v225: the cover you tap flies into the game page ===== Opening a game from anything that shows its cover (Home, queue, library, rail, deck,… |
| 20864–20873 | script | 1 | ===== v226: platforms as logos on the game page (platChips / platHtml, next to platList) ===== |
| 20873–20927 | script | 3 | ===== v227: prices for games the stores' title search can't find (the owner's GTA 6: the PS Store's old search doesn't list it, though its store… |
| 20927–20936 | script | 1 | ===== v228: platform logos in one pill; price checks never skip a store for good (pxStale/pxGap, batches of 5); helper v13 ===== |
| 20937–21242 | script | 33 | ===== v229: Nexi's brain. The owner wants Nexi "super smart", saying a lot more, all of it different and useful, and using their name much more.… |
| 21242–21258 | style | 1 | v230: Browse and choose, every store |
| 21258–21372 | script | 9 | ===== v230: pictures straight from the stores. Browse and choose (Edit → Look) now shows, next to SteamGridDB and RAWG, the official art of Steam… |
| 21372–21381 | script | 1 | ===== v231 (daily fixer): #180/#181/#154 the tour's Next button answered slowly (it saved and opened the next screen inside the tap; now that waits… |
| 21381–21489 | style | 10 | ===== v232: the same shape for every open queue card and every game page ===== |
| 21489–21735 | script | 23 | ===== v232: the owner: "each queue card is a different size when open" and "the new game page lacks organisation: each game looks different because… |
| 21735–21812 | style | 8 | ===== v236: game suggestions you can see (big cards, a preview with store screenshots), swipe to add, Discover, share to add ===== |
| 21812–22128 | script | 28 | ===== v236: the owner: "adding Baldur's Gate 3 from Similar added the wrong game; the pictures are too small and I need more of a description; maybe… |
| 22128–22143 | style | 1 | ===== v237: screenshots on every game page; Grumble only takes real hits ===== |
| 22143–22266 | script | 11 | ===== v237: the owner: "I deleted Baldur's Gate and Grumble lost HP, which is wrong; and when I add a game, forget to say it's played and then say… |
| 22266–22414 | script | 13 | ===== v238: the owner: "only a few games got screenshots, and the ones that did took time to load. I want them instant and always ready, not fetched… |
| 22414–22518 | style | 12 | ===== v239/v242: Game room — a little 3D room built from your games (owner's isometric "B" room reference: layout and style, our colours). Plain CSS… |
| 22518–22890 | script | 31 | ===== v239: Game room. The owner sent an isometric illustration (a room built around a giant book-letter) and asked for something like it in 3D that… |
| 22890–22921 | style | 3 | v241: hours to beat from HowLongToBeat |
| 22921–23127 | script | 17 | ===== v241: hours to beat from HowLongToBeat (`hl*`). The owner: "most hours I type by hand; the automatic ones are super wrong". Two automatic… |
| 23127–23145 | script | 1 | ===== v243 (#313, the owner: "pressing on the text should expand it, not only Read more"): a tap on a cut-short description (or the story so far)… |
| 23145–23156 | script | 1 | ===== v244 (#319): a tap on a game's description (or the story so far) now closes it again too; see the v243 listener. Tour fixes (#307): the v236… |
| 23156–23165 | script | 1 | ===== v245 (#301): pnShelf skips the Finished shelf while its screen is hidden (it forced a full layout of the screen you were on, 130ms at 4x);… |
| 23165–23175 | script | 1 | ===== v246 (#309): no press animation. The owner: "when I touch anything an animation happened, delete that". Until v268 this script stripped… |
| 23175–23179 | style | 0 | v247: the rail's Add and Settings buttons draw the menu's own icons (they were the text glyphs ＋ and ⚙) |
| 23179–23191 | script | 1 | ===== v247: new menu icons, picked by the owner from two drawn sets: Home (house), Games (game case with a disc), Plan (calendar with a release… |
| 23191–23334 | script | 11 | ===== v248: three things that run by themselves. 1. Release days in your calendar: Settings → Extra features → turn on makes a random id (S.calId),… |
| 23334–23365 | style | 3 | v249: buy advice line under the prices, the drifting card on a game page, the shopping plan and queue clean-up sheets |
| 23365–23730 | script | 34 | ===== v249: three smarter things that run by themselves (the owner picked them from a list: "1 2 4"). 1. Buy advice (`sm*` / `smBuy`): price alerts… |
| 23730–23805 | script | 7 | ===== v249, smoother everywhere (the owner: "all tabs and windows lag"; "a lot of lags that even Nexi doesn't see"). 1. Nexi's own problem checks… |
| 23805–23834 | script | 2 | ===== v250 (the owner, with a screenshot of a "picked for you" card: "not all games have screenshots inside here, fix"): 1. The preview (gpLoad)… |
| 23834–23869 | style | 3 | ===== v251: the rail's live strip (replaces the queue covers) + Nexi sized to his seat ===== |
| 23869–24059 | script | 17 | ===== v251 (the owner, screenshot of the unfolded rail with the queue covers circled: "change them into something useful with live animation that is… |
| 24059–24067 | script | 1 | v253 (#338): the view switcher's marker is measured in the next frame (slidePill), not inside the tap |
| 24067–24081 | script | 1 | after v255 (#354 #355 #358): the lag checks skip frames that overlap a resize (fold/unfold, 0.8s after) or the time the app was hidden (asleep, in… |
| 24081–24092 | script | 1 | v256 (#352): the calling card is drawn in steps (callingCard, yf) |
| 24092–24136 | style | 4 | v257 live background (hlv*): a game's art and three lights taken from it drift behind the screen, plus rising specks. Only transform/opacity move,… |
| 24136–24357 | script | 13 | v257 live background. Switches: S.hlvOff (Home), S.hlvX.games / .game / .plan / .taste (Customise on each screen) |
| 24357–24432 | style | 8 | v259 three new card looks, from the owner's references (layout and style copied, our colours): dzp = Home "Playing now" colour cards, dzd = On deck… |
| 24432–24527 | script | 8 | v259 three new card looks (switch: S.dzX) |
| 24527–24532 | style | 0 | v260 smoothness + battery: fewer full-screen layers stacked on every frame |
| 24532–24579 | script | 3 | v260: #bgtint only while an art backdrop is up |
| 24580–24584 | style | 0 | v261: the view pill's highlight slides on the GPU: it used to animate width (a layout every frame for .28s) |
| 24584–24600 | script | 1 | v261: smooth at the screen's own refresh rate. The highlight takes its new width at once and a transform animation (scaleX from the old width) makes… |
| 24601–24640 | script | 3 | v261: Nexi learned (the owner: "still lags a lot, I want 120 fps"): an animation the phone can't hand to its GPU makes the main thread draw every… |
| 24640–24647 | script | 1 | — |
| 24648–24655 | style | 0 | v263 (120 fps): Nexi's face: a mood used to morph his eyes and mouth (height, top, width, borders: main-thread transitions), and a blink restarted… |
| 24655–24799 | script | 11 | v263 (120 fps): a looping animation on something that isn't drawn (a hidden screen; a list row scrolled far enough that the browser skips drawing… |
| 24799–24806 | style | 1 | v264: the whole background in one layer (hlvf*, last script). The flat canvas keeps v257's own canvas rule (its own layer, will-change: transform)… |
| 24806–25046 | script | 17 | v264 (the owner, the fifth time: "still lags"). v263 made the main thread quiet; what was left is the GPU. A trace of what it draws per frame (the… |
| 25046–25054 | style | 1 | v265 smoothness check (prb*, last script): while it tests a suspect, that suspect is switched off for a little while |
| 25054–25178 | script | 9 | v265: the smoothness check (the owner, after five rounds: "it lags everywhere, as if it's too much: a fast scroll up or down on the Queue, sideways… |
| 25178–25192 | script | 1 | v265 (the owner: "if I scroll fast it lags, up or down on the Queue"): touch scrolling no longer waits for the app. Every screen but Home and every… |
| 25192–25207 | script | 1 | v265: the light behind the screen holds still while you're touching, scrolling or switching (it moves about a pixel per step, so a pause can't be… |
| 25207–25248 | script | 4 | v265: Nexi learned (the blocking touchmove above sat on every screen for months and nothing noticed: it doesn't throw, doesn't look wrong, and the… |
| 25248–25408 | script | 12 | v266: the self-test (step 1 of the plan the owner agreed: "measure first, on the phone, before changing anything"). Settings → This build → "Run the… |
| 25408–25417 | style | 1 | v266 self-test result card (inside the diary sheet's box) |
| 25417–25427 | style | 1 | v271: the progress line draws itself to the step a game just reached. While it runs, the segment's own lines go transparent and .gd-fill (a dim… |
| 25427–25434 | script | 0 | — |
| 25434–25573 | script | 10 | v267 picture fit, layer 2 (layer 1 is sw.js: nothing bigger than the screen). Lists show pictures far smaller than the screen: a Queue row's faded… |
| 25573–25667 | script | 8 | v267: Nexi learned (the phone's self-test: 372 MP of pictures on the Queue for 14 MP on screen, one picture 70 MP; none of his checks looked at… |
| 25667–25676 | script | 1 | v269: the tidy-up (the owner: "organise the code, merge it, cut what isn't needed"). No new feature: onNav (screen-change hooks in one place), dead… |
| 25676–25720 | script | 3 | v270: buzzes on the iPhone too. Safari has no navigator.vibrate, but iOS 18+ gives a light tap each time an <input type="checkbox" switch> flips,… |
| 25720–25777 | script | 4 | v271: two moments that used to jump now move (the owner: "smoothness is rule number one, but I do like animations"). Picked with the… |
| 25777–25786 | script | 1 | #432: Settings (#sheet) let a sideways swipe through to the game page behind it; the other sheets already stop it (noHub) |
