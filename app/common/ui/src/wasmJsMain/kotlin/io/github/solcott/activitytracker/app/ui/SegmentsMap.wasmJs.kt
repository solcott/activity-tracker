package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.solcott.activitytracker.app.MapCameraRequest
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.MapViewport
import kotlinx.collections.immutable.ImmutableList

/** No MapLibre on wasmJs yet: segment browse leaves the map out on web and browses by name only (segments/segment-browse). */
actual val interactiveMapSupported: Boolean = false

/** Never placed while [interactiveMapSupported] is false; draws the canvas stand-in if it is. */
@Composable
actual fun SegmentsMap(
    segments: ImmutableList<MapSegmentLine>,
    selected: String?,
    camera: MapCameraRequest?,
    onViewport: (MapViewport) -> Unit,
    onSelect: (String?) -> Unit,
    modifier: Modifier,
) = SegmentsPreview(segments, selected, modifier)
