package io.github.solcott.activitytracker.activityfile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HrvTest {
    @Test
    fun rmssdOfAKnownSeries() {
        // Successive differences alternate +30 and -10 ms: sqrt((900 + 100) / 2) = 22.4.
        val rr = List(41) { listOf(0.80, 0.83, 0.82)[it % 3] }.let { series -> series.take(Hrv.MIN_INTERVALS + 1) }
        val diffs = rr.zipWithNext { a, b -> (b - a) * 1000 }
        val expected = kotlin.math.sqrt(diffs.map { it * it }.average())
        assertEquals(kotlin.math.round(expected).toInt(), Hrv.rmssd(rr))
    }

    @Test
    fun artifactsAreDropped() {
        val steady = List(40) { if (it % 2 == 0) 0.49 else 0.51 }
        val withDropout = steady.toMutableList().apply { add(20, 4.0) }
        val withDoubledBeat = steady.toMutableList().apply { add(20, 0.25) }
        val withJump = steady.toMutableList().apply { add(20, 0.70) }

        assertEquals(20, Hrv.rmssd(steady))
        assertEquals(20, Hrv.rmssd(withDropout), "out of range")
        assertEquals(20, Hrv.rmssd(withDoubledBeat), "out of range")
        assertEquals(20, Hrv.rmssd(withJump), "more than 20% from the last kept")
    }

    @Test
    fun tooFewIntervalsGiveNone() {
        assertNull(Hrv.rmssd(List(Hrv.MIN_INTERVALS - 1) { 0.5 }))
        assertNull(Hrv.rmssd(List(40) { 3.0 }), "none plausible")
        assertEquals(0, Hrv.rmssd(List(Hrv.MIN_INTERVALS) { 0.5 }))
    }
}
