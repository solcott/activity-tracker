package io.github.solcott.activitytracker.location

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** A path a segment may lie on: its [key], its line [pieces], and the sport groups it is open to ([sports]; empty for any). */
data class PathCandidate<K>(val key: K, val pieces: List<List<GeoPoint>>, val sports: Set<String> = emptySet())

/**
 * Which paths a segment lies on (segments/segment-paths): trail or road lines, in one or more pieces, that segments are listed under. The
 * AppView serves membership by this test and the segment importer publishes paths by it, so both always agree.
 *
 * A segment lies on a path when the stretches of it that follow the path add up to [SHARE] of its length or [MIN_METERS], whichever is
 * less. A stretch counts only for the paths nearest it: within [SegmentGeometry.CORRIDOR_M] and within [SHARED_M] of the nearest path, so a
 * trail running parallel a few tens of metres away loses to the one a segment is drawn on, while trails sharing tread both count. A path
 * not open to the segment's sport group neither holds it nor competes for it.
 */
object PathOverlap {
    /** A segment lies on a path when this share of its length follows the path... */
    const val SHARE = 0.25

    /** ...or this many meters of it do, whichever is shorter. */
    const val MIN_METERS = 1_000.0

    /** How much farther than the nearest path another may be and still share a stretch: tread two paths both follow. */
    const val SHARED_M = 5.0

    private const val METERS_PER_DEGREE = 111_320.0
    private const val DEGREES_PER_HALF_TURN = 180.0
    private const val MIN_COS = 0.01

    /** The keys of the [candidates] that [segment], of the sport group [sport], lies on. */
    fun <K> pathsUnder(segment: List<GeoPoint>, sport: String?, candidates: List<PathCandidate<K>>): Set<K> {
        val length = SegmentGeometry.lengthMeters(segment)
        if (segment.size < 2 || length <= 0.0) return emptySet()
        val needed = min(length * SHARE, MIN_METERS)
        return overlaps(segment, candidates.filter { sport == null || it.sports.isEmpty() || sport in it.sports })
            .filterValues { it >= needed }
            .keys
    }

    /** How many meters of [segment] follow each of [candidates], each stretch counting only for the paths nearest it. */
    fun <K> overlaps(segment: List<GeoPoint>, candidates: List<PathCandidate<K>>): Map<K, Double> {
        val near =
            if (segment.size < 2) emptyList()
            else candidates.map { it.key to edgesNear(segment, it.pieces) }.filter { it.second.isNotEmpty() }
        if (near.isEmpty()) return emptyMap()
        val points = SegmentGeometry.resample(segment)
        // For each point, which paths it counts for: those in the corridor no more than SHARED_M farther than the nearest.
        val credited = points.map { point ->
            val distances = near.map { (_, edges) -> edges.minOf { (a, b) -> SegmentGeometry.toSegmentMeters(point, a, b) } }
            val nearest = distances.min()
            distances.indices.filter { distances[it] <= SegmentGeometry.CORRIDOR_M && distances[it] <= nearest + SHARED_M }.toSet()
        }
        val meters = DoubleArray(near.size)
        for (i in 1 until points.size) {
            val step = SegmentGeometry.distanceMeters(points[i - 1], points[i])
            for (k in credited[i - 1] intersect credited[i]) meters[k] += step
        }
        return near.indices.associate { near[it].first to meters[it] }
    }

    /** How many meters of [segment] follow [pieces], with no other path competing for them. */
    fun overlapMeters(segment: List<GeoPoint>, pieces: List<List<GeoPoint>>): Double =
        overlaps(segment, listOf(PathCandidate(Unit, pieces)))[Unit] ?: 0.0

    /** Whether [segment] lies on the path of [pieces], with no other path competing for it. */
    fun liesOn(segment: List<GeoPoint>, pieces: List<List<GeoPoint>>): Boolean =
        pathsUnder(segment, null, listOf(PathCandidate(Unit, pieces))).isNotEmpty()

    /** The pieces' edges whose box comes within the corridor of [segment]'s box, so a long path costs only its nearby stretch. */
    private fun edgesNear(segment: List<GeoPoint>, pieces: List<List<GeoPoint>>): List<Pair<GeoPoint, GeoPoint>> {
        val pad = SegmentGeometry.CORRIDOR_M / METERS_PER_DEGREE
        val south = segment.minOf { it.lat } - pad
        val north = segment.maxOf { it.lat } + pad
        val lngPad = pad / max(cos(segment.first().lat * PI / DEGREES_PER_HALF_TURN), MIN_COS)
        val west = segment.minOf { it.lng } - lngPad
        val east = segment.maxOf { it.lng } + lngPad
        return pieces.flatMap { piece ->
            piece.zipWithNext().filter { (a, b) ->
                max(a.lat, b.lat) >= south && min(a.lat, b.lat) <= north && max(a.lng, b.lng) >= west && min(a.lng, b.lng) <= east
            }
        }
    }
}
