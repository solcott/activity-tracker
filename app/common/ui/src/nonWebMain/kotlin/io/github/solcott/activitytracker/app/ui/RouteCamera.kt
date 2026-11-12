package io.github.solcott.activitytracker.app.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.MapBounds
import io.github.solcott.activitytracker.app.MapFrame
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position

/** The margin left around the route when it's framed. */
internal val FIT_MARGIN = MapFrame.MARGIN.dp

/** The closest a framed route is drawn: a few metres of route doesn't fill a card with one building. */
internal const val MAX_FIT_ZOOM = MapFrame.MAX_ZOOM

/**
 * The north-up camera that frames [bounds] in a map of [size] with [margin] on every side, as `cameraForBounds` would on a live map; a
 * snapshot has no map state to ask. The frame is [MapFrame.fit]'s, which the native iOS map frames with too.
 */
internal fun fitCamera(bounds: BoundingBox, size: DpSize, margin: Dp = FIT_MARGIN): CameraPosition {
    val frame =
        MapFrame.fit(
            MapBounds(west = bounds.west, south = bounds.south, east = bounds.east, north = bounds.north),
            size.width.value.toDouble(),
            size.height.value.toDouble(),
            margin = margin.value.toDouble(),
        )
    return CameraPosition(center = Position(longitude = frame.longitude, latitude = frame.latitude), zoom = frame.zoom)
}
