package io.github.shamlawy.playnext

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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
import io.github.shamlawy.playnext.report.FrameGate
import io.github.shamlawy.playnext.ui.PlayNextRoot
import io.github.shamlawy.playnext.ui.Route
import io.github.shamlawy.playnext.update.Updater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var route by mutableStateOf(Route.Home)
    private var gate: FrameGate? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // the splash is black with a still icon and goes with the first frame: nothing to wait for here (G7)
        installSplashScreen()
        if (CrashGuard.counter.safeMode) {
            super.onCreate(savedInstanceState)
            startActivity(Intent(this, SafeModeActivity::class.java))
            finish()
            return
        }
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        go(Links.route(intent?.dataString) ?: Route.Home)
        setContent { PlayNextRoot(route, ::go) }
        gate = FrameGate.track(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Links.route(intent.dataString)?.let(::go)
    }

    override fun onStart() {
        super.onStart()
        // at start and on coming back after 10+ min, in the background: never blocks a screen (critic §3.7)
        lifecycleScope.launch { Updater.checkInBackground(applicationContext) }
    }

    override fun onResume() {
        super.onResume()
        gate?.on(true)
    }

    override fun onPause() {
        gate?.on(false)
        super.onPause()
    }

    private fun go(r: Route) {
        route = r
        Screen.set(this, r.name.lowercase())
    }
}
