package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.solcott.activitytracker.app.MapCameraRequest
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.MapViewport
import kotlinx.collections.immutable.ImmutableList

/**
 * Whether this platform draws [SegmentsMap]. MapLibre Compose has no wasmJs target yet (see [ActivityMap]), so the web leaves the map out
 * and browses segments by name only (segments/segment-browse).
 */
expect val interactiveMapSupported: Boolean

/**
 * Many segments' lines on a map the viewer pans and zooms, north-up (segments/segment-browse): runs in the primary colour, lifts muted and
 * dashed, and the [selected] and [hovered] runs highlighted alike. A tap on a run calls [onSelect] with its URI, and a tap away from every
 * run with null. A mouse pointer over a run calls [onHover] with its URI, and with null once it leaves every run or the map. Each time the
 * camera stops, [onViewport] gets what the map shows. Each new [camera] request moves the map once, framing its box at the map's size when
 * it has one. Previews and screenshot tests draw the lines on a plain canvas instead.
 */
@Composable
expect fun SegmentsMap(
    segments: ImmutableList<MapSegmentLine>,
    selected: String?,
    camera: MapCameraRequest?,
    onViewport: (MapViewport) -> Unit,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    hovered: String? = null,
    onHover: (String?) -> Unit = {},
)
