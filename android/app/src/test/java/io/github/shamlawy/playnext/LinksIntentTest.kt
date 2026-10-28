package io.github.shamlawy.playnext

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import android.text.style.StyleSpan
import io.github.shamlawy.playnext.ui.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Links.of with real intents (Robolectric): what the share sheet and a web page's link bring. Made-up games only. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class LinksIntentTest {
    private fun send(text: CharSequence) = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)

    /** Styled text (a SpannableString, as some apps share) used to arrive empty: getStringExtra reads it as null. */
    @Test fun sharedStyledTextKeepsItsWords() {
        val s = SpannableString("Starfall Lantern").apply { setSpan(StyleSpan(1), 0, 8, 0) }
        assertEquals("Starfall Lantern", (Links.of(send(s)) as Route.Add).shared?.name)
        assertEquals("plain text still works", "Copper Meadow", (Links.of(send("Copper Meadow")) as Route.Add).shared?.name)
    }

    /** A web page can't start the 120 Hz test; adb, the profile walk and the app's own links still open it. */
    @Test fun aWebPageCantStartTheProbe() {
        fun view(link: String) = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        assertNull(Links.of(view("playnext://open?nudge=probe").addCategory(Intent.CATEGORY_BROWSABLE)))
        assertEquals(Route.Probe, Links.of(view("playnext://open?nudge=probe")))
        assertEquals("other links from a page still open", Route.Tab("games", "queue"), Links.of(view("playnext://open?nudge=queue").addCategory(Intent.CATEGORY_BROWSABLE)))
    }
}
