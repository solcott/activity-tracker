package io.github.solcott.activitytracker.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.solcott.activitytracker.location.GeoPoint
import kotlin.math.PI
import kotlin.math.cos

/**
 * Where each point goes on a canvas of [size] to fit all of [points] inside [pad]: scaled together, centred, north up, with longitudes
 * shrunk by the cosine of the first point's latitude so shapes keep their proportions. Null for no points. The map stand-ins draw routes
 * and segments with it ([RoutePreview], [SegmentsPreview]).
 */
internal fun canvasFit(points: List<GeoPoint>, size: Size, pad: Float): ((GeoPoint) -> Offset)? {
    if (points.isEmpty()) return null
    val latScale = cos(points.first().lat * PI / HALF_TURN)
    val minX = points.minOf { it.lng * latScale }
    val maxY = points.maxOf { it.lat }
    val spanX = (points.maxOf { it.lng * latScale } - minX).coerceAtLeast(MIN_SPAN)
    val spanY = (maxY - points.minOf { it.lat }).coerceAtLeast(MIN_SPAN)
    val scale = minOf((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY)
    val offsetX = (size.width - spanX * scale) / 2
    val offsetY = (size.height - spanY * scale) / 2
    return { point ->
        Offset(((point.lng * latScale - minX) * scale + offsetX).toFloat(), ((maxY - point.lat) * scale + offsetY).toFloat())
    }
}

private const val HALF_TURN = 180.0
private const val MIN_SPAN = 1e-9
