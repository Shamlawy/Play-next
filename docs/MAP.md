# Code map of index.html

Made by `node tools/map.mjs` (run it again after a release; line numbers move). 25298 lines, 1882 KB, 97 scripts, 58 styles.

How the file is built: one core style (line 15) and one core script (the biggest block below), then one patch per release, oldest first. A patch adds its own code and often wraps a core function (`const _of = openFull; openFull = function () { ... _of.apply(this, arguments) ... }`), so the **last** wrap runs first. To change what a function does, read every layer listed under "Wrapped functions". New code goes in the last script/style.

## Wrapped functions (most layers first)

| function | defined | layers (line: block) |
|---|---|---|
| `save` | 4129 | 8: 12842 core, 13489 core, 13545 core, 13906 core, 14476 v150, 18654 v211, 22115 v237, 23267 v248 |
| `bugNice` | 17160 | 7: 19516 v215, 21569 v232, 21669 v232, 22069 v236, 22198 v237, 22338 v238, 22773 v239 |
| `renderHome` | 10909 | 6: 11211 core, 11579 core, 12954 core, 16682 v194, 17605 v204, 24149 v259 |
| `syncPlayed` | 3234 | 6: 11414 core, 19902 v218, 20369 v220, 20426 v220, 20677 v221, 23501 v249 |
| `applyLook` | 3493 | 6: 12563 core, 14242, 16684 v194, 17412 v200, 17604 v204, 20417 v220 |
| `bugLayout` | 17005 | 6: 17398 v199, 17416 v200, 17478 v202, 17818 v204, 18435 v208, 18488 v208 |
| `paintTab` | 4381 | 5: 11428 core, 11530 core, 11580 core, 12583 core, 18225 v206 |
| `pnBubSay` | 12100 | 5: 12997 core, 14150 core, 16311 v189, 17809 v204, 21150 v229 |
| `openEdit` | 5127 | 4: 12611 core, 15490 v178, 20120 v219, 23066 v241 |
| `pnBubLines` | 12030 | 4: 13015 core, 17796 v204, 20401 v220, 20616 v221 |
| `renderQueue` | 8463 | 3: 11244 core, 20604 v221, 21666 v232 |
| `pnBubShow` | 12115 | 3: 16312 v189, 17606 v204, 20653 v221 |
| `lookDesign` | 18373 | 3: 19496 v215, 20564 v221, 21563 v232 |
| `renderTaste` | 10355 | 2: 12888 core, 18180 v205 |
| `xFeatures` | 12556 | 2: 12958 core, 13373 core |
| `qCounts` | 7175 | 2: 13194 core, 21556 v232 |
| `queueList` | 7184 | 2: 13195 core, 21557 v232 |
| `pnPowerUp` | 13702 | 2: 14153 core, 24256 v260 |
| `setSub` | 3783 | 2: 18008 v205, 20609 v221 |
| `nxContext` | 13018 | 2: 18893 v212, 21185 v229 |
| `wiAdd` | 18811 | 2: 19577 v216, 19909 v218 |
| `pxRun` | 19034 | 2: 19816 v217, 23491 v249 |
| `nxPlan` | 13259 | 2: 19912 v218, 20083 v219 |
| `gdMarkup` | 16835 | 2: 20072 v219, 20340 v220 |
| `dyShow` | 10467 | 2: 20305 v220, 23743 v249 |
| `pnBubReact` | 11703 | 2: 20403 v220, 22130 v237 |
| `tasteInsightsPage` | 18035 | 2: 20695 v221, 24170 v259 |
| `closeFull` | 9688 | 2: 20814 v225, 23741 v249 |
| `gdShots` | 22140 | 2: 22304 v238, 22335 v238 |
| `sgdbFillOne` | 4501 | 2: 22342 v238, 22974 v241 |

## Screen-change hooks (onNav / onSettings, since v269)

goTab / goSub / openFull run every `onNav("before"|"after", fn)` hook and openSettings every `onSettings(...)` hook (one wrap each, defined at line 12594, in registration order below). New code registers here; `kit.mjs syntax` fails on a new wrap of those four.

- 12609: `onNav("after", (k, a) => { if (k === "sub") try { uVisit(a[0]); } catch (e) {} });`
- 12610: `onNav("before", (k, a) => { if (k === "game" && (hubAt !== a[0] || !$("#full.on"))) uCount("o", "game"); });`
- 12612: `onSettings("before", (k, a) => { const sc = a[0]; uCount("o", "settings" + (sc && sc !== "app" ? "/" + sc : ""`
- 15489 v178: `onSettings("after", () => { $("#sheet-in").classList.remove("drill", "ssearch"); calmSheet(true); });`
- 16092 v187: `onSettings("after", () => setTimeout(helperCheck, 50));`
- 16651 v194: `onSettings("after", () => shxSetWire());`
- 17044 v196: `onNav("after", () => bugSeen());`
- 17045 v196: `onSettings("after", () => { bugCrumb("screen settings"); bugLayoutSoon(); });`
- 17505 v203: `onNav("before", k => { if (k === "game") openAt = performance.now(); });`
- 18189 v205: `onNav("after", k => { if (k === "tab" && curTab() !== "taste") ixShown.clear(); });`
- 18726 v211: `onSettings("after", () => cbSetWire());`
- 19287 v213: `onSettings("after", () => pxSetWire());`
- 19289 v213: `onNav("after", (k, a) => { if (k !== "game") return;`
- 19578 v216: `onNav("after", (k, a) => { if (k !== "game") return; const g = S.games.find(x => x.id === a[0]);`
- 20076 v219: `onNav("after", (k, a) => { if (k !== "game") return;`
- 20304 v220: `onSettings("before", () => { const el = document.getElementById("sheet"); if (el) el._flkOpen = performance.no`
- 20608 v221: `onNav("after", k => { if (k === "tab" && !lfBox) setTimeout(() => { try { lfApply(); } catch (e) {} }, 60); })`
- 20611 v221: `onNav("before", (k, a) => { if (k !== "game") return; const g = S.games.find(x => x.id === a[0]), d = g ? dust`
- 20808 v225: `onNav("before", k => { if (k !== "game") return;`
- 22166 v237: `onNav("after", (k, a) => { if (k !== "game") return;`
- 23740 v249: `onNav("after", k => { if (k === "tab") lagWatch(() => "Switching to " + lagWhere()); else if (k === "game") la`
- 23742 v249: `onSettings("after", () => lagWatch("Opening Settings"));`
- 24546 v264: `onNav("after", k => { if (k === "tab") { STACK.go = null; stackArm(3500); } });`
- 24671 v265: `onNav("before", k => prbStart(k === "game" ? "open" : "switch", 1000));`
- 25065 v267: `onNav("after", () => requestAnimationFrame(fitRetry));`
- 25108 v267: `onNav("after", k => { if (k !== "sub") picArm(4000); });`
- 25149 v267: `onNav("after", k => { if (k !== "sub") picWait(); });`
- 25255 v271: `onNav("after", (k, a) => {`

## Blocks

| lines | kind | KB | what |
|---|---|---|---|
| 15–2996 | style | 211 | **CORE** — — |
| 3001–3036 | script | 3 | v252: the card's markup, here so the launch intro can use it before the rest of the page loads. o: k (speed), ar (width / height), word, rv (reveal… |
| 3157–14202 | script | 709 | **CORE** — v169 speed: building a date/number formatter is slow, and the app formats dates for every card on every redraw. Keep one formatter per… |
| 14203–14221 | style | 1 | — |
| 14221–14261 | script | 2 | — |
| 14262–14298 | style | 3 | v147 Top board: ranked rows, big score, share as an image |
| 14298–14389 | script | 7 | v147 Top board in Played, plus a share-as-image button |
| 14390–14416 | style | 2 | v150 badges |
| 14416–14483 | script | 4 | v150 badges: worked out from what you've done, saved with the date you earned them |
| 14483–14489 | script | 0 | v151 art strength slider: live while dragging, saved when let go |
| 14489–14490 | script | 0 | — |
| 14490–14507 | style | 1 | v155 light board motion: only fade + slide, which the phone's graphics chip does for free |
| 14507–14527 | script | 2 | — |
| 14527–14553 | style | 2 | v156 scroll-driven motion: the browser ties these to your finger on its own graphics thread, so they stay at full refresh rate however fast you… |
| 14553–14563 | style | 1 | v157 the real drag: a full-screen blurred picture sat behind every screen, slowly zooming forever, so the phone re-blurred the whole screen on every… |
| 14563–14570 | script | 0 | — |
| 14571–14590 | style | 1 | v163: Why you'll love it — one sheet, fades and slides in, nothing loops |
| 14590–14660 | script | 4 | v163: "Why you'll love it" — sends your ratings and this game to your Cloudflare helper, which asks free Workers AI why it fits your taste (or… |
| 14661–14692 | script | 2 | v166: reset scores — one game from its page, or everything from Settings → Your data. Games, art, hours and diary stay; played games stay played,… |
| 14693–14703 | style | 1 | v167 Nexi's glow sometimes showed as a square box. Three stacked fixes (blur, clip-path, mask) each add a separate layer the phone can drop… |
| 14704–14744 | style | 3 | v169 speed: these animations changed colours/sizes/positions, which makes the phone redraw on every frame. Same looks, now moved or faded instead,… |
| 14745–14756 | style | 1 | v171 swipe a queue card left to delete the game. Only the card's contents slide (translate), the red button waits underneath. |
| 14756–14817 | script | 3 | — |
| 14818–14833 | style | 1 | v172 what's-new tour: a small card with Nexi's words, while Nexi points at the real button |
| 14833–15026 | script | 17 | v172: after every update Nexi shows what's new — where it is, and a nudge to try it. HOW TO ADD ONE: push an entry to WHATS_NEW with the version it… |
| 15026–15268 | style | 22 | ===== v174 Home dashboard (layout from the owner's reference, our black + blue colours) ===== |
| 15268–15354 | style | 8 | ===== v177 play sessions ===== |
| 15354–15494 | script | 10 | ===== v178 calmer settings and edit: search, most-used shortcuts, one section at a time, fewer knobs up front ===== |
| 15494–15540 | style | 4 | ===== v178 calmer settings ===== |
| 15540–15553 | style | 1 | ===== v180 finish month picker ===== |
| 15553–15561 | style | 1 | ===== v181 theme picker ===== |
| 15561–15708 | script | 13 | ===== v181 Game Shuffler: swipe through your games one at a time (right = tempted, left = not now). Styled after the owner's chess reference: a big… |
| 15708–15789 | style | 8 | ===== v181 Game Shuffler (chess reference layout, our black + theme accent) ===== |
| 15789–15815 | style | 2 | ===== v182 lists ===== |
| 15815–15852 | style | 3 | ===== v183 extras off by default, quick duel / score sheets, usage table, quick add ===== |
| 15852–15875 | script | 2 | ===== v183 what's new + settings extras ===== |
| 15875–15920 | script | 3 | ---- badges that keep going as you duel ---- |
| 15920–15997 | style | 7 | ===== v184 ranking: tiers, confidence dots, On deck, Nexi on Home, insights ===== |
| 15997–16017 | script | 2 | ===== v185 what's new ===== |
| 16017–16064 | style | 4 | ===== v185 Nexi chat, personalities, similar games ===== |
| 16064–16072 | script | 0 | ===== v186 what's new ===== |
| 16072–16099 | script | 3 | ===== v187: similar games picked by the AI and checked on RAWG; helper status in Settings → Nexi ===== |
| 16099–16217 | style | 8 | ===== v188 "Clean" style: the rest of the app speaks Home's language ===== One layer, scoped to html.ui2 (Settings → Look of the app → Style →… |
| 16217–16228 | script | 1 | ===== v188 what's new: the Clean style ===== |
| 16228–16248 | style | 2 | ===== v189 tour: Nexi drops out of the notch / camera hole, then rides on the tour card ===== |
| 16248–16349 | script | 8 | ===== v189 tour: Nexi comes out of the notch and stays with the tour card ===== |
| 16349–16505 | style | 12 | ===== v194 "Sicker everywhere": Home's look on every screen (Clean style only) ===== Each screen's header is a card like Home's: a big title, live… |
| 16505–16715 | script | 16 | ===== v194: every screen's header is a card like Home's ===== |
| 16715–16832 | style | 12 | ===== v195: the Add screen and a game's page in Home's language (both switchable) ===== |
| 16832–16882 | script | 4 | ===== v195: a game's page, New style ===== |
| 16882–16887 | style | 0 | v196: on phones under ~400px the pick card was 352px wide whatever the screen (its 220px min-height through the 16:10 ratio set a minimum width) and… |
| 16887–17132 | script | 19 | ===== v196: problem reports. The app notices when something breaks and files it (Settings → This build) ===== What it watches: script errors, a page… |
| 17132–17150 | style | 2 | — |
| 17150–17233 | script | 6 | ===== v197: Nexi says when he's spotted (and already reported) a problem, and the report sheet lists them ===== |
| 17233–17301 | script | 6 | ===== v198: problem reports catch lag, not just freezes ===== The v196 check only saw 400ms+ freezes; the stutters you feel on a 120Hz screen are… |
| 17301–17405 | script | 8 | ===== v199: Nexi's eyes see more: cut-off text, text on top of text, a blank screen ===== Runs with bugLayout (idle, once a screen settles). Only… |
| 17405–17435 | script | 3 | ===== v200: the two lags the owner felt: Queue scrolling and a game page opening ===== |
| 17435–17447 | script | 1 | ===== v201: every picture in the SteamGridDB and Steam pickers shows its real size (4K / HD / Low) before you pick; SteamGridDB lists sharpest first… |
| 17447–17498 | script | 4 | ===== v202: 4K art really shows in 4K (the swap to the sharp copy was starved by the live countdown, see the liteUp kick), RAWG screenshots as… |
| 17498–17522 | script | 2 | ===== v203: a game page is sharp as soon as it opens. warmGame (touch-down, and each neighbour for the swipe) already downloaded and decoded the… |
| 17522–17596 | style | 7 | ===== v204: each personality has its own face, idle move and a signature move when it talks. Persona rules use :where() so they beat the plain face… |
| 17596–17840 | script | 21 | ===== v204: Nexi's personalities get their own lines (context-aware, ~20 each), faces and moves; more everyday lines for everyone; double tap opens… |
| 17840–18003 | style | 17 | ===== v205: Taste → Insights. Psychology, Patterns and Analytics in one page, ordered the way attention works: who you are (identity) → how good… |
| 18003–18197 | script | 19 | ===== v205: Taste → Insights (see the style block above for the order and why) ===== |
| 18197–18246 | script | 4 | ===== v206: lag fixes from the problem reports ===== render() rebuilt all seven screens on every save, add or delete (#58: a 260ms tap on Edit →… |
| 18246–18258 | style | 1 | v208: found by Nexi's new checks on the first sweep: queue rank numbers were drawn in the border colour (1.9:1, hard to read) and the small icon… |
| 18258–18513 | script | 21 | ===== v208: Nexi's new senses. The owner hit 10–15 problems he never saw: a page that flashed back while closing (#68), a description cut short with… |
| 18513–18530 | script | 1 | ===== v209: #81 (4K art stayed low-res). The fix itself is in liteUp's go() (retries, decode() fallback, LITE_NOFULL) and warmGame; this is the tour… |
| 18530–18785 | script | 19 | ===== v211: cloud backup ===== Everything on this phone is sealed here (AES-GCM, key made from the restore code) and sent to the helper, which only… |
| 18785–18801 | style | 1 | — |
| 18801–18906 | script | 8 | ===== v212: "Where was I?" notes ===== A game keeps short notes about where you stopped (g.wasI = [{t, x}], newest last, 20 kept). The newest shows… |
| 18906–18946 | style | 4 | — |
| 18946–19336 | script | 41 | ===== v213: price alerts (Steam + PlayStation Store) ===== Games you're waiting for (not played, not dropped, not competitive) are priced through… |
| 19336–19392 | style | 4 | — |
| 19392–19404 | style | 1 | ===== v215 fixes ===== |
| 19404–19522 | script | 12 | ===== v215: the frozen game page (#99, #102, #103), Read more (#85, #100), Taste names (#86), Queue rows (#89), 4K pictures by default (#84), and… |
| 19522–19537 | style | 1 | ===== v216: the story so far ===== |
| 19537–19594 | script | 5 | ===== v216: "the story so far". Under your newest 📍 note on a game's page, Nexi writes a short recap of the plot up to where the note says you are… |
| 19594–19694 | script | 9 | ===== v216: Steam's genres and "more like this". Steam's own tags are the genres players vote on ("Souls-like", "Metroidvania", "JRPG",… |
| 19694–19713 | script | 2 | — |
| 19713–19720 | style | 0 | ===== v217: release-date watcher + deal radar ===== |
| 19720–19840 | script | 11 | ===== v217: two new things Nexi does on his own (the owner loves automatic features). 1. Release-date watcher (`rdWatch`): every game you're waiting… |
| 19840–19844 | style | 0 | ===== v218: "Finished it?" (same card as Where was I?) ===== |
| 19844–19926 | script | 7 | ===== v218: "Finished it?" — Nexi notices when a game you're playing is probably done and asks, by himself. Signals (`fnWhy(g)`, first that fits):… |
| 19926–19944 | style | 1 | ===== v219: reviews are in (+ your score, predicted) ===== |
| 19944–20187 | script | 20 | ===== v219: three more things Nexi does by himself, and smarter ones (the owner: "I need more smarter and useful auto things"). 1. Reviews are in… |
| 20187–20218 | style | 3 | ===== v220: away report, finish forecast, time capsule, seasons ===== |
| 20218–20466 | script | 23 | ===== v220: six more things that happen by themselves (the owner: "more auto, unique, rare and out of the box"). 1. While you were away (`aw*`):… |
| 20466–20487 | style | 1 | ===== v221: logo fit, Nexi in the rail, dusty backlog, crystal ball ===== |
| 20487–20724 | script | 22 | ===== v221: the owner's and their friend's reports (#118 prices stuck on "checking…", #114 "fit the logo in the queue automatically", #123 "the rail… |
| 20724–20737 | script | 1 | ===== v222: #153 tour steps that point at things only there once you've scored games. The fixes are in the v184/v215 steps (fall back to the "What… |
| 20737–20749 | script | 1 | ===== v224: prices in one line (pxCard, store logos PX_LOGO) + a tap on seated Nexi hides the rail (railHideNow) ===== |
| 20749–20755 | style | 0 | ===== v225: the cover you tap flies into the game page ===== |
| 20755–20821 | script | 5 | ===== v225: the cover you tap flies into the game page ===== Opening a game from anything that shows its cover (Home, queue, library, rail, deck,… |
| 20821–20830 | script | 1 | ===== v226: platforms as logos on the game page (platChips / platHtml, next to platList) ===== |
| 20830–20884 | script | 3 | ===== v227: prices for games the stores' title search can't find (the owner's GTA 6: the PS Store's old search doesn't list it, though its store… |
| 20884–20893 | script | 1 | ===== v228: platform logos in one pill; price checks never skip a store for good (pxStale/pxGap, batches of 5); helper v13 ===== |
| 20894–21199 | script | 33 | ===== v229: Nexi's brain. The owner wants Nexi "super smart", saying a lot more, all of it different and useful, and using their name much more.… |
| 21199–21215 | style | 1 | v230: Browse and choose, every store |
| 21215–21329 | script | 9 | ===== v230: pictures straight from the stores. Browse and choose (Edit → Look) now shows, next to SteamGridDB and RAWG, the official art of Steam… |
| 21329–21338 | script | 1 | ===== v231 (daily fixer): #180/#181/#154 the tour's Next button answered slowly (it saved and opened the next screen inside the tap; now that waits… |
| 21338–21446 | style | 10 | ===== v232: the same shape for every open queue card and every game page ===== |
| 21446–21692 | script | 23 | ===== v232: the owner: "each queue card is a different size when open" and "the new game page lacks organisation: each game looks different because… |
| 21692–21769 | style | 8 | ===== v236: game suggestions you can see (big cards, a preview with store screenshots), swipe to add, Discover, share to add ===== |
| 21769–22085 | script | 28 | ===== v236: the owner: "adding Baldur's Gate 3 from Similar added the wrong game; the pictures are too small and I need more of a description; maybe… |
| 22085–22100 | style | 1 | ===== v237: screenshots on every game page; Grumble only takes real hits ===== |
| 22100–22223 | script | 11 | ===== v237: the owner: "I deleted Baldur's Gate and Grumble lost HP, which is wrong; and when I add a game, forget to say it's played and then say… |
| 22223–22371 | script | 13 | ===== v238: the owner: "only a few games got screenshots, and the ones that did took time to load. I want them instant and always ready, not fetched… |
| 22371–22475 | style | 12 | ===== v239/v242: Game room — a little 3D room built from your games (owner's isometric "B" room reference: layout and style, our colours). Plain CSS… |
| 22475–22847 | script | 31 | ===== v239: Game room. The owner sent an isometric illustration (a room built around a giant book-letter) and asked for something like it in 3D that… |
| 22847–22878 | style | 3 | v241: hours to beat from HowLongToBeat |
| 22878–23084 | script | 17 | ===== v241: hours to beat from HowLongToBeat (`hl*`). The owner: "most hours I type by hand; the automatic ones are super wrong". Two automatic… |
| 23084–23102 | script | 1 | ===== v243 (#313, the owner: "pressing on the text should expand it, not only Read more"): a tap on a cut-short description (or the story so far)… |
| 23102–23113 | script | 1 | ===== v244 (#319): a tap on a game's description (or the story so far) now closes it again too; see the v243 listener. Tour fixes (#307): the v236… |
| 23113–23122 | script | 1 | ===== v245 (#301): pnShelf skips the Finished shelf while its screen is hidden (it forced a full layout of the screen you were on, 130ms at 4x);… |
| 23122–23132 | script | 1 | ===== v246 (#309): no press animation. The owner: "when I touch anything an animation happened, delete that". Until v268 this script stripped… |
| 23132–23136 | style | 0 | v247: the rail's Add and Settings buttons draw the menu's own icons (they were the text glyphs ＋ and ⚙) |
| 23136–23148 | script | 1 | ===== v247: new menu icons, picked by the owner from two drawn sets: Home (house), Games (game case with a disc), Plan (calendar with a release… |
| 23148–23291 | script | 11 | ===== v248: three things that run by themselves. 1. Release days in your calendar: Settings → Extra features → turn on makes a random id (S.calId),… |
| 23291–23322 | style | 3 | v249: buy advice line under the prices, the drifting card on a game page, the shopping plan and queue clean-up sheets |
| 23322–23687 | script | 34 | ===== v249: three smarter things that run by themselves (the owner picked them from a list: "1 2 4"). 1. Buy advice (`sm*` / `smBuy`): price alerts… |
| 23687–23762 | script | 7 | ===== v249, smoother everywhere (the owner: "all tabs and windows lag"; "a lot of lags that even Nexi doesn't see"). 1. Nexi's own problem checks… |
| 23762–23791 | script | 2 | ===== v250 (the owner, with a screenshot of a "picked for you" card: "not all games have screenshots inside here, fix"): 1. The preview (gpLoad)… |
| 23791–23826 | style | 3 | ===== v251: the rail's live strip (replaces the queue covers) + Nexi sized to his seat ===== |
| 23826–24016 | script | 17 | ===== v251 (the owner, screenshot of the unfolded rail with the queue covers circled: "change them into something useful with live animation that is… |
| 24016–24024 | script | 1 | v253 (#338): the view switcher's marker is measured in the next frame (slidePill), not inside the tap |
| 24024–24038 | script | 1 | after v255 (#354 #355 #358): the lag checks skip frames that overlap a resize (fold/unfold, 0.8s after) or the time the app was hidden (asleep, in… |
| 24038–24049 | script | 1 | v256 (#352): the calling card is drawn in steps (callingCard, yf) |
| 24049–24124 | style | 8 | v259 three new card looks, from the owner's references (layout and style copied, our colours): dzp = Home "Playing now" colour cards, dzd = On deck… |
| 24124–24219 | script | 8 | v259 three new card looks (switch: S.dzX) |
| 24219–24223 | style | 0 | v260 smoothness + battery: fewer full-screen layers stacked on every frame |
| 24223–24270 | script | 3 | v260: #bgtint only while an art backdrop is up |
| 24271–24275 | style | 0 | v261: the view pill's highlight slides on the GPU: it used to animate width (a layout every frame for .28s) |
| 24275–24291 | script | 1 | v261: smooth at the screen's own refresh rate. The highlight takes its new width at once and a transform animation (scaleX from the old width) makes… |
| 24292–24331 | script | 3 | v261: Nexi learned (the owner: "still lags a lot, I want 120 fps"): an animation the phone can't hand to its GPU makes the main thread draw every… |
| 24331–24338 | script | 1 | — |
| 24339–24346 | style | 0 | v263 (120 fps): Nexi's face: a mood used to morph his eyes and mouth (height, top, width, borders: main-thread transitions), and a blink restarted… |
| 24346–24490 | script | 11 | v263 (120 fps): a looping animation on something that isn't drawn (a hidden screen; a list row scrolled far enough that the browser skips drawing… |
| 24490–24554 | script | 5 | v264: Nexi learned (the owner's fifth "still lags", with nothing in his own reports since v263): the lag he couldn't see was the GPU blending a pile… |
| 24554–24562 | style | 1 | v265 smoothness check (prb*, last script): while it tests a suspect, that suspect is switched off for a little while |
| 24562–24686 | script | 9 | v265: the smoothness check (the owner, after five rounds: "it lags everywhere, as if it's too much: a fast scroll up or down on the Queue, sideways… |
| 24686–24700 | script | 1 | v265 (the owner: "if I scroll fast it lags, up or down on the Queue"): touch scrolling no longer waits for the app. Every screen but Home and every… |
| 24700–24704 | script | 0 | the smoothness check counts swiping a game's page to the next game as a sideways moment (it's not a scroll) |
| 24704–24745 | script | 4 | v265: Nexi learned (the blocking touchmove above sat on every screen for months and nothing noticed: it doesn't throw, doesn't look wrong, and the… |
| 24745–24905 | script | 12 | v266: the self-test (step 1 of the plan the owner agreed: "measure first, on the phone, before changing anything"). Settings → This build → "Run the… |
| 24905–24914 | style | 1 | v266 self-test result card (inside the diary sheet's box) |
| 24914–24924 | style | 1 | v271: the progress line draws itself to the step a game just reached. While it runs, the segment's own lines go transparent and .gd-fill (a dim… |
| 24924–24931 | script | 0 | — |
| 24931–25070 | script | 10 | v267 picture fit, layer 2 (layer 1 is sw.js: nothing bigger than the screen). Lists show pictures far smaller than the screen: a Queue row's faded… |
| 25070–25164 | script | 8 | v267: Nexi learned (the phone's self-test: 372 MP of pictures on the Queue for 14 MP on screen, one picture 70 MP; none of his checks looked at… |
| 25164–25173 | script | 1 | v269: the tidy-up (the owner: "organise the code, merge it, cut what isn't needed"). No new feature: onNav (screen-change hooks in one place), dead… |
| 25173–25217 | script | 3 | v270: buzzes on the iPhone too. Safari has no navigator.vibrate, but iOS 18+ gives a light tap each time an <input type="checkbox" switch> flips,… |
| 25217–25274 | script | 4 | v271: two moments that used to jump now move (the owner: "smoothness is rule number one, but I do like animations"). Picked with the… |
| 25274–25283 | script | 1 | #432: Settings (#sheet) let a sideways swipe through to the game page behind it; the other sheets already stop it (noHub) |
| 25283–25298 | script | 1 | v273 (the owner: "return the background to a solid gradient color, I hate how it looks"): the live background (v257 lights, specks and drifting art;… |
