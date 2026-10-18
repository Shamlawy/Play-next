# Code map of index.html

Made by `node tools/map.mjs` (run it again after a release; line numbers move). 27603 lines, 2095 KB, 123 scripts, 66 styles.

How the file is built: one core style (line 15) and one core script (the biggest block below), then one patch per release, oldest first. A patch adds its own code and often wraps a core function (`const _of = openFull; openFull = function () { ... _of.apply(this, arguments) ... }`), so the **last** wrap runs first. To change what a function does, read every layer listed under "Wrapped functions". New code goes in the last script/style.

## Wrapped functions (most layers first)

| function | defined | layers (line: block) |
|---|---|---|

## Screen-change hooks (onNav / onSettings, since v269)

goTab / goSub / openFull run every `onNav("before"|"after", fn)` hook and openSettings every `onSettings(...)` hook (one wrap each, defined at line 12930, in registration order below). New code registers here; `kit.mjs syntax` fails on a new wrap of those four.

- 12945: `onNav("after", (k, a) => { if (k === "sub") try { uVisit(a[0]); } catch (e) {} });`
- 12946: `onNav("before", (k, a) => { if (k === "game" && (hubAt !== a[0] || !$("#full.on"))) uCount("o", "game"); });`
- 12948: `onSettings("before", (k, a) => { const sc = a[0]; uCount("o", "settings" + (sc && sc !== "app" ? "/" + sc : ""`
- 15826 v178: `onSettings("after", () => { $("#sheet-in").classList.remove("drill", "ssearch"); calmSheet(true); });`
- 16424 v187: `onSettings("after", () => setTimeout(helperCheck, 50));`
- 16983 v194: `onSettings("after", () => shxSetWire());`
- 17395 v196: `onNav("after", () => bugSeen());`
- 17396 v196: `onSettings("after", () => { bugCrumb("screen settings"); bugLayoutSoon(); });`
- 17888 v203: `onNav("before", k => { if (k === "game") openAt = performance.now(); });`
- 18572 v205: `onNav("after", k => { if (k === "tab" && curTab() !== "taste") ixShown.clear(); });`
- 19117 v211: `onSettings("after", () => cbSetWire());`
- 19679 v213: `onSettings("after", () => pxSetWire());`
- 19681 v213: `onNav("after", (k, a) => { if (k !== "game") return;`
- 19848 v215: `onSettings("after", () => requestAnimationFrame(prune)); onNav("after", () => requestAnimationFrame(prune));`
- 19978 v216: `onNav("after", (k, a) => { if (k !== "game") return; const g = S.games.find(x => x.id === a[0]);`
- 20456 v219: `onNav("after", (k, a) => { if (k !== "game") return;`
- 20687 v220: `onSettings("before", () => { const el = document.getElementById("sheet"); if (el) el._flkOpen = performance.no`
- 20997 v221: `onNav("after", k => { if (k === "tab") setTimeout(() => { try { lfApply(); } catch (e) {} }, 60); });`
- 21000 v221: `onNav("before", (k, a) => { if (k !== "game") return; const g = S.games.find(x => x.id === a[0]), d = g ? dust`
- 21197 v225: `onNav("before", k => { if (k !== "game") return;`
- 22533 v237: `onNav("after", (k, a) => { if (k !== "game") return;`
- 24093 v249: `onNav("after", k => { if (k === "tab") lagWatch(() => "Switching to " + lagWhere()); else if (k === "game") la`
- 24095 v249: `onSettings("after", () => lagWatch("Opening Settings"));`
- 24886 v264: `onNav("after", k => { if (k === "tab") { STACK.go = null; stackArm(3500); } });`
- 25013 v265: `onNav("before", k => prbStart(k === "game" ? "open" : "switch", 1000));`
- 25452 v267: `onNav("after", () => requestAnimationFrame(fitNow));`
- 25498 v267: `onNav("after", k => { if (k !== "sub") picArm(4000); });`
- 25548 v267: `onNav("after", k => { if (k !== "sub") picWait(); });`
- 25654 v271: `onNav("after", (k, a) => {`
- 25835 v278: `onSettings("before", (k, a) => { lvpScope = SCOPE_NAME[a[0]] ? a[0] : "app"; });`
- 25836 v278: `onSettings("after", () => requestAnimationFrame(lvpSync));`
- 26123 v281: `onSettings("after", () => spSetWire());`
- 26259 v285: `onSettings("after", () => {`
- 26299 : `onNav("after", () => requestAnimationFrame(() => { try { cdTick(); } catch (e) {} }));`
- 26584 v288: `onNav("before", () => { TEA.nav = performance.now(); if (TEA.on) teaStop(); });`
- 27001 v297: `onSettings("before", () => srlOff());`
- 27002 v297: `onSettings("after", () => srlBuild());`

## Blocks

| lines | kind | KB | what |
|---|---|---|---|
| 15–2905 | style | 202 | **CORE** — — |
| 2909–2943 | script | 3 | v252: the card's markup, here so the launch intro can use it before the rest of the page loads. o: k (speed), ar (width / height), word, rv (reveal… |
| 2943–3324 | script | 27 | REEL-START: "One pixel plays everything", the 15s opening reel (120 BPM, 30 beats). Every pixel is computed from t alone (no timers, no state… |
| 3437–14546 | script | 729 | **CORE** — ===== v279: extend(name, hook) — how a feature adds to an app function, instead of wrapping it ===== The app grew by wrapping functions (const _f =… |
| 14547–14565 | style | 1 | — |
| 14565–14605 | script | 2 | — |
| 14606–14642 | style | 3 | v147 Top board: ranked rows, big score, share as an image |
| 14642–14732 | script | 7 | v147 Top board in Played, plus a share-as-image button |
| 14733–14759 | style | 2 | v150 badges |
| 14759–14828 | script | 4 | v150 badges: worked out from what you've done, saved with the date you earned them |
| 14828–14834 | script | 0 | v151 art strength slider: live while dragging, saved when let go |
| 14834–14835 | script | 0 | — |
| 14835–14852 | style | 1 | v155 light board motion: only fade + slide, which the phone's graphics chip does for free |
| 14852–14869 | script | 1 | — |
| 14869–14895 | style | 2 | v156 scroll-driven motion: the browser ties these to your finger on its own graphics thread, so they stay at full refresh rate however fast you… |
| 14895–14902 | style | 0 | v157 the real drag: a full-screen blurred picture sat behind every screen, slowly zooming forever, so the phone re-blurred the whole screen on every… |
| 14902–14909 | script | 0 | — |
| 14910–14929 | style | 1 | v163: Why you'll love it — one sheet, fades and slides in, nothing loops |
| 14929–14999 | script | 4 | v163: "Why you'll love it" — sends your ratings and this game to your Cloudflare helper, which asks free Workers AI why it fits your taste (or… |
| 15000–15031 | script | 2 | v166: reset scores — one game from its page, or everything from Settings → Your data. Games, art, hours and diary stay; played games stay played,… |
| 15032–15042 | style | 1 | v167 Nexi's glow sometimes showed as a square box. Three stacked fixes (blur, clip-path, mask) each add a separate layer the phone can drop… |
| 15043–15080 | style | 3 | v169 speed: these animations changed colours/sizes/positions, which makes the phone redraw on every frame. Same looks, now moved or faded instead,… |
| 15081–15092 | style | 1 | v171 swipe a queue card left to delete the game. Only the card's contents slide (translate), the red button waits underneath. |
| 15092–15153 | script | 3 | — |
| 15154–15169 | style | 1 | v172 what's-new tour: a small card with Nexi's words, while Nexi points at the real button |
| 15169–15371 | script | 18 | v172: after every update Nexi shows what's new — where it is, and a nudge to try it. HOW TO ADD ONE: push an entry to WHATS_NEW with the version it… |
| 15371–15614 | style | 22 | ===== v174 Home dashboard (layout from the owner's reference, our black + blue colours) ===== |
| 15614–15700 | style | 8 | ===== v177 play sessions ===== |
| 15700–15831 | script | 9 | ===== v178 calmer settings and edit: search, most-used shortcuts, one section at a time, fewer knobs up front ===== |
| 15831–15877 | style | 4 | ===== v178 calmer settings ===== |
| 15877–15890 | style | 1 | ===== v180 finish month picker ===== |
| 15890–15898 | style | 1 | ===== v181 theme picker ===== |
| 15898–16045 | script | 13 | ===== v181 Game Shuffler: swipe through your games one at a time (right = tempted, left = not now). Styled after the owner's chess reference: a big… |
| 16045–16126 | style | 8 | ===== v181 Game Shuffler (chess reference layout, our black + theme accent) ===== |
| 16126–16149 | style | 2 | ===== v182 lists ===== |
| 16149–16186 | style | 3 | ===== v183 extras off by default, quick duel / score sheets, usage table, quick add ===== |
| 16186–16208 | script | 2 | ===== v183 what's new + settings extras ===== |
| 16208–16252 | script | 3 | ---- badges that keep going as you duel ---- |
| 16252–16329 | style | 7 | ===== v184 ranking: tiers, confidence dots, On deck, Nexi on Home, insights ===== |
| 16329–16348 | script | 2 | ===== v185 what's new ===== |
| 16348–16395 | style | 4 | ===== v185 Nexi chat, personalities, similar games ===== |
| 16395–16403 | script | 0 | ===== v186 what's new ===== |
| 16403–16431 | script | 3 | ===== v187: similar games picked by the AI and checked on RAWG; helper status in Settings → Nexi ===== |
| 16431–16549 | style | 8 | ===== v188 "Clean" style: the rest of the app speaks Home's language ===== One layer, scoped to html.ui2 (Settings → Look of the app → Style →… |
| 16549–16560 | script | 1 | ===== v188 what's new: the Clean style ===== |
| 16560–16580 | style | 2 | ===== v189 tour: Nexi drops out of the notch / camera hole, then rides on the tour card ===== |
| 16580–16681 | script | 8 | ===== v189 tour: Nexi comes out of the notch and stays with the tour card ===== |
| 16681–16837 | style | 12 | ===== v194 "Sicker everywhere": Home's look on every screen (Clean style only) ===== Each screen's header is a card like Home's: a big title, live… |
| 16837–17047 | script | 16 | ===== v194: every screen's header is a card like Home's ===== |
| 17047–17163 | style | 12 | ===== v195: the Add screen and a game's page in Home's language (both switchable) ===== |
| 17163–17213 | script | 4 | ===== v195: a game's page, New style ===== |
| 17213–17218 | style | 0 | v196: on phones under ~400px the pick card was 352px wide whatever the screen (its 220px min-height through the 16:10 ratio set a minimum width) and… |
| 17218–17482 | script | 21 | ===== v196: problem reports. The app notices when something breaks and files it (Settings → This build) ===== What it watches: script errors, a page… |
| 17482–17500 | style | 2 | — |
| 17500–17597 | script | 8 | ===== v197: Nexi says when he's spotted (and already reported) a problem, and the report sheet lists them ===== |
| 17597–17689 | script | 8 | ===== v198: problem reports catch lag, not just freezes ===== The v196 check only saw 400ms+ freezes; the stutters you feel on a 120Hz screen are… |
| 17689–17792 | script | 8 | ===== v199: Nexi's eyes see more: cut-off text, text on top of text, a blank screen ===== Runs with bugLayout (idle, once a screen settles). Only… |
| 17792–17819 | script | 2 | ===== v200: the two lags the owner felt: Queue scrolling and a game page opening ===== |
| 17819–17831 | script | 1 | ===== v201: every picture in the SteamGridDB and Steam pickers shows its real size (4K / HD / Low) before you pick; SteamGridDB lists sharpest first… |
| 17831–17881 | script | 4 | ===== v202: 4K art really shows in 4K (the swap to the sharp copy was starved by the live countdown, see the liteUp kick), RAWG screenshots as… |
| 17881–17905 | script | 2 | ===== v203: a game page is sharp as soon as it opens. warmGame (touch-down, and each neighbour for the swipe) already downloaded and decoded the… |
| 17905–17979 | style | 7 | ===== v204: each personality has its own face, idle move and a signature move when it talks. Persona rules use :where() so they beat the plain face… |
| 17979–18223 | script | 21 | ===== v204: Nexi's personalities get their own lines (context-aware, ~20 each), faces and moves; more everyday lines for everyone; double tap opens… |
| 18223–18386 | style | 17 | ===== v205: Taste → Insights. Psychology, Patterns and Analytics in one page, ordered the way attention works: who you are (identity) → how good… |
| 18386–18580 | script | 19 | ===== v205: Taste → Insights (see the style block above for the order and why) ===== |
| 18580–18629 | script | 4 | ===== v206: lag fixes from the problem reports ===== render() rebuilt all seven screens on every save, add or delete (#58: a 260ms tap on Edit →… |
| 18629–18641 | style | 1 | v208: found by Nexi's new checks on the first sweep: queue rank numbers were drawn in the border colour (1.9:1, hard to read) and the small icon… |
| 18641–18904 | script | 22 | ===== v208: Nexi's new senses. The owner hit 10–15 problems he never saw: a page that flashed back while closing (#68), a description cut short with… |
| 18904–18921 | script | 1 | ===== v209: #81 (4K art stayed low-res). The fix itself is in liteUp's go() (retries, decode() fallback, LITE_NOFULL) and warmGame; this is the tour… |
| 18921–19176 | script | 19 | ===== v211: cloud backup ===== Everything on this phone is sealed here (AES-GCM, key made from the restore code) and sent to the helper, which only… |
| 19176–19192 | style | 1 | — |
| 19192–19298 | script | 8 | ===== v212: "Where was I?" notes ===== A game keeps short notes about where you stopped (g.wasI = [{t, x}], newest last, 20 kept). The newest shows… |
| 19298–19338 | style | 4 | — |
| 19338–19728 | script | 41 | ===== v213: price alerts (Steam + PlayStation Store) ===== Games you're waiting for (not played, not dropped, not competitive) are priced through… |
| 19728–19784 | style | 4 | — |
| 19784–19796 | style | 1 | ===== v215 fixes ===== |
| 19796–19922 | script | 13 | ===== v215: the frozen game page (#99, #102, #103), Read more (#85, #100), Taste names (#86), Queue rows (#89), 4K pictures by default (#84), and… |
| 19922–19937 | style | 1 | ===== v216: the story so far ===== |
| 19937–19994 | script | 5 | ===== v216: "the story so far". Under your newest 📍 note on a game's page, Nexi writes a short recap of the plot up to where the note says you are… |
| 19994–20078 | script | 8 | ===== v216: Steam's genres and "more like this". Steam's own tags are the genres players vote on ("Souls-like", "Metroidvania", "JRPG",… |
| 20078–20097 | script | 2 | — |
| 20097–20104 | style | 0 | ===== v217: release-date watcher + deal radar ===== |
| 20104–20224 | script | 11 | ===== v217: two new things Nexi does on his own (the owner loves automatic features). 1. Release-date watcher (`rdWatch`): every game you're waiting… |
| 20224–20228 | style | 0 | ===== v218: "Finished it?" (same card as Where was I?) ===== |
| 20228–20310 | script | 7 | ===== v218: "Finished it?" — Nexi notices when a game you're playing is probably done and asks, by himself. Signals (`fnWhy(g)`, first that fits):… |
| 20310–20328 | style | 1 | ===== v219: reviews are in (+ your score, predicted) ===== |
| 20328–20570 | script | 20 | ===== v219: three more things Nexi does by himself, and smarter ones (the owner: "I need more smarter and useful auto things"). 1. Reviews are in… |
| 20570–20601 | style | 3 | ===== v220: away report, finish forecast, time capsule, seasons ===== |
| 20601–20844 | script | 22 | ===== v220: six more things that happen by themselves (the owner: "more auto, unique, rare and out of the box"). 1. While you were away (`aw*`):… |
| 20844–20863 | style | 1 | ===== v221: logo fit, Nexi in the rail, dusty backlog, crystal ball ===== |
| 20863–21113 | script | 23 | ===== v221: the owner's and their friend's reports (#118 prices stuck on "checking…", #114 "fit the logo in the queue automatically", #123 "the rail… |
| 21113–21126 | script | 1 | ===== v222: #153 tour steps that point at things only there once you've scored games. The fixes are in the v184/v215 steps (fall back to the "What… |
| 21126–21138 | script | 1 | ===== v224: prices in one line (pxCard, store logos PX_LOGO) + a tap on seated Nexi hides the rail (railHideNow) ===== |
| 21138–21144 | style | 0 | ===== v225: the cover you tap flies into the game page ===== |
| 21144–21210 | script | 5 | ===== v225: the cover you tap flies into the game page ===== Opening a game from anything that shows its cover (Home, queue, library, rail, deck,… |
| 21210–21219 | script | 1 | ===== v226: platforms as logos on the game page (platChips / platHtml, next to platList) ===== |
| 21219–21273 | script | 3 | ===== v227: prices for games the stores' title search can't find (the owner's GTA 6: the PS Store's old search doesn't list it, though its store… |
| 21273–21282 | script | 1 | ===== v228: platform logos in one pill; price checks never skip a store for good (pxStale/pxGap, batches of 5); helper v13 ===== |
| 21283–21588 | script | 33 | ===== v229: Nexi's brain. The owner wants Nexi "super smart", saying a lot more, all of it different and useful, and using their name much more.… |
| 21588–21604 | style | 1 | v230: Browse and choose, every store |
| 21604–21717 | script | 9 | ===== v230: pictures straight from the stores. Browse and choose (Edit → Look) now shows, next to SteamGridDB and RAWG, the official art of Steam… |
| 21717–21726 | script | 1 | ===== v231 (daily fixer): #180/#181/#154 the tour's Next button answered slowly (it saved and opened the next screen inside the tap; now that waits… |
| 21726–21830 | style | 9 | ===== v232: the same shape for every open queue card and every game page ===== |
| 21830–22064 | script | 22 | ===== v232: the owner: "each queue card is a different size when open" and "the new game page lacks organisation: each game looks different because… |
| 22064–22141 | style | 8 | ===== v236: game suggestions you can see (big cards, a preview with store screenshots), swipe to add, Discover, share to add ===== |
| 22141–22454 | script | 28 | ===== v236: the owner: "adding Baldur's Gate 3 from Similar added the wrong game; the pictures are too small and I need more of a description; maybe… |
| 22454–22469 | style | 1 | ===== v237: screenshots on every game page; Grumble only takes real hits ===== |
| 22469–22587 | script | 11 | ===== v237: the owner: "I deleted Baldur's Gate and Grumble lost HP, which is wrong; and when I add a game, forget to say it's played and then say… |
| 22587–22732 | script | 12 | ===== v238: the owner: "only a few games got screenshots, and the ones that did took time to load. I want them instant and always ready, not fetched… |
| 22732–22836 | style | 12 | ===== v239/v242: Game room — a little 3D room built from your games (owner's isometric "B" room reference: layout and style, our colours). Plain CSS… |
| 22836–23205 | script | 31 | ===== v239: Game room. The owner sent an isometric illustration (a room built around a giant book-letter) and asked for something like it in 3D that… |
| 23205–23236 | style | 3 | v241: hours to beat from HowLongToBeat |
| 23236–23441 | script | 16 | ===== v241: hours to beat from HowLongToBeat (`hl*`). The owner: "most hours I type by hand; the automatic ones are super wrong". Two automatic… |
| 23441–23459 | script | 1 | ===== v243 (#313, the owner: "pressing on the text should expand it, not only Read more"): a tap on a cut-short description (or the story so far)… |
| 23459–23470 | script | 1 | ===== v244 (#319): a tap on a game's description (or the story so far) now closes it again too; see the v243 listener. Tour fixes (#307): the v236… |
| 23470–23479 | script | 1 | ===== v245 (#301): pnShelf skips the Finished shelf while its screen is hidden (it forced a full layout of the screen you were on, 130ms at 4x);… |
| 23479–23489 | script | 1 | ===== v246 (#309): no press animation. The owner: "when I touch anything an animation happened, delete that". Until v268 this script stripped… |
| 23489–23493 | style | 0 | v247: the rail's Add and Settings buttons draw the menu's own icons (they were the text glyphs ＋ and ⚙) |
| 23493–23505 | script | 1 | ===== v247: new menu icons, picked by the owner from two drawn sets: Home (house), Games (game case with a disc), Plan (calendar with a release… |
| 23505–23648 | script | 11 | ===== v248: three things that run by themselves. 1. Release days in your calendar: Settings → Extra features → turn on makes a random id (S.calId),… |
| 23648–23679 | style | 3 | v249: buy advice line under the prices, the drifting card on a game page, the shopping plan and queue clean-up sheets |
| 23679–24040 | script | 33 | ===== v249: three smarter things that run by themselves (the owner picked them from a list: "1 2 4"). 1. Buy advice (`sm*` / `smBuy`): price alerts… |
| 24040–24115 | script | 7 | ===== v249, smoother everywhere (the owner: "all tabs and windows lag"; "a lot of lags that even Nexi doesn't see"). 1. Nexi's own problem checks… |
| 24115–24144 | script | 2 | ===== v250 (the owner, with a screenshot of a "picked for you" card: "not all games have screenshots inside here, fix"): 1. The preview (gpLoad)… |
| 24144–24179 | style | 3 | ===== v251: the rail's live strip (replaces the queue covers) + Nexi sized to his seat ===== |
| 24179–24369 | script | 16 | ===== v251 (the owner, screenshot of the unfolded rail with the queue covers circled: "change them into something useful with live animation that is… |
| 24369–24377 | script | 1 | v253 (#338): the view switcher's marker is measured in the next frame (slidePill), not inside the tap |
| 24377–24391 | script | 1 | after v255 (#354 #355 #358): the lag checks skip frames that overlap a resize (fold/unfold, 0.8s after) or the time the app was hidden (asleep, in… |
| 24391–24402 | script | 1 | v256 (#352): the calling card is drawn in steps (callingCard, yf) |
| 24402–24467 | style | 7 | v259 three new card looks, from the owner's references (layout and style copied, our colours): dzp = Home "Playing now" colour cards, dzd = On deck… |
| 24467–24558 | script | 7 | v259 three new card looks (switch: S.dzX) |
| 24558–24601 | script | 3 | v260 rest: 12s without a touch, every looping animation holds still (no frames to draw, nothing for the GPU to do); the first touch, key, scroll or… |
| 24602–24606 | style | 0 | v261: the view pill's highlight slides on the GPU: it used to animate width (a layout every frame for .28s) |
| 24606–24622 | script | 1 | v261: smooth at the screen's own refresh rate. The highlight takes its new width at once and a transform animation (scaleX from the old width) makes… |
| 24623–24662 | script | 3 | v261: Nexi learned (the owner: "still lags a lot, I want 120 fps"): an animation the phone can't hand to its GPU makes the main thread draw every… |
| 24662–24669 | script | 1 | — |
| 24670–24677 | style | 0 | v263 (120 fps): Nexi's face: a mood used to morph his eyes and mouth (height, top, width, borders: main-thread transitions), and a blink restarted… |
| 24677–24829 | script | 11 | v263 (120 fps): a looping animation on something that isn't drawn (a hidden screen; a list row scrolled far enough that the browser skips drawing… |
| 24829–24894 | script | 5 | v264: Nexi learned (the owner's fifth "still lags", with nothing in his own reports since v263): the lag he couldn't see was the GPU blending a pile… |
| 24894–24904 | style | 1 | v265 smoothness check (prb*, last script): while it tests a suspect, that suspect is switched off for a little while |
| 24904–25028 | script | 9 | v265: the smoothness check (the owner, after five rounds: "it lags everywhere, as if it's too much: a fast scroll up or down on the Queue, sideways… |
| 25028–25042 | script | 1 | v265 (the owner: "if I scroll fast it lags, up or down on the Queue"): touch scrolling no longer waits for the app. Every screen but Home and every… |
| 25042–25046 | script | 0 | the smoothness check counts swiping a game's page to the next game as a sideways moment (it's not a scroll) |
| 25046–25087 | script | 4 | v265: Nexi learned (the blocking touchmove above sat on every screen for months and nothing noticed: it doesn't throw, doesn't look wrong, and the… |
| 25087–25247 | script | 12 | v266: the self-test (step 1 of the plan the owner agreed: "measure first, on the phone, before changing anything"). Settings → This build → "Run the… |
| 25247–25256 | style | 1 | v266 self-test result card (inside the diary sheet's box) |
| 25256–25266 | style | 1 | v271: the progress line draws itself to the step a game just reached. While it runs, the segment's own lines go transparent and .gd-fill (a dim… |
| 25266–25273 | script | 0 | — |
| 25273–25458 | script | 14 | v267 picture fit, layer 2 (layer 1 is sw.js: nothing bigger than the screen). Lists show pictures far smaller than the screen: a Queue row's faded… |
| 25458–25563 | script | 9 | v267: Nexi learned (the phone's self-test: 372 MP of pictures on the Queue for 14 MP on screen, one picture 70 MP; none of his checks looked at… |
| 25563–25572 | script | 1 | v269: the tidy-up (the owner: "organise the code, merge it, cut what isn't needed"). No new feature: onNav (screen-change hooks in one place), dead… |
| 25572–25616 | script | 3 | v270: buzzes on the iPhone too. Safari has no navigator.vibrate, but iOS 18+ gives a light tap each time an <input type="checkbox" switch> flips,… |
| 25616–25673 | script | 4 | v271: two moments that used to jump now move (the owner: "smoothness is rule number one, but I do like animations"). Picked with the… |
| 25673–25682 | script | 1 | #432: Settings (#sheet) let a sideways swipe through to the game page behind it; the other sheets already stop it (noHub) |
| 25682–25695 | script | 1 | v273 (the owner: "return the background to a solid gradient color, I hate how it looks"): the live background (v257 lights, specks and drifting art;… |
| 25695–25703 | style | 1 | v274 (the owner, #447): one Nexi per screen. Unfolded, Home's own Nexi (the face beside your greeting) and the rail's seated Nexi showed at once:… |
| 25703–25716 | script | 1 | — |
| 25716–25724 | style | 0 | #201 (the friend, iPhone/iPad: "Restore a code is not working"): while a keyboard (or a zoom) hides the bottom of the screen, the open sheet sits on… |
| 25724–25779 | script | 4 | #201: Safari's keyboard doesn't shrink the page, it slides over it. Sheets (#diary) sit at the bottom of the screen, so a short one (Restore from a… |
| 25779–25786 | script | 1 | — |
| 25786–25799 | script | 1 | v276 (the owner: "black with a little blue gradient, mostly black, with the option to edit how much gradient and the background colour"): the… |
| 25799–25806 | script | 0 | — |
| 25806–25820 | style | 1 | v278 (#171, the friend: "a live preview when I'm editing the looks of all features, the same look everywhere, smart, off by default"): with Settings… |
| 25820–25853 | script | 3 | v278 (#171): when the sheet docks for Live preview. A screen's Customise always does; Settings itself only while one of the look groups is the one… |
| 25853–25878 | script | 2 | Nexi learned (#174, the friend, iPad v228: "Nexi disappeared while the rail was open"): with the rail open he sits at its foot (html.nxseat). A… |
| 25878–25900 | style | 2 | v278 (#130, the owner: "Radar needs a makeover, looks kinda ugly and doesn't match our theme"): Radar in the Clean style. Its words are sentence… |
| 25900–25922 | script | 2 | Nexi learned (#130, the owner: "Radar looks kinda ugly and doesn't match our theme"): Radar kept the old Arcade look (slanted, all-caps heading)… |
| 25922–25966 | script | 4 | v279 usage counts (the owner: "remove any junk or things that are not used"). Every tap and switch change counts once under the nearest name it has:… |
| 25966–25974 | script | 0 | v280's scroll hold (mvh*: every endless loop paused from the first move until the scroll stopped) was removed in v282 |
| 25974–26006 | style | 3 | v281 (#7) cards rising in by the browser itself (timeline-trigger) was removed in v284: in Chrome it left cards stuck invisible (More for you, the… |
| 26006–26155 | script | 11 | ===== v281 (#8): the bar on a game's page (styles above). Tapping the name scrolls back to the top. ===== |
| 26155–26175 | script | 2 | v282 (the owner: "App freezes a lot when I move or scroll and nexi animation and rail stops ... I dont want animation to stop while scrolling").… |
| 26175–26190 | script | 1 | v283 (#134 Month, #136 Insights, #416/#333/#367 switching to Library/Queue, all back after being closed): a Chromium trace of each scroll… |
| 26190–26234 | script | 3 | v284 (the owner, a screenshot: "Fix this empty space it should have 2 stuff"): Home's "More for you" was an empty band on the Fold. v281's reveal by… |
| 26234–26281 | script | 3 | v285 (#177, the friend: "all the sounds in the app are not working" on an iPhone 11 and an iPad 9th gen): on iPhone/iPad a web app's sounds follow… |
| 26281–26308 | script | 2 | Oct 2026 clean-up (step 3b, work nobody sees). 1. A CSS loop that was held (its screen hidden, or resting) and set going again with play() no longer… |
| 26308–26328 | style | 2 | ===== v287: fewer GPU layers (#60 Home, #62 game page, #207 Played, #116 blank on Queue). The phone's own timing of a Played stutter: "1 long frame,… |
| 26328–26335 | script | 0 | the shelf's covers get their own layers while it's swiped, and give them back 400ms after it stops |
| 26335–26431 | script | 8 | v287: Nexi spots GPU layers piling up on the screen you're on. The stutters that kept coming back (#60, #62, #207) were drawing, not code (the… |
| 26431–26466 | style | 3 | ===== v288: Nexi's tea break. The owner (after a Spy x Family ending where the family never gets to drink their tea): now and then, when the app… |
| 26466–26621 | script | 14 | ===== v288: Nexi's tea break (`teaTry`). Every few seconds `teaTick` looks whether the app has been still for 25s (no touch, scroll or screen… |
| 26621–26775 | script | 13 | ===== v290: Queue logos, look A (the owner picked it from four demos: "make A") ===== The row logo has its own upright slot inside the row (CSS at… |
| 26775–26816 | style | 4 | ===== v297: Settings rail, look B (the owner picked it from four demos: "B looks very sick"). Settings and every Customise are one scroll of open… |
| 26816–27031 | script | 24 | ===== v297: Settings rail (look B) and drawn group icons ===== GICON: one 24x24 drawing per Settings / Customise / Edit group and Most used… |
| 27031–27401 | style | 38 | ===== v299: the open Queue card, round 9 "Sweep" (the owner, after rounds 7-9: "Perfect"; they hated v298's fixed blue slab, the round stamp that… |
| 27401–27487 | script | 9 | — |
| 27487–27504 | script | 2 | v305 (#207 Played): every finished game's row had its scroll-linked motion (view() timelines) all the time, and Chromium gives each scroll-linked… |
| 27504–27565 | script | 4 | v305: Nexi learned two more kinds of drawing load (the stutters whose phone timing said "script 0, render 157ms"): 1. pictures drawn as url()… |
| 27565–27603 | script | 3 | — |
