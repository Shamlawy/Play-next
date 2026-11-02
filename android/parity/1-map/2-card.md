Mapped at web APP_V 327 (index.html, Nov 2 2026). Area: Phase 1 piece 2 (the Queue's open card), piece 3's Game Shuffler deck and Nexi's seat. Read-only map: nothing in the app was changed. Line numbers are index.html at APP_V 327; "css" = the last `<style>` block (27177-27380) unless a line is given.

# 0. Port traps (read first)

The three things most likely to be ported wrong:
1. **The card is two plates that share one grid cell, and the double tap rotates both the same way (+180 deg each turn).** Front 0 to 180, back -180 to 0 going to the back; front 180 to 360, back 0 to 180 coming back. 420 ms, `cubic-bezier(.3,0,.15,1)`, perspective 1600px. Never a swing past and back (v306). Taps are ignored while it turns (`dataset.busy`); the plan's "interruptible" rule is a decision to make, not what the web does.
2. **One tap closes the card only after waiting 320 ms for a second tap, and the same tap logic runs on the BACK too** (a non-button tap anywhere on the back closes the card; two quick taps turn it back). The gap is timed from the finger-lift time stamp, not from when the app handled the event (v307). A tap on any button (box art, Edit, ⋯) cancels a pending close.
3. **Dates and numbers are not what the rest of the Android code writes.** The card's dates are en-GB, day first ("12 Mar", "12 Mar 27", stub "12 Mar" over "2027"), but `data/Times.kt` writes US "Mar 12". The day count is `ceil((date at UTC midnight - now) / 86400000)`, not local-midnight days. The back's "evenings" line uses `S.evening || 2` while the rest of the app uses 4 as the default. The card's headline words differ per state (table in section 1.3); copy them verbatim.

Also: the web's Game Shuffler is still the v181 look (uppercase serif title, emoji, tilted cover and side cards), which breaks house rules set later (section 5.9). It needs an owner call before Android copies it. **Decided Nov 2** (android/parity/1-brief.md): the deck is drawn in the open Queue card's look, same swiping.

# 1. The open card's front

## 1.1 Structure and where it lives

| Thing | Where |
|---|---|
| `renderQueue` -> `big(g, n)` builds `.hero[data-row][--accent = g.colour] > .hflip.qkf[.on] > (.plate.slab.pfront.qk[data-flip] = qkFront) + (.plate.slab.pback.qb[data-unflip] = qkBack)` | 8892-8899 |
| `qkStat(g, cd, score, n)`: every state's words and numbers | 8605-8620 |
| `qkFront(g, n, cd, score, morph)` | 8621-8639 |
| `qkBack`, `qbAreas`, `qbVessel`, `QB_IC` icon paths | 8639-8756 (QB_IC 8639, qbAreas 8646, QB_PAD 8657, qbVessel 8659, qkBack 8665) |
| `countdown(g)`, `myScore(g)` (closures inside renderQueue) | 8870-8890 |
| CSS front | 27177-27282; back 27283-27358 |
| Wrapped/extended? | `node tools/where.mjs qkFront` shows only the definition: nothing hooks it. `renderQueue` is extended (open-card size watcher 22117-22131, glide/qKeep, row logos); not the card markup. |

`myScore(g)` = round(mean of `g.mine[k]` over the 8 areas that are > 0, times 10); 0 when none are scored. `n` = the card's place in the sorted live queue (1-based). `cd = countdown(g)` = no date: `{v:"TBA", l:"no date yet"}`; `n = ceil((new Date(g.date) - new Date())/86400000)` (a date-only string parses as UTC midnight); n > 0: `{v:n, l: n==1 ? "day away" : "days away"}`; else `{v:"OUT", l: n==0 ? "out today" : "available now"}`.

## 1.2 What the front shows, top to bottom (in z order)

| Layer | Detail |
|---|---|
| Card box | `.hero .plate.qk`: `min-height: 340px` (folded; LOG v298 said 324, the CSS is 340 since v299), from 620 px wide `.hero .hflip` min-height 555px (v235: every open card is one height; the back never sizes it, it scrolls inside: `.pback {height:0; min-height:100%; overflow-y:auto}`). Radius `var(--u-r)` = 20px. No border; inset 1px hairline `rgba(234,242,255,.08)` (`--u-line`). Background `--void` #04060E. `.hero` margin-bottom 26px. |
| Picture `.qkbg` | fills the whole card, `background-size: cover`, `background-color: --ink` #080F24. Source order: `g.hero`, else Steam `library_hero.jpg` (if `g.steamId`), else `g.bg`, else `cover(g)` (the box art). Never `g.art` (the logo blown up is "just big letters"). None at all when Settings › Queue cards `S.cardBg === "plain"` or `g.bgOff`. Position `g.bgX`% `g.bgY`% (defaults 100 and 50: right-aligned). |
| Scrim `.qkbg::after` | `radial-gradient(100% 75% at 0% 0%, rgba(4,6,14,.78), rgba(4,6,14,.35) 50%, rgba(4,6,14,0) 78%)` over `linear-gradient(0deg, rgba(4,6,14,.94) 0%, rgba(4,6,14,.6) 26%, rgba(4,6,14,0) 52%)`. |
| No picture (`.qkbg.none`) | `radial-gradient(120% 90% at 85% 0%, color-mix(srgb, accent 32%, transparent), transparent 62%), --ink`. |
| Strap `.qkstrap` | left 0, top 36px, width 72%, height 108px, radius `0 24px 24px 0`, clipped, `transform: skewY(-6deg)` with origin `0 50%` (its right end sits ~31px higher than its left at 412 wide). Fill `linear-gradient(100deg, mix(accent 55%, rgb(5,7,15)), mix(accent 76%, rgb(5,7,15)) 55%, accent)` (srgb mix; so only the left part is darkened, the right end is the full game colour), shadow `0 14px 40px color-mix(accent 40%, transparent)`. `accent` = `g.colour` (hex #rrggbb from `PALETTE`, set per game); there is NO contrast guard for pale colours (e.g. #FFE066) on the web: white words just sit on it with a faint text shadow. Copy exactly; raise as a question only if it looks wrong. |
| Box art `.qkcov` | straight (no skew, no tilt), left 18, top 18, width 92 (so 92 x 138), z 2, a `button[data-hub]`. `img`: aspect 2/3, `object-fit: cover` + `object-position` from `g.imgX/imgY` (only when not 50/50), radius 12, shadow `0 14px 30px rgba(0,0,0,.5)` + ring `0 0 0 1px rgba(255,255,255,.14)`. `cover(g)` = `g.img` or the drawn placeholder. |
| Info column `.qkinfo` | straight text over the skewed strap. left 124, top 26, width `calc(72% - 136px)`, grid, gap 4, `justify-items:start`, no pointer events, every child `text-shadow: 0 1px 3px rgba(0,0,0,.35)`. Four children (the entrance delays count them): kicker, headline, platforms, genres. |
| Kicker `.qkkick` | `600 11.5px/1.2 Poppins`, `rgba(255,255,255,.8)`, one line with ellipsis. A 7x7 dot (colour `--qsc` per state) with a 3px halo `color-mix(qsc 30%, transparent)`, gap 7, then `"{word} . {spot}"` (a middle dot): e.g. "Out now . Next up", "Coming . 3rd in queue". Spot = `n===1 ? "Next up" : ord(n)+" in queue"` (the back says "in the queue"). |
| Headline `.qkhead` | `700 18px/1.1 Poppins`, letter-spacing -.02em, white, one line + ellipsis. |
| Platforms `.qkpl` | margin-top 3. `platHtml(g.platforms, "qkp")`: one flex-wrapping row `.pfms` (gap 9px 8px, wraps to a 2nd line INSIDE the strap, v306). Each system = a 16x16 logo (`PLAT_LOGO` SVG paths at 7796, `fill: currentColor` white, `drop-shadow(0 1px 2px rgba(0,0,0,.35))`) in a flex box 16px tall; its version sits in the same box after the logo (gap 1px), `700 9px/1 Poppins`, white, raised by `margin-top:-5px` (an exponent). "4·Vita", "One·360": versions of one family are sorted by `parseFloat` (non-numbers last), joined with " . " and the spaces removed. iOS draws the word "iOS" (`700 12px`, margin-top 3). Order fixed: `PLAT_ORDER` = ps, sw, xb, pc, dk, ap, io, an, lx. Unrecognised systems (Wii, 3DS...) are plain text `600 12px/18px`, margin-left 10, at most 2, after the logos. Family matching is `platOf()` (7798); names split on commas (`platList`). |
| Genres `.qkgn` | first 2 of `g.genres` (comma list) joined " . ", `600 12px/1.3`, `rgba(255,255,255,.82)`, one line + ellipsis. Omitted when empty. |
| Ticket `.qktk` | left 16, bottom 16, height 60, `filter: drop-shadow(0 10px 20px rgba(0,0,0,.45))`. Two grid halves, padding 0 12 each. **Stub** (left): radius `12 0 0 12`, `linear-gradient(160deg, accent, mix(accent 68%, rgb(5,7,15)))`, a half-circle notch r=7 cut out of its right edge at the top and bottom corners (`mask`), number white with `text-shadow 0 1px 6px rgba(0,0,0,.35)`, label `rgba(255,255,255,.85)`. **Body** (right): radius `0 12 12 0`, fill `mix(accent 30%, rgb(11,15,30))`, `border-left: 2px dashed mix(accent 55%, rgba(234,242,255,.4))` (the perforation), mirrored notches on its left edge, number `--ice`, label `rgba(234,242,255,.72)`. Number `700 20px/1 Poppins`, -.03em, tabular; label `600 10.5px/1.2`, margin-top 3. **Released game (`snap`)**: the stub hangs torn: `transform-origin 0 100%; translate(-5px,4px) rotate(8deg)`, body `translateX(4px)`. A coming game's ticket stays whole. |
| Logo `.qkt` | fixed slot right 16, bottom 16, width 40%, height 60, content aligned bottom-right, `min-width:0`. A soft dark halo behind it (`::before`, inset -60px -40px -40px -50px, `radial-gradient(closest-side, rgba(4,6,14,.72), transparent)`). `img`: `max-width/height 100%`, `object-fit: contain; object-position: right bottom`, `drop-shadow(0 2px 12px rgba(0,0,0,.55))`. A near-black logo is drawn light: `filter: brightness(0) invert(.9)` (+ the shadow). "Near-black" = `LF[g.art][5]` is the logo's mean luminance (`0.2126R+0.7152G+0.0722B` over pixels with alpha > 128, 0-255, measured once by `lfMeasure`, 20961, cached in localStorage `gq:logofit`; -1 unknown) and `0 <= lum < 50`. No `g.art`: the game's title as `h2` (`700 20px/1.08 Poppins`, right-aligned, `--ice`, 2 lines max, `text-shadow 0 2px 14px rgba(0,0,0,.7)`). |

Wide (>= 620 px, unfolded): strap top 56 / width 54% / height 176 / radius `0 30 30 0`; box art left 30, top 30, width 150; info left 214, top 64, width `calc(54% - 226px)`, gap 8, kicker 13, headline 26, genres 14, platform logos 22px (gap 10px 13px, version 10px, raised -6); ticket left 28, bottom 26, height 88, padding 0 20, number 32, label 12.5; logo right 28, bottom 26, width 40% (max 340), height 100, title h2 30px. Narrow (<= 390 px): ticket left 12, bottom 14, height 54, padding 0 9, number 17, label 10; logo right 12, bottom 14, width 36%, height 52. The 620 px and 390 px breakpoints are CSS px; Phase 0's calibration (docs 0-map/2-look.md) decides their dp equivalents.

Per-state colour `--qsc` (kicker dot): coming `--ice`, out rgb(79,209,139), playing rgb(140,196,255), played rgb(243,210,122), dropped `--mute` #7C92C4. Tokens: `--ice` #EAF2FF, `--mute` #7C92C4, `--void` #04060E, `--ink` #080F24, `--volt` #4DA3FF, `--ease` `cubic-bezier(.22,1,.36,1)`, `--u-r` 20px, `--u-rs` 14px (index.html 17-18, 2021, 16428-16429). Android has these in `ui/Tokens.kt` (`Pn.*`, generated into `gen/Web`); `--qsc` values and the `rgb(5,7,15)` / `rgb(11,15,30)` mix targets are not tokens: they are literals inside this card's CSS.

## 1.3 `qkStat`: the headline, ticket and snap for every state (8605)

Checked in this order (first match wins). `hrs = hrsOf(g)` (0 for competitive `g.comp`; else hours-to-beat by Settings play style: main / extras (`hoursX`, or main x 1.5) / all (`hours100`, or `hoursX` x 1.6, or main x 2.4)); `lg = num(g.logged)`; `hb = hrs ? [hrs+" h","to beat"] : ["—","to beat"]`; `day = shortDate(g.date)` ("12 Mar", plus " 27" if not this year), `yr` = full year. `stub`/`body` = [big number, small label].

| # | Test | k / kicker word | Headline | Stub | Body | snap |
|---|---|---|---|---|---|---|
| 1 | `isPlayed(g)` (an entry in `S.played`) | played / "Played" | runs==1 "Finished"; 2 "Played twice"; else "Played N times" (`runs` = `playedRec.runs`, 1..99) | [score-or-"—", "my score"]; score = `myScore(g)` else `round(p.real*10)` if the record is scored | [runs+"×", "played"] | yes |
| 2 | `g.state==="playing"` | playing / "Playing" | with hours: `left = max(0, round(hrs - logged))`; left>0 "N h left" else "Nearly done"; no hours "Playing now" | with hours [min(99, round(logged/hrs*100))+"%", "done"]; else [round(logged)+" h", "played"] | with hours [left+" h","left"]; else `hb` | yes |
| 3 | `g.state==="dropped"` | dropped / "Dropped" | "Set aside" | [round(logged)+" h", "played"] | `hb` | yes |
| 4 | `cd.v` is a number (date in the future) | coming / "Coming" | 1: "Out tomorrow"; else "Out in N days" | [day without the year, yr] | [N+" day(s)", "to go"] | no (ticket whole) |
| 5 | `cd.v==="TBA"` | coming / "Coming" | "No date yet" | ["TBA","date"] | `hb` | no |
| 6 | else (date today or passed) | out / "Out now" | `isFresh(g)` ("Just out", only if `state==="backlog"` and released within 14 days) else "Ready to play" | with a date [day w/o year, yr]; none ["Out","now"] | `hb` | yes |

Note a `state` of "upcoming" whose date has passed also lands in row 6 ("Out now / Ready to play").

## 1.4 Settings and game fields the front reads

Game: `id`, `title`, `colour`, `hero`, `steamId`, `bg`, `bgOff`, `bgX`, `bgY`, `img`, `imgX`, `imgY`, `art`, `platforms`, `genres`, `date`, `state`, `logged`, `hours`, `hoursX`, `hours100`, `comp`, `mine{}`; `S.played[]` records `{id, real, z, dk, runs, ...}`; globals `S.cardBg` ("hero" default, "plain", others draw the older `cardLayers`, not the open card), `S.playStyle`-ish via `playStyle()`, `LF` logo table. The Android `Game` (data/Library.kt:54) already exposes `state` and `colour`; the rest are raw `o[...]` reads.

# 2. Opening and closing

## 2.1 Row to card

- A closed row's `onclick` (8992-9004): `morphId`/`qKeep` bookkeeping, then `if (row is the open card) {collapsed=true; focusId=null; beep("back")} else {collapsed=false; focusId=row id}`; `renderQueue()` swaps the row for the card (`qPatch` replaces only the changed rows, 8742). `qKeep` (v307): in the next frame the tapped row is scrolled back under the finger, and a card that would end up under the tab bar slides up (never past its own top); Nexi reports an open card that sits off screen 0.9 s later.
- Entrance (only when `!collapsed && S.motion !== false && S.vt !== false && !calm()`): class `.heroin` on `.hero` for 2000 ms; `sfx("qkopen", snap)` plays whenever the card opens (also with motion off). The old hype/rating ticks (`revealSound`) are gone (v306): never play them. Timeline, all transform/opacity, easing `--ease` unless noted (css 27250-27272):

| Part | Animation | Start | Duration |
|---|---|---|---|
| picture `.qkbg` | scale 1.07 -> 1 | 0 | 1.2 s |
| strap | `skewY(-6deg) translateX(-112%)` -> rest | 0 | .6 s |
| box art | from opacity 0, translateY(-30px); at 70%: opacity 1, translateY(3px); then 0 | .25 s | .6 s |
| info line 1 / 2 / 3 / 4 (kicker, headline, platforms, genres) | from opacity 0, translateY(10px) | .55 / .62 / .70 / .76 s | .45 s each |
| ticket | from opacity 0, `translateY(40px) rotate(-6deg)`, `cubic-bezier(.34,1.56,.64,1)` | .75 s | .6 s |
| logo | from opacity 0, `translateY(8px) scale(.97)` | 1.0 s | .6 s |
| strap glint (`::after`, 30% wide white sheen `rgba(255,255,255,.55)`, skewX(-20deg)) | translateX -120% -> 420% | 1.15 s | .9 s (`ease-out`) |
| torn stub (released only) | none -> 35%: `translate(-1px,0) rotate(-2deg)` -> 70%: `translate(-5px,5px) rotate(10deg)` -> rest `translate(-5px,4px) rotate(8deg)` | 1.4 s | .5 s |
| body (released only) | none -> 35%: `translateX(1px)` -> rest `translateX(4px)` | 1.4 s | .5 s |

Total about 1.9 s. Calm (`html.still`, reduced motion, `S.motion===false`): no entrance, the card just appears.

## 2.2 Sounds (web `sfx(kind, x)` at 11646; all synthesised, gated by `S.sound` through `audio()`)

| When | Web call | Recipe (11668-11676) | Android `Sfx` (kit/Recipes.kt:11-16) |
|---|---|---|---|
| card opens (snap false) | `sfx("qkopen", false)` | ink-drop bloop + soft chord + six plucks 0.6-1.06 s | `CardOpen` 1.53 s |
| card opens, released game (snap true) | `sfx("qkopen", true)` | same + paper tear at 1.4 s + a note at 1.56 s | `CardTear` 1.79 s |
| turn to the back | `sfx("qkflip", true)` then `sfx("qkback")` | page-turn noise + rising notes 784 -> 1047; then click, 7 bubbles, 4-note start jingle at 0.9 s | `CardFlip`, `CardBack` |
| turn back to the front | `sfx("qkflip", false)` | falling notes 1047 -> 784 | `CardFlipBack` |
| one tap closes | `sfx("qkshut")` | two notes 659, 494 | `CardShut` |
| tap on the open row in "Character select" look | `beep("back")` / `sfx("qkopen")` (8847-8852) | | `Back` |

The Android recipes are already ported (`Sounds.play(Sfx.X)`, gated by `Sounds.on`, Settings › Sounds, `Prefs.sound`). The Settings screen already lists these six under "The Queue card" (ui/Settings.kt:451). Nothing plays them yet: no Queue screen exists. Vibration: `navigator.vibrate(8)` on the turn only (`Buzz.big(Big.CardTurn)` exists, Buzz.kt:75).

## 2.3 One tap closes (`qkTap` 10773, `qkShut` 10798)

`qkTap(w, at)`: `now = event.timeStamp`. If a single-tap timer is pending and `now - qkTapAt < 320` -> cancel it and turn (`qkTurn`). Otherwise remember `qkTapAt = now` and start a 320 ms timer that calls `qkShut`. `qkShut`: ignored while `dataset.busy` (mid-turn); `sfx("qkshut")`; with motion: `.hero.qkshut` = `opacity -> 0, translateY(-10px)`, `.22s ease-in`, pointer-events none; after 220 ms `collapsed=true; focusId=null; flipId=null; renderQueue()` (the row is back, no animation of its own beyond the row entrance); calm: immediate. A tap on any `button, a, input, select, textarea, label, .hmenu, .hmore, [data-more], [data-diary], [data-comp], [data-edit]` clears the pending close (the button does its own job).

## 2.4 Entry points (later phases)

- Box art (`button.qkcov[data-hub]`, title "Open <title>'s own page") -> `hubJump(img, id)` (8961) -> `openFull(id)`: the game page (Phase 2). No cover-to-figure morph on the web any more ("the hub fades up instead", 8715); Android's placeholder page already flies the cover (ui/GamePage.kt, `FlyFrom`), so the card's box art can use it.
- Edit (`[data-edit]`, on the back) -> `openEdit(id)` (9052). Edit is Phase 2.
- Row long-press -> drag, row contextmenu -> `openFull`: piece 3 / list, not here.

# 3. The turn (`qkTurn` 10781)

- Trigger: two taps within 320 ms (front or back).
- Guard: needs both plates and `!w.dataset.busy`.
- Immediately (before any animation): `on = !w.classList.contains("on")`; toggle `.on`; `flipId = on ? id : null`; close any open ⋯ menu; sounds as in 2.2; `vibrate(8)`; going to the back, the back's `[data-count]` numbers are set at once.
- Calm / `S.motion===false` / `prefers-reduced-motion`: stop here: the card is simply on the other side. No animation.
- Motion: `a = on ? 0 : 180`; options `{duration: 420, easing: "cubic-bezier(.3,0,.15,1)"}`; front `rotateY(a)` -> `rotateY(a+180)`, back `rotateY(a-180)` -> `rotateY(a)`. So both directions rotate positively through the edge-on moment at about the middle. `.hflip` has `perspective: 1600px`. During the turn the leaving side stays `visibility: visible` and `.qkturn` is on the wrapper, which re-enables `backface-visibility: hidden` + `will-change` on both plates (at rest they are off, v317, because a card-sized GPU layer was kept forever). A sheen sweeps each plate (`::after`, `linear-gradient(105deg, transparent 35%, rgba(234,242,255,.22) 50%, transparent 65%)`, opacity 0 -> 1 at 30% -> 0, `.42s ease-in-out`).
- Finish (`onfinish`/`oncancel`): remove `busy` and `.qkturn`, restore visibility.
- The box and the scroll must not move while it turns (`card-turn.mjs` checks, v306).
- Back fills that start when `.on` is set (they are CSS animations, so they run during/after the turn; 3.1).
- What the plan wants that the web lacks: "a tap mid-animation reverses it smoothly" (ANDROID-PLAN.md line 32). The web simply ignores taps while turning. A decision, not a bug.

## 3.1 Back entrance (css 27344-27356, motion only, none in `html.still`)

| Part | Animation | Delay | Duration |
|---|---|---|---|
| each of the 8 buttons' liquid `.qbliq` | `translateY(var(--bt))` (empty) -> its level `translateY(var(--y))`, `cubic-bezier(.16,1,.3,1)` | .3 s + i x 50 ms (i = 0..7) | .8 s |
| the controller's liquid | same | .22 s | 1 s |
| bubbles `.qbbub` | opacity 0 -> 1 (30%) -> 0 while rising 14px | .75 s | 1 s `ease-out` |
| the four numbers | from opacity 0, translateY(12px), `cubic-bezier(.16,1,.3,1)` | .45 s + i x 50 ms | .4 s |

# 4. The back (v300 "Controller", `qkBack` 8665)

## 4.1 Layout (`.qbin` is a grid, gap 12, padding 14/14/18; from 620 px gap 18, padding 22/26/26)

1. **Header row** (grid `30px | 1fr | auto`, gap 10; from 620 `44px | 1fr | auto`): box art 30 wide, aspect 2/3, radius 6, border `1px rgba(234,242,255,.18)`; the logo in a 28px-tall box (40 at 620), left-aligned, drawn light when dark (same `LF` rule) or the title as `700 16px/1.1` (22 at 620); then the icon buttons: **Edit**, **⋯** (and, only in "My order" sort, two move arrows: NOT PORTED, owner Nov 2: drag does it). Icon buttons 34x34 (40 at 620), radius 12, bg `rgba(234,242,255,.06)`, border `1px rgba(234,242,255,.1)`, 18px glyph, gap 6. Move arrows are bare (`color-mix(accent 45%, ice)`). Icons are drawn paths in `QB_IC` (8639-8644): up, down, edit (24x24 viewBox, fill-only `currentColor`, one part `opacity .35`), more (three dots r=2.1 at x 5/12/19), diary (book), comp (crossed swords).
2. **Sub line** `.qbsub` (12px/600 `--mute`; 13.5 at 620): `<b>{spot}</b> . {when}`; spot "Next up" or "3rd in the queue"; `when` = date ? (OUT ? "released " : "out ") + `shortDate` : "no date yet"; `b` colour `color-mix(accent 40%, ice)`.
3. **The controller + its 8 buttons** `.qbpad`: grid `128px | 1fr`, gap 12 (236px, gap 28 at 620), centred vertically.
 - Controller (`.qbbig`): SVG viewBox `0 0 64 44`, outline `QB_PAD` (8657), liquid level `y = 43 - (big/100) * 36` (`v = big/10`; top 7, bottom 43). Fill colour = the game's colour; glass `fill rgba(234,242,255,.05)`, `stroke rgba(234,242,255,.4)` 1.2. Decorations (`.qbdeco` stroke `rgba(234,242,255,.3)` 1.2): D-pad plus, four face-button circles r=2.3 at (49,14), (54.5,19.5), (43.5,19.5), (49,25). The little screen `rect x23 y10 w18 h15 rx3`, fill `rgba(4,6,14,.72)`, stroke `rgba(234,242,255,.32)` 1. The score sits on the screen: `700 17px/1 Poppins` (30 at 620), -.03em, tabular, `--ice`, centred in a box with the SVG's 64:44 aspect with 13% bottom padding. Caption under it: `10.5px/600 --mute`: "my score" / "Nexi's guess" / "no scores yet".
 - Eight round buttons `.qbbtns` (grid 4 columns, gap 8px 6px; 8 columns, gap 10 at 620): each a 36px (52 at 620) SVG `0 0 32 32`, circle `QB_BTN`, level `y = 31 - (v/10) * 28`; number inside (`700 11px`, 13 at 620, `--ice`, text-shadow `0 1px 3px rgba(4,6,14,.85)`; one decimal, "—" when 0); caption below `10px/600 --mute` (12 at 620). Liquid fill `color-mix(accent 68%, --ink)` (so a white number reads); the controller's is the pure game colour. Small shine arc `M8.5 11a9 9 0 0 1 5-4.5` stroke `rgba(234,242,255,.45)` 1.4. Liquid is a wave path (`QB_WAVE` 8658) clipped by the shape, moved by `translateY`; two bubble dots per vessel (r 1.6 and 1.1, `rgba(234,242,255,.7)`).
 - **Nexi's guess** (no area scored yet, other games scored): dashed glass (`stroke-dasharray 3 2.5`), liquid dimmer: buttons `mix(accent 42%, ink)`, controller `mix(accent 60%, ink)`; label "Nexi's guess".
4. **Caption** `.qbcap` (12px/600 `--mute`; 13.5): unscored: "Nexi's guess, area by area, from the games you've scored"; scored: "Your scores" or, played more than once, "Your scores . played N×"; no data: "Score a game you've played and Nexi starts guessing these".
5. **The four numbers** `.qbfour`: grid of 4 (gap 8; 26 at 620). Each cell: a 4px-wide bar (radius 3, `linear-gradient(180deg, mix(accent 45%, ice), accent)`) spanning two rows, value `700 18px/1.05` (32 at 620), -.03em, with a unit `small` (`11px/600 --mute`), label `10.5px/600 --mute` (12.5). Values:
 - played: [score or "—", "my score"], [`p.real` if scored else "—" + "/10", "it deserved"], [runs + "×", "played"], [`g.critic` or "—", "critics"];
 - not played: [`cd.v` ("Out" when OUT, "TBA", or the day count), `cd.l`], [hrs or "—" + "h", "to beat"], [`g.hype` or "—" + "/10", "hype"], and `expected(g)` ? [`e.toFixed(1)` + "/10", "we'd expect"] : [`g.critic` or "—", "critics" if set else "we'd expect"].
6. **Platforms and evenings** `.qbpf` (only if the game has platforms or hours): logos 18px `--ice` (same chips as the front) and `<em>About {ceil(hrs/eve)} evenings at {eve} hours a night</em>`, `eve = S.evening || 2` (trap: elsewhere the default is 4).
7. `g.learn` ("Learn") and `g.tips` ("Play it") paragraphs, then "Like this" (`simBack`, 13570: up to 3 local look-alikes as cover buttons that open that game (`data-opg`), plus chips "More like it" (`data-sim`) and "Trailer" (`data-trailer`, opens a YouTube search)). `simLocal` was not read; it is the Similar logic (Phase 2/Taste).

## 4.2 The ⋯ menu

`.hmore > .hmenu` on the back: **Diary** (drawn book icon; suffix " . N" when `g.diary` has entries; opens the Diary sheet `openDiary(id)`, 10878; hidden by `html.nodiary`/`S.diaryOn` off) and **Competitive** ("Competitive" / "Competitive ✓"; `compSet(g, !g.comp)` at 10806: a competitive game's `hours` is stored in `hoursWas` and set to 0, restored when switched off; toast "<title> is competitive — immune ∞" / "<title> counts again"; Nexi says a line 500 ms later). Menu box: min-width 170, `--ink` bg, `1px --edge` (#24407F) border, radius 14, padding 6, items bold with 18px icons, opens DOWN and right-aligned on the back (`top: calc(100% + 6px); right: 0`); in with `hmIn .22s cubic-bezier(.34,1.56,.64,1)` (opacity 0, translateY(6px), scale .96). Opening ⋯ plays `beep("tap")`; a tap anywhere else closes it.

**Important correction to the Brief:** at APP_V 327 the open card's ⋯ holds ONLY Diary and Competitive. "Score it" and "Duel it" (`data-qscore` / `data-qduel`) are NOT in the card: they are on the game page's action list (`hubActs`, 9482) and in Taste/Home. So there is nothing to "leave out" of the card; just do not add them.

## 4.3 Data the back reads and where it is stored

- The 8 areas (`RATINGS`, 3758), in this order, stored as numbers 0-10 (decimals allowed) at `g.mine[key]`: story "Story", music "Music", gameplay "Gameplay", replay "Replayability", visuals "Visuals", world "World", chars "Characters", pace "Pacing". Button captions use `RING_CAP` (3776): replay "Replay", gameplay "Play", chars "Cast", visuals "Look"; the rest full. (Advanced scoring `SUBRATE` writes back into the same `g.mine` place; nothing here reads the sub-scores.)
- `qbAreas(g)` (8646): own scores if any area > 0 (guess false). Else Nexi's guess: from every OTHER game that has any area scored, per area weighted mean of the scored values, a game sharing a genre (case-insensitive compare of the comma lists) counting 3x, any other 1x, rounded to one decimal; areas with no data are 0; none anywhere -> `null` (empty buttons, "no scores yet"). The guess never uses the played record or hype.
- Big number: scored: `myScore(g)` (or the mean x 10 if that is 0); guess: `round(mean of nonzero guess values * 10)`; none: "—".
- `expected(g)` (8382) needs `hypeBias()` (at least one scored played game that had a hype) and `g.hype`; shift = mean of the per-genre (hit - hype) means (`genrePattern`, genres with >= 2 played games) for this game's genres, else the overall mean; `clamp(1..10, round(hype + shift, 1))`. This is Taste's maths; Phase 1 needs only the finished number (or reimplement from `S.played` + hype, Phase 6).
- `p.real` (played record, 0-10 "it deserved") scored iff a number > 0, or 0 with `z===1`, and not `dk` ("don't remember") (`scored()` 3504).

# 5. The Game Shuffler deck

## 5.1 How it opens

| Opener | Code | What happens |
|---|---|---|
| **Hold Shuffle** in the Queue bar (`[data-qshuf]`) | `qdShufArm` 8802: pointerdown starts a 480 ms timer; pointerup/cancel/leave clears it; contextmenu prevented; the click after a hold is swallowed | `beep("tap")` + `openShuffle()` |
| Tap Shuffle | `qdShuffle` 8811 | NOT the deck: picks a random game that is out (else any live game), never the same twice in a row (`qdLast`), switches the filter to All if it is not in view, redraws, `beep("nudge")`, lights the row (`.qslit` 2.4 s, scroll to centre, smooth unless calm), toast "Tonight: <title>" |
| Home's "Shuffle" pill (`[data-shuffle]`, only with 2+ live games) | capture click 16032 | `openShuffle()` |

## 5.2 State and deck (15915-15921)

`st = {mode: arg || S.shufMode || "all", deck, i: 0, picks: [], hist: []}`. Deck `shDeck(mode)` (15904): `S.games` not played, not dropped; mode "out" keeps `shOut(g)` = state backlog/playing, or date <= today (en-CA local date string); then Fisher-Yates shuffle. `S.shufMode` is written whenever the switch changes (persists).

## 5.3 The screen (css 16035-16112; all values px)

`#shuf` fixed full-screen, z-index 97, bg #03050b, fades in/out `.28s ease`, padding top `safe-top+10`, bottom `safe-bottom+12`; Nexi's bubble is hidden while it is open (`html.shuffling`).
- **Top bar** (padding 0 16 8): left, the mode switch: two 30px round buttons, white #f2f4f8 ("Everything waiting", `data-shm="all"`) and black #0a0c12 with a faint ring ("Only games that are out", `data-shm="out"`); the selected one has ring `0 0 0 3px #03050b, 0 0 0 5px --volt`; a 12px `--mute` label ("Everything waiting" / "Out now only"). Right: close X, 40px circle `rgba(255,255,255,.08)`.
- **Stage**: the card, 440px max wide (`min(100%, 440px)`), as tall as the stage (max 760), radius 34, padding 16/20/18, background `radial-gradient(120% 70% at 30% 18%, --tint -60%, transparent 60%)` over `linear-gradient(170deg, #1a2236 0%, #0c1120 55%, #070a13 100%)`, shadow `0 30px 70px -20px rgba(0,0,0,.9)` + inset top highlight `rgba(255,255,255,.07)`. `--tint` = `g.colour` if a valid #rrggbb else #4DA3FF. A picture (`bgSource`: hero, Steam hero, `g.bg`, `g.art`, cover) sits behind at opacity .22 under a fade to #0c1120.
- Card contents top to bottom: pills "n / N" (11px/600, letter-spacing .08em, `rgba(255,255,255,.08)`) and, if already picked, "★ Tempted" (volt pill); the hero: the title's first part (`shShort` = split at the first ": ", "– ", "— " or "- ") in UPPERCASE Playfair Display 700, size `min(22vw,104px) x fs` (fs 1 up to 6 letters, .78 up to 10, .58 up to 16, .46 longer), line-height .95, white with a heavy shadow, over `g.key` (a figure image, contained, max 86% wide) or else the box art as a poster (`height 82%`, max 300, aspect 3/4, radius 18, rotated -4 deg, 3px white border); a row of 6 covers as 42px circles (current one full opacity, 2px volt ring, scale 1.1; the window starts at `max(0, min(i-2, len-6))`); "Why play <short> next" (`18px/600`); the text (`13.5px/1.5`, 78% ice, 4 lines): `g.desc` or `g.about`, else built from the date ("It's out and waiting for you." / "It lands <date> — <N days away>."), hours ("About N evenings at your usual pace.", N = max(1, round(hrs / num(S.evening, 4, 1, 12)))) and hype ("You hyped it N/10."), else "No notes on this one yet. Add some in Edit and they'll show here."; chips (outlined `rgba(234,242,255,.28)`, radius 10, 12px/500): "🔥 hype/10", "{hrs}h", "Out now" / date / "No date", first genre. Two stamps over the card: "Tempted" (volt, left 20, top 58, rotated -12 deg) and "Not now" (`--hot`, right 20, +12 deg), Playfair uppercase 20px, border 2px, opacity 0 until you drag.
- **Side cards** (neighbours): 128x190 (150x220 at 620), radius 26, left one rotated -10 deg at left -58px and 80% up, right one +10 deg at right -58px; each shows the neighbour's cover (64x84), a vertical uppercase title, a "🔥 hype" pill; a tap moves one step (`data-shside` l/r). At 620+ they sit `calc(50% -+ 220px -+ 150px)` from the middle.
- **Bar** (gap 14, padding 12/16/0): five round buttons 48px (`rgba(255,255,255,.06)`): Back one (`data-sh="undo"`, disabled with no history, 30% opacity), Not now (X), **Tempted** (star, 66px, `--volt` fill, glow `0 14px 30px -10px rgba(--vrgb,.9)`), Shuffle again (`data-sh="again"`), Your picks (`data-sh="picks"`, count badge top-right).

## 5.4 Gestures and animations

- Drag the card (pointer events, not on buttons; `touch-action: none` on the stage): after 6px of movement the pointer is captured; `translateX(dx) rotate(dx/22 deg)` with no transition; stamp opacity = `|dx|/90` clamped to 0..1 (right = Tempted, left = Not now). Release: `|dx| > 90` decides (right = yes); else springs back, `transform .35s cubic-bezier(.34,1.4,.64,1)`.
- `decide(yes)`: push `{i,id,yes,had}` to history; yes adds to `picks` (once), no removes; `beep(yes ? "add" : "tap")`; vibrate 14 (yes) / 6 (no); card flies off `translateX(+-130%) rotate(+-16deg)`, opacity 0, `.3s cubic-bezier(.4,0,.6,1)`; after 260 ms `i++` and repaint with the entrance: opacity 0, `translateY(26px) scale(.95)` -> none, 420 ms `cubic-bezier(.16,1,.3,1)`. Calm: no animation, straight to the next. Running off the end of the deck shows the results.
- Undo pops history and restores the pick state (`beep("back")`); Shuffle again re-deals, i=0, history cleared (`beep("open")`); thumbnails and side cards jump (`beep("tap")`); the mode buttons re-deal from the new mode (`beep("tap")`). Keyboard: Esc closes, arrows decide.
- Opening plays `beep("open")`. **All Shuffler sounds are the old `beep()` blips (v1 sounds), not `sfx()`**; Android's `Sfx.Open / Add / Tap / Back` are the same four.

## 5.5 Results (15974)

Heading "Tempted by N" (or "Nothing grabbed you"), line "Start one now, or open it to have a look." / "Shuffle again, or switch to games that are out.". One row per pick (46x60 cover, title, "Playing now" / days text / "No date yet", left border 3px in the game's colour): **"▶ Play now"** (`data-shplay`; only for a game that is out and not already playing): sets `g.state = "playing"` (nothing else: no order change, no played record), `touched()`, redraw, toast "<title> is now Playing", `beep("add")`, results repainted; **open** icon (`data-shopen`): closes the deck, then after 200 ms `openFull(id)` (game page, Phase 2). Buttons: **"📋 Save as a list"** (`data-shlist`, only with 2+ picks): creates a list "Tempted . <d Mon>" with emoji 🎯 (`listNew` 12862), adds its id to each pick's `g.lists`, toast "Saved as “<name>” — find it in the Queue filter", `beep("add")`, the button removes itself; "Shuffle again"; "Keep going" (when the deck is not finished).

## 5.6 State read and written

Reads `S.games`, `S.played`, `S.shufMode`, `S.evening`, `S.lists`, game fields above, `g.key`, `g.desc`, `g.about`, `g.hype`. Writes (all go through `touched()`/`save()`): `S.shufMode`, `g.state`, `g.lists`, `S.lists`. In the Android practice copy these writes must stay in memory only (Brief: "practice copy: resets when the app reopens"). Usage is counted under `shuffle` (12979).

## 5.7 Rules and visual debt

- LOG v181 (the deck was made as a chess-reference look), v303 notes it is "Moving things" in the Brief. Not restyled since. It breaks later house rules: tilted poster and side cards (v299 "never a tilted cover" is about the Queue card, not this), emoji as icons (v297), uppercase heading (v188), `Playfair Display` serif. Decision needed before Android copies it as-is or applies the house look. (Playfair is bundled on Android, 0.md:42.) **Decided Nov 2:** the open Queue card's look (1-brief.md).
- Nexi's bubble hides while it is open; no Nexi menu inside it.

# 6. Nexi's seat

Out of scope here: his menu `pnBubWheel` (12363; hook points: hold 600 ms or double tap on him opens it; v296) and his personalities (`#pnbub[data-p]` rules, css 17975+, `pnArc`, `PN_STORY`). They belong to a later phase; only the hook is noted.

## 6.1 Who is on screen

| Width / state | What shows |
|---|---|
| < 620 px (folded, or phone portrait) | `#pnbub` is `display:none`. The only Nexi is Home's own face `.nxh-face` (52px round, two eyes 8x12 blinking `nxhBlink 5s`; tap = "Duel with Nexi" `data-qduel`, a later hook can turn it into chat, 13752). Home's line `nxHomeLine` (13316) sits beside it. |
| >= 620 px, rail shown (`html.pnrail`, not `railhide`), Settings › Extra features › "Nexi sits in the rail" on (`!S.nxSeatOff`) | **Seated** (`html.nxseat`): he sits at the foot of the rail on EVERY screen including Home. `seatOn()` (21125) also needs `!nxtouring`, `!nxguide`. |
| >= 620 px, rail hidden (`railhide`) | **Floating** `#pnbub`, draggable. |
| Seated and on Home | Home's own face is hidden (`@media (min-width:620px){html.nxseat.onhome #p-home .nxh-face{display:none}}`, 25832) so there is one Nexi (v296; v274 had done it the other way round). |

## 6.2 His face (`pnBubble` 11998, CSS 2302-2366 and 20925-20932)

`#pnbub`: 62x62, `position: fixed`, moved only by `transform: translate3d(x,y,0)` (`pnBubPlace` 11997), z-index 60, `will-change: transform`, `touch-action: none`. Children: `.aura` (blob loop 6s), `.core` (circle, `--volt` or his picture, border 3px `--void`, loop `bubBob 3.4s ease-in-out infinite`: `translateY(-5px) rotate(-3deg)` at 50%), `.face` (`radial-gradient(circle at 34% 28%, #1c2c52, #070a14 72%)`, cheeks), `.eyes` (two 11x15 ovals `#EAF2FF`, glow `0 0 8px #8FD0FF`, at top 32%, left/right 27%; they blink with `pnBlink 4.6s infinite`: `scaleY 1 -> .1` at 96%, back by 100%, the right eye .04s later), `.mouth` (14x7 arc, 2.5px `#8FD0FF` border, top 62%), `.zz`, `.tear`, `.sweat`, `.nt`, `.hat`, `.badge`; plus `::before` breath glow (inset -20px, `rgba(--vrgb,.4)`, `pnBreath 4.5s`: opacity .45 <-> 1, scale .94 <-> 1.04). Face vs picture: the face unless `S.bubImg` / `S.bubMode==="game"` (his picture = the #1 game's art); glow tint comes from the #1 cover's strongest colour (12x12 sample, `--g1/--g2`; gold `#FFD34D` at level 10+). Eyes follow the finger only while floating (`--ex/--ey`, up to 5px, `.18s ease-out`). Asleep (`.sleep`) 01:00-06:00. Moods are classes (`PN_MOODS`: happy, talk, dizzy, ...).

## 6.3 Seated (css 24266-24270, 26588-26590; JS 21122-21166)

- Position `seatPos()`: `x = rail.offsetLeft + (rail.offsetWidth - 62)/2`, `y = rail.offsetTop + rail.offsetHeight - 62 - 16` (the rail keeps `padding-bottom: 84px` from 620). His inner parts are scaled to .72 (`html.nxseat #pnbub > * { scale: .72 }`), aura inset -7px.
- Gliding in/out of the seat: a WAAPI move of 560 ms `cubic-bezier(.34,1.3,.64,1)` (skipped when calm or when he moves < 4px). Seat is re-synced on `html` class changes, resize and 900 ms after load.
- **At rest he keeps only his blink** (v296): under `html.nxseat:not(:has(#pnbubmsg.on))` the breath glow, `.orb`, `.aura`, `.zz`, `.nt` and the `.core` persona loop are off, except while a reaction class is on him (`ssj-charge pop wob laugh angry sneeze stretch sig grab faint`). While he speaks he moves as before.
- Taps (capture handler on pointerdown): hold 600 ms -> menu; two taps -> `pnBubMood("happy",900)` + menu; **one tap, after 400 ms -> `railHideNow()`** (11855: `S.railHide = true`, class `railhide`, `pnBubShow()`: he glides out to his floating spot). The rail's » hide button is hidden while seated. He cannot be dragged out while seated.
- Floating mode: drag, flick carries 120 ms of velocity, snaps to the nearest edge (`PN_EDGE` 34px) with `520ms cubic-bezier(.34,1.45,.64,1)`; saved in `S.bubPos` and `S.railSide`. One tap (400 ms wait for a second) brings the rail back on his side (`html.railleft`), double tap or hold 600 ms opens the menu.

## 6.4 His speech bubble (`pnBubSay` 12458, `#pnbubmsg`)

- Look: fixed, max width 240, padding 10/14, radius 16, background `--ice`, text `--void`, `700 13px/1.3`, shadow `0 12px 30px rgba(0,0,0,.5)`; opacity 0 and no touches until `.on`; `transition: opacity .3s, transform .45s cubic-bezier(.34,1.56,.64,1)`.
- Where: on the side away from the edge (`right = pnBubX > innerWidth/2`): x = `pnBubX - w - 12` (right) or `pnBubX + 74` (left), y = `pnBubY + 8`, `w = min(240, own width)`; the transform origin is the side nearest him. Pops in from `scale(.6)` to `scale(1)` over two frames; `vibrate(6)`; his mood "talk" for 1.7 s; his eyes shift 4px toward the text. Hidden again after **5500 ms** (`pnBubHush`), when he is grabbed, or when a newer line arrives (`pnSayN` cancels a pending one). Tapping the bubble runs its action: `"pick"` -> roll then the pick card; `"duel"/"played"/"games"/"taste"/"add"` -> that tab (duel = Taste); a game id -> `openFull(id)`.
- When he speaks on his own (v274: no random chatter, no idle wandering/yawns): only on news. After `pnBubShow` (1.2 s): first meeting, 3+ days away ("N days… I was so bored without you"), his birthday, the daily brief once a day (`S.bubDay`), a release-day party (`S.nxPartyDay`). `pnBubReact` (12066; every 11.2 s while FLOATING, plus 5 s after show) says a line when a game was finished, delayed, came out, a game was added (60% of the time), a duel settled (50%), a hat unlocked; also the crystal-ball line after a score (1.4 s later), the competitive game line (500 ms). The daily hot take waits on his menu.
- `pnBubLines()` (12387) builds the old random line list ([text, action] pairs from names, rankings, hours, streaks, story). **No call site of `pnBubSay()` without a line exists in index.html at 327** (grep: zero), so those lines are effectively dead code; every shipped line is passed in. Do not port the random pool until the owner asks for it.
- Android: Sounds has `Sfx.Chime` and `Sounds.nexi(...)` gated by `Prefs.nexiVoice` (the web's "Nexi's blips"; the bubble itself is silent); the web only vibrates 6 ms per line (not wired on Android).

# 7. Edge cases

| Case | Web behaviour |
|---|---|
| Coming game with a date | kicker "Coming . 2nd in queue", headline "Out in N days" (1 -> "Out tomorrow"), ticket whole, stub `["12 Mar", "2027"]`, body `["N days", "to go"]`. |
| Coming, no date | "No date yet", stub `["TBA","date"]`, body hours-to-beat or "— to beat", ticket whole. |
| Released (not started) | "Ready to play" (or "Just out" within 14 days if `state==="backlog"`), torn stub; body hours-to-beat. Back says "released <date>". |
| Playing | "N h left" (or "Nearly done" / "Playing now" with no hours), stub percent done. |
| Played | "Finished" / "Played twice" / "Played N times", stub = my score, body "N× played". Back shows "my score / it deserved / played / critics". |
| Dropped | "Set aside". |
| Competitive (`g.comp`, never ends) | `hrsOf` = 0, so every "to beat" is "—"; a playing comp game says "Playing now"; Competitive ✓ in the ⋯; the Shadow (Grumble) cannot take it. |
| No picture | `.qkbg.none` radial wash in the game colour on `--ink`; no `hero`/`bg`/Steam id falls back to the box art; `S.cardBg==="plain"` or `g.bgOff` forces it. |
| No box art | `cover(g)` = the drawn placeholder (`placeholder(g)`); Android has `DrawnCover` (ui/Cover.kt:81). |
| No logo (`g.art`) | the title in `h2` in the logo slot (front) / `b` (back). |
| No colour | `g.colour` falls back to `PALETTE[0]` #4DA3FF in some screens; `qkFront` itself reads `g.colour` raw (in `big()` -> `--accent:${g.colour}`; an empty value leaves `--accent` unset and the CSS falls back to `--volt`). Android's `Game.colour` already falls back to `Web.PALETTE[0]` (Library.kt:70). |
| Very long name | front: the card shows only the logo or the `h2` clamped to 2 lines, `overflow-wrap:anywhere`; kicker/headline/genres are one line with ellipsis; back title one line with ellipsis; the Shuffler shrinks the serif title by letter count. |
| Many platforms | logos wrap inside the strap (v306); nothing runs past the strap's text column (`card-turn.mjs`); the Settings sample "folded logos 16px / gap 8px so five systems still fit one line at 412". |
| Very short names/numbers | Short things stay whole: `white-space: nowrap` (a date, "16h"). |
| Folded vs unfolded | one set of rules per width band (< 391, 391-619, >= 620) in section 1.2; every open card has the same height at a given width. |
| Calm / animations off / reduced motion | no entrance, no turn animation (the card is simply on the other side), no closing fold, immediate close; sounds still play. |
| Mid-turn taps | ignored (`busy`). |
| Taps on the back that are not buttons | close the card (one) or turn it back (two). |

# 8. LOG rules that apply (docs/LOG.md; CLAUDE.md "Look")

| Version | Rule |
|---|---|
| v298 | No buttons on the front; the box art opens the game page; every open card is one height; the pinned logo and corner platforms no longer draw on the card. |
| v299 | The picture fills the card, rounded corners; straight box art on the strap in the game's own colour; one plain headline; ticket (released = stub torn, coming = whole); logo in a fixed bottom-right slot; one tap closes, two quick taps turn; never the stamp, a tilted cover or a fixed-colour slab. Entrance ~1.9 s. |
| v300 | Back = "Controller"; never radar charts or potions ("a game app"). |
| v306 | Platform versions sit inside the logo's box, raised; the list wraps inside the strap; turn = 420 ms straight over, never a swing past and back; `revealSound` ticks gone, never on open. |
| v307 | Double tap timed from the click's `timeStamp`; tapping a row below an open card keeps the tapped row under the finger. |
| v317 | At rest no `will-change`/`backface-visibility` on the plates (a held card-sized layer). |
| v290 | Logos upright, same visual size, near-black drawn light (applies to the logo slot). |
| v296 | Seated Nexi: one Nexi per screen, only his blink at rest; menu is the small named-groups card (not this piece). |
| v280 / #309 | No press animation, no touch glow or ripple. |
| v297 | Icons drawn in the v247 menu style (24x24, fill-only `currentColor`, one part `opacity .35`), no emoji icons. |
| Speed rule | Animate only transform/opacity; no looping blur. |
| Sounds | Everything through `sfx()`/`beep()`, gated by Settings › Sounds (`S.sound`, off by default); the Shuffler uses `beep()`. |

# 9. What the Android code has today (android/app/src/main/java/io/github/shamlawy/playnext)

| Need | Status |
|---|---|
| Queue screen, open card, back, turn, close fold | **Nothing yet.** `ui/Pages.kt:206` (`QueueSoon`) lists the backup's games as plain text until Phase 1. No `Card`/`Queue` composables. |
| Card sounds | **Done**: `kit/Recipes.kt` renders `CardOpen`, `CardTear`, `CardFlip`, `CardFlipBack`, `CardShut`, `CardBack` (plus `Open`, `Add`, `Tap`, `Back`, `Nudge` for the Shuffler); `kit/Sounds.kt` plays them (`Sounds.play`, off unless `Prefs.sound`). Not triggered by any card. |
| Haptics | **Done**: `kit/Buzz.kt` `Big.CardTurn` (primitives LOW_TICK + TICK, fallback 8 ms), `Buzz.ms(...)` for the 6/8/14 ms ticks. Not wired. |
| Calm / reduced motion | **Done**: `ui/Motion.kt` `LocalCalm`, `rememberCalm(prefs)` (animations off in the backup, the phone's animator scale 0, battery saver); `Ease.house` is `--ease`; `Ease.enter` is `(.16,1,.3,1)`. The card's own curves are not defined: `cubic-bezier(.3,0,.15,1)` (turn), `(.34,1.56,.64,1)` (ticket), `(.34,1.3,.64,1)` / `(.34,1.45,.64,1)` (Nexi). Add them to `Ease`. |
| Cover flight onto the game page | **Done as a placeholder**: `ui/GamePage.kt` (`FlyFrom`, `GamePage(g, tap, from, ...)`, 380 ms flight, 220 ms close) takes any tapped cover; the card's box art can call it. |
| Pictures and the drawn cover | `ui/Cover.kt` `PicBox`, `DrawnCover`, `picUrl`; `Pic.cover/backdrop` in data. The card needs `hero`/`bg`/Steam-hero selection (section 1.2) which `Pic` may or may not cover: not checked. |
| Tokens | `ui/Tokens.kt` `Pn.*` (ice, mute, void, ink, edge, line, volt), `Pn.r` 20dp, `Pn.rs`. The 120-hour probe (`probe/ProbeScreen.kt`) already tests "a full-picture card with a see-through strap". |
| Poppins / Playfair | bundled (0.md:42). |
| Dates | `data/Times.kt` has only "Mar 12" US forms; the card needs `shortDate` (en-GB "12 Mar" / "12 Mar 27") and the UTC-midnight day count: new helpers. |
| Platform logos (`PLAT_LOGO`, `platOf`, `platChips`) | Not seen in Android sources (not searched beyond file names): needs the 9 SVG paths (index.html 7796) as Compose `PathParser` paths, plus the family/version matching. |
| Logo luminance (`lfMeasure`) | Not present; needs a per-URL cached mean-luminance measure. Piece 1 shares it (the closed rows' logo slot). |
| Game Shuffler deck | **Nothing yet.** Bar's Shuffle (tap/hold) is piece 3. |
| Nexi's seat | **Nothing yet.** `ui/Shell.kt:388-389` notes "Nexi's seat with the Queue's (Phase 1)"; the rail itself exists (72dp wide, `Shell.kt`). No Nexi face, bubble or `Sounds.nexi` caller. |
| Edit / Diary / Competitive sheets | Not present (Phase 2 / later). The card's buttons need entry points only. |

# 10. Not found, not verified, and open questions

- **Not verified**: `simLocal` (the "Like this" row's matching); `playedRows()`, `genrePattern()` internals beyond their use in `expected`; `lfRule` (the closed rows' logo sizing, piece 1); `S.playStyle` source (`playStyle()` was used but not opened); the exact CSS of `.simback`/`.sb-g` on the card back; whether Android's `Pic` already picks the Steam hero.
- **Not found**: any caller of `pnBubSay()` without an argument (zero); a contrast guard for pale game colours on the strap (none exists); any place that writes `g.logged` for "Nearly done" logic (not needed for the card).
- **Brief correction**: Score it / Duel it are not in the card's ⋯ at 327 (section 4.2).
- **Decisions for the owner or the build**: (1) taps during a turn: web ignores them, the Android plan says interruptible; (2) copy the Game Shuffler's v181 look as it is, or restyle it to the house rules; (3) the `S.evening` default (2 on the card back, 4 elsewhere) is probably an old inconsistency; copy it as is unless told otherwise; (4) practice-copy writes (Play now, Save as a list, Competitive, shufMode) stay in memory; (5) the 320 ms close delay makes a single tap feel late; the web is the reference, so keep it unless the owner says otherwise.
- Tests worth porting as screenshot/instrumented checks: `tools/scenarios/queue-card.mjs`, `card-turn.mjs` (turn <= 450 ms, box and scroll don't move, no part overlaps another, opening plays only the card's own tones), `logo-look.mjs`, `queue-open-place.mjs`, `nexi-wheel.mjs`, `rail-nexi.mjs`, `calm-nexi.mjs`.
