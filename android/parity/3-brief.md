# Phase 3 · Home, the rail and widgets · the Brief (draft Nov 16 2026)

Phase 2 (the game page) is finishing with android-v45. Nothing of Phase 3 is built, not even the Map, until the owner
answers this. The Map then runs in a new chat with "ultracode" in the owner's message (L170). Web inventory: `renderHome`
11250 → `dxHome` 11115 (the top half) + More for you (`pnStack` / `pnHold` / `pnStackEdit` 11906-11990), Nexi's line on
top (`nxHomeLine` 13316), the Playing now colour cards (`dzPlay` 24615), the reveal (`pnReveal` 11692), the rail
(`pnRail` 11858) and its live strip (`rlvItems` 24306, v251), the opening reel (`#pnboot`, 2925 / 8129). The plan counts
about 4 build chats; each piece below reaches the phone as its own update, so you can try it the same day it's done.

Pictures sent with this (web app at 412 = folded, 859 = unfolded; the last one is today's Android app):
`p3-home-top-412.png` (the top of Home), `p3-home-down1-412.png` (Your queue, Your statistic, Playing now),
`p3-home-down2-412.png` (Next out) and `p3-home-down3-412.png` (More for you), `p3-home-top-859.png` and
`p3-home-down1-859.png` (Home unfolded, with the web rail and its live strip on the right), `p3-android-rail-859.png`
(the Android rail as it is now: tabs, the open tab's views as words, Add, Settings, Nexi seated).

Already decided (Decisions):
- The rail's full build waits for your pick (Nov 5): question 1 below. Nexi's seat in it is done (Nov 7, android-v26).
- The Duel button stays hidden until Phase 6 brings Duels (Nov 10): the big card's "Settle a duel" button and the
  Settle a duel tile's tap wait for Phase 6; the Duels and Standings tiles still show their numbers.
- The hub figure (the cut-out character) is not ported (Nov 10): where Home stands a character (behind the big card,
  stepping out of the Next out ticket, standing in a Playing now card), Android shows the game's own picture, as the
  web already does for a game without one.
- Widgets are new (Oct 23) and stay read-only until switch day: a tap opens the app, it never changes a game.

## The pieces (each one comes to the phone as its own update)
1. **The top of Home.** Nexi on top with his line ("play this next", and why) and your top 3 as three buttons. Then
   the Play next (N) / Played (N) tabs, the game search box (type a few letters, up to 6 games show, Enter opens the
   first), Shuffle and Customise. The big "Play this next" card: the game's picture and logo, Hype, the dice, the play
   button, "#1 of N"; and the next four games under it with their date and genre. Try: type part of a game's name and
   open it from the list; tap the big card.
2. **The middle tiles.** Your queue (the big number of games waiting with covers floating round it; a tap opens the
   Queue), Playing now (three colour cards, each game in its own colour with its big name and how far you are; New or
   Classic look), Your statistic (queue hours as a ring, about how many evenings, Plan evenings), Next out (a tilted
   ticket with the next release and a live days / hours / minutes / seconds countdown, Open game). Try: watch the
   countdown tick, tap a Playing now card.
3. **More for you.** Two card stacks you swipe through (one up and down, one sideways), with dots to jump. They hold the
   rest of Home's 18 tiles, each by its name on screen: Your read, What decides it, Duels, Standings, Settle a duel,
   Needs a verdict, With Nh tonight (Tonight), Hype trap (or "You're underselling it"), Furthest from your hype, Genre
   lean, Where the year bunches up (Year), Play sessions (only with the timer on), the year so far / wrapped, the month
   so far / recap, and Where your hours go. Every card sits on its game's picture. Hold a stack half a second to choose
   which cards it holds and in what order (Edit this stack: up, down, Shown / Hidden, Reset, Done). A tap on a card
   whose screen comes in a later phase (the timer, wrapped and recaps, Plan, Played: Phase 5; Taste: Phase 6): see
   question 4. Try: hold a stack, hide two cards, move one to the top.
4. **Pick for me.** The dice on the big card, the "Not sure? I'll pick one" bubble and the rail's dice pick one of your
   top 3 (never the one it picked last time). The reveal: a flash and a slash, the game's picture and name slam in
   under "TONIGHT'S PICK", the screen shakes, then the picture cracks into shards that fly off onto the game's page;
   its own sounds (hit, slash, tick, slam) behind your Sounds switch and a short buzz. With calm motion it goes
   straight to the page. Try: tap the dice three times.
5. **The opening reel.** A glowing pixel when the app opens; a tap plays the 15-second reel on the beat with its own
   music (words, shapes, a 3D shelf of your own covers with the game you're playing in the middle, your week in
   numbers, the play button), Skip at any time. Settings › This build › Opening reel: Every launch or Off; off by
   itself with calm motion. Try: open the app from cold and let it play.
6. **The unfolded rail, finished.** What you pick in question 1, plus the dice ("Pick one for me") the web rail has.
   Try: unfold, glance at the rail, tap a tile in it.
7. **Widgets.** The ones you pick in question 2, on the home screen (and on the Fold's cover screen if Samsung allows
   it). They update by themselves (your backup changes, midnight, a release day). Try: add each widget, fold the phone.

Then the check (Perfect, in a fresh chat: L262) and "Approve Phase 3 on your phone".

## Home's switches today (Customise on Home, web)
- **Tiles on Home:** 18 on / off chips (Next out, Your read, What decides it, Duels, Standings, Settle a duel, Needs a
  verdict, Tonight, Hype trap, Furthest from hype, Genre lean, Queue hours donut, Year, Queue count, Playing now, Play
  sessions, Year wrapped, Monthly recap). A tile also hides itself while it has nothing to show.
- **How tiles look:** tint tiles with the game's own colour (Yes / No); the counts under the title (Show / Hide).
- **Playing now cards:** New (each game's colour, big name) or Classic (cover, stars, progress bar).
- **The standings tile:** how many places it lists (2 to 10).
- **Each stack's own order and hidden cards** (the hold, piece 3).
The usage counts (the weekly "what gets used" issue) couldn't be read from this chat, so there are no "never tapped"
hints this time.

## Questions
1. **The rail's live strip (unfolded).** Today on the web, under the tabs, a column of small round tiles drifts slowly
   upward forever: what you're playing (how far, "13d left"), the next releases ("9d until out"), a watched game on
   sale ("−30%"), up next, the rest of your queue, your backlog hours. Touch holds it, a drag scrubs it. On Android the
   rail already lists the open tab's views as words, so the strip gets the room under them. What should it show?
   - a. The web's strip as it is, drifting forever (suggested: it was your own ask in v251, "live animation … that
     doesn't end").
   - b. The same tiles standing still; you scroll them with a finger, nothing moves on its own.
   - c. Just three bigger, still tiles: what you're playing, the next release, up next.
   - d. No strip: the rail stays as it is now (tabs, views, Add, Settings, Nexi), plus the dice.
2. **Widgets.** Picture your phone's home screen. Which of these do you want there?
   - **Play next:** your top game's picture, name and headline ("Ready to play"); a tap opens its card.
   - **Countdown:** the next release, "Out in 3 days"; it ticks over at midnight.
   - **Nexi:** his face and one line ("play this next"); a tap opens the app (his chat comes in Phase 8b).
   - **Top 3:** a bigger one with the Queue's first three.
   All four (suggested), or only some. The Fold's cover screen gets them too if Samsung lets an app put widgets there.
3. **Home's switches.** All of the list above comes over as it is (suggested). Or leave out Playing now's Classic
   look, the way On deck's Classic was left out on Nov 2, so the cards are always the colour ones.
4. **Tiles whose screen isn't built yet.** Say you tap "Year so far" on Home in December, but the year's recap only
   arrives with the Played update (Phase 5). Should Home:
   - a. show every tile now, and a tap on one like that says in one line which update brings it (suggested), or
   - b. keep each of those tiles off Home until its screen comes, so nothing on Home leads nowhere.
5. **The opening reel on Android.** Each time the app opens from cold, the glowing pixel waits for your tap and the
   15-second reel plays. Keep it as on the web, every launch with an Off switch (suggested), or show it only the first
   time each day?
