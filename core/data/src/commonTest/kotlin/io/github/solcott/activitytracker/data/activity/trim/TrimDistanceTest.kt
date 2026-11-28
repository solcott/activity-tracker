package io.github.solcott.activitytracker.data.activity.trim

import io.github.solcott.activitytracker.data.activity.trim.TrimFixtures.DRIVE_START
import io.github.solcott.activitytracker.data.activity.trim.TrimFixtures.RIDE_END
import io.github.solcott.activitytracker.data.activity.trim.TrimFixtures.STANDING_START
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrimDistanceTest {
    private val distance = TrimDistance.of(TrimFixtures.input)!!

    @Test
    fun aStartAtADistanceAlsoCutsStandingThere() {
        // Nothing is ridden in the car park, so a start at 0 m keeps from the moment the athlete set off.
        assertEquals(STANDING_START, distance.startAt(0.0))
        assertEquals(STANDING_START + 100, distance.startAt(800.0))
    }

    @Test
    fun anEndAtADistanceAlsoCutsStandingThere() {
        // The ride ends 28.8 km in; the 9 min at the car add nothing, so they go with the drive.
        assertEquals(RIDE_END, distance.endAt(3600 * TrimFixtures.RIDE_MPS))
        assertEquals(DRIVE_START + 120, distance.endAt(3600 * TrimFixtures.RIDE_MPS + 120 * TrimFixtures.DRIVE_MPS))
    }

    @Test
    fun distanceCountsFromTheFirstSample() {
        // An activity trimmed before distance was re-based starts its distance where the old start was cut.
        val offset = 480_000
        val shifted = TrimFixtures.streams.copy(distance = TrimFixtures.streams.distance!!.map { it?.plus(offset) })
        val moved = TrimDistance.of(TrimInput(TrimFixtures.start, TrimFixtures.END, shifted, emptyList()))!!
        assertEquals(0.0, moved.at(0))
        assertEquals(distance.total, moved.total)
        assertEquals(STANDING_START + 100, moved.startAt(800.0))
    }

    @Test
    fun noDistanceWithoutStreamsOrRoute() {
        assertNull(TrimDistance.of(TrimInput(TrimFixtures.start, 600, null, emptyList())))
    }
}
