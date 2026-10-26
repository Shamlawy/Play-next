package io.github.shamlawy.playnext

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build

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

    fun set(ctx: Context, name: String) {
        if (name == current) return
        current = name
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
}
