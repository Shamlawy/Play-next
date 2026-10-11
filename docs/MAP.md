# Code map of index.html

Made by `node tools/map.mjs` (run it again after a release; line numbers move). 25657 lines, 1906 KB, 95 scripts, 59 styles.

How the file is built: one core style (line 15) and one core script (the biggest block below), then one patch per release, oldest first. A patch adds its own code and often wraps a core function (`const _of = openFull; openFull = function () { ... _of.apply(this, arguments) ... }`), so the **last** wrap runs first. To change what a function does, read every layer listed under "Wrapped functions". New code goes in the last script/style.

## Wrapped functions (most layers first)

| function | defined | layers (line: block) |
|---|---|---|
| `openFull` | 9502 | 9: 12628 core, 17065 v196, 17522 v203, 19304 v213, 19590 v216, 20088 v219, 20623 v221, 20821 v225, 22180 v237 |
| `openSettings` | 5935 | 9: 12630 core, 15507 v178, 16111 v187, 16670 v194, 17066 v196, 18741 v211, 19302 v213, 20316 v220, 23776 v249 |
| `save` | 4129 | 8: 12860 core, 13507 core, 13563 core, 13924 core, 14494 v150, 18669 v211, 22129 v237, 23281 v248 |
| `renderHome` | 10952 | 7: 11254 core, 11622 core, 12972 core, 16701 v194, 17622 v204, 24311 v257, 24448 v259 |
| `bugNice` | 17181 | 7: 19528 v215, 21583 v232, 21683 v232, 22083 v236, 22212 v237, 22352 v238, 22787 v239 |
| `syncPlayed` | 3234 | 6: 11457 core, 19914 v218, 20381 v220, 20438 v220, 20690 v221, 23515 v249 |
| `applyLook` | 3493 | 6: 12606 core, 14260, 16703 v194, 17429 v200, 17621 v204, 20429 v220 |
| `bugLayout` | 17024 | 6: 17415 v199, 17433 v200, 17495 v202, 17835 v204, 18450 v208, 18503 v208 |
| `paintTab` | 4381 | 5: 11471 core, 11573 core, 11623 core, 12626 core, 18242 v206 |
| `pnBubSay` | 12143 | 5: 13015 core, 14168 core, 16330 v189, 17826 v204, 21164 v229 |
| `openEdit` | 5128 | 4: 12629 core, 15509 v178, 20132 v219, 23080 v241 |
| `pnBubLines` | 12073 | 4: 13033 core, 17813 v204, 20413 v220, 20629 v221 |
| `renderQueue` | 8506 | 3: 11287 core, 20616 v221, 21680 v232 |
| `pnBubShow` | 12158 | 3: 16331 v189, 17623 v204, 20666 v221 |
| `goTab` | 3814 | 3: 17063 v196, 18206 v205, 20620 v221 |
| `lookDesign` | 18388 | 3: 19508 v215, 20576 v221, 21577 v232 |
| `goSub` | 3802 | 2: 12627 core, 17064 v196 |
| `renderTaste` | 10398 | 2: 12906 core, 18197 v205 |
| `xFeatures` | 12599 | 2: 12976 core, 13391 core |
| `qCounts` | 7218 | 2: 13212 core, 21570 v232 |
| `queueList` | 7227 | 2: 13213 core, 21571 v232 |
| `pnPowerUp` | 13720 | 2: 14171 core, 24556 v260 |
| `setSub` | 3783 | 2: 18025 v205, 20621 v221 |
| `nxContext` | 13036 | 2: 18908 v212, 21199 v229 |
| `wiAdd` | 18826 | 2: 19589 v216, 19921 v218 |
| `pxRun` | 19049 | 2: 19828 v217, 23505 v249 |
| `nxPlan` | 13277 | 2: 19924 v218, 20095 v219 |
| `gdMarkup` | 16854 | 2: 20084 v219, 20352 v220 |
| `dyShow` | 10510 | 2: 20317 v220, 23777 v249 |
| `pnBubReact` | 11746 | 2: 20415 v220, 22144 v237 |
| `tasteInsightsPage` | 18052 | 2: 20708 v221, 24469 v259 |
| `closeFull` | 9731 | 2: 20828 v225, 23775 v249 |
| `gdShots` | 22154 | 2: 22318 v238, 22349 v238 |
| `sgdbFillOne` | 4501 | 2: 22356 v238, 22988 v241 |
| `applyBg` | 3550 | 2: 24313 v257, 24525 v260 |

## Screen-change hooks (onNav, since v269)

goTab / goSub / openFull also run every `onNav("before"|"after", fn)` hook (one wrap, defined at line 23709). New code that wants to know about a screen change registers here instead of wrapping those three.

- 23774 v249: `onNav("after", k => { if (k === "tab") lagWatch(() => "Switching to " + lagWhere()); else if (k === "game") la`
- 24312 v257: `onNav("after", k => { if (k === "tab") hlvKick(); });`
- 25026 v264: `onNav("after", k => { if (k === "tab") { STACK.go = null; stackArm(3500); } });`
- 25154 v265: `onNav("before", k => prbStart(k === "game" ? "open" : "switch", 1000));`
- 25193 v265: `onNav("before", () => hold(900));`
- 25549 v267: `onNav("after", () => requestAnimationFrame(fitRetry));`
- 25592 v267: `onNav("after", k => { if (k !== "sub") picArm(4000); });`
- 25633 v267: `onNav("after", k => { if (k !== "sub") picWait(); });`

## Blocks

| lines | kind | KB | what |
|---|---|---|---|
| 15–2996 | style | 211 | **CORE** — — |
| 3001–3036 | script | 3 | v252: the card's markup, here so the launch intro can use it before the rest of the page loads. o: k (speed), ar (width / height), word, rv (reveal… |
| 3157–14220 | script | 709 | **CORE** — v169 speed: building a date/number formatter is slow, and the app formats dates for every card on every redraw. Keep one formatter per… |
| 14221–14239 | style | 1 | — |
| 14239–14279 | script | 2 | — |
| 14280–14316 | style | 3 | v147 Top board: ranked rows, big score, share as an image |
| 14316–14407 | script | 7 | v147 Top board in Played, plus a share-as-image button |
| 14408–14434 | style | 2 | v150 badges |
| 14434–14501 | script | 4 | v150 badges: worked out from what you've done, saved with the date you earned them |
| 14501–14507 | script | 0 | v151 art strength slider: live while dragging, saved when let go |
| 14507–14508 | script | 0 | — |
| 14508–14525 | style | 1 | v155 light board motion: only fade + slide, which the phone's graphics chip does for free |
| 14525–14545 | script | 2 | — |
| 14545–14571 | style | 2 | v156 scroll-driven motion: the browser ties these to your finger on its own graphics thread, so they stay at full refresh rate however fast you… |
| 14571–14581 | style | 1 | v157 the real drag: a full-screen blurred picture sat behind every screen, slowly zooming forever, so the phone re-blurred the whole screen on every… |
| 14581–14588 | script | 0 | — |
| 14589–14608 | style | 1 | v163: Why you'll love it — one sheet, fades and slides in, nothing loops |
| 14608–14678 | script | 4 | v163: "Why you'll love it" — sends your ratings and this game to your Cloudflare helper, which asks free Workers AI why it fits your taste (or… |
| 14679–14710 | script | 2 | v166: reset scores — one game from its page, or everything from Settings → Your data. Games, art, hours and diary stay; played games stay played,… |
| 14711–14721 | style | 1 | v167 Nexi's glow sometimes showed as a square box. Three stacked fixes (blur, clip-path, mask) each add a separate layer the phone can drop… |
| 14722–14762 | style | 3 | v169 speed: these animations changed colours/sizes/positions, which makes the phone redraw on every frame. Same looks, now moved or faded instead,… |
| 14763–14774 | style | 1 | v171 swipe a queue card left to delete the game. Only the card's contents slide (translate), the red button waits underneath. |
| 14774–14835 | script | 3 | — |
| 14836–14851 | style | 1 | v172 what's-new tour: a small card with Nexi's words, while Nexi points at the real button |
| 14851–15044 | script | 17 | v172: after every update Nexi shows what's new — where it is, and a nudge to try it. HOW TO ADD ONE: push an entry to WHATS_NEW with the version it… |
| 15044–15286 | style | 22 | ===== v174 Home dashboard (layout from the owner's reference, our black + blue colours) ===== |
| 15286–15372 | style | 8 | ===== v177 play sessions ===== |
| 15372–15513 | script | 10 | ===== v178 calmer settings and edit: search, most-used shortcuts, one section at a time, fewer knobs up front ===== |
| 15513–15559 | style | 4 | ===== v178 calmer settings ===== |
| 15559–15572 | style | 1 | ===== v180 finish month picker ===== |
| 15572–15580 | style | 1 | ===== v181 theme picker ===== |
| 15580–15727 | script | 13 | ===== v181 Game Shuffler: swipe through your games one at a time (right = tempted, left = not now). Styled after the owner's chess reference: a big… |
| 15727–15808 | style | 8 | ===== v181 Game Shuffler (chess reference layout, our black + theme accent) ===== |
| 15808–15834 | style | 2 | ===== v182 lists ===== |
| 15834–15871 | style | 3 | ===== v183 extras off by default, quick duel / score sheets, usage table, quick add ===== |
| 15871–15894 | script | 2 | ===== v183 what's new + settings extras ===== |
| 15894–15939 | script | 3 | ---- badges that keep going as you duel ---- |
| 15939–16016 | style | 7 | ===== v184 ranking: tiers, confidence dots, On deck, Nexi on Home, insights ===== |
| 16016–16036 | script | 2 | ===== v185 what's new ===== |
| 16036–16083 | style | 4 | ===== v185 Nexi chat, personalities, similar games ===== |
| 16083–16091 | script | 0 | ===== v186 what's new ===== |
| 16091–16118 | script | 3 | ===== v187: similar games picked by the AI and checked on RAWG; helper status in Settings → Nexi ===== |
| 16118–16236 | style | 8 | ===== v188 "Clean" style: the rest of the app speaks Home's language ===== One layer, scoped to html.ui2 (Settings → Look of the app → Style →… |
| 16236–16247 | script | 1 | ===== v188 what's new: the Clean style ===== |
| 16247–16267 | style | 2 | ===== v189 tour: Nexi drops out of the notch / camera hole, then rides on the tour card ===== |
| 16267–16368 | script | 8 | ===== v189 tour: Nexi comes out of the notch and stays with the tour card ===== |
| 16368–16524 | style | 12 | ===== v194 "Sicker everywhere": Home's look on every screen (Clean style only) ===== Each screen's header is a card like Home's: a big title, live… |
| 16524–16734 | script | 17 | ===== v194: every screen's header is a card like Home's ===== |
| 16734–16851 | style | 12 | ===== v195: the Add screen and a game's page in Home's language (both switchable) ===== |
| 16851–16901 | script | 4 | ===== v195: a game's page, New style ===== |
| 16901–16906 | style | 0 | v196: on phones under ~400px the pick card was 352px wide whatever the screen (its 220px min-height through the 16:10 ratio set a minimum width) and… |
| 16906–17153 | script | 19 | ===== v196: problem reports. The app notices when something breaks and files it (Settings → This build) ===== What it watches: script errors, a page… |
| 17153–17171 | style | 2 | — |
| 17171–17254 | script | 6 | ===== v197: Nexi says when he's spotted (and already reported) a problem, and the report sheet lists them ===== |
| 17254–17318 | script | 5 | ===== v198: problem reports catch lag, not just freezes ===== The v196 check only saw 400ms+ freezes; the stutters you feel on a 120Hz screen are… |
| 17318–17422 | script | 8 | ===== v199: Nexi's eyes see more: cut-off text, text on top of text, a blank screen ===== Runs with bugLayout (idle, once a screen settles). Only… |
| 17422–17452 | script | 3 | ===== v200: the two lags the owner felt: Queue scrolling and a game page opening ===== |
| 17452–17464 | script | 1 | ===== v201: every picture in the SteamGridDB and Steam pickers shows its real size (4K / HD / Low) before you pick; SteamGridDB lists sharpest first… |
| 17464–17515 | script | 4 | ===== v202: 4K art really shows in 4K (the swap to the sharp copy was starved by the live countdown, see the liteUp kick), RAWG screenshots as… |
| 17515–17539 | script | 2 | ===== v203: a game page is sharp as soon as it opens. warmGame (touch-down, and each neighbour for the swipe) already downloaded and decoded the… |
| 17539–17613 | style | 7 | ===== v204: each personality has its own face, idle move and a signature move when it talks. Persona rules use :where() so they beat the plain face… |
| 17613–17857 | script | 21 | ===== v204: Nexi's personalities get their own lines (context-aware, ~20 each), faces and moves; more everyday lines for everyone; double tap opens… |
| 17857–18020 | style | 17 | ===== v205: Taste → Insights. Psychology, Patterns and Analytics in one page, ordered the way attention works: who you are (identity) → how good… |
| 18020–18214 | script | 19 | ===== v205: Taste → Insights (see the style block above for the order and why) ===== |
| 18214–18263 | script | 4 | ===== v206: lag fixes from the problem reports ===== render() rebuilt all seven screens on every save, add or delete (#58: a 260ms tap on Edit →… |
| 18263–18275 | style | 1 | v208: found by Nexi's new checks on the first sweep: queue rank numbers were drawn in the border colour (1.9:1, hard to read) and the small icon… |
| 18275–18528 | script | 21 | ===== v208: Nexi's new senses. The owner hit 10–15 problems he never saw: a page that flashed back while closing (#68), a description cut short with… |
| 18528–18545 | script | 1 | ===== v209: #81 (4K art stayed low-res). The fix itself is in liteUp's go() (retries, decode() fallback, LITE_NOFULL) and warmGame; this is the tour… |
| 18545–18800 | script | 19 | ===== v211: cloud backup ===== Everything on this phone is sealed here (AES-GCM, key made from the restore code) and sent to the helper, which only… |
| 18800–18816 | style | 1 | — |
| 18816–18921 | script | 8 | ===== v212: "Where was I?" notes ===== A game keeps short notes about where you stopped (g.wasI = [{t, x}], newest last, 20 kept). The newest shows… |
| 18921–18961 | style | 4 | — |
| 18961–19351 | script | 41 | ===== v213: price alerts (Steam + PlayStation Store) ===== Games you're waiting for (not played, not dropped, not competitive) are priced through… |
| 19351–19407 | style | 4 | — |
| 19407–19419 | style | 1 | ===== v215 fixes ===== |
| 19419–19534 | script | 11 | ===== v215: the frozen game page (#99, #102, #103), Read more (#85, #100), Taste names (#86), Queue rows (#89), 4K pictures by default (#84), and… |
| 19534–19549 | style | 1 | ===== v216: the story so far ===== |
| 19549–19606 | script | 5 | ===== v216: "the story so far". Under your newest 📍 note on a game's page, Nexi writes a short recap of the plot up to where the note says you are… |
| 19606–19706 | script | 9 | ===== v216: Steam's genres and "more like this". Steam's own tags are the genres players vote on ("Souls-like", "Metroidvania", "JRPG",… |
| 19706–19725 | script | 2 | — |
| 19725–19732 | style | 0 | ===== v217: release-date watcher + deal radar ===== |
| 19732–19852 | script | 11 | ===== v217: two new things Nexi does on his own (the owner loves automatic features). 1. Release-date watcher (`rdWatch`): every game you're waiting… |
| 19852–19856 | style | 0 | ===== v218: "Finished it?" (same card as Where was I?) ===== |
| 19856–19938 | script | 7 | ===== v218: "Finished it?" — Nexi notices when a game you're playing is probably done and asks, by himself. Signals (`fnWhy(g)`, first that fits):… |
| 19938–19956 | style | 1 | ===== v219: reviews are in (+ your score, predicted) ===== |
| 19956–20199 | script | 20 | ===== v219: three more things Nexi does by himself, and smarter ones (the owner: "I need more smarter and useful auto things"). 1. Reviews are in… |
| 20199–20230 | style | 3 | ===== v220: away report, finish forecast, time capsule, seasons ===== |
| 20230–20478 | script | 23 | ===== v220: six more things that happen by themselves (the owner: "more auto, unique, rare and out of the box"). 1. While you were away (`aw*`):… |
| 20478–20499 | style | 1 | ===== v221: logo fit, Nexi in the rail, dusty backlog, crystal ball ===== |
| 20499–20737 | script | 22 | ===== v221: the owner's and their friend's reports (#118 prices stuck on "checking…", #114 "fit the logo in the queue automatically", #123 "the rail… |
| 20737–20750 | script | 1 | ===== v222: #153 tour steps that point at things only there once you've scored games. The fixes are in the v184/v215 steps (fall back to the "What… |
| 20750–20762 | script | 1 | ===== v224: prices in one line (pxCard, store logos PX_LOGO) + a tap on seated Nexi hides the rail (railHideNow) ===== |
| 20762–20768 | style | 0 | ===== v225: the cover you tap flies into the game page ===== |
| 20768–20835 | script | 5 | ===== v225: the cover you tap flies into the game page ===== Opening a game from anything that shows its cover (Home, queue, library, rail, deck,… |
| 20835–20844 | script | 1 | ===== v226: platforms as logos on the game page (platChips / platHtml, next to platList) ===== |
| 20844–20898 | script | 3 | ===== v227: prices for games the stores' title search can't find (the owner's GTA 6: the PS Store's old search doesn't list it, though its store… |
| 20898–20907 | script | 1 | ===== v228: platform logos in one pill; price checks never skip a store for good (pxStale/pxGap, batches of 5); helper v13 ===== |
| 20908–21213 | script | 33 | ===== v229: Nexi's brain. The owner wants Nexi "super smart", saying a lot more, all of it different and useful, and using their name much more.… |
| 21213–21229 | style | 1 | v230: Browse and choose, every store |
| 21229–21343 | script | 9 | ===== v230: pictures straight from the stores. Browse and choose (Edit → Look) now shows, next to SteamGridDB and RAWG, the official art of Steam… |
| 21343–21352 | script | 1 | ===== v231 (daily fixer): #180/#181/#154 the tour's Next button answered slowly (it saved and opened the next screen inside the tap; now that waits… |
| 21352–21460 | style | 10 | ===== v232: the same shape for every open queue card and every game page ===== |
| 21460–21706 | script | 23 | ===== v232: the owner: "each queue card is a different size when open" and "the new game page lacks organisation: each game looks different because… |
| 21706–21783 | style | 8 | ===== v236: game suggestions you can see (big cards, a preview with store screenshots), swipe to add, Discover, share to add ===== |
| 21783–22099 | script | 28 | ===== v236: the owner: "adding Baldur's Gate 3 from Similar added the wrong game; the pictures are too small and I need more of a description; maybe… |
| 22099–22114 | style | 1 | ===== v237: screenshots on every game page; Grumble only takes real hits ===== |
| 22114–22237 | script | 11 | ===== v237: the owner: "I deleted Baldur's Gate and Grumble lost HP, which is wrong; and when I add a game, forget to say it's played and then say… |
| 22237–22385 | script | 13 | ===== v238: the owner: "only a few games got screenshots, and the ones that did took time to load. I want them instant and always ready, not fetched… |
| 22385–22489 | style | 12 | ===== v239/v242: Game room — a little 3D room built from your games (owner's isometric "B" room reference: layout and style, our colours). Plain CSS… |
| 22489–22861 | script | 31 | ===== v239: Game room. The owner sent an isometric illustration (a room built around a giant book-letter) and asked for something like it in 3D that… |
| 22861–22892 | style | 3 | v241: hours to beat from HowLongToBeat |
| 22892–23098 | script | 17 | ===== v241: hours to beat from HowLongToBeat (`hl*`). The owner: "most hours I type by hand; the automatic ones are super wrong". Two automatic… |
| 23098–23116 | script | 1 | ===== v243 (#313, the owner: "pressing on the text should expand it, not only Read more"): a tap on a cut-short description (or the story so far)… |
| 23116–23127 | script | 1 | ===== v244 (#319): a tap on a game's description (or the story so far) now closes it again too; see the v243 listener. Tour fixes (#307): the v236… |
| 23127–23136 | script | 1 | ===== v245 (#301): pnShelf skips the Finished shelf while its screen is hidden (it forced a full layout of the screen you were on, 130ms at 4x);… |
| 23136–23146 | script | 1 | ===== v246 (#309): no press animation. The owner: "when I touch anything an animation happened, delete that". Until v268 this script stripped… |
| 23146–23150 | style | 0 | v247: the rail's Add and Settings buttons draw the menu's own icons (they were the text glyphs ＋ and ⚙) |
| 23150–23162 | script | 1 | ===== v247: new menu icons, picked by the owner from two drawn sets: Home (house), Games (game case with a disc), Plan (calendar with a release… |
| 23162–23305 | script | 11 | ===== v248: three things that run by themselves. 1. Release days in your calendar: Settings → Extra features → turn on makes a random id (S.calId),… |
| 23305–23336 | style | 3 | v249: buy advice line under the prices, the drifting card on a game page, the shopping plan and queue clean-up sheets |
| 23336–23701 | script | 34 | ===== v249: three smarter things that run by themselves (the owner picked them from a list: "1 2 4"). 1. Buy advice (`sm*` / `smBuy`): price alerts… |
| 23701–23721 | script | 1 | Screen changes in one place (the v269 tidy-up). goTab, goSub and openFull used to be wrapped once per feature that wanted to know about a screen… |
| 23721–23796 | script | 7 | ===== v249, smoother everywhere (the owner: "all tabs and windows lag"; "a lot of lags that even Nexi doesn't see"). 1. Nexi's own problem checks… |
| 23796–23825 | script | 2 | ===== v250 (the owner, with a screenshot of a "picked for you" card: "not all games have screenshots inside here, fix"): 1. The preview (gpLoad)… |
| 23825–23860 | style | 3 | ===== v251: the rail's live strip (replaces the queue covers) + Nexi sized to his seat ===== |
| 23860–24050 | script | 17 | ===== v251 (the owner, screenshot of the unfolded rail with the queue covers circled: "change them into something useful with live animation that is… |
| 24050–24058 | script | 1 | v253 (#338): the view switcher's marker is measured in the next frame (slidePill), not inside the tap |
| 24058–24072 | script | 1 | after v255 (#354 #355 #358): the lag checks skip frames that overlap a resize (fold/unfold, 0.8s after) or the time the app was hidden (asleep, in… |
| 24072–24083 | script | 1 | v256 (#352): the calling card is drawn in steps (callingCard, yf) |
| 24083–24127 | style | 4 | v257 live background (hlv*): a game's art and three lights taken from it drift behind the screen, plus rising specks. Only transform/opacity move,… |
| 24127–24348 | script | 13 | v257 live background. Switches: S.hlvOff (Home), S.hlvX.games / .game / .plan / .taste (Customise on each screen) |
| 24348–24423 | style | 8 | v259 three new card looks, from the owner's references (layout and style copied, our colours): dzp = Home "Playing now" colour cards, dzd = On deck… |
| 24423–24518 | script | 8 | v259 three new card looks (switch: S.dzX) |
| 24518–24523 | style | 0 | v260 smoothness + battery: fewer full-screen layers stacked on every frame |
| 24523–24570 | script | 3 | v260: #bgtint only while an art backdrop is up |
| 24571–24575 | style | 0 | v261: the view pill's highlight slides on the GPU: it used to animate width (a layout every frame for .28s) |
| 24575–24591 | script | 1 | v261: smooth at the screen's own refresh rate. The highlight takes its new width at once and a transform animation (scaleX from the old width) makes… |
| 24592–24631 | script | 3 | v261: Nexi learned (the owner: "still lags a lot, I want 120 fps"): an animation the phone can't hand to its GPU makes the main thread draw every… |
| 24631–24638 | script | 1 | — |
| 24639–24646 | style | 0 | v263 (120 fps): Nexi's face: a mood used to morph his eyes and mouth (height, top, width, borders: main-thread transitions), and a blink restarted… |
| 24646–24790 | script | 11 | v263 (120 fps): a looping animation on something that isn't drawn (a hidden screen; a list row scrolled far enough that the browser skips drawing… |
| 24790–24797 | style | 1 | v264: the whole background in one layer (hlvf*, last script). The flat canvas keeps v257's own canvas rule (its own layer, will-change: transform)… |
| 24797–25037 | script | 17 | v264 (the owner, the fifth time: "still lags"). v263 made the main thread quiet; what was left is the GPU. A trace of what it draws per frame (the… |
| 25037–25045 | style | 1 | v265 smoothness check (prb*, last script): while it tests a suspect, that suspect is switched off for a little while |
| 25045–25169 | script | 9 | v265: the smoothness check (the owner, after five rounds: "it lags everywhere, as if it's too much: a fast scroll up or down on the Queue, sideways… |
| 25169–25183 | script | 1 | v265 (the owner: "if I scroll fast it lags, up or down on the Queue"): touch scrolling no longer waits for the app. Every screen but Home and every… |
| 25183–25198 | script | 1 | v265: the light behind the screen holds still while you're touching, scrolling or switching (it moves about a pixel per step, so a pause can't be… |
| 25198–25239 | script | 4 | v265: Nexi learned (the blocking touchmove above sat on every screen for months and nothing noticed: it doesn't throw, doesn't look wrong, and the… |
| 25239–25399 | script | 12 | v266: the self-test (step 1 of the plan the owner agreed: "measure first, on the phone, before changing anything"). Settings → This build → "Run the… |
| 25399–25408 | style | 1 | v266 self-test result card (inside the diary sheet's box) |
| 25408–25415 | script | 0 | — |
| 25415–25554 | script | 10 | v267 picture fit, layer 2 (layer 1 is sw.js: nothing bigger than the screen). Lists show pictures far smaller than the screen: a Queue row's faded… |
| 25554–25648 | script | 8 | v267: Nexi learned (the phone's self-test: 372 MP of pictures on the Queue for 14 MP on screen, one picture 70 MP; none of his checks looked at… |
| 25648–25657 | script | 1 | v269: the tidy-up (the owner: "organise the code, merge it, cut what isn't needed"). No new feature: onNav (screen-change hooks in one place), dead… |
