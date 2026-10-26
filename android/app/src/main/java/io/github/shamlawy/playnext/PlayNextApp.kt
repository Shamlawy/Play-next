package io.github.shamlawy.playnext

import android.app.Application
import android.os.StrictMode
import io.github.shamlawy.playnext.crash.CrashGuard
import io.github.shamlawy.playnext.report.Reporter
import io.github.shamlawy.playnext.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Start: the bookkeeping store, the reporter and the crash guard first (a crash from here on is caught), then off the
 * main thread the reports an earlier run left (crash files, Android's exit records) and the once-per-version "ran".
 * The update check is MainActivity's (never before the first frame); the 6-hourly one is the backstop for safe mode.
 */
class PlayNextApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Reporter.init(this)
        CrashGuard.install(this)
        if (BuildConfig.DEBUG) {
            // Debug builds only (CI's emulator tests): disk or network work on the main thread shows in the log.
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        }
        scope.launch {
            Reporter.adopt(CrashGuard.pendingFiles())
            CrashGuard.readExits(this@PlayNextApp)
            if (!CrashGuard.counter.safeMode) Reporter.ranOnce()
            Reporter.flushSoon(4000)
        }
        Updater.schedule(this)
    }
}
