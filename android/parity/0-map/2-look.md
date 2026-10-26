# Map 2: Phase 0 visual foundation (web APP_V 327)

Source: `index.html` (28,868 lines; `const APP_V = "327"` at 4308). Line numbers are for this version. Read-only map, nothing edited.
CSS cascades in this file: a later block wins over an earlier one at equal specificity, and the "Clean" style (`html.ui2`, the default) lives at 16426+. It overrides much of the older "Arcade"/"Soft" CSS above it. **For the port, the Clean values are the real ones.** Arcade (S.uiStyle = "arcade") and Chamfered corners (S.corners = "sharp") are legacy looks.

---

## 1. Colour

### 1a. Root tokens (`:root`, line 16-19, unless noted)
| token | value | role |
|---|---|---|
| `--void` | `#04060E` | deepest near-black (text on accent fills, `theme-color` meta was this before v276) |
| `--ink` | `#080F24` | old panel colour; still used by #hrail (rail background), dock, old sheets |
| `--steel` | `#101C40` | old raised surface / inactive chip; rail icon tiles, `.conf` dots |
| `--edge` | `#24407F` | old border (rail border, separators) |
| `--volt` | `#4DA3FF` | **accent** (set by theme at run time, see 1c) |
| `--volt2` | `#2F6BFF` | second accent (gradients, chameleon dark tone) |
| `--vrgb` | `77,163,255` | accent as RGB triplet for `rgba(var(--vrgb), a)` |
| `--v2rgb` | `47,107,255` | accent2 triplet |
| `--ice` | `#EAF2FF` | main text (body `color`, line 33) |
| `--mute` | `#7C92C4` | muted text / labels |
| `--hot` | `#FF5470` | danger / sale / error |
| `--tS` | `#FFD34D` | S-tier gold (line 16243; `.tier.tS` text `#1a1200`) |
| `--skew` | `0deg` (line 1968; Arcade used a slant) | |
| `--r` | CSS fallback `22px` (1968); **applyLook writes `S.radius` default 18px** (3852) | "How round" |
| `--ease` | `cubic-bezier(.22,1,.36,1)` (line 2021) | house easing |

### 1b. Clean tokens (`html.ui2`, line 16426-16430)
| token | value | use (count of `var()` uses) |
|---|---|---|
| `--u-pan` | `#0a0e1b` | panels, sheets, rows, inputs (26) |
| `--u-card` | `#131a32` | cards, chips, Settings groups (51) |
| `--u-card2` | `#19213d` | raised card on a card (13) |
| `--u-line` | `rgba(234,242,255,.08)` | hairline (48) |
| `--u-line2` | `rgba(234,242,255,.14)` | stronger hairline, chip/ghost border, grab handle (37) |
| `--u-r` | `20px` | cards, rows, sheets |
| `--u-rs` | `14px` | buttons, fields, small things |

Other colours a port needs (hard-coded in CSS, not tokens):
- Folded tab bar's lit tab tile: `#0a0e1b` (= `--u-pan`, line 15591).
- Danger button: bg `rgba(255,84,112,.14)`, text `#FF8BA0`, border `rgba(255,84,112,.4)` (16457).
- Tiers: tA = `--volt` fill on `--void`; tB = `rgba(vrgb,.22)` fill + `rgba(vrgb,.5)` border; tC = transparent, `--mute`, `--steel` border (16243-16248). Confidence dots: off `--steel`, on `--volt`, c3 `#3FD9A4`.
- Game colour palette `PALETTE` (3783-3789): 32 hex colours (16 original + 16 added in v294). It is also the swatch list for the Gradient colour picker. A game's own colour comes in as `--accent` on its element.
- Status green `#3ddc84` (rail "out" dot, 2144); grey dot `#6b7896`.

### 1c. How Settings changes them: `applyLook()` (3817-3868)
- Every write goes through `lookSet(k, v)`, which skips values that didn't change (3821). A custom property on `<html>` restyles the whole page.
- **Theme** (`S.theme`, Settings › Look of the app › Theme, chips at 6784; `THEMES` 3805-3809):
  | key | name | a (`--volt`) | b (`--volt2`) |
  |---|---|---|---|
  | midnight (default) | Midnight | `#4DA3FF` | `#2F6BFF` |
  | crimson | Crimson | `#FF5C7A` | `#D4264B` |
  | emerald | Emerald | `#3DDC97` | `#139A64` |
  | amethyst | Amethyst | `#B28CFF` | `#7446F0` |
  | sunset | Sunset | `#FFB547` | `#F2711C` |
  | mono | Mono | `#E8EDF7` | `#6F7F9E` |
  | chameleon | Now playing | `chamTone(game.colour, .64)` | `chamTone(game.colour, .42)` (20815-20831) |
  applyLook sets `--volt`, `--volt2`, `--vrgb`, `--v2rgb` (`rgbOf`) and `html[data-theme]`. Chameleon: the game is the most recently played "playing" game with a hex colour, else On deck #1, else Midnight's colours. `chamTone` = HSL with saturation clamped to .55-.95 and lightness .64 / .42. Re-checked after `syncPlayed` and `render` (extend hooks, 20829-20832).
- **Style** `S.uiStyle`: Clean (default, adds `html.ui2`) or Arcade (3846).
- **Corners** `S.corners`: Rounded (default, `html.round`) or Chamfered (clip-path polygons, 45-52). **How round** `S.radius` 0-34, default 18 → `--r` (slider at 6816).
- Row-art knobs also written here: `--afh` (S.artFloat 10), `--afs` (S.artSpeed 60 → 6s), `--aglow` (S.artGlow 9), `--apun`/`--acon` (S.artPunch 0), `--raw` (S.rowArtW 150), `--rao` (S.rowArtOp 95%), `--rag` (S.rowArtGlow 7), `--bgmask`, `--rtp` (S.rowTint), `--kshy/--kshb/--ksho/--kglb/--kglo` (figure shadow/glow). These belong to the screens that use them, not Phase 0.

### 1d. The background (one still layer)
- `html { background: var(--bgb, #000); }` (line 28).
- `html.paper::before` (29-35), exactly:
  ```css
  content:""; position:fixed; inset:0; z-index:-2; pointer-events:none;
  background:
    radial-gradient(130% 75% at 50% 118%, rgba(var(--bgt), .9) 0%, rgba(var(--bgt), 0) 62%),
    radial-gradient(80% 55% at 104% -6%, rgba(var(--bgt), .35) 0%, rgba(var(--bgt), 0) 70%);
  opacity: var(--wop, .3);
  ```
  So it is two ellipses: a big glow rising from below the bottom edge (centre 50% x, 118% y; radii 130% × 75% of the box; alpha .9 → 0 at 62%) and a smaller one off the top-right corner (centre 104%, -6%; radii 80% × 55%; alpha .35 → 0 at 70%). The whole layer's opacity is `--wop`.
- applyLook (3848-3851):
  - `--bgb` = `S.bgBase` if valid hex, else `BG_BASE` `#000000`.
  - `--bgt` = RGB triplet of `mix(S.bgTint || BG_TINT, "#000000", 0.35)`, i.e. the tint darkened 35% toward black. Default `BG_TINT` `#2F6BFF` → **`31,70,166`**.
  - `--wop` = `S.bgGrad / 100`, **default `BG_GRAD` = 30** → `.30`. Range 0-100 (slider 6807).
  - `html.paper` only when amount > 0. At 0% the page is the plain base colour.
  - `<meta name="theme-color">` is set to the base colour (status bar colour).
- `BG_BASES` (3815), the Background colour swatches, in order: `#000000` (black, default), `#0E1A3D` navy, `#16307A` blue, `#0C3D5C` ocean, `#0B4A48` teal, `#0F3D22` forest, `#2A1B5E` indigo, `#4A1460` purple, `#5A1240` plum, `#5C1020` wine, `#5A2A0E` rust, `#2E2E36` graphite. A custom colour picker comes after them (`swatchRow`, 4852; wired 7099-7100). The Gradient colour swatches use `PALETTE`.
- Effective defaults on screen: about `rgb(8,19,45)` at the bottom centre (.27 alpha of 31,70,166 over black) fading to black by mid-screen, and about `rgb(3,7,17)` at the top-right corner.
- Compose: one `Modifier.drawBehind` on the root with two `Brush.radialGradient`s. Compose radial gradients are circular, so the ellipse needs a scale transform (`drawContext.transform.scale(sx, sy)` about the centre). Then `alpha = bgGrad/100`. Draw it once and never animate it.

---

## 2. Fonts

- Loading (line 11-13): Google Fonts CSS with `display=swap` and preconnects:
  - **Poppins** weights 400, 500, 600, 700 (no 800/900 files: CSS 800/900 on Poppins gets 700 or synthetic bold).
  - **Playfair Display** 600, 700.
  - **Archivo** variable: roman wdth 62-125 × wght 400-900; italic wdth 62-125 × wght 700-900.
  - `sw.js` does not cache fonts. Offline, the page falls back to system-ui. The app bundles them (Phase 0 row).
- `body` (31-36, 1969): `font-family: "Poppins", "Archivo", system-ui, sans-serif; font-size: 15px; line-height: 1.45; color: var(--ice); -webkit-font-smoothing: antialiased`.
- Where each is used:
  - **Poppins**: everything in Clean. `html.ui2` forces Poppins upright on headings (16432), on tiers / board titles / ring numbers (16511-16512), and sets `font-style: normal` on every element except em/i (16505).
  - **Archivo** (66 hits) is italic 900 condensed, the "Persona" display face. It survives in Clean only on: the tab-switch slash wipe text (`.pnw-slab b`, 2678: `italic 900 clamp(56px,17vw,112px)/1`, uppercase, letter-spacing -.03em), the wrapped/recap screens (`.wr-huge` clamp(96px,34vw,200px), `.wr-big`, `.wr-vs b`, `.wr-grid b`; 15663-15679), the celebration overlays (`.fx*`, `#fxrad`, `#fxboss`, `#fxcal`, `.fxclr`; 1659-1769), the boot reveal (`#pnrev`, 2072), the intro card (`.pcd-txt b`, 2741), and canvas share images (8263-8321, 11731, 12788-12804, 14715-14737). In Clean, `html.ui2 .wr-huge, .wr-big, .wr-vs b, .wr-grid b, .pnw-slab b` stay italic (16506).
  - **Playfair Display** (3 rules): only the Game Shuffler card (`.sh-name` 700 uppercase `calc(min(22vw,104px)*var(--fs,1))/.95`, letter-spacing .01em; `.sh-stamp` 700 20px uppercase; `.sh-side .sv` 700 22px; 16060-16084).
  - `ui-monospace` for backup text boxes (1555, 2696) and the intro card's small print (2740, 2748).
- Type scale (Clean; SKILL.md §4 plus these rules):
  | role | size / weight / tracking | line |
  |---|---|---|
  | Screen title `.tabbar h3` | 26px (30px from 620px), 700, -.015em, lh 1.1 | 16438-16439 |
  | Header `h1` | `clamp(34px, 11vw, 58px)`, 700 in Clean, -.015em (Arcade: 900 italic uppercase) | 74-80, 16432 |
  | Game page hub `h2` | `clamp(28px, 8.4vw, 50px)`, 700, lh 1.02 | 16442 |
  | Edit sheet head `.ehead h3` | 22px 700 | 16441 |
  | Settings group head / `details.grp summary` | 14.5px 600, tracking 0 | 16444, 16518 |
  | Group description `.gdesc` | 12px 400 | 16519 |
  | Labels | 12.5px 600 `--mute`, .01em | 16445 |
  | Count next to title `.tcount` | 12.5px 500 | 16440 |
  | Kickers / tags / small caps-like labels | 600, .08em tracking | 16447-16448 |
  | Row name `.row .name` | 600 (16px from 700px, 1610) | 16446 |
  | Stat numbers `.stat b` etc. | 700, -.01em | 16443 |
  | Big number `.bignum` | 800, -.03em | 16518 |
  | Button `.btn` | 600, tracking 0, padding 12px 20px | 16451-16455 |
  | Chip | 500 (selected 600), padding 8px 14px | 16460-16464 |
  | Body | 15px / 1.45 | 34 |
  | Folded tab label | 600 10px/1 Poppins, .01em | 15590 |
  | Rail tab label (old nav rail) | 9.5px, .03em | 104 |
- Most common sizes across the file: 12, 13 (81 each), 11, 12.5, 14, 11.5, 15, 10, 10.5, 13.5 px. Weights: 600 (97), 700 (89), 900 (72, mostly Archivo), 800 (66), 500 (25), 400 (2). The house rule for new UI is 500/600/700 only.
- Headings are upright and sentence case, never italic or uppercase (v188, T3).

---

## 3. Shape

- **Radii (Clean)**: `--u-r` 20px (rows, cards, empty states, hits), `--u-rs` 14px (buttons, inputs), chips are pills (999px). Other Clean values: `.stat` 16px, `.libcard`/`.libshot` 16px, Settings `details.grp` 18px, open queue hero `.plate` 26px, row thumbnails 10px, deck thumbnails 8px, sheets `28px 28px 0 0` (all four corners 28px from 660px), the folded current tab 22px (24px top corners on Home), the rail 30px, rail tabs 16px, rail game tiles round (50%).
- **S.radius / Corners**: `html.round` (default) turns every clip-path chamfer into `border-radius: var(--r)` (895-929). It derives `--r × .72` (chip, btn, nav button, icon), `× .5` (thumbs, tags, kickers), `× .85` (covers), `× .6` (hub figure, cards). **In Clean the `html.ui2` rules come later at equal specificity, so `.row`, `.btn`, `.chip`, `.stat`, `.libcard` use the fixed `--u-*` values, and the How round slider only reaches the remaining legacy selectors.** Chamfered = `clip-path: polygon(...)` with `--c` 11px (default), 22px (plate, sheet), 13px (row), 9px (chip, btn, nav), 7px (stat).
- **Spacing**: there is no token scale. The values in use cluster on a 2px grid. gap: 8px (106), 10 (81), 6 (69), 12 (61), 4, 5, 3. Page gutter: `.wrap` padding `20px 16px 40px` (42), `26px 22px 60px` from 700px (1605), max-width 880px centred. Rail clearance: `.wrap` padding-right 96px on the rail's side at 620-1099 (16518-16521; 2125, 2345). Card padding is usually 12-18px.
  Proposed port scale: 2/4/6/8/10/12/14/16/18/20/22/26 dp.
- **Shadows (Clean)**:
  - Rows: `0 10px 24px -18px rgba(0,0,0,.9)` (16480).
  - Primary button: `0 8px 20px -10px rgba(var(--vrgb), .8)` (an accent glow under the button). Ghost, danger and chips have none.
  - Sheet: `0 -1px 0 var(--u-line2), 0 -30px 60px -20px rgba(0,0,0,.8)` (16532).
  - Hero plate: an inset 1px `--u-line` ring.
  - Rail: `0 20px 40px rgba(0,0,0,.5)`, or `0 24px 60px rgba(0,0,0,.75)` off Home (2134, 2257).
  - Field focus ring: `border rgba(vrgb,.7)` + `0 0 0 3px rgba(vrgb,.16)` (16472).
- Sheet grab handle: 40×4px, radius 4px, `--u-line2`, 8px from the top (16533-16534).
- A row's game colour is a 4px accent bar on the hero plate's left edge (`top/bottom 22px`, radius `0 4px 4px 0`, 16491), never a thick edge.

---

## 4. Layout

### Breakpoints (CSS widths: 35× 620px, 13× 760px)
| width | what changes |
|---|---|
| ≤ 619 (folded 412, 360, iPhone 414) | Tab bar = icon row **at the top of the page, in normal flow** (not fixed: it scrolls away). The Queue deck bar wraps under 500. |
| ≥ 620 | Nexi's rail `#hrail` replaces the tab bar (`html.pnrail nav { display:none !important }`, 2245). Titles go 26 → 30px. |
| 620-1099 (unfolded 859) | `.wrap` gets 96px padding on the rail's side (16518). The Queue bar shortens words (27479). |
| ≥ 700 | `.wrap` padding 26/22/60; bigger row images and names. |
| ≥ 760 | Old `html.rail` nav drawer styles (unused once pnrail is on). `applyLook` treats ≥760 as `wide` for `S.nav = auto`. |
| ≥ 1100 | The rail floats over the centred 880px column with no extra padding. |
Also used: 660 (sheet fully rounded), 500/520/400/390/380 small tweaks.

### Folded tab bar (`nav`, markup 3306-3313; Clean phone CSS 15583-15600)
- Order: **Home** (`data-tab="home"`), **Games** (`games`), **Plan** (`plan`), **Taste** (`taste`), **Add** (`add`), then **Settings** (`#gear`, opens the Settings sheet; not a tab). Icons are inline 24×24 SVGs, fill-only, with one part at opacity .35 (v247 style).
- Buttons are 52×52 with radius 18, transparent, `--mute`, gap 2px, centred, `margin: 6px 0 16px`. Icons 21px. Labels are hidden.
- The current tab is 66×62 on `#0a0e1b`, radius 22, icon `--volt`, and shows its label (white, 600 10px). **On Home** it is 70px tall with top corners 24/24/0/0 and fuses into the Home panel below through two 20px inverse-radius "notch" corners (radial gradients, 15594-15598).
- Safe area: the tab bar has **no** safe-area handling (top flow; the iPhone status bar is `black-translucent`, viewport-fit=cover). Only fixed overlays use `env(safe-area-inset-*)`. Android: edge-to-edge with `WindowInsets.statusBars` padding above it.
- `S.nav` (Getting around, 6823-6828; applyLook 3859-3867): `auto` (default), `rail`, `top`, `dock`.
  - `dock` = a floating pill at the bottom centre (1250-1266): `bottom: 12px`, padding 6, gap 3, `--ink` with an `--edge` border, radius 999, shadow `0 16px 38px rgba(0,0,0,.55)`, icons 19px, current tab on `--steel`, Add filled with `--volt`, `.wrap` padding-bottom 96, `--dockh` 62px.
  - The others only matter under 620, because pnrail hides `nav` at ≥620.
  - **`S.railW` (64-150, default 92 → `--railw`) is effectively dead**: it sizes the old left nav drawer, which pnrail hides. Ask the owner before porting it.

### Unfolded rail `#hrail` (CSS 2132-2136, 2250-2296, 24239-24270; built by `pnRail()` 11858-11899)
- Fixed: `right: 12px; top: 12px; bottom: 12px; width: 72px`, radius 30, `--ink`, 1px `--edge` border, column with gap 10 and padding `14px 0`, z-index 40. Shown at ≥620.
- Contents top to bottom:
  - Tab buttons `.tb` for home, games, plan, taste (icons copied from the nav). 48×44, radius 16; on = `--volt` fill with `--void` icon.
  - A separator.
  - A "pick for me" dice button.
  - A vertical "QUEUE" label.
  - The live list `.rlv` (v251): the top games as 38px round covers with a progress ring and a short line, an endless vertical scroll loop.
  - A separator.
  - Add.
  - Settings.
  - Hide `»`.
- `S.railHide` slides it out (`translate3d(130%,0,0)`) and Nexi floats. `S.railSide = "left"` moves it to the left (`html.railleft`). Nexi can sit inside it (`html.nxseat`, padding-bottom 84).
- Motion: `transform .45s var(--ease), opacity .35s`. Its own layer (`will-change: transform`, `contain: layout paint style`).

### Screens, tabs, sub-tabs (`SUBS` etc., 3955-3969; `goTab` 4006, `goSub` 3994, `paintTab` 4578)
- Five tabs, with panels inside `.wrap`:
  | tab | sub keys → label | panel | default | saved in |
  |---|---|---|---|---|
  | `home` | none | `p-home` | | |
  | `games` | `queue` Queue, `lib` Library, `played` Played, `replay` Replay | `p-queue` / `p-lib` / `p-played` / `p-replay` (`SUBPANEL`) | queue | `S.gamesView` |
  | `plan` | `month` Month, `flow` Timeline, `plan` Plan, `timer` Timer | `p-cal` | month | `S.calView` |
  | `taste` | `psych` Psychology, `duels` Duels, `patterns` Patterns, `analytics` Analytics, `friend` Friend | `p-taste` | psych | `S.tasteView` |
  | `add` | none | `p-add` | | |
  Also `export` (`buildExport`).
- `SUBHOME` maps an old tab name to its tab: `goTab("queue")` → games/queue, `goTab("cal")` → plan.
- Sub-tabs switch through the floating view pill (`renderPill`). With `S.pill === false`, chips stand in (`subChips`).
- Overlays (not tabs):
  - Game page `#full` (`openFull(id)`, 9767; `position: fixed; inset: 0; z-index: 70`).
  - Settings / Customise / Edit sheet `#sheet` (`openSettings`).
  - Diary `#diary`.
  - Shuffler `#shuf`.
- Screen-change hooks: `onNav("before"|"after", (kind, args))` with kind tab / sub / game, and `onSettings`. Note (L143): the tab bar and rail call `paintTab` directly, so anything that must react to every tab change hooks `paintTab`.
- The big `header` (h1) shows only on Queue (`header.tucked` elsewhere, 4587).

---

## 5. Motion settings

- **`calm()`** (4537): true when `html.still` OR `prefers-reduced-motion: reduce`. Local copies at 13756, 14594 and 16600 also check `S.motion === false`.
- **Animations** `S.motion` (Settings › Motion › Animations, 6731): Off → `html.still` (3834). Line 1507: `html.still *, *::before, *::after { animation: none !important; transition: none !important; }`. Every CSS animation and transition is off, and JS entrances check `calm()` / `S.motion !== false` (the hub swipe 9809, the open card 9001, the cover-fly, rank-up, celebrations).
- **Reduced motion** (OS): line 2038 `@media (prefers-reduced-motion: reduce) { *, *::before, *::after { animation: none !important; transition: none !important; } }`, plus 25 local guards. Android: `Settings.Global.ANIMATOR_DURATION_SCALE == 0` or the accessibility "Remove animations" setting.
- **Battery saver** (20876-20885): `navigator.getBattery()`. When not charging and level ≤ 15%, it adds `html.still batsave` and Nexi says so, unless `S.batOff`. It clears on charge. Android: `PowerManager.isPowerSaveMode` plus a battery level ≤ 15% and not charging.
- **Rest when idle** `S.restOff` (24678-24700): after `REST_AFTER` = 60s without a touch (waits for scrolling to stop), every running infinite animation is paused (except `pnSpin`, `gDot`, `ncDot`, `pnShim` under 2s). It re-checks every 5s, and the first touch, key, scroll or return to the app resumes. This is separate from calm.
- **Speed boost** `S.fast` (default on, `html.fast`, 14615): solid backgrounds instead of `backdrop-filter` blur on the pill, react bar, back button, load bar and story, and fewer first-paint entrances. Native has no backdrop blur by default, so this is the native default.
- **Switching tabs** `S.tabFade` (6766; default set to `"slash"` in applyLook 3835):
  - `"slash"` (Persona slash, default) → `html.tabslash`. Two things run:
    1. `pnWipe(tab)` (7978): a fixed full-screen `.pnwipe` (z 9999). Its slab is black, skewX(-12deg), 160% wide, with a `--volt` stripe (6vw) and a `--ice` stripe (1.2vw, .8 opacity) on its right edge. It holds the tab's name in Archivo italic 900 uppercase, rotated -7deg, which pops in `pnwT` .22s `cubic-bezier(.2,1.5,.4,1)` from scale 1.3 + opacity 0. After a .12s delay the slab slides out `translateX(-140%)` over .36s `cubic-bezier(.75,0,.25,1)`. It is removed at 560ms (2674-2680).
    2. The new panel runs `slashin` .38s `cubic-bezier(.16,1,.3,1)`: opacity 0 → 1, translateX(-24px) → 0 (127-128).
  - `true` (Fade in) → `html.tabfade`: `cut` .28s `cubic-bezier(.16,1,.3,1)` from opacity 0, translateY(14px) scale(.99).
  - `false` (Instant): no class. The v110 direction slide still runs: `smSlide` (4572) adds `sm-r`/`sm-l` by tab order home, games, plan, taste, add. The panel slides from ±26px with an opacity fade over .42s `cubic-bezier(.16,1,.3,1)` (2506-2513, skipped under tabslash). **So "Instant" isn't instant.** This is a quirk to confirm with the owner.
  - All of these are skipped under still or reduced motion, or when `window.pnQuiet > now`.
- **Screen transitions** `S.vt` (Morph / Instant, 6752-6757): the View Transitions it was named for were removed (8732-8734, "the hub fades up instead"; `::view-transition-*` CSS at 725-742 is leftover). **Today it only gates the open queue card's entrance** (`.hero.heroin`, 9001): `.plate` from opacity 0, translateY(10px) scale(.985), .32s `cubic-bezier(.16,1,.3,1)`. The v299 card's own entrance runs about 1.9s (the stub tears at 1.4s). Port it as "Open card entrance on/off" or drop it (ask the owner).
- **Cover-fly** (v225, 21233-21298): opening a game from any cover clones that cover as a fixed image. The clone flies from the tapped spot to the game page's cover in 380ms `cubic-bezier(.3,.9,.35,1)`, with keyframes `translate+scale(src) rotate 0` → (offset .72) `scale(1.06) rotate(-6deg)` → `scale(1) rotate(-4deg)`. The clone has radius 16 and shadow `0 16px 30px rgba(0,0,0,.6)` plus a 2px white ring. The page's cover hides until it lands (1200ms safety; done at 900ms). It is skipped on calm, with `S.hubAnim === false`, in the tour, or on a swipe between pages. Compose: a shared-element style overlay with `graphicsLayer` translation, scale and rotation only.
- Other app-wide motion values:
  - First open (`html.first`, removed at 1.4s, 11580): `pnRise` .6s `--ease` from translateY(18px) scale(.98) + opacity 0, staggered .05s.
  - Rail drawer: .42s `cubic-bezier(.22,1,.36,1)`.
  - Rail tile entrance `pnRail` .55s.
  - Sheet: `translate` .42s `cubic-bezier(.16,1,.3,1)` (22185).
  - Selected chip/tab pop `pnPick` .45s from scale(.9) (2044). **This conflicts with #309 in spirit (a tap makes it scale), so don't port it.**
  - Common easings: `--ease` (.22,1,.36,1); (.16,1,.3,1) entrances; (.32,.72,0,1); (.2,.9,.25,1).
- Many legacy `transition: background-color/color/border-color` rules exist (2030, 2508). The rule for new work is transform/opacity only.

---

## 6. House rules → Android lint / test checks
| rule (source) | web check today | Compose check |
|---|---|---|
| No press animation, nothing shrinks, lifts or fades on touch (#309, v246, T14) | `kit.mjs syntax`: an `:active` rule with transform/scale/translate/rotate/filter/opacity (tools/kit.mjs 95-103); a runtime script also strips movement from `:active` (23571) | Detekt/Lint rule: forbid `collectIsPressedAsState()` feeding scale/alpha/offset/graphicsLayer. Forbid `Modifier.scale` / `graphicsLayer{scaleX}` keyed to an `InteractionSource`. Test: tap every button and screenshot-diff during the press (no change). |
| No ripple or glow under the finger (v280, T25) | `kit.mjs syntax` (`:hover` only inside `@media (hover:hover) and (pointer:fine)`), `touch-empty.mjs` | Provide `LocalIndication provides NoIndication` at the root and lint-ban `rememberRipple`, `ripple(` and `indication = ` other than `null`. Set `clickable(interactionSource, indication = null)` through one house `Modifier.tap{}`. Lint-ban material `Button`/`IconButton`/`Card(onClick)` without the house wrapper (they ripple). Disable overscroll stretch (`LocalOverscrollFactory provides null`, Plan gate 10) and the predictive-back shrink. |
| No hover looks on touch | as above | n/a (no hover on phone); ban `hoverable` |
| One still background layer (v273/v276, T21/T23) | `kit.mjs syntax` (only `html` var(--bgb) and `html.paper::before`); `bg-gradient.mjs`, `bg-colours.mjs`, `idle-motion.mjs` | One `AppBackground` composable at the root. Lint: no `.background(` with a Brush or animated value on screen-root composables, and no `Modifier.background` inside a `Scaffold` container colour other than Transparent. Screenshot test: the root at rest, two frames 1s apart, identical. |
| Colours only from tokens (v181, T2) | `design-check.mjs` hex, the audit `hex` budget | Lint: ban `Color(0x…)` literals outside `ui/theme/Tokens.kt` (generated from index.html per gate 7), except scene/status colours on an allow-list. |
| No emoji as icons (v297, T32) | review, `gIcon`/`GICON` | Lint: ban `Text("…")` whose content is emoji-only (regex `\p{Extended_Pictographic}`) used as an icon. Icons come only from a generated `GIcon` ImageVector set. |
| Icon format: 24×24 viewBox, fill-only `currentColor`, one part at `opacity=".35"` (v247/v297) | `GICON` (26958-27012, 52 entries incl. `_` gear fallback; `gIcon(name)` 27013); `UIC` (2842, `uiIc`); nav icons inline (3307-3312) | Generate ImageVectors at build time from GICON/UIC/nav SVG (gate 7). Lint/test: every vector is 24×24, every path has a fill of the tint colour, and its alpha is 1 or .35. `Icon(tint = LocalContentColor)` only. Every icon has a TalkBack `contentDescription` (gate 10). |
| Animate only transform/opacity, no looping blur, no stacked effects (Speed rule, T20) | `kit.mjs syntax`, `motion-audit.mjs`, `smooth`, `layers` | Lint: animated values (`animate*AsState`, `Animatable`, `InfiniteTransition`) may only be read inside a `graphicsLayer {}` lambda (translationX/Y, scaleX/Y, rotation*, alpha) or `Modifier.offset { }` lambda form. Ban animated `Modifier.size/padding/height/blur/background(color)`. Ban `Modifier.blur` inside an infinite transition. Compose stability report and recomposition counts in CI (gate 3). |
| Loops keep running mid-scroll (v282, T26) | `kit.mjs smooth` | Macrobenchmark/UI test: an infinite transition's frame clock keeps advancing during a fling. Never tie loops to `isScrollInProgress`. |
| No scroll-linked motion on list rows (v308) | review | Lint: no `graphicsLayer` reading `LazyListState.firstVisibleItemScrollOffset` inside list items. |
| Upright sentence-case headings, Poppins 500/600/700 (v188, T3) | `design-check.mjs` | Typography tokens only. Lint: ban `FontStyle.Italic` and `.uppercase()` in heading styles (allow-list: wrapped, celebrations, slash wipe). Ban `FontWeight` outside {Medium, SemiBold, Bold} for Poppins. |
| Short things stay whole (v292) | the eye (`broken`) | `Text(softWrap = false, maxLines = 1)` for dates, "16h" and tiers. Screenshot check at font scale 1.3 and 2.0. |
| Calm motion honoured | 48 `html.still` rules + reduced-motion | One `LocalCalm` (motion off ‖ animator scale 0 ‖ power save at ≤15%). Lint: any `animate*` / `InfiniteTransition` call site must be under a `calm`-aware helper (`houseTween()` returns `snap()` when calm). |
| No fill-forwards leftovers / cancel on every path (v215) | review | n/a in Compose (state-driven); test interrupted transitions. |

---

## Kotlin port checklist
1. `Tokens.kt` generated from index.html:
   - :root tokens (void, ink, steel, edge, volt, volt2, ice, mute, hot, tS).
   - `--u-*` tokens.
   - THEMES (6 + chameleon logic `chamTone`).
   - BG_BASES, BG_TINT `#2F6BFF`, BG_GRAD 30.
   - PALETTE (32).
   Fail the build if any table disappears or changes shape.
2. `AppTheme`: `CompositionLocal` for accent (volt/volt2 from `S.theme`, chameleon recomputed after sync); no Material You dynamic colour.
3. Background composable: base colour + two elliptical radial glows of `mix(tint, black, .35)` at alpha .9/.35 with the stops above, layer alpha `bgGrad/100`, hidden at 0. The status/nav bar colour = base, edge-to-edge.
4. Fonts bundled:
   - Poppins 400/500/600/700.
   - Archivo variable (or static italic 900 condensed and 800) for the slash wipe, celebrations and share images.
   - Playfair Display 700 (Shuffler only, a later phase).
   - Fallback fonts for JP/AR.
   Type scale table above as `TextStyle`s. Body 15sp/1.45. Calibrate sp = web px on the Fold (gate 4).
5. Shapes: `--u-r` 20, `--u-rs` 14, pill, 16/18/26/28/30 named shapes. Decide whether "How round" and "Chamfered" survive (in Clean they barely apply). Arcade style: propose "not ported".
6. Shadows as named elevations (row, primary button glow in accent, sheet, rail). Native shadows differ, so draw them as a `drawBehind` blur-free gradient or use `Modifier.shadow(spotColor)`, and screenshot-compare.
7. Layout by window width:
   - < 620dp: top icon tab bar in the scroll flow, 52dp items, a 66×62 current tile, the Home notch fusion, Settings gear last.
   - ≥ 620dp: right (or left) floating rail 72dp, inset 12dp, radius 30, with tabs, dice, live queue, Add, Settings, hide. Content padding 96dp on the rail side up to 1099dp. Column max width 880dp.
   - `S.nav = dock` floating pill (folded only).
   Handle split screen / cover screen widths (gate 10).
8. Navigation model: tabs home/games/plan/taste/add; sub keys and defaults as in the table, saved in gamesView / calView / tasteView; overlays game page / sheet / diary; `onNav` equivalents fire on every tab change, including taps on the bar and rail.
9. Motion kit:
   - `LocalCalm`.
   - The house easing `CubicBezierEasing(.22f,1f,.36f,1f)` and the entrance easing `(.16f,1f,.3f,1f)`.
   - Tab switch modes: slash default (wipe overlay + panel slide-in 24dp/.38s), fade (.28s), "instant" (v110 ±26dp slide .42s, or truly instant; ask).
   - Open-card entrance gated by S.vt.
   - Cover-fly 380ms.
   - Rest-when-idle after 60s pauses infinite transitions.
   - Battery ≤15% → calm.
10. Interaction kit: house `Modifier.tap{}` (no indication, haptic only where asked); global `NoIndication`; overscroll off; predictive-back preview without shrink.
11. Icons: generated ImageVectors from GICON (52), UIC and the five nav SVGs plus the gear. TalkBack labels. 48dp targets (the folded tab buttons are 52, rail tabs 48×44, so pad to 48).
12. Lint rules from section 6, plus screenshot tests at 412/859/360 and font scale 1.3/2.0, plus parity sheets against `kit.mjs look`.

## Risks / unknowns
- **Dead or misnamed settings**: `S.vt` ("Screen transitions: Morph") no longer morphs; `S.railW` sizes a nav that pnrail hides at ≥620; Arcade style and Chamfered corners are legacy; "Instant" tab switching still slides 26px. The Brief should ask the owner which to port and which to mark "not ported".
- **How round barely works in Clean** (equal specificity, ui2 later). Porting it faithfully means applying it only to legacy selectors, which is odd. Propose: drop it, or make it scale `--u-r`/`--u-rs`.
- Folded tab bar scrolls away with the page (no fixed bar, no safe-area padding). The owner may expect a fixed bar natively. Copy it as it is unless the owner says otherwise. The iPhone relies on black-translucent behaviour.
- Poppins 800/900 appear in about 60 legacy rules but no files exist for them, so browsers render 700/synthetic. The port should map 800/900 → Bold to look the same.
- Archivo italic condensed (wdth 62-78 via `font-variation-settings`) needs the variable font, or static cuts that match the condensed width.
- Elliptical radial gradients and `rgba(var(--bgt), a)` with the 35% darkening: exact parity needs a scale transform. Check with a parity sheet. Banding at low alpha on OLED may need dithering.
- Web px vs Android dp/sp: the Fold's Chrome zoom and font scale decide the mapping (gate 4 calibration). The 620/1099 breakpoints must be re-expressed in dp after calibration.
- Selected-chip `pnPick` scale pop (2044) and `.row:active` transition-duration rules are borderline with #309. Don't port any scale on selection.
- Chameleon theme depends on game data (played sessions, On deck), so it needs the data layer before the theme can resolve. Start with Midnight until loaded to avoid a flash.
- Rail live list `.rlv` and Nexi seated in the rail are endless loops. They must keep running mid-scroll (T26) yet pause after 60s rest, which is a battery budget item (T19).
- Some `html.still` CSS lines are local, so a few JS-driven effects may ignore `S.motion` and check only `calm()`. Audit per screen in later phases.
