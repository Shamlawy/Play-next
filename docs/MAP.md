# Code map of index.html

Made by `node tools/map.mjs` (run it again after a release; line numbers move). 25655 lines, 1912 KB, 110 scripts, 62 styles.

How the file is built: one core style (line 15) and one core script (the biggest block below), then one patch per release, oldest first. A patch adds its own code and often wraps a core function (`const _of = openFull; openFull = function () { ... _of.apply(this, arguments) ... }`), so the **last** wrap runs first. To change what a function does, read every layer listed under "Wrapped functions". New code goes in the last script/style.

## Wrapped functions (most layers first)

| function | defined | layers (line: block) |
|---|---|---|
| `renderQueue` | 8352 | 3: 11120 core, 20496 v221, 21548 v232 |
| `pnBubShow` | 11956 | 3: 16143 v189, 17490 v204, 20545 v221 |
| `lookDesign` | 18261 | 3: 19393 v215, 20456 v221, 21455 v232 |
| `dyShow` | 10360 | 3: 20200 v220, 23604 v249, 25204 |
| `renderTaste` | 10249 | 2: 12730 core, 18064 v205 |
| `xFeatures` | 12398 | 2: 12798 core, 13213 core |
| `qCounts` | 7064 | 2: 13034 core, 21448 v232 |
| `queueList` | 7073 | 2: 13035 core, 21449 v232 |
| `pnPowerUp` | 13532 | 2: 13982 core, 24108 v260 |
| `setSub` | 3697 | 2: 17892 v205, 20501 v221 |
| `nxContext` | 12858 | 2: 18782 v212, 21077 v229 |
| `wiAdd` | 18700 | 2: 19469 v216, 19801 v218 |
| `pxRun` | 18923 | 2: 19708 v217, 23356 v249 |
| `nxPlan` | 13099 | 2: 19804 v218, 19975 v219 |
| `gdMarkup` | 16665 | 2: 19964 v219, 20235 v220 |
| `pnBubReact` | 11554 | 2: 20298 v220, 22005 v237 |
| `tasteInsightsPage` | 17919 | 2: 20587 v221, 24031 v259 |
| `closeFull` | 9582 | 2: 20706 v225, 23602 v249 |
| `gdShots` | 22015 | 2: 22176 v238, 22207 v238 |
| `sgdbFillOne` | 4421 | 2: 22211 v238, 22840 v241 |

## Screen-change hooks (onNav / onSettings, since v269)

goTab / goSub / openFull run every `onNav("before"|"after", fn)` hook and openSettings every `onSettings(...)` hook (one wrap each, defined at line 12436, in registration order below). New code registers here; `kit.mjs syntax` fails on a new wrap of those four.

- 12451: `onNav("after", (k, a) => { if (k === "sub") try { uVisit(a[0]); } catch (e) {} });`
- 12452: `onNav("before", (k, a) => { if (k === "game" && (hubAt !== a[0] || !$("#full.on"))) uCount("o", "game"); });`
- 12454: `onSettings("before", (k, a) => { const sc = a[0]; uCount("o", "settings" + (sc && sc !== "app" ? "/" + sc : ""`
- 15319 v178: `onSettings("after", () => { $("#sheet-in").classList.remove("drill", "ssearch"); calmSheet(true); });`
- 15923 v187: `onSettings("after", () => setTimeout(helperCheck, 50));`
- 16482 v194: `onSettings("after", () => shxSetWire());`
- 16894 v196: `onNav("after", () => bugSeen());`
- 16895 v196: `onSettings("after", () => { bugCrumb("screen settings"); bugLayoutSoon(); });`
- 17389 v203: `onNav("before", k => { if (k === "game") openAt = performance.now(); });`
- 18073 v205: `onNav("after", k => { if (k === "tab" && curTab() !== "taste") ixShown.clear(); });`
- 18615 v211: `onSettings("after", () => cbSetWire());`
- 19176 v213: `onSettings("after", () => pxSetWire());`
- 19178 v213: `onNav("after", (k, a) => { if (k !== "game") return;`
- 19470 v216: `onNav("after", (k, a) => { if (k !== "game") return; const g = S.games.find(x => x.id === a[0]);`
- 19968 v219: `onNav("after", (k, a) => { if (k !== "game") return;`
- 20199 v220: `onSettings("before", () => { const el = document.getElementById("sheet"); if (el) el._flkOpen = performance.no`
- 20500 v221: `onNav("after", k => { if (k === "tab" && !lfBox) setTimeout(() => { try { lfApply(); } catch (e) {} }, 60); })`
- 20503 v221: `onNav("before", (k, a) => { if (k !== "game") return; const g = S.games.find(x => x.id === a[0]), d = g ? dust`
- 20700 v225: `onNav("before", k => { if (k !== "game") return;`
- 22041 v237: `onNav("after", (k, a) => { if (k !== "game") return;`
- 23601 v249: `onNav("after", k => { if (k === "tab") lagWatch(() => "Switching to " + lagWhere()); else if (k === "game") la`
- 23603 v249: `onSettings("after", () => lagWatch("Opening Settings"));`
- 24400 v264: `onNav("after", k => { if (k === "tab") { STACK.go = null; stackArm(3500); } });`
- 24523 v265: `onNav("before", k => prbStart(k === "game" ? "open" : "switch", 1000));`
- 24917 v267: `onNav("after", () => requestAnimationFrame(fitRetry));`
- 24962 v267: `onNav("after", k => { if (k !== "sub") picArm(4000); });`
- 25003 v267: `onNav("after", k => { if (k !== "sub") picWait(); });`
- 25109 v271: `onNav("after", (k, a) => {`
- 25287 v278: `onSettings("before", (k, a) => { lvpScope = SCOPE_NAME[a[0]] ? a[0] : "app"; });`
- 25288 v278: `onSettings("after", () => requestAnimationFrame(lvpSync));`
- 25588 v281: `onSettings("after", () => spSetWire());`

## Blocks

| lines | kind | KB | what |
|---|---|---|---|
| 15–2954 | style | 205 | **CORE** — — |
| 2958–2993 | script | 3 | v252: the card's markup, here so the launch intro can use it before the rest of the page loads. o: k (speed), ar (width / height), word, rv (reveal… |
| 3106–14031 | script | 703 | **CORE** — ===== v279: extend(name, hook) — how a feature adds to an app function, instead of wrapping it ===== The app grew by wrapping functions (const _f =… |
| 14032–14050 | style | 1 | — |
| 14050–14090 | script | 2 | — |
| 14091–14127 | style | 3 | v147 Top board: ranked rows, big score, share as an image |
| 14127–14217 | script | 7 | v147 Top board in Played, plus a share-as-image button |
| 14218–14244 | style | 2 | v150 badges |
| 14244–14313 | script | 4 | v150 badges: worked out from what you've done, saved with the date you earned them |
| 14313–14319 | script | 0 | v151 art strength slider: live while dragging, saved when let go |
| 14319–14320 | script | 0 | — |
| 14320–14337 | style | 1 | v155 light board motion: only fade + slide, which the phone's graphics chip does for free |
| 14337–14354 | script | 1 | — |
| 14354–14380 | style | 2 | v156 scroll-driven motion: the browser ties these to your finger on its own graphics thread, so they stay at full refresh rate however fast you… |
| 14380–14388 | style | 1 | v157 the real drag: a full-screen blurred picture sat behind every screen, slowly zooming forever, so the phone re-blurred the whole screen on every… |
| 14388–14395 | script | 0 | — |
| 14396–14415 | style | 1 | v163: Why you'll love it — one sheet, fades and slides in, nothing loops |
| 14415–14485 | script | 4 | v163: "Why you'll love it" — sends your ratings and this game to your Cloudflare helper, which asks free Workers AI why it fits your taste (or… |
| 14486–14517 | script | 2 | v166: reset scores — one game from its page, or everything from Settings → Your data. Games, art, hours and diary stay; played games stay played,… |
| 14518–14528 | style | 1 | v167 Nexi's glow sometimes showed as a square box. Three stacked fixes (blur, clip-path, mask) each add a separate layer the phone can drop… |
| 14529–14564 | style | 3 | v169 speed: these animations changed colours/sizes/positions, which makes the phone redraw on every frame. Same looks, now moved or faded instead,… |
| 14565–14576 | style | 1 | v171 swipe a queue card left to delete the game. Only the card's contents slide (translate), the red button waits underneath. |
| 14576–14637 | script | 3 | — |
| 14638–14653 | style | 1 | v172 what's-new tour: a small card with Nexi's words, while Nexi points at the real button |
| 14653–14855 | script | 18 | v172: after every update Nexi shows what's new — where it is, and a nudge to try it. HOW TO ADD ONE: push an entry to WHATS_NEW with the version it… |
| 14855–15098 | style | 22 | ===== v174 Home dashboard (layout from the owner's reference, our black + blue colours) ===== |
| 15098–15184 | style | 8 | ===== v177 play sessions ===== |
| 15184–15324 | script | 9 | ===== v178 calmer settings and edit: search, most-used shortcuts, one section at a time, fewer knobs up front ===== |
| 15324–15370 | style | 4 | ===== v178 calmer settings ===== |
| 15370–15383 | style | 1 | ===== v180 finish month picker ===== |
| 15383–15391 | style | 1 | ===== v181 theme picker ===== |
| 15391–15538 | script | 13 | ===== v181 Game Shuffler: swipe through your games one at a time (right = tempted, left = not now). Styled after the owner's chess reference: a big… |
| 15538–15619 | style | 8 | ===== v181 Game Shuffler (chess reference layout, our black + theme accent) ===== |
| 15619–15645 | style | 2 | ===== v182 lists ===== |
| 15645–15682 | style | 3 | ===== v183 extras off by default, quick duel / score sheets, usage table, quick add ===== |
| 15682–15705 | script | 2 | ===== v183 what's new + settings extras ===== |
| 15705–15750 | script | 3 | ---- badges that keep going as you duel ---- |
| 15750–15827 | style | 7 | ===== v184 ranking: tiers, confidence dots, On deck, Nexi on Home, insights ===== |
| 15827–15847 | script | 2 | ===== v185 what's new ===== |
| 15847–15894 | style | 4 | ===== v185 Nexi chat, personalities, similar games ===== |
| 15894–15902 | script | 0 | ===== v186 what's new ===== |
| 15902–15930 | script | 3 | ===== v187: similar games picked by the AI and checked on RAWG; helper status in Settings → Nexi ===== |
| 15930–16048 | style | 8 | ===== v188 "Clean" style: the rest of the app speaks Home's language ===== One layer, scoped to html.ui2 (Settings → Look of the app → Style →… |
| 16048–16059 | script | 1 | ===== v188 what's new: the Clean style ===== |
| 16059–16079 | style | 2 | ===== v189 tour: Nexi drops out of the notch / camera hole, then rides on the tour card ===== |
| 16079–16180 | script | 8 | ===== v189 tour: Nexi comes out of the notch and stays with the tour card ===== |
| 16180–16336 | style | 12 | ===== v194 "Sicker everywhere": Home's look on every screen (Clean style only) ===== Each screen's header is a card like Home's: a big title, live… |
| 16336–16546 | script | 16 | ===== v194: every screen's header is a card like Home's ===== |
| 16546–16662 | style | 12 | ===== v195: the Add screen and a game's page in Home's language (both switchable) ===== |
| 16662–16712 | script | 4 | ===== v195: a game's page, New style ===== |
| 16712–16717 | style | 0 | v196: on phones under ~400px the pick card was 352px wide whatever the screen (its 220px min-height through the 16:10 ratio set a minimum width) and… |
| 16717–16982 | script | 21 | ===== v196: problem reports. The app notices when something breaks and files it (Settings → This build) ===== What it watches: script errors, a page… |
| 16982–17000 | style | 2 | — |
| 17000–17097 | script | 8 | ===== v197: Nexi says when he's spotted (and already reported) a problem, and the report sheet lists them ===== |
| 17097–17189 | script | 8 | ===== v198: problem reports catch lag, not just freezes ===== The v196 check only saw 400ms+ freezes; the stutters you feel on a 120Hz screen are… |
| 17189–17293 | script | 8 | ===== v199: Nexi's eyes see more: cut-off text, text on top of text, a blank screen ===== Runs with bugLayout (idle, once a screen settles). Only… |
| 17293–17320 | script | 2 | ===== v200: the two lags the owner felt: Queue scrolling and a game page opening ===== |
| 17320–17332 | script | 1 | ===== v201: every picture in the SteamGridDB and Steam pickers shows its real size (4K / HD / Low) before you pick; SteamGridDB lists sharpest first… |
| 17332–17382 | script | 4 | ===== v202: 4K art really shows in 4K (the swap to the sharp copy was starved by the live countdown, see the liteUp kick), RAWG screenshots as… |
| 17382–17406 | script | 2 | ===== v203: a game page is sharp as soon as it opens. warmGame (touch-down, and each neighbour for the swipe) already downloaded and decoded the… |
| 17406–17480 | style | 7 | ===== v204: each personality has its own face, idle move and a signature move when it talks. Persona rules use :where() so they beat the plain face… |
| 17480–17724 | script | 21 | ===== v204: Nexi's personalities get their own lines (context-aware, ~20 each), faces and moves; more everyday lines for everyone; double tap opens… |
| 17724–17887 | style | 17 | ===== v205: Taste → Insights. Psychology, Patterns and Analytics in one page, ordered the way attention works: who you are (identity) → how good… |
| 17887–18081 | script | 19 | ===== v205: Taste → Insights (see the style block above for the order and why) ===== |
| 18081–18130 | script | 4 | ===== v206: lag fixes from the problem reports ===== render() rebuilt all seven screens on every save, add or delete (#58: a 260ms tap on Edit →… |
| 18130–18142 | style | 1 | v208: found by Nexi's new checks on the first sweep: queue rank numbers were drawn in the border colour (1.9:1, hard to read) and the small icon… |
| 18142–18402 | script | 21 | ===== v208: Nexi's new senses. The owner hit 10–15 problems he never saw: a page that flashed back while closing (#68), a description cut short with… |
| 18402–18419 | script | 1 | ===== v209: #81 (4K art stayed low-res). The fix itself is in liteUp's go() (retries, decode() fallback, LITE_NOFULL) and warmGame; this is the tour… |
| 18419–18674 | script | 19 | ===== v211: cloud backup ===== Everything on this phone is sealed here (AES-GCM, key made from the restore code) and sent to the helper, which only… |
| 18674–18690 | style | 1 | — |
| 18690–18795 | script | 8 | ===== v212: "Where was I?" notes ===== A game keeps short notes about where you stopped (g.wasI = [{t, x}], newest last, 20 kept). The newest shows… |
| 18795–18835 | style | 4 | — |
| 18835–19225 | script | 41 | ===== v213: price alerts (Steam + PlayStation Store) ===== Games you're waiting for (not played, not dropped, not competitive) are priced through… |
| 19225–19281 | style | 4 | — |
| 19281–19293 | style | 1 | ===== v215 fixes ===== |
| 19293–19414 | script | 12 | ===== v215: the frozen game page (#99, #102, #103), Read more (#85, #100), Taste names (#86), Queue rows (#89), 4K pictures by default (#84), and… |
| 19414–19429 | style | 1 | ===== v216: the story so far ===== |
| 19429–19486 | script | 5 | ===== v216: "the story so far". Under your newest 📍 note on a game's page, Nexi writes a short recap of the plot up to where the note says you are… |
| 19486–19586 | script | 9 | ===== v216: Steam's genres and "more like this". Steam's own tags are the genres players vote on ("Souls-like", "Metroidvania", "JRPG",… |
| 19586–19605 | script | 2 | — |
| 19605–19612 | style | 0 | ===== v217: release-date watcher + deal radar ===== |
| 19612–19732 | script | 11 | ===== v217: two new things Nexi does on his own (the owner loves automatic features). 1. Release-date watcher (`rdWatch`): every game you're waiting… |
| 19732–19736 | style | 0 | ===== v218: "Finished it?" (same card as Where was I?) ===== |
| 19736–19818 | script | 7 | ===== v218: "Finished it?" — Nexi notices when a game you're playing is probably done and asks, by himself. Signals (`fnWhy(g)`, first that fits):… |
| 19818–19836 | style | 1 | ===== v219: reviews are in (+ your score, predicted) ===== |
| 19836–20082 | script | 20 | ===== v219: three more things Nexi does by himself, and smarter ones (the owner: "I need more smarter and useful auto things"). 1. Reviews are in… |
| 20082–20113 | style | 3 | ===== v220: away report, finish forecast, time capsule, seasons ===== |
| 20113–20360 | script | 23 | ===== v220: six more things that happen by themselves (the owner: "more auto, unique, rare and out of the box"). 1. While you were away (`aw*`):… |
| 20360–20379 | style | 1 | ===== v221: logo fit, Nexi in the rail, dusty backlog, crystal ball ===== |
| 20379–20616 | script | 22 | ===== v221: the owner's and their friend's reports (#118 prices stuck on "checking…", #114 "fit the logo in the queue automatically", #123 "the rail… |
| 20616–20629 | script | 1 | ===== v222: #153 tour steps that point at things only there once you've scored games. The fixes are in the v184/v215 steps (fall back to the "What… |
| 20629–20641 | script | 1 | ===== v224: prices in one line (pxCard, store logos PX_LOGO) + a tap on seated Nexi hides the rail (railHideNow) ===== |
| 20641–20647 | style | 0 | ===== v225: the cover you tap flies into the game page ===== |
| 20647–20713 | script | 5 | ===== v225: the cover you tap flies into the game page ===== Opening a game from anything that shows its cover (Home, queue, library, rail, deck,… |
| 20713–20722 | script | 1 | ===== v226: platforms as logos on the game page (platChips / platHtml, next to platList) ===== |
| 20722–20776 | script | 3 | ===== v227: prices for games the stores' title search can't find (the owner's GTA 6: the PS Store's old search doesn't list it, though its store… |
| 20776–20785 | script | 1 | ===== v228: platform logos in one pill; price checks never skip a store for good (pxStale/pxGap, batches of 5); helper v13 ===== |
| 20786–21091 | script | 33 | ===== v229: Nexi's brain. The owner wants Nexi "super smart", saying a lot more, all of it different and useful, and using their name much more.… |
| 21091–21107 | style | 1 | v230: Browse and choose, every store |
| 21107–21221 | script | 9 | ===== v230: pictures straight from the stores. Browse and choose (Edit → Look) now shows, next to SteamGridDB and RAWG, the official art of Steam… |
| 21221–21230 | script | 1 | ===== v231 (daily fixer): #180/#181/#154 the tour's Next button answered slowly (it saved and opened the next screen inside the tap; now that waits… |
| 21230–21338 | style | 10 | ===== v232: the same shape for every open queue card and every game page ===== |
| 21338–21571 | script | 22 | ===== v232: the owner: "each queue card is a different size when open" and "the new game page lacks organisation: each game looks different because… |
| 21571–21648 | style | 8 | ===== v236: game suggestions you can see (big cards, a preview with store screenshots), swipe to add, Discover, share to add ===== |
| 21648–21961 | script | 28 | ===== v236: the owner: "adding Baldur's Gate 3 from Similar added the wrong game; the pictures are too small and I need more of a description; maybe… |
| 21961–21976 | style | 1 | ===== v237: screenshots on every game page; Grumble only takes real hits ===== |
| 21976–22095 | script | 11 | ===== v237: the owner: "I deleted Baldur's Gate and Grumble lost HP, which is wrong; and when I add a game, forget to say it's played and then say… |
| 22095–22240 | script | 12 | ===== v238: the owner: "only a few games got screenshots, and the ones that did took time to load. I want them instant and always ready, not fetched… |
| 22240–22344 | style | 12 | ===== v239/v242: Game room — a little 3D room built from your games (owner's isometric "B" room reference: layout and style, our colours). Plain CSS… |
| 22344–22713 | script | 31 | ===== v239: Game room. The owner sent an isometric illustration (a room built around a giant book-letter) and asked for something like it in 3D that… |
| 22713–22744 | style | 3 | v241: hours to beat from HowLongToBeat |
| 22744–22949 | script | 16 | ===== v241: hours to beat from HowLongToBeat (`hl*`). The owner: "most hours I type by hand; the automatic ones are super wrong". Two automatic… |
| 22949–22967 | script | 1 | ===== v243 (#313, the owner: "pressing on the text should expand it, not only Read more"): a tap on a cut-short description (or the story so far)… |
| 22967–22978 | script | 1 | ===== v244 (#319): a tap on a game's description (or the story so far) now closes it again too; see the v243 listener. Tour fixes (#307): the v236… |
| 22978–22987 | script | 1 | ===== v245 (#301): pnShelf skips the Finished shelf while its screen is hidden (it forced a full layout of the screen you were on, 130ms at 4x);… |
| 22987–22997 | script | 1 | ===== v246 (#309): no press animation. The owner: "when I touch anything an animation happened, delete that". Until v268 this script stripped… |
| 22997–23001 | style | 0 | v247: the rail's Add and Settings buttons draw the menu's own icons (they were the text glyphs ＋ and ⚙) |
| 23001–23013 | script | 1 | ===== v247: new menu icons, picked by the owner from two drawn sets: Home (house), Games (game case with a disc), Plan (calendar with a release… |
| 23013–23156 | script | 11 | ===== v248: three things that run by themselves. 1. Release days in your calendar: Settings → Extra features → turn on makes a random id (S.calId),… |
| 23156–23187 | style | 3 | v249: buy advice line under the prices, the drifting card on a game page, the shopping plan and queue clean-up sheets |
| 23187–23548 | script | 34 | ===== v249: three smarter things that run by themselves (the owner picked them from a list: "1 2 4"). 1. Buy advice (`sm*` / `smBuy`): price alerts… |
| 23548–23623 | script | 7 | ===== v249, smoother everywhere (the owner: "all tabs and windows lag"; "a lot of lags that even Nexi doesn't see"). 1. Nexi's own problem checks… |
| 23623–23652 | script | 2 | ===== v250 (the owner, with a screenshot of a "picked for you" card: "not all games have screenshots inside here, fix"): 1. The preview (gpLoad)… |
| 23652–23687 | style | 3 | ===== v251: the rail's live strip (replaces the queue covers) + Nexi sized to his seat ===== |
| 23687–23877 | script | 17 | ===== v251 (the owner, screenshot of the unfolded rail with the queue covers circled: "change them into something useful with live animation that is… |
| 23877–23885 | script | 1 | v253 (#338): the view switcher's marker is measured in the next frame (slidePill), not inside the tap |
| 23885–23899 | script | 1 | after v255 (#354 #355 #358): the lag checks skip frames that overlap a resize (fold/unfold, 0.8s after) or the time the app was hidden (asleep, in… |
| 23899–23910 | script | 1 | v256 (#352): the calling card is drawn in steps (callingCard, yf) |
| 23910–23985 | style | 8 | v259 three new card looks, from the owner's references (layout and style copied, our colours): dzp = Home "Playing now" colour cards, dzd = On deck… |
| 23985–24080 | script | 8 | v259 three new card looks (switch: S.dzX) |
| 24080–24122 | script | 3 | v260 rest: 12s without a touch, every looping animation holds still (no frames to draw, nothing for the GPU to do); the first touch, key, scroll or… |
| 24123–24127 | style | 0 | v261: the view pill's highlight slides on the GPU: it used to animate width (a layout every frame for .28s) |
| 24127–24143 | script | 1 | v261: smooth at the screen's own refresh rate. The highlight takes its new width at once and a transform animation (scaleX from the old width) makes… |
| 24144–24183 | script | 3 | v261: Nexi learned (the owner: "still lags a lot, I want 120 fps"): an animation the phone can't hand to its GPU makes the main thread draw every… |
| 24183–24190 | script | 1 | — |
| 24191–24198 | style | 0 | v263 (120 fps): Nexi's face: a mood used to morph his eyes and mouth (height, top, width, borders: main-thread transitions), and a blink restarted… |
| 24198–24343 | script | 11 | v263 (120 fps): a looping animation on something that isn't drawn (a hidden screen; a list row scrolled far enough that the browser skips drawing… |
| 24343–24408 | script | 5 | v264: Nexi learned (the owner's fifth "still lags", with nothing in his own reports since v263): the lag he couldn't see was the GPU blending a pile… |
| 24408–24414 | style | 1 | v265 smoothness check (prb*, last script): while it tests a suspect, that suspect is switched off for a little while |
| 24414–24538 | script | 9 | v265: the smoothness check (the owner, after five rounds: "it lags everywhere, as if it's too much: a fast scroll up or down on the Queue, sideways… |
| 24538–24552 | script | 1 | v265 (the owner: "if I scroll fast it lags, up or down on the Queue"): touch scrolling no longer waits for the app. Every screen but Home and every… |
| 24552–24556 | script | 0 | the smoothness check counts swiping a game's page to the next game as a sideways moment (it's not a scroll) |
| 24556–24597 | script | 4 | v265: Nexi learned (the blocking touchmove above sat on every screen for months and nothing noticed: it doesn't throw, doesn't look wrong, and the… |
| 24597–24757 | script | 12 | v266: the self-test (step 1 of the plan the owner agreed: "measure first, on the phone, before changing anything"). Settings → This build → "Run the… |
| 24757–24766 | style | 1 | v266 self-test result card (inside the diary sheet's box) |
| 24766–24776 | style | 1 | v271: the progress line draws itself to the step a game just reached. While it runs, the segment's own lines go transparent and .gd-fill (a dim… |
| 24776–24783 | script | 0 | — |
| 24783–24923 | script | 10 | v267 picture fit, layer 2 (layer 1 is sw.js: nothing bigger than the screen). Lists show pictures far smaller than the screen: a Queue row's faded… |
| 24923–25018 | script | 8 | v267: Nexi learned (the phone's self-test: 372 MP of pictures on the Queue for 14 MP on screen, one picture 70 MP; none of his checks looked at… |
| 25018–25027 | script | 1 | v269: the tidy-up (the owner: "organise the code, merge it, cut what isn't needed"). No new feature: onNav (screen-change hooks in one place), dead… |
| 25027–25071 | script | 3 | v270: buzzes on the iPhone too. Safari has no navigator.vibrate, but iOS 18+ gives a light tap each time an <input type="checkbox" switch> flips,… |
| 25071–25128 | script | 4 | v271: two moments that used to jump now move (the owner: "smoothness is rule number one, but I do like animations"). Picked with the… |
| 25128–25137 | script | 1 | #432: Settings (#sheet) let a sideways swipe through to the game page behind it; the other sheets already stop it (noHub) |
| 25137–25150 | script | 1 | v273 (the owner: "return the background to a solid gradient color, I hate how it looks"): the live background (v257 lights, specks and drifting art;… |
| 25150–25156 | style | 0 | v274 (the owner, #447): one Nexi per screen. Unfolded, Home's own Nexi (the face beside your greeting) and the rail's seated Nexi showed at once:… |
| 25156–25169 | script | 1 | — |
| 25169–25177 | style | 0 | #201 (the friend, iPhone/iPad: "Restore a code is not working"): while a keyboard (or a zoom) hides the bottom of the screen, the open sheet sits on… |
| 25177–25232 | script | 4 | #201: Safari's keyboard doesn't shrink the page, it slides over it. Sheets (#diary) sit at the bottom of the screen, so a short one (Restore from a… |
| 25232–25239 | script | 1 | — |
| 25239–25252 | script | 1 | v276 (the owner: "black with a little blue gradient, mostly black, with the option to edit how much gradient and the background colour"): the… |
| 25252–25259 | script | 0 | — |
| 25259–25273 | style | 1 | v278 (#171, the friend: "a live preview when I'm editing the looks of all features, the same look everywhere, smart, off by default"): with Settings… |
| 25273–25305 | script | 3 | v278 (#171): when the sheet docks for Live preview. A screen's Customise always does; Settings itself only while one of the look groups is the one… |
| 25305–25330 | script | 2 | Nexi learned (#174, the friend, iPad v228: "Nexi disappeared while the rail was open"): with the rail open he sits at its foot (html.nxseat). A… |
| 25330–25352 | style | 2 | v278 (#130, the owner: "Radar needs a makeover, looks kinda ugly and doesn't match our theme"): Radar in the Clean style. Its words are sentence… |
| 25352–25374 | script | 2 | Nexi learned (#130, the owner: "Radar looks kinda ugly and doesn't match our theme"): Radar kept the old Arcade look (slanted, all-caps heading)… |
| 25374–25418 | script | 4 | v279 usage counts (the owner: "remove any junk or things that are not used"). Every tap and switch change counts once under the nearest name it has:… |
| 25418–25426 | script | 0 | v280's scroll hold (mvh*: every endless loop paused from the first move until the scroll stopped) was removed in v282 |
| 25426–25470 | style | 4 | ===== v281 (#7): cards rise in when you scroll to them, by the browser itself (Chrome 145+: timeline-trigger + animation-trigger). Only "forwards":… |
| 25470–25620 | script | 11 | ===== v281 (#8): the bar on a game's page (styles above). Tapping the name scrolls back to the top. ===== |
| 25620–25640 | script | 2 | v282 (the owner: "App freezes a lot when I move or scroll and nexi animation and rail stops ... I dont want animation to stop while scrolling").… |
| 25640–25655 | script | 1 | v283 (#134 Month, #136 Insights, #416/#333/#367 switching to Library/Queue, all back after being closed): a Chromium trace of each scroll… |
