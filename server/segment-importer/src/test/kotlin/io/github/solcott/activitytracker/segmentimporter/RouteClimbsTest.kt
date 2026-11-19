package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.ALPINE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.RIDE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.RUN
import io.github.solcott.activitytracker.location.SegmentClimb
import kotlin.test.Test
import kotlin.test.assertEquals

/** A one-way piece with no climb of its own taking the climb of the one-way routes through it (segments/trail-candidates). */
class RouteClimbsTest {
    private fun candidate(
        id: String,
        vararg ways: Long,
        climb: SegmentClimb? = null,
        group: String = RIDE,
        oneWay: Boolean = true,
    ) =
        Candidate(
            id = id,
            group = group,
            name = id,
            line = "",
            points = emptyList(),
            lengthMeters = 1000,
            osmWays = ways.toList(),
            kind = TrailKind.entries.first(),
            whole = false,
            oneWay = oneWay,
            climb = climb,
        )

    private fun climbs(vararg candidates: Candidate): Map<String, SegmentClimb?> =
        RouteClimbs.inherit(candidates.toList()).associate { it.id to it.climb }

    @Test
    fun aGentleSectionTakesTheClimbOfEveryRouteThroughIt() {
        val found =
            climbs(
                candidate("trees", 2),
                candidate("rush", 1, 2, 3, climb = SegmentClimb.Downhill),
                candidate("rushToShoreline", 2, 3, climb = SegmentClimb.Downhill),
            )
        assertEquals(SegmentClimb.Downhill, found["trees"])
    }

    @Test
    fun routesThatDisagreeGiveNoClimb() {
        val found =
            climbs(
                candidate("saddle", 2),
                candidate("east", 1, 2, climb = SegmentClimb.Downhill),
                candidate("west", 2, 3, climb = SegmentClimb.Uphill),
            )
        assertEquals(null, found["saddle"])
    }

    @Test
    fun onlyOneWayRoutesOfItsSportInItsOrderCount() {
        val found =
            climbs(
                candidate("piece", 2, 3),
                candidate("otherSport", 1, 2, 3, climb = SegmentClimb.Downhill, group = RUN),
                candidate("twoWay", 1, 2, 3, climb = SegmentClimb.Downhill, oneWay = false),
                candidate("reversed", 3, 2, 1, climb = SegmentClimb.Downhill),
            )
        assertEquals(null, found["piece"])
    }

    @Test
    fun aClimbOfItsOwnIsKeptAndAlpineTakesNone() {
        val found =
            climbs(
                candidate("own", 2, climb = SegmentClimb.Uphill),
                candidate("piste", 2, group = ALPINE),
                candidate("route", 1, 2, 3, climb = SegmentClimb.Downhill),
                candidate("pisteRoute", 1, 2, 3, climb = SegmentClimb.Downhill, group = ALPINE),
            )
        assertEquals(SegmentClimb.Uphill, found["own"])
        assertEquals(null, found["piste"])
    }

    @Test
    fun aTwoWayPieceTakesNoClimb() {
        val found = climbs(candidate("twoWay", 2, oneWay = false), candidate("route", 1, 2, 3, climb = SegmentClimb.Downhill))
        assertEquals(null, found["twoWay"])
    }
}
