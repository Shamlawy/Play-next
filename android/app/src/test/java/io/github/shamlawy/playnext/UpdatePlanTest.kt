package io.github.shamlawy.playnext

import io.github.shamlawy.playnext.update.UpdatePlan
import io.github.shamlawy.playnext.update.UpdatePlan.Ask
import io.github.shamlawy.playnext.update.UpdatePlan.Next
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UpdatePlanTest {
    /**
     * android-v1 looped (Oct 27): installed from Chrome, it waited for the background to install v2, where Android
     * can't show its one tap. A copy Play next didn't install itself never waits for the background.
     */
    @Test
    fun aCopyFromChromeNeverWaitsForTheBackground() {
        for (ask in Ask.entries) assertNotEquals("$ask", Next.WhenLeft, UpdatePlan.next(ask, selfOwned = false))
        assertEquals(Next.TapNextOpen, UpdatePlan.next(Ask.Background, selfOwned = false))
        assertEquals(Next.InstallNow, UpdatePlan.next(Ask.Check, selfOwned = false))
        assertEquals(Next.InstallNow, UpdatePlan.next(Ask.Install, selfOwned = false))
    }

    /** Once Play next installed its own copy, updates go in by themselves when the app is left; Install still installs now. */
    @Test
    fun aSelfInstalledCopyUpdatesWhenTheAppIsLeft() {
        assertEquals(Next.WhenLeft, UpdatePlan.next(Ask.Background, selfOwned = true))
        assertEquals(Next.WhenLeft, UpdatePlan.next(Ask.Check, selfOwned = true))
        assertEquals(Next.InstallNow, UpdatePlan.next(Ask.Install, selfOwned = true))
    }
}
