package io.github.shamlawy.playnext

import android.app.Application
import io.github.shamlawy.playnext.crash.CrashGuard
import io.github.shamlawy.playnext.report.Reporter
import io.github.shamlawy.playnext.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PlayNextApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // the crash guard first: everything after it is covered (the two stores it writes through come before it)
        Store.init(this)
        Reporter.init(this)
        CrashGuard.install(this)
        // reports a crashed run left behind join the queue; safe mode sends them, a normal start a few seconds in
        Reporter.adopt(CrashGuard.pendingFiles())
        if (CrashGuard.counter.safeMode) return
        scope.launch {
            runCatching { CrashGuard.readExits(this@PlayNextApp) }
            Reporter.ranOnce()
        }
        Updater.schedule(this)
    }
}
