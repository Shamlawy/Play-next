package io.github.shamlawy.playnext.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.shamlawy.playnext.data.Game
import io.github.shamlawy.playnext.data.Games
import io.github.shamlawy.playnext.data.Page
import io.github.shamlawy.playnext.data.Screenshots
import kotlinx.coroutines.delay

/**
 * The page's screenshots, fetched by themselves (M5, `shotsFetch`): 700 ms after a page shows (the one you opened and
 * the swipe neighbours the pager draws, as the web's `gdShots` asks for both) the stores and RAWG are asked when
 * [Screenshots.due]; the strip redraws when the answer comes in. Returns the strip's shots (small copy, big copy).
 */
@Composable
internal fun rememberShots(page: Page, g: Game): List<Pair<String, String>> {
    val tick by Screenshots.seen.collectAsState()
    val lib = page.q.lib
    val cc = shopCC(lib)
    val now = LocalNow.current
    LaunchedEffect(g.id) {
        delay(700)
        if (askOnOpen) Screenshots.fetch(lib, g, Games.rawgKey, cc, now)
    }
    return remember(page, g, tick) { page.shots(g) }
}
