package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.RouteEnds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Where a route map puts its markers: the [start] and [end] of the whole line (null where a privacy zone hides it, [shownAt]), and the
 * [breakEnds] either side of each break (`feed.activity#polylineGaps`), the last point before it and the first after, in order.
 */
@Immutable
internal data class RouteMarkers<T>(val start: T?, val end: T?, val breakEnds: ImmutableList<T>) {
    companion object {
        /** The markers of a line drawn as [pieces] (`SegmentedPolyline.pieces`); null when there are no points. */
        fun <T> of(pieces: List<List<T>>): RouteMarkers<T>? {
            val drawn = pieces.filter { it.isNotEmpty() }
            if (drawn.isEmpty()) return null
            val breakEnds = drawn.zipWithNext().flatMap { (before, after) -> listOf(before.last(), after.first()) }.toImmutableList()
            return RouteMarkers(drawn.first().first(), drawn.last().last(), breakEnds)
        }

        /** The start marker's fill; the end's is [END_COLOR]. Both are ringed in white, and break ends in the route's colour. */
        val START_COLOR = Color(0xFF43A047)
        val END_COLOR = Color(0xFFE53935)

        /** The start and end markers' radius, and the smaller break ends', each including its [RING]. */
        val END_RADIUS = 7.dp
        val BREAK_RADIUS = 5.dp
        val RING = 2.dp
    }
}

/** These markers with the start and end at [ends] when given: the owner's untrimmed line marks the ends of the route others see. */
internal fun RouteMarkers<Pair<Double, Double>>.shownAt(ends: RouteEnds?): RouteMarkers<Pair<Double, Double>> =
    if (ends == null) this else copy(start = ends.start, end = ends.end)
