package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.segments.GroupMatch
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.location.TimedPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** feed/group-activities "Together in time and place", on the pure test. */
class GroupMatchTest {
    /** A track heading north from [lat0] at about 5 m/s, a point every 10 s for [seconds], starting at epoch second [start]. */
    private fun ride(start: Double, seconds: Int, lat0: Double = 44.0, lng: Double = -72.0): List<TimedPoint> =
        (0..seconds step 10).map { s -> TimedPoint(lat0 + s * 5 / 111_000.0, lng, start + s) }

    private fun track(vararg pieces: List<TimedPoint>, elapsed: Double? = null) =
        GroupMatch.Track(pieces.toList(), elapsed ?: (pieces.last().last().t - pieces.first().first().t))

    @Test
    fun sideBySidePairs() {
        val a = track(ride(0.0, 3600))
        val b = track(ride(6.0, 3600, lng = -72.0003)) // ~24 m east, 30 m behind
        assertTrue(GroupMatch.together(a, b))
        assertTrue(GroupMatch.together(b, a))
    }

    @Test
    fun theSameLoopHoursApartDoesnt() {
        assertFalse(GroupMatch.together(track(ride(0.0, 3600)), track(ride(7200.0, 3600))))
    }

    @Test
    fun routesKilometresApartDont() {
        assertFalse(GroupMatch.together(track(ride(0.0, 3600)), track(ride(0.0, 3600, lng = -72.05))))
    }

    @Test
    fun splittingUpEarlyDoesnt() {
        val a = track(ride(0.0, 7200))
        // Together for 10 minutes, then off on a route 4 km away.
        val b = track(ride(0.0, 600), ride(610.0, 6590, lat0 = 44.0 + 3000 / 111_000.0, lng = -71.95))
        assertFalse(GroupMatch.together(a, b))
    }

    @Test
    fun aShortOverlapDoesnt() {
        assertFalse(GroupMatch.together(track(ride(0.0, 240)), track(ride(0.0, 240))))
    }

    @Test
    fun aSharedStartInsideAPrivacyZoneStillPairsOnTheShownParts() {
        val full = ride(0.0, 3600)
        // Alice's zone hides her first 10 minutes; Bob's track is whole.
        val a = track(full.filter { it.t >= 600 }, elapsed = 3600.0)
        val b = track(ride(0.0, 3600, lng = -72.0002))
        assertTrue(GroupMatch.together(a, b))
    }

    @Test
    fun gapsCountAsNeitherNearNorFar() {
        val full = ride(0.0, 3600)
        // Bob's track has a 20-minute hole mid-ride; everywhere both are shown they're together.
        val b = track(full.filter { it.t < 1200 }, full.filter { it.t > 2400 }, elapsed = 3600.0)
        assertTrue(GroupMatch.together(track(full), b))
    }

    @Test
    fun paddlersGetMoreRoom() {
        val a = track(ride(0.0, 3600))
        val b = track(ride(0.0, 3600, lng = -72.0016)) // ~128 m apart
        assertFalse(GroupMatch.together(a, b, GroupMatch.toleranceM(SegmentSportGroup.RIDE)))
        assertTrue(GroupMatch.together(a, b, GroupMatch.toleranceM(SegmentSportGroup.PADDLE)))
        assertEquals(GroupMatch.TOLERANCE_M, GroupMatch.toleranceM(SegmentSportGroup.RUN))
    }
}
