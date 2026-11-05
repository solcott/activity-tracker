package io.github.solcott.activitytracker.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ElevationGainTest {
    @Test
    fun noisyFlatHasNoGainOrLoss() {
        val heights = List(200) { 100.0 + if (it % 2 == 0) 1.0 else -1.0 }
        assertEquals(GainLoss(0, 0), ElevationGain.of(heights))
    }

    @Test
    fun steadyClimbCountsItAll() {
        val heights = List(121) { it.toDouble() }
        assertEquals(GainLoss(120, 0), ElevationGain.of(heights))
    }

    @Test
    fun climbThenDescent() {
        val heights = (0..50).map { it.toDouble() } + (49 downTo 20).map { it.toDouble() }
        assertEquals(GainLoss(50, 30), ElevationGain.of(heights))
    }

    @Test
    fun skipsMissingSamples() {
        assertEquals(GainLoss(10, 0), ElevationGain.of(listOf(0.0, null, 5.0, null, 10.0)))
    }

    @Test
    fun needsTwoKnownHeights() {
        assertNull(ElevationGain.of(listOf(null, 4.0, null)))
        assertNull(ElevationGain.of(emptyList()))
    }
}
