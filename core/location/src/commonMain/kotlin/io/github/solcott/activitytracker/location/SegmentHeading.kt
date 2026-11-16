package io.github.solcott.activitytracker.location

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos

/**
 * Which way a segment's line goes: a cardinal direction from its start to its end, or clockwise or counterclockwise for a loop. [word] is
 * the English word the segment importer adds to a two-way trail's name (segments/trail-candidates); apps show their own labels.
 */
enum class SegmentHeading(val word: String) {
    Northbound("northbound"),
    Eastbound("eastbound"),
    Southbound("southbound"),
    Westbound("westbound"),
    Clockwise("clockwise"),
    Counterclockwise("counterclockwise");

    companion object {
        /** How far apart a line's ends may be for it to be a loop, in meters: the duplicate distance. */
        const val LOOP_M = SegmentGeometry.DUPLICATE_DISTANCE_M

        private const val QUARTER_TURN = 90.0
        private const val HALF_TURN = 180.0
        private const val FULL_TURN = 360.0
        private const val EIGHTH_TURN = 45.0
        private val COMPASS = listOf(Northbound, Eastbound, Southbound, Westbound)

        /** The heading named by [word], if it is one of [entries]' words. */
        fun ofWord(word: String): SegmentHeading? = entries.firstOrNull { it.word == word }

        /**
         * Which way [points] go: clockwise or counterclockwise for a loop (its ends within [LOOP_M]), by the sign of its area, otherwise
         * the cardinal direction from start to end. [points] must not be empty.
         */
        fun of(points: List<GeoPoint>): SegmentHeading {
            val (first, last) = points.first() to points.last()
            if (SegmentGeometry.distanceMeters(first, last) > LOOP_M) return compass(first, last)
            val scale = cos(first.lat * PI / HALF_TURN)
            // The shoelace sum, with x east and y north: positive for a counterclockwise ring.
            val area = (points + first).zipWithNext().sumOf { (p, q) -> (p.lng * scale) * q.lat - (q.lng * scale) * p.lat }
            return if (area > 0) Counterclockwise else Clockwise
        }

        /** The cardinal direction of travel from [from] to [to]. */
        fun compass(from: GeoPoint, to: GeoPoint): SegmentHeading {
            val dx = (to.lng - from.lng) * cos(from.lat * PI / HALF_TURN)
            val dy = to.lat - from.lat
            val bearing = (atan2(dx, dy) * HALF_TURN / PI + FULL_TURN) % FULL_TURN
            return COMPASS[(((bearing + EIGHTH_TURN) % FULL_TURN) / QUARTER_TURN).toInt()]
        }
    }
}
