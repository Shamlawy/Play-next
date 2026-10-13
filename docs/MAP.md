# Code map of index.html

Made by `node tools/map.mjs` (run it again after a release; line numbers move). 25283 lines, 1883 KB, 104 scripts, 61 styles.

How the file is built: one core style (line 15) and one core script (the biggest block below), then one patch per release, oldest first. A patch adds its own code and often wraps a core function (`const _of = openFull; openFull = function () { ... _of.apply(this, arguments) ... }`), so the **last** wrap runs first. To change what a function does, read every layer listed under "Wrapped functions". New code goes in the last script/style.

## Wrapped functions (most layers first)

| function | defined | layers (line: block) |
|---|---|---|
| `renderHome` | 10731 | 6: 11033 core, 11401 core, 12752 core, 16469 v194, 17420 v204, 23931 v259 |
| `syncPlayed` | 3193 | 6: 11236 core, 19711 v218, 20182 v220, 20239 v220, 20488 v221, 23283 v249 |
| `applyLook` | 3454 | 6: 12361 core, 14034, 16471 v194, 17231 v200, 17419 v204, 20230 v220 |
| `paintTab` | 4258 | 5: 11250 core, 11352 core, 11402 core, 12381 core, 18040 v206 |
| `pnBubSay` | 11898 | 5: 12795 core, 13943 core, 16098 v189, 17624 v204, 20961 v229 |
| `openEdit` | 5003 | 4: 12409 core, 15277 v178, 19929 v219, 22851 v241 |
| `pnBubLines` | 11828 | 4: 12813 core, 17611 v204, 20214 v220, 20427 v221 |
| `renderQueue` | 8300 | 3: 11066 core, 20415 v221, 21467 v232 |
| `pnBubShow` | 11912 | 3: 16099 v189, 17421 v204, 20464 v221 |
| `lookDesign` | 18188 | 3: 19310 v215, 20375 v221, 21374 v232 |
| `dyShow` | 10307 | 3: 20118 v220, 23525 v249, 25120 |
| `renderTaste` | 10195 | 2: 12686 core, 17995 v205 |
| `xFeatures` | 12354 | 2: 12756 core, 13171 core |
| `qCounts` | 7012 | 2: 12992 core, 21367 v232 |
| `queueList` | 7021 | 2: 12993 core, 21368 v232 |
| `pnPowerUp` | 13495 | 2: 13945 core, 24028 v260 |
| `setSub` | 3654 | 2: 17823 v205, 20420 v221 |
| `nxContext` | 12816 | 2: 18707 v212, 20996 v229 |
| `wiAdd` | 18625 | 2: 19386 v216, 19718 v218 |
| `pxRun` | 18848 | 2: 19625 v217, 23276 v249 |
| `nxPlan` | 13057 | 2: 19721 v218, 19892 v219 |
| `gdMarkup` | 16621 | 2: 19881 v219, 20153 v220 |
| `pnBubReact` | 11511 | 2: 20216 v220, 21924 v237 |
| `tasteInsightsPage` | 17850 | 2: 20506 v221, 23952 v259 |
| `closeFull` | 9528 | 2: 20625 v225, 23523 v249 |
| `gdShots` | 21934 | 2: 22095 v238, 22126 v238 |
| `sgdbFillOne` | 4378 | 2: 22130 v238, 22759 v241 |

## Screen-change hooks (onNav / onSettings, since v269)

goTab / goSub / openFull run every `onNav("before"|"after", fn)` hook and openSettings every `onSettings(...)` hook (one wrap each, defined at line 12392, in registration order below). New code registers here; `kit.mjs syntax` fails on a new wrap of those four.

- 12407: `onNav("after", (k, a) => { if (k === "sub") try { uVisit(a[0]); } catch (e) {} });`
- 12408: `onNav("before", (k, a) => { if (k === "game" && (hubAt !== a[0] || !$("#full.on"))) uCount("o", "game"); });`
- 12410: `onSettings("before", (k, a) => { const sc = a[0]; uCount("o", "settings" + (sc && sc !== "app" ? "/" + sc : ""`
- 15276 v178: `onSettings("after", () => { $("#sheet-in").classList.remove("drill", "ssearch"); calmSheet(true); });`
- 15879 v187: `onSettings("after", () => setTimeout(helperCheck, 50));`
- 16438 v194: `onSettings("after", () => shxSetWire());`
- 16849 v196: `onNav("after", () => bugSeen());`
- 16850 v196: `onSettings("after", () => { bugCrumb("screen settings"); bugLayoutSoon(); });`
- 17320 v203: `onNav("before", k => { if (k === "game") openAt = performance.now(); });`
- 18004 v205: `onNav("after", k => { if (k === "tab" && curTab() !== "taste") ixShown.clear(); });`
- 18540 v211: `onSettings("after", () => cbSetWire());`
- 19101 v213: `onSettings("after", () => pxSetWire());`
- 19103 v213: `onNav("after", (k, a) => { if (k !== "game") return;`
- 19387 v216: `onNav("after", (k, a) => { if (k !== "game") return; const g = S.games.find(x => x.id === a[0]);`
- 19885 v219: `onNav("after", (k, a) => { if (k !== "game") return;`
- 20117 v220: `onSettings("before", () => { const el = document.getElementById("sheet"); if (el) el._flkOpen = performance.no`
- 20419 v221: `onNav("after", k => { if (k === "tab" && !lfBox) setTimeout(() => { try { lfApply(); } catch (e) {} }, 60); })`
- 20422 v221: `onNav("before", (k, a) => { if (k !== "game") return; const g = S.games.find(x => x.id === a[0]), d = g ? dust`
- 20619 v225: `onNav("before", k => { if (k !== "game") return;`
- 21960 v237: `onNav("after", (k, a) => { if (k !== "game") return;`
- 23522 v249: `onNav("after", k => { if (k === "tab") lagWatch(() => "Switching to " + lagWhere()); else if (k === "game") la`
- 23524 v249: `onSettings("after", () => lagWatch("Opening Settings"));`
- 24318 v264: `onNav("after", k => { if (k === "tab") { STACK.go = null; stackArm(3500); } });`
- 24441 v265: `onNav("before", k => prbStart(k === "game" ? "open" : "switch", 1000));`
- 24835 v267: `onNav("after", () => requestAnimationFrame(fitRetry));`
- 24878 v267: `onNav("after", k => { if (k !== "sub") picArm(4000); });`
- 24919 v267: `onNav("after", k => { if (k !== "sub") picWait(); });`
- 25025 v271: `onNav("after", (k, a) => {`
- 25196 v277: `onSettings("before", (k, a) => { lvpScope = SCOPE_NAME[a[0]] ? a[0] : "app"; });`
- 25197 v277: `onSettings("after", () => requestAnimationFrame(lvpSync));`

## Blocks

| lines | kind | KB | what |
|---|---|---|---|
| 15–2964 | style | 206 | **CORE** — — |
| 2968–3003 | script | 3 | v252: the card's markup, here so the launch intro can use it before the rest of the page loads. o: k (speed), ar (width / height), word, rv (reveal… |
| 3116–13994 | script | 700 | **CORE** — v169 speed: building a date/number formatter is slow, and the app formats dates for every card on every redraw. Keep one formatter per… |
| 13995–14013 | style | 1 | — |
| 14013–14053 | script | 2 | — |
| 14054–14090 | style | 3 | v147 Top board: ranked rows, big score, share as an image |
| 14090–14181 | script | 7 | v147 Top board in Played, plus a share-as-image button |
| 14182–14208 | style | 2 | v150 badges |
| 14208–14277 | script | 4 | v150 badges: worked out from what you've done, saved with the date you earned them |
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
| 16502–16618 | style | 12 | ===== v195: the Add screen and a game's page in Home's language (both switchable) ===== |
| 16618–16668 | script | 4 | ===== v195: a game's page, New style ===== |
| 16668–16673 | style | 0 | v196: on phones under ~400px the pick card was 352px wide whatever the screen (its 220px min-height through the 16:10 ratio set a minimum width) and… |
| 16673–16937 | script | 21 | ===== v196: problem reports. The app notices when something breaks and files it (Settings → This build) ===== What it watches: script errors, a page… |
| 16937–16955 | style | 2 | — |
| 16955–17052 | script | 8 | ===== v197: Nexi says when he's spotted (and already reported) a problem, and the report sheet lists them ===== |
| 17052–17120 | script | 6 | ===== v198: problem reports catch lag, not just freezes ===== The v196 check only saw 400ms+ freezes; the stutters you feel on a 120Hz screen are… |
| 17120–17224 | script | 8 | ===== v199: Nexi's eyes see more: cut-off text, text on top of text, a blank screen ===== Runs with bugLayout (idle, once a screen settles). Only… |
| 17224–17251 | script | 2 | ===== v200: the two lags the owner felt: Queue scrolling and a game page opening ===== |
| 17251–17263 | script | 1 | ===== v201: every picture in the SteamGridDB and Steam pickers shows its real size (4K / HD / Low) before you pick; SteamGridDB lists sharpest first… |
| 17263–17313 | script | 4 | ===== v202: 4K art really shows in 4K (the swap to the sharp copy was starved by the live countdown, see the liteUp kick), RAWG screenshots as… |
| 17313–17337 | script | 2 | ===== v203: a game page is sharp as soon as it opens. warmGame (touch-down, and each neighbour for the swipe) already downloaded and decoded the… |
| 17337–17411 | style | 7 | ===== v204: each personality has its own face, idle move and a signature move when it talks. Persona rules use :where() so they beat the plain face… |
| 17411–17655 | script | 21 | ===== v204: Nexi's personalities get their own lines (context-aware, ~20 each), faces and moves; more everyday lines for everyone; double tap opens… |
| 17655–17818 | style | 17 | ===== v205: Taste → Insights. Psychology, Patterns and Analytics in one page, ordered the way attention works: who you are (identity) → how good… |
| 17818–18012 | script | 19 | ===== v205: Taste → Insights (see the style block above for the order and why) ===== |
| 18012–18061 | script | 4 | ===== v206: lag fixes from the problem reports ===== render() rebuilt all seven screens on every save, add or delete (#58: a 260ms tap on Edit →… |
| 18061–18073 | style | 1 | v208: found by Nexi's new checks on the first sweep: queue rank numbers were drawn in the border colour (1.9:1, hard to read) and the small icon… |
| 18073–18327 | script | 21 | ===== v208: Nexi's new senses. The owner hit 10–15 problems he never saw: a page that flashed back while closing (#68), a description cut short with… |
| 18327–18344 | script | 1 | ===== v209: #81 (4K art stayed low-res). The fix itself is in liteUp's go() (retries, decode() fallback, LITE_NOFULL) and warmGame; this is the tour… |
| 18344–18599 | script | 19 | ===== v211: cloud backup ===== Everything on this phone is sealed here (AES-GCM, key made from the restore code) and sent to the helper, which only… |
| 18599–18615 | style | 1 | — |
| 18615–18720 | script | 8 | ===== v212: "Where was I?" notes ===== A game keeps short notes about where you stopped (g.wasI = [{t, x}], newest last, 20 kept). The newest shows… |
| 18720–18760 | style | 4 | — |
| 18760–19150 | script | 41 | ===== v213: price alerts (Steam + PlayStation Store) ===== Games you're waiting for (not played, not dropped, not competitive) are priced through… |
| 19150–19206 | style | 4 | — |
| 19206–19218 | style | 1 | ===== v215 fixes ===== |
| 19218–19331 | script | 11 | ===== v215: the frozen game page (#99, #102, #103), Read more (#85, #100), Taste names (#86), Queue rows (#89), 4K pictures by default (#84), and… |
| 19331–19346 | style | 1 | ===== v216: the story so far ===== |
| 19346–19403 | script | 5 | ===== v216: "the story so far". Under your newest 📍 note on a game's page, Nexi writes a short recap of the plot up to where the note says you are… |
| 19403–19503 | script | 9 | ===== v216: Steam's genres and "more like this". Steam's own tags are the genres players vote on ("Souls-like", "Metroidvania", "JRPG",… |
| 19503–19522 | script | 2 | — |
| 19522–19529 | style | 0 | ===== v217: release-date watcher + deal radar ===== |
| 19529–19649 | script | 11 | ===== v217: two new things Nexi does on his own (the owner loves automatic features). 1. Release-date watcher (`rdWatch`): every game you're waiting… |
| 19649–19653 | style | 0 | ===== v218: "Finished it?" (same card as Where was I?) ===== |
| 19653–19735 | script | 7 | ===== v218: "Finished it?" — Nexi notices when a game you're playing is probably done and asks, by himself. Signals (`fnWhy(g)`, first that fits):… |
| 19735–19753 | style | 1 | ===== v219: reviews are in (+ your score, predicted) ===== |
| 19753–20000 | script | 20 | ===== v219: three more things Nexi does by himself, and smarter ones (the owner: "I need more smarter and useful auto things"). 1. Reviews are in… |
| 20000–20031 | style | 3 | ===== v220: away report, finish forecast, time capsule, seasons ===== |
| 20031–20279 | script | 23 | ===== v220: six more things that happen by themselves (the owner: "more auto, unique, rare and out of the box"). 1. While you were away (`aw*`):… |
| 20279–20298 | style | 1 | ===== v221: logo fit, Nexi in the rail, dusty backlog, crystal ball ===== |
| 20298–20535 | script | 22 | ===== v221: the owner's and their friend's reports (#118 prices stuck on "checking…", #114 "fit the logo in the queue automatically", #123 "the rail… |
| 20535–20548 | script | 1 | ===== v222: #153 tour steps that point at things only there once you've scored games. The fixes are in the v184/v215 steps (fall back to the "What… |
| 20548–20560 | script | 1 | ===== v224: prices in one line (pxCard, store logos PX_LOGO) + a tap on seated Nexi hides the rail (railHideNow) ===== |
| 20560–20566 | style | 0 | ===== v225: the cover you tap flies into the game page ===== |
| 20566–20632 | script | 5 | ===== v225: the cover you tap flies into the game page ===== Opening a game from anything that shows its cover (Home, queue, library, rail, deck,… |
| 20632–20641 | script | 1 | ===== v226: platforms as logos on the game page (platChips / platHtml, next to platList) ===== |
| 20641–20695 | script | 3 | ===== v227: prices for games the stores' title search can't find (the owner's GTA 6: the PS Store's old search doesn't list it, though its store… |
| 20695–20704 | script | 1 | ===== v228: platform logos in one pill; price checks never skip a store for good (pxStale/pxGap, batches of 5); helper v13 ===== |
| 20705–21010 | script | 33 | ===== v229: Nexi's brain. The owner wants Nexi "super smart", saying a lot more, all of it different and useful, and using their name much more.… |
| 21010–21026 | style | 1 | v230: Browse and choose, every store |
| 21026–21140 | script | 9 | ===== v230: pictures straight from the stores. Browse and choose (Edit → Look) now shows, next to SteamGridDB and RAWG, the official art of Steam… |
| 21140–21149 | script | 1 | ===== v231 (daily fixer): #180/#181/#154 the tour's Next button answered slowly (it saved and opened the next screen inside the tap; now that waits… |
| 21149–21257 | style | 10 | ===== v232: the same shape for every open queue card and every game page ===== |
| 21257–21490 | script | 22 | ===== v232: the owner: "each queue card is a different size when open" and "the new game page lacks organisation: each game looks different because… |
| 21490–21567 | style | 8 | ===== v236: game suggestions you can see (big cards, a preview with store screenshots), swipe to add, Discover, share to add ===== |
| 21567–21880 | script | 28 | ===== v236: the owner: "adding Baldur's Gate 3 from Similar added the wrong game; the pictures are too small and I need more of a description; maybe… |
| 21880–21895 | style | 1 | ===== v237: screenshots on every game page; Grumble only takes real hits ===== |
| 21895–22014 | script | 11 | ===== v237: the owner: "I deleted Baldur's Gate and Grumble lost HP, which is wrong; and when I add a game, forget to say it's played and then say… |
| 22014–22159 | script | 12 | ===== v238: the owner: "only a few games got screenshots, and the ones that did took time to load. I want them instant and always ready, not fetched… |
| 22159–22263 | style | 12 | ===== v239/v242: Game room — a little 3D room built from your games (owner's isometric "B" room reference: layout and style, our colours). Plain CSS… |
| 22263–22632 | script | 31 | ===== v239: Game room. The owner sent an isometric illustration (a room built around a giant book-letter) and asked for something like it in 3D that… |
| 22632–22663 | style | 3 | v241: hours to beat from HowLongToBeat |
| 22663–22869 | script | 17 | ===== v241: hours to beat from HowLongToBeat (`hl*`). The owner: "most hours I type by hand; the automatic ones are super wrong". Two automatic… |
| 22869–22887 | script | 1 | ===== v243 (#313, the owner: "pressing on the text should expand it, not only Read more"): a tap on a cut-short description (or the story so far)… |
| 22887–22898 | script | 1 | ===== v244 (#319): a tap on a game's description (or the story so far) now closes it again too; see the v243 listener. Tour fixes (#307): the v236… |
| 22898–22907 | script | 1 | ===== v245 (#301): pnShelf skips the Finished shelf while its screen is hidden (it forced a full layout of the screen you were on, 130ms at 4x);… |
| 22907–22917 | script | 1 | ===== v246 (#309): no press animation. The owner: "when I touch anything an animation happened, delete that". Until v268 this script stripped… |
| 22917–22921 | style | 0 | v247: the rail's Add and Settings buttons draw the menu's own icons (they were the text glyphs ＋ and ⚙) |
| 22921–22933 | script | 1 | ===== v247: new menu icons, picked by the owner from two drawn sets: Home (house), Games (game case with a disc), Plan (calendar with a release… |
| 22933–23076 | script | 11 | ===== v248: three things that run by themselves. 1. Release days in your calendar: Settings → Extra features → turn on makes a random id (S.calId),… |
| 23076–23107 | style | 3 | v249: buy advice line under the prices, the drifting card on a game page, the shopping plan and queue clean-up sheets |
| 23107–23469 | script | 34 | ===== v249: three smarter things that run by themselves (the owner picked them from a list: "1 2 4"). 1. Buy advice (`sm*` / `smBuy`): price alerts… |
| 23469–23544 | script | 7 | ===== v249, smoother everywhere (the owner: "all tabs and windows lag"; "a lot of lags that even Nexi doesn't see"). 1. Nexi's own problem checks… |
| 23544–23573 | script | 2 | ===== v250 (the owner, with a screenshot of a "picked for you" card: "not all games have screenshots inside here, fix"): 1. The preview (gpLoad)… |
| 23573–23608 | style | 3 | ===== v251: the rail's live strip (replaces the queue covers) + Nexi sized to his seat ===== |
| 23608–23798 | script | 17 | ===== v251 (the owner, screenshot of the unfolded rail with the queue covers circled: "change them into something useful with live animation that is… |
| 23798–23806 | script | 1 | v253 (#338): the view switcher's marker is measured in the next frame (slidePill), not inside the tap |
| 23806–23820 | script | 1 | after v255 (#354 #355 #358): the lag checks skip frames that overlap a resize (fold/unfold, 0.8s after) or the time the app was hidden (asleep, in… |
| 23820–23831 | script | 1 | v256 (#352): the calling card is drawn in steps (callingCard, yf) |
| 23831–23906 | style | 8 | v259 three new card looks, from the owner's references (layout and style copied, our colours): dzp = Home "Playing now" colour cards, dzd = On deck… |
| 23906–24001 | script | 8 | v259 three new card looks (switch: S.dzX) |
| 24001–24042 | script | 3 | v260 rest: 12s without a touch, every looping animation holds still (no frames to draw, nothing for the GPU to do); the first touch, key, scroll or… |
| 24043–24047 | style | 0 | v261: the view pill's highlight slides on the GPU: it used to animate width (a layout every frame for .28s) |
| 24047–24063 | script | 1 | v261: smooth at the screen's own refresh rate. The highlight takes its new width at once and a transform animation (scaleX from the old width) makes… |
| 24064–24103 | script | 3 | v261: Nexi learned (the owner: "still lags a lot, I want 120 fps"): an animation the phone can't hand to its GPU makes the main thread draw every… |
| 24103–24110 | script | 1 | — |
| 24111–24118 | style | 0 | v263 (120 fps): Nexi's face: a mood used to morph his eyes and mouth (height, top, width, borders: main-thread transitions), and a blink restarted… |
| 24118–24262 | script | 11 | v263 (120 fps): a looping animation on something that isn't drawn (a hidden screen; a list row scrolled far enough that the browser skips drawing… |
| 24262–24326 | script | 5 | v264: Nexi learned (the owner's fifth "still lags", with nothing in his own reports since v263): the lag he couldn't see was the GPU blending a pile… |
| 24326–24332 | style | 1 | v265 smoothness check (prb*, last script): while it tests a suspect, that suspect is switched off for a little while |
| 24332–24456 | script | 9 | v265: the smoothness check (the owner, after five rounds: "it lags everywhere, as if it's too much: a fast scroll up or down on the Queue, sideways… |
| 24456–24470 | script | 1 | v265 (the owner: "if I scroll fast it lags, up or down on the Queue"): touch scrolling no longer waits for the app. Every screen but Home and every… |
| 24470–24474 | script | 0 | the smoothness check counts swiping a game's page to the next game as a sideways moment (it's not a scroll) |
| 24474–24515 | script | 4 | v265: Nexi learned (the blocking touchmove above sat on every screen for months and nothing noticed: it doesn't throw, doesn't look wrong, and the… |
| 24515–24675 | script | 12 | v266: the self-test (step 1 of the plan the owner agreed: "measure first, on the phone, before changing anything"). Settings → This build → "Run the… |
| 24675–24684 | style | 1 | v266 self-test result card (inside the diary sheet's box) |
| 24684–24694 | style | 1 | v271: the progress line draws itself to the step a game just reached. While it runs, the segment's own lines go transparent and .gd-fill (a dim… |
| 24694–24701 | script | 0 | — |
| 24701–24840 | script | 10 | v267 picture fit, layer 2 (layer 1 is sw.js: nothing bigger than the screen). Lists show pictures far smaller than the screen: a Queue row's faded… |
| 24840–24934 | script | 8 | v267: Nexi learned (the phone's self-test: 372 MP of pictures on the Queue for 14 MP on screen, one picture 70 MP; none of his checks looked at… |
| 24934–24943 | script | 1 | v269: the tidy-up (the owner: "organise the code, merge it, cut what isn't needed"). No new feature: onNav (screen-change hooks in one place), dead… |
| 24943–24987 | script | 3 | v270: buzzes on the iPhone too. Safari has no navigator.vibrate, but iOS 18+ gives a light tap each time an <input type="checkbox" switch> flips,… |
| 24987–25044 | script | 4 | v271: two moments that used to jump now move (the owner: "smoothness is rule number one, but I do like animations"). Picked with the… |
| 25044–25053 | script | 1 | #432: Settings (#sheet) let a sideways swipe through to the game page behind it; the other sheets already stop it (noHub) |
| 25053–25066 | script | 1 | v273 (the owner: "return the background to a solid gradient color, I hate how it looks"): the live background (v257 lights, specks and drifting art;… |
| 25066–25072 | style | 0 | v274 (the owner, #447): one Nexi per screen. Unfolded, Home's own Nexi (the face beside your greeting) and the rail's seated Nexi showed at once:… |
| 25072–25085 | script | 1 | — |
| 25085–25093 | style | 0 | #201 (the friend, iPhone/iPad: "Restore a code is not working"): while a keyboard (or a zoom) hides the bottom of the screen, the open sheet sits on… |
| 25093–25148 | script | 4 | #201: Safari's keyboard doesn't shrink the page, it slides over it. Sheets (#diary) sit at the bottom of the screen, so a short one (Restore from a… |
| 25148–25155 | script | 1 | — |
| 25155–25168 | script | 1 | v276 (the owner: "black with a little blue gradient, mostly black, with the option to edit how much gradient and the background colour"): the… |
| 25168–25182 | style | 1 | v277 (#171, the friend: "a live preview when I'm editing the looks of all features, the same look everywhere, smart, off by default"): with Settings… |
| 25182–25214 | script | 3 | v277 (#171): when the sheet docks for Live preview. A screen's Customise always does; Settings itself only while one of the look groups is the one… |
| 25214–25239 | script | 2 | Nexi learned (#174, the friend, iPad v228: "Nexi disappeared while the rail was open"): with the rail open he sits at its foot (html.nxseat). A… |
| 25239–25261 | style | 2 | v277 (#130, the owner: "Radar needs a makeover, looks kinda ugly and doesn't match our theme"): Radar in the Clean style. Its words are sentence… |
| 25261–25283 | script | 2 | Nexi learned (#130, the owner: "Radar looks kinda ugly and doesn't match our theme"): Radar kept the old Arcade look (slanted, all-caps heading)… |
