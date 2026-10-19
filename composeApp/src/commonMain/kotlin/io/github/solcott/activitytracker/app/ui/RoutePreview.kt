package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.RouteEnds
import io.github.solcott.activitytracker.data.activity.SegmentedPolyline
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.math.cos
import kotlinx.collections.immutable.ImmutableList

/**
 * Draws an encoded polyline scaled to fit, broken at [gaps] (`feed.activity#polylineGaps`), with its [RouteMarkers] (the start and end at
 * [ends] when given). Placeholder until a real map is integrated.
 */
@Composable
fun RoutePreview(
    polyline: String,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    gaps: ImmutableList<Int>? = null,
    ends: RouteEnds? = null,
) {
    val points = remember(polyline) { runCatching { TrackMath.decodePolyline(polyline) }.getOrDefault(emptyList()) }
    if (points.size < 2) return
    val markers = remember(points, gaps, ends) { RouteMarkers.of(SegmentedPolyline.pieces(points, gaps))?.shownAt(ends) }
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(height)) {
        val latScale = cos(points.first().first * kotlin.math.PI / 180)
        val xs = points.map { it.second * latScale }
        val ys = points.map { -it.first }
        val minX = xs.min()
        val minY = ys.min()
        val spanX = (xs.max() - minX).coerceAtLeast(1e-9)
        val spanY = (ys.max() - minY).coerceAtLeast(1e-9)
        val pad = 12.dp.toPx()
        val scale = minOf((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY)
        val offsetX = (size.width - spanX * scale) / 2
        val offsetY = (size.height - spanY * scale) / 2

        fun at(point: Pair<Double, Double>) =
            Offset(
                ((point.second * latScale - minX) * scale + offsetX).toFloat(),
                ((-point.first - minY) * scale + offsetY).toFloat(),
            )
        fun at(i: Int) = at(points[i])
        val pieces = SegmentedPolyline.pieces(points.indices.toList(), gaps)
        val path =
            Path().apply {
                for (piece in pieces) {
                    moveTo(at(piece.first()).x, at(piece.first()).y)
                    for (i in piece.drop(1)) lineTo(at(i).x, at(i).y)
                }
            }
        drawPath(path, color, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
        markers?.let { (start, end, breakEnds) ->
            for (point in breakEnds) drawMarker(at(point), RouteMarkers.BREAK_RADIUS, fill = Color.White, ring = color)
            start?.let { drawMarker(at(it), RouteMarkers.END_RADIUS, fill = RouteMarkers.START_COLOR, ring = Color.White) }
            end?.let { drawMarker(at(it), RouteMarkers.END_RADIUS, fill = RouteMarkers.END_COLOR, ring = Color.White) }
        }
    }
}

/** A dot of [radius] filled with [fill] and ringed with [ring], [RouteMarkers.RING] wide, as `ActivityMap` draws its markers. */
private fun DrawScope.drawMarker(center: Offset, radius: Dp, fill: Color, ring: Color) {
    drawCircle(ring, radius.toPx(), center)
    drawCircle(fill, (radius - RouteMarkers.RING).toPx(), center)
}
