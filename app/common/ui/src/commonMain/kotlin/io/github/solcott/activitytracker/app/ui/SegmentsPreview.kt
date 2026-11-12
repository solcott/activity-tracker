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
import io.github.solcott.activitytracker.app.MapSegmentRole
import io.github.solcott.activitytracker.app.SegmentsMap
import kotlinx.collections.immutable.ImmutableList

/**
 * [SegmentsMap]'s stand-in where MapLibre can't run (previews, tests and screenshot tools): every line scaled together to fit, runs in the
 * primary colour, lifts dashed and muted, and the [selected] run in the accent colour, on the surface colour rather than map tiles.
 */
@Composable
internal fun SegmentsPreview(segments: ImmutableList<MapSegmentLine>, selected: String?, modifier: Modifier = Modifier) {
    val map = remember(segments, selected) { SegmentsMap.of(segments, selected) }
    val run = MaterialTheme.colorScheme.primary
    val lift = MaterialTheme.colorScheme.outline
    val highlight = MaterialTheme.colorScheme.tertiary
    Canvas(modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        val at = canvasFit(map.allPoints, size, pad = 16.dp.toPx()) ?: return@Canvas

        val dashes = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        // In the shared drawing order: lifts, then runs, then the selected run on top.
        for (line in map.drawn) {
            val path =
                Path().apply { line.points.map { at(it) }.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
            val (color, width, effect) =
                when (line.role) {
                    MapSegmentRole.Lift -> Triple(lift, 2.dp, dashes)
                    MapSegmentRole.Selected -> Triple(highlight, 6.dp, null)
                    MapSegmentRole.Run -> Triple(run, 4.dp, null)
                }
            drawPath(path, color, style = Stroke(width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
        }
    }
}
