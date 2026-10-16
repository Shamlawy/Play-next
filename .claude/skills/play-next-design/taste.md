# The owner's taste log

Every look call the owner (or the friend) made: their words, the rule it means, what checks it. Newest last. Written with
`node tools/taste.mjs add …` (never by hand, so the format stays readable by `taste.mjs for`, which design-check.mjs runs).
`~` marks a paraphrase. Screenshots (`shot:`) are of the made-up seed games only: the repo is public.

### T1 · v174 · whole app · pick
- said: ~shared a Dribbble game dashboard: copy its layout and style, not its colours
- rule: When the owner shares a reference, copy layout and style, keep our black and blue, and write the 5-point brief first.
- check: none (the brief in SKILL.md §2; pairs in references/)

### T2 · v181 · whole app · rule
- said: ~one-tap themes must recolour everything
- rule: Accents go through var(--volt) / rgba(var(--vrgb), a), never a hex accent in CSS.
- check: node tools/design-check.mjs (hex), audit.mjs hex budget

### T3 · v188 · whole app · rule
- said: ~"Clean" style
- rule: Upright sentence-case headings, no italic/uppercase titles, one dark surface plus a hairline, Poppins 500/600/700, corners 20/14px.
- check: node tools/design-check.mjs

### T4 · v194/v210 · header cards · pick
- said: ~liked the header card but found it empty
- rule: A screen's header card carries live content (numbers, a spotlight line, a chart), not just a title.
- check: none

### T5 · v199 · whole app · rule
- said: he still misses the problems I run into, even design ones
- rule: Nexi spots look problems himself (cut-off text, flicker, faint text, wrong-way motion).
- check: bugLook / bugLayout; kit.mjs sweep --plant --expect

### T6 · v209 · game page · hate
- said: 4K picture keeps showing low-res
- rule: Big pictures use the sharp art, shown at the size they're displayed.
- check: kit.mjs sw, kit.mjs layers

### T7 · v226 · Add game page · pick
- said: replace PC / Switch / PS5 with logos
- rule: Platforms show as logo chips, not names.
- check: none

### T8 · v232 · Queue · hate
- said: each queue card is a different size when open
- rule: Open cards use fixed slots: every open card is the same height at a width.
- check: none

### T9 · v232 · Add game page · hate
- said: the New game page lacks organisation: each game looks different because of its status
- rule: A game page uses one skeleton for every state; only the content changes.
- check: none

### T10 · v233 · game page · hate
- said: looks good but too many buttons and stuff, make it better and smarter
- rule: One smart main action chosen by the game's state; the rest tucked behind a menu.
- check: none

### T11 · v234 · game page · pick
- said: ~it only shows 2-3 genres, tap to show all?
- rule: A cut-short list gets a +N tap that unfolds the rest in place.
- check: bugLook (cut-off text)

### T12 · v236 · Similar sheet · hate
- said: the pictures are too small and I need more description
- rule: Pictures and descriptions in pick lists are big enough to decide from.
- check: none

### T13 · v242 · game page · hate
- said: it turns opposite of my swipe, I hate that
- rule: A drag turns things the way the finger goes.
- check: rmDirCheck

### T14 · v246 · whole app · hate
- said: #309 remove the touch animation, when I touch anything an animation happened, delete that
- rule: Nothing shrinks, lifts or fades on press; no :active rule moves anything.
- check: node tools/kit.mjs syntax

### T15 · v247 · whole app · pick
- said: ~picked a mix of two icon sets drawn in the real nav
- rule: Settle look questions with demos in the real screen at 412 and 859 and let the owner pick.
- check: none (a process rule)

### T16 · v251 · Nexi · pick
- said: change them into something useful with live animation that is smooth buttery and doesn't end; Nexi needs to get smaller to fit the border of the rail
- rule: Rail content is useful and loops smoothly; Nexi fits inside the rail.
- check: kit.mjs smooth, bugLayout

### T17 · v255 · Nexi · pick
- said: remove the hand, the card comes to you from far … then the card animates a lot
- rule: The Nexi card flies in from far and plays a lot; no hand.
- check: none

### T18 · v259 · game page · hate
- said: the color thing in the game page I can't spot it
- rule: A visual effect must be clearly visible, not subtle.
- check: none

### T19 · v265 · whole app · rule
- said: regular apps are smooth… I don't want the app to be a monster of battery and RAM
- rule: Smoothness first, with battery and RAM kept low.
- check: kit.mjs perf, smooth, layers

### T20 · v271 · whole app · rule
- said: smoothness is rule number one, but I do like animations
- rule: Keep animations, transform/opacity only.
- check: kit.mjs syntax, motion-audit.mjs

### T21 · v273 · whole app · hate
- said: return the background to a solid gradient color, I hate how it looks
- rule: No moving lights, drifting art or specks; one still gradient.
- check: kit.mjs syntax, bg-gradient.mjs, idle-motion.mjs

### T22 · v274 · Nexi · hate
- said: remove all and fix 1 2 3 (two Nexis on Home, idle fidgeting, art backdrop)
- rule: Never two Nexis on one screen; Nexi doesn't wander or talk at random.
- check: calm-nexi.mjs, idle-motion.mjs

### T23 · v276 · whole app · pick
- said: make our app background black with a little blue gradient but mostly black
- rule: html = base colour (black), html.paper::before = one soft blue glow at S.bgGrad %, nothing else.
- check: bg-gradient.mjs, kit.mjs syntax

### T24 · v277 · Settings · pick
- said: give different colours to it, not all black, but default black
- rule: Real background colours, black first and default, each dark enough for white text.
- check: bg-colours.mjs

### T25 · v280 · whole app · hate
- said: remove the touch glow and any touch effects when I touch an empty spot
- rule: Nothing appears under the finger; :hover only inside @media (hover: hover) and (pointer: fine).
- check: kit.mjs syntax, touch-empty.mjs

### T26 · v282 · Nexi · hate
- said: nexi animation and rail stops its bad … I dont want animation to stop while schrolling
- rule: Endless transform/opacity loops keep running mid-scroll; never pause them.
- check: kit.mjs smooth

### T27 · v286 · Nexi · pick
- said: ~do all three (HP bar only with Grumble on, sweat drop and tag shine stop looping)
- rule: Hidden features don't show; decorative loops end after a few plays.
- check: audit.mjs endless-animations, idle-motion.mjs

### T28 · v290 · Queue · hate+pick
- said: the logos look ugly … ensure you can pick those ugly design or disproportionate stuff; make A
- rule: Row logos sit upright in their own slot inside the row, same visual size, near-black ones drawn light, never past the row or screen edge.
- check: logo-look.mjs, Nexi's lfLook, kit.mjs eye
- shot: references/queue-logos/after.jpg

### T29 · v292 · whole app · rule
- said: the design skill that keeps learning (Oct 2026)
- rule: Repeated things on any screen never spill, overlap, break short words, squash pictures or barely show.
- check: node tools/kit.mjs eye
