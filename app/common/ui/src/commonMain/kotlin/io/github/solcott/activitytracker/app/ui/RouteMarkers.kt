package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.RouteEnds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Where a route map puts its markers: the [start] and [end] of the whole line (null where a privacy zone hides it, [shownAt]), and the
 * [breaks] of the line (`feed.activity#polylineGaps`), in order.
 */
@Immutable
internal data class RouteMarkers<T>(val start: T?, val end: T?, val breaks: ImmutableList<Break<T>>) {
    /** A break in the line: the last point before it, where recording stopped, and the first after it, where it resumed. */
    @Immutable data class Break<T>(val stop: T, val resume: T)

    companion object {
        /** The markers of a line drawn as [pieces] (`SegmentedPolyline.pieces`); null when there are no points. */
        fun <T> of(pieces: List<List<T>>): RouteMarkers<T>? {
            val drawn = pieces.filter { it.isNotEmpty() }
            if (drawn.isEmpty()) return null
            val breaks = drawn.zipWithNext { before, after -> Break(before.last(), after.first()) }.toImmutableList()
            return RouteMarkers(drawn.first().first(), drawn.last().last(), breaks)
        }

        /** The start marker's fill; the end's is [END_COLOR]. Both are ringed in white. */
        val START_COLOR = Color(0xFF43A047)
        val END_COLOR = Color(0xFFE53935)

        /**
         * A break's colour, apart from the route's, the start's and the end's: the fill of its stop marker (ringed in white), the ring of
         * its resume marker (filled white) and its dashed connector.
         */
        val BREAK_COLOR = Color(0xFFFFA000)

        /** The start, end and break markers' radius, each including its ring: [RING], or [RESUME_RING] on a resume marker. */
        val END_RADIUS = 7.dp
        val BREAK_RADIUS = END_RADIUS
        val RING = 2.dp
        val RESUME_RING = 3.dp

        /** The dashed line joining a break's stop and resume: its width, and the length of each dash and of the space after it. */
        val CONNECTOR_WIDTH = 2.5.dp
        val CONNECTOR_DASH = 8.dp
        val CONNECTOR_SPACE = 5.dp
    }
}

/** These markers with the start and end at [ends] when given: the owner's untrimmed line marks the ends of the route others see. */
internal fun RouteMarkers<Pair<Double, Double>>.shownAt(ends: RouteEnds?): RouteMarkers<Pair<Double, Double>> =
    if (ends == null) this else copy(start = ends.start, end = ends.end)
