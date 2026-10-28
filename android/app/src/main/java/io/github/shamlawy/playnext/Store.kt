package io.github.shamlawy.playnext

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.StrictMode

/** The app's own bookkeeping (never in the backup): sent-report days, update ETag, the last exit seen and so on. */
object Store {
    lateinit var prefs: SharedPreferences
        private set

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("pn", Context.MODE_PRIVATE)
    }
}

/** Which screen is open: reports name it, and Android's exit records keep it (setProcessStateSummary). */
object Screen {
    @Volatile
    var current: String = "home"
        private set

    /** Android has been told [current] at least once (the first screen is "home" too, so name == current isn't enough). */
    @Volatile
    private var told = false

    fun set(ctx: Context, name: String) {
        if (name == current && told) return
        current = name
        told = true
        runCatching {
            ctx.getSystemService(ActivityManager::class.java).setProcessStateSummary(name.take(100).toByteArray())
        }
    }
}

/** The phone as reports describe it: "App Android 16 SM-F9xx unfolded" (the helper keeps 40 characters). */
object Device {
    fun widthDp(ctx: Context) = ctx.resources.configuration.screenWidthDp
    fun heightDp(ctx: Context) = ctx.resources.configuration.screenHeightDp
    fun density(ctx: Context) = ctx.resources.displayMetrics.density.toDouble()
    fun unfolded(ctx: Context) = ctx.resources.configuration.smallestScreenWidthDp >= 600

    fun dev(ctx: Context): String =
        ("App Android ${Build.VERSION.RELEASE} ${Build.MODEL} " + if (unfolded(ctx)) "unfolded" else "folded").take(40)

    val versionCode: Int get() = BuildConfig.VERSION_CODE

    /** CI's emulators (the Baseline Profile and the smoke test) run the app too; their runs must never count as the phone's. */
    val emulator: Boolean by lazy {
        Build.HARDWARE in setOf("ranchu", "goldfish") || Build.PRODUCT.startsWith("sdk_") || Build.FINGERPRINT.startsWith("generic")
    }
}

/**
 * StrictMode, logging only (gate 3: no disk or network work on the main thread; leaked closeables and registrations):
 * in debug builds, and on CI's emulator walks (the made-up backup's switch, [io.github.shamlawy.playnext.data.Demo]),
 * so the release build's main thread is watched where tools/android-smoke.sh can list what it logs. Never on the phone.
 */
object Strict {
    fun install(ctx: Context) {
        if (!BuildConfig.DEBUG && !io.github.shamlawy.playnext.data.Demo.on(ctx)) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads().detectDiskWrites().detectNetwork().detectUnbufferedIo().detectResourceMismatches().detectCustomSlowCalls()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects().detectLeakedRegistrationObjects().detectActivityLeaks().detectLeakedSqlLiteObjects()
                .detectFileUriExposure().detectContentUriWithoutPermission().detectUnsafeIntentLaunch().detectIncorrectContextUse()
                .penaltyLog()
                .build(),
        )
    }
}
