# Phase 0 map 3: the shared kit (web APP_V 327, index.html 28,868 lines)

Read-only map. Line numbers are index.html unless a file is named. "calm" everywhere = `calm()` (4537):
`html.still` (set when `S.motion === false`, 3834, or battery <= 15% and not charging unless `S.batOff`, `batApply` 20877)
OR `prefers-reduced-motion: reduce`. Android: Animations off / reduce-motion / battery saver (plan row 0).

---------------------------------------------------------------------------------------------------------------------
## 1. Sounds

### 1a. Engines and gates (there are FOUR separate engines, with different gates)

| Engine | Where | Gate | Notes |
|---|---|---|---|
| `audio()` shared context `AC` | 3878 | `S.sound` truthy AND `userTouched` (any pointerdown/touchstart/mousedown/click/keydown, 3874) | resumes if not running (iOS "interrupted") |
| `beep(kind)` UI ticks | 3897 (`blip` 3886) | via `audio()` | ~220 call sites |
| `sfx(kind, x)` effect recipes | 11646 | via `audio()` | master gain .55 |
| `pnBeep(f,d,type,v)` Nexi retro text blips | 12243 | `S.pnVoice !== "mute"` (and legacy `S.pnMute`), **not** S.sound | own context `pnAC`; default ON |
| FX pack `zing/boom/clang/fanfare` + day-card ticks | 13780-13892 (IIFE "FX pack" from 13754, its own `let AC` 13777) | **no S.sound gate at all** | see Risks |
| Opening reel soundtrack (OfflineAudioContext, 120 BPM, 15 s) | 3262-3292, started 8173 only `if (S.sound)` | Phase 3 (opening reel), not kit | |
| Book music `skbAC` | 28703 | excluded | |

Settings (group "Sound and reactions", `data-scope="app"`, 6535):
- **Sounds** `#s-sound` On/Off -> `S.sound` (default **undefined = off**; reset at 7273 keeps it). Turning on plays `beep("add")` (6996).
- "Play sounds in Silent mode" `S.silentPlay` (26370-26405): iPhone `navigator.audioSession` only; not ported.
- Reactions `S.reactions` (text lines, not sound), Grumble toggle. Nexi's blip/mute is a speaker button on his story/lore sheets (`pnSndWire` 12251) writing `S.pnVoice` ("blip" | "mute").
- The owner's phone has Sounds OFF (CLAUDE.md v321/v322).

### 1b. `beep(kind)` (3897) - recipe
Per note: oscillator `type`, freq constant; gain 0 at t, linear to `vol` at t+0.005, exponential to 0.0001 at t+dur; stop t+dur+0.02; connected straight to destination (no master). Next note starts at `t += dur * 0.72` (overlap).

| kind | notes [freq Hz, dur s, wave, vol] | used for (examples) |
|---|---|---|
| tap | [520, .045, triangle, .05] | most taps: chips, Settings picks, pill switch (11094), open/close Queue card row (8849), sheets |
| open | [420,.07,tri,.06] [630,.09,tri,.05] | openFull (9790), sheets/screens open, Home tiles -> tab, closed card opens without the v299 entrance (9003) |
| add | [523,.08,tri,.06] [659,.08,tri,.06] [880,.16,tri,.05] | game added/saved/logged, "back in the queue", Sounds switched on |
| swipe | [300,.06,sine,.04] [200,.07,sine,.03] | one-finger view swipe / two-finger tab swipe (11022/11029), hub page swipe (9962), drag reorder step (7455) |
| nudge | [760,.035,square,.025] | drag start (7426), move up/down buttons (9050), sort changes |
| back | [300,.06,triangle,.04] | close/collapse, skip duel, draw, delete |

### 1c. `sfx(kind, x)` (11646) - helpers, exact
- Master: `out = Gain(.55) -> destination`. `t = currentTime + .005`.
- Noise source: ONE 1-second mono white-noise buffer (`Math.random()*2-1`) at the context rate, made once (`pnNoise`), played from offset 0, not looped (so any noise longer than 1 s is cut at 1 s, e.g. bkopen's 1.3 s band).
- `env(node, v, at, dur, attack=.004)`: gain 0 at `at`, linear to `v` at `at+attack`, exponential to .0001 at `at+dur`.
- `tone(type, f0, f1, v, at, dur, attack)`: oscillator freq f0 at `at`, exponential to f1 at `at+dur`; env; stop `at+dur+.02`.
- `noise(ftype, f0, f1, Q, v, at, dur, attack)`: noise -> Biquad(ftype, Q) with cutoff f0 -> exp f1 over dur; env; stop `at+dur+.02`.

Recipes (times are offsets from t, seconds):
- **hit**: tone(sine 160->40, v .9, 0, dur .28); noise(highpass 2000->6000, Q .7, v .5, 0, .06).
- **slash**: noise(bandpass 600->5000, Q 1.2, v .9, 0, .28, attack .06).
- **tick**: tone(triangle 1500->1200, .25, 0, .06).
- **slam**: tone(sine 110->32, 1, 0, .5); noise(lowpass 1400->200, Q .8, .7, 0, .3); tone(square 220->110, .08, 0, .12).
- **crack**: noise(highpass 3500->5000, Q 1, .9, 0, .05); noise(highpass 3000->4500, Q 1, .7, .05, .05).
- **shatter**: noise(highpass 2500->7000, Q .6, .8, 0, .55, attack .002) + 12x tone(sine f0=2600+rand*3800 -> f1=2400+rand*2000, v .12, at rand*.35, dur .18). (random)
- **cheer**: triangle 523/659/784/1047 constant, v .22, at i*.08, dur .3.
- **qkopen** (open Queue card, v299; x = released game's stub tears): tone(sine 1100->260, .45, 0, .22, att .003); noise(lowpass 800->200, Q .5, .12, .02, .5, att .05); chord triangle 262/330/392/494, v .05, at .1, dur 1.4, att .35; plucks triangle [(.6,523),(.7,659),(.77,784),(.84,880),(.95,1047),(1.06,1319)] v .09 dur .28 att .003; if x: noise(bandpass 3200->1200, Q 1.4, .3, 1.4, .16, att .01) + tone(triangle 1568->1175, .08, 1.56, .2, att .003).
- **qkflip** (card turn; x = turning to back): noise(bandpass 1200->500, Q .8, .3, 0, .26, att .05); then triangle notes x ? [784,1047] : [1047,784], v .08, at .3+i*.06, dur .26, att .003.
- **qkshut**: triangle 659 then 494, v .07, at i*.06, dur .22, att .003.
- **qkback** (back of the card opens): noise(highpass 2500->3500, Q 1, .25, .2, .03, att .001); tone(sine 600->400, .12, .2, .05); 7 bubbles: f=380+rand*520, tone(sine f->1.9f, .08, .35+i*.06+rand*.03, .07, att .002); jingle square 523/659/784/1047, v .03, at .9+i*.06, dur .14, att .003.
- bkopen / bkpage / bkland: skills book (Phase 6), recipes at 11677-11684 if needed.

When sfx plays: `pnReveal` (hit, slash, tick, slam, crack, shatter: see section 4d), `celebrate` cheer (11799, a game marked played, after 150 ms; not when calm), qkopen (8852, 9003; only when the v299 entrance `.qkin` exists), qkflip + qkback (10786 `qkTurn`), qkshut (10800).

### 1d. `pnBeep` - Nexi blips (12243)
Oscillator type/f, gain set to v at t (no attack), exponential to .0001 at t+d, stop t+d. Defaults d .04, square, v .04.
- `pnChime` (12247): sine 880, 1175, 1568, d .4, v .06, started 90 ms apart (story/journal sheets open).
- Typewriter (12263): every 2nd non-space char, f = (low ? 170 : 520) + rand*60, d .035, low ? sawtooth : square (Grum's lines low).
- Page turn 12266: triangle 660, .08, v .05.
- Power-up (14078/14087): charge sawtooth 180/200/225/250 d .12 v .025 every 180 ms; burst square 440/560/700 d .08 v .03 every 70 ms.
- Cinematic Nexi vs Grumble (14378-14537): local `beep(f,d,t,v)` -> pnBeep. These belong with Nexi's phase.

### 1e. FX pack (13780-13892), own context, UNGATED by S.sound
- **zing** (tap on a duel pick `.duelpick`, 13798): j = .9 + rand*.2; master .5. Noise buffer .35 s -> bandpass Q 9, 7000j -> 600 exp over .22; gain 0 -> .9 linear by .02, exp -> .001 at .3. Partials sine 1250j x [1, 2.76, 5.4, 8.93]: start .07, gain 0 -> .12/(i+1) linear at .075, exp -> .0005 at .9 - i*.15, stop 1.0. Thump sine 140 -> 38 exp .07..25, gain .7 at .07 exp -> .001 at .3, stop .32.
- **boom** (duel "VS" stamp 620 ms after a new pair shows, 13817; also after fanfare/day card): master .55; triangle 110 -> 32 exp over .35, gain .9 exp -> .001 at .45, stop .5; squares 520/741/1190 gain .05/(i+1) exp -> .0005 at .5, stop .55.
- **clang** ("CLEAR!" when a game becomes played, onSave after-hook 13860): master .5; sine 90 -> 40 over .2, gain .8 -> .001 at .25; triangles 659/784/988/1319 start .12 + i*.07, gain 0 -> .12 in .01, exp -> .001 at +.6. (Note: celebrate's `cheer` fires for the same event, so both play.)
- **fanfare** ("OUT NOW" card, only if `S.pcdOff`, 13866): master .3 through lowpass 2600; notes (392@0, 523@.1, 659@.2, 784@.3, 1047@.42), each 2 sawtooths at f and f*1.005, gain 0 -> .35 in .02, exp -> .001 at len (.14, last note .9); boom at +420 ms.
- **Day card ticks** (`cal`, first open each day, 13891): 3 square 1800 Hz at .75 + [0, .07, .14], gain .06 exp -> .001 in .04; boom at +1050 ms.

---------------------------------------------------------------------------------------------------------------------
## 2. Haptics (navigator.vibrate, ms)

`S.buzz` ("Buzz as each cover lands", Library settings 6375, default on: `S.buzz !== false`) gates ONLY the three 9 ms swipe ticks. Every other buzz is ungated. iPhone shim 25704-25738 (not ported).

| Pattern | Where | Moment |
|---|---|---|
| 9 | 9224 `wireReel` (S.buzz) | Library swipe reel: each new cover centred |
| 9 | 9685 `wireLx` (S.buzz) | Library big-cover swipe step |
| 9 | 9963 `hubDragEnd` (S.buzz) | swipe to the next game page |
| 14 | 7427 `startDrag` | Queue row picked up for reordering |
| 8 | 10748 `flLift` | old flip card lift |
| 8 | 10787 `qkTurn` | Queue card turn (front <-> back) |
| 18 | 13134 `qDuel` | Quick duel: winner picked |
| [30,60,90] | 4551 `allOut` | big score reveal ("all out") |
| 40 | 4567 `rankUp` | RANK UP toast |
| 22 | 3543 `pnClash` | duel clash shake |
| [30,40,20] | 3549 `pnStampShow` | "PLAYED" stamp |
| [12,60,25] | 11703 `pnReveal` start | Pick-for-me hit |
| 40 | 11769 `pnReveal` crack | picture cracks |
| 14 / 6 | 15969 `openShuffle` | shuffler yes / no |
| 18 | 11964 `pnHold` | hold a Home stack (550 ms) |
| 18 | 12016 `pnBubble` hold 600 ms | Nexi menu by hold |
| 14 | 12025 `pnBubble` double tap | Nexi menu by double tap |
| 6 | 12470 `pnBubSay` | EVERY Nexi speech bubble |
| [60,40,30,40,30] | 14086 `pnPowerUp` | Nexi power-up burst |
| 35 | 14392 cinematic | hit in Nexi vs Grumble |
| 4 | 23101 `rmTick` | Game room spin, every 30 deg |
| 8 | 23199 `rmAct` | Game room object tap |

None on CLEAR!, duel-win in Taste duels, or the day card. Plan item 11 (Phase 0): "richer vibrations for the big moments (CLEAR!, a duel win, the card turn), never on every tap" = new Android patterns (suggest VibrationEffect.Composition primitives: CLICK/THUD/SPIN), keep the rest as the ms table.

---------------------------------------------------------------------------------------------------------------------
## 3. Header card ("shx", v194 + v210 extras) - 16672-17021

Scope: the `.tabbar` header of Queue, Library, Played, Replay, Plan (`p-cal`), Taste, Add (`SHX_PANELS` 16827). **Home is not one of them** (Home's top is its own `dxHome` hero, Phase 3). Only in Clean style (`html.ui2` = `S.uiStyle !== "arcade"`, 3846).

Settings (`shxSetHtml` 16941, in "Header cards" and every screen's Customise):
- Mode chips `#s-shx`: **Full** -> `S.shxX = all 6`; **Simple** -> `S.shxX = ["fan"]`; **Off** -> `S.shxOff = true` (plain title). Mode shows unlit if the list is a custom mix.
- Extras chips `#s-shxx` (`SHX_X` 16862), order fixed: fan "Covers", line "Spotlight line", tags "Top genres", chart "6-month chart", bar "Mix bar", art "Backdrop art". Tapping toggles one and clears Off.
- Defaults: `S.shxX` undefined -> all six (`shxList` 16866); `S.shxOff` undefined -> on.
- Numbers ("stats") always show.

Redraw: after each screen render (wrapped window functions 16998-17002; renderHome -> Add), on applyLook. Cache key = stats + cover ids/img + extras HTML; no redraw if same.

### 3a. Stats + covers per screen (`shxData` 16829). `c` = up to 4 covers; `st` = [value, label]
- queue: covers `onDeck(4)`; [waiting count, "waiting"], [round(sum hrsOf(waiting)) + "h", "to beat"], [waiting not upcoming, "out now"]. waiting = not played, not dropped.
- lib: 4 most recently `added`; [games, "games"], [played records, "played"], [not played & upcoming, "coming"].
- played: `ranked()` top 4 (rankTable order, else played order); [played, "finished"], [scores >= 9 in rankTable.sc, "S-tier"], [mean of scored `real` 1 dp or "–", "average score"].
- replay: played with `again` (else ranked) top 4; [again count, "to replay"], [played, "finished"].
- plan: next dated unplayed games from today, by date; [days to first ("Today" / Nd / "–"), "to <title>" / "nothing dated"], [count within 31 days, "in 30 days"].
- taste: ranked top 4; [rankTable.order length, "ranked"], [duels, "duels"], [S-tier count].
- add: recent 4; [games, "in your library"], [added this month, "added this month"].
First stat value is in volt colour with a glow.

### 3b. Extras (`shxExtra` 16867)
- **line (spotlight)**: a button opening the game (`data-opg`). queue: `onDeck(1)[0]`, kicker "Up next", tail "playing" / "out <d Mon>" / "coming" / "out now". lib/add: newest added, "Newest"/"Last added", "added today / yesterday / N days ago / on <d Mon>". played/taste: ranked #1, "Your #1", "<score>/10" (taste: "<n> duels"). replay: first `again` (else #1), "Replay next"/"Your #1". plan: next release, "Next out", "<d Mon>".
- **tags**: genres counted over the screen's pool (queue: waiting; lib/add: all; played/taste: played games; replay: again or played; plan: upcoming); keep genres with count > 1, top 3 by count; chip shows name + count.
- **chart (6 months)**: counts per calendar month for 6 months ending this month (plan: this month + 5 ahead). queue/lib/add: `added` dates, caption "added"; played/replay: played `t`, "finished"; taste: duel `t`, "duels"; plan: release dates, "releases ahead". Hidden when all zero. Bars scaleY = max(.08, v/max); the LAST highest bar is volt, others ice 22%. Month labels computed (narrow) but only in the title tooltip.
- **bar (mix)**: segments flex by count, zero segments dropped, hidden if all zero. queue: playing / out now / coming. lib: played / playing / waiting / coming. played & taste: S / A / B / C / unscored (tiers: S >= 9, A >= 7.5, B >= 6, else C; 13034). replay: to replay / once was enough. plan: this week (< 7 d) / this month (< 31 d) / later. add: none. Colours data-c 0..4: volt, volt 55%, volt 28%, ice 30%, ice 13%. Legend line below with dots.
- **art (backdrop)**: `bgSource(front cover game)` else line game's bgSource or cover. Right 72% of the card (64% at >= 620), img opacity .4, gradients from the panel colour (90deg 0%/55% at 40%/transparent 75%; bottom 70% -> transparent 55%). Glow drops to .55.

### 3c. Look (exact)
Card (16676): radius 26, border 1px white 5%, padding 16 (>= 620: 22/24), min-height 138 (>= 620: 172; with chart < 620: 190), margin-bottom 16, background linear-gradient(120deg, --u-pan 38%, mix(--shc 16%, --u-pan)), shadow 0 26 50 -30 black 95%. `--shc` = first cover's `colour` if hex, else volt. Glow: circle 78% wide at right -18% / top -55%, radial(mix --shc 42%) to transparent 66%. Title 28px (34). Stats Poppins 700 23px/1 (32), gap 14 (28), labels 11px (12) mute.
Fan (16697): `--fw` 124 / `--cw` 60 (>= 620: 250 / 90, right 150, height 150, bottom -22); right 12, bottom -16, height 100. Positions `POS[i] = [k, rot, y]`: [3, 14deg, 10px], [2, 4deg, 1px], [1, -6deg, 4px], [0, -15deg, 14px]; first (most relevant) cover in front on the right (z 9-i), left = (fw - cw) * k / 3; < 620 the 4th is hidden and left = (fw - cw)(k-1)/2. Covers 3:4, radius 12 (14), shadow 0 12 24 black 60% + 1px white 9% ring, transform-origin 50% 110%. Tap = open game.
Motion (only when that screen's cover set changed, `SHX_SEEN`): deal = from opacity 0, translate(40px, 50px) rotate(r + 16deg), .55 s cubic-bezier(.22,1,.36,1), delay i*70 ms; chart bars scaleY 0 -> 1, .6 s same ease, delay 120 + i*50 ms. None when calm.

---------------------------------------------------------------------------------------------------------------------
## 4. View pill, cover-fly, share sheet, Pick for me

### 4a. View pill (`renderPill` 11049, `slidePillNow` reassigned at 24728)
- `S.pill` (default on, `!== false`); Settings › (nav group) "Floating switch" `#s-pill` Show it / Hide it (6831). Off: `subChips(tab)` row of chips in the header instead (3987).
- Shows on tabs with views (`SUBS`): Games = Queue, Library, Played, Replay; Plan = Month, Timeline, Plan (+ Timer when `S.timerOn`, 12923); Taste = Insights, Duels, Friend, Book (18463, 28816). Remembered per tab in `S.gamesView` / `S.calView` / `S.tasteView` (defaults queue / month / insights, 18464).
- Look (1054): fixed, centred, bottom 18, z 40, padding 6, gap 4, radius 999, background gradient(180deg, rgba(150,180,255,.14), rgba(10,15,30,.46)) over rgba(10,15,30,.25), backdrop blur 12 + saturate 1.3, border 1px ice 16%, shadow 0 16 38 black 50% + inset top highlight; top 30% sheen. Buttons 11px weight 900 uppercase, letter-spacing .04em, padding 9x15, colour mute, selected = --void on the volt slide. With the rail: shifted by half the rail width. > 3 items: `.wide`, scrolls sideways, max-width min(92vw, 560px).
- Motion: highlight jumps to the new size, then animates translateX(old x) scaleX(old w / new w) -> translateX(x), 280 ms cubic-bezier(.4,0,.2,1) (not when calm). First draw has no animation.
- Tap: `goSub` + beep tap. Also: one-finger horizontal swipe (|dx| >= 70, |dy| <= 45, not in a side scroller/the book) walks the views; two fingers walk the tabs (11003-11029), beep swipe.

### 4b. Cover-fly + page grow (game page open)
- Grow (11820-11849, wraps openFull/closeFull): open = `#full` scale .93 & opacity .35 -> none & 1, 300 ms, `PN_EASE` cubic-bezier(.22,1,.36,1), origin = last pointerdown point. Close = none -> scale .9 & opacity 0, 220 ms cubic-bezier(.4,0,1,1), then really closes. Skipped when calm, page already open, or from the reveal (`pnNoGrow`).
- Cover-fly (v225, 21234-21296): on a "game" nav, if the tap (within 1.5 s) was on something holding a cover-shaped image (aspect <= 1.25, side >= 28 px, < 40% of the screen, visible; the one under the finger first, else the biggest), a fixed copy flies from its rect to the page's `.gd-cov`: keyframes translate(dx,dy) scale(max(srcW/w, srcH/h)) rotate 0 -> at 72%: translate 0 scale 1.06 rotate -6deg -> scale 1 rotate -4deg; 380 ms cubic-bezier(.3,.9,.35,1); radius 16, shadow + 2px white ring; the real cover hidden until it lands (max 1.2 s). Skipped: calm, `S.hubAnim === false` (Customise › A game's own page › Motion on this page), the tour, a page-to-page swipe, nothing with a cover tapped. Closing removes it.
- The older view-transition morph is retired (8732); `S.vt` (Settings "Screen transitions" Morph/Instant, 6752) now only gates the Queue card entrance (9001).
- Android: shared-element transition from the tapped cover to the page cover; the -4deg resting tilt is the page cover's own look.

### 4c. Share sheet (navigator.share callers)
| Where | What is shared | Fallback |
|---|---|---|
| 8066 Taste › Friend "send code" | text: "My Play Next taste code. Open Taste, then Friend, and paste this whole message:\n\n" + code | copy |
| 8338 `callingCard` (game page menu "Calling card", 9489) | PNG file of a drawn card (seeded random by title; Reshuffle) | download |
| 12815 `wrCard` (year wrapped / month recap) | PNG 1080x1920 | download |
| 13850 `shareTop5` (Duels "Share my top 5" `#fx-share`) | PNG 1080x1350 "playnext-top5-<ts>.png" | download |
| 14894 Played board (`pnShareBoard`, `[data-pnsend]`) | PNG "play-next-top.png", title "My top games" | download |
| 19167 `cbSaveCode` restore code | text "My Play next restore code: <code>\nTo get my games back: Settings → Cloud backup → Restore from a code." (title "Play next restore code"), only after the Password Manager store fails | |
Images are drawn on canvas; each card's drawing is its owning screen's job; the kit only needs `shareText()` / `shareImage(bitmap, name, title)` with the same fallbacks (Android: ACTION_SEND via FileProvider; AbortError = user cancelled, ignore).
Share TARGET (into the app): manifest `share_target` GET ?title&text&url -> `goTab("add"); shOpen(q)` after 1.6 s (22528), URL cleaned. Android: intent filter ACTION_SEND text/plain -> Add.

### 4d. Pick for me / What should I play?
Three different paths:
1. **Home dice button** "I'll pick one" (`data-pickone`, 11138, only when > 1 live game; or `#hintbub`), handler 11507: `ranked` = live games (not played/dropped) sorted by On deck score desc (games not in the deck = -1), then Elo desc (`eloTable`, 1500 start, K 24, slight edge = .75), then hype desc. Remove `S.hintLast`; pick UNIFORMLY at random among the first min(3, n); fallback the top pick. Save `S.hintLast = id`; run `pnReveal(g)`.
2. **Nexi's menu "Pick for me"** (`pnBubPick` 12114): Nexi mood "roll" 1.3 s, then `nxPickShow`: candidates (`nxCands` 12110) = not played, not skipped this session, and (backlog OR playing OR date <= today); score = rank index in `pnLastRanked` (the rail's ranked list; 50 if absent) + 2 x `S.nxSkip[id]` - `S.nxYes[id]` - (2 if fresh: backlog, out within 14 days); lowest wins, NO randomness. Card: "<NEXI> PICKS", cover, title, why ("#R in your ranking · out now · Nh to beat · skipped N×"); "Nah, next" -> skip +1 (at 3: Nexi suggests moving it down), next candidate; "Let's go" -> yes +1, +1 XP, open the game (grow from Nexi). Nothing -> sad line.
3. **App shortcut "What should I play?"** (`?nudge=next`, 13740): after 1.8 s opens `onDeck(1)[0]`'s page directly (no reveal). Same for push "Tonight's pick", "weekly", "comeback" without a game.

On deck score (`onDeckScores` 13260; live = not played, not dropped, not competitive): 0.45 x genre similarity to ranked favourites (weighted Jaccard; weight max(.15, 1 - i/max(4, n)) x score/10; normalised by the max) + 0.20 x normalised Elo (.5 if all equal) + 0.20 x on current platform (`S.curPlat`, default "Switch"; "any" matches all) + 0.10 x hype/10 (default 5) + 0.08 if playing + **1.0 if out** (backlog, playing, fresh, or date <= today).

**`pnReveal(g)`** (11692; calm -> just open the page): full-screen `#pnrev` z 90, game colour `--c` (default #4DA3FF), art = bgSource or cover.
- 0 ms: sfx hit, vibrate [12,60,25]; black bg fades in 120 ms; white flash 300 ms (opaque to 35%); colour slash .5 s cubic-bezier(.7,0,.2,1) delay .1 s and white slash .45 s delay .18 s, both rotated -12deg sliding in, gone at .6/.65 s; art scale 1.35 rotate -3deg -> 1, .9 s cubic-bezier(.16,1,.3,1) delay .22 s, clipped polygon(0 14%, 100% 0, 100% 86%, 0 100%), dark bottom gradient; "TONIGHT'S PICK" tag (white, skew -12deg) from +80 px at .5 s; title (Archivo italic 900, uppercase, clamp(34px, 9vw, 84px), 6px colour shadow) from -60 px skew -14deg at .58 s; colour bar 70% wide scaleX at .7 s.
- 110 ms sfx slash; 500 ms tick; 600 ms slam + shake (.25 s, translate 6/-4, -5/3, 3/2).
- 950 ms: game page opened underneath (no grow). 1150 ms: shards pre-drawn (8 triangles from a random centre C = (48..56%, 44..54%) to 8 edge points, edge midpoints jittered 35-65% / 30-70%).
- 1550 ms (or a tap): crack lines flash 160 ms + sfx crack + vibrate 40; +110 ms: sfx shatter, white blink .9 -> 0 in 180 ms, each shard flies outward along centre->shard direction for D x (.7..1.1) (D = max screen side), rotate random +-40deg, scale 1.1, opacity -> 0, 650..800 ms cubic-bezier(.45,0,.7,.2); overlay removed at +850 ms.
- Android: one Canvas/Compose overlay; shards can be clip paths of one bitmap.

---------------------------------------------------------------------------------------------------------------------
## 5. Deep links / routes

No hash routing, no pushState: the web app always starts at Home (tab/view restored from S) and reads `?nudge=` once, then `history.replaceState` cleans the URL. Handlers fire after ~1.8-2.2 s.

| URL | Action | Source line |
|---|---|---|
| `?nudge=add` | Add tab | 23719 (shortcut) |
| `?nudge=queue` | Games › Queue | 23719 (shortcut) |
| `?nudge=next` | open On deck #1's game page | 13740 (shortcut, Tonight's pick, weekly, comeback) |
| `?nudge=duel` | Quick duel | 13740 |
| `?nudge=badge` | Played tab | 13740 |
| `?nudge=verdict` | Quick score picker `qScorePick` | 13742 |
| `?nudge=release&g=<id>` / `game&g=<id>` | open that game | 13741 (release, countdown, playing, memory, comeback, price "plus", calendar feed) |
| `?nudge=recap&m=YYYY-MM` | open monthly Wrapped | 13743 |
| `?nudge=deals` | price deals sheet `pxDeals` | 19786 (helper price push) |
| `?nudge=finish&g=<id>` | played ? score it : "Did you finish?" ask | 20385 |
| `?nudge=reviews&g=<id>` | game page + reviews watch | 20552 |
| `?nudge=steam&g=<id>` | Steam sync, open game + its newest session | 26260 (helper cron push) |
| `?nudge=update`, `?nudge=tip`, others | just opens (counts the open; tour runs on its own) | |
| `?title=&text=&url=` | share target -> Add with the shared text | 22528 |
Every `?nudge=` open is counted `uCount("o","nudge")`.

Manifest shortcuts (manifest.webmanifest) to copy as Android static shortcuts (icons sc-add.png / sc-pick.png / sc-queue.png, 96px):
1. "Add a game" (short "Add") -> nudge=add
2. "What should I play?" (short "Play next") -> nudge=next
3. "Queue" -> nudge=queue
Suggested Android scheme: one `playnext://open?nudge=...&g=...&m=...` intent (and the https github.io URLs as verified App Links if wanted) handled by one router with the table above; notifications (Phase 9) and widgets (Phase 3) reuse it.

---------------------------------------------------------------------------------------------------------------------
## 6. Problem reports ("Report a problem")

Client: `bugReport(kind, msg, x)` 17251, queue flushed by `bugFlush` 17270. One extend (25373: self-test skip, web only).
- Entry: Settings › This build button `[data-bugreport]` (6895) -> `bugAsk` sheet (17451): textarea (max 800), "Show me where" (`bugPoint`/`bugSnap`: tap point, element path, rect, scroll vs client size, styles, clipping parents, picture load state, never text), privacy note (public GitHub), Send. Also Nexi's menu.
- Auto kinds: error, promise, layout, look, slow, stuck, helper, tour, music, review; user = "user". `S.bugOff === true` silences all but user.
- Dedup: `sig` = FNV-1a 32-bit hex (8 chars; offset 0x811c9dc5, prime 0x01000193) of `kind|msg with digits -> #, first 240 chars|sigAt (at minus ":line:col")` + `|screen` for layout/slow + `|Date.now()` for user. Once per sig per day (`S.bugSent[sig] = YYYY-MM-DD`, max 150 kept).
- Scrubber `bugScrub`: cut to 2000 chars; every game title and list name (> 2 chars, longest first) -> "‹game›"; RAWG key -> "…"; `?key=|token=|sig=|id=` values -> "…"; blob: and long data: URLs shortened.
- Flush: POST `HELPER + "/bug"`, JSON `{ reports: [ up to 10 ] }`, keepalive; 3 s after an auto report, 50 ms after a user one; retry on online/pagehide; failed batch requeued (cap 30). User sees "Sent. Thanks!" or "Saved. It'll send when you're back online".

Report object fields (17262):
```
{ sig, kind, msg, stack (first 8 lines, scrubbed), at, screen, note (user's full text, scrubbed),
  w, h (viewport css px), dpr (2 dp), dev ("Android Chrome 141 (installed)" style), v (APP_V),
  style (S.uiStyle || "clean"), crumbs (last 10 "HH:MM:SS tap [data-x] on <screen>"), lib ("N games (a out, b coming), L lists, U uploaded pictures (KB), longest title C chars") }
```
User report: msg = first 200 chars of the text, note = full text, at = pointed path, stack = the pointed snapshot.
Screen names (`bugScreen`): "game page (new|showcase|plain)", "settings/edit", or "tab/view" e.g. "games/queue".

Helper (worker/helper.js 178, `bugTake` 1283): Origin must be in ALLOW (`https://shamlawy.github.io`, `http://localhost:8765`) OR absent. Drops reports whose sig isn't 8 lowercase hex. **Keeps only a fixed field list** and cuts: kind 16, msg 600, stack 1500, at 200, screen 60, w/h/dpr numbers, dev 40, v 8, style 12, note 800, crumbs 10x60, lib 200; stored in KV `bug:<sig>` (n, vs, devs, screens). An extra `app` field is silently dropped today.

---------------------------------------------------------------------------------------------------------------------
## Kotlin port checklist
- [ ] `Sfx` engine: one AudioTrack/Oboe synth with oscillator (sine/triangle/square/saw), exponential freq ramps, attack-linear/exp-decay envelope, white-noise buffer through biquad (low/high/bandpass with Q); master .55. Port `beep` 6 kinds, `sfx` hit/slash/tick/slam/crack/shatter/cheer/qkopen/qkflip/qkshut/qkback, Nexi `pnBeep` set, FX zing/boom/clang/fanfare/day ticks. Pre-render each recipe to a PCM buffer at start (SoundPool) so taps cost nothing. Golden test: render the web recipe in OfflineAudioContext and compare envelopes.
- [ ] Gates: S.sound (default off) for beep/sfx; S.pnVoice for Nexi blips; decide FX pack gate (see Risks). Duck/stop for calls (plan).
- [ ] `Haptics` helper with the ms table; `S.buzz` gates only the 3 swipe ticks; add the new big-moment patterns (CLEAR!, duel win, card turn).
- [ ] `HeaderCard(kind)` composable: stats per screen, fan of up to 4 covers (3 under 600dp), 5 extras with S.shxX / S.shxOff, deal + chart grow animations only on cover-set change, colour glow from the first cover, Clean style only.
- [ ] `ViewPill`: per-tab view sets incl. Timer and Book, S.pill, fallback chip row, slide 280 ms, swipe walking views (1 finger) / tabs (2 fingers).
- [ ] Game page open: grow from tap point (300 ms / 220 ms close) + shared-element cover fly (380 ms, ends at -4deg), off with calm or S.hubAnim false.
- [ ] `calm` = S.motion false || system reduce-motion/animator scale 0 || power saver/battery <= 15% (unless S.batOff).
- [ ] Share helpers: text and PNG via FileProvider, AbortError-equivalent ignored, fallback copy/save; share target -> Add.
- [ ] Pick for me: Home dice (top-3 random excluding S.hintLast) + reveal timeline; Nexi deterministic picker with S.nxSkip/S.nxYes; onDeck scoring formula; rankTable / eloTable shared with later phases.
- [ ] Deep-link router for every `nudge` value + static shortcuts Add / What should I play? / Queue.
- [ ] Problem reports: same object, same FNV sig + once-per-day map, same scrubber (planted-title test), batch of 10 to /bug with `Origin: https://shamlawy.github.io`; add `app: "android"` (needs helper change) and app versionName in `v` (<= 8 chars).

## Risks / unknowns
1. **`app: "android"` is dropped by the helper**: `bugTake` copies a fixed field list. Phase 0 needs a helper change (keep `app`, cut 12) + HELPER_V bump + bugsync label `android-bug`, or the app must mark itself in `dev` (40 chars, e.g. "Android app 0.1 Fold") and `v`. `v` is cut to 8 chars.
2. **FX pack sounds ignore Sounds**: zing (duel pick), boom (duel VS, day card), clang (CLEAR!), fanfare (OUT NOW), day-card ticks have no S.sound check (13780-13892). With Sounds off the owner may still hear them (or not, if the context never resumes before a tap). Ask the owner whether the app should gate them on Sounds (recommended) or keep the web's behaviour.
3. CLEAR! plays clang AND celebrate's cheer at once (two engines).
4. The plan says the shortcut "needs" the Pick for me reveal, but the web's `?nudge=next` opens On deck #1 directly, no reveal and no randomness. Brief question: keep web behaviour or run the reveal.
5. Three different "pick" algorithms (Home: random top-3 by deck/Elo/hype; Nexi: deterministic rank + skips; shortcut: On deck #1). Port all three exactly or ask to unify.
6. `sfx` noise buffer is 1 s and not looped: bkopen's 1.3 s band is cut at 1 s in the web. Reproduce the cut or not (book phase).
7. Randomness (`Math.random`) in shatter/qkback/zing pitches and reveal shards: the port can't match sample-exact; use the same distributions.
8. `pnBubSay` buzzes 6 ms on every Nexi bubble; the plan's "never on every tap" might want this softened. Ask.
9. Home has no shx header card; the Brief's "Home/Queue" wording should read "Queue and the other five screens + Add"; Home's hero belongs to Phase 3.
10. Taste's default view is "insights" (`SUBDEF.taste` reset at 18464; the original "psych" set was replaced at 18463).
11. `slidePillNow` is reassigned at 24728 (breaks the house "extend, don't reassign" rule); behaviour mapped is the v261 one.
12. Opening reel soundtrack (3262) and Nexi blips are mapped only at the level needed here; their phases own them.
