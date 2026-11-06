package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.location.TrackMath
import kotlinx.collections.immutable.ImmutableList

/**
 * [SegmentsMap]'s stand-in where MapLibre can't run (previews, tests and screenshot tools): every line scaled together to fit, runs in the
 * primary colour, lifts dashed and muted, and the [selected] run in the accent colour, on the surface colour rather than map tiles.
 */
@Composable
internal fun SegmentsPreview(segments: ImmutableList<MapSegmentLine>, selected: String?, modifier: Modifier = Modifier) {
    val lines =
        remember(segments) {
            segments.mapNotNull { line -> TrackMath.decodePolylineOrNull(line.polyline)?.takeIf { it.size >= 2 }?.let { line to it } }
        }
    val run = MaterialTheme.colorScheme.primary
    val lift = MaterialTheme.colorScheme.outline
    val highlight = MaterialTheme.colorScheme.tertiary
    Canvas(modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        val at = canvasFit(lines.flatMap { it.second }, size, pad = 16.dp.toPx()) ?: return@Canvas

        val dashes = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        // Lifts first, then runs, then the selected run on top.
        val ordered = lines.sortedBy { (line, _) -> if (line.lift) 0 else if (line.uri == selected) 2 else 1 }
        for ((line, linePoints) in ordered) {
            val path =
                Path().apply { linePoints.map { at(it) }.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
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
