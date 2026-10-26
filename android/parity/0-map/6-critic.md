# Map 6: skeptic and completeness critic (Phase 0, web v327)

I checked the five mapper reports against index.html (28,868 lines), worker/helper.js, a live Maven artifact and the live Android reference page. I didn't edit any repo file.

---

## 1. Spot-checks of the most load-bearing claims

| # | Claim (report) | Verdict | Evidence |
|---|---|---|---|
| 1 | Key derivation: `id = sha256hex("pn-vault-id:"+c)[0,24]`, `tok = sha256hex("pn-vault-tok:"+c)`, `key = sha256("pn-vault-key:"+c)` raw AES-256, with no salt or KDF (1-data §1.3) | **CONFIRMED** | index.html 19018-19022 |
| 2 | Sealed format `[1][z][iv 12][ct+tag]`, AAD `[1,z]`, gzip when z≠0, a first byte ≠1 gives "a newer kind of backup" (1-data §1.4) | **CONFIRMED** | 19024-19040. `cbOpen` decompresses whenever `b[1]` is truthy, so the Kotlin `z != 0` check matches. |
| 3 | `cbNorm`: upper case, strip non-alphanumerics, drop a leading `PN` when the length is 22, require 20 characters from the alphabet (1-data §1.2) | **CONFIRMED** | 19008-19009 |
| 4 | Every vault call needs `Origin` exactly `https://shamlawy.github.io` (else 403 `origin`); id must be 24 hex, tok 64 hex; `list` returns `[]` with no token check when nothing is stored (1-data §1.6) | **CONFIRMED** | helper.js 41, 495-543 (497 origin, 499 format, 504 pub fields, 519 v cut to 8, 525-528 list) |
| 5 | `bugTake` keeps a fixed field list, so `app` is silently dropped; `v` is cut to 8 characters, `sig` must be 8 lowercase hex (3-kit §6, 4-ci §2.1) | **CONFIRMED** | helper.js 1283-1306 (the sample object at 1289-1291) |
| 6 | `/bug` accepts calls with no Origin and rejects only a wrong one; `/announce` and `/nudge/key` have no Origin check, and `/nudge/key` creates the VAPID key (1-data §3) | **CONFIRMED** | helper.js 180, 134-137, 284-287 |
| 7 | compileSdk 37 and AGP ≥ 9.1 are forced by `androidx.core:core:1.19.1` (5-toolchain §1) | **CONFIRMED** | I downloaded `core-1.19.1.aar`: `minCompileSdk=37`, `minAndroidGradlePluginVersion=9.1.0`. BOM 2026.09.00 pins ui/ui-android 1.12.1. Only true if we take the newest libraries, which we should. |
| 8 | Silent self-update rules: `USER_ACTION_NOT_REQUIRED` + target floor (34 on 16, 35 on 17) + the installer "updating itself" qualifies (5-toolchain §3) | **CONFIRMED** | The live reference text matches word for word. |
| 8b | `setRequestUpdateOwnership(true)` on the first self-update makes Play next the update owner (5-toolchain §3) | **WRONG** | The reference says: "can only be enabled on initial installation. Set this to true on package update is a no-op", and it needs `ENFORCE_UPDATE_OWNERSHIP`. **Drop it.** "Updating itself" is enough. |
| 8c | The first self-update asks because Chrome is the installer of record (4-ci risk 8) | **WRONG as stated** | "Updating itself" qualifies whoever the installer of record is. The one-time ask is the "Install unknown apps" grant for Play next (`canRequestPackageInstalls`). The plan's "asked once" holds for that reason. Still prove it on the phone. |
| 9 | The header card is on 7 panels (Queue, Library, Played, Replay, Plan, Taste, Add), not Home; Clean style only (3-kit §3) | **CONFIRMED** | `SHX_PANELS` at 16829 (the report says 16827). Note: the extras chips use emoji (`SHX_X`: 📊 🟦 🖼️, 16860-16862), which the app's no-emoji rule must replace with drawn icons. |
| 10 | Taste views are psych / duels / patterns / analytics / friend, default psych (2-look §4 table) | **WRONG** | v205 replaced them: `SUBS.taste = insights, duels, friend` and `SUBDEF.taste = "insights"` (18463-18464). Book is added at 28816. Old values are mapped to insights through `extend("setSub")`. 3-kit §4a is right. |
| 11 | `?nudge=next` (the "What should I play?" shortcut) opens On deck #1's game page directly, with no reveal and no randomness (3-kit §4d) | **CONFIRMED** | 13737-13744 |
| 12 | Friend sync is a second writing host (`SYNC_URL` 8083), and `frAuto` publishes on every save when `S.syncOn` is set (1-data §3) | **CONFIRMED** | 8083, 8088 |
| 13 | The web app stores the restore code in the password manager (1-data, plan "may fill it in") | **CONFIRMED, with a catch** | 19164-19165: `PasswordCredential({id:"Play next backup"})` is saved for the **origin shamlawy.github.io**. Google autofill offers a website's password inside an app only when Digital Asset Links tie the two together (see gap G9). |

Smaller drifts (harmless): SHX line numbers are off by two in 3-kit. 4-ci quotes bugTake as 1284-1306. 5-toolchain's sample workflow uses `actions/*@v7` while the repo uses `@v4`; either is fine once SHA-pinned.

---

## 2. Completeness: Phase 0 items no report covers well enough to build from

Each gap lists the fact or decision needed and where to get it. "Claude's call" means it doesn't need the owner.

**G1. Safe mode (not covered at all).**
- Decide:
  - Put a crash counter in a tiny file written first thing in `Application.onCreate`.
  - A `Thread.setDefaultUncaughtExceptionHandler` writes the report to `filesDir/crash/` and bumps the counter.
  - The counter clears after 60 s of stable running.
  - Two crashes within 60 s of start launch `SafeModeActivity`. It's a plain View-based activity with no Compose and no data load, and it lives in the same process. It does three things only: update check and install, send the saved reports, "Try again".
  - The WorkManager periodic update job is the backstop when even the activity can't start.
- Source: the plan's "Never stuck" section plus the Android docs. Claude's call.

**G2. Crash capture and exit reasons (not covered).**
- At start, read `ActivityManager.getHistoricalProcessExitReasons(pkg, 0, 10)` newer than a stored timestamp (bookkeeping store).
- Map the reasons:
  - `REASON_ANR`: kind `stuck`, wording "Opening the app froze for Nms" / "The app froze on X". Attach `traceInputStream`, first lines, scrubbed.
  - `REASON_CRASH` / `REASON_CRASH_NATIVE`: kind `error`.
  - `REASON_LOW_MEMORY` / `REASON_EXCESSIVE_RESOURCE_USAGE`: log-only kind (bugsync needs a KIND entry or a held rule).
- Call `setProcessStateSummary(screen)` so each exit record says which screen was open.
- R8: use `-keepattributes SourceFile,LineNumberTable`. I recommend `-dontobfuscate` too: shrinking and optimising still run, and stacks stay readable without retrace. Keep `mapping.txt` anyway as a **release asset** (public repo, class names only); Actions artifacts expire.
- Debug-only StrictMode and LeakCanary run in the CI emulator tests, since debug builds never reach the phone.

**G3. The 120 Hz probe screen (only the APIs are covered, not the screen or a pass rule).** Proposal:
- **Where:** Settings › This build › "120 Hz test". It also runs once by itself on the first open of the probe release.
- **Contents:**
  - the real background composable;
  - a LazyColumn of about 40 full-bleed picture cards (from the owner's backup when it's loaded, else bundled repo images), each with a skewed strap at alpha 0.85 in the game's colour;
  - one endless transform/alpha loop (blink-like) on top;
  - a scripted 10 s fling (`animateScrollBy` with decay), then 5 s idle.
- **Records:**
  - Choreographer frame intervals;
  - `display.refreshRate` and `supportedModes`;
  - JankStats jank %;
  - FrameMetrics p50/p90/p99;
  - `ProfileVerifier.getCompilationStatusAsync()` (fully compiled or not);
  - folded/unfolded state;
  - Android version and One UI build;
  - whether Motion smoothness is Adaptive (inferred: no mode above 60 Hz).
- **Pass:**
  - ≥ 95% of fling frames at ≤ 8.9 ms;
  - p99 < 16.7 ms;
  - refresh 120 during the fling.
- **On a fail:** retry with `Modifier.preferredFrameRate(FrameRateCategory.High)`, then `preferredDisplayModeId`.
- **Sending:** the result goes up as a report of kind `probe` (log-only in bugsync) and shows on screen.

**G4. Text calibration method (not covered).**
- Fact needed: on the Fold, does Chrome's CSS px equal Android dp, and what are Chrome's text scale and the system `fontScale`?
- How to get it: the probe also sends `screenWidthDp`, `densityDpi` and `fontScale`. Compare them with the web reports' `w`/`dpr` from the same phone (already in the bug KV / issues, e.g. w = 412/859).
  - If `screenWidthDp` equals the web `w`, then 1 px = 1 dp, and text px maps to sp at fontScale 1.
  - Text: Chrome's Accessibility › Text scaling follows the system size by default, so sp works.
- The cap rule (Claude's call): fixed-size things (tab labels, header stats, straps, tickets) use `fontScale.coerceAtMost(1.3f)`; body text scales freely.

**G5. Keystore storage of the restore code (only partly covered in 5-toolchain).**
- Decide:
  - an AES-GCM key in AndroidKeyStore (StrongBox with a fallback, **no** user-auth requirement, or a fingerprint lock appears: item 12 was not picked);
  - IV + ciphertext in a small file;
  - the derived `tok`/`key`/`id` in memory only;
  - the RAWG key, `bookKey` and the raw state kept in app-private storage.
- If the Keystore key is invalidated (lock screen reset), ask for the code again.

**G6. Cleartext off and Android backup off (one line each, no specifics).**
- `android:usesCleartextTraffic="false"` plus a network-security-config with `cleartextTrafficPermitted=false`.
- `android:allowBackup="false"` **and** `android:dataExtractionRules` excluding everything for both `cloud-backup` and `device-transfer` (API 31+ ignores allowBackup for device-to-device transfer).
- **New fact:** Chrome auto-upgrades `http://` images on an https page. Old saved games with `http://` picture links show on the web, so the picture resolver must rewrite `http://` to `https://`, or those pictures go blank natively.

**G7. Splash (covered only as a library).**
- Settle the conflict between "stays up while the backup loads" and "opens instantly from the last copy":
  - the splash holds only until the **local** copy is parsed (or the restore-code screen is ready);
  - never while waiting on the network;
  - black `windowSplashScreenBackground`, a static icon, no exit animation.

**G8. The practice-copy rules in the UI (data side covered, behaviour not).**
- "Edits reset when the app reopens" conflicts with "re-reads the backup whenever you come back". Proposal:
  - edits live in an in-memory overlay;
  - on resume, re-read; if the cloud copy is newer, replace it, drop the edits and say so in the line;
  - otherwise keep the edits until a cold start.
- The thin line text: "Practice copy: changes reset when the app reopens".
- The "as of" line, and the over-a-day warning.
- Name: "Play next ·new", with the mark drawn into the adaptive icon foreground (generated from `logo-512.png`). The label changes in a later update (Phase 10) with no reinstall. Nobody mapped the icon assets: `logo-512.png`, `sc-*.png` for the shortcuts, and a one-colour notification icon.

**G9. Restore-code autofill (not covered).**
- The code is saved for shamlawy.github.io. For autofill to offer it in the app, `https://shamlawy.github.io/.well-known/assetlinks.json` with the app's cert SHA-256 is needed. That lives in the **user-site repo** (Shamlawy/shamlawy.github.io), not this one.
- Decision: skip it. Field hints are `ContentType.Password` and `autofillHints`; the owner can also copy the code from web Settings. Tell the owner in one line. Not a blocker.

**G10. The network client's write-route switch (helper side well covered; three holes).**
- (a) Direct hosts outside the helper: `api.rawg.io` (read), `itunes.apple.com` (read), `wsrv.nl` (not ported), image CDNs (Steam, Nintendo, RAWG media), `raw.githubusercontent.com` (picture links rewritten from GitHub links, index.html 4222). The allow-list must cover these as reads.
- (b) The friend-sync worker: block `PUT /u/*`, never run `frAuto` even though the restored state has `syncOn`, and strip nothing from the raw state (it must survive).
- (c) `/review` (AI design review) and `/chat/memory` are read-only, fine.
- Keep `/bug` allowed. Add the `ran` and `probe` kinds.
- The unit test enumerates every route in helper.js's dispatcher (lines 86-310) from a **generated list**, so a new helper route can't slip past unclassified.

**G11. The golden file of shared numbers (no harness designed).**
- Fact: `tools/seed.mjs` dates are relative to `Date.now()`, so the seed must be made under the same fixed clock.
- Harness: `tools/golden.mjs` (in `tools/`, reusing the kit's Playwright):
  - a context with `timezoneId: "Asia/Dubai"` and `page.clock.install({time: T})`;
  - load index.html with `seed()` made in that page;
  - write `android/app/build/golden/golden.json` (generated, not committed, so `*.json` is fine);
  - plus clock variants at 11:30 PM and 12:30 AM UAE time.
- Phase 0 needs only: the load-time flip (upcoming → backlog), `days()`, `isUpcoming`, `isFresh`, both "today" forms, the header-card stats per screen (`shxData`), `onDeck(4)`, `ranked()`, the tiers. Phase 1 adds `qkStat`, order and Nexi's guesses.
- The Kotlin tests read it with an injected `Clock`.

**G12. Content generated from index.html (no extractor designed).**
- Facts: the tables are top-level `const` literals: `UIC` 2842, `PALETTE` 3783, `THEMES` 3805, `BG_BASES` 3815, `NX_PERSONAS` 13336, `NX_KINDS` 13619, `WHATS_NEW` 15162, `NX_PLINES` 18084, `GICON` 26958, `BOOK_SEALED` 28303. CSS tokens are in `:root` (16-19) and `html.ui2` (16426-16430). There are 144 such tables in all.
- **`WHATS_NEW` is extended by about 122 `.push` calls, and its `go`/`at` are JS functions**, so only `v`/`say`/`tryIt` can be generated. The app needs its own what's-new list for Android-only changes anyway.
- Method: `tools/android-gen.mjs` parses every script block with acorn (already installed by lint/app-check), accepts only pure-literal initialisers (it fails on anything else), and writes Kotlin into `android/app/build/generated/pn/`. A shape schema per table fails the build when a table changes. It runs as a Gradle `Exec` task that needs `node`; in-chat builds need Node, which is present.

**G13. release-guard / second-look / design skill (well covered in 4-ci §3).** Two gaps:
- (a) `gh pr merge` isn't hooked, and that's the PC's merge path. Add it, or say Android merges happen only from cloud chats.
- (b) The Android what's-new file and the release counter (`android/version.properties`) must exist before the rule can check them. Build them in the same PR.

**G14. android-check.yml (covered).** Missing pieces:
- running `tools/bugsync-check.mjs` and the generator's shape test;
- Node + Playwright 1.56.1 for the golden file;
- the live-helper Origin test, which must tolerate the helper being down: retry, then fail with a clear message.

**G15. "Updated" notification.**
- It needs `POST_NOTIFICATIONS`, a runtime prompt on Android 13+, but the plan puts allowing notifications in Phase 9 or switch day. Proposal: until then, the update says "Updated to vN" as an in-app line at the next open, and the tour runs then. Ask for notifications in Phase 9.

**G16. Kit items from the Oct 26 UI tips (none mapped).** These belong in Phase 0's kit, not later phases:
- swipe-down-to-close sheet;
- interruptible `Animatable`-based transitions (the card turn, the cover fly);
- balanced headlines (`TextStyle(lineBreak = LineBreak.Heading)`);
- the picture placeholder: a **static** soft gradient in `g.colour`, never an animated blur;
- an idempotency key field on the network client's write path. Phase 0 writes only `/bug`, and the sig dedupe already covers it.

---

## 3. Conflicts and proposed resolutions

1. **"The helper stays as it is" vs `app:"android"` (plan "Your games come with you" vs bugTake).** Pick one and amend the plan's line in the same PR:
   - **Recommended:** a one-line helper change keeping `app` (must match `/^android$/`), deployed by deploy-helper. It's a fix-sized change.
   - The app also puts "App Android …" at the start of `dev` and hashes sigs with an `android|` prefix, so nothing collides with the web's sigs even before the deploy.
   - `v` carries plain versionCode digits (`vNum("a12")` = 0 in bugsync).
   - Add a `ran` heartbeat once per version. Without it, an android `pending-proof` can never close.
2. **Shortcut and the Pick reveal.**
   - The plan says the reveal is in Phase 0 "because the shortcut needs it". The web shortcut never shows the reveal (13740). The reveal also needs On deck scoring plus the Elo/rank tables.
   - Resolution: copy the web. Phase 0 builds the deep-link router and the 3 static shortcuts. "What should I play?" opens On deck #1's page once the game page exists (Phase 2); until then it opens the Queue at that row.
   - `pnReveal` moves with the Home dice (Phase 3), or to Phase 1 with On deck. Tell the owner in the Brief; it isn't a question.
3. **versionCode = run number.**
   - It resets if the workflow is renamed or a separate rollback workflow is used, and a re-run reuses the number.
   - Resolution (4-ci): `versionCode = max(run_number + OFFSET, latest android-v tag + 1)`, and refuse when the tag exists.
   - Rollback is a `ref` input of the **same** workflow.
   - A repo counter `android/version.properties` keys the what's-new entry and the LOG line, which release-guard can check.
4. **Signing key: 5-toolchain says GitHub secrets plus an offline backup by the owner. The plan says Cloudflare KV with no owner step.** Follow the plan (4-ci's workflow). 5-toolchain's sample `build` job passes signing secrets on `pull_request`, which breaks the plan's "PR builds use a throwaway key". Don't copy it. A third copy outside Cloudflare would be an owner step (question 1).
5. **`releases/latest` (5-toolchain) vs the filtered list (4-ci).** Use the filtered `android-v*` list with ETag/304, falling back to `releases/latest/download/android-latest.txt`. Mark only android releases "latest". The release is published from draft only after the emulator smoke test.
6. **"At most one release a day" vs "each piece comes to the phone as soon as it works" (Oct 23).** The later decision wins: a finished, checked piece ships at once. The one-a-day cap applies only to fix-only merges. A `crash-fix` PR label skips the cap.
7. **"Update check runs first at start, before any screen loads" vs "opens instantly".** Normal start runs the check in the background with a time budget and never blocks the first frame. Only safe mode puts the update check first. Installs wait for `commitSessionAfterInstallConstraintsAreMet(setAppNotForegroundRequired)`.
8. **The header card in Phase 0 vs its screens arriving in Phases 1-6.** The card needs `onDeck`, `ranked`, `rankTable` and `eloTable` (later-phase logic). Build the composable plus the `shxData` stats in Phase 0 against the golden file, shown on each tab's placeholder ("Queue arrives in Phase 1"). The tables it needs become shared code from Phase 0.
9. **Report 2's Taste view list is wrong** (see check 10). Use insights / duels / friend / book.
10. **Light mode / CLAUDE.md naming only `auto-bug`.** Add one line: `android-bug` belongs to the build sessions. second-look blocks on a rule left contradicting itself.

### Questions that genuinely need the owner (plain words)
1. **Signing key copy:** "The key that lets updates install is kept twice in your Cloudflare account. Do you also want a locked copy you keep yourself (e.g. in your password manager), so losing Cloudflare never means reinstalling? Yes / no."
2. **Old looks that barely do anything:** "Arcade style, Chamfered corners, How round, 'Screen transitions: Morph' and Rail width hardly change anything today. Leave them out of the app?"
3. **Sounds with Sounds off:** "The duel, CLEAR! and 'Out now' sounds play even with Sounds off in the web app. Should the app keep them quiet when Sounds is off?"
4. **Nexi's tiny buzz:** "Your phone buzzes very slightly every time Nexi talks. Keep it, or only on the big moments?"

(The rest are Claude's calls: the app id, the helper line, release cadence, the reveal's phase, autofill.)

---

## 4. Piece 1 build order (first build session)

1. **Fix the permanent choices and write them into the plan's Decisions:**
   - app id `io.github.shamlawy.playnext` (reverse of a domain the owner controls; never changes);
   - label "Play next ·new";
   - minSdk 36, target/compile 37, AGP 9.4.1, Gradle 9.8.2, Kotlin 2.4.21, BOM 2026.09.00.
   - Check whether `dl.google.com` is reachable; otherwise build only in CI.
2. **`.gitignore` Android lines** (build dirs, `local.properties`, `*.jks` / `*.keystore` / `*.p12`, `*.apk` / `*.aab`, `key.properties`). release-guard gets an always-block for keystores and `google-services.json`, plus its plugin.json version bump.
3. **Skeleton `android/`:**
   - wrapper (jar committed, validated in CI), version catalog, `:app` + `:baselineprofile`;
   - manifest: INTERNET, REQUEST_INSTALL_PACKAGES, `allowBackup=false` + `dataExtractionRules` exclude-all, `usesCleartextTraffic=false` + network-security-config, `enableOnBackInvokedCallback`;
   - a black static splash, edge-to-edge, root `LocalIndication` = none, overscroll off, no dynamic colour;
   - R8 release (`-dontobfuscate` + line numbers);
   - the icon with the ·new mark.
4. **Crash guard + safe mode (G1/G2):**
   - startup counter, uncaught-exception file, exit-reason reader with `setProcessStateSummary`;
   - plain `SafeModeActivity` (update / send reports / try again).
   - Unit test: two crashes within 60 s go to safe mode.
5. **Network client:**
   - OkHttp, `Origin: https://shamlawy.github.io` on every call, timeouts and retries;
   - an allow-list of (host, method, path) with `WriteBlocked` for everything else;
   - a test that enumerates every helper route (G10).
6. **Problem-report sender:**
   - the web report shape, FNV sig with an `android|` prefix, once per day per sig;
   - a scrubber with a planted-title test that also removes 24/64-hex tokens and `PN-` codes;
   - an `App Android …` start to `dev`, `ran` + `probe` kinds, batches of 10 to `/bug`;
   - "Report a problem" button.
7. **Updater:**
   - filtered releases list with ETag, a resumable download to `filesDir`, SHA-256 + same-signer + higher versionCode checks;
   - a PackageInstaller session with `USER_ACTION_NOT_REQUIRED` (**no** `setRequestUpdateOwnership`), `commitSessionAfterInstallConstraintsAreMet(appNotForeground)`;
   - `STATUS_PENDING_USER_ACTION` handled only while visible, `STATUS_FAILURE_BLOCKED` → one plain line + a report;
   - the `canRequestPackageInstalls` grant flow, a 6 h WorkManager check, `MY_PACKAGE_REPLACED` → in-app "Updated to vN" (G15).
8. **120 Hz probe screen (G3) with the calibration readout (G4)**, both results sent as `probe` reports.
9. **`android-check.yml`:**
   - SHA-pinned actions, `contents: read`, no secrets, a throwaway key;
   - wrapper validation, unit tests, lint, Roborazzi at 412/859 dp;
   - `assembleRelease`, the 16 KB and size checks, the live-helper Origin test, `tools/bugsync-check.mjs`.
10. **`android-signing-key.yml`** (one-time, exact-title KV namespaces, `--remote`, read-back compare). Run it once and record the cert SHA-256 in the plan.
11. **`android-release.yml`:**
    - main push + dispatch(`ref`), versionCode by the max rule, the key only on main;
    - R8 + a Baseline Profile generated on the emulator;
    - a **draft** release (apk, `.sha256`, `android-latest.txt`, mapping);
    - an emulator smoke test of the signed APK (start, open probe, no crash), then publish as latest.
12. **bugsync:** the `android-bug` label, (app, sig) routing, families and proof per app, versions from `android-v*` tags, the `ran` / `probe` / exit kinds. Add bugsync-check run 3. Deploy the helper's one-line `app` field and amend the plan line in the same PR. bug-inbox reads `android-bug`.
13. **Guards and docs:**
    - release-guard Android rules (check passed on head, version counter, LOG line, what's-new entry; hook `gh pr merge` or document it);
    - Android notes for `.claude/second-look.md`;
    - the design skill's Android section;
    - CLAUDE.md workflow and label lines;
    - a LOG line.
14. **Release android-v1.** The owner installs it (Auto Blocker / Install unknown apps steps). Then ship v2 only to test the self-update: did it ask, and does it still install with Auto Blocker back on? Run the probe folded and unfolded, record the results in the plan's Start here, and stop for the owner's decision if 120 Hz fails.
