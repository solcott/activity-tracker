package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.location.SegmentClimb
import java.util.Collections

/**
 * The last step of a candidate's climb (segments/trail-candidates): a one-way OSM candidate that neither clearly climbs nor takes a climb
 * from its trail takes the climb of the longer one-way candidates of its sport group that follow all its ways in its order, when they all
 * agree. So a section of a downhill-only trail system that drops too gently on its own still reads "Downhill only", as every route through
 * it does, while a section the routes through it disagree on, or that no route with a climb passes, keeps its compass name.
 */
internal object RouteClimbs {
    fun inherit(candidates: List<Candidate>): List<Candidate> {
        val withClimb = candidates.filter { it.oneWay && it.climb != null && it.osmWays.isNotEmpty() }
        val byWay = HashMap<Long, MutableList<Candidate>>()
        for (route in withClimb) for (way in route.osmWays.toSet()) byWay.getOrPut(way) { mutableListOf() } += route
        return candidates.map { candidate ->
            if (!needsClimb(candidate)) return@map candidate
            val climbs =
                byWay[candidate.osmWays.first()]
                    .orEmpty()
                    .filter { it !== candidate && it.group == candidate.group && it.osmWays.size > candidate.osmWays.size }
                    .filter { it.osmWays.follows(candidate.osmWays) }
                    .mapNotNull { it.climb }
                    .toSet()
            climbs.singleOrNull()?.let(candidate::withClimb) ?: candidate
        }
    }

    private fun needsClimb(candidate: Candidate): Boolean =
        candidate.oneWay && candidate.climb == null && candidate.osmWays.isNotEmpty() && candidate.group != SegmentSportGroup.ALPINE

    /** Whether [ways] appear in this list in a row, in the same order. */
    private fun List<Long>.follows(ways: List<Long>): Boolean = Collections.indexOfSubList(this, ways) >= 0
}

/** This candidate with [climb] in place of its own. */
internal fun Candidate.withClimb(climb: SegmentClimb): Candidate =
    Candidate(
        id = id,
        group = group,
        name = name,
        line = line,
        points = points,
        lengthMeters = lengthMeters,
        osmWays = osmWays,
        kind = kind,
        whole = whole,
        source = source,
        license = license,
        attribution = attribution,
        agencyFeatures = agencyFeatures,
        segmentKind = segmentKind,
        oneWay = oneWay,
        climb = climb,
    )
