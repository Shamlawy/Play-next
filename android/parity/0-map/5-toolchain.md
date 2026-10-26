# 5. Android toolchain for the native Play next app (Kotlin + Compose)

Researched 2026-10-26. Target: one Samsung Galaxy Z Fold 8 Ultra (One UI, 120 Hz LTPO, folded and unfolded), sideloaded, updating itself from GitHub Releases.
I read every version below from a live `maven-metadata.xml`, SDK repository XML or `git ls-remote` call made from this container today. "Stable" means the newest version with no alpha, beta, rc or dev suffix.

Sources:
- `G` = Google Maven, `https://dl.google.com/dl/android/maven2/<group path>/<artifact>/maven-metadata.xml`
- `MC` = Maven Central, `https://repo1.maven.org/maven2/...`
- `GP` = Gradle Plugin Portal, `https://plugins.gradle.org/m2/...`
- `SDK` = `https://dl.google.com/android/repository/repository2-3.xml`

Watch out: the older `repository2-1.xml` only lists platforms up to `android-36`. The 36.1 and 37.x platforms only appear in schema 2-3 and 2-4. Current `sdkmanager` reads those, so this only matters if you parse the XML yourself.

---

## 0. What this container has

| Item | Found | Notes |
|---|---|---|
| JDK | **OpenJDK 21.0.12.1** at `/usr/bin/java` (`/usr/lib/jvm/java-21-openjdk-amd64`) | Good enough: AGP 9.x needs JDK 17+, and 21 is fine. Nothing to fetch. If a JDK 17 is ever needed: `apt-get install openjdk-17-jdk-headless`, or setup-java `temurin` 17 in CI. |
| Gradle | `/opt/gradle` = **Gradle 8.14.3**, which is too old for AGP 9.4.1 (needs 9.6.0+) | Never matters in practice: the project's wrapper (`gradlew`) downloads 9.8.2 from services.gradle.org, which is reachable. |
| Android SDK | **Not installed** (`ANDROID_HOME` empty, no `sdkmanager`) | It downloads fine (HTTP 200 on the cmdline-tools zip). |
| KVM | **No `/dev/kvm`** | So **no emulator can run here**. Instrumented tests and emulator screenshots run in GitHub Actions only. Robolectric/Roborazzi JVM tests do run here. |
| CPU / RAM | 4 cores, 16 GB | Enough for `assembleRelease` with `org.gradle.jvmargs=-Xmx4g`. |
| Disk | `df` shows 29 GB free on a 252 GB volume, but the real allowance is fixed and smaller | Keep the footprint to the estimate below. |

### Installing the SDK here (headless; not done)
```bash
export ANDROID_HOME=$HOME/android-sdk
mkdir -p $ANDROID_HOME/cmdline-tools && cd /tmp
curl -fLO https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip   # cmdline-tools 23.0, 181 MB
unzip -q commandlinetools-linux-16111833_latest.zip -d $ANDROID_HOME/cmdline-tools && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"
```
sdkmanager is Java, so it should use `JAVA_TOOL_OPTIONS` (already set to the proxy plus the JKS truststore). If it fails TLS, add `--no_https` as a last resort; never turn off verification.

**Disk estimate here (build only, no emulator):**

| Part | Size |
|---|---|
| cmdline-tools | ~180 MB zip, ~350 MB unpacked |
| `platforms;android-37.0` | 67 MB zip, ~200 MB unpacked |
| `build-tools;37.0.0` | 66 MB zip, ~180 MB unpacked |
| platform-tools | 9 MB zip, ~25 MB unpacked |
| Gradle 9.8.2 distribution | ~140 MB zip, ~200 MB unpacked |
| `~/.gradle/caches` for a Compose app with these libs | **1.5–3 GB** |
| Project `build/` dirs | ~0.5–1 GB |
| **Total** | **about 3–5 GB** |

An emulator would add another 0.35 GB plus a 2.1 GB system image zip, about 8 GB unpacked with an AVD. Skip it here, since there is no KVM anyway.

---

## 1. Core build versions

| Thing | Version | Coordinate / source |
|---|---|---|
| **Gradle** | **9.8.2** (current, built 2026-10-19) | `https://services.gradle.org/versions/current`; `distributionUrl=https\://services.gradle.org/distributions/gradle-9.8.2-bin.zip` |
| **Android Gradle Plugin** | **9.4.1** stable (9.5.0-rc01 and 9.6.0-alpha01 exist) | G `com/android/tools/build/gradle`. Its `VersionCheckPlugin` says the **minimum Gradle is 9.6.0**. |
| **Kotlin** | **2.4.21** | MC `org/jetbrains/kotlin/kotlin-gradle-plugin` |
| **Compose compiler** | Kotlin plugin `org.jetbrains.kotlin.plugin.compose` **2.4.21** (always the Kotlin version) | MC `.../plugin/compose/org.jetbrains.kotlin.plugin.compose.gradle.plugin` |
| kotlinx.serialization plugin | `org.jetbrains.kotlin.plugin.serialization` **2.4.21** | GP |
| **Compose BOM** | **2026.09.00**: ui/foundation/runtime **1.12.1**, material3 **1.4.0** | G `androidx/compose/compose-bom`, read from the BOM pom |
| compileSdk | **37** (`platforms;android-37.0`). Also stable: 37.1 and 37.2 (minor SDKs), plus 36.1, 36-ext18 and 36-ext19 | SDK repository2-3.xml (channel-0). 37.2 betas are also listed. |
| build-tools | **37.0.0** | SDK |
| platform-tools | 37.0.1 | SDK |
| cmdline-tools | 23.0 (`commandlinetools-linux-16111833_latest.zip`) | SDK |
| Emulator | 37.2.12 stable (37.3.5 on the canary channel) | SDK |

### compileSdk is forced to 37
I read the `aar-metadata.properties` of current artifacts:

| Artifact | minCompileSdk | Minimum AGP |
|---|---|---|
| `androidx.core:core:1.19.1` | **37** | 9.1.0 |
| `androidx.compose.ui:ui-android:1.12.1` (in BOM 2026.09.00) | **37** | 9.1.0 |
| `androidx.navigation3:navigation3-runtime:1.2.0` | **37** | 9.1.0 |
| `androidx.activity:activity:1.13.0` | 36 | |
| `androidx.compose.material3:material3:1.4.0` | 35 | |
| `androidx.window:window:1.5.1` | 34 | |

So with today's libraries you **must** use `compileSdk = 37` and AGP ≥ 9.1. Use 9.4.1.

### targetSdk
- **Recommendation: `targetSdk = 37`.** The silent self-update rule moves with the device's Android version. Today it needs target ≥ 34 on Android 16 and ≥ 35 on Android 17 (see §3), so 37 is safe through at least Android 18 or 19.
- Android 16 (36) changes that hit us:
  - Edge-to-edge can't be opted out.
  - Predictive back is on by default (`onBackPressed` is no longer called).
  - Orientation and resizability are ignored on sw ≥ 600dp, which includes **the unfolded Fold**. Expect the activity to be recreated on fold/unfold.
  - `elegantTextHeight` is ignored. (https://developer.android.com/about/versions/16/behavior-changes-16)
- Extra Android 17 (37) changes:
  - The resizability opt-out is gone.
  - `ACCESS_LOCAL_NETWORK` permission for LAN access. Not needed: we only talk to the internet.
  - **Background audio (play, focus, volume) needs a running foreground service.** This matters if the book's soundtrack should keep playing in the background. It shouldn't (the web app stops it on leave), so this is fine.
  - Certificate Transparency on by default.
  - Native libraries loaded with `System.load` must be read-only.
  - (https://developer.android.com/about/versions/17/behavior-changes-17)
- The brief says the phone runs Android 16. A Fold launched in mid-2026 may ship One UI 9 on Android 17. Check Settings › About phone › Software information. Either way, compile 37 / target 37 runs fine on Android 16.

### minSdk
- **Recommendation: `minSdk = 36`.** One phone, already on Android 16+. That means no version checks for:
  - `setRequireUserAction` (31)
  - `setRequestUpdateOwnership` and `InstallConstraints` (34)
  - `View.setRequestedFrameRate` (35)
  - predictive back
  - Keystore StrongBox
- Baseline Profiles / Macrobenchmark work at any minSdk.
- CI emulator images exist for 36, 36.1 and 37.x (google_apis and the `_ps16k` 16 KB variants).
- Drop to 35 only if you want to test on an older spare phone.

### AGP 9 notes
- **Kotlin is built into AGP 9.** Don't apply `org.jetbrains.kotlin.android`.
- AGP 9.4.1's pom pulls `kotlin-gradle-plugin` **2.2.10** at runtime. To compile with Kotlin 2.4.21, put KGP 2.4.21 on the build classpath, for example `alias(libs.plugins.kotlin.compose) apply false` and `alias(libs.plugins.kotlin.serialization) apply false` in the root build. Gradle then resolves to the higher version. Confirm with `./gradlew buildEnvironment`.
- The new DSL is on by default. The variant API `android.applicationVariants` is gone; use `androidComponents {}`.
- **No KSP needed.** No Room or Hilt; serialization is a compiler plugin. If it's ever needed: `com.google.devtools.ksp` **2.3.12** (MC; KSP2 is versioned on its own).

---

## 2. Libraries (stable, with coordinates)

| Need | Pick | Coordinate : version | Source / notes |
|---|---|---|---|
| Image loading | **Coil 3** | `io.coil-kt.coil3:coil-compose:3.6.3` + `io.coil-kt.coil3:coil-network-okhttp:3.6.3` | MC. Coil 3 has no network fetcher by default; you must add the network artifact. Share one OkHttpClient. |
| JSON | **kotlinx.serialization** | `org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0` + plugin 2.4.21 | MC. Use `Json { ignoreUnknownKeys = true; explicitNulls = false }` for helper and RAWG responses. |
| HTTP | **OkHttp 5** (plain, no Retrofit) | `com.squareup.okhttp3:okhttp:5.5.1` (Okio 3.18.2 comes along) | MC. Alternative: Ktor `io.ktor:ktor-client-okhttp:3.6.0` + `ktor-client-content-negotiation` + `ktor-serialization-kotlinx-json`. That's more weight for no gain on one app; Retrofit 3.0.0 is also overkill for a handful of `POST /x` helper routes. |
| Coroutines | | `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0` | MC |
| Settings / small state | **DataStore** | `androidx.datastore:datastore:1.3.0` (typed, with a kotlinx-serialization `Serializer`) or `androidx.datastore:datastore-preferences:1.3.0` | G. The saved-games list probably wants its own JSON file through DataStore (typed) or plain atomic files, not Preferences. |
| Secret code (encrypted) | **Android Keystore directly** | No library: `KeyGenerator.getInstance("AES","AndroidKeyStore")` with `KeyGenParameterSpec` (AES/GCM/NoPadding, 256, `setIsStrongBoxBacked(true)` with a fallback, optionally `setUserAuthenticationRequired` + BiometricPrompt). Store the IV + ciphertext in DataStore. | `androidx.security:security-crypto` reached **1.1.0** stable (G), but `EncryptedSharedPreferences`/`MasterKey` are **deprecated** (since 1.1.0-alpha07). Don't use them. If you want a library: Tink `com.google.crypto.tink:tink-android:1.24.0` (MC) with an `AndroidKeysetManager`, but raw Keystore is about 40 lines and has no dependency. |
| Window size + hinge | **androidx.window** + **M3 adaptive** | `androidx.window:window:1.5.1` (`WindowInfoTracker.windowLayoutInfo()` → `FoldingFeature` with `state` FLAT/HALF_OPENED, `orientation`, `bounds`, `isSeparating`); `androidx.compose.material3.adaptive:adaptive:1.3.0` (`currentWindowAdaptiveInfo()` → `windowSizeClass`, `windowPosture`), plus optionally `adaptive-layout` / `adaptive-navigation` 1.3.0 for list-detail panes | G. M3 1.4 has the new `WindowSizeClass` breakpoints (incl. LARGE/EXTRA_LARGE widths). `material3-window-size-class:1.4.0` is the older API; prefer `adaptive`. |
| Navigation | **Navigation 3** | `androidx.navigation3:navigation3-runtime:1.2.0`, `navigation3-ui:1.2.0`, `androidx.lifecycle:lifecycle-viewmodel-navigation3:2.11.0`, `androidx.compose.material3.adaptive:adaptive-navigation3:1.3.0` | G. Back stack is a plain `SnapshotStateList`; predictive back and scenes (two panes unfolded) are built in. Alternative: `androidx.navigation:navigation-compose:2.10.2`. |
| Activity / lifecycle | | `androidx.activity:activity-compose:1.13.0`, `androidx.core:core-ktx:1.19.1`, `androidx.lifecycle:lifecycle-runtime-compose:2.11.0`, `lifecycle-viewmodel-compose:2.11.0` | G |
| Predictive back | Built in | `androidx.activity:activity-compose:1.13.0` (`PredictiveBackHandler { progress: Flow<BackEventCompat> -> }`, `BackHandler`); Nav3's `NavDisplay` animates it | G. On by default at target 36+; keep `android:enableOnBackInvokedCallback="true"` (the default). |
| Splash screen | | `androidx.core:core-splashscreen:1.2.0` | G. Theme `Theme.SplashScreen` + `installSplashScreen()`; keep the splash on with `setKeepOnScreenCondition` only until the first data load (no long holds). |
| JankStats | | `androidx.metrics:metrics-performance:1.0.0` (first stable) | G. See §4. |
| Baseline Profile | | Plugin `androidx.baselineprofile` **1.5.0** (G `androidx/baselineprofile/androidx.baselineprofile.gradle.plugin`); runtime `androidx.profileinstaller:profileinstaller:1.4.1` | Apply the plugin to `:app` and to a `:baselineprofile` test module (`com.android.test`). Sideloaded apps get **no cloud profiles**, so the shipped Baseline Profile is the main fix for first-run and startup jank. ProfileInstaller compiles it at install or after first run. |
| Macrobenchmark | | `androidx.benchmark:benchmark-macro-junit4:1.5.0`, `androidx.test.uiautomator:uiautomator:2.4.0`, `androidx.test.ext:junit:1.3.0`, `androidx.test:runner:1.7.0` | G. `FrameTimingMetric` gives `frameDurationCpuMs` / `frameOverrunMs` (P50/P90/P99) for scroll tests; `StartupTimingMetric` for cold start. Runs on a real device or emulator (an emulator gives the "EMULATOR" warning; real numbers need the Fold over `adb`). |
| Screenshot tests | **Roborazzi** (JVM, Robolectric) | `io.github.takahirom.roborazzi:roborazzi:1.77.0`, `roborazzi-compose:1.77.0`, plugin `io.github.takahirom.roborazzi` 1.77.0 (MC); `org.robolectric:robolectric:4.17` (MC; has `android-all-instrumented` 17 = API 37 images); `androidx.compose.ui:ui-test-junit4` + `ui-test-manifest` (BOM 1.12.1) | Runs **in this container** (no emulator needed). Uses `@Config(qualifiers = "w412dp-h915dp")` and `"w859dp-h1000dp"`-style for folded/unfolded, mirroring the web kit's 412/859 widths. **Official** Compose Preview Screenshot Testing (`com.android.compose.screenshot`) is still **0.0.1-alpha16** (G, last updated 2026-07-27), so not stable. **Paparazzi** stable is 1.3.5 (2.0.0 is alpha05); it lags new compileSdks. Roborazzi is the safest. |
| Memory leaks | LeakCanary | `com.squareup.leakcanary:leakcanary-android:2.14` (debugImplementation) | MC. 3.0 is still alpha-9. 2.14 is old (2024) but works by `ContentProvider` auto-install; if it breaks on API 37, drop it rather than fight it. |
| Background update check | WorkManager | `androidx.work:work-runtime-ktx:2.12.0` | G. A periodic (e.g. 6 h) "check GitHub latest release" worker. |
| Shrinking | **R8** (bundled with AGP 9.4.1) | no dependency; `isMinifyEnabled = true`, `isShrinkResources = true`, `proguard-android-optimize.txt`. R8 standalone stable is 9.5.28 (G `com/android/tools/r8`), only for pinning a newer R8 if a bug needs it. | kotlinx.serialization ships its own consumer rules; keep `@Serializable` classes. AGP 9 turns on R8 "full mode" by default. Test the **release** build, never just debug (debug Compose is 2–5x slower and unrepresentative of 120 Hz smoothness). |
| Tracing (optional) | | `androidx.tracing:tracing-perfetto:1.0.1` | G. For `composition tracing` in Perfetto. |

---

## 3. Self-update without the Play Store

### Mechanism
1. **Check.** `GET https://api.github.com/repos/<owner>/<repo>/releases/latest`. It's a public repo, so no token; unauthenticated calls are limited to 60 per hour per IP. Pick the asset `play-next-<versionCode>.apk` and compare with `BuildConfig.VERSION_CODE`. Ship a `.sha256` asset (or put the hash in the release body).
2. **Download** with OkHttp into `cacheDir/update.apk` (or `DownloadManager`, but we're in-app anyway).
3. **Verify before install:**
   - SHA-256 matches.
   - `packageManager.getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)?.signingInfo` has the **same signing certificate** as the running app.
   - `longVersionCode` is higher than the current one.
   - Android itself refuses a different signature, but checking first gives a clear message.
4. **Install** with a `PackageInstaller` session:
   ```kotlin
   val pi = context.packageManager.packageInstaller
   val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
       setAppPackageName(context.packageName)
       setSize(apk.length())
       setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED) // API 31
       setRequestUpdateOwnership(true)                                               // API 34: become update owner of ourselves
       setPackageSource(PackageInstaller.PACKAGE_SOURCE_OTHER)                       // API 33
       setInstallReason(PackageManager.INSTALL_REASON_USER)
   }
   val id = pi.createSession(params)
   pi.openSession(id).use { s ->
       s.openWrite("base.apk", 0, apk.length()).use { out -> apk.inputStream().copyTo(out); s.fsync(out) }
       val pending = PendingIntent.getBroadcast(context, id, Intent(context, InstallResultReceiver::class.java),
           PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE) // MUTABLE: the system adds the status extras
       s.commit(pending.intentSender)
   }
   ```
   - In the receiver, read `EXTRA_STATUS`. On `STATUS_PENDING_USER_ACTION`, start `intent.getParcelableExtra(Intent.EXTRA_INTENT)` with `FLAG_ACTIVITY_NEW_TASK` (only works while we're visible). On `STATUS_SUCCESS`, the process is killed and replaced.
   - Register an `ACTION_MY_PACKAGE_REPLACED` receiver to post "Updated to vN" or show the what's-new tour on next launch.
   - **Better UX on API 34+:** `pi.commitSessionAfterInstallConstraintsAreMet(id, sender, InstallConstraints.Builder().setAppNotForegroundRequired().build(), timeoutMillis)`. The update then lands while the app isn't on screen, so it never kills the owner mid-scroll. This is the web app's "update arrives by itself" feel.
5. **Manifest:** `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>`, plus INTERNET. Optionally `UPDATE_PACKAGES_WITHOUT_USER_ACTION` (normal permission, API 31+).
   - It's another way to meet the silent-update condition, but it's only honoured for an installer that's update owner / installer of record or updating itself. In practice "updating itself" already covers us.

### When the tap can be skipped (official text, `PackageInstaller.SessionParams#setRequireUserAction`)
Source: https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams (fetched today).

For installers granted `REQUEST_INSTALL_PACKAGES`, user action is **not** required when **all** of these hold:
- `USER_ACTION_NOT_REQUIRED` is set. (Unset with `REQUEST_INSTALL_PACKAGES` acts as `USER_ACTION_REQUIRED`.)
- The app being installed targets at least the level the device requires:

  | Device | Installed app must target |
  |---|---|
  | Android 12 (S, 31) | API 29 |
  | Android 13 (T, 33) | API 30 |
  | Android 14 (U, 34) | API 31 |
  | Android 15 (V, 35) | API 33 |
  | Android 16 (B, 36) | API 34 |
  | Android 17 (C, 37) | API 35 |

  "Will advance in future Android versions", so keep targetSdk current. **37 is safe.**
- The installer is **one** of:
  - the update owner of the existing app, if update-ownership enforcement is on;
  - the installer of record, if it's off;
  - **updating itself**;
  - or it declares `UPDATE_PACKAGES_WITHOUT_USER_ACTION`.
- Always be ready for `STATUS_PENDING_USER_ACTION`. Also expect a short system throttle on back-to-back silent updates of the same package (seconds). Don't retry in a loop.

So for us:
- The **very first install** is manual: an APK from the browser or Files, or `adb install`.
- The user must also grant **"Install unknown apps"** to Play next once. Check `packageManager.canRequestPackageInstalls()`; if false, open `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` with `package:` URI.
- **After that, every update is silent**, because "updating itself" qualifies whoever the original installer of record was.
- Requesting update ownership (API 34) on the first self-update makes Play next the update owner. Another installer (e.g. a browser-downloaded APK) would then get a confirmation prompt, which is fine.

### Platform changes 14 / 15 / 16 / 17 that matter
- **14 (API 34):**
  - Apps targeting below 23 can't be installed (n/a).
  - Update ownership: `setRequestUpdateOwnership`.
  - `InstallConstraints` / `commitSessionAfterInstallConstraintsAreMet`.
  - Implicit / pending intents must be explicit, and the status `PendingIntent` must be `FLAG_MUTABLE` with an explicit component (done above).
- **15 (35):**
  - Silent-update target floor rises to 33.
  - App archiving (`setUnarchiveId`), n/a.
  - **Private space** apps can't be updated by a regular-profile installer. Keep the app in the main profile.
- **16 (36):** target floor 34. **Restricted settings**: sideloaded apps (package source "downloaded file" or local file) can't get Accessibility / Notification listener without an extra unlock. We don't need either.
- **17 (37):** target floor 35. Native libraries read-only (we have none). Background-activity-start tightening applies to `IntentSender`: launch the pending-user-action intent only while in the foreground.

### Samsung Auto Blocker
- Auto Blocker ships **on by default on Galaxy phones that launch with One UI 6.1.1 or later**, so it's almost certainly on for a 2026 Fold 8. While on, it blocks app installs from anything but Galaxy Store / Play (and USB/ADB commands).
- **The owner must turn it off once:** Settings › Security and privacy › Auto Blocker › Off. Then grant Play next "Install unknown apps".
- A One UI 8.5 teardown showed a test option that **re-enables Auto Blocker 30 minutes after it's turned off**. If that ships, sideloaded self-updates would fail silently again. Handle it: when an install session fails with `STATUS_FAILURE_BLOCKED`, tell the owner it was "blocked by Auto Blocker" and deep-link to Settings.
- Sources:
  - https://www.osnews.com/story/140312/new-samsung-phones-block-sideloading-by-default/
  - https://www.techradar.com/phones/samsung-galaxy-phones/samsung-galaxy-phones-just-removed-one-of-androids-best-features-but-you-can-fix-it
  - https://www.androidauthority.com/one-ui-8-5-auto-blocker-auto-turn-on-apk-teardown-3599873
- Also: **Play Protect** scans sideloaded APKs and may warn about "unknown developer" on first install. Choose "Install anyway". Registering the app (below) avoids that.

### Google developer verification (status on 2026-10-26)
Source: https://developer.android.com/developer-verification + `/guides/faq` + `/guides/limited-distribution`, fetched today.

- **August 2026:** developer APIs, **limited distribution accounts** and the power-user **"advanced flow"** launched. The advanced flow is a one-time developer-mode check, a restart and a **24-hour wait**, then allow unverified installs for 7 days or indefinitely.
- **2026-09-30:** enforcement began, but only for installs from **participating stores** in **Brazil, Indonesia, Singapore, Thailand** on certified devices (Android 7+). The FAQ (updated 2026-06-18) says the Sept 30 deadline applies only to those stores; plain sideloading isn't covered yet.
- **2027:** global rollout. No exact date for the UAE yet. After that, installs **and updates** of apps from unregistered developers will be blocked on certified devices unless the user has the advanced flow on.
- **ADB installs stay exempt.**
- **Limited distribution account:** free, no government ID, register your package names, share with **up to 20 devices** the user authorises (QR/link handshake). **This fits Play next exactly.** Register the package name (e.g. `app.playnext`) and the Fold before the 2027 UAE date. Full ADC account = $25 + ID.
- Do this early: the signing key and package name you register are what the phone will check.
- News: https://androidauthority.com/google-android-advanced-flow-sideloading-rollout-begins-3700073

### Signing
- One upload/signing key forever, made with `keytool -genkeypair -alias playnext -keyalg RSA -keysize 4096 -validity 36500 -keystore playnext.jks`.
- Kept as GitHub secrets `SIGNING_KEYSTORE_B64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. Plus an **offline backup**: losing it means uninstalling the app, which **wipes the phone's local data**.
- APK Signature Scheme v2/v3 (AGP default; minSdk 36 drops v1).
- `.gitignore` already blocks `*.json`; also block `*.jks` / `*.keystore`.
- versionCode = monotonic (e.g. `APP_V`-like counter or `github.run_number`).

---

## 4. 120 Hz: asking for it and measuring it

### Asking
- **Samsung's setting comes first:** Settings › Display › Motion smoothness › **Adaptive** (120 Hz). On "Standard" (60 Hz) no app API helps. The panel is LTPO: it drops to 1–24 Hz when idle and jumps on touch. Apps only *vote*.
- **Window (whole activity):** pick the highest-rate mode with the same resolution, then set the window attributes:
  ```kotlin
  val d = display!!
  val best = d.supportedModes
      .filter { it.physicalWidth == d.mode.physicalWidth && it.physicalHeight == d.mode.physicalHeight }
      .maxBy { it.refreshRate }
  window.attributes = window.attributes.also { it.preferredDisplayModeId = best.modeId }
  ```
  - Use `preferredRefreshRate` for a rate without a mode switch.
  - Re-run in `onConfigurationChanged` or on `DisplayListener`: fold/unfold switches the **display** (cover vs inner screen); each has its own modes.
  - Caution: pinning 120 Hz all the time costs battery and fights LTPO. Better to vote high only while something moves (below).
- **View / Compose, API 35+ (ARR, "adaptive refresh rate"):**
  - `View.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH)` on the `ComposeView` / decor view during animations.
  - **Compose has its own API in `androidx.compose.ui:ui` 1.12.1** (I confirmed it in the classes jar; stable, no `@OptIn`): `Modifier.preferredFrameRate(FrameRateCategory.High)` and `Modifier.preferredFrameRate(120f)` (`androidx.compose.ui.FrameRateKt`, `FrameRateCategory.Default/Normal/High`).
  - Put it on the animating or scrolling node (e.g. the open Queue card while it flips, the book while pages turn). Compose combines the votes; the system goes to 120 only while those nodes draw.
  - Compose already marks scrolling and flinging as high-category on ARR devices; check whether One UI honours ARR on the Fold.
- **Surface (SurfaceView/TextureView only):** `Surface.setFrameRate(120f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT, Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS)` (API 31 form). Only needed if the book's page curl moves to a GL/AGSL surface.
- Keep the web app's speed rules: animate only `graphicsLayer { translationX/scale/alpha/rotation }` (the Compose equivalent of transform/opacity, read in the layer/draw phase via lambdas so there's no recomposition per frame). No `Modifier.blur` in loops. Prefer `Modifier.drawWithCache`.

### Measuring
- **JankStats** (`androidx.metrics:metrics-performance:1.0.0`):
  - `JankStats.createAndTrack(window) { fd: FrameData -> }` gives `frameDurationUiNanos`, `isJank` (relative to the *current* refresh period, so it adapts to 120 Hz), and states from `PerformanceMetricsState.getHolderForHierarchy(view).state?.putState("screen","queue")`.
  - Feed it into the app's own problem reports (the native version of the web lag observers). Enable it only while the screen is on and unpause in `onResume`.
- **Choreographer:** `Choreographer.getInstance().postFrameCallback { frameTimeNanos -> … }` to log the real frame interval (≈8.33 ms at 120 Hz). `display.refreshRate` / `display.mode.refreshRate` for the current mode; register `DisplayManager.DisplayListener` for changes.
- **Lab:**
  - Macrobenchmark `FrameTimingMetric` (P90 `frameOverrunMs` < 0 = no missed deadlines).
  - `adb shell dumpsys gfxinfo <pkg> framestats`.
  - `adb shell dumpsys SurfaceFlinger | grep -i "refresh\|vsync"`.
  - Perfetto system trace (`adb shell perfetto -o /data/misc/perfetto-traces/t -t 10s sched gfx view` or Android Studio). It shows "Expected/Actual Timeline" jank types.
  - On the phone: Developer options › **Show refresh rate** overlay (Samsung keeps it).
- Always measure the **release** build with the Baseline Profile installed. In CI: `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`. Emulator numbers are only a trend.

---

## 5. GitHub Actions

Current action tags (`git ls-remote --tags` today):

| Action | Tag |
|---|---|
| `actions/checkout` | v7.0.1 |
| `actions/setup-java` | v6.1.0 |
| `gradle/actions` | v6.4.0 |
| `ReactiveCircus/android-emulator-runner` | v2.38.0 |
| `android-actions/setup-android` | v4.0.4 |
| `softprops/action-gh-release` | v3.0.3 |
| `actions/upload-artifact` | v7.0.2 |

Pin each to its commit SHA, per the `github-actions-hardening` skill: the repo is public and holds the signing secrets.

GitHub's `ubuntu-latest` image already has an Android SDK (`ANDROID_HOME`). Missing packages (`platforms;android-37.0`, `build-tools;37.0.0`) are fetched by AGP automatically once the licences are accepted, or explicitly with `sdkmanager`.

```yaml
name: android
on:
  pull_request: { paths: ['android/**', '.github/workflows/android.yml'] }
  push: { branches: [main], paths: ['android/**'] }
  workflow_dispatch:
permissions: { contents: read }
concurrency: { group: android-${{ github.ref }}, cancel-in-progress: true }
jobs:
  build:
    runs-on: ubuntu-latest
    defaults: { run: { working-directory: android } }
    steps:
      - uses: actions/checkout@v7            # pin SHA
      - uses: actions/setup-java@v6          # pin SHA
        with: { distribution: temurin, java-version: '21' }
      - uses: gradle/actions/setup-gradle@v6 # pin SHA; caches ~/.gradle, validates the wrapper
        with: { cache-read-only: ${{ github.ref != 'refs/heads/main' }} }
      - run: yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null
      - run: ./gradlew lint testDebugUnitTest verifyRoborazziDebug assembleRelease --stacktrace
        env:
          SIGNING_KEYSTORE_B64: ${{ secrets.SIGNING_KEYSTORE_B64 }}   # decoded in build.gradle.kts / a step; never echo
          SIGNING_STORE_PASSWORD: ${{ secrets.SIGNING_STORE_PASSWORD }}
          SIGNING_KEY_ALIAS: ${{ secrets.SIGNING_KEY_ALIAS }}
          SIGNING_KEY_PASSWORD: ${{ secrets.SIGNING_KEY_PASSWORD }}
      - name: 16 KB page-size check
        run: |
          BT=$ANDROID_HOME/build-tools/37.0.0
          APK=$(ls app/build/outputs/apk/release/*.apk)
          $BT/zipalign -c -P 16 -v 4 "$APK" | tail -1          # uncompressed .so aligned to 16 KB
          # ELF segment alignment of any bundled .so (expect 2**14 or more):
          unzip -o -q "$APK" 'lib/arm64-v8a/*' -d so || true
          for f in so/lib/arm64-v8a/*.so; do [ -e "$f" ] || continue
            objdump -p "$f" | awk '/LOAD/{print FILENAME": "$NF}' FILENAME="$f"; done
      - uses: actions/upload-artifact@v7
        with: { name: apk, path: android/app/build/outputs/apk/release/*.apk }

  instrumented:
    runs-on: ubuntu-latest
    needs: build
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with: { distribution: temurin, java-version: '21' }
      - uses: gradle/actions/setup-gradle@v6
      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules && sudo udevadm trigger --name-match=kvm
      - uses: ReactiveCircus/android-emulator-runner@v2   # v2.38.0, pin SHA
        with:
          api-level: 36                 # or 37.0 once the runner action accepts the minor form
          target: google_apis_ps16k     # 16 KB page-size image (also proves the app boots on 16 KB)
          arch: x86_64
          profile: pixel_9_pro_fold     # foldable profile; check `avdmanager list device` for the exact id
          disable-animations: true
          emulator-options: -no-window -gpu swiftshader_indirect -no-snapshot -noaudio -no-boot-anim
          working-directory: android
          script: ./gradlew connectedDebugAndroidTest
```

Release job (on a tag `v*` or a manual dispatch):
- `assembleRelease`, then `softprops/action-gh-release@v3` with `files: app-release.apk, app-release.apk.sha256`.
- Needs `permissions: contents: write` on that job only.
- The phone's update checker reads `releases/latest`.

### 16 KB page size
- Android 15+ devices may use 16 KB pages.
- **A pure Kotlin/Compose app with no `.so` files is compatible automatically.** Check that no dependency brings native code. In the list above, none should (Coil, OkHttp, Tink-java, LeakCanary are JVM-only; check with `unzip -l app-release.apk | grep '\.so$'`).
- AGP 8.5.1+ (so 9.4.1) aligns uncompressed `.so` files to 16 KB by default.
- Other ways to check: Android Studio APK Analyzer's "16 KB alignment" column, `zipalign -c -P 16`, `objdump -p` LOAD align ≥ `2**14`, and booting a `_ps16k` image (above). Images exist for 36, 36.1, 37.0, 37.1 and 37.2 (SDK sys-img google_apis/sys-img2-3.xml).

---

## Recommended `gradle/libs.versions.toml`

```toml
[versions]
agp = "9.4.1"                 # G com/android/tools/build/gradle (min Gradle 9.6.0; wrapper 9.8.2)
kotlin = "2.4.21"             # MC org/jetbrains/kotlin/kotlin-gradle-plugin
composeBom = "2026.09.00"     # G androidx/compose/compose-bom -> ui 1.12.1, material3 1.4.0
activityCompose = "1.13.0"    # G
coreKtx = "1.19.1"            # G (needs compileSdk 37, AGP 9.1+)
lifecycle = "2.11.0"          # G
navigation3 = "1.2.0"         # G
m3Adaptive = "1.3.0"          # G androidx/compose/material3/adaptive
window = "1.5.1"              # G
datastore = "1.3.0"           # G
splashscreen = "1.2.0"        # G
work = "2.12.0"               # G
metricsPerformance = "1.0.0"  # G (JankStats)
profileinstaller = "1.4.1"    # G
benchmark = "1.5.0"           # G (macro + baselineprofile plugin)
uiautomator = "2.4.0"         # G
androidxTestExt = "1.3.0"     # G
androidxTestRunner = "1.7.0"  # G
coroutines = "1.11.0"         # MC
serialization = "1.12.0"      # MC
okhttp = "5.5.1"              # MC
coil = "3.6.3"                # MC io/coil-kt/coil3
leakcanary = "2.14"           # MC
roborazzi = "1.77.0"          # MC
robolectric = "4.17"          # MC
junit = "4.13.2"              # MC

[libraries]
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-foundation = { module = "androidx.compose.foundation:foundation" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
m3-adaptive = { module = "androidx.compose.material3.adaptive:adaptive", version.ref = "m3Adaptive" }
m3-adaptive-layout = { module = "androidx.compose.material3.adaptive:adaptive-layout", version.ref = "m3Adaptive" }
m3-adaptive-navigation3 = { module = "androidx.compose.material3.adaptive:adaptive-navigation3", version.ref = "m3Adaptive" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
core-splashscreen = { module = "androidx.core:core-splashscreen", version.ref = "splashscreen" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-viewmodel-navigation3 = { module = "androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycle" }
navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "navigation3" }
navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "navigation3" }
window = { module = "androidx.window:window", version.ref = "window" }
datastore = { module = "androidx.datastore:datastore", version.ref = "datastore" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
metrics-performance = { module = "androidx.metrics:metrics-performance", version.ref = "metricsPerformance" }
profileinstaller = { module = "androidx.profileinstaller:profileinstaller", version.ref = "profileinstaller" }
benchmark-macro-junit4 = { module = "androidx.benchmark:benchmark-macro-junit4", version.ref = "benchmark" }
uiautomator = { module = "androidx.test.uiautomator:uiautomator", version.ref = "uiautomator" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestExt" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidxTestRunner" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network-okhttp = { module = "io.coil-kt.coil3:coil-network-okhttp", version.ref = "coil" }
leakcanary = { module = "com.squareup.leakcanary:leakcanary-android", version.ref = "leakcanary" }
roborazzi = { module = "io.github.takahirom.roborazzi:roborazzi", version.ref = "roborazzi" }
roborazzi-compose = { module = "io.github.takahirom.roborazzi:roborazzi-compose", version.ref = "roborazzi" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
junit = { module = "junit:junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-test = { id = "com.android.test", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
baselineprofile = { id = "androidx.baselineprofile", version.ref = "benchmark" }
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
# no kotlin-android plugin: AGP 9 has Kotlin built in
```

`app/build.gradle.kts` essentials:
- `compileSdk = 37`, `minSdk = 36`, `targetSdk = 37`
- `jvmToolchain(21)` / `compileOptions { sourceCompatibility = JavaVersion.VERSION_21 }`
- `buildFeatures { compose = true; buildConfig = true }`
- release: `isMinifyEnabled = true`, `isShrinkResources = true`, `signingConfig` from env
- `testOptions { unitTests.isIncludeAndroidResources = true }` (Roborazzi)

---

## Risks / unknowns

1. **Android version on the phone.** The brief says 16. A mid-2026 Fold may be on Android 17 / One UI 9. This doesn't change the plan (compile/target 37), but it changes which silent-update target floor applies (35 vs 34). Check on the device.
2. **Developer verification in the UAE (2027).** No published date. Until then sideloading works; after it, unregistered apps can't install **or update**. Mitigation:
   - Create the free limited-distribution account and register the package and the Fold soon.
   - Keep `adb install` (exempt) and the advanced flow as fallbacks.
   - The rules are still moving (dates shifted twice in 2026).
3. **Samsung Auto Blocker.** It's on by default and blocks the self-updater, so the owner must switch it off. A possible One UI 8.5+ "turn back on after 30 min" would break silent updates; the app must detect `STATUS_FAILURE_BLOCKED` and say so. Play Protect may also warn on first install.
4. **AGP 9 built-in Kotlin vs KGP 2.4.21.** AGP 9.4.1 pins KGP 2.2.10 at runtime. Forcing 2.4.21 should work but hasn't been tested here. Fallback: stay on the Kotlin version AGP brings, with a matching compose-compiler plugin version.
5. **Version churn.** compileSdk 37 is *forced* by core 1.19 / Compose 1.12. AGP 9.5 is at rc01 and Compose 1.13 at beta02, so expect a bump within weeks. `AGP 9.4.1 + Gradle 9.8.2` is a supported combination per its version check (min 9.6.0); I haven't checked the maximum.
6. **No KVM here.** Emulator tests, Macrobenchmark and Baseline Profile generation can only run in GitHub Actions (slow, and emulator frame timings aren't 120 Hz-representative) or on the real Fold over ADB. Real smoothness numbers need the phone (the owner's PC + `adb`, or in-app JankStats reports).
7. **Robolectric/Roborazzi on API 37.** Robolectric 4.17 has an API 37 image (`android-all-instrumented 17-robolectric-15733970-i7`). Rendering is still software (`@GraphicsMode(NATIVE)`) and can differ from One UI. Fine for layout regressions, not for colour or blur fidelity. Official Compose screenshot testing is still alpha.
8. **LeakCanary 2.14 is old** (3.0 still alpha). It may misbehave on API 37; debug-only, so low risk.
9. **ARR / Compose `preferredFrameRate` on One UI.** Samsung may keep its own refresh governor and only partly honour app votes. Test with the "Show refresh rate" overlay. `preferredDisplayModeId` is the blunt fallback (battery cost). Fold/unfold changes the display, so re-apply the mode on display change.
10. **The signing key is a single point of failure.** Losing it means reinstalling, which wipes local data (same lesson as the web app's backups). Keep an offline copy plus cloud backup of app data.
11. **GitHub API rate limit** for update checks: 60 per hour per IP unauthenticated. Fine for one phone at 6 h intervals; don't poll on every resume. Alternatively host a `latest.json` on GitHub Pages (the existing site) and avoid the API entirely.
12. **Disk here.** About 3–5 GB for SDK + Gradle caches + builds. Confirm it fits the container's fixed allowance before running a first build here; `df` isn't the real number.
