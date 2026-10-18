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

### T29 · v293 · whole app · rule
- said: the design skill that keeps learning (Oct 2026)
- rule: Repeated things on any screen never spill, overlap, break short words, squash pictures or barely show.
- check: node tools/kit.mjs eye

### T30 · v296 · Nexi · hate
- said: double tapping on nexi shows emoji and other commands, remove the emoji and the commands shorten them and organise them better
- rule: Nexi's menu is one small card in a few named groups of short plain-word buttons; no emoji buttons, no rings of icons
- check: tools/scenarios/nexi-wheel.mjs

### T31 · v297 · Settings · pick
- said: B looks very sick (the icon rail of four Settings rail demos)
- rule: Settings and Customise are one scroll of open groups with a sticky On this page rail of group icons: the lit icon (bar + tile) follows the group under the title and its name pops out for a moment.
- check: tools/scenarios/settings-rail.mjs

### T32 · v297 · whole app · hate
- said: I hate the icons they look like default ugly icons I actually hate them across the app
- rule: No emoji as icons: draw them in the v247 menu style (24x24, fill-only currentColor, one part at 35% opacity); Settings groups use gIcon / GICON.
- check: none

### T33 · v298 · Queue · hate
- said: I hated sharp corners; the info is kinda blocking the back drop art
- rule: The open Queue card has rounded corners everywhere (cover, slab, stat blocks, pills) and the backdrop picture fills the whole card, darkened only near the bottom.
- check: none

### T34 · v298 · Queue · pick
- said: D and make the pressing on the card flips it no need for a switch button; pressing on the art cover should take us to the game pages so remove the button
- rule: Open Queue card = round 6 look D (Stamp): full picture, A's blue slab + cover drop, a round status stamp on the cover corner; the front shows only status, logo, platform, genres, no buttons; tapping the cover opens the game page, tapping anywhere else turns the card; dates, numbers, scores, Edit and More live on the back.
- check: none

### T35 · v298 · Queue · hate
- said: looks bad … the colour thing behind the cover art should have the game colour not fixed blue … super ugly … the logo I want it bottom right fixed … info should look artistic
- rule: The open Queue card takes its colour from the game (g.colour / --accent), never a fixed blue; no round stamp that looks like a score when there is none; the logo lives in one fixed bottom-right slot on every game; info is set as designed type, not scattered chips.
- check: none

### T36 · v298 · Queue · pick
- said: I like B so much but I liked the animations and style of the colour behind the box art … I want it top left … the ps5 or switch should be logo … add something bottom left … be creative with the animations and designs and sounds
- rule: Open Queue card builds on round 7 B: box art top-left on v298's swept slab in the game's own colour, platforms as logos, the game logo fixed bottom-right, a designed element bottom-left; the entrance may carry its own quiet synthesised sounds (following Settings › Sounds).
- check: none

### T37 · v298 · Queue · pick
- said: I like A so much but like the sound from C also the strap of colour behind the game art is so long shorten it … new better flip animation … double tap to flip and one tap to close … the info on the strap make it better looking
- rule: Open Queue card = round 9: round 8 A (Sweep: game-colour strap behind top-left box art, ticket bottom-left, logo bottom-right) with C's ink sounds; strap ~72% folded / 54% unfolded carrying status dot + queue spot, one plain headline, platform logos, genres; double tap turns the card (same direction, slight overshoot, light sweep), one tap closes it into its row, the box art opens the game page.
- check: none

### T38 · v298 · Queue · pick
- said: Perfect now the ticket snaps if the game is released if not it stays intact
- rule: Round 9 approved. The bottom-left ticket of a released game (out, playing, played) tears: the stub snaps off at the perforation and droops (with a tear sound); a coming game's ticket stays whole.
- check: none

### T39 · v299 · Queue · hate
- said: the backside info looks bad … it looks bad and plain … show me 3 demos
- rule: The open card's back must carry the front's language (game colour, drawn icons, designed type), not grey stat tiles, an all-caps kicker, text platform chips and a loose Edit/⋯/arrow row.
- check: none

### T40 · v299 · Queue · hate
- said: I like B idea but hate the hexogram or whatever so be creative again
- rule: The card back keeps round 10 B's idea (one picture of the eight areas with the score in it, the four numbers beside), but never a radar/octagon chart; round 11 offers petals, a bezel and a constellation.
- check: none

### T41 · v299 · Queue · hate
- said: Nah they are all good but didn't like, I liked the creativity though so try again
- rule: Petals, bezel and constellation backs were good but not liked; round 12 tries game-world pictures (RPG status screen with a rank letter, mixing desk faders, potion flasks) for the eight areas.
- check: none

### T42 · v299 · Queue · pick
- said: C is very very sick but it doesn't match our theme cuz its a game app
- rule: Card back: keep round 12 C's idea (each area fills with the game's colour, bubbles, the score in a big vessel), but the vessels must be gaming things (controller and buttons, cartridges, energy cells), not fantasy props like potions.
- check: none

### T43 · v299 · Queue · pick
- said: I like A so much so keep it but I want extra 3 demos for cards like yugioh cards and other
- rule: Card back: round 13 A (controller fill) is the owner's pick so far; trading-card layouts (duel, monster, arcane) are being tried as extras, layout only, no real names or logos, the game's colour on black.
- check: none

### T44 · v300 · Queue · pick
- said: Nah A it is but fix the numbers on the controller they're design is kinda off and build and merge
- rule: The open card's back is round 13 A, the Controller: score on the controller's little screen (never over the D-pad), each area's number inside its round button, the four numbers underneath.
- check: tools/scenarios/queue-card.mjs (score on the screen, numbers inside the buttons)

### T45 · v299 · Queue · hate
- said: I really don't like how the queue looks when the cards are closed, looks kinda ugly and meh for my unfold; I like the header card
- rule: Closed Queue rows must use the unfolded width (no long empty gap between the facts and the logo); keep the header card and chips as they are; round 1 offers A Strap, B Cartridge, C Save slots, D Console shelf.
- check: none

### T46 · v299 · Queue · pick
- said: Upgrade A make it better with the colour and design and keep D make 2 new ones also in all upgrade the things on top … the everything and sort by and my order and boss and stuff so make it better
- rule: Round 2: A (Strap) gets more of the game's colour and design, D (Console shelf) stays an option, two new looks (E Quest line, F Character select); the chips under the header become one control deck in every look (filter tabs with counts, one Order button with a grouped menu, Shuffle that picks in place, Boss as a live HP meter).
- check: none

### T47 · v301 · Queue · pick
- said: A and the new bar, make it and F too as a second option if we want to switch to it
- rule: Closed Queue rows are round 2 A (Strap: spot + box art on a slanted strap in the game's colour, picture behind, kicker, name, headline, torn ticket, progress line, logo slot) with one control bar (tabs with counts, Order menu in groups, Shuffle picks in place / hold for the shuffler, Boss HP); Character select is the second look in Settings › Queue cards.
- check: tools/scenarios/queue-rows.mjs

### T48 · v303 · Queue · hate
- said: on deck some games have a tall image and some wide so fix
- rule: On deck's three cards share one shape: picture on top, same size and place in each; #1 differs only by its button word
- check: node tools/kit.mjs run tools/scenarios/genre-filter.mjs --widths 412,859

### T49 · v305 · Queue · hate
- said: the numbers or version looks bad and into each other
- rule: A platform version (4·Vita, One·360) sits in its logo's own box as a raised label, never overlapping the next logo; a long platform list wraps inside the strap.
- check: node tools/kit.mjs run tools/scenarios/card-turn.mjs --widths 412,859,360

### T50 · v305 · Queue · hate
- said: double tapping the card has a slow animation and shift and too slow
- rule: The open Queue card turns straight over in about 420ms, no swing past and back.
- check: node tools/kit.mjs run tools/scenarios/card-turn.mjs --widths 412,859,360
