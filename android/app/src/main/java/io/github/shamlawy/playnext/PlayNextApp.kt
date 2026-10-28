package io.github.shamlawy.playnext

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.github.shamlawy.playnext.crash.CrashGuard
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.kit.Buzz
import io.github.shamlawy.playnext.kit.Sounds
import io.github.shamlawy.playnext.net.Net
import io.github.shamlawy.playnext.report.Reporter
import io.github.shamlawy.playnext.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class PlayNextApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        // the crash guard first: everything after it is covered (the two stores it writes through come before it)
        Store.init(this)
        Reporter.init(this)
        CrashGuard.install(this)
        Sounds.init(this)
        Buzz.init(this)
        // reports a crashed run left behind join the queue; safe mode sends them, a normal start a few seconds in
        Reporter.adopt(CrashGuard.pendingFiles())
        // safe mode starts nothing more; its "Try again" starts the rest from MainActivity, in this same process
        if (!CrashGuard.counter.safeMode) start(this)
    }

    companion object {
        private val started = AtomicBoolean(false)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Your games (this phone's copy first, then a look for a newer backup), Android's exit records and the update check: once per process. */
        fun start(app: Application) {
            if (!started.compareAndSet(false, true)) return
            Games.start(app)
            scope.launch {
                runCatching { CrashGuard.readExits(app) }
                Reporter.ranOnce()
            }
            Updater.schedule(app)
        }
    }

    /** Pictures (covers, backdrops) load through the app's one network client, so they pass its write switch too. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).components { add(OkHttpNetworkFetcherFactory(callFactory = { Net.client })) }.build()
}
