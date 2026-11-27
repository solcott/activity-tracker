package io.github.solcott.activitytracker.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class MovingTimeTest {
    private val start = Instant.fromEpochSeconds(1_700_000_000)

    private fun at(second: Int, northMeters: Double) =
        LocationSample(45.0 + northMeters / METERS_PER_DEGREE, 7.0, null, null, null, start + second.seconds)

    @Test
    fun thresholdIsHalfAMeterPerSecond() {
        assertTrue(MovingTime.isMoving(0.5))
        assertFalse(MovingTime.isMoving(0.49))
    }

    @Test
    fun countsOnlyMovingSteps() {
        // 10 s at 2 m/s, 10 s standing, 10 s at 2 m/s.
        val samples = listOf(at(0, 0.0), at(10, 20.0), at(20, 20.0), at(30, 40.0))
        assertEquals(20.0, MovingTime.seconds(samples)!!, 0.01)
    }

    @Test
    fun needsTwoSamples() {
        assertNull(MovingTime.seconds(listOf(at(0, 0.0))))
    }

    private companion object {
        const val METERS_PER_DEGREE = 111_195.0
    }
}
