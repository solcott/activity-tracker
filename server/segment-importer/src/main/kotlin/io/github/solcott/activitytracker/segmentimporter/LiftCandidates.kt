package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.location.SegmentGeometry
import kotlin.math.roundToLong

/**
 * Ski lifts as candidates (segments/trail-candidates): each named lift way ([TrailTags.isLift]) gives one `alpine` candidate of kind lift,
 * its whole line in its drawn direction, bottom to top by OSM's convention. Lifts are never joined to other ways, cut at junctions, given a
 * reverse candidate or shortened by generic words.
 */
internal object LiftCandidates {
    /** The key their ids hash in place of a sport group, so a lift's id never meets a run's along the same way. */
    private const val ID_GROUP = "alpine-lift"

    fun of(data: OsmData): List<Candidate> =
        data.ways.filter { it.rules === TrailTags && TrailTags.isLift(it.tags) }.mapNotNull { way -> candidate(data, way) }

    /** [way]'s candidate: none when a node has no coordinate or its line is outside the alpine group's length bounds. */
    private fun candidate(data: OsmData, way: OsmWay): Candidate? {
        val points = way.nodes.asList().mapNotNull { data.coords[it] }.takeIf { it.size == way.nodes.size && it.size >= 2 } ?: return null
        val line = CandidateCutter.simplified(points)
        val length = SegmentGeometry.lengthMeters(line)
        val fits = length >= SegmentSportGroup.minMeters(SegmentSportGroup.ALPINE) && length <= SegmentSportGroup.MAX_METERS
        return if (!fits) null
        else
            Candidate(
                id = CandidateCutter.idOf(ID_GROUP, listOf(way.id), alongFirstWay = true, way.nodes.first(), way.nodes.last()),
                group = SegmentSportGroup.ALPINE,
                name = CandidateCutter.truncate(checkNotNull(way.name)),
                line = SegmentGeometry.encodePolyline(line),
                points = line,
                lengthMeters = length.roundToLong(),
                osmWays = listOf(way.id),
                kind = TrailKind.NAMED,
                whole = true,
                segmentKind = SegmentKind.LIFT,
            )
    }
}
