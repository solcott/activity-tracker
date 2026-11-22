package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry

/**
 * The order a trail's line pieces run in along its profile (segments/path-profile). A path's pieces are stored in no particular order or
 * direction, so they're chained end to end: the longest keeps its direction, and each piece whose end lies within [JOIN_M] of the chain's
 * last or first point runs on from it, turned round when its other end is the one that meets. A piece that meets a chained piece anywhere
 * else is a branch and is left out. What's left is another part of the trail, chained the same way after a break.
 */
internal object PathChains {
    /** How near two pieces' ends must be for one to run on from the other, or a piece's end to another's line for it to branch off. */
    const val JOIN_M = 30.0

    /**
     * The piece at [index] in the given lines, turned round when [reversed], and whether it runs on from the piece before it ([joined]).
     */
    data class Placed(val index: Int, val reversed: Boolean, val joined: Boolean)

    /** [lines] (a trail's pieces, each of two or more points) in the order they run, each once; branches left out. */
    fun order(lines: List<List<GeoPoint>>): List<Placed> {
        val left = lines.indices.filter { lines[it].size >= 2 }.toMutableSet()
        val chained = mutableListOf<List<GeoPoint>>()
        val order = mutableListOf<Placed>()
        while (left.isNotEmpty()) {
            val seed = left.maxBy { SegmentGeometry.lengthMeters(lines[it]) }
            left -= seed
            val chain = ArrayDeque(listOf(seed to false))
            fun oriented(placed: Pair<Int, Boolean>) = lines[placed.first].let { if (placed.second) it.asReversed() else it }
            // The tail first: a piece whose start meets it runs on as drawn, one whose end does is turned round.
            while (true) {
                val tail = oriented(chain.last()).last()
                val (index, atEnd) = nearestEnd(tail, left, lines) ?: break
                left -= index
                chain.addLast(index to atEnd)
            }
            // Then the head: a piece whose end meets it comes before as drawn, one whose start does is turned round.
            while (true) {
                val head = oriented(chain.first()).first()
                val (index, atEnd) = nearestEnd(head, left, lines) ?: break
                left -= index
                chain.addFirst(index to !atEnd)
            }
            chain.forEachIndexed { i, (index, reversed) -> order += Placed(index, reversed, joined = i > 0) }
            chained += chain.map { lines[it.first] }
            left.removeAll { index -> lines[index].let { listOf(it.first(), it.last()) }.any { end -> touches(end, chained) } }
        }
        return order
    }

    /** The piece among [candidates] with an end nearest [point], within [JOIN_M], and whether that end is its last point. */
    private fun nearestEnd(point: GeoPoint, candidates: Set<Int>, lines: List<List<GeoPoint>>): Pair<Int, Boolean>? =
        candidates
            .flatMap { index ->
                listOf(
                    Triple(index, false, SegmentGeometry.distanceMeters(point, lines[index].first())),
                    Triple(index, true, SegmentGeometry.distanceMeters(point, lines[index].last())),
                )
            }
            .filter { it.third <= JOIN_M }
            .minByOrNull { it.third }
            ?.let { (index, atEnd) -> index to atEnd }

    private fun touches(point: GeoPoint, lines: List<List<GeoPoint>>): Boolean = lines.any {
        SegmentGeometry.distanceToLineMeters(point, it) <= JOIN_M
    }
}
