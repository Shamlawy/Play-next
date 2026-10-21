# Code map of index.html

Made by `node tools/map.mjs` (run it again after a release; line numbers move). 28727 lines, 2231 KB, 125 scripts, 66 styles.

How the file is built: one core style (line 15) and one core script (the biggest block below), then one patch per release, oldest first. A patch adds its own code and often wraps a core function (`const _of = openFull; openFull = function () { ... _of.apply(this, arguments) ... }`), so the **last** wrap runs first. To change what a function does, read every layer listed under "Wrapped functions". New code goes in the last script/style.

## Wrapped functions (most layers first)

| function | defined | layers (line: block) |
|---|---|---|

## Screen-change hooks (onNav / onSettings, since v269)

goTab / goSub / openFull run every `onNav("before"|"after", fn)` hook and openSettings every `onSettings(...)` hook (one wrap each, defined at line 12955, in registration order below). New code registers here; `kit.mjs syntax` fails on a new wrap of those four.

- 12970: `onNav("after", (k, a) => { if (k === "sub") try { uVisit(a[0]); } catch (e) {} });`
- 12971: `onNav("before", (k, a) => { if (k === "game" && (hubAt !== a[0] || !$("#full.on"))) uCount("o", "game"); });`
- 12973: `onSettings("before", (k, a) => { const sc = a[0]; uCount("o", "settings" + (sc && sc !== "app" ? "/" + sc : ""`
- 15813 v178: `onSettings("after", () => { $("#sheet-in").classList.remove("drill", "ssearch"); calmSheet(true); });`
- 16412 v187: `onSettings("after", () => setTimeout(helperCheck, 50));`
- 16971 v194: `onSettings("after", () => shxSetWire());`
- 17424 v196: `onNav("after", () => bugSeen());`
- 17425 v196: `onSettings("after", () => { bugCrumb("screen settings"); bugLayoutSoon(); });`
- 17954 v203: `onNav("before", k => { if (k === "game") openAt = performance.now(); });`
- 18645 v205: `onNav("after", k => { if (k === "tab" && curTab() !== "taste") ixShown.clear(); });`
- 19191 v211: `onSettings("after", () => cbSetWire());`
- 19753 v213: `onSettings("after", () => pxSetWire());`
- 19755 v213: `onNav("after", (k, a) => { if (k !== "game") return;`
- 19922 v215: `onSettings("after", () => requestAnimationFrame(prune)); onNav("after", () => requestAnimationFrame(prune));`
- 20058 v216: `onNav("after", (k, a) => { if (k !== "game") return; const g = S.games.find(x => x.id === a[0]);`
- 20536 v219: `onNav("after", (k, a) => { if (k !== "game") return;`
- 20767 v220: `onSettings("before", () => { const el = document.getElementById("sheet"); if (el) el._flkOpen = performance.no`
- 21090 v221: `onNav("after", k => { if (k === "tab") setTimeout(() => { try { lfApply(); } catch (e) {} }, 60); });`
- 21093 v221: `onNav("before", (k, a) => { if (k !== "game") return; const g = S.games.find(x => x.id === a[0]), d = g ? dust`
- 21290 v225: `onNav("before", k => { if (k !== "game") return;`
- 22621 v237: `onNav("after", (k, a) => { if (k !== "game") return;`
- 24182 v249: `onNav("after", k => { if (k === "tab") lagWatch(() => "Switching to " + lagWhere()); else if (k === "game") la`
- 24184 v249: `onSettings("after", () => lagWatch("Opening Settings"));`
- 25015 v264: `onNav("after", k => { if (k === "tab") { STACK.go = null; stackArm(3500); } });`
- 25142 v265: `onNav("before", k => prbStart(k === "game" ? "open" : "switch", 1000));`
- 25581 v267: `onNav("after", () => requestAnimationFrame(fitNow));`
- 25627 v267: `onNav("after", k => { if (k !== "sub") picArm(4000); });`
- 25677 v267: `onNav("after", k => { if (k !== "sub") picWait(); });`
- 25783 v271: `onNav("after", (k, a) => {`
- 25964 v278: `onSettings("before", (k, a) => { lvpScope = SCOPE_NAME[a[0]] ? a[0] : "app"; });`
- 25965 v278: `onSettings("after", () => requestAnimationFrame(lvpSync));`
- 26255 v281: `onSettings("after", () => spSetWire());`
- 26391 v285: `onSettings("after", () => {`
- 26434 : `onNav("after", () => requestAnimationFrame(() => { try { cdTick(); } catch (e) {} }));`
- 26720 v288: `onNav("before", () => { TEA.nav = performance.now(); if (TEA.on) teaStop(); });`
- 27142 v297: `onSettings("before", () => srlOff());`
- 27143 v297: `onSettings("after", () => srlBuild());`
- 27969 : `onNav("after", () => { lookNavAt = performance.now(); }); onSettings("after", () => { lookNavAt = performance.`
- 28020 : `onNav("after", () => { const p = P(); if (!p) return;`
- 28679 v317: `onSettings("before", () => { if (SKB_MUS.g) skbMusStop(.5); });`
- 28685 v317: `onSettings("after", () => { const c = $("#s-bkmus"); if (!c) return;`
- 28700 v317: `onNav("before", () => { if (SKB.intro) skbSkip(); if (SKB_MUS.g) skbMusStop(.5); });`
- 28702 v317: `onNav("after", (k, args) => { if (k === "tab" && (args[0] === "taste" || args[0] === "book") && subOf("taste")`

## Blocks

| lines | kind | KB | what |
|---|---|---|---|
| 15–2834 | style | 198 | **CORE** — — |
| 2838–2924 | script | 13 | ===== v314: drawn icons on the chips, buttons and sheet headers that still had emoji (the owner: "do the emoji to icons cleanup", T32). uiIc(name):… |
| 2924–3305 | script | 27 | REEL-START: "One pixel plays everything", the 15s opening reel (120 BPM, 30 beats). Every pixel is computed from t alone (no timers, no state… |
| 3418–14572 | script | 734 | **CORE** — ===== v279: extend(name, hook) — how a feature adds to an app function, instead of wrapping it ===== The app grew by wrapping functions (const _f =… |
| 14573–14590 | style | 1 | — |
| 14590–14630 | script | 2 | — |
| 14631–14663 | style | 3 | v147 Top board: ranked rows, big score, share as an image |
| 14663–14753 | script | 7 | v147 Top board in Played, plus a share-as-image button |
| 14754–14780 | style | 2 | v150 badges |
| 14780–14849 | script | 4 | v150 badges: worked out from what you've done, saved with the date you earned them |
| 14849–14855 | script | 0 | v151 art strength slider: live while dragging, saved when let go |
| 14855–14856 | script | 0 | — |
| 14856–14865 | style | 0 | v155 light board motion: only fade + slide, which the phone's graphics chip does for free |
| 14865–14877 | script | 1 | — |
| 14877–14889 | style | 1 | v156 scroll-driven motion: the browser ties these to your finger on its own graphics thread, so they stay at full refresh rate however fast you… |
| 14889–14896 | script | 0 | — |
| 14897–14916 | style | 1 | v163: Why you'll love it — one sheet, fades and slides in, nothing loops |
| 14916–14986 | script | 4 | v163: "Why you'll love it" — sends your ratings and this game to your Cloudflare helper, which asks free Workers AI why it fits your taste (or… |
| 14987–15018 | script | 2 | v166: reset scores — one game from its page, or everything from Settings → Your data. Games, art, hours and diary stay; played games stay played,… |
| 15019–15029 | style | 1 | v167 Nexi's glow sometimes showed as a square box. Three stacked fixes (blur, clip-path, mask) each add a separate layer the phone can drop… |
| 15030–15067 | style | 3 | v169 speed: these animations changed colours/sizes/positions, which makes the phone redraw on every frame. Same looks, now moved or faded instead,… |
| 15068–15079 | style | 1 | v171 swipe a queue card left to delete the game. Only the card's contents slide (translate), the red button waits underneath. |
| 15079–15140 | script | 3 | — |
| 15141–15156 | style | 1 | v172 what's-new tour: a small card with Nexi's words, while Nexi points at the real button |
| 15156–15358 | script | 18 | v172: after every update Nexi shows what's new — where it is, and a nudge to try it. HOW TO ADD ONE: push an entry to WHATS_NEW with the version it… |
| 15358–15601 | style | 22 | ===== v174 Home dashboard (layout from the owner's reference, our black + blue colours) ===== |
| 15601–15687 | style | 8 | ===== v177 play sessions ===== |
| 15687–15818 | script | 9 | ===== v178 calmer settings and edit: search, most-used shortcuts, one section at a time, fewer knobs up front ===== |
| 15818–15864 | style | 4 | ===== v178 calmer settings ===== |
| 15864–15877 | style | 1 | ===== v180 finish month picker ===== |
| 15877–15885 | style | 1 | ===== v181 theme picker ===== |
| 15885–16032 | script | 13 | ===== v181 Game Shuffler: swipe through your games one at a time (right = tempted, left = not now). Styled after the owner's chess reference: a big… |
| 16032–16113 | style | 8 | ===== v181 Game Shuffler (chess reference layout, our black + theme accent) ===== |
| 16113–16136 | style | 2 | ===== v182 lists ===== |
| 16136–16173 | style | 3 | ===== v183 extras off by default, quick duel / score sheets, usage table, quick add ===== |
| 16173–16195 | script | 2 | ===== v183 what's new + settings extras ===== |
| 16195–16239 | script | 3 | ---- badges that keep going as you duel ---- |
| 16239–16316 | style | 7 | ===== v184 ranking: tiers, confidence dots, On deck, Nexi on Home, insights ===== |
| 16316–16335 | script | 2 | ===== v185 what's new ===== |
| 16335–16382 | style | 4 | ===== v185 Nexi chat, personalities, similar games ===== |
| 16382–16390 | script | 0 | ===== v186 what's new ===== |
| 16390–16419 | script | 4 | ===== v187: similar games picked by the AI and checked on RAWG; helper status in Settings → Nexi ===== |
| 16419–16537 | style | 8 | ===== v188 "Clean" style: the rest of the app speaks Home's language ===== One layer, scoped to html.ui2 (Settings → Look of the app → Style →… |
| 16537–16548 | script | 1 | ===== v188 what's new: the Clean style ===== |
| 16548–16568 | style | 2 | ===== v189 tour: Nexi drops out of the notch / camera hole, then rides on the tour card ===== |
| 16568–16669 | script | 8 | ===== v189 tour: Nexi comes out of the notch and stays with the tour card ===== |
| 16669–16825 | style | 12 | ===== v194 "Sicker everywhere": Home's look on every screen (Clean style only) ===== Each screen's header is a card like Home's: a big title, live… |
| 16825–17035 | script | 16 | ===== v194: every screen's header is a card like Home's ===== |
| 17035–17151 | style | 12 | ===== v195: the Add screen and a game's page in Home's language (both switchable) ===== |
| 17151–17201 | script | 4 | ===== v195: a game's page, New style ===== |
| 17201–17206 | style | 0 | v196: on phones under ~400px the pick card was 352px wide whatever the screen (its 220px min-height through the 16:10 ratio set a minimum width) and… |
| 17206–17542 | script | 26 | ===== v196: problem reports. The app notices when something breaks and files it (Settings → This build) ===== What it watches: script errors, a page… |
| 17542–17560 | style | 2 | — |
| 17560–17661 | script | 8 | ===== v197: Nexi says when he's spotted (and already reported) a problem, and the report sheet lists them ===== |
| 17661–17755 | script | 9 | ===== v198: problem reports catch lag, not just freezes ===== The v196 check only saw 400ms+ freezes; the stutters you feel on a 120Hz screen are… |
| 17755–17858 | script | 8 | ===== v199: Nexi's eyes see more: cut-off text, text on top of text, a blank screen ===== Runs with bugLayout (idle, once a screen settles). Only… |
| 17858–17885 | script | 2 | ===== v200: the two lags the owner felt: Queue scrolling and a game page opening ===== |
| 17885–17897 | script | 1 | ===== v201: every picture in the SteamGridDB and Steam pickers shows its real size (4K / HD / Low) before you pick; SteamGridDB lists sharpest first… |
| 17897–17947 | script | 4 | ===== v202: 4K art really shows in 4K (the swap to the sharp copy was starved by the live countdown, see the liteUp kick), RAWG screenshots as… |
| 17947–17971 | script | 2 | ===== v203: a game page is sharp as soon as it opens. warmGame (touch-down, and each neighbour for the swipe) already downloaded and decoded the… |
| 17971–18045 | style | 7 | ===== v204: each personality has its own face, idle move and a signature move when it talks. Persona rules use :where() so they beat the plain face… |
| 18045–18291 | script | 21 | ===== v204: Nexi's personalities get their own lines (context-aware, ~20 each), faces and moves; more everyday lines for everyone; double tap opens… |
| 18291–18459 | style | 17 | ===== v205: Taste → Insights. Psychology, Patterns and Analytics in one page, ordered the way attention works: who you are (identity) → how good… |
| 18459–18653 | script | 19 | ===== v205: Taste → Insights (see the style block above for the order and why) ===== |
| 18653–18702 | script | 4 | ===== v206: lag fixes from the problem reports ===== render() rebuilt all seven screens on every save, add or delete (#58: a 260ms tap on Edit →… |
| 18702–18714 | style | 1 | v208: found by Nexi's new checks on the first sweep: queue rank numbers were drawn in the border colour (1.9:1, hard to read) and the small icon… |
| 18714–18978 | script | 22 | ===== v208: Nexi's new senses. The owner hit 10–15 problems he never saw: a page that flashed back while closing (#68), a description cut short with… |
| 18978–18995 | script | 1 | ===== v209: #81 (4K art stayed low-res). The fix itself is in liteUp's go() (retries, decode() fallback, LITE_NOFULL) and warmGame; this is the tour… |
| 18995–19250 | script | 19 | ===== v211: cloud backup ===== Everything on this phone is sealed here (AES-GCM, key made from the restore code) and sent to the helper, which only… |
| 19250–19266 | style | 1 | — |
| 19266–19372 | script | 8 | ===== v212: "Where was I?" notes ===== A game keeps short notes about where you stopped (g.wasI = [{t, x}], newest last, 20 kept). The newest shows… |
| 19372–19412 | style | 4 | — |
| 19412–19802 | script | 41 | ===== v213: price alerts (Steam + PlayStation Store) ===== Games you're waiting for (not played, not dropped, not competitive) are priced through… |
| 19802–19858 | style | 4 | — |
| 19858–19870 | style | 1 | ===== v215 fixes ===== |
| 19870–20002 | script | 13 | ===== v215: the frozen game page (#99, #102, #103), Read more (#85, #100), Taste names (#86), Queue rows (#89), 4K pictures by default (#84), and… |
| 20002–20017 | style | 1 | ===== v216: the story so far ===== |
| 20017–20074 | script | 5 | ===== v216: "the story so far". Under your newest 📍 note on a game's page, Nexi writes a short recap of the plot up to where the note says you are… |
| 20074–20158 | script | 8 | ===== v216: Steam's genres and "more like this". Steam's own tags are the genres players vote on ("Souls-like", "Metroidvania", "JRPG",… |
| 20158–20177 | script | 2 | — |
| 20177–20184 | style | 0 | ===== v217: release-date watcher + deal radar ===== |
| 20184–20304 | script | 11 | ===== v217: two new things Nexi does on his own (the owner loves automatic features). 1. Release-date watcher (`rdWatch`): every game you're waiting… |
| 20304–20308 | style | 0 | ===== v218: "Finished it?" (same card as Where was I?) ===== |
| 20308–20390 | script | 7 | ===== v218: "Finished it?" — Nexi notices when a game you're playing is probably done and asks, by himself. Signals (`fnWhy(g)`, first that fits):… |
| 20390–20408 | style | 1 | ===== v219: reviews are in (+ your score, predicted) ===== |
| 20408–20650 | script | 20 | ===== v219: three more things Nexi does by himself, and smarter ones (the owner: "I need more smarter and useful auto things"). 1. Reviews are in… |
| 20650–20681 | style | 3 | ===== v220: away report, finish forecast, time capsule, seasons ===== |
| 20681–20924 | script | 22 | ===== v220: six more things that happen by themselves (the owner: "more auto, unique, rare and out of the box"). 1. While you were away (`aw*`):… |
| 20924–20943 | style | 1 | ===== v221: logo fit, Nexi in the rail, dusty backlog, crystal ball ===== |
| 20943–21206 | script | 24 | ===== v221: the owner's and their friend's reports (#118 prices stuck on "checking…", #114 "fit the logo in the queue automatically", #123 "the rail… |
| 21206–21219 | script | 1 | ===== v222: #153 tour steps that point at things only there once you've scored games. The fixes are in the v184/v215 steps (fall back to the "What… |
| 21219–21231 | script | 1 | ===== v224: prices in one line (pxCard, store logos PX_LOGO) + a tap on seated Nexi hides the rail (railHideNow) ===== |
| 21231–21237 | style | 0 | ===== v225: the cover you tap flies into the game page ===== |
| 21237–21303 | script | 5 | ===== v225: the cover you tap flies into the game page ===== Opening a game from anything that shows its cover (Home, queue, library, rail, deck,… |
| 21303–21312 | script | 1 | ===== v226: platforms as logos on the game page (platChips / platHtml, next to platList) ===== |
| 21312–21366 | script | 3 | ===== v227: prices for games the stores' title search can't find (the owner's GTA 6: the PS Store's old search doesn't list it, though its store… |
| 21366–21375 | script | 1 | ===== v228: platform logos in one pill; price checks never skip a store for good (pxStale/pxGap, batches of 5); helper v13 ===== |
| 21376–21681 | script | 33 | ===== v229: Nexi's brain. The owner wants Nexi "super smart", saying a lot more, all of it different and useful, and using their name much more.… |
| 21681–21697 | style | 1 | v230: Browse and choose, every store |
| 21697–21810 | script | 9 | ===== v230: pictures straight from the stores. Browse and choose (Edit → Look) now shows, next to SteamGridDB and RAWG, the official art of Steam… |
| 21810–21819 | script | 1 | ===== v231 (daily fixer): #180/#181/#154 the tour's Next button answered slowly (it saved and opened the next screen inside the tap; now that waits… |
| 21819–21917 | style | 9 | ===== v232: the same shape for every open queue card and every game page ===== |
| 21917–22151 | script | 22 | ===== v232: the owner: "each queue card is a different size when open" and "the new game page lacks organisation: each game looks different because… |
| 22151–22228 | style | 8 | ===== v236: game suggestions you can see (big cards, a preview with store screenshots), swipe to add, Discover, share to add ===== |
| 22228–22542 | script | 28 | ===== v236: the owner: "adding Baldur's Gate 3 from Similar added the wrong game; the pictures are too small and I need more of a description; maybe… |
| 22542–22557 | style | 1 | ===== v237: screenshots on every game page; Grumble only takes real hits ===== |
| 22557–22675 | script | 11 | ===== v237: the owner: "I deleted Baldur's Gate and Grumble lost HP, which is wrong; and when I add a game, forget to say it's played and then say… |
| 22675–22820 | script | 12 | ===== v238: the owner: "only a few games got screenshots, and the ones that did took time to load. I want them instant and always ready, not fetched… |
| 22820–22924 | style | 12 | ===== v239/v242: Game room — a little 3D room built from your games (owner's isometric "B" room reference: layout and style, our colours). Plain CSS… |
| 22924–23293 | script | 31 | ===== v239: Game room. The owner sent an isometric illustration (a room built around a giant book-letter) and asked for something like it in 3D that… |
| 23293–23324 | style | 3 | v241: hours to beat from HowLongToBeat |
| 23324–23529 | script | 16 | ===== v241: hours to beat from HowLongToBeat (`hl*`). The owner: "most hours I type by hand; the automatic ones are super wrong". Two automatic… |
| 23529–23547 | script | 1 | ===== v243 (#313, the owner: "pressing on the text should expand it, not only Read more"): a tap on a cut-short description (or the story so far)… |
| 23547–23558 | script | 1 | ===== v244 (#319): a tap on a game's description (or the story so far) now closes it again too; see the v243 listener. Tour fixes (#307): the v236… |
| 23558–23567 | script | 1 | ===== v245 (#301): pnShelf skips the Finished shelf while its screen is hidden (it forced a full layout of the screen you were on, 130ms at 4x);… |
| 23567–23577 | script | 1 | ===== v246 (#309): no press animation. The owner: "when I touch anything an animation happened, delete that". Until v268 this script stripped… |
| 23577–23581 | style | 0 | v247: the rail's Add and Settings buttons draw the menu's own icons (they were the text glyphs ＋ and ⚙) |
| 23581–23593 | script | 1 | ===== v247: new menu icons, picked by the owner from two drawn sets: Home (house), Games (game case with a disc), Plan (calendar with a release… |
| 23593–23736 | script | 11 | ===== v248: three things that run by themselves. 1. Release days in your calendar: Settings → Extra features → turn on makes a random id (S.calId),… |
| 23736–23767 | style | 3 | v249: buy advice line under the prices, the drifting card on a game page, the shopping plan and queue clean-up sheets |
| 23767–24128 | script | 33 | ===== v249: three smarter things that run by themselves (the owner picked them from a list: "1 2 4"). 1. Buy advice (`sm*` / `smBuy`): price alerts… |
| 24128–24204 | script | 7 | ===== v249, smoother everywhere (the owner: "all tabs and windows lag"; "a lot of lags that even Nexi doesn't see"). 1. Nexi's own problem checks… |
| 24204–24235 | script | 3 | ===== v250 (the owner, with a screenshot of a "picked for you" card: "not all games have screenshots inside here, fix"): 1. The preview (gpLoad)… |
| 24235–24270 | style | 3 | ===== v251: the rail's live strip (replaces the queue covers) + Nexi sized to his seat ===== |
| 24270–24460 | script | 16 | ===== v251 (the owner, screenshot of the unfolded rail with the queue covers circled: "change them into something useful with live animation that is… |
| 24460–24468 | script | 1 | v253 (#338): the view switcher's marker is measured in the next frame (slidePill), not inside the tap |
| 24468–24482 | script | 1 | after v255 (#354 #355 #358): the lag checks skip frames that overlap a resize (fold/unfold, 0.8s after) or the time the app was hidden (asleep, in… |
| 24482–24493 | script | 1 | v256 (#352): the calling card is drawn in steps (callingCard, yf) |
| 24493–24580 | style | 10 | v259 three new card looks, from the owner's references (layout and style copied, our colours): dzp = Home "Playing now" colour cards, dzd = On deck… |
| 24580–24675 | script | 8 | v259 three new card looks (switch: S.dzX) |
| 24675–24718 | script | 3 | v260 rest: 12s without a touch, every looping animation holds still (no frames to draw, nothing for the GPU to do); the first touch, key, scroll or… |
| 24719–24723 | style | 0 | v261: the view pill's highlight slides on the GPU: it used to animate width (a layout every frame for .28s) |
| 24723–24739 | script | 1 | v261: smooth at the screen's own refresh rate. The highlight takes its new width at once and a transform animation (scaleX from the old width) makes… |
| 24740–24779 | script | 3 | v261: Nexi learned (the owner: "still lags a lot, I want 120 fps"): an animation the phone can't hand to its GPU makes the main thread draw every… |
| 24779–24786 | script | 1 | — |
| 24787–24794 | style | 0 | v263 (120 fps): Nexi's face: a mood used to morph his eyes and mouth (height, top, width, borders: main-thread transitions), and a blink restarted… |
| 24794–24958 | script | 12 | v263 (120 fps): a looping animation on something that isn't drawn (a hidden screen; a list row scrolled far enough that the browser skips drawing… |
| 24958–25023 | script | 5 | v264: Nexi learned (the owner's fifth "still lags", with nothing in his own reports since v263): the lag he couldn't see was the GPU blending a pile… |
| 25023–25033 | style | 1 | v265 smoothness check (prb*, last script): while it tests a suspect, that suspect is switched off for a little while |
| 25033–25157 | script | 9 | v265: the smoothness check (the owner, after five rounds: "it lags everywhere, as if it's too much: a fast scroll up or down on the Queue, sideways… |
| 25157–25171 | script | 1 | v265 (the owner: "if I scroll fast it lags, up or down on the Queue"): touch scrolling no longer waits for the app. Every screen but Home and every… |
| 25171–25175 | script | 0 | the smoothness check counts swiping a game's page to the next game as a sideways moment (it's not a scroll) |
| 25175–25216 | script | 4 | v265: Nexi learned (the blocking touchmove above sat on every screen for months and nothing noticed: it doesn't throw, doesn't look wrong, and the… |
| 25216–25376 | script | 12 | v266: the self-test (step 1 of the plan the owner agreed: "measure first, on the phone, before changing anything"). Settings → This build → "Run the… |
| 25376–25385 | style | 1 | v266 self-test result card (inside the diary sheet's box) |
| 25385–25395 | style | 1 | v271: the progress line draws itself to the step a game just reached. While it runs, the segment's own lines go transparent and .gd-fill (a dim… |
| 25395–25402 | script | 0 | — |
| 25402–25587 | script | 14 | v267 picture fit, layer 2 (layer 1 is sw.js: nothing bigger than the screen). Lists show pictures far smaller than the screen: a Queue row's faded… |
| 25587–25692 | script | 9 | v267: Nexi learned (the phone's self-test: 372 MP of pictures on the Queue for 14 MP on screen, one picture 70 MP; none of his checks looked at… |
| 25692–25701 | script | 1 | v269: the tidy-up (the owner: "organise the code, merge it, cut what isn't needed"). No new feature: onNav (screen-change hooks in one place), dead… |
| 25701–25745 | script | 3 | v270: buzzes on the iPhone too. Safari has no navigator.vibrate, but iOS 18+ gives a light tap each time an <input type="checkbox" switch> flips,… |
| 25745–25802 | script | 4 | v271: two moments that used to jump now move (the owner: "smoothness is rule number one, but I do like animations"). Picked with the… |
| 25802–25811 | script | 1 | #432: Settings (#sheet) let a sideways swipe through to the game page behind it; the other sheets already stop it (noHub) |
| 25811–25824 | script | 1 | v273 (the owner: "return the background to a solid gradient color, I hate how it looks"): the live background (v257 lights, specks and drifting art;… |
| 25824–25832 | style | 1 | v274 (the owner, #447): one Nexi per screen. Unfolded, Home's own Nexi (the face beside your greeting) and the rail's seated Nexi showed at once:… |
| 25832–25845 | script | 1 | — |
| 25845–25853 | style | 0 | #201 (the friend, iPhone/iPad: "Restore a code is not working"): while a keyboard (or a zoom) hides the bottom of the screen, the open sheet sits on… |
| 25853–25908 | script | 4 | #201: Safari's keyboard doesn't shrink the page, it slides over it. Sheets (#diary) sit at the bottom of the screen, so a short one (Restore from a… |
| 25908–25915 | script | 1 | — |
| 25915–25928 | script | 1 | v276 (the owner: "black with a little blue gradient, mostly black, with the option to edit how much gradient and the background colour"): the… |
| 25928–25935 | script | 0 | — |
| 25935–25949 | style | 1 | v278 (#171, the friend: "a live preview when I'm editing the looks of all features, the same look everywhere, smart, off by default"): with Settings… |
| 25949–25982 | script | 3 | v278 (#171): when the sheet docks for Live preview. A screen's Customise always does; Settings itself only while one of the look groups is the one… |
| 25982–26007 | script | 2 | Nexi learned (#174, the friend, iPad v228: "Nexi disappeared while the rail was open"): with the rail open he sits at its foot (html.nxseat). A… |
| 26007–26029 | style | 2 | v278 (#130, the owner: "Radar needs a makeover, looks kinda ugly and doesn't match our theme"): Radar in the Clean style. Its words are sentence… |
| 26029–26054 | script | 2 | Nexi learned (#130, the owner: "Radar looks kinda ugly and doesn't match our theme"): Radar kept the old Arcade look (slanted, all-caps heading)… |
| 26054–26098 | script | 4 | v279 usage counts (the owner: "remove any junk or things that are not used"). Every tap and switch change counts once under the nearest name it has:… |
| 26098–26106 | script | 0 | v280's scroll hold (mvh*: every endless loop paused from the first move until the scroll stopped) was removed in v282 |
| 26106–26138 | style | 3 | v281 (#7) cards rising in by the browser itself (timeline-trigger) was removed in v284: in Chrome it left cards stuck invisible (More for you, the… |
| 26138–26287 | script | 11 | ===== v281 (#8): the bar on a game's page (styles above). Tapping the name scrolls back to the top. ===== |
| 26287–26307 | script | 2 | v282 (the owner: "App freezes a lot when I move or scroll and nexi animation and rail stops ... I dont want animation to stop while scrolling").… |
| 26307–26322 | script | 1 | v283 (#134 Month, #136 Insights, #416/#333/#367 switching to Library/Queue, all back after being closed): a Chromium trace of each scroll… |
| 26322–26366 | script | 3 | v284 (the owner, a screenshot: "Fix this empty space it should have 2 stuff"): Home's "More for you" was an empty band on the Fold. v281's reveal by… |
| 26366–26413 | script | 3 | v285 (#177, the friend: "all the sounds in the app are not working" on an iPhone 11 and an iPad 9th gen): on iPhone/iPad a web app's sounds follow… |
| 26413–26443 | script | 3 | Oct 2026 clean-up (step 3b, work nobody sees). 1. A CSS loop that was held (its screen hidden, or resting) and set going again with play() no longer… |
| 26443–26463 | style | 2 | ===== v287: fewer GPU layers (#60 Home, #62 game page, #207 Played, #116 blank on Queue). The phone's own timing of a Played stutter: "1 long frame,… |
| 26463–26470 | script | 0 | the shelf's covers get their own layers while it's swiped, and give them back 400ms after it stops |
| 26470–26567 | script | 8 | v287: Nexi spots GPU layers piling up on the screen you're on. The stutters that kept coming back (#60, #62, #207) were drawing, not code (the… |
| 26567–26602 | style | 3 | ===== v288: Nexi's tea break. The owner (after a Spy x Family ending where the family never gets to drink their tea): now and then, when the app… |
| 26602–26757 | script | 14 | ===== v288: Nexi's tea break (`teaTry`). Every few seconds `teaTick` looks whether the app has been still for 25s (no touch, scroll or screen… |
| 26757–26911 | script | 13 | ===== v290: Queue logos, look A (the owner picked it from four demos: "make A") ===== The row logo has its own upright slot inside the row (CSS at… |
| 26911–26952 | style | 4 | ===== v297: Settings rail, look B (the owner picked it from four demos: "B looks very sick"). Settings and every Customise are one scroll of open… |
| 26952–27172 | script | 25 | ===== v297: Settings rail (look B) and drawn group icons ===== GICON: one 24x24 drawing per Settings / Customise / Edit group and Most used… |
| 27172–27607 | style | 46 | ===== v299: the open Queue card, round 9 "Sweep" (the owner, after rounds 7-9: "Perfect"; they hated v298's fixed blue slab, the round stamp that… |
| 27607–27694 | script | 9 | — |
| 27694–27710 | script | 1 | v305 (#207 Played): every finished game's row had its scroll-linked motion (view() timelines) all the time, and Chromium gives each scroll-linked… |
| 27710–27771 | script | 4 | v305: Nexi learned two more kinds of drawing load (the stutters whose phone timing said "script 0, render 157ms"): 1. pictures drawn as url()… |
| 27771–27830 | script | 6 | — |
| 27830–28122 | script | 29 | ===== Oct 18 (the owner: "upgrade how you are testing the app and seeing glitches and ui problems … inside the app via nexi and you"; open idea… |
| 28122–28266 | style | 18 | ===== v317: the skills book (Taste › Book, ideas M41/M42; the owner picked card look A "Perks" and the round 2 book: "Very good apply it now"). A… |
| 28266–28727 | script | 56 | ===== v317: the skills book (Taste › Book). What each game you finished trained in you, as a book you leaf through. The words for each game are… |
