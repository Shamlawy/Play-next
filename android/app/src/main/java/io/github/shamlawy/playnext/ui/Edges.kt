package io.github.shamlawy.playnext.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.shamlawy.playnext.Store

/**
 * The system bars and the camera cutout, never the keyboard (`safeDrawing` without the IME): Nexi, the rail and the Sort
 * menu stay where they are when the keyboard opens, as on the web, where the keyboard only covers the page.
 */
val WindowInsets.Companion.steady: WindowInsets
    @Composable get() = systemBars.union(displayCutout)

/**
 * The room kept free at the foot of the screen for Samsung's taskbar (the owner, Nov 6: unfolded, the taskbar floats
 * over the app, the view pill got squashed into it and cards scrolled under it). Pages, the rail and the sheets end
 * above it.
 *
 * Auto, unfolded, uses what Android reports at the bottom (the navigation bar or the taskbar, whichever is taller);
 * folded it keeps none (no taskbar there: pages draw edge to edge, as before). When the unfolded report is under
 * [REPORTED_MIN] (only the gesture handle: the taskbar isn't reported, as on the owner's phone), it keeps [FALLBACK]
 * instead. Settings › Taskbar room sets it by hand; that choice lives on this phone only (never in the
 * backup).
 */
object Taskbar {
    /** Auto, or the room picked by hand in dp. */
    const val AUTO = -1

    /** Unfolded, a bottom inset under this is only the gesture handle, not a taskbar. */
    val REPORTED_MIN = 40.dp

    /** Auto's room unfolded when Android reports no taskbar: about One UI's taskbar with its icons. */
    val FALLBACK = 60.dp

    /** The hand-picked rooms Settings offers (dp), besides Auto. */
    val CHOICES = listOf(0, 48, 60, 72, 88)

    private const val KEY = "taskbarRoom"

    /** Settings' choice: [AUTO] or dp. Read from this phone's own store once; tests without one start on Auto. */
    var choice by mutableIntStateOf(runCatching { if (Store.ready()) Store.prefs.getInt(KEY, AUTO) else AUTO }.getOrDefault(AUTO))
        private set

    fun pick(dp: Int) {
        choice = dp
        runCatching { if (Store.ready()) Store.prefs.edit().putInt(KEY, dp).apply() }
    }

    /** What the room comes to: [reported] is Android's bottom inset, [wide] whether the app is unfolded (the rail shows). */
    fun room(choice: Int, reported: Dp, wide: Boolean): Dp = when {
        choice >= 0 -> choice.dp
        // folded there's no taskbar: the pages draw edge to edge as before, their own padding clears the bars
        !wide -> 0.dp
        reported < REPORTED_MIN -> FALLBACK
        else -> reported
    }
}

/** Android's bottom inset now: the navigation bar or the taskbar (tappable), whichever is taller. */
@Composable
fun reportedBottom(): Dp {
    val d = LocalDensity.current
    val px = maxOf(WindowInsets.navigationBars.getBottom(d), WindowInsets.tappableElement.getBottom(d))
    return with(d) { px.toDp() }
}

/**
 * What [room] adds on top of the navigation bar, for things padded by `windowInsetsPadding(navigationBars)` first: so
 * they end [room] above the foot even when the tappable inset (the taskbar) is taller than the navigation bar.
 */
@Composable
fun overNavBar(room: Dp): Dp {
    val d = LocalDensity.current
    return (room - with(d) { WindowInsets.navigationBars.getBottom(d).toDp() }).coerceAtLeast(0.dp)
}

/** The room at the foot of the screen ([Taskbar]); the shell provides it, 0 outside it (the tests' bare screens). */
val LocalBottomRoom = compositionLocalOf { 0.dp }
