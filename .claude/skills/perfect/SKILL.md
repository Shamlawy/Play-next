---
name: perfect
description: Perfect one Play next feature with a team of helper Claudes (ultracode). Use when the owner types /perfect <feature>, "perfect the <feature>", or "ultracode perfect <feature>". Runs the saved perfect-feature workflow (map, 6 review lenses, a skeptic per finding, a completeness check), then fixes every proven defect, tests, releases and merges.
---

# Perfect a feature

The owner typing `/perfect <feature>` (or "perfect the <feature>") is their opt-in to a multi-agent workflow: run it without asking again.

## 1. Start

- No feature named → ask once as a ❓ choice from the app's screens (top tabs Home / Games / Plan / Taste / + / Settings): Home (Nexi's pick, Play next list), Queue (open card, closed rows + bar, On deck), Library, Played, Replay, Plan (Month, Timeline), Taste (Insights, Duels, Friend, Book), a game page, Settings + Customise, Nexi (menu, chat, problem reports), the What's new tour. One feature per run: a whole screen is several.
- First line of the reply, in the Heads-up box: `⏱ About 1–2 h: the review runs ~30–60 min (should be done around HH:MM UAE time), then I fix, test and release.` (`TZ=Asia/Dubai date +%H:%M`).
- Run the workflow: `Workflow({ scriptPath: ".claude/workflows/perfect-feature.js", args: { feature: "<feature as the owner said it>", widths: "412,859,360" } })` (by name `perfect-feature` works too when `.claude/workflows/` is loaded). It runs in the background; wait for its notification, don't poll.
- Stages set their own effort (map + review high, every proof xhigh), whatever the chat's effort is. The main chat at high is right for the fixes.

## 2. When it returns

- `toFix`: proven defects, most severe first. Fix them yourself, one at a time, keeping every CLAUDE.md rule (the feature pause allows fixes; check its date). Use the `fix` field as the starting point, not as gospel: re-read the code first.
- `needsWebkit`: iPhone findings nobody could reproduce here: prove them with webkit-check.yml (its `scenario` input) before fixing.
- `ownerDecides` (taste, new looks, new features): never build them unasked. During the feature pause each goes in `node tools/learn.mjs add --kind=idea`; after it, list them for the owner as a ❓ choice.
- `notChecked` / `lensesLost`: say in one line what wasn't covered.
- A problem Nexi could have caught himself → teach him (CLAUDE.md "Problem reports").

## 3. Ship

The normal release: bump `APP_V`, a `WHATS_NEW` entry and a docs/LOG.md line when something visible changed, `node tools/kit.mjs all`, `node tools/kit.mjs look` (Read every changed picture), merge main in, open the PR, merge it when app-check is green (the owner: "Always merge auto"). Then tell the owner in a short reply: what was fixed, what waits for them, and what to open on the phone.
