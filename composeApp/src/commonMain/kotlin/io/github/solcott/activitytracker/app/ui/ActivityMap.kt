package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * An activity's route on a map, framed to fit the whole track. Android, iOS and desktop use MapLibre; MapLibre Compose has no wasmJs
 * target yet (maplibre/maplibre-compose#209, waiting on Compose Multiplatform 1.13; see BACKLOG.md), so web falls back to [RoutePreview]. Keep MapLibre's pre-1.0 API inside the nonWeb actual so its churn stays in one file.
 */
@Composable expect fun ActivityMap(polyline: String, modifier: Modifier = Modifier, height: Dp = 240.dp)

/** OpenFreeMap styles: free, no API key or request limits. Swap the URLs to change tile provider. */
internal object MapStyles {
    const val LIGHT = "https://tiles.openfreemap.org/styles/liberty"
    const val DARK = "https://tiles.openfreemap.org/styles/dark"
}
