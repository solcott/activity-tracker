package io.github.solcott.activitytracker.data.segments

import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.SegmentGeometry

/**
 * An activity's route as a segment can be cut from it (segments/segment-creation): the [pieces] its privacy settings leave shown
 * (`PrivacySettings.pieces`, as its segment track holds them), laid end to end by distance. A gap between two pieces takes no distance, so
 * a selection is two distances along this line, and one spanning a gap crosses a privacy zone, the hidden ends or a break in the recording.
 */
class SegmentRoute(pieces: List<List<LocationSample>>) {
    private val pieces: List<List<GeoPoint>> = pieces.filter { it.size >= 2 }.map { p -> p.map { GeoPoint(it.latitude, it.longitude) } }

    /** Each piece's distance from the route's start to each of its points, in meters. */
    private val along: List<List<Double>> = buildList {
        var d = 0.0
        for (piece in this@SegmentRoute.pieces) {
            val distances = mutableListOf(d)
            for ((a, b) in piece.zipWithNext()) {
                d += SegmentGeometry.distanceMeters(a, b)
                distances += d
            }
            add(distances)
        }
    }

    /** The whole route's length, gaps left out, in meters. */
    val lengthMeters: Double = along.lastOrNull()?.last() ?: 0.0

    /** Where each gap between pieces falls, in meters along the route. */
    val gaps: List<Double> = along.dropLast(1).map { it.last() }

    /** Whether any of the route is left to cut a segment from. */
    val isEmpty: Boolean
        get() = pieces.isEmpty()

    /** The whole route, each piece as its own line, for drawing it. */
    val lines: List<List<GeoPoint>>
        get() = pieces

    /** What cutting a segment of [sport] from [start] to [end] meters along the route gives. */
    fun select(start: Double, end: Double, sport: String): SegmentSelection {
        val from = minOf(start, end).coerceIn(0.0, lengthMeters)
        val to = maxOf(start, end).coerceIn(0.0, lengthMeters)
        // The piece holding both ends, give or take a slider's rounding; a selection starting right at a gap belongs to the piece after it.
        val index = along.indexOfFirst { from >= it.first() - END_TOLERANCE_M && to <= it.last() + END_TOLERANCE_M }
        if (index < 0) return SegmentSelection.CrossesGap
        val piece = along[index]
        val meters = to.coerceAtMost(piece.last()) - from.coerceAtLeast(piece.first())
        val min = SegmentSportGroup.minMeters(sport)
        return when {
            meters < min -> SegmentSelection.TooShort(min)
            meters > SegmentSportGroup.MAX_METERS -> SegmentSelection.TooLong(SegmentSportGroup.MAX_METERS)
            else -> SegmentSelection.Ok(cut(pieces[index], piece, from.coerceAtLeast(piece.first()), to.coerceAtMost(piece.last())), meters)
        }
    }

    private companion object {
        /** How far past a piece's end a selection may reach and still be in it, for the rounding of a slider's float values. */
        const val END_TOLERANCE_M = 0.5
    }

    /** [piece] from [from] to [to] meters along the route, its ends interpolated. */
    private fun cut(piece: List<GeoPoint>, distances: List<Double>, from: Double, to: Double): List<GeoPoint> = buildList {
        add(pointAt(piece, distances, from))
        for (i in piece.indices) if (distances[i] > from && distances[i] < to) add(piece[i])
        add(pointAt(piece, distances, to))
    }

    private fun pointAt(piece: List<GeoPoint>, distances: List<Double>, d: Double): GeoPoint {
        val i = distances.indexOfFirst { it >= d }.coerceAtLeast(1)
        val (a, b) = piece[i - 1] to piece[i]
        val span = distances[i] - distances[i - 1]
        val f = if (span <= 0.0) 0.0 else ((d - distances[i - 1]) / span).coerceIn(0.0, 1.0)
        return GeoPoint(a.lat + (b.lat - a.lat) * f, a.lng + (b.lng - a.lng) * f)
    }
}

/** A stretch chosen along a [SegmentRoute], and whether it can be a segment. */
sealed interface SegmentSelection {
    /** A segment can be cut: its [line] from start to end, [meters] long. */
    data class Ok(val line: List<GeoPoint>, val meters: Double) : SegmentSelection {
        /** [line] as `feed.segment#line` stores it. */
        val encoded: String
            get() = SegmentGeometry.encodePolyline(line)
    }

    /** Shorter than [minMeters], its sport group's minimum. */
    data class TooShort(val minMeters: Int) : SegmentSelection

    /** Longer than [maxMeters]. */
    data class TooLong(val maxMeters: Int) : SegmentSelection

    /** Spans a gap: a privacy zone, the hidden ends or a break in the recording. */
    data object CrossesGap : SegmentSelection
}
