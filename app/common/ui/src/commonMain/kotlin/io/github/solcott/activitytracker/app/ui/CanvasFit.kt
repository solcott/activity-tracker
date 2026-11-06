package io.github.solcott.activitytracker.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.PI
import kotlin.math.cos

/**
 * Where each latitude/longitude pair goes on a canvas of [size] to fit all of [points] inside [pad]: scaled together, centred, north up,
 * with longitudes shrunk by the cosine of the first point's latitude so shapes keep their proportions. Null for no points. The map
 * stand-ins draw routes and segments with it ([RoutePreview], [SegmentsPreview]).
 */
internal fun canvasFit(points: List<Pair<Double, Double>>, size: Size, pad: Float): ((Pair<Double, Double>) -> Offset)? {
    if (points.isEmpty()) return null
    val latScale = cos(points.first().first * PI / HALF_TURN)
    val minX = points.minOf { it.second * latScale }
    val maxY = points.maxOf { it.first }
    val spanX = (points.maxOf { it.second * latScale } - minX).coerceAtLeast(MIN_SPAN)
    val spanY = (maxY - points.minOf { it.first }).coerceAtLeast(MIN_SPAN)
    val scale = minOf((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY)
    val offsetX = (size.width - spanX * scale) / 2
    val offsetY = (size.height - spanY * scale) / 2
    return { point ->
        Offset(((point.second * latScale - minX) * scale + offsetX).toFloat(), ((maxY - point.first) * scale + offsetY).toFloat())
    }
}

private const val HALF_TURN = 180.0
private const val MIN_SPAN = 1e-9
