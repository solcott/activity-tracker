package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

/**
 * A combined activity's route ([ActivityCombiner]): positions follow the same rule as every other channel, each moment from the
 * highest-ranked member with a position then, so a device that died hands over to the next without a jump back in time.
 */
object CombinedRoute {
    /**
     * [members]' tracks joined in time order: each member's points within its [covered] stretches, where no higher-ranked member in
     * [ranking] (best first) has a point within [ActivityCombiner.SAMPLE_TOLERANCE_SECONDS].
     */
    fun build(members: List<CombineMember>, ranking: List<AtUri>, covered: Map<AtUri, List<Span>> = emptyMap()): List<LocationSample> {
        val byUri = members.associateBy { it.uri }
        val kept = mutableListOf<List<LocationSample>>()
        for (uri in ranking) {
            // Only where the member really recorded ([CombinedCoverage.of]): not a dead device's leftover points.
            val spans = covered[uri]
            val track =
                byUri[uri]?.track?.sortedBy { it.time }?.filter { p -> spans == null || spans.any { p.time in it.start..it.end } }
                    ?: continue
            val higher = kept.flatten().map { it.time.toEpochMilliseconds() }.sorted()
            kept += track.filter { point -> !covered(higher, point.time.toEpochMilliseconds()) }
        }
        return kept.flatten().sortedBy { it.time }
    }

    private fun covered(times: List<Long>, at: Long): Boolean {
        val found = times.binarySearch(at)
        val after = -found - 1
        return found >= 0 || listOf(after - 1, after).any { it in times.indices && abs(times[it] - at) <= TOLERANCE_MILLIS }
    }

    /**
     * [route] (in time order) cut wherever no position was recorded for more than [BREAK_SECONDS]: a shuttle, a lift or a lunch between
     * members recorded one after another. Only combined activities are cut, since their gaps are between recordings.
     */
    fun segments(route: List<LocationSample>): List<List<LocationSample>> {
        if (route.isEmpty()) return emptyList()
        val segments = mutableListOf(mutableListOf(route.first()))
        for ((previous, point) in route.zipWithNext()) {
            if (point.time - previous.time > BREAK_SECONDS.seconds) segments += mutableListOf(point) else segments.last() += point
        }
        return segments
    }

    /** Positions further apart in time than this break a combined activity's map line. */
    const val BREAK_SECONDS = 120

    private const val TOLERANCE_MILLIS = ActivityCombiner.SAMPLE_TOLERANCE_SECONDS * 1000L
}

/**
 * A polyline drawn in pieces: [polyline] is every piece's simplified points in order, and [gaps] the indices of its decoded points after
 * which the line breaks (`feed.activity#polylineGaps`), null when it's one piece.
 */
data class SegmentedPolyline(val polyline: String, val gaps: List<Int>?) {
    companion object {
        /**
         * How closely a published polyline follows its track, in meters (feed/route-line-detail). [LINE_VERSION] names it, so a change here
         * bumps that too and existing polylines are rebuilt.
         */
        const val TOLERANCE_M = 2.0

        /** How closely the owner's map follows their full track, in meters: there's no record to fit. */
        const val OWN_TOLERANCE_M = 1.0

        /** The longest a published polyline may be (`feed.activity#polyline`'s `maxLength`); the AppView drops a longer one. */
        const val MAX_CHARS = 50_000

        /**
         * The polyline detail [TOLERANCE_M] gives, as `actor.routeTrim#lineVersion` records it: 0 was 5 m. Polylines last rebuilt at a
         * lower version are rebuilt from their tracks ([RouteTrimmer]).
         */
        const val LINE_VERSION = 1

        /**
         * [segments], with fewer than two points left out, each simplified at one tolerance: [toleranceMeters], or coarser only as far as
         * the whole encoding needs to fit [maxChars] (`TrackMath.simplifyToFit`). Null when none is left.
         */
        fun of(
            segments: List<List<LocationSample>>,
            toleranceMeters: Double = TOLERANCE_M,
            maxChars: Int = MAX_CHARS,
        ): SegmentedPolyline? {
            val kept = segments.filter { it.size >= 2 }
            if (kept.isEmpty()) return null
            val pieces = TrackMath.simplifyToFit(kept, toleranceMeters, maxChars) { TrackMath.encodePolyline(it.flatten()) }
            val gaps = pieces.dropLast(1).runningFold(0) { end, piece -> end + piece.size }.drop(1).map { it - 1 }
            return SegmentedPolyline(TrackMath.encodePolyline(pieces.flatten()), gaps.ifEmpty { null })
        }

        /** Splits a decoded polyline's [points] at [gaps], for drawing. */
        fun <T> pieces(points: List<T>, gaps: List<Int>?): List<List<T>> {
            if (gaps.isNullOrEmpty()) return listOf(points)
            val cuts = gaps.filter { it in 0 until points.lastIndex }.distinct().sorted()
            val bounds = listOf(-1) + cuts + points.lastIndex
            return bounds.zipWithNext { after, end -> points.subList(after + 1, end + 1) }.filter { it.isNotEmpty() }
        }
    }
}
