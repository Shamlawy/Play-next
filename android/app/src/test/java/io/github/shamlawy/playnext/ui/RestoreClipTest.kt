package io.github.shamlawy.playnext.ui

import android.app.Application
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** The restore code copies with Android's sensitive mark (Perfect run basics 13); it failed before the fix. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36])
class RestoreClipTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()

    /** A restore code copied from its field carries ClipDescription.EXTRA_IS_SENSITIVE. */
    @Test fun aCopiedRestoreCodeIsMarkedSensitive() {
        rule.setContent { Frame(Look.of(null), Prefs.of(null), now = { now }) { RestoreScreen(onSkip = {}, onDone = {}) } }
        val code = "PN-ABCD-EFGH-JKMN-PQRS-TVWX"
        val field = rule.onNodeWithContentDescription("Restore code")
        field.performTextInput(code)
        field.performTextInputSelection(TextRange(0, code.length))
        field.performSemanticsAction(SemanticsActions.CopyText)
        rule.waitForIdle()
        val cm = ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
        val clip = cm.primaryClip
        assertEquals(code, clip?.getItemAt(0)?.text?.toString())
        assertTrue("copied without the sensitive mark", clip?.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true)
    }
}
