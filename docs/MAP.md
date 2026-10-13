# Code map of index.html

Made by `node tools/map.mjs` (run it again after a release; line numbers move). 25096 lines, 1870 KB, 98 scripts, 58 styles.

How the file is built: one core style (line 15) and one core script (the biggest block below), then one patch per release, oldest first. A patch adds its own code and often wraps a core function (`const _of = openFull; openFull = function () { ... _of.apply(this, arguments) ... }`), so the **last** wrap runs first. To change what a function does, read every layer listed under "Wrapped functions". New code goes in the last script/style.

## Wrapped functions (most layers first)

| function | defined | layers (line: block) |
|---|---|---|
| `save` | 4020 | 8: 12648 core, 13295 core, 13351 core, 13707 core, 14276 v150, 18447 v211, 21908 v237, 23060 v248 |
| `bugNice` | 16953 | 7: 19309 v215, 21362 v232, 21462 v232, 21862 v236, 21991 v237, 22131 v238, 22566 v239 |
| `renderHome` | 10739 | 6: 11041 core, 11409 core, 12760 core, 16475 v194, 17398 v204, 23942 v259 |
| `syncPlayed` | 3215 | 6: 11244 core, 19695 v218, 20162 v220, 20219 v220, 20470 v221, 23294 v249 |
| `applyLook` | 3474 | 6: 12369 core, 14042, 16477 v194, 17205 v200, 17397 v204, 20210 v220 |
| `bugLayout` | 16798 | 6: 17191 v199, 17209 v200, 17271 v202, 17611 v204, 18228 v208, 18281 v208 |
| `paintTab` | 4272 | 5: 11258 core, 11360 core, 11410 core, 12389 core, 18018 v206 |
| `pnBubSay` | 11906 | 5: 12803 core, 13951 core, 16104 v189, 17602 v204, 20943 v229 |
| `openEdit` | 5017 | 4: 12417 core, 15283 v178, 19913 v219, 22859 v241 |
| `pnBubLines` | 11836 | 4: 12821 core, 17589 v204, 20194 v220, 20409 v221 |
| `renderQueue` | 8311 | 3: 11074 core, 20397 v221, 21459 v232 |
| `pnBubShow` | 11920 | 3: 16105 v189, 17399 v204, 20446 v221 |
| `lookDesign` | 18166 | 3: 19289 v215, 20357 v221, 21356 v232 |
| `renderTaste` | 10203 | 2: 12694 core, 17973 v205 |
| `xFeatures` | 12362 | 2: 12764 core, 13179 core |
| `qCounts` | 7023 | 2: 13000 core, 21349 v232 |
| `queueList` | 7032 | 2: 13001 core, 21350 v232 |
| `pnPowerUp` | 13503 | 2: 13953 core, 24039 v260 |
| `setSub` | 3676 | 2: 17801 v205, 20402 v221 |
| `nxContext` | 12824 | 2: 18686 v212, 20978 v229 |
| `wiAdd` | 18604 | 2: 19370 v216, 19702 v218 |
| `pxRun` | 18827 | 2: 19609 v217, 23284 v249 |
| `nxPlan` | 13065 | 2: 19705 v218, 19876 v219 |
| `gdMarkup` | 16628 | 2: 19865 v219, 20133 v220 |
| `dyShow` | 10315 | 2: 20098 v220, 23536 v249 |
| `pnBubReact` | 11519 | 2: 20196 v220, 21923 v237 |
| `tasteInsightsPage` | 17828 | 2: 20488 v221, 23963 v259 |
| `closeFull` | 9536 | 2: 20607 v225, 23534 v249 |
| `gdShots` | 21933 | 2: 22097 v238, 22128 v238 |
| `sgdbFillOne` | 4392 | 2: 22135 v238, 22767 v241 |

## Screen-change hooks (onNav / onSettings, since v269)

goTab / goSub / openFull run every `onNav("before"|"after", fn)` hook and openSettings every `onSettings(...)` hook (one wrap each, defined at line 12400, in registration order below). New code registers here; `kit.mjs syntax` fails on a new wrap of those four.

- 12415: `onNav("after", (k, a) => { if (k === "sub") try { uVisit(a[0]); } catch (e) {} });`
- 12416: `onNav("before", (k, a) => { if (k === "game" && (hubAt !== a[0] || !$("#full.on"))) uCount("o", "game"); });`
- 12418: `onSettings("before", (k, a) => { const sc = a[0]; uCount("o", "settings" + (sc && sc !== "app" ? "/" + sc : ""`
- 15282 v178: `onSettings("after", () => { $("#sheet-in").classList.remove("drill", "ssearch"); calmSheet(true); });`
- 15885 v187: `onSettings("after", () => setTimeout(helperCheck, 50));`
- 16444 v194: `onSettings("after", () => shxSetWire());`
- 16837 v196: `onNav("after", () => bugSeen());`
- 16838 v196: `onSettings("after", () => { bugCrumb("screen settings"); bugLayoutSoon(); });`
- 17298 v203: `onNav("before", k => { if (k === "game") openAt = performance.now(); });`
- 17982 v205: `onNav("after", k => { if (k === "tab" && curTab() !== "taste") ixShown.clear(); });`
- 18519 v211: `onSettings("after", () => cbSetWire());`
- 19080 v213: `onSettings("after", () => pxSetWire());`
- 19082 v213: `onNav("after", (k, a) => { if (k !== "game") return;`
- 19371 v216: `onNav("after", (k, a) => { if (k !== "game") return; const g = S.games.find(x => x.id === a[0]);`
- 19869 v219: `onNav("after", (k, a) => { if (k !== "game") return;`
- 20097 v220: `onSettings("before", () => { const el = document.getElementById("sheet"); if (el) el._flkOpen = performance.no`
- 20401 v221: `onNav("after", k => { if (k === "tab" && !lfBox) setTimeout(() => { try { lfApply(); } catch (e) {} }, 60); })`
- 20404 v221: `onNav("before", (k, a) => { if (k !== "game") return; const g = S.games.find(x => x.id === a[0]), d = g ? dust`
- 20601 v225: `onNav("before", k => { if (k !== "game") return;`
- 21959 v237: `onNav("after", (k, a) => { if (k !== "game") return;`
- 23533 v249: `onNav("after", k => { if (k === "tab") lagWatch(() => "Switching to " + lagWhere()); else if (k === "game") la`
- 23535 v249: `onSettings("after", () => lagWatch("Opening Settings"));`
- 24329 v264: `onNav("after", k => { if (k === "tab") { STACK.go = null; stackArm(3500); } });`
- 24452 v265: `onNav("before", k => prbStart(k === "game" ? "open" : "switch", 1000));`
- 24846 v267: `onNav("after", () => requestAnimationFrame(fitRetry));`
- 24889 v267: `onNav("after", k => { if (k !== "sub") picArm(4000); });`
- 24930 v267: `onNav("after", k => { if (k !== "sub") picWait(); });`
- 25036 v271: `onNav("after", (k, a) => {`

## Blocks

| lines | kind | KB | what |
|---|---|---|---|
| 15–2986 | style | 210 | **CORE** — — |
| 2990–3025 | script | 3 | v252: the card's markup, here so the launch intro can use it before the rest of the page loads. o: k (speed), ar (width / height), word, rv (reveal… |
| 3138–14002 | script | 698 | **CORE** — v169 speed: building a date/number formatter is slow, and the app formats dates for every card on every redraw. Keep one formatter per… |
| 14003–14021 | style | 1 | — |
| 14021–14061 | script | 2 | — |
| 14062–14098 | style | 3 | v147 Top board: ranked rows, big score, share as an image |
| 14098–14189 | script | 7 | v147 Top board in Played, plus a share-as-image button |
| 14190–14216 | style | 2 | v150 badges |
| 14216–14283 | script | 4 | v150 badges: worked out from what you've done, saved with the date you earned them |
| 14283–14289 | script | 0 | v151 art strength slider: live while dragging, saved when let go |
| 14289–14290 | script | 0 | — |
| 14290–14307 | style | 1 | v155 light board motion: only fade + slide, which the phone's graphics chip does for free |
| 14307–14327 | script | 2 | — |
| 14327–14353 | style | 2 | v156 scroll-driven motion: the browser ties these to your finger on its own graphics thread, so they stay at full refresh rate however fast you… |
| 14353–14361 | style | 1 | v157 the real drag: a full-screen blurred picture sat behind every screen, slowly zooming forever, so the phone re-blurred the whole screen on every… |
| 14361–14368 | script | 0 | — |
| 14369–14388 | style | 1 | v163: Why you'll love it — one sheet, fades and slides in, nothing loops |
| 14388–14458 | script | 4 | v163: "Why you'll love it" — sends your ratings and this game to your Cloudflare helper, which asks free Workers AI why it fits your taste (or… |
| 14459–14490 | script | 2 | v166: reset scores — one game from its page, or everything from Settings → Your data. Games, art, hours and diary stay; played games stay played,… |
| 14491–14501 | style | 1 | v167 Nexi's glow sometimes showed as a square box. Three stacked fixes (blur, clip-path, mask) each add a separate layer the phone can drop… |
| 14502–14537 | style | 3 | v169 speed: these animations changed colours/sizes/positions, which makes the phone redraw on every frame. Same looks, now moved or faded instead,… |
| 14538–14549 | style | 1 | v171 swipe a queue card left to delete the game. Only the card's contents slide (translate), the red button waits underneath. |
| 14549–14610 | script | 3 | — |
| 14611–14626 | style | 1 | v172 what's-new tour: a small card with Nexi's words, while Nexi points at the real button |
| 14626–14819 | script | 17 | v172: after every update Nexi shows what's new — where it is, and a nudge to try it. HOW TO ADD ONE: push an entry to WHATS_NEW with the version it… |
| 14819–15061 | style | 22 | ===== v174 Home dashboard (layout from the owner's reference, our black + blue colours) ===== |
| 15061–15147 | style | 8 | ===== v177 play sessions ===== |
| 15147–15287 | script | 10 | ===== v178 calmer settings and edit: search, most-used shortcuts, one section at a time, fewer knobs up front ===== |
| 15287–15333 | style | 4 | ===== v178 calmer settings ===== |
| 15333–15346 | style | 1 | ===== v180 finish month picker ===== |
| 15346–15354 | style | 1 | ===== v181 theme picker ===== |
| 15354–15501 | script | 13 | ===== v181 Game Shuffler: swipe through your games one at a time (right = tempted, left = not now). Styled after the owner's chess reference: a big… |
| 15501–15582 | style | 8 | ===== v181 Game Shuffler (chess reference layout, our black + theme accent) ===== |
| 15582–15608 | style | 2 | ===== v182 lists ===== |
| 15608–15645 | style | 3 | ===== v183 extras off by default, quick duel / score sheets, usage table, quick add ===== |
| 15645–15668 | script | 2 | ===== v183 what's new + settings extras ===== |
| 15668–15713 | script | 3 | ---- badges that keep going as you duel ---- |
| 15713–15790 | style | 7 | ===== v184 ranking: tiers, confidence dots, On deck, Nexi on Home, insights ===== |
| 15790–15810 | script | 2 | ===== v185 what's new ===== |
| 15810–15857 | style | 4 | ===== v185 Nexi chat, personalities, similar games ===== |
| 15857–15865 | script | 0 | ===== v186 what's new ===== |
| 15865–15892 | script | 3 | ===== v187: similar games picked by the AI and checked on RAWG; helper status in Settings → Nexi ===== |
| 15892–16010 | style | 8 | ===== v188 "Clean" style: the rest of the app speaks Home's language ===== One layer, scoped to html.ui2 (Settings → Look of the app → Style →… |
| 16010–16021 | script | 1 | ===== v188 what's new: the Clean style ===== |
| 16021–16041 | style | 2 | ===== v189 tour: Nexi drops out of the notch / camera hole, then rides on the tour card ===== |
| 16041–16142 | script | 8 | ===== v189 tour: Nexi comes out of the notch and stays with the tour card ===== |
| 16142–16298 | style | 12 | ===== v194 "Sicker everywhere": Home's look on every screen (Clean style only) ===== Each screen's header is a card like Home's: a big title, live… |
| 16298–16508 | script | 16 | ===== v194: every screen's header is a card like Home's ===== |
| 16508–16625 | style | 12 | ===== v195: the Add screen and a game's page in Home's language (both switchable) ===== |
| 16625–16675 | script | 4 | ===== v195: a game's page, New style ===== |
| 16675–16680 | style | 0 | v196: on phones under ~400px the pick card was 352px wide whatever the screen (its 220px min-height through the 16:10 ratio set a minimum width) and… |
| 16680–16925 | script | 19 | ===== v196: problem reports. The app notices when something breaks and files it (Settings → This build) ===== What it watches: script errors, a page… |
| 16925–16943 | style | 2 | — |
| 16943–17026 | script | 6 | ===== v197: Nexi says when he's spotted (and already reported) a problem, and the report sheet lists them ===== |
| 17026–17094 | script | 6 | ===== v198: problem reports catch lag, not just freezes ===== The v196 check only saw 400ms+ freezes; the stutters you feel on a 120Hz screen are… |
| 17094–17198 | script | 8 | ===== v199: Nexi's eyes see more: cut-off text, text on top of text, a blank screen ===== Runs with bugLayout (idle, once a screen settles). Only… |
| 17198–17228 | script | 3 | ===== v200: the two lags the owner felt: Queue scrolling and a game page opening ===== |
| 17228–17240 | script | 1 | ===== v201: every picture in the SteamGridDB and Steam pickers shows its real size (4K / HD / Low) before you pick; SteamGridDB lists sharpest first… |
| 17240–17291 | script | 4 | ===== v202: 4K art really shows in 4K (the swap to the sharp copy was starved by the live countdown, see the liteUp kick), RAWG screenshots as… |
| 17291–17315 | script | 2 | ===== v203: a game page is sharp as soon as it opens. warmGame (touch-down, and each neighbour for the swipe) already downloaded and decoded the… |
| 17315–17389 | style | 7 | ===== v204: each personality has its own face, idle move and a signature move when it talks. Persona rules use :where() so they beat the plain face… |
| 17389–17633 | script | 21 | ===== v204: Nexi's personalities get their own lines (context-aware, ~20 each), faces and moves; more everyday lines for everyone; double tap opens… |
| 17633–17796 | style | 17 | ===== v205: Taste → Insights. Psychology, Patterns and Analytics in one page, ordered the way attention works: who you are (identity) → how good… |
| 17796–17990 | script | 19 | ===== v205: Taste → Insights (see the style block above for the order and why) ===== |
| 17990–18039 | script | 4 | ===== v206: lag fixes from the problem reports ===== render() rebuilt all seven screens on every save, add or delete (#58: a 260ms tap on Edit →… |
| 18039–18051 | style | 1 | v208: found by Nexi's new checks on the first sweep: queue rank numbers were drawn in the border colour (1.9:1, hard to read) and the small icon… |
| 18051–18306 | script | 21 | ===== v208: Nexi's new senses. The owner hit 10–15 problems he never saw: a page that flashed back while closing (#68), a description cut short with… |
| 18306–18323 | script | 1 | ===== v209: #81 (4K art stayed low-res). The fix itself is in liteUp's go() (retries, decode() fallback, LITE_NOFULL) and warmGame; this is the tour… |
| 18323–18578 | script | 19 | ===== v211: cloud backup ===== Everything on this phone is sealed here (AES-GCM, key made from the restore code) and sent to the helper, which only… |
| 18578–18594 | style | 1 | — |
| 18594–18699 | script | 8 | ===== v212: "Where was I?" notes ===== A game keeps short notes about where you stopped (g.wasI = [{t, x}], newest last, 20 kept). The newest shows… |
| 18699–18739 | style | 4 | — |
| 18739–19129 | script | 41 | ===== v213: price alerts (Steam + PlayStation Store) ===== Games you're waiting for (not played, not dropped, not competitive) are priced through… |
| 19129–19185 | style | 4 | — |
| 19185–19197 | style | 1 | ===== v215 fixes ===== |
| 19197–19315 | script | 12 | ===== v215: the frozen game page (#99, #102, #103), Read more (#85, #100), Taste names (#86), Queue rows (#89), 4K pictures by default (#84), and… |
| 19315–19330 | style | 1 | ===== v216: the story so far ===== |
| 19330–19387 | script | 5 | ===== v216: "the story so far". Under your newest 📍 note on a game's page, Nexi writes a short recap of the plot up to where the note says you are… |
| 19387–19487 | script | 9 | ===== v216: Steam's genres and "more like this". Steam's own tags are the genres players vote on ("Souls-like", "Metroidvania", "JRPG",… |
| 19487–19506 | script | 2 | — |
| 19506–19513 | style | 0 | ===== v217: release-date watcher + deal radar ===== |
| 19513–19633 | script | 11 | ===== v217: two new things Nexi does on his own (the owner loves automatic features). 1. Release-date watcher (`rdWatch`): every game you're waiting… |
| 19633–19637 | style | 0 | ===== v218: "Finished it?" (same card as Where was I?) ===== |
| 19637–19719 | script | 7 | ===== v218: "Finished it?" — Nexi notices when a game you're playing is probably done and asks, by himself. Signals (`fnWhy(g)`, first that fits):… |
| 19719–19737 | style | 1 | ===== v219: reviews are in (+ your score, predicted) ===== |
| 19737–19980 | script | 20 | ===== v219: three more things Nexi does by himself, and smarter ones (the owner: "I need more smarter and useful auto things"). 1. Reviews are in… |
| 19980–20011 | style | 3 | ===== v220: away report, finish forecast, time capsule, seasons ===== |
| 20011–20259 | script | 23 | ===== v220: six more things that happen by themselves (the owner: "more auto, unique, rare and out of the box"). 1. While you were away (`aw*`):… |
| 20259–20280 | style | 1 | ===== v221: logo fit, Nexi in the rail, dusty backlog, crystal ball ===== |
| 20280–20517 | script | 22 | ===== v221: the owner's and their friend's reports (#118 prices stuck on "checking…", #114 "fit the logo in the queue automatically", #123 "the rail… |
| 20517–20530 | script | 1 | ===== v222: #153 tour steps that point at things only there once you've scored games. The fixes are in the v184/v215 steps (fall back to the "What… |
| 20530–20542 | script | 1 | ===== v224: prices in one line (pxCard, store logos PX_LOGO) + a tap on seated Nexi hides the rail (railHideNow) ===== |
| 20542–20548 | style | 0 | ===== v225: the cover you tap flies into the game page ===== |
| 20548–20614 | script | 5 | ===== v225: the cover you tap flies into the game page ===== Opening a game from anything that shows its cover (Home, queue, library, rail, deck,… |
| 20614–20623 | script | 1 | ===== v226: platforms as logos on the game page (platChips / platHtml, next to platList) ===== |
| 20623–20677 | script | 3 | ===== v227: prices for games the stores' title search can't find (the owner's GTA 6: the PS Store's old search doesn't list it, though its store… |
| 20677–20686 | script | 1 | ===== v228: platform logos in one pill; price checks never skip a store for good (pxStale/pxGap, batches of 5); helper v13 ===== |
| 20687–20992 | script | 33 | ===== v229: Nexi's brain. The owner wants Nexi "super smart", saying a lot more, all of it different and useful, and using their name much more.… |
| 20992–21008 | style | 1 | v230: Browse and choose, every store |
| 21008–21122 | script | 9 | ===== v230: pictures straight from the stores. Browse and choose (Edit → Look) now shows, next to SteamGridDB and RAWG, the official art of Steam… |
| 21122–21131 | script | 1 | ===== v231 (daily fixer): #180/#181/#154 the tour's Next button answered slowly (it saved and opened the next screen inside the tap; now that waits… |
| 21131–21239 | style | 10 | ===== v232: the same shape for every open queue card and every game page ===== |
| 21239–21485 | script | 23 | ===== v232: the owner: "each queue card is a different size when open" and "the new game page lacks organisation: each game looks different because… |
| 21485–21562 | style | 8 | ===== v236: game suggestions you can see (big cards, a preview with store screenshots), swipe to add, Discover, share to add ===== |
| 21562–21878 | script | 28 | ===== v236: the owner: "adding Baldur's Gate 3 from Similar added the wrong game; the pictures are too small and I need more of a description; maybe… |
| 21878–21893 | style | 1 | ===== v237: screenshots on every game page; Grumble only takes real hits ===== |
| 21893–22016 | script | 11 | ===== v237: the owner: "I deleted Baldur's Gate and Grumble lost HP, which is wrong; and when I add a game, forget to say it's played and then say… |
| 22016–22164 | script | 13 | ===== v238: the owner: "only a few games got screenshots, and the ones that did took time to load. I want them instant and always ready, not fetched… |
| 22164–22268 | style | 12 | ===== v239/v242: Game room — a little 3D room built from your games (owner's isometric "B" room reference: layout and style, our colours). Plain CSS… |
| 22268–22640 | script | 31 | ===== v239: Game room. The owner sent an isometric illustration (a room built around a giant book-letter) and asked for something like it in 3D that… |
| 22640–22671 | style | 3 | v241: hours to beat from HowLongToBeat |
| 22671–22877 | script | 17 | ===== v241: hours to beat from HowLongToBeat (`hl*`). The owner: "most hours I type by hand; the automatic ones are super wrong". Two automatic… |
| 22877–22895 | script | 1 | ===== v243 (#313, the owner: "pressing on the text should expand it, not only Read more"): a tap on a cut-short description (or the story so far)… |
| 22895–22906 | script | 1 | ===== v244 (#319): a tap on a game's description (or the story so far) now closes it again too; see the v243 listener. Tour fixes (#307): the v236… |
| 22906–22915 | script | 1 | ===== v245 (#301): pnShelf skips the Finished shelf while its screen is hidden (it forced a full layout of the screen you were on, 130ms at 4x);… |
| 22915–22925 | script | 1 | ===== v246 (#309): no press animation. The owner: "when I touch anything an animation happened, delete that". Until v268 this script stripped… |
| 22925–22929 | style | 0 | v247: the rail's Add and Settings buttons draw the menu's own icons (they were the text glyphs ＋ and ⚙) |
| 22929–22941 | script | 1 | ===== v247: new menu icons, picked by the owner from two drawn sets: Home (house), Games (game case with a disc), Plan (calendar with a release… |
| 22941–23084 | script | 11 | ===== v248: three things that run by themselves. 1. Release days in your calendar: Settings → Extra features → turn on makes a random id (S.calId),… |
| 23084–23115 | style | 3 | v249: buy advice line under the prices, the drifting card on a game page, the shopping plan and queue clean-up sheets |
| 23115–23480 | script | 34 | ===== v249: three smarter things that run by themselves (the owner picked them from a list: "1 2 4"). 1. Buy advice (`sm*` / `smBuy`): price alerts… |
| 23480–23555 | script | 7 | ===== v249, smoother everywhere (the owner: "all tabs and windows lag"; "a lot of lags that even Nexi doesn't see"). 1. Nexi's own problem checks… |
| 23555–23584 | script | 2 | ===== v250 (the owner, with a screenshot of a "picked for you" card: "not all games have screenshots inside here, fix"): 1. The preview (gpLoad)… |
| 23584–23619 | style | 3 | ===== v251: the rail's live strip (replaces the queue covers) + Nexi sized to his seat ===== |
| 23619–23809 | script | 17 | ===== v251 (the owner, screenshot of the unfolded rail with the queue covers circled: "change them into something useful with live animation that is… |
| 23809–23817 | script | 1 | v253 (#338): the view switcher's marker is measured in the next frame (slidePill), not inside the tap |
| 23817–23831 | script | 1 | after v255 (#354 #355 #358): the lag checks skip frames that overlap a resize (fold/unfold, 0.8s after) or the time the app was hidden (asleep, in… |
| 23831–23842 | script | 1 | v256 (#352): the calling card is drawn in steps (callingCard, yf) |
| 23842–23917 | style | 8 | v259 three new card looks, from the owner's references (layout and style copied, our colours): dzp = Home "Playing now" colour cards, dzd = On deck… |
| 23917–24012 | script | 8 | v259 three new card looks (switch: S.dzX) |
| 24012–24053 | script | 3 | v260 rest: 12s without a touch, every looping animation holds still (no frames to draw, nothing for the GPU to do); the first touch, key, scroll or… |
| 24054–24058 | style | 0 | v261: the view pill's highlight slides on the GPU: it used to animate width (a layout every frame for .28s) |
| 24058–24074 | script | 1 | v261: smooth at the screen's own refresh rate. The highlight takes its new width at once and a transform animation (scaleX from the old width) makes… |
| 24075–24114 | script | 3 | v261: Nexi learned (the owner: "still lags a lot, I want 120 fps"): an animation the phone can't hand to its GPU makes the main thread draw every… |
| 24114–24121 | script | 1 | — |
| 24122–24129 | style | 0 | v263 (120 fps): Nexi's face: a mood used to morph his eyes and mouth (height, top, width, borders: main-thread transitions), and a blink restarted… |
| 24129–24273 | script | 11 | v263 (120 fps): a looping animation on something that isn't drawn (a hidden screen; a list row scrolled far enough that the browser skips drawing… |
| 24273–24337 | script | 5 | v264: Nexi learned (the owner's fifth "still lags", with nothing in his own reports since v263): the lag he couldn't see was the GPU blending a pile… |
| 24337–24343 | style | 1 | v265 smoothness check (prb*, last script): while it tests a suspect, that suspect is switched off for a little while |
| 24343–24467 | script | 9 | v265: the smoothness check (the owner, after five rounds: "it lags everywhere, as if it's too much: a fast scroll up or down on the Queue, sideways… |
| 24467–24481 | script | 1 | v265 (the owner: "if I scroll fast it lags, up or down on the Queue"): touch scrolling no longer waits for the app. Every screen but Home and every… |
| 24481–24485 | script | 0 | the smoothness check counts swiping a game's page to the next game as a sideways moment (it's not a scroll) |
| 24485–24526 | script | 4 | v265: Nexi learned (the blocking touchmove above sat on every screen for months and nothing noticed: it doesn't throw, doesn't look wrong, and the… |
| 24526–24686 | script | 12 | v266: the self-test (step 1 of the plan the owner agreed: "measure first, on the phone, before changing anything"). Settings → This build → "Run the… |
| 24686–24695 | style | 1 | v266 self-test result card (inside the diary sheet's box) |
| 24695–24705 | style | 1 | v271: the progress line draws itself to the step a game just reached. While it runs, the segment's own lines go transparent and .gd-fill (a dim… |
| 24705–24712 | script | 0 | — |
| 24712–24851 | script | 10 | v267 picture fit, layer 2 (layer 1 is sw.js: nothing bigger than the screen). Lists show pictures far smaller than the screen: a Queue row's faded… |
| 24851–24945 | script | 8 | v267: Nexi learned (the phone's self-test: 372 MP of pictures on the Queue for 14 MP on screen, one picture 70 MP; none of his checks looked at… |
| 24945–24954 | script | 1 | v269: the tidy-up (the owner: "organise the code, merge it, cut what isn't needed"). No new feature: onNav (screen-change hooks in one place), dead… |
| 24954–24998 | script | 3 | v270: buzzes on the iPhone too. Safari has no navigator.vibrate, but iOS 18+ gives a light tap each time an <input type="checkbox" switch> flips,… |
| 24998–25055 | script | 4 | v271: two moments that used to jump now move (the owner: "smoothness is rule number one, but I do like animations"). Picked with the… |
| 25055–25064 | script | 1 | #432: Settings (#sheet) let a sideways swipe through to the game page behind it; the other sheets already stop it (noHub) |
| 25064–25077 | script | 1 | v273 (the owner: "return the background to a solid gradient color, I hate how it looks"): the live background (v257 lights, specks and drifting art;… |
| 25077–25083 | style | 0 | v274 (the owner, #447): one Nexi per screen. Unfolded, Home's own Nexi (the face beside your greeting) and the rail's seated Nexi showed at once:… |
| 25083–25096 | script | 1 | — |
