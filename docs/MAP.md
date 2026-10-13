# Code map of index.html

Made by `node tools/map.mjs` (run it again after a release; line numbers move). 25185 lines, 1878 KB, 102 scripts, 59 styles.

How the file is built: one core style (line 15) and one core script (the biggest block below), then one patch per release, oldest first. A patch adds its own code and often wraps a core function (`const _of = openFull; openFull = function () { ... _of.apply(this, arguments) ... }`), so the **last** wrap runs first. To change what a function does, read every layer listed under "Wrapped functions". New code goes in the last script/style.

## Wrapped functions (most layers first)

| function | defined | layers (line: block) |
|---|---|---|
| `save` | 4023 | 8: 12647 core, 13294 core, 13350 core, 13706 core, 14275 v150, 18446 v211, 21907 v237, 23059 v248 |
| `bugNice` | 16952 | 7: 19308 v215, 21361 v232, 21461 v232, 21861 v236, 21990 v237, 22130 v238, 22565 v239 |
| `renderHome` | 10738 | 6: 11040 core, 11408 core, 12759 core, 16474 v194, 17397 v204, 23941 v259 |
| `syncPlayed` | 3215 | 6: 11243 core, 19694 v218, 20161 v220, 20218 v220, 20469 v221, 23293 v249 |
| `applyLook` | 3479 | 6: 12368 core, 14041, 16476 v194, 17204 v200, 17396 v204, 20209 v220 |
| `bugLayout` | 16797 | 6: 17190 v199, 17208 v200, 17270 v202, 17610 v204, 18227 v208, 18280 v208 |
| `paintTab` | 4275 | 5: 11257 core, 11359 core, 11409 core, 12388 core, 18017 v206 |
| `pnBubSay` | 11905 | 5: 12802 core, 13950 core, 16103 v189, 17601 v204, 20942 v229 |
| `openEdit` | 5020 | 4: 12416 core, 15282 v178, 19912 v219, 22858 v241 |
| `pnBubLines` | 11835 | 4: 12820 core, 17588 v204, 20193 v220, 20408 v221 |
| `renderQueue` | 8310 | 3: 11073 core, 20396 v221, 21458 v232 |
| `pnBubShow` | 11919 | 3: 16104 v189, 17398 v204, 20445 v221 |
| `lookDesign` | 18165 | 3: 19288 v215, 20356 v221, 21355 v232 |
| `dyShow` | 10314 | 3: 20097 v220, 23535 v249, 25130 |
| `renderTaste` | 10202 | 2: 12693 core, 17972 v205 |
| `xFeatures` | 12361 | 2: 12763 core, 13178 core |
| `qCounts` | 7022 | 2: 12999 core, 21348 v232 |
| `queueList` | 7031 | 2: 13000 core, 21349 v232 |
| `pnPowerUp` | 13502 | 2: 13952 core, 24038 v260 |
| `setSub` | 3679 | 2: 17800 v205, 20401 v221 |
| `nxContext` | 12823 | 2: 18685 v212, 20977 v229 |
| `wiAdd` | 18603 | 2: 19369 v216, 19701 v218 |
| `pxRun` | 18826 | 2: 19608 v217, 23283 v249 |
| `nxPlan` | 13064 | 2: 19704 v218, 19875 v219 |
| `gdMarkup` | 16627 | 2: 19864 v219, 20132 v220 |
| `pnBubReact` | 11518 | 2: 20195 v220, 21922 v237 |
| `tasteInsightsPage` | 17827 | 2: 20487 v221, 23962 v259 |
| `closeFull` | 9535 | 2: 20606 v225, 23533 v249 |
| `gdShots` | 21932 | 2: 22096 v238, 22127 v238 |
| `sgdbFillOne` | 4395 | 2: 22134 v238, 22766 v241 |

## Screen-change hooks (onNav / onSettings, since v269)

goTab / goSub / openFull run every `onNav("before"|"after", fn)` hook and openSettings every `onSettings(...)` hook (one wrap each, defined at line 12399, in registration order below). New code registers here; `kit.mjs syntax` fails on a new wrap of those four.

- 12414: `onNav("after", (k, a) => { if (k === "sub") try { uVisit(a[0]); } catch (e) {} });`
- 12415: `onNav("before", (k, a) => { if (k === "game" && (hubAt !== a[0] || !$("#full.on"))) uCount("o", "game"); });`
- 12417: `onSettings("before", (k, a) => { const sc = a[0]; uCount("o", "settings" + (sc && sc !== "app" ? "/" + sc : ""`
- 15281 v178: `onSettings("after", () => { $("#sheet-in").classList.remove("drill", "ssearch"); calmSheet(true); });`
- 15884 v187: `onSettings("after", () => setTimeout(helperCheck, 50));`
- 16443 v194: `onSettings("after", () => shxSetWire());`
- 16836 v196: `onNav("after", () => bugSeen());`
- 16837 v196: `onSettings("after", () => { bugCrumb("screen settings"); bugLayoutSoon(); });`
- 17297 v203: `onNav("before", k => { if (k === "game") openAt = performance.now(); });`
- 17981 v205: `onNav("after", k => { if (k === "tab" && curTab() !== "taste") ixShown.clear(); });`
- 18518 v211: `onSettings("after", () => cbSetWire());`
- 19079 v213: `onSettings("after", () => pxSetWire());`
- 19081 v213: `onNav("after", (k, a) => { if (k !== "game") return;`
- 19370 v216: `onNav("after", (k, a) => { if (k !== "game") return; const g = S.games.find(x => x.id === a[0]);`
- 19868 v219: `onNav("after", (k, a) => { if (k !== "game") return;`
- 20096 v220: `onSettings("before", () => { const el = document.getElementById("sheet"); if (el) el._flkOpen = performance.no`
- 20400 v221: `onNav("after", k => { if (k === "tab" && !lfBox) setTimeout(() => { try { lfApply(); } catch (e) {} }, 60); })`
- 20403 v221: `onNav("before", (k, a) => { if (k !== "game") return; const g = S.games.find(x => x.id === a[0]), d = g ? dust`
- 20600 v225: `onNav("before", k => { if (k !== "game") return;`
- 21958 v237: `onNav("after", (k, a) => { if (k !== "game") return;`
- 23532 v249: `onNav("after", k => { if (k === "tab") lagWatch(() => "Switching to " + lagWhere()); else if (k === "game") la`
- 23534 v249: `onSettings("after", () => lagWatch("Opening Settings"));`
- 24328 v264: `onNav("after", k => { if (k === "tab") { STACK.go = null; stackArm(3500); } });`
- 24451 v265: `onNav("before", k => prbStart(k === "game" ? "open" : "switch", 1000));`
- 24845 v267: `onNav("after", () => requestAnimationFrame(fitRetry));`
- 24888 v267: `onNav("after", k => { if (k !== "sub") picArm(4000); });`
- 24929 v267: `onNav("after", k => { if (k !== "sub") picWait(); });`
- 25035 v271: `onNav("after", (k, a) => {`

## Blocks

| lines | kind | KB | what |
|---|---|---|---|
| 15–2986 | style | 210 | **CORE** — — |
| 2990–3025 | script | 3 | v252: the card's markup, here so the launch intro can use it before the rest of the page loads. o: k (speed), ar (width / height), word, rv (reveal… |
| 3138–14001 | script | 699 | **CORE** — v169 speed: building a date/number formatter is slow, and the app formats dates for every card on every redraw. Keep one formatter per… |
| 14002–14020 | style | 1 | — |
| 14020–14060 | script | 2 | — |
| 14061–14097 | style | 3 | v147 Top board: ranked rows, big score, share as an image |
| 14097–14188 | script | 7 | v147 Top board in Played, plus a share-as-image button |
| 14189–14215 | style | 2 | v150 badges |
| 14215–14282 | script | 4 | v150 badges: worked out from what you've done, saved with the date you earned them |
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
| 16507–16624 | style | 12 | ===== v195: the Add screen and a game's page in Home's language (both switchable) ===== |
| 16624–16674 | script | 4 | ===== v195: a game's page, New style ===== |
| 16674–16679 | style | 0 | v196: on phones under ~400px the pick card was 352px wide whatever the screen (its 220px min-height through the 16:10 ratio set a minimum width) and… |
| 16679–16924 | script | 19 | ===== v196: problem reports. The app notices when something breaks and files it (Settings → This build) ===== What it watches: script errors, a page… |
| 16924–16942 | style | 2 | — |
| 16942–17025 | script | 6 | ===== v197: Nexi says when he's spotted (and already reported) a problem, and the report sheet lists them ===== |
| 17025–17093 | script | 6 | ===== v198: problem reports catch lag, not just freezes ===== The v196 check only saw 400ms+ freezes; the stutters you feel on a 120Hz screen are… |
| 17093–17197 | script | 8 | ===== v199: Nexi's eyes see more: cut-off text, text on top of text, a blank screen ===== Runs with bugLayout (idle, once a screen settles). Only… |
| 17197–17227 | script | 3 | ===== v200: the two lags the owner felt: Queue scrolling and a game page opening ===== |
| 17227–17239 | script | 1 | ===== v201: every picture in the SteamGridDB and Steam pickers shows its real size (4K / HD / Low) before you pick; SteamGridDB lists sharpest first… |
| 17239–17290 | script | 4 | ===== v202: 4K art really shows in 4K (the swap to the sharp copy was starved by the live countdown, see the liteUp kick), RAWG screenshots as… |
| 17290–17314 | script | 2 | ===== v203: a game page is sharp as soon as it opens. warmGame (touch-down, and each neighbour for the swipe) already downloaded and decoded the… |
| 17314–17388 | style | 7 | ===== v204: each personality has its own face, idle move and a signature move when it talks. Persona rules use :where() so they beat the plain face… |
| 17388–17632 | script | 21 | ===== v204: Nexi's personalities get their own lines (context-aware, ~20 each), faces and moves; more everyday lines for everyone; double tap opens… |
| 17632–17795 | style | 17 | ===== v205: Taste → Insights. Psychology, Patterns and Analytics in one page, ordered the way attention works: who you are (identity) → how good… |
| 17795–17989 | script | 19 | ===== v205: Taste → Insights (see the style block above for the order and why) ===== |
| 17989–18038 | script | 4 | ===== v206: lag fixes from the problem reports ===== render() rebuilt all seven screens on every save, add or delete (#58: a 260ms tap on Edit →… |
| 18038–18050 | style | 1 | v208: found by Nexi's new checks on the first sweep: queue rank numbers were drawn in the border colour (1.9:1, hard to read) and the small icon… |
| 18050–18305 | script | 21 | ===== v208: Nexi's new senses. The owner hit 10–15 problems he never saw: a page that flashed back while closing (#68), a description cut short with… |
| 18305–18322 | script | 1 | ===== v209: #81 (4K art stayed low-res). The fix itself is in liteUp's go() (retries, decode() fallback, LITE_NOFULL) and warmGame; this is the tour… |
| 18322–18577 | script | 19 | ===== v211: cloud backup ===== Everything on this phone is sealed here (AES-GCM, key made from the restore code) and sent to the helper, which only… |
| 18577–18593 | style | 1 | — |
| 18593–18698 | script | 8 | ===== v212: "Where was I?" notes ===== A game keeps short notes about where you stopped (g.wasI = [{t, x}], newest last, 20 kept). The newest shows… |
| 18698–18738 | style | 4 | — |
| 18738–19128 | script | 41 | ===== v213: price alerts (Steam + PlayStation Store) ===== Games you're waiting for (not played, not dropped, not competitive) are priced through… |
| 19128–19184 | style | 4 | — |
| 19184–19196 | style | 1 | ===== v215 fixes ===== |
| 19196–19314 | script | 12 | ===== v215: the frozen game page (#99, #102, #103), Read more (#85, #100), Taste names (#86), Queue rows (#89), 4K pictures by default (#84), and… |
| 19314–19329 | style | 1 | ===== v216: the story so far ===== |
| 19329–19386 | script | 5 | ===== v216: "the story so far". Under your newest 📍 note on a game's page, Nexi writes a short recap of the plot up to where the note says you are… |
| 19386–19486 | script | 9 | ===== v216: Steam's genres and "more like this". Steam's own tags are the genres players vote on ("Souls-like", "Metroidvania", "JRPG",… |
| 19486–19505 | script | 2 | — |
| 19505–19512 | style | 0 | ===== v217: release-date watcher + deal radar ===== |
| 19512–19632 | script | 11 | ===== v217: two new things Nexi does on his own (the owner loves automatic features). 1. Release-date watcher (`rdWatch`): every game you're waiting… |
| 19632–19636 | style | 0 | ===== v218: "Finished it?" (same card as Where was I?) ===== |
| 19636–19718 | script | 7 | ===== v218: "Finished it?" — Nexi notices when a game you're playing is probably done and asks, by himself. Signals (`fnWhy(g)`, first that fits):… |
| 19718–19736 | style | 1 | ===== v219: reviews are in (+ your score, predicted) ===== |
| 19736–19979 | script | 20 | ===== v219: three more things Nexi does by himself, and smarter ones (the owner: "I need more smarter and useful auto things"). 1. Reviews are in… |
| 19979–20010 | style | 3 | ===== v220: away report, finish forecast, time capsule, seasons ===== |
| 20010–20258 | script | 23 | ===== v220: six more things that happen by themselves (the owner: "more auto, unique, rare and out of the box"). 1. While you were away (`aw*`):… |
| 20258–20279 | style | 1 | ===== v221: logo fit, Nexi in the rail, dusty backlog, crystal ball ===== |
| 20279–20516 | script | 22 | ===== v221: the owner's and their friend's reports (#118 prices stuck on "checking…", #114 "fit the logo in the queue automatically", #123 "the rail… |
| 20516–20529 | script | 1 | ===== v222: #153 tour steps that point at things only there once you've scored games. The fixes are in the v184/v215 steps (fall back to the "What… |
| 20529–20541 | script | 1 | ===== v224: prices in one line (pxCard, store logos PX_LOGO) + a tap on seated Nexi hides the rail (railHideNow) ===== |
| 20541–20547 | style | 0 | ===== v225: the cover you tap flies into the game page ===== |
| 20547–20613 | script | 5 | ===== v225: the cover you tap flies into the game page ===== Opening a game from anything that shows its cover (Home, queue, library, rail, deck,… |
| 20613–20622 | script | 1 | ===== v226: platforms as logos on the game page (platChips / platHtml, next to platList) ===== |
| 20622–20676 | script | 3 | ===== v227: prices for games the stores' title search can't find (the owner's GTA 6: the PS Store's old search doesn't list it, though its store… |
| 20676–20685 | script | 1 | ===== v228: platform logos in one pill; price checks never skip a store for good (pxStale/pxGap, batches of 5); helper v13 ===== |
| 20686–20991 | script | 33 | ===== v229: Nexi's brain. The owner wants Nexi "super smart", saying a lot more, all of it different and useful, and using their name much more.… |
| 20991–21007 | style | 1 | v230: Browse and choose, every store |
| 21007–21121 | script | 9 | ===== v230: pictures straight from the stores. Browse and choose (Edit → Look) now shows, next to SteamGridDB and RAWG, the official art of Steam… |
| 21121–21130 | script | 1 | ===== v231 (daily fixer): #180/#181/#154 the tour's Next button answered slowly (it saved and opened the next screen inside the tap; now that waits… |
| 21130–21238 | style | 10 | ===== v232: the same shape for every open queue card and every game page ===== |
| 21238–21484 | script | 23 | ===== v232: the owner: "each queue card is a different size when open" and "the new game page lacks organisation: each game looks different because… |
| 21484–21561 | style | 8 | ===== v236: game suggestions you can see (big cards, a preview with store screenshots), swipe to add, Discover, share to add ===== |
| 21561–21877 | script | 28 | ===== v236: the owner: "adding Baldur's Gate 3 from Similar added the wrong game; the pictures are too small and I need more of a description; maybe… |
| 21877–21892 | style | 1 | ===== v237: screenshots on every game page; Grumble only takes real hits ===== |
| 21892–22015 | script | 11 | ===== v237: the owner: "I deleted Baldur's Gate and Grumble lost HP, which is wrong; and when I add a game, forget to say it's played and then say… |
| 22015–22163 | script | 13 | ===== v238: the owner: "only a few games got screenshots, and the ones that did took time to load. I want them instant and always ready, not fetched… |
| 22163–22267 | style | 12 | ===== v239/v242: Game room — a little 3D room built from your games (owner's isometric "B" room reference: layout and style, our colours). Plain CSS… |
| 22267–22639 | script | 31 | ===== v239: Game room. The owner sent an isometric illustration (a room built around a giant book-letter) and asked for something like it in 3D that… |
| 22639–22670 | style | 3 | v241: hours to beat from HowLongToBeat |
| 22670–22876 | script | 17 | ===== v241: hours to beat from HowLongToBeat (`hl*`). The owner: "most hours I type by hand; the automatic ones are super wrong". Two automatic… |
| 22876–22894 | script | 1 | ===== v243 (#313, the owner: "pressing on the text should expand it, not only Read more"): a tap on a cut-short description (or the story so far)… |
| 22894–22905 | script | 1 | ===== v244 (#319): a tap on a game's description (or the story so far) now closes it again too; see the v243 listener. Tour fixes (#307): the v236… |
| 22905–22914 | script | 1 | ===== v245 (#301): pnShelf skips the Finished shelf while its screen is hidden (it forced a full layout of the screen you were on, 130ms at 4x);… |
| 22914–22924 | script | 1 | ===== v246 (#309): no press animation. The owner: "when I touch anything an animation happened, delete that". Until v268 this script stripped… |
| 22924–22928 | style | 0 | v247: the rail's Add and Settings buttons draw the menu's own icons (they were the text glyphs ＋ and ⚙) |
| 22928–22940 | script | 1 | ===== v247: new menu icons, picked by the owner from two drawn sets: Home (house), Games (game case with a disc), Plan (calendar with a release… |
| 22940–23083 | script | 11 | ===== v248: three things that run by themselves. 1. Release days in your calendar: Settings → Extra features → turn on makes a random id (S.calId),… |
| 23083–23114 | style | 3 | v249: buy advice line under the prices, the drifting card on a game page, the shopping plan and queue clean-up sheets |
| 23114–23479 | script | 34 | ===== v249: three smarter things that run by themselves (the owner picked them from a list: "1 2 4"). 1. Buy advice (`sm*` / `smBuy`): price alerts… |
| 23479–23554 | script | 7 | ===== v249, smoother everywhere (the owner: "all tabs and windows lag"; "a lot of lags that even Nexi doesn't see"). 1. Nexi's own problem checks… |
| 23554–23583 | script | 2 | ===== v250 (the owner, with a screenshot of a "picked for you" card: "not all games have screenshots inside here, fix"): 1. The preview (gpLoad)… |
| 23583–23618 | style | 3 | ===== v251: the rail's live strip (replaces the queue covers) + Nexi sized to his seat ===== |
| 23618–23808 | script | 17 | ===== v251 (the owner, screenshot of the unfolded rail with the queue covers circled: "change them into something useful with live animation that is… |
| 23808–23816 | script | 1 | v253 (#338): the view switcher's marker is measured in the next frame (slidePill), not inside the tap |
| 23816–23830 | script | 1 | after v255 (#354 #355 #358): the lag checks skip frames that overlap a resize (fold/unfold, 0.8s after) or the time the app was hidden (asleep, in… |
| 23830–23841 | script | 1 | v256 (#352): the calling card is drawn in steps (callingCard, yf) |
| 23841–23916 | style | 8 | v259 three new card looks, from the owner's references (layout and style copied, our colours): dzp = Home "Playing now" colour cards, dzd = On deck… |
| 23916–24011 | script | 8 | v259 three new card looks (switch: S.dzX) |
| 24011–24052 | script | 3 | v260 rest: 12s without a touch, every looping animation holds still (no frames to draw, nothing for the GPU to do); the first touch, key, scroll or… |
| 24053–24057 | style | 0 | v261: the view pill's highlight slides on the GPU: it used to animate width (a layout every frame for .28s) |
| 24057–24073 | script | 1 | v261: smooth at the screen's own refresh rate. The highlight takes its new width at once and a transform animation (scaleX from the old width) makes… |
| 24074–24113 | script | 3 | v261: Nexi learned (the owner: "still lags a lot, I want 120 fps"): an animation the phone can't hand to its GPU makes the main thread draw every… |
| 24113–24120 | script | 1 | — |
| 24121–24128 | style | 0 | v263 (120 fps): Nexi's face: a mood used to morph his eyes and mouth (height, top, width, borders: main-thread transitions), and a blink restarted… |
| 24128–24272 | script | 11 | v263 (120 fps): a looping animation on something that isn't drawn (a hidden screen; a list row scrolled far enough that the browser skips drawing… |
| 24272–24336 | script | 5 | v264: Nexi learned (the owner's fifth "still lags", with nothing in his own reports since v263): the lag he couldn't see was the GPU blending a pile… |
| 24336–24342 | style | 1 | v265 smoothness check (prb*, last script): while it tests a suspect, that suspect is switched off for a little while |
| 24342–24466 | script | 9 | v265: the smoothness check (the owner, after five rounds: "it lags everywhere, as if it's too much: a fast scroll up or down on the Queue, sideways… |
| 24466–24480 | script | 1 | v265 (the owner: "if I scroll fast it lags, up or down on the Queue"): touch scrolling no longer waits for the app. Every screen but Home and every… |
| 24480–24484 | script | 0 | the smoothness check counts swiping a game's page to the next game as a sideways moment (it's not a scroll) |
| 24484–24525 | script | 4 | v265: Nexi learned (the blocking touchmove above sat on every screen for months and nothing noticed: it doesn't throw, doesn't look wrong, and the… |
| 24525–24685 | script | 12 | v266: the self-test (step 1 of the plan the owner agreed: "measure first, on the phone, before changing anything"). Settings → This build → "Run the… |
| 24685–24694 | style | 1 | v266 self-test result card (inside the diary sheet's box) |
| 24694–24704 | style | 1 | v271: the progress line draws itself to the step a game just reached. While it runs, the segment's own lines go transparent and .gd-fill (a dim… |
| 24704–24711 | script | 0 | — |
| 24711–24850 | script | 10 | v267 picture fit, layer 2 (layer 1 is sw.js: nothing bigger than the screen). Lists show pictures far smaller than the screen: a Queue row's faded… |
| 24850–24944 | script | 8 | v267: Nexi learned (the phone's self-test: 372 MP of pictures on the Queue for 14 MP on screen, one picture 70 MP; none of his checks looked at… |
| 24944–24953 | script | 1 | v269: the tidy-up (the owner: "organise the code, merge it, cut what isn't needed"). No new feature: onNav (screen-change hooks in one place), dead… |
| 24953–24997 | script | 3 | v270: buzzes on the iPhone too. Safari has no navigator.vibrate, but iOS 18+ gives a light tap each time an <input type="checkbox" switch> flips,… |
| 24997–25054 | script | 4 | v271: two moments that used to jump now move (the owner: "smoothness is rule number one, but I do like animations"). Picked with the… |
| 25054–25063 | script | 1 | #432: Settings (#sheet) let a sideways swipe through to the game page behind it; the other sheets already stop it (noHub) |
| 25063–25076 | script | 1 | v273 (the owner: "return the background to a solid gradient color, I hate how it looks"): the live background (v257 lights, specks and drifting art;… |
| 25076–25082 | style | 0 | v274 (the owner, #447): one Nexi per screen. Unfolded, Home's own Nexi (the face beside your greeting) and the rail's seated Nexi showed at once:… |
| 25082–25095 | script | 1 | — |
| 25095–25103 | style | 0 | #201 (the friend, iPhone/iPad: "Restore a code is not working"): while a keyboard (or a zoom) hides the bottom of the screen, the open sheet sits on… |
| 25103–25158 | script | 4 | #201: Safari's keyboard doesn't shrink the page, it slides over it. Sheets (#diary) sit at the bottom of the screen, so a short one (Restore from a… |
| 25158–25165 | script | 1 | — |
| 25165–25178 | script | 1 | v276 (the owner: "black with a little blue gradient, mostly black, with the option to edit how much gradient and the background colour"): the… |
| 25178–25185 | script | 0 | — |
