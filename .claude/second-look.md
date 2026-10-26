Play next is a single-file web app (index.html, sw.js, worker/helper.js) used by two people: the owner on a Samsung Galaxy Z Fold
(Chrome, installed to the home screen, folded ~360-412px and unfolded ~860px, high refresh rate) and a friend on an iPhone 11
(Safari, installed to the home screen). The owner never reads code and merges automatically, so you are the last look before a
change reaches both phones. Block on: anything that breaks or throws on either phone (iOS Safari quirks included); saved data in
localStorage S or the cloud backup (/vault) lost or corrupted; game titles, scores or personal data in a public file; the hard rules
in CLAUDE.md (press animation, animating anything but transform/opacity, looping blur, hex colours, native date inputs, a doctype,
reassigning a wrapped const, layout read right after innerHTML, a fill-forwards animation never cancelled, *.json committed, a
repo-root file deleted); APP_V bumped without a WHATS_NEW entry with go/at/say/tryIt; a helper route the app calls that
worker/helper.js lacks; a new rule or decision (CLAUDE.md, docs/ANDROID-PLAN.md, a skill, a mod's prompts) that leaves an older rule saying the opposite standing: the same change must amend the older one (L165, it took a follow-up PR three times).

The native Android app lives in android/ (Kotlin + Jetpack Compose, Gradle root android/; android/parity/ is documentation). It is
for the owner's Fold only (folded and unfolded, 120 Hz); the friend keeps the web app. Until switch day it is a read-only practice
copy beside the web app: it reads the owner's games but must never write to the helper or the cloud backup except the problem
reports it is allowed to send. A release is a merge that raises release= in android/version.properties (with a whatsnew.txt block
and an android-vN LOG line); android-release.yml builds, signs and publishes it from main. Block on: a writing helper route (any
POST/PUT/DELETE other than the allowed report) that gets past the network client's write switch; the restore code, tokens, the
keystore or its passwords in logs, problem reports, CI artifacts, build output or the repo; a helper call without the header
`Origin: https://shamlawy.github.io`; any change to `applicationId`; `allowBackup` or the data-extraction rules not switched off;
cleartext traffic allowed; a versionCode that can go down or doesn't come from the release rule; a release build that is
debuggable or built without R8; Compose ripple / `indication` (the root `LocalIndication` must stay none) or any press scale,
lift or fade (#309); Material You dynamic colour; a colour that isn't from the generated token list; emoji used as icons;
animating anything but graphicsLayer translation / scale / alpha / rotation; unknown fields of the backup JSON dropped when it is
saved; game titles in problem reports; a pull-request build that can reach the real signing key; and in the workflows, `${{ }}`
expressions inside `run:` (pass them through `env:`), third-party actions not pinned to a full commit SHA, or `pull_request_target`.
first: index.html sw.js worker manifest.webmanifest android/app/build.gradle.kts android/app/src/main/AndroidManifest.xml .github/workflows/android-*.yml
skip: docs/MAP.md docs/LESSONS.md android/gradle/wrapper/* *.jar android/**/baseline-prof.txt
