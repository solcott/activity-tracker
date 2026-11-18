package io.github.solcott.activitytracker.data.units

import kotlin.test.Test
import kotlin.test.assertEquals

class ClockTest {
    @Test
    fun minutesAndSecondsUnderAnHourThenHours() {
        assertEquals("0:00", formatClock(0))
        assertEquals("5:07", formatClock(307))
        assertEquals("59:59", formatClock(3_599))
        assertEquals("1:00:00", formatClock(3_600))
        assertEquals("12:03:09", formatClock(43_389))
    }
}
