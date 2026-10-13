# Code map of index.html

Made by `node tools/map.mjs` (run it again after a release; line numbers move). 25295 lines, 1884 KB, 105 scripts, 61 styles.

How the file is built: one core style (line 15) and one core script (the biggest block below), then one patch per release, oldest first. A patch adds its own code and often wraps a core function (`const _of = openFull; openFull = function () { ... _of.apply(this, arguments) ... }`), so the **last** wrap runs first. To change what a function does, read every layer listed under "Wrapped functions". New code goes in the last script/style.

## Wrapped functions (most layers first)

| function | defined | layers (line: block) |
|---|---|---|
| `renderHome` | 10736 | 6: 11038 core, 11406 core, 12757 core, 16474 v194, 17425 v204, 23936 v259 |
| `syncPlayed` | 3196 | 6: 11241 core, 19716 v218, 20187 v220, 20244 v220, 20493 v221, 23288 v249 |
| `applyLook` | 3459 | 6: 12366 core, 14039, 16476 v194, 17236 v200, 17424 v204, 20235 v220 |
| `paintTab` | 4263 | 5: 11255 core, 11357 core, 11407 core, 12386 core, 18045 v206 |
| `pnBubSay` | 11903 | 5: 12800 core, 13948 core, 16103 v189, 17629 v204, 20966 v229 |
| `openEdit` | 5008 | 4: 12414 core, 15282 v178, 19934 v219, 22856 v241 |
| `pnBubLines` | 11833 | 4: 12818 core, 17616 v204, 20219 v220, 20432 v221 |
| `renderQueue` | 8305 | 3: 11071 core, 20420 v221, 21472 v232 |
| `pnBubShow` | 11917 | 3: 16104 v189, 17426 v204, 20469 v221 |
| `lookDesign` | 18193 | 3: 19315 v215, 20380 v221, 21379 v232 |
| `dyShow` | 10312 | 3: 20123 v220, 23530 v249, 25125 |
| `renderTaste` | 10200 | 2: 12691 core, 18000 v205 |
| `xFeatures` | 12359 | 2: 12761 core, 13176 core |
| `qCounts` | 7017 | 2: 12997 core, 21372 v232 |
| `queueList` | 7026 | 2: 12998 core, 21373 v232 |
| `pnPowerUp` | 13500 | 2: 13950 core, 24033 v260 |
| `setSub` | 3659 | 2: 17828 v205, 20425 v221 |
| `nxContext` | 12821 | 2: 18712 v212, 21001 v229 |
| `wiAdd` | 18630 | 2: 19391 v216, 19723 v218 |
| `pxRun` | 18853 | 2: 19630 v217, 23281 v249 |
| `nxPlan` | 13062 | 2: 19726 v218, 19897 v219 |
| `gdMarkup` | 16626 | 2: 19886 v219, 20158 v220 |
| `pnBubReact` | 11516 | 2: 20221 v220, 21929 v237 |
| `tasteInsightsPage` | 17855 | 2: 20511 v221, 23957 v259 |
| `closeFull` | 9533 | 2: 20630 v225, 23528 v249 |
| `gdShots` | 21939 | 2: 22100 v238, 22131 v238 |
| `sgdbFillOne` | 4383 | 2: 22135 v238, 22764 v241 |

## Screen-change hooks (onNav / onSettings, since v269)

goTab / goSub / openFull run every `onNav("before"|"after", fn)` hook and openSettings every `onSettings(...)` hook (one wrap each, defined at line 12397, in registration order below). New code registers here; `kit.mjs syntax` fails on a new wrap of those four.

- 12412: `onNav("after", (k, a) => { if (k === "sub") try { uVisit(a[0]); } catch (e) {} });`
- 12413: `onNav("before", (k, a) => { if (k === "game" && (hubAt !== a[0] || !$("#full.on"))) uCount("o", "game"); });`
- 12415: `onSettings("before", (k, a) => { const sc = a[0]; uCount("o", "settings" + (sc && sc !== "app" ? "/" + sc : ""`
- 15281 v178: `onSettings("after", () => { $("#sheet-in").classList.remove("drill", "ssearch"); calmSheet(true); });`
- 15884 v187: `onSettings("after", () => setTimeout(helperCheck, 50));`
- 16443 v194: `onSettings("after", () => shxSetWire());`
- 16854 v196: `onNav("after", () => bugSeen());`
- 16855 v196: `onSettings("after", () => { bugCrumb("screen settings"); bugLayoutSoon(); });`
- 17325 v203: `onNav("before", k => { if (k === "game") openAt = performance.now(); });`
- 18009 v205: `onNav("after", k => { if (k === "tab" && curTab() !== "taste") ixShown.clear(); });`
- 18545 v211: `onSettings("after", () => cbSetWire());`
- 19106 v213: `onSettings("after", () => pxSetWire());`
- 19108 v213: `onNav("after", (k, a) => { if (k !== "game") return;`
- 19392 v216: `onNav("after", (k, a) => { if (k !== "game") return; const g = S.games.find(x => x.id === a[0]);`
- 19890 v219: `onNav("after", (k, a) => { if (k !== "game") return;`
- 20122 v220: `onSettings("before", () => { const el = document.getElementById("sheet"); if (el) el._flkOpen = performance.no`
- 20424 v221: `onNav("after", k => { if (k === "tab" && !lfBox) setTimeout(() => { try { lfApply(); } catch (e) {} }, 60); })`
- 20427 v221: `onNav("before", (k, a) => { if (k !== "game") return; const g = S.games.find(x => x.id === a[0]), d = g ? dust`
- 20624 v225: `onNav("before", k => { if (k !== "game") return;`
- 21965 v237: `onNav("after", (k, a) => { if (k !== "game") return;`
- 23527 v249: `onNav("after", k => { if (k === "tab") lagWatch(() => "Switching to " + lagWhere()); else if (k === "game") la`
- 23529 v249: `onSettings("after", () => lagWatch("Opening Settings"));`
- 24323 v264: `onNav("after", k => { if (k === "tab") { STACK.go = null; stackArm(3500); } });`
- 24446 v265: `onNav("before", k => prbStart(k === "game" ? "open" : "switch", 1000));`
- 24840 v267: `onNav("after", () => requestAnimationFrame(fitRetry));`
- 24883 v267: `onNav("after", k => { if (k !== "sub") picArm(4000); });`
- 24924 v267: `onNav("after", k => { if (k !== "sub") picWait(); });`
- 25030 v271: `onNav("after", (k, a) => {`
- 25208 v278: `onSettings("before", (k, a) => { lvpScope = SCOPE_NAME[a[0]] ? a[0] : "app"; });`
- 25209 v278: `onSettings("after", () => requestAnimationFrame(lvpSync));`

## Blocks

| lines | kind | KB | what |
|---|---|---|---|
| 15–2967 | style | 206 | **CORE** — — |
| 2971–3006 | script | 3 | v252: the card's markup, here so the launch intro can use it before the rest of the page loads. o: k (speed), ar (width / height), word, rv (reveal… |
| 3119–13999 | script | 700 | **CORE** — v169 speed: building a date/number formatter is slow, and the app formats dates for every card on every redraw. Keep one formatter per… |
| 14000–14018 | style | 1 | — |
| 14018–14058 | script | 2 | — |
| 14059–14095 | style | 3 | v147 Top board: ranked rows, big score, share as an image |
| 14095–14186 | script | 7 | v147 Top board in Played, plus a share-as-image button |
| 14187–14213 | style | 2 | v150 badges |
| 14213–14282 | script | 4 | v150 badges: worked out from what you've done, saved with the date you earned them |
| 14282–14288 | script | 0 | v151 art strength slider: live while dragging, saved when let go |
| 14288–14289 | script | 0 | — |
| 14289–14306 | style | 1 | v155 light board motion: only fade + slide, which the phone's graphics chip does for free |
| 14306–14326 | script | 2 | — |
| 14326–14352 | style | 2 | v156 scroll-driven motion: the browser ties these to your finger on its own graphics thread, so they stay at full refresh rate however fast you… |
| 14352–14360 | style | 1 | v157 the real drag: a full-screen blurred picture sat behind every screen, slowly zooming forever, so the phone re-blurred the whole screen on every… |
| 14360–14367 | script | 0 | — |
| 14368–14387 | style | 1 | v163: Why you'll love it — one sheet, fades and slides in, nothing loops |
| 14387–14457 | script | 4 | v163: "Why you'll love it" — sends your ratings and this game to your Cloudflare helper, which asks free Workers AI why it fits your taste (or… |
| 14458–14489 | script | 2 | v166: reset scores — one game from its page, or everything from Settings → Your data. Games, art, hours and diary stay; played games stay played,… |
| 14490–14500 | style | 1 | v167 Nexi's glow sometimes showed as a square box. Three stacked fixes (blur, clip-path, mask) each add a separate layer the phone can drop… |
| 14501–14536 | style | 3 | v169 speed: these animations changed colours/sizes/positions, which makes the phone redraw on every frame. Same looks, now moved or faded instead,… |
| 14537–14548 | style | 1 | v171 swipe a queue card left to delete the game. Only the card's contents slide (translate), the red button waits underneath. |
| 14548–14609 | script | 3 | — |
| 14610–14625 | style | 1 | v172 what's-new tour: a small card with Nexi's words, while Nexi points at the real button |
| 14625–14818 | script | 17 | v172: after every update Nexi shows what's new — where it is, and a nudge to try it. HOW TO ADD ONE: push an entry to WHATS_NEW with the version it… |
| 14818–15060 | style | 22 | ===== v174 Home dashboard (layout from the owner's reference, our black + blue colours) ===== |
| 15060–15146 | style | 8 | ===== v177 play sessions ===== |
| 15146–15286 | script | 10 | ===== v178 calmer settings and edit: search, most-used shortcuts, one section at a time, fewer knobs up front ===== |
| 15286–15332 | style | 4 | ===== v178 calmer settings ===== |
| 15332–15345 | style | 1 | ===== v180 finish month picker ===== |
| 15345–15353 | style | 1 | ===== v181 theme picker ===== |
| 15353–15500 | script | 13 | ===== v181 Game Shuffler: swipe through your games one at a time (right = tempted, left = not now). Styled after the owner's chess reference: a big… |
| 15500–15581 | style | 8 | ===== v181 Game Shuffler (chess reference layout, our black + theme accent) ===== |
| 15581–15607 | style | 2 | ===== v182 lists ===== |
| 15607–15644 | style | 3 | ===== v183 extras off by default, quick duel / score sheets, usage table, quick add ===== |
| 15644–15667 | script | 2 | ===== v183 what's new + settings extras ===== |
| 15667–15712 | script | 3 | ---- badges that keep going as you duel ---- |
| 15712–15789 | style | 7 | ===== v184 ranking: tiers, confidence dots, On deck, Nexi on Home, insights ===== |
| 15789–15809 | script | 2 | ===== v185 what's new ===== |
| 15809–15856 | style | 4 | ===== v185 Nexi chat, personalities, similar games ===== |
| 15856–15864 | script | 0 | ===== v186 what's new ===== |
| 15864–15891 | script | 3 | ===== v187: similar games picked by the AI and checked on RAWG; helper status in Settings → Nexi ===== |
| 15891–16009 | style | 8 | ===== v188 "Clean" style: the rest of the app speaks Home's language ===== One layer, scoped to html.ui2 (Settings → Look of the app → Style →… |
| 16009–16020 | script | 1 | ===== v188 what's new: the Clean style ===== |
| 16020–16040 | style | 2 | ===== v189 tour: Nexi drops out of the notch / camera hole, then rides on the tour card ===== |
| 16040–16141 | script | 8 | ===== v189 tour: Nexi comes out of the notch and stays with the tour card ===== |
| 16141–16297 | style | 12 | ===== v194 "Sicker everywhere": Home's look on every screen (Clean style only) ===== Each screen's header is a card like Home's: a big title, live… |
| 16297–16507 | script | 16 | ===== v194: every screen's header is a card like Home's ===== |
| 16507–16623 | style | 12 | ===== v195: the Add screen and a game's page in Home's language (both switchable) ===== |
| 16623–16673 | script | 4 | ===== v195: a game's page, New style ===== |
| 16673–16678 | style | 0 | v196: on phones under ~400px the pick card was 352px wide whatever the screen (its 220px min-height through the 16:10 ratio set a minimum width) and… |
| 16678–16942 | script | 21 | ===== v196: problem reports. The app notices when something breaks and files it (Settings → This build) ===== What it watches: script errors, a page… |
| 16942–16960 | style | 2 | — |
| 16960–17057 | script | 8 | ===== v197: Nexi says when he's spotted (and already reported) a problem, and the report sheet lists them ===== |
| 17057–17125 | script | 6 | ===== v198: problem reports catch lag, not just freezes ===== The v196 check only saw 400ms+ freezes; the stutters you feel on a 120Hz screen are… |
| 17125–17229 | script | 8 | ===== v199: Nexi's eyes see more: cut-off text, text on top of text, a blank screen ===== Runs with bugLayout (idle, once a screen settles). Only… |
| 17229–17256 | script | 2 | ===== v200: the two lags the owner felt: Queue scrolling and a game page opening ===== |
| 17256–17268 | script | 1 | ===== v201: every picture in the SteamGridDB and Steam pickers shows its real size (4K / HD / Low) before you pick; SteamGridDB lists sharpest first… |
| 17268–17318 | script | 4 | ===== v202: 4K art really shows in 4K (the swap to the sharp copy was starved by the live countdown, see the liteUp kick), RAWG screenshots as… |
| 17318–17342 | script | 2 | ===== v203: a game page is sharp as soon as it opens. warmGame (touch-down, and each neighbour for the swipe) already downloaded and decoded the… |
| 17342–17416 | style | 7 | ===== v204: each personality has its own face, idle move and a signature move when it talks. Persona rules use :where() so they beat the plain face… |
| 17416–17660 | script | 21 | ===== v204: Nexi's personalities get their own lines (context-aware, ~20 each), faces and moves; more everyday lines for everyone; double tap opens… |
| 17660–17823 | style | 17 | ===== v205: Taste → Insights. Psychology, Patterns and Analytics in one page, ordered the way attention works: who you are (identity) → how good… |
| 17823–18017 | script | 19 | ===== v205: Taste → Insights (see the style block above for the order and why) ===== |
| 18017–18066 | script | 4 | ===== v206: lag fixes from the problem reports ===== render() rebuilt all seven screens on every save, add or delete (#58: a 260ms tap on Edit →… |
| 18066–18078 | style | 1 | v208: found by Nexi's new checks on the first sweep: queue rank numbers were drawn in the border colour (1.9:1, hard to read) and the small icon… |
| 18078–18332 | script | 21 | ===== v208: Nexi's new senses. The owner hit 10–15 problems he never saw: a page that flashed back while closing (#68), a description cut short with… |
| 18332–18349 | script | 1 | ===== v209: #81 (4K art stayed low-res). The fix itself is in liteUp's go() (retries, decode() fallback, LITE_NOFULL) and warmGame; this is the tour… |
| 18349–18604 | script | 19 | ===== v211: cloud backup ===== Everything on this phone is sealed here (AES-GCM, key made from the restore code) and sent to the helper, which only… |
| 18604–18620 | style | 1 | — |
| 18620–18725 | script | 8 | ===== v212: "Where was I?" notes ===== A game keeps short notes about where you stopped (g.wasI = [{t, x}], newest last, 20 kept). The newest shows… |
| 18725–18765 | style | 4 | — |
| 18765–19155 | script | 41 | ===== v213: price alerts (Steam + PlayStation Store) ===== Games you're waiting for (not played, not dropped, not competitive) are priced through… |
| 19155–19211 | style | 4 | — |
| 19211–19223 | style | 1 | ===== v215 fixes ===== |
| 19223–19336 | script | 11 | ===== v215: the frozen game page (#99, #102, #103), Read more (#85, #100), Taste names (#86), Queue rows (#89), 4K pictures by default (#84), and… |
| 19336–19351 | style | 1 | ===== v216: the story so far ===== |
| 19351–19408 | script | 5 | ===== v216: "the story so far". Under your newest 📍 note on a game's page, Nexi writes a short recap of the plot up to where the note says you are… |
| 19408–19508 | script | 9 | ===== v216: Steam's genres and "more like this". Steam's own tags are the genres players vote on ("Souls-like", "Metroidvania", "JRPG",… |
| 19508–19527 | script | 2 | — |
| 19527–19534 | style | 0 | ===== v217: release-date watcher + deal radar ===== |
| 19534–19654 | script | 11 | ===== v217: two new things Nexi does on his own (the owner loves automatic features). 1. Release-date watcher (`rdWatch`): every game you're waiting… |
| 19654–19658 | style | 0 | ===== v218: "Finished it?" (same card as Where was I?) ===== |
| 19658–19740 | script | 7 | ===== v218: "Finished it?" — Nexi notices when a game you're playing is probably done and asks, by himself. Signals (`fnWhy(g)`, first that fits):… |
| 19740–19758 | style | 1 | ===== v219: reviews are in (+ your score, predicted) ===== |
| 19758–20005 | script | 20 | ===== v219: three more things Nexi does by himself, and smarter ones (the owner: "I need more smarter and useful auto things"). 1. Reviews are in… |
| 20005–20036 | style | 3 | ===== v220: away report, finish forecast, time capsule, seasons ===== |
| 20036–20284 | script | 23 | ===== v220: six more things that happen by themselves (the owner: "more auto, unique, rare and out of the box"). 1. While you were away (`aw*`):… |
| 20284–20303 | style | 1 | ===== v221: logo fit, Nexi in the rail, dusty backlog, crystal ball ===== |
| 20303–20540 | script | 22 | ===== v221: the owner's and their friend's reports (#118 prices stuck on "checking…", #114 "fit the logo in the queue automatically", #123 "the rail… |
| 20540–20553 | script | 1 | ===== v222: #153 tour steps that point at things only there once you've scored games. The fixes are in the v184/v215 steps (fall back to the "What… |
| 20553–20565 | script | 1 | ===== v224: prices in one line (pxCard, store logos PX_LOGO) + a tap on seated Nexi hides the rail (railHideNow) ===== |
| 20565–20571 | style | 0 | ===== v225: the cover you tap flies into the game page ===== |
| 20571–20637 | script | 5 | ===== v225: the cover you tap flies into the game page ===== Opening a game from anything that shows its cover (Home, queue, library, rail, deck,… |
| 20637–20646 | script | 1 | ===== v226: platforms as logos on the game page (platChips / platHtml, next to platList) ===== |
| 20646–20700 | script | 3 | ===== v227: prices for games the stores' title search can't find (the owner's GTA 6: the PS Store's old search doesn't list it, though its store… |
| 20700–20709 | script | 1 | ===== v228: platform logos in one pill; price checks never skip a store for good (pxStale/pxGap, batches of 5); helper v13 ===== |
| 20710–21015 | script | 33 | ===== v229: Nexi's brain. The owner wants Nexi "super smart", saying a lot more, all of it different and useful, and using their name much more.… |
| 21015–21031 | style | 1 | v230: Browse and choose, every store |
| 21031–21145 | script | 9 | ===== v230: pictures straight from the stores. Browse and choose (Edit → Look) now shows, next to SteamGridDB and RAWG, the official art of Steam… |
| 21145–21154 | script | 1 | ===== v231 (daily fixer): #180/#181/#154 the tour's Next button answered slowly (it saved and opened the next screen inside the tap; now that waits… |
| 21154–21262 | style | 10 | ===== v232: the same shape for every open queue card and every game page ===== |
| 21262–21495 | script | 22 | ===== v232: the owner: "each queue card is a different size when open" and "the new game page lacks organisation: each game looks different because… |
| 21495–21572 | style | 8 | ===== v236: game suggestions you can see (big cards, a preview with store screenshots), swipe to add, Discover, share to add ===== |
| 21572–21885 | script | 28 | ===== v236: the owner: "adding Baldur's Gate 3 from Similar added the wrong game; the pictures are too small and I need more of a description; maybe… |
| 21885–21900 | style | 1 | ===== v237: screenshots on every game page; Grumble only takes real hits ===== |
| 21900–22019 | script | 11 | ===== v237: the owner: "I deleted Baldur's Gate and Grumble lost HP, which is wrong; and when I add a game, forget to say it's played and then say… |
| 22019–22164 | script | 12 | ===== v238: the owner: "only a few games got screenshots, and the ones that did took time to load. I want them instant and always ready, not fetched… |
| 22164–22268 | style | 12 | ===== v239/v242: Game room — a little 3D room built from your games (owner's isometric "B" room reference: layout and style, our colours). Plain CSS… |
| 22268–22637 | script | 31 | ===== v239: Game room. The owner sent an isometric illustration (a room built around a giant book-letter) and asked for something like it in 3D that… |
| 22637–22668 | style | 3 | v241: hours to beat from HowLongToBeat |
| 22668–22874 | script | 17 | ===== v241: hours to beat from HowLongToBeat (`hl*`). The owner: "most hours I type by hand; the automatic ones are super wrong". Two automatic… |
| 22874–22892 | script | 1 | ===== v243 (#313, the owner: "pressing on the text should expand it, not only Read more"): a tap on a cut-short description (or the story so far)… |
| 22892–22903 | script | 1 | ===== v244 (#319): a tap on a game's description (or the story so far) now closes it again too; see the v243 listener. Tour fixes (#307): the v236… |
| 22903–22912 | script | 1 | ===== v245 (#301): pnShelf skips the Finished shelf while its screen is hidden (it forced a full layout of the screen you were on, 130ms at 4x);… |
| 22912–22922 | script | 1 | ===== v246 (#309): no press animation. The owner: "when I touch anything an animation happened, delete that". Until v268 this script stripped… |
| 22922–22926 | style | 0 | v247: the rail's Add and Settings buttons draw the menu's own icons (they were the text glyphs ＋ and ⚙) |
| 22926–22938 | script | 1 | ===== v247: new menu icons, picked by the owner from two drawn sets: Home (house), Games (game case with a disc), Plan (calendar with a release… |
| 22938–23081 | script | 11 | ===== v248: three things that run by themselves. 1. Release days in your calendar: Settings → Extra features → turn on makes a random id (S.calId),… |
| 23081–23112 | style | 3 | v249: buy advice line under the prices, the drifting card on a game page, the shopping plan and queue clean-up sheets |
| 23112–23474 | script | 34 | ===== v249: three smarter things that run by themselves (the owner picked them from a list: "1 2 4"). 1. Buy advice (`sm*` / `smBuy`): price alerts… |
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
| 25173–25180 | script | 0 | — |
| 25180–25194 | style | 1 | v278 (#171, the friend: "a live preview when I'm editing the looks of all features, the same look everywhere, smart, off by default"): with Settings… |
| 25194–25226 | script | 3 | v278 (#171): when the sheet docks for Live preview. A screen's Customise always does; Settings itself only while one of the look groups is the one… |
| 25226–25251 | script | 2 | Nexi learned (#174, the friend, iPad v228: "Nexi disappeared while the rail was open"): with the rail open he sits at its foot (html.nxseat). A… |
| 25251–25273 | style | 2 | v278 (#130, the owner: "Radar needs a makeover, looks kinda ugly and doesn't match our theme"): Radar in the Clean style. Its words are sentence… |
| 25273–25295 | script | 2 | Nexi learned (#130, the owner: "Radar looks kinda ugly and doesn't match our theme"): Radar kept the old Arcade look (slanted, all-caps heading)… |
