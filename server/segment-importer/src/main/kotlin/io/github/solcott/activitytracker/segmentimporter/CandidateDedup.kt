package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.location.SegmentCells
import io.github.solcott.activitytracker.location.SegmentGeometry

/**
 * Drops candidates that duplicate another of their sport group by the AppView's test ([SegmentGeometry.duplicates]), keeping an agency's
 * over OSM's (the land manager's statement of allowed use, in the public domain), a route relation's over a named way's, a whole trail over
 * a stretch, then the lower id. Only candidates whose ends share cells are compared, since a duplicate's ends lie within the test's
 * distance of each other's.
 */
internal object CandidateDedup {
    fun dedup(candidates: List<Candidate>): List<Candidate> =
        candidates
            .groupBy { it.group }
            .values
            .flatMap { group ->
                val kept = mutableListOf<Candidate>()
                val byEnds = mutableMapOf<Pair<Long, Long>, MutableList<Candidate>>()
                for (candidate in
                    group.sortedWith(
                        compareBy({ it.source != SegmentSource.AGENCY }, { it.kind != TrailKind.RELATION }, { !it.whole }, { it.id })
                    )) {
                    val start = candidate.points.first()
                    val end = candidate.points.last()
                    val near =
                        SegmentCells.cellsOf(start).flatMap { s -> SegmentCells.cellsOf(end).flatMap { e -> byEnds[s to e].orEmpty() } }
                    if (near.any { SegmentGeometry.duplicates(candidate.points, it.points) }) continue
                    kept += candidate
                    byEnds.getOrPut(SegmentCells.cellOf(start.lat, start.lng) to SegmentCells.cellOf(end.lat, end.lng)) {
                        mutableListOf()
                    } += candidate
                }
                kept
            }
}
