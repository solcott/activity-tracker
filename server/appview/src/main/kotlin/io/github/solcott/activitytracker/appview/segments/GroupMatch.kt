package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.location.TimedPoint
import io.github.solcott.activitytracker.location.TrackMath

/**
 * Whether two athletes' activities were done together (feed/group-activities), from their segment tracks with each point's `t` in absolute
 * epoch seconds. Pure, so it's tested on its own; the [SegmentMatcher]'s `group` job decides who may be compared.
 */
object GroupMatch {
    /** One activity: its shown pieces (privacy-zone gaps between them) and its elapsed time in seconds. */
    data class Track(val pieces: List<List<TimedPoint>>, val elapsedS: Double)

    /** The shortest overlap that counts, in seconds. */
    const val MIN_OVERLAP_S = 300.0
    /** The share of the shorter activity's elapsed time the overlap must cover. */
    const val MIN_OVERLAP_SHARE = 0.5
    /** How often the overlap is sampled, in seconds. */
    const val SAMPLE_S = 30.0
    /** The fewest samples where both tracks have a position. */
    const val MIN_SHARED_SAMPLES = 10
    /** The share of those samples that must be within [toleranceM] of each other. */
    const val MIN_CLOSE_SHARE = 0.6
    /** How close two athletes are at one moment to count as together, in meters. */
    const val TOLERANCE_M = 100.0
    /** Paddlers spread out more on the water. */
    const val PADDLE_TOLERANCE_M = 150.0

    /** The distance within which two athletes of [group] count as together at one moment. */
    fun toleranceM(group: String): Double = if (group == SegmentSportGroup.PADDLE) PADDLE_TOLERANCE_M else TOLERANCE_M

    /**
     * Whether [a] and [b] overlap for at least [MIN_OVERLAP_S] and [MIN_OVERLAP_SHARE] of the shorter one, and, sampled every [SAMPLE_S]
     * across the overlap, at least [MIN_CLOSE_SHARE] of the [MIN_SHARED_SAMPLES] or more moments where both have a position are within
     * [toleranceM]. A moment in either track's gap (a privacy zone, a hidden end) counts as neither near nor far.
     */
    @Suppress("ReturnCount") // Each reason they weren't together.
    fun together(a: Track, b: Track, toleranceM: Double = TOLERANCE_M): Boolean {
        val (startA, endA) = a.span() ?: return false
        val (startB, endB) = b.span() ?: return false
        val start = maxOf(startA, startB)
        val end = minOf(endA, endB)
        val overlap = end - start
        val shorter = minOf(a.elapsedS.takeIf { it > 0 } ?: (endA - startA), b.elapsedS.takeIf { it > 0 } ?: (endB - startB))
        if (overlap < MIN_OVERLAP_S || overlap < MIN_OVERLAP_SHARE * shorter) return false
        var shared = 0
        var close = 0
        var t = start
        while (t <= end) {
            val pa = a.positionAt(t)
            val pb = b.positionAt(t)
            if (pa != null && pb != null) {
                shared++
                if (TrackMath.distanceMeters(pa.first, pa.second, pb.first, pb.second) <= toleranceM) close++
            }
            t += SAMPLE_S
        }
        return shared >= MIN_SHARED_SAMPLES && close >= MIN_CLOSE_SHARE * shared
    }

    /** The first and last moment of the shown pieces, or null when there are none. */
    private fun Track.span(): Pair<Double, Double>? {
        val shown = pieces.filter { it.isNotEmpty() }
        if (shown.isEmpty()) return null
        return shown.minOf { it.first().t } to shown.maxOf { it.last().t }
    }

    /** The position at [t] interpolated within the piece covering it, or null in a gap. */
    @Suppress("ReturnCount") // At a sample, between two, or in a gap.
    private fun Track.positionAt(t: Double): Pair<Double, Double>? {
        for (piece in pieces) {
            if (piece.isEmpty() || t < piece.first().t || t > piece.last().t) continue
            val i = piece.indexOfFirst { it.t >= t }
            val after = piece[i]
            if (i == 0 || after.t == t) return after.lat to after.lng
            val before = piece[i - 1]
            val f = (t - before.t) / (after.t - before.t)
            return (before.lat + (after.lat - before.lat) * f) to (before.lng + (after.lng - before.lng) * f)
        }
        return null
    }
}
