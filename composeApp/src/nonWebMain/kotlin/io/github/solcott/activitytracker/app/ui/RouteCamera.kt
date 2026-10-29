package io.github.solcott.activitytracker.app.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.tan
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position

/** The margin left around the route when it's framed. */
internal val FIT_MARGIN = 24.dp

/** MapLibre's world is this many dp wide at zoom 0. */
private const val WORLD_DP = 512.0

/** Degrees in a turn and a half turn. */
private const val FULL_TURN = 360.0
private const val HALF_TURN = 180.0

/** The closest a framed route is drawn: a few metres of route doesn't fill a card with one building. */
internal const val MAX_FIT_ZOOM = 16.0

/**
 * The north-up camera that frames [bounds] in a map of [size] with [margin] on every side, as `cameraForBounds` would on a live map; a
 * snapshot has no map state to ask. The zoom fits the tighter of the two directions in Web Mercator and is clamped to [0, MAX_FIT_ZOOM].
 */
internal fun fitCamera(bounds: BoundingBox, size: DpSize, margin: Dp = FIT_MARGIN): CameraPosition {
    val west = mercatorX(bounds.west)
    val east = mercatorX(bounds.east)
    val north = mercatorY(bounds.north)
    val south = mercatorY(bounds.south)
    val width = (size.width - margin * 2).value.coerceAtLeast(1f)
    val height = (size.height - margin * 2).value.coerceAtLeast(1f)
    // A span of 0 (a single point, or a due north-south or east-west line) fits at any zoom in that direction.
    val zoomX = if (east > west) log2(width / ((east - west) * WORLD_DP)) else Double.POSITIVE_INFINITY
    val zoomY = if (south > north) log2(height / ((south - north) * WORLD_DP)) else Double.POSITIVE_INFINITY
    val zoom = min(zoomX, zoomY).coerceIn(0.0, MAX_FIT_ZOOM)
    val center = Position(longitude = (west + east) / 2 * FULL_TURN - HALF_TURN, latitude = latitude((north + south) / 2))
    return CameraPosition(center = center, zoom = zoom)
}

/** [longitude] as a fraction of the world's width, from the antimeridian eastwards. */
private fun mercatorX(longitude: Double): Double = (longitude + HALF_TURN) / FULL_TURN

/** [latitude] as a fraction of the Web Mercator world's height, from the top. */
private fun mercatorY(latitude: Double): Double {
    val phi = latitude * PI / HALF_TURN
    return (1 - ln(tan((PI / 2 + phi) / 2)) / PI) / 2
}

/** The latitude at [y], a fraction of the world's height from the top: [mercatorY]'s inverse. */
private fun latitude(y: Double): Double = (2 * atan(exp(PI * (1 - 2 * y))) - PI / 2) * HALF_TURN / PI
