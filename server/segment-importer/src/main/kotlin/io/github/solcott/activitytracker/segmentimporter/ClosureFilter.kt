package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentCells
import io.github.solcott.activitytracker.location.SegmentGeometry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Leaves out OSM candidates that follow an agency line closed to their group (segments/trail-candidates): those whose samples, every
 * [SAMPLE_M] along the line, lie within [NEAR_M] of a line closed to the group and not within [NEAR_M] of one open to it for at least
 * [CLOSED_M] in all. Agency candidates are always kept. Each line's stretches are indexed by the [SegmentCells] cells within [NEAR_M] of
 * them, so a sample is compared only with the stretches in its own cell.
 */
internal class ClosureFilter(lines: List<AgencyLine>) {
    private class Stretch(val line: AgencyLine, val a: GeoPoint, val b: GeoPoint)

    private val cells: Map<Long, List<Stretch>> =
        buildMap<Long, MutableList<Stretch>> {
            for (line in lines) {
                for ((a, b) in line.points.zipWithNext()) {
                    val stretch = Stretch(line, a, b)
                    // The cells are found from points every REFERENCE_SPACING_M along the stretch, so widen the margin by half that.
                    for (cell in SegmentCells.cellsOf(listOf(a, b), NEAR_M + SegmentGeometry.REFERENCE_SPACING_M / 2)) {
                        getOrPut(cell) { mutableListOf() } += stretch
                    }
                }
            }
        }

    /** How many OSM candidates of each group [keep] has left out. */
    val leftOut: Map<String, Int>
        field = sortedMapOf<String, Int>()

    fun keep(candidate: Candidate): Boolean {
        if (candidate.osmWays.isEmpty() || cells.isEmpty()) return true
        val closed = closedAlong(candidate.points, candidate.group)
        if (closed) leftOut.merge(candidate.group, 1, Int::plus)
        return !closed
    }

    /** Whether [points] lie on ground closed to [group] for at least [CLOSED_M], stopping as soon as they do. */
    private fun closedAlong(points: List<GeoPoint>, group: String): Boolean {
        var closedM = 0.0
        for ((previous, sample) in SegmentGeometry.resample(points, SAMPLE_M).zipWithNext()) {
            if (closedAt(sample, group)) closedM += SegmentGeometry.distanceMeters(previous, sample)
            if (closedM >= CLOSED_M) return true
        }
        return false
    }

    /** Whether [point] lies within [NEAR_M] of a line closed to [group] and of none open to it. */
    private fun closedAt(point: GeoPoint, group: String): Boolean {
        val near =
            cells[SegmentCells.cellOf(point.lat, point.lng)].orEmpty().filter { stretch ->
                (group in stretch.line.open || group in stretch.line.closed) && metersTo(point, stretch.a, stretch.b) <= NEAR_M
            }
        return near.any { group in it.line.closed } && near.none { group in it.line.open }
    }

    companion object {
        /** How far apart samples of a candidate's line are, in meters. */
        const val SAMPLE_M = 5.0

        /** How near an agency line a sample must be to lie on it: OSM and agency digitising of one trail differ by a few metres. */
        const val NEAR_M = 15.0

        /** How much of a candidate must lie on closed ground to leave it out, more than a crossing or a junction's approach. */
        const val CLOSED_M = 100.0

        private const val EARTH_RADIUS_M = 6_371_000.0
        private const val DEGREES_PER_HALF_TURN = 180.0

        /** Distance from [p] to the stretch a→b, in meters east and north of [p] (equirectangular, fine at this scale). */
        private fun metersTo(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
            val scale = PI / DEGREES_PER_HALF_TURN * EARTH_RADIUS_M
            val east = cos(p.lat * PI / DEGREES_PER_HALF_TURN) * scale
            val ax = (a.lng - p.lng) * east
            val ay = (a.lat - p.lat) * scale
            val dx = (b.lng - a.lng) * east
            val dy = (b.lat - a.lat) * scale
            val length2 = dx * dx + dy * dy
            val f = if (length2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / length2).coerceIn(0.0, 1.0)
            val x = ax + dx * f
            val y = ay + dy * f
            return sqrt(x * x + y * y)
        }
    }
}
