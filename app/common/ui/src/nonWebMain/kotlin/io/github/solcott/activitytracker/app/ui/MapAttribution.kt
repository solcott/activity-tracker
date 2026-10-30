package io.github.solcott.activitytracker.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.maplibre.compose.map.MapState
import org.maplibre.compose.overlay.AttributionDefaults
import org.maplibre.compose.overlay.AttributionLinks
import org.maplibre.compose.overlay.MapOverlay
import org.maplibre.compose.overlay.MapOverlayScope
import org.maplibre.compose.overlay.MaplibreLogo
import org.maplibre.compose.overlay.attributions

/** OSM's attribution guidelines allow collapsing the attribution after about five seconds. */
private val ATTRIBUTION_EXPANDED_FOR = 5.seconds
private val ATTRIBUTION_SHAPE = RoundedCornerShape(24.dp)

/**
 * Whether a map has shown its attribution expanded yet in this process. Only the first map does; later ones start collapsed behind the info
 * button.
 */
private var attributionShown = false

/**
 * [MapOverlay.AttributionOnly], except that the attribution collapses on its own: the library's button stays expanded until a gesture moves
 * the camera, and the static [ActivityMap] allows none. [action], given its placement, goes in the top-end corner, clear of both.
 */
@Composable
internal fun MapOverlayScope.RouteMapOverlay(mapState: MapState, action: @Composable (Modifier) -> Unit = {}) {
    Box(Modifier.fillMaxSize().padding(MapOverlay.Spacing)) {
        MaplibreLogo(Modifier.align(Alignment.BottomStart))
        CollapsingAttribution(mapState, Modifier.align(Alignment.BottomEnd))
        action(Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun CollapsingAttribution(mapState: MapState, modifier: Modifier = Modifier) {
    val style = mapState.style
    val attributions by remember(style) { derivedStateOf { style.attributions() } }
    // Marked shown only once the attribution appears (the style has loaded), so a map whose style
    // never loads doesn't use up the first showing.
    val showFirst = remember { !attributionShown }
    var expanded by remember { mutableStateOf(showFirst) }
    var autoCollapse by remember { mutableStateOf(showFirst) }
    if (attributions.isEmpty()) return

    LaunchedEffect(autoCollapse) {
        attributionShown = true
        if (autoCollapse) {
            delay(ATTRIBUTION_EXPANDED_FOR)
            expanded = false
            autoCollapse = false
        }
    }

    val containerColor by animateColorAsState(if (expanded) AttributionDefaults.ContainerColor else Color.Transparent)
    Row(
        modifier.background(containerColor, ATTRIBUTION_SHAPE),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = expandHorizontally(expandFrom = Alignment.End),
            exit = shrinkHorizontally(shrinkTowards = Alignment.End),
        ) {
            AttributionLinks(attributions, Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp))
        }
        AttributionDefaults.button {
            expanded = !expanded
            autoCollapse = false
        }
    }
}
