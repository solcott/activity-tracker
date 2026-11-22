package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.MapSegmentRole
import io.github.solcott.activitytracker.app.SegmentsMap
import io.github.solcott.activitytracker.app.segment.TrailRating
import io.github.solcott.activitytracker.app.ui.map.TrailRatingColors
import io.github.solcott.activitytracker.location.GeoPoint
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * [SegmentsMap]'s stand-in where MapLibre can't run (previews, tests and screenshot tools): every line scaled together to fit, runs in
 * their rating level's colour on its casing (segments/trail-ratings), lifts dashed and muted, and the [selected] and [hovered] runs in the
 * accent colour, on the surface colour rather than map tiles, with the map's legend in its top-start corner.
 */
@Composable
internal fun SegmentsPreview(
    segments: ImmutableList<MapSegmentLine>,
    selected: String?,
    modifier: Modifier = Modifier,
    hovered: String? = null,
    hoveredLines: ImmutableSet<String> = persistentSetOf(),
    marker: GeoPoint? = null,
) {
    val map =
        remember(segments, selected, hovered, hoveredLines) {
            SegmentsMap.of(segments, selected, hoveredLines = hoveredLines + setOfNotNull(hovered))
        }
    val ratings = TrailRatingColors.current()
    val plain = MaterialTheme.colorScheme.primary
    val lift = MaterialTheme.colorScheme.outline
    val highlight = MaterialTheme.colorScheme.tertiary
    val ringColor = MaterialTheme.colorScheme.surface
    Box(modifier) {
        Canvas(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
            val at = canvasFit(map.allPoints, size, pad = 16.dp.toPx()) ?: return@Canvas

            val dashes = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            // In the shared drawing order: lifts, then runs, then the selected and hovered runs on top.
            for (line in map.drawn) {
                val path =
                    Path().apply {
                        line.points.map { at(it) }.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                    }
                fun stroke(color: Color, width: Dp, effect: PathEffect? = null) =
                    drawPath(path, color, style = Stroke(width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
                when (line.role) {
                    MapSegmentRole.Lift -> stroke(lift, 2.dp, dashes)
                    MapSegmentRole.Selected -> stroke(highlight, 6.dp)
                    MapSegmentRole.Run if !line.trail -> stroke(plain, LINE_WIDTH)
                    MapSegmentRole.Run -> {
                        val level = line.rating?.ordinal
                        stroke(ratings.casing(level), LINE_WIDTH + CASING_EXTRA)
                        stroke(ratings.line(level), LINE_WIDTH)
                        if (line.rating == TrailRating.EXTREME) stroke(TrailRatingColors.EXTREME_CENTRE, LINE_WIDTH / 3)
                    }
                }
            }
            marker?.let { drawMarker(at(it), highlight, ringColor) }
        }
        RatingLegend(map.legend, Modifier.align(Alignment.TopStart).padding(8.dp))
    }
}

/** The marker a [SegmentsMap] puts at a place: a dot in [fill] ringed in [ring], as the map draws it. */
private fun DrawScope.drawMarker(center: Offset, fill: Color, ring: Color) {
    drawCircle(ring, MARKER_RADIUS.toPx(), center)
    drawCircle(fill, (MARKER_RADIUS - MARKER_RING).toPx(), center)
}

/** The marker's radius and its ring's width, on every map of segments. */
internal val MARKER_RADIUS = 7.dp
internal val MARKER_RING = 2.dp
