# Code map of index.html

Made by `node tools/map.mjs` (run it again after a release; line numbers move). 25173 lines, 1877 KB, 101 scripts, 59 styles.

How the file is built: one core style (line 15) and one core script (the biggest block below), then one patch per release, oldest first. A patch adds its own code and often wraps a core function (`const _of = openFull; openFull = function () { ... _of.apply(this, arguments) ... }`), so the **last** wrap runs first. To change what a function does, read every layer listed under "Wrapped functions". New code goes in the last script/style.

## Wrapped functions (most layers first)

| function | defined | layers (line: block) |
|---|---|---|
| `save` | 4018 | 8: 12642 core, 13289 core, 13345 core, 13701 core, 14270 v150, 18441 v211, 21902 v237, 23054 v248 |
| `bugNice` | 16947 | 7: 19303 v215, 21356 v232, 21456 v232, 21856 v236, 21985 v237, 22125 v238, 22560 v239 |
| `renderHome` | 10733 | 6: 11035 core, 11403 core, 12754 core, 16469 v194, 17392 v204, 23936 v259 |
| `syncPlayed` | 3212 | 6: 11238 core, 19689 v218, 20156 v220, 20213 v220, 20464 v221, 23288 v249 |
| `applyLook` | 3474 | 6: 12363 core, 14036, 16471 v194, 17199 v200, 17391 v204, 20204 v220 |
| `bugLayout` | 16792 | 6: 17185 v199, 17203 v200, 17265 v202, 17605 v204, 18222 v208, 18275 v208 |
| `paintTab` | 4270 | 5: 11252 core, 11354 core, 11404 core, 12383 core, 18012 v206 |
| `pnBubSay` | 11900 | 5: 12797 core, 13945 core, 16098 v189, 17596 v204, 20937 v229 |
| `openEdit` | 5015 | 4: 12411 core, 15277 v178, 19907 v219, 22853 v241 |
| `pnBubLines` | 11830 | 4: 12815 core, 17583 v204, 20188 v220, 20403 v221 |
| `renderQueue` | 8305 | 3: 11068 core, 20391 v221, 21453 v232 |
| `pnBubShow` | 11914 | 3: 16099 v189, 17393 v204, 20440 v221 |
| `lookDesign` | 18160 | 3: 19283 v215, 20351 v221, 21350 v232 |
| `dyShow` | 10309 | 3: 20092 v220, 23530 v249, 25125 |
| `renderTaste` | 10197 | 2: 12688 core, 17967 v205 |
| `xFeatures` | 12356 | 2: 12758 core, 13173 core |
| `qCounts` | 7017 | 2: 12994 core, 21343 v232 |
| `queueList` | 7026 | 2: 12995 core, 21344 v232 |
| `pnPowerUp` | 13497 | 2: 13947 core, 24033 v260 |
| `setSub` | 3674 | 2: 17795 v205, 20396 v221 |
| `nxContext` | 12818 | 2: 18680 v212, 20972 v229 |
| `wiAdd` | 18598 | 2: 19364 v216, 19696 v218 |
| `pxRun` | 18821 | 2: 19603 v217, 23278 v249 |
| `nxPlan` | 13059 | 2: 19699 v218, 19870 v219 |
| `gdMarkup` | 16622 | 2: 19859 v219, 20127 v220 |
| `pnBubReact` | 11513 | 2: 20190 v220, 21917 v237 |
| `tasteInsightsPage` | 17822 | 2: 20482 v221, 23957 v259 |
| `closeFull` | 9530 | 2: 20601 v225, 23528 v249 |
| `gdShots` | 21927 | 2: 22091 v238, 22122 v238 |
| `sgdbFillOne` | 4390 | 2: 22129 v238, 22761 v241 |

## Screen-change hooks (onNav / onSettings, since v269)

goTab / goSub / openFull run every `onNav("before"|"after", fn)` hook and openSettings every `onSettings(...)` hook (one wrap each, defined at line 12394, in registration order below). New code registers here; `kit.mjs syntax` fails on a new wrap of those four.

- 12409: `onNav("after", (k, a) => { if (k === "sub") try { uVisit(a[0]); } catch (e) {} });`
- 12410: `onNav("before", (k, a) => { if (k === "game" && (hubAt !== a[0] || !$("#full.on"))) uCount("o", "game"); });`
- 12412: `onSettings("before", (k, a) => { const sc = a[0]; uCount("o", "settings" + (sc && sc !== "app" ? "/" + sc : ""`
- 15276 v178: `onSettings("after", () => { $("#sheet-in").classList.remove("drill", "ssearch"); calmSheet(true); });`
- 15879 v187: `onSettings("after", () => setTimeout(helperCheck, 50));`
- 16438 v194: `onSettings("after", () => shxSetWire());`
- 16831 v196: `onNav("after", () => bugSeen());`
- 16832 v196: `onSettings("after", () => { bugCrumb("screen settings"); bugLayoutSoon(); });`
- 17292 v203: `onNav("before", k => { if (k === "game") openAt = performance.now(); });`
- 17976 v205: `onNav("after", k => { if (k === "tab" && curTab() !== "taste") ixShown.clear(); });`
- 18513 v211: `onSettings("after", () => cbSetWire());`
- 19074 v213: `onSettings("after", () => pxSetWire());`
- 19076 v213: `onNav("after", (k, a) => { if (k !== "game") return;`
- 19365 v216: `onNav("after", (k, a) => { if (k !== "game") return; const g = S.games.find(x => x.id === a[0]);`
- 19863 v219: `onNav("after", (k, a) => { if (k !== "game") return;`
- 20091 v220: `onSettings("before", () => { const el = document.getElementById("sheet"); if (el) el._flkOpen = performance.no`
- 20395 v221: `onNav("after", k => { if (k === "tab" && !lfBox) setTimeout(() => { try { lfApply(); } catch (e) {} }, 60); })`
- 20398 v221: `onNav("before", (k, a) => { if (k !== "game") return; const g = S.games.find(x => x.id === a[0]), d = g ? dust`
- 20595 v225: `onNav("before", k => { if (k !== "game") return;`
- 21953 v237: `onNav("after", (k, a) => { if (k !== "game") return;`
- 23527 v249: `onNav("after", k => { if (k === "tab") lagWatch(() => "Switching to " + lagWhere()); else if (k === "game") la`
- 23529 v249: `onSettings("after", () => lagWatch("Opening Settings"));`
- 24323 v264: `onNav("after", k => { if (k === "tab") { STACK.go = null; stackArm(3500); } });`
- 24446 v265: `onNav("before", k => prbStart(k === "game" ? "open" : "switch", 1000));`
- 24840 v267: `onNav("after", () => requestAnimationFrame(fitRetry));`
- 24883 v267: `onNav("after", k => { if (k !== "sub") picArm(4000); });`
- 24924 v267: `onNav("after", k => { if (k !== "sub") picWait(); });`
- 25030 v271: `onNav("after", (k, a) => {`

## Blocks

| lines | kind | KB | what |
|---|---|---|---|
| 15–2983 | style | 210 | **CORE** — — |
| 2987–3022 | script | 3 | v252: the card's markup, here so the launch intro can use it before the rest of the page loads. o: k (speed), ar (width / height), word, rv (reveal… |
| 3135–13996 | script | 698 | **CORE** — v169 speed: building a date/number formatter is slow, and the app formats dates for every card on every redraw. Keep one formatter per… |
| 13997–14015 | style | 1 | — |
| 14015–14055 | script | 2 | — |
| 14056–14092 | style | 3 | v147 Top board: ranked rows, big score, share as an image |
| 14092–14183 | script | 7 | v147 Top board in Played, plus a share-as-image button |
| 14184–14210 | style | 2 | v150 badges |
| 14210–14277 | script | 4 | v150 badges: worked out from what you've done, saved with the date you earned them |
| 14277–14283 | script | 0 | v151 art strength slider: live while dragging, saved when let go |
| 14283–14284 | script | 0 | — |
| 14284–14301 | style | 1 | v155 light board motion: only fade + slide, which the phone's graphics chip does for free |
| 14301–14321 | script | 2 | — |
| 14321–14347 | style | 2 | v156 scroll-driven motion: the browser ties these to your finger on its own graphics thread, so they stay at full refresh rate however fast you… |
| 14347–14355 | style | 1 | v157 the real drag: a full-screen blurred picture sat behind every screen, slowly zooming forever, so the phone re-blurred the whole screen on every… |
| 14355–14362 | script | 0 | — |
| 14363–14382 | style | 1 | v163: Why you'll love it — one sheet, fades and slides in, nothing loops |
| 14382–14452 | script | 4 | v163: "Why you'll love it" — sends your ratings and this game to your Cloudflare helper, which asks free Workers AI why it fits your taste (or… |
| 14453–14484 | script | 2 | v166: reset scores — one game from its page, or everything from Settings → Your data. Games, art, hours and diary stay; played games stay played,… |
| 14485–14495 | style | 1 | v167 Nexi's glow sometimes showed as a square box. Three stacked fixes (blur, clip-path, mask) each add a separate layer the phone can drop… |
| 14496–14531 | style | 3 | v169 speed: these animations changed colours/sizes/positions, which makes the phone redraw on every frame. Same looks, now moved or faded instead,… |
| 14532–14543 | style | 1 | v171 swipe a queue card left to delete the game. Only the card's contents slide (translate), the red button waits underneath. |
| 14543–14604 | script | 3 | — |
| 14605–14620 | style | 1 | v172 what's-new tour: a small card with Nexi's words, while Nexi points at the real button |
| 14620–14813 | script | 17 | v172: after every update Nexi shows what's new — where it is, and a nudge to try it. HOW TO ADD ONE: push an entry to WHATS_NEW with the version it… |
| 14813–15055 | style | 22 | ===== v174 Home dashboard (layout from the owner's reference, our black + blue colours) ===== |
| 15055–15141 | style | 8 | ===== v177 play sessions ===== |
| 15141–15281 | script | 10 | ===== v178 calmer settings and edit: search, most-used shortcuts, one section at a time, fewer knobs up front ===== |
| 15281–15327 | style | 4 | ===== v178 calmer settings ===== |
| 15327–15340 | style | 1 | ===== v180 finish month picker ===== |
| 15340–15348 | style | 1 | ===== v181 theme picker ===== |
| 15348–15495 | script | 13 | ===== v181 Game Shuffler: swipe through your games one at a time (right = tempted, left = not now). Styled after the owner's chess reference: a big… |
| 15495–15576 | style | 8 | ===== v181 Game Shuffler (chess reference layout, our black + theme accent) ===== |
| 15576–15602 | style | 2 | ===== v182 lists ===== |
| 15602–15639 | style | 3 | ===== v183 extras off by default, quick duel / score sheets, usage table, quick add ===== |
| 15639–15662 | script | 2 | ===== v183 what's new + settings extras ===== |
| 15662–15707 | script | 3 | ---- badges that keep going as you duel ---- |
| 15707–15784 | style | 7 | ===== v184 ranking: tiers, confidence dots, On deck, Nexi on Home, insights ===== |
| 15784–15804 | script | 2 | ===== v185 what's new ===== |
| 15804–15851 | style | 4 | ===== v185 Nexi chat, personalities, similar games ===== |
| 15851–15859 | script | 0 | ===== v186 what's new ===== |
| 15859–15886 | script | 3 | ===== v187: similar games picked by the AI and checked on RAWG; helper status in Settings → Nexi ===== |
| 15886–16004 | style | 8 | ===== v188 "Clean" style: the rest of the app speaks Home's language ===== One layer, scoped to html.ui2 (Settings → Look of the app → Style →… |
| 16004–16015 | script | 1 | ===== v188 what's new: the Clean style ===== |
| 16015–16035 | style | 2 | ===== v189 tour: Nexi drops out of the notch / camera hole, then rides on the tour card ===== |
| 16035–16136 | script | 8 | ===== v189 tour: Nexi comes out of the notch and stays with the tour card ===== |
| 16136–16292 | style | 12 | ===== v194 "Sicker everywhere": Home's look on every screen (Clean style only) ===== Each screen's header is a card like Home's: a big title, live… |
| 16292–16502 | script | 16 | ===== v194: every screen's header is a card like Home's ===== |
| 16502–16619 | style | 12 | ===== v195: the Add screen and a game's page in Home's language (both switchable) ===== |
| 16619–16669 | script | 4 | ===== v195: a game's page, New style ===== |
| 16669–16674 | style | 0 | v196: on phones under ~400px the pick card was 352px wide whatever the screen (its 220px min-height through the 16:10 ratio set a minimum width) and… |
| 16674–16919 | script | 19 | ===== v196: problem reports. The app notices when something breaks and files it (Settings → This build) ===== What it watches: script errors, a page… |
| 16919–16937 | style | 2 | — |
| 16937–17020 | script | 6 | ===== v197: Nexi says when he's spotted (and already reported) a problem, and the report sheet lists them ===== |
| 17020–17088 | script | 6 | ===== v198: problem reports catch lag, not just freezes ===== The v196 check only saw 400ms+ freezes; the stutters you feel on a 120Hz screen are… |
| 17088–17192 | script | 8 | ===== v199: Nexi's eyes see more: cut-off text, text on top of text, a blank screen ===== Runs with bugLayout (idle, once a screen settles). Only… |
| 17192–17222 | script | 3 | ===== v200: the two lags the owner felt: Queue scrolling and a game page opening ===== |
| 17222–17234 | script | 1 | ===== v201: every picture in the SteamGridDB and Steam pickers shows its real size (4K / HD / Low) before you pick; SteamGridDB lists sharpest first… |
| 17234–17285 | script | 4 | ===== v202: 4K art really shows in 4K (the swap to the sharp copy was starved by the live countdown, see the liteUp kick), RAWG screenshots as… |
| 17285–17309 | script | 2 | ===== v203: a game page is sharp as soon as it opens. warmGame (touch-down, and each neighbour for the swipe) already downloaded and decoded the… |
| 17309–17383 | style | 7 | ===== v204: each personality has its own face, idle move and a signature move when it talks. Persona rules use :where() so they beat the plain face… |
| 17383–17627 | script | 21 | ===== v204: Nexi's personalities get their own lines (context-aware, ~20 each), faces and moves; more everyday lines for everyone; double tap opens… |
| 17627–17790 | style | 17 | ===== v205: Taste → Insights. Psychology, Patterns and Analytics in one page, ordered the way attention works: who you are (identity) → how good… |
| 17790–17984 | script | 19 | ===== v205: Taste → Insights (see the style block above for the order and why) ===== |
| 17984–18033 | script | 4 | ===== v206: lag fixes from the problem reports ===== render() rebuilt all seven screens on every save, add or delete (#58: a 260ms tap on Edit →… |
| 18033–18045 | style | 1 | v208: found by Nexi's new checks on the first sweep: queue rank numbers were drawn in the border colour (1.9:1, hard to read) and the small icon… |
| 18045–18300 | script | 21 | ===== v208: Nexi's new senses. The owner hit 10–15 problems he never saw: a page that flashed back while closing (#68), a description cut short with… |
| 18300–18317 | script | 1 | ===== v209: #81 (4K art stayed low-res). The fix itself is in liteUp's go() (retries, decode() fallback, LITE_NOFULL) and warmGame; this is the tour… |
| 18317–18572 | script | 19 | ===== v211: cloud backup ===== Everything on this phone is sealed here (AES-GCM, key made from the restore code) and sent to the helper, which only… |
| 18572–18588 | style | 1 | — |
| 18588–18693 | script | 8 | ===== v212: "Where was I?" notes ===== A game keeps short notes about where you stopped (g.wasI = [{t, x}], newest last, 20 kept). The newest shows… |
| 18693–18733 | style | 4 | — |
| 18733–19123 | script | 41 | ===== v213: price alerts (Steam + PlayStation Store) ===== Games you're waiting for (not played, not dropped, not competitive) are priced through… |
| 19123–19179 | style | 4 | — |
| 19179–19191 | style | 1 | ===== v215 fixes ===== |
| 19191–19309 | script | 12 | ===== v215: the frozen game page (#99, #102, #103), Read more (#85, #100), Taste names (#86), Queue rows (#89), 4K pictures by default (#84), and… |
| 19309–19324 | style | 1 | ===== v216: the story so far ===== |
| 19324–19381 | script | 5 | ===== v216: "the story so far". Under your newest 📍 note on a game's page, Nexi writes a short recap of the plot up to where the note says you are… |
| 19381–19481 | script | 9 | ===== v216: Steam's genres and "more like this". Steam's own tags are the genres players vote on ("Souls-like", "Metroidvania", "JRPG",… |
| 19481–19500 | script | 2 | — |
| 19500–19507 | style | 0 | ===== v217: release-date watcher + deal radar ===== |
| 19507–19627 | script | 11 | ===== v217: two new things Nexi does on his own (the owner loves automatic features). 1. Release-date watcher (`rdWatch`): every game you're waiting… |
| 19627–19631 | style | 0 | ===== v218: "Finished it?" (same card as Where was I?) ===== |
| 19631–19713 | script | 7 | ===== v218: "Finished it?" — Nexi notices when a game you're playing is probably done and asks, by himself. Signals (`fnWhy(g)`, first that fits):… |
| 19713–19731 | style | 1 | ===== v219: reviews are in (+ your score, predicted) ===== |
| 19731–19974 | script | 20 | ===== v219: three more things Nexi does by himself, and smarter ones (the owner: "I need more smarter and useful auto things"). 1. Reviews are in… |
| 19974–20005 | style | 3 | ===== v220: away report, finish forecast, time capsule, seasons ===== |
| 20005–20253 | script | 23 | ===== v220: six more things that happen by themselves (the owner: "more auto, unique, rare and out of the box"). 1. While you were away (`aw*`):… |
| 20253–20274 | style | 1 | ===== v221: logo fit, Nexi in the rail, dusty backlog, crystal ball ===== |
| 20274–20511 | script | 22 | ===== v221: the owner's and their friend's reports (#118 prices stuck on "checking…", #114 "fit the logo in the queue automatically", #123 "the rail… |
| 20511–20524 | script | 1 | ===== v222: #153 tour steps that point at things only there once you've scored games. The fixes are in the v184/v215 steps (fall back to the "What… |
| 20524–20536 | script | 1 | ===== v224: prices in one line (pxCard, store logos PX_LOGO) + a tap on seated Nexi hides the rail (railHideNow) ===== |
| 20536–20542 | style | 0 | ===== v225: the cover you tap flies into the game page ===== |
| 20542–20608 | script | 5 | ===== v225: the cover you tap flies into the game page ===== Opening a game from anything that shows its cover (Home, queue, library, rail, deck,… |
| 20608–20617 | script | 1 | ===== v226: platforms as logos on the game page (platChips / platHtml, next to platList) ===== |
| 20617–20671 | script | 3 | ===== v227: prices for games the stores' title search can't find (the owner's GTA 6: the PS Store's old search doesn't list it, though its store… |
| 20671–20680 | script | 1 | ===== v228: platform logos in one pill; price checks never skip a store for good (pxStale/pxGap, batches of 5); helper v13 ===== |
| 20681–20986 | script | 33 | ===== v229: Nexi's brain. The owner wants Nexi "super smart", saying a lot more, all of it different and useful, and using their name much more.… |
| 20986–21002 | style | 1 | v230: Browse and choose, every store |
| 21002–21116 | script | 9 | ===== v230: pictures straight from the stores. Browse and choose (Edit → Look) now shows, next to SteamGridDB and RAWG, the official art of Steam… |
| 21116–21125 | script | 1 | ===== v231 (daily fixer): #180/#181/#154 the tour's Next button answered slowly (it saved and opened the next screen inside the tap; now that waits… |
| 21125–21233 | style | 10 | ===== v232: the same shape for every open queue card and every game page ===== |
| 21233–21479 | script | 23 | ===== v232: the owner: "each queue card is a different size when open" and "the new game page lacks organisation: each game looks different because… |
| 21479–21556 | style | 8 | ===== v236: game suggestions you can see (big cards, a preview with store screenshots), swipe to add, Discover, share to add ===== |
| 21556–21872 | script | 28 | ===== v236: the owner: "adding Baldur's Gate 3 from Similar added the wrong game; the pictures are too small and I need more of a description; maybe… |
| 21872–21887 | style | 1 | ===== v237: screenshots on every game page; Grumble only takes real hits ===== |
| 21887–22010 | script | 11 | ===== v237: the owner: "I deleted Baldur's Gate and Grumble lost HP, which is wrong; and when I add a game, forget to say it's played and then say… |
| 22010–22158 | script | 13 | ===== v238: the owner: "only a few games got screenshots, and the ones that did took time to load. I want them instant and always ready, not fetched… |
| 22158–22262 | style | 12 | ===== v239/v242: Game room — a little 3D room built from your games (owner's isometric "B" room reference: layout and style, our colours). Plain CSS… |
| 22262–22634 | script | 31 | ===== v239: Game room. The owner sent an isometric illustration (a room built around a giant book-letter) and asked for something like it in 3D that… |
| 22634–22665 | style | 3 | v241: hours to beat from HowLongToBeat |
| 22665–22871 | script | 17 | ===== v241: hours to beat from HowLongToBeat (`hl*`). The owner: "most hours I type by hand; the automatic ones are super wrong". Two automatic… |
| 22871–22889 | script | 1 | ===== v243 (#313, the owner: "pressing on the text should expand it, not only Read more"): a tap on a cut-short description (or the story so far)… |
| 22889–22900 | script | 1 | ===== v244 (#319): a tap on a game's description (or the story so far) now closes it again too; see the v243 listener. Tour fixes (#307): the v236… |
| 22900–22909 | script | 1 | ===== v245 (#301): pnShelf skips the Finished shelf while its screen is hidden (it forced a full layout of the screen you were on, 130ms at 4x);… |
| 22909–22919 | script | 1 | ===== v246 (#309): no press animation. The owner: "when I touch anything an animation happened, delete that". Until v268 this script stripped… |
| 22919–22923 | style | 0 | v247: the rail's Add and Settings buttons draw the menu's own icons (they were the text glyphs ＋ and ⚙) |
| 22923–22935 | script | 1 | ===== v247: new menu icons, picked by the owner from two drawn sets: Home (house), Games (game case with a disc), Plan (calendar with a release… |
| 22935–23078 | script | 11 | ===== v248: three things that run by themselves. 1. Release days in your calendar: Settings → Extra features → turn on makes a random id (S.calId),… |
| 23078–23109 | style | 3 | v249: buy advice line under the prices, the drifting card on a game page, the shopping plan and queue clean-up sheets |
| 23109–23474 | script | 34 | ===== v249: three smarter things that run by themselves (the owner picked them from a list: "1 2 4"). 1. Buy advice (`sm*` / `smBuy`): price alerts… |
| 23474–23549 | script | 7 | ===== v249, smoother everywhere (the owner: "all tabs and windows lag"; "a lot of lags that even Nexi doesn't see"). 1. Nexi's own problem checks… |
| 23549–23578 | script | 2 | ===== v250 (the owner, with a screenshot of a "picked for you" card: "not all games have screenshots inside here, fix"): 1. The preview (gpLoad)… |
| 23578–23613 | style | 3 | ===== v251: the rail's live strip (replaces the queue covers) + Nexi sized to his seat ===== |
| 23613–23803 | script | 17 | ===== v251 (the owner, screenshot of the unfolded rail with the queue covers circled: "change them into something useful with live animation that is… |
| 23803–23811 | script | 1 | v253 (#338): the view switcher's marker is measured in the next frame (slidePill), not inside the tap |
| 23811–23825 | script | 1 | after v255 (#354 #355 #358): the lag checks skip frames that overlap a resize (fold/unfold, 0.8s after) or the time the app was hidden (asleep, in… |
| 23825–23836 | script | 1 | v256 (#352): the calling card is drawn in steps (callingCard, yf) |
| 23836–23911 | style | 8 | v259 three new card looks, from the owner's references (layout and style copied, our colours): dzp = Home "Playing now" colour cards, dzd = On deck… |
| 23911–24006 | script | 8 | v259 three new card looks (switch: S.dzX) |
| 24006–24047 | script | 3 | v260 rest: 12s without a touch, every looping animation holds still (no frames to draw, nothing for the GPU to do); the first touch, key, scroll or… |
| 24048–24052 | style | 0 | v261: the view pill's highlight slides on the GPU: it used to animate width (a layout every frame for .28s) |
| 24052–24068 | script | 1 | v261: smooth at the screen's own refresh rate. The highlight takes its new width at once and a transform animation (scaleX from the old width) makes… |
| 24069–24108 | script | 3 | v261: Nexi learned (the owner: "still lags a lot, I want 120 fps"): an animation the phone can't hand to its GPU makes the main thread draw every… |
| 24108–24115 | script | 1 | — |
| 24116–24123 | style | 0 | v263 (120 fps): Nexi's face: a mood used to morph his eyes and mouth (height, top, width, borders: main-thread transitions), and a blink restarted… |
| 24123–24267 | script | 11 | v263 (120 fps): a looping animation on something that isn't drawn (a hidden screen; a list row scrolled far enough that the browser skips drawing… |
| 24267–24331 | script | 5 | v264: Nexi learned (the owner's fifth "still lags", with nothing in his own reports since v263): the lag he couldn't see was the GPU blending a pile… |
| 24331–24337 | style | 1 | v265 smoothness check (prb*, last script): while it tests a suspect, that suspect is switched off for a little while |
| 24337–24461 | script | 9 | v265: the smoothness check (the owner, after five rounds: "it lags everywhere, as if it's too much: a fast scroll up or down on the Queue, sideways… |
| 24461–24475 | script | 1 | v265 (the owner: "if I scroll fast it lags, up or down on the Queue"): touch scrolling no longer waits for the app. Every screen but Home and every… |
| 24475–24479 | script | 0 | the smoothness check counts swiping a game's page to the next game as a sideways moment (it's not a scroll) |
| 24479–24520 | script | 4 | v265: Nexi learned (the blocking touchmove above sat on every screen for months and nothing noticed: it doesn't throw, doesn't look wrong, and the… |
| 24520–24680 | script | 12 | v266: the self-test (step 1 of the plan the owner agreed: "measure first, on the phone, before changing anything"). Settings → This build → "Run the… |
| 24680–24689 | style | 1 | v266 self-test result card (inside the diary sheet's box) |
| 24689–24699 | style | 1 | v271: the progress line draws itself to the step a game just reached. While it runs, the segment's own lines go transparent and .gd-fill (a dim… |
| 24699–24706 | script | 0 | — |
| 24706–24845 | script | 10 | v267 picture fit, layer 2 (layer 1 is sw.js: nothing bigger than the screen). Lists show pictures far smaller than the screen: a Queue row's faded… |
| 24845–24939 | script | 8 | v267: Nexi learned (the phone's self-test: 372 MP of pictures on the Queue for 14 MP on screen, one picture 70 MP; none of his checks looked at… |
| 24939–24948 | script | 1 | v269: the tidy-up (the owner: "organise the code, merge it, cut what isn't needed"). No new feature: onNav (screen-change hooks in one place), dead… |
| 24948–24992 | script | 3 | v270: buzzes on the iPhone too. Safari has no navigator.vibrate, but iOS 18+ gives a light tap each time an <input type="checkbox" switch> flips,… |
| 24992–25049 | script | 4 | v271: two moments that used to jump now move (the owner: "smoothness is rule number one, but I do like animations"). Picked with the… |
| 25049–25058 | script | 1 | #432: Settings (#sheet) let a sideways swipe through to the game page behind it; the other sheets already stop it (noHub) |
| 25058–25071 | script | 1 | v273 (the owner: "return the background to a solid gradient color, I hate how it looks"): the live background (v257 lights, specks and drifting art;… |
| 25071–25077 | style | 0 | v274 (the owner, #447): one Nexi per screen. Unfolded, Home's own Nexi (the face beside your greeting) and the rail's seated Nexi showed at once:… |
| 25077–25090 | script | 1 | — |
| 25090–25098 | style | 0 | #201 (the friend, iPhone/iPad: "Restore a code is not working"): while a keyboard (or a zoom) hides the bottom of the screen, the open sheet sits on… |
| 25098–25153 | script | 4 | #201: Safari's keyboard doesn't shrink the page, it slides over it. Sheets (#diary) sit at the bottom of the screen, so a short one (Restore from a… |
| 25153–25160 | script | 1 | — |
| 25160–25173 | script | 1 | v276 (the owner: "black with a little blue gradient, mostly black, with the option to edit how much gradient and the background colour"): the… |
