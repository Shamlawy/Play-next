package io.github.shamlawy.playnext.crash

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.github.shamlawy.playnext.MainActivity
import io.github.shamlawy.playnext.gen.Web
import io.github.shamlawy.playnext.report.Reporter
import io.github.shamlawy.playnext.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Safe mode: plain Views, no Compose and no data, so it opens even when the rest of the app can't. */
class SafeModeActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(pad, pad, pad, pad)
        }
        col.setOnApplyWindowInsetsListener { v, insets ->
            val b = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            v.setPadding(pad + b.left, pad + b.top, pad + b.right, pad + b.bottom)
            insets
        }
        // the web app's own colours (generated from index.html): --ice words, --mute status, --u-card buttons
        fun text(s: String, size: Float, color: Int) = TextView(this).apply {
            text = s; textSize = size; setTextColor(color); setPadding(0, 0, 0, pad / 2)
        }
        col.addView(text("Safe mode", 26f, Color.WHITE))
        col.addView(text("Play next closed twice right after opening, so it started plainly. An update usually fixes it.", 15f, Web.ICE.toInt()))
        status = text("", 14f, Web.MUTE.toInt())
        col.addView(status)
        // A flat card colour: no ripple, no lift (#309, no touch effects).
        fun button(s: String, go: () -> Unit) = Button(this).apply {
            text = s; isAllCaps = false; stateListAnimator = null; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(Web.U_CARD.toInt()); cornerRadius = 14 * resources.displayMetrics.density }
            setOnClickListener { go() }
            col.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = pad / 2 })
        }
        button("Check for an update") {
            status.text = "Looking for an update…"
            scope.launch { status.text = Updater.installNowFromSafeMode(this@SafeModeActivity) }
        }
        button("Send the problem report") {
            status.text = "Sending…"
            scope.launch { status.text = if (Reporter.flush()) "Sent. Thanks!" else "Saved. It'll send when you're back online." }
        }
        button("Try again") {
            CrashGuard.counter.calm()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
        setContentView(col)
        scope.launch { Reporter.flush() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
