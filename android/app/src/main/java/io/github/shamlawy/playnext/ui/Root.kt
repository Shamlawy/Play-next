package io.github.shamlawy.playnext.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import io.github.shamlawy.playnext.probe.ProbeScreen

/** The screens Piece 1 has. Later phases replace Home with the tabs. */
enum class Route { Home, Probe }

/**
 * The root of every screen: no touch effect anywhere (#309, v280: the root indication draws nothing), no overscroll
 * stretch or glow, and the one still background.
 */
@Composable
fun PlayNextRoot(route: Route, go: (Route) -> Unit) {
    CompositionLocalProvider(
        LocalIndication provides NoIndication,
        LocalOverscrollFactory provides null,
        LocalTextSelectionColors provides TextSelectionColors(handleColor = Pn.volt, backgroundColor = Pn.volt.copy(alpha = 0.3f)),
    ) {
        AppBackground(Modifier.fillMaxSize()) {
            when (route) {
                Route.Home -> HomeScreen(openProbe = { go(Route.Probe) })
                Route.Probe -> {
                    // the back swipe closes what's open first (Decisions, 1a)
                    BackHandler { go(Route.Home) }
                    ProbeScreen(done = { go(Route.Home) })
                }
            }
        }
    }
}

/** An indication that draws nothing: a tap shows only its result. */
private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : androidx.compose.ui.Modifier.Node() {}
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 7
}
