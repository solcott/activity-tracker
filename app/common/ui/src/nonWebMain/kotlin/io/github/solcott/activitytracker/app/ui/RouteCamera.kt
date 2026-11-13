package io.github.solcott.activitytracker.app.ui

import io.github.solcott.activitytracker.app.MapFrame
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.spatialk.geojson.Position

/**
 * The frame as MapLibre's north-up camera. [MapFrame]'s zoom is MapLibre's (a 512-unit world at zoom 0) and a Compose map's units are dp,
 * so a frame asked for the map's dp size is the camera that draws it so; the live map and the feed's map images both set it.
 */
internal fun MapFrame.toCameraPosition(): CameraPosition = CameraPosition(center = center(), zoom = zoom)

/** The frame as a camera move, for animating to it (Recenter). */
internal fun MapFrame.toCameraUpdate(): CameraUpdate = CameraUpdate(center = center(), zoom = zoom)

private fun MapFrame.center(): Position = Position(longitude = longitude, latitude = latitude)
