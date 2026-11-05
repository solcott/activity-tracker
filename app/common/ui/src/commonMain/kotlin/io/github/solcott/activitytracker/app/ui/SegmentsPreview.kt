package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.math.PI
import kotlin.math.cos
import kotlinx.collections.immutable.ImmutableList

/**
 * [SegmentsMap]'s stand-in where MapLibre can't run (previews, tests and screenshot tools): every line scaled together to fit, runs in the
 * primary colour, lifts dashed and muted, and the [selected] run in the accent colour, on the surface colour rather than map tiles.
 */
@Composable
internal fun SegmentsPreview(segments: ImmutableList<MapSegmentLine>, selected: String?, modifier: Modifier = Modifier) {
    val lines =
        remember(segments) {
            segments.mapNotNull { line ->
                runCatching { TrackMath.decodePolyline(line.polyline) }.getOrNull()?.takeIf { it.size >= 2 }?.let { line to it }
            }
        }
    val run = MaterialTheme.colorScheme.primary
    val lift = MaterialTheme.colorScheme.outline
    val highlight = MaterialTheme.colorScheme.tertiary
    Canvas(modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        val points = lines.flatMap { it.second }
        if (points.isEmpty()) return@Canvas
        val latScale = cos(points.first().first * PI / HALF_TURN)
        val minX = points.minOf { it.second * latScale }
        val maxY = points.maxOf { it.first }
        val spanX = (points.maxOf { it.second * latScale } - minX).coerceAtLeast(MIN_SPAN)
        val spanY = (maxY - points.minOf { it.first }).coerceAtLeast(MIN_SPAN)
        val pad = 16.dp.toPx()
        val scale = minOf((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY)
        val offsetX = (size.width - spanX * scale) / 2
        val offsetY = (size.height - spanY * scale) / 2

        fun at(point: Pair<Double, Double>) =
            Offset(((point.second * latScale - minX) * scale + offsetX).toFloat(), ((maxY - point.first) * scale + offsetY).toFloat())

        val dashes = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        // Lifts first, then runs, then the selected run on top.
        val ordered = lines.sortedBy { (line, _) -> if (line.lift) 0 else if (line.uri == selected) 2 else 1 }
        for ((line, linePoints) in ordered) {
            val path = Path().apply { linePoints.map(::at).forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
            val (color, width, effect) =
                when {
                    line.lift -> Triple(lift, 2.dp, dashes)
                    line.uri == selected -> Triple(highlight, 6.dp, null)
                    else -> Triple(run, 4.dp, null)
                }
            drawPath(path, color, style = Stroke(width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
        }
    }
}

private const val HALF_TURN = 180.0
private const val MIN_SPAN = 1e-9
