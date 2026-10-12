# Code map of index.html

Made by `node tools/map.mjs` (run it again after a release; line numbers move). 25772 lines, 1913 KB, 96 scripts, 60 styles.

How the file is built: one core style (line 15) and one core script (the biggest block below), then one patch per release, oldest first. A patch adds its own code and often wraps a core function (`const _of = openFull; openFull = function () { ... _of.apply(this, arguments) ... }`), so the **last** wrap runs first. To change what a function does, read every layer listed under "Wrapped functions". New code goes in the last script/style.

## Wrapped functions (most layers first)

| function | defined | layers (line: block) |
|---|---|---|
| `save` | 4129 | 8: 12885 core, 13532 core, 13588 core, 13949 core, 14519 v150, 18693 v211, 22153 v237, 23305 v248 |
| `renderHome` | 10952 | 7: 11254 core, 11622 core, 12997 core, 16725 v194, 17646 v204, 24315 v257, 24452 v259 |
| `bugNice` | 17203 | 7: 19554 v215, 21607 v232, 21707 v232, 22107 v236, 22236 v237, 22376 v238, 22811 v239 |
| `syncPlayed` | 3234 | 6: 11457 core, 19940 v218, 20407 v220, 20464 v220, 20715 v221, 23539 v249 |
| `applyLook` | 3493 | 6: 12606 core, 14285, 16727 v194, 17453 v200, 17645 v204, 20455 v220 |
| `bugLayout` | 17048 | 6: 17439 v199, 17457 v200, 17519 v202, 17859 v204, 18474 v208, 18527 v208 |
| `paintTab` | 4381 | 5: 11471 core, 11573 core, 11623 core, 12626 core, 18266 v206 |
| `pnBubSay` | 12143 | 5: 13040 core, 14193 core, 16354 v189, 17850 v204, 21188 v229 |
| `openEdit` | 5128 | 4: 12654 core, 15533 v178, 20158 v219, 23104 v241 |
| `pnBubLines` | 12073 | 4: 13058 core, 17837 v204, 20439 v220, 20654 v221 |
| `renderQueue` | 8506 | 3: 11287 core, 20642 v221, 21704 v232 |
| `pnBubShow` | 12158 | 3: 16355 v189, 17647 v204, 20691 v221 |
| `lookDesign` | 18412 | 3: 19534 v215, 20602 v221, 21601 v232 |
| `renderTaste` | 10398 | 2: 12931 core, 18221 v205 |
| `xFeatures` | 12599 | 2: 13001 core, 13416 core |
| `qCounts` | 7218 | 2: 13237 core, 21594 v232 |
| `queueList` | 7227 | 2: 13238 core, 21595 v232 |
| `pnPowerUp` | 13745 | 2: 14196 core, 24560 v260 |
| `setSub` | 3783 | 2: 18049 v205, 20647 v221 |
| `nxContext` | 13061 | 2: 18932 v212, 21223 v229 |
| `wiAdd` | 18850 | 2: 19615 v216, 19947 v218 |
| `pxRun` | 19073 | 2: 19854 v217, 23529 v249 |
| `nxPlan` | 13302 | 2: 19950 v218, 20121 v219 |
| `gdMarkup` | 16878 | 2: 20110 v219, 20378 v220 |
| `dyShow` | 10510 | 2: 20343 v220, 23781 v249 |
| `pnBubReact` | 11746 | 2: 20441 v220, 22168 v237 |
| `tasteInsightsPage` | 18076 | 2: 20733 v221, 24473 v259 |
| `closeFull` | 9731 | 2: 20852 v225, 23779 v249 |
| `gdShots` | 22178 | 2: 22342 v238, 22373 v238 |
| `sgdbFillOne` | 4501 | 2: 22380 v238, 23012 v241 |
| `applyBg` | 3550 | 2: 24317 v257, 24529 v260 |

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
- 17546 v203: `onNav("before", k => { if (k === "game") openAt = performance.now(); });`
- 18230 v205: `onNav("after", k => { if (k === "tab" && curTab() !== "taste") ixShown.clear(); });`
- 18765 v211: `onSettings("after", () => cbSetWire());`
- 19326 v213: `onSettings("after", () => pxSetWire());`
- 19328 v213: `onNav("after", (k, a) => { if (k !== "game") return;`
- 19616 v216: `onNav("after", (k, a) => { if (k !== "game") return; const g = S.games.find(x => x.id === a[0]);`
- 20114 v219: `onNav("after", (k, a) => { if (k !== "game") return;`
- 20342 v220: `onSettings("before", () => { const el = document.getElementById("sheet"); if (el) el._flkOpen = performance.no`
- 20646 v221: `onNav("after", k => { if (k === "tab" && !lfBox) setTimeout(() => { try { lfApply(); } catch (e) {} }, 60); })`
- 20649 v221: `onNav("before", (k, a) => { if (k !== "game") return; const g = S.games.find(x => x.id === a[0]), d = g ? dust`
- 20846 v225: `onNav("before", k => { if (k !== "game") return;`
- 22204 v237: `onNav("after", (k, a) => { if (k !== "game") return;`
- 23778 v249: `onNav("after", k => { if (k === "tab") lagWatch(() => "Switching to " + lagWhere()); else if (k === "game") la`
- 23780 v249: `onSettings("after", () => lagWatch("Opening Settings"));`
- 24316 v257: `onNav("after", k => { if (k === "tab") hlvKick(); });`
- 25030 v264: `onNav("after", k => { if (k === "tab") { STACK.go = null; stackArm(3500); } });`
- 25158 v265: `onNav("before", k => prbStart(k === "game" ? "open" : "switch", 1000));`
- 25197 v265: `onNav("before", () => hold(900));`
- 25563 v267: `onNav("after", () => requestAnimationFrame(fitRetry));`
- 25606 v267: `onNav("after", k => { if (k !== "sub") picArm(4000); });`
- 25647 v267: `onNav("after", k => { if (k !== "sub") picWait(); });`
- 25753 v271: `onNav("after", (k, a) => {`

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
| 17276–17342 | script | 6 | ===== v198: problem reports catch lag, not just freezes ===== The v196 check only saw 400ms+ freezes; the stutters you feel on a 120Hz screen are… |
| 17342–17446 | script | 8 | ===== v199: Nexi's eyes see more: cut-off text, text on top of text, a blank screen ===== Runs with bugLayout (idle, once a screen settles). Only… |
| 17446–17476 | script | 3 | ===== v200: the two lags the owner felt: Queue scrolling and a game page opening ===== |
| 17476–17488 | script | 1 | ===== v201: every picture in the SteamGridDB and Steam pickers shows its real size (4K / HD / Low) before you pick; SteamGridDB lists sharpest first… |
| 17488–17539 | script | 4 | ===== v202: 4K art really shows in 4K (the swap to the sharp copy was starved by the live countdown, see the liteUp kick), RAWG screenshots as… |
| 17539–17563 | script | 2 | ===== v203: a game page is sharp as soon as it opens. warmGame (touch-down, and each neighbour for the swipe) already downloaded and decoded the… |
| 17563–17637 | style | 7 | ===== v204: each personality has its own face, idle move and a signature move when it talks. Persona rules use :where() so they beat the plain face… |
| 17637–17881 | script | 21 | ===== v204: Nexi's personalities get their own lines (context-aware, ~20 each), faces and moves; more everyday lines for everyone; double tap opens… |
| 17881–18044 | style | 17 | ===== v205: Taste → Insights. Psychology, Patterns and Analytics in one page, ordered the way attention works: who you are (identity) → how good… |
| 18044–18238 | script | 19 | ===== v205: Taste → Insights (see the style block above for the order and why) ===== |
| 18238–18287 | script | 4 | ===== v206: lag fixes from the problem reports ===== render() rebuilt all seven screens on every save, add or delete (#58: a 260ms tap on Edit →… |
| 18287–18299 | style | 1 | v208: found by Nexi's new checks on the first sweep: queue rank numbers were drawn in the border colour (1.9:1, hard to read) and the small icon… |
| 18299–18552 | script | 21 | ===== v208: Nexi's new senses. The owner hit 10–15 problems he never saw: a page that flashed back while closing (#68), a description cut short with… |
| 18552–18569 | script | 1 | ===== v209: #81 (4K art stayed low-res). The fix itself is in liteUp's go() (retries, decode() fallback, LITE_NOFULL) and warmGame; this is the tour… |
| 18569–18824 | script | 19 | ===== v211: cloud backup ===== Everything on this phone is sealed here (AES-GCM, key made from the restore code) and sent to the helper, which only… |
| 18824–18840 | style | 1 | — |
| 18840–18945 | script | 8 | ===== v212: "Where was I?" notes ===== A game keeps short notes about where you stopped (g.wasI = [{t, x}], newest last, 20 kept). The newest shows… |
| 18945–18985 | style | 4 | — |
| 18985–19375 | script | 41 | ===== v213: price alerts (Steam + PlayStation Store) ===== Games you're waiting for (not played, not dropped, not competitive) are priced through… |
| 19375–19431 | style | 4 | — |
| 19431–19443 | style | 1 | ===== v215 fixes ===== |
| 19443–19560 | script | 12 | ===== v215: the frozen game page (#99, #102, #103), Read more (#85, #100), Taste names (#86), Queue rows (#89), 4K pictures by default (#84), and… |
| 19560–19575 | style | 1 | ===== v216: the story so far ===== |
| 19575–19632 | script | 5 | ===== v216: "the story so far". Under your newest 📍 note on a game's page, Nexi writes a short recap of the plot up to where the note says you are… |
| 19632–19732 | script | 9 | ===== v216: Steam's genres and "more like this". Steam's own tags are the genres players vote on ("Souls-like", "Metroidvania", "JRPG",… |
| 19732–19751 | script | 2 | — |
| 19751–19758 | style | 0 | ===== v217: release-date watcher + deal radar ===== |
| 19758–19878 | script | 11 | ===== v217: two new things Nexi does on his own (the owner loves automatic features). 1. Release-date watcher (`rdWatch`): every game you're waiting… |
| 19878–19882 | style | 0 | ===== v218: "Finished it?" (same card as Where was I?) ===== |
| 19882–19964 | script | 7 | ===== v218: "Finished it?" — Nexi notices when a game you're playing is probably done and asks, by himself. Signals (`fnWhy(g)`, first that fits):… |
| 19964–19982 | style | 1 | ===== v219: reviews are in (+ your score, predicted) ===== |
| 19982–20225 | script | 20 | ===== v219: three more things Nexi does by himself, and smarter ones (the owner: "I need more smarter and useful auto things"). 1. Reviews are in… |
| 20225–20256 | style | 3 | ===== v220: away report, finish forecast, time capsule, seasons ===== |
| 20256–20504 | script | 23 | ===== v220: six more things that happen by themselves (the owner: "more auto, unique, rare and out of the box"). 1. While you were away (`aw*`):… |
| 20504–20525 | style | 1 | ===== v221: logo fit, Nexi in the rail, dusty backlog, crystal ball ===== |
| 20525–20762 | script | 22 | ===== v221: the owner's and their friend's reports (#118 prices stuck on "checking…", #114 "fit the logo in the queue automatically", #123 "the rail… |
| 20762–20775 | script | 1 | ===== v222: #153 tour steps that point at things only there once you've scored games. The fixes are in the v184/v215 steps (fall back to the "What… |
| 20775–20787 | script | 1 | ===== v224: prices in one line (pxCard, store logos PX_LOGO) + a tap on seated Nexi hides the rail (railHideNow) ===== |
| 20787–20793 | style | 0 | ===== v225: the cover you tap flies into the game page ===== |
| 20793–20859 | script | 5 | ===== v225: the cover you tap flies into the game page ===== Opening a game from anything that shows its cover (Home, queue, library, rail, deck,… |
| 20859–20868 | script | 1 | ===== v226: platforms as logos on the game page (platChips / platHtml, next to platList) ===== |
| 20868–20922 | script | 3 | ===== v227: prices for games the stores' title search can't find (the owner's GTA 6: the PS Store's old search doesn't list it, though its store… |
| 20922–20931 | script | 1 | ===== v228: platform logos in one pill; price checks never skip a store for good (pxStale/pxGap, batches of 5); helper v13 ===== |
| 20932–21237 | script | 33 | ===== v229: Nexi's brain. The owner wants Nexi "super smart", saying a lot more, all of it different and useful, and using their name much more.… |
| 21237–21253 | style | 1 | v230: Browse and choose, every store |
| 21253–21367 | script | 9 | ===== v230: pictures straight from the stores. Browse and choose (Edit → Look) now shows, next to SteamGridDB and RAWG, the official art of Steam… |
| 21367–21376 | script | 1 | ===== v231 (daily fixer): #180/#181/#154 the tour's Next button answered slowly (it saved and opened the next screen inside the tap; now that waits… |
| 21376–21484 | style | 10 | ===== v232: the same shape for every open queue card and every game page ===== |
| 21484–21730 | script | 23 | ===== v232: the owner: "each queue card is a different size when open" and "the new game page lacks organisation: each game looks different because… |
| 21730–21807 | style | 8 | ===== v236: game suggestions you can see (big cards, a preview with store screenshots), swipe to add, Discover, share to add ===== |
| 21807–22123 | script | 28 | ===== v236: the owner: "adding Baldur's Gate 3 from Similar added the wrong game; the pictures are too small and I need more of a description; maybe… |
| 22123–22138 | style | 1 | ===== v237: screenshots on every game page; Grumble only takes real hits ===== |
| 22138–22261 | script | 11 | ===== v237: the owner: "I deleted Baldur's Gate and Grumble lost HP, which is wrong; and when I add a game, forget to say it's played and then say… |
| 22261–22409 | script | 13 | ===== v238: the owner: "only a few games got screenshots, and the ones that did took time to load. I want them instant and always ready, not fetched… |
| 22409–22513 | style | 12 | ===== v239/v242: Game room — a little 3D room built from your games (owner's isometric "B" room reference: layout and style, our colours). Plain CSS… |
| 22513–22885 | script | 31 | ===== v239: Game room. The owner sent an isometric illustration (a room built around a giant book-letter) and asked for something like it in 3D that… |
| 22885–22916 | style | 3 | v241: hours to beat from HowLongToBeat |
| 22916–23122 | script | 17 | ===== v241: hours to beat from HowLongToBeat (`hl*`). The owner: "most hours I type by hand; the automatic ones are super wrong". Two automatic… |
| 23122–23140 | script | 1 | ===== v243 (#313, the owner: "pressing on the text should expand it, not only Read more"): a tap on a cut-short description (or the story so far)… |
| 23140–23151 | script | 1 | ===== v244 (#319): a tap on a game's description (or the story so far) now closes it again too; see the v243 listener. Tour fixes (#307): the v236… |
| 23151–23160 | script | 1 | ===== v245 (#301): pnShelf skips the Finished shelf while its screen is hidden (it forced a full layout of the screen you were on, 130ms at 4x);… |
| 23160–23170 | script | 1 | ===== v246 (#309): no press animation. The owner: "when I touch anything an animation happened, delete that". Until v268 this script stripped… |
| 23170–23174 | style | 0 | v247: the rail's Add and Settings buttons draw the menu's own icons (they were the text glyphs ＋ and ⚙) |
| 23174–23186 | script | 1 | ===== v247: new menu icons, picked by the owner from two drawn sets: Home (house), Games (game case with a disc), Plan (calendar with a release… |
| 23186–23329 | script | 11 | ===== v248: three things that run by themselves. 1. Release days in your calendar: Settings → Extra features → turn on makes a random id (S.calId),… |
| 23329–23360 | style | 3 | v249: buy advice line under the prices, the drifting card on a game page, the shopping plan and queue clean-up sheets |
| 23360–23725 | script | 34 | ===== v249: three smarter things that run by themselves (the owner picked them from a list: "1 2 4"). 1. Buy advice (`sm*` / `smBuy`): price alerts… |
| 23725–23800 | script | 7 | ===== v249, smoother everywhere (the owner: "all tabs and windows lag"; "a lot of lags that even Nexi doesn't see"). 1. Nexi's own problem checks… |
| 23800–23829 | script | 2 | ===== v250 (the owner, with a screenshot of a "picked for you" card: "not all games have screenshots inside here, fix"): 1. The preview (gpLoad)… |
| 23829–23864 | style | 3 | ===== v251: the rail's live strip (replaces the queue covers) + Nexi sized to his seat ===== |
| 23864–24054 | script | 17 | ===== v251 (the owner, screenshot of the unfolded rail with the queue covers circled: "change them into something useful with live animation that is… |
| 24054–24062 | script | 1 | v253 (#338): the view switcher's marker is measured in the next frame (slidePill), not inside the tap |
| 24062–24076 | script | 1 | after v255 (#354 #355 #358): the lag checks skip frames that overlap a resize (fold/unfold, 0.8s after) or the time the app was hidden (asleep, in… |
| 24076–24087 | script | 1 | v256 (#352): the calling card is drawn in steps (callingCard, yf) |
| 24087–24131 | style | 4 | v257 live background (hlv*): a game's art and three lights taken from it drift behind the screen, plus rising specks. Only transform/opacity move,… |
| 24131–24352 | script | 13 | v257 live background. Switches: S.hlvOff (Home), S.hlvX.games / .game / .plan / .taste (Customise on each screen) |
| 24352–24427 | style | 8 | v259 three new card looks, from the owner's references (layout and style copied, our colours): dzp = Home "Playing now" colour cards, dzd = On deck… |
| 24427–24522 | script | 8 | v259 three new card looks (switch: S.dzX) |
| 24522–24527 | style | 0 | v260 smoothness + battery: fewer full-screen layers stacked on every frame |
| 24527–24574 | script | 3 | v260: #bgtint only while an art backdrop is up |
| 24575–24579 | style | 0 | v261: the view pill's highlight slides on the GPU: it used to animate width (a layout every frame for .28s) |
| 24579–24595 | script | 1 | v261: smooth at the screen's own refresh rate. The highlight takes its new width at once and a transform animation (scaleX from the old width) makes… |
| 24596–24635 | script | 3 | v261: Nexi learned (the owner: "still lags a lot, I want 120 fps"): an animation the phone can't hand to its GPU makes the main thread draw every… |
| 24635–24642 | script | 1 | — |
| 24643–24650 | style | 0 | v263 (120 fps): Nexi's face: a mood used to morph his eyes and mouth (height, top, width, borders: main-thread transitions), and a blink restarted… |
| 24650–24794 | script | 11 | v263 (120 fps): a looping animation on something that isn't drawn (a hidden screen; a list row scrolled far enough that the browser skips drawing… |
| 24794–24801 | style | 1 | v264: the whole background in one layer (hlvf*, last script). The flat canvas keeps v257's own canvas rule (its own layer, will-change: transform)… |
| 24801–25041 | script | 17 | v264 (the owner, the fifth time: "still lags"). v263 made the main thread quiet; what was left is the GPU. A trace of what it draws per frame (the… |
| 25041–25049 | style | 1 | v265 smoothness check (prb*, last script): while it tests a suspect, that suspect is switched off for a little while |
| 25049–25173 | script | 9 | v265: the smoothness check (the owner, after five rounds: "it lags everywhere, as if it's too much: a fast scroll up or down on the Queue, sideways… |
| 25173–25187 | script | 1 | v265 (the owner: "if I scroll fast it lags, up or down on the Queue"): touch scrolling no longer waits for the app. Every screen but Home and every… |
| 25187–25202 | script | 1 | v265: the light behind the screen holds still while you're touching, scrolling or switching (it moves about a pixel per step, so a pause can't be… |
| 25202–25243 | script | 4 | v265: Nexi learned (the blocking touchmove above sat on every screen for months and nothing noticed: it doesn't throw, doesn't look wrong, and the… |
| 25243–25403 | script | 12 | v266: the self-test (step 1 of the plan the owner agreed: "measure first, on the phone, before changing anything"). Settings → This build → "Run the… |
| 25403–25412 | style | 1 | v266 self-test result card (inside the diary sheet's box) |
| 25412–25422 | style | 1 | v271: the progress line draws itself to the step a game just reached. While it runs, the segment's own lines go transparent and .gd-fill (a dim… |
| 25422–25429 | script | 0 | — |
| 25429–25568 | script | 10 | v267 picture fit, layer 2 (layer 1 is sw.js: nothing bigger than the screen). Lists show pictures far smaller than the screen: a Queue row's faded… |
| 25568–25662 | script | 8 | v267: Nexi learned (the phone's self-test: 372 MP of pictures on the Queue for 14 MP on screen, one picture 70 MP; none of his checks looked at… |
| 25662–25671 | script | 1 | v269: the tidy-up (the owner: "organise the code, merge it, cut what isn't needed"). No new feature: onNav (screen-change hooks in one place), dead… |
| 25671–25715 | script | 3 | v270: buzzes on the iPhone too. Safari has no navigator.vibrate, but iOS 18+ gives a light tap each time an <input type="checkbox" switch> flips,… |
| 25715–25772 | script | 4 | v271: two moments that used to jump now move (the owner: "smoothness is rule number one, but I do like animations"). Picked with the… |
