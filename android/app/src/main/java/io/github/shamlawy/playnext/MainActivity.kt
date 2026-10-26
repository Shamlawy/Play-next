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
import io.github.shamlawy.playnext.probe.ProbeScreen
import io.github.shamlawy.playnext.ui.Home
import io.github.shamlawy.playnext.ui.PnRoot
import io.github.shamlawy.playnext.update.Updater
import kotlinx.coroutines.launch

/**
 * The one activity. Two quick crashes in a row send it to safe mode before anything draws. Otherwise: the black splash
 * (gone with the first frame, no exit animation), edge to edge, and the deep links (`playnext://open?nudge=probe`).
 */
class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf("home")

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        if (CrashGuard.counter.safeMode) {
            startActivity(Intent(this, SafeModeActivity::class.java))
            finish()
            return
        }
        splash.setOnExitAnimationListener { it.remove() }
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        if (savedInstanceState == null) {
            open(intent)
            // The 120 Hz test runs once by itself, the first time the app opens.
            if (!Store.prefs.getBoolean("probeAuto", false)) {
                Store.prefs.edit().putBoolean("probeAuto", true).apply()
                screen = "probe"
            }
        }
        setContent {
            PnRoot {
                when (screen) {
                    "probe" -> ProbeScreen(onClose = { screen = "home" })
                    else -> Home(onProbe = { screen = "probe" })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        open(intent)
    }

    override fun onStart() {
        super.onStart()
        // At start and on coming back after 10+ minutes; never blocks a frame, installs only once the app is left.
        lifecycleScope.launch { Updater.checkInBackground(this@MainActivity) }
    }

    /** The deep-link router: shortcuts, widgets and notifications all come in as playnext://open?nudge=… */
    private fun open(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "playnext" || uri.host != "open") return
        when (uri.getQueryParameter("nudge")) {
            "probe" -> screen = "probe"
            else -> screen = "home"
        }
    }
}
