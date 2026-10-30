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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.RouteEnds
import io.github.solcott.activitytracker.data.activity.SegmentedPolyline
import io.github.solcott.activitytracker.location.RouteCurve
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.math.cos
import kotlinx.collections.immutable.ImmutableList

/**
 * Draws an encoded polyline scaled to fit, broken at [gaps] (`feed.activity#polylineGaps`), with its [RouteMarkers] (the start and end at
 * [ends] when given), and a [highlight] polyline over it in the accent colour, as `ActivityMap` draws one. The maps' stand-in: under a feed
 * card's map image until it's drawn or when it can't be (`RouteMapTile`), in previews and tests, and on web, which can't draw maps yet.
 */
@Composable
fun RoutePreview(
    polyline: String,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    gaps: ImmutableList<Int>? = null,
    ends: RouteEnds? = null,
    highlight: String? = null,
) {
    val points = remember(polyline) { runCatching { TrackMath.decodePolyline(polyline) }.getOrDefault(emptyList()) }
    if (points.size < 2) return
    val stretch = remember(highlight) { highlight?.let { runCatching { TrackMath.decodePolyline(it) }.getOrNull() }.orEmpty() }
    val highlightColor = MaterialTheme.colorScheme.tertiary
    val markers = remember(points, gaps, ends) { RouteMarkers.of(SegmentedPolyline.pieces(points, gaps))?.shownAt(ends) }
    // Drawn as curves through the stored points (feed/route-line-detail); markers and framing keep the points themselves.
    val curves = remember(points, gaps) { SegmentedPolyline.pieces(points, gaps).map(RouteCurve::smooth) }
    val stretchCurve = remember(stretch) { RouteCurve.smooth(stretch) }
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
        val path =
            Path().apply {
                for (piece in curves) {
                    moveTo(at(piece.first()).x, at(piece.first()).y)
                    for (p in piece.drop(1)) lineTo(at(p).x, at(p).y)
                }
            }
        drawPath(path, color, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawHighlight(stretchCurve.map(::at), highlightColor)
        markers?.let { (start, end, breaks) ->
            val dashes = PathEffect.dashPathEffect(floatArrayOf(RouteMarkers.CONNECTOR_DASH.toPx(), RouteMarkers.CONNECTOR_SPACE.toPx()))
            for ((stop, resume) in breaks) {
                drawLine(RouteMarkers.BREAK_COLOR, at(stop), at(resume), RouteMarkers.CONNECTOR_WIDTH.toPx(), pathEffect = dashes)
            }
            for ((stop) in breaks) drawMarker(at(stop), RouteMarkers.BREAK_RADIUS, fill = RouteMarkers.BREAK_COLOR, ring = Color.White)
            for ((_, resume) in breaks) {
                drawMarker(
                    at(resume),
                    RouteMarkers.BREAK_RADIUS,
                    fill = Color.White,
                    ring = RouteMarkers.BREAK_COLOR,
                    RouteMarkers.RESUME_RING,
                )
            }
            start?.let { drawMarker(at(it), RouteMarkers.END_RADIUS, fill = RouteMarkers.START_COLOR, ring = Color.White) }
            end?.let { drawMarker(at(it), RouteMarkers.END_RADIUS, fill = RouteMarkers.END_COLOR, ring = Color.White) }
        }
    }
}

/** [points], a highlighted stretch already placed on the canvas, as a wider line over the route; nothing for fewer than two. */
private fun DrawScope.drawHighlight(points: List<Offset>, color: Color) {
    if (points.size < 2) return
    val path =
        Path().apply {
            moveTo(points.first().x, points.first().y)
            for (p in points.drop(1)) lineTo(p.x, p.y)
        }
    drawPath(path, color, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A dot of [radius] filled with [fill] and ringed with [ring], [ringWidth] wide, as `ActivityMap` draws its markers. */
private fun DrawScope.drawMarker(center: Offset, radius: Dp, fill: Color, ring: Color, ringWidth: Dp = RouteMarkers.RING) {
    drawCircle(ring, radius.toPx(), center)
    drawCircle(fill, (radius - ringWidth).toPx(), center)
}
