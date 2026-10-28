package io.github.shamlawy.playnext

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import io.github.shamlawy.playnext.crash.CrashGuard
import io.github.shamlawy.playnext.crash.SafeModeActivity
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.report.FrameGate
import io.github.shamlawy.playnext.ui.PlayNextRoot
import io.github.shamlawy.playnext.ui.Route
import io.github.shamlawy.playnext.update.Updater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** A link that opened the app (a shortcut, the 120 Hz test's link) or something shared into it: the screen takes it, then clears it. */
    private var link by mutableStateOf<Route?>(null)
    private var gate: FrameGate? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // the splash is black with a still icon; it waits for this phone's copy of your games (a moment, never the
        // network, at most 1.5 s) so the first screen isn't a flash of "opening" (G7)
        val t0 = SystemClock.uptimeMillis()
        installSplashScreen().setKeepOnScreenCondition { Games.state.value is Games.State.Starting && SystemClock.uptimeMillis() - t0 < 1500 }
        if (CrashGuard.counter.safeMode) {
            super.onCreate(savedInstanceState)
            startActivity(Intent(this, SafeModeActivity::class.java))
            finish()
            return
        }
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        // a launch link counts once: unfolding, turning or coming back after Android let the app go recreates it with
        // the same intent, and reading it again would jump back to where the link pointed
        if (savedInstanceState == null) link = Links.of(intent)
        setContent { PlayNextRoot(link) { link = null } }
        gate = FrameGate.track(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Links.of(intent)?.let { link = it }
    }

    override fun onStart() {
        super.onStart()
        // at start and on coming back: an update waiting for Android's tap asks for it now (once per version), else the
        // check after 10+ min, in the background: never blocks a screen (critic §3.7)
        lifecycleScope.launch { Updater.onOpen(applicationContext) }
        // your games: a newer backup since the last look (over a minute ago)?
        Games.onOpen(applicationContext)
    }

    override fun onResume() {
        super.onResume()
        gate?.on(true)
    }

    override fun onPause() {
        gate?.on(false)
        super.onPause()
    }
}
