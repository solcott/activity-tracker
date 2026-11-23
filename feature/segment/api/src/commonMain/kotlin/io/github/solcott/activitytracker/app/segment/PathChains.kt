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
            val chain = ArrayDeque(listOf(Turned(seed, reversed = false)))
            fun oriented(piece: Turned) = lines[piece.index].let { if (piece.reversed) it.asReversed() else it }
            // The tail first: a piece whose start meets it runs on as drawn, one whose end does is turned round.
            while (true) {
                val tail = oriented(chain.last()).last()
                val end = nearestEnd(tail, left, lines) ?: break
                left -= end.index
                chain.addLast(Turned(end.index, reversed = end.atEnd))
            }
            // Then the head: a piece whose end meets it comes before as drawn, one whose start does is turned round.
            while (true) {
                val head = oriented(chain.first()).first()
                val end = nearestEnd(head, left, lines) ?: break
                left -= end.index
                chain.addFirst(Turned(end.index, reversed = !end.atEnd))
            }
            chain.forEachIndexed { i, piece -> order += Placed(piece.index, piece.reversed, joined = i > 0) }
            chained += chain.map { lines[it.index] }
            left.removeAll { index -> lines[index].let { listOf(it.first(), it.last()) }.any { end -> touches(end, chained) } }
        }
        return order
    }

    /** The piece among [candidates] with an end nearest [point], within [JOIN_M], and whether that end is its last point. */
    private fun nearestEnd(point: GeoPoint, candidates: Set<Int>, lines: List<List<GeoPoint>>): PieceEnd? =
        candidates
            .flatMap { index ->
                listOf(
                    PieceEnd(index, atEnd = false, SegmentGeometry.distanceMeters(point, lines[index].first())),
                    PieceEnd(index, atEnd = true, SegmentGeometry.distanceMeters(point, lines[index].last())),
                )
            }
            .filter { it.meters <= JOIN_M }
            .minByOrNull { it.meters }

    /** The piece at [index] in a chain, turned round when [reversed]. */
    private data class Turned(val index: Int, val reversed: Boolean)

    /** One end of the piece at [index]: its last point when [atEnd], else its first, [meters] from the point asked about. */
    private data class PieceEnd(val index: Int, val atEnd: Boolean, val meters: Double)

    private fun touches(point: GeoPoint, lines: List<List<GeoPoint>>): Boolean = lines.any {
        SegmentGeometry.distanceToLineMeters(point, it) <= JOIN_M
    }
}
