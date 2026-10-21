package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.RouteEnds
import kotlinx.collections.immutable.ImmutableList

/**
 * An activity's route on a map, framed to fit the whole track. Android, iOS and desktop use MapLibre; MapLibre Compose has no wasmJs target
 * yet (maplibre/maplibre-compose#209, waiting on Compose Multiplatform 1.13; see BACKLOG.md), so web falls back to [RoutePreview]. Keep
 * MapLibre's pre-1.0 API inside the nonWeb actual so its churn stays in one file.
 */
/**
 * The route on a map, with its [RouteMarkers]; [gaps] are where the line breaks (`feed.activity#polylineGaps`), and [ends] where its start
 * and end are marked when not at the line's own ends.
 *
 * The map is static unless [interactive], which lets the viewer pan and zoom it (north-up) and recenter it on the route. With [onExpand], a
 * tap on the static map or its expand button calls it (`feed/route-map-viewer`). Web's preview ignores both. A [highlight] polyline
 * (precision
 * 5) is drawn over the route in the accent colour, such as the stretch chosen for a new segment.
 */
@Composable
expect fun ActivityMap(
    polyline: String,
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
    gaps: ImmutableList<Int>? = null,
    ends: RouteEnds? = null,
    onExpand: (() -> Unit)? = null,
    interactive: Boolean = false,
    highlight: String? = null,
)

/** OpenFreeMap styles: free, no API key or request limits. Swap the URLs to change tile provider. */
internal object MapStyles {
    const val LIGHT = "https://tiles.openfreemap.org/styles/liberty"
    const val DARK = "https://tiles.openfreemap.org/styles/dark"
}
