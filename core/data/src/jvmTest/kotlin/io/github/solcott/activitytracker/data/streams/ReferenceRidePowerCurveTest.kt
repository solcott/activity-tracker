package io.github.solcott.activitytracker.data.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A real ride with 30 minutes of timer stops, whose best 20 minutes Garmin Connect (and the Edge's own summary) gives as 179 W. */
class ReferenceRidePowerCurveTest {
    private val ride: Pair<List<Int>, List<Int?>> by lazy {
        val lines =
            assertNotNull(javaClass.getResourceAsStream("/streams/edge840-mtb-power.csv")).bufferedReader().readLines().filterNot {
                it.startsWith("#")
            }
        lines.map { it.substringBefore(',').toInt() } to lines.map { it.substringAfter(',').toIntOrNull() }
    }

    private fun curve(seconds: List<Int>) = StreamAnalytics.meanMax(seconds).toMap()

    @Test
    fun theBest20MinutesMatchGarmin() {
        val (time, power) = ride
        val curve = curve(StreamAnalytics.clockSeconds(time, power))
        assertEquals(179, curve[1200])
        assertEquals(219, curve[300])
        assertEquals(708, curve[1], "the ride's maximum power")
        assertEquals(7200, curve.keys.max(), "Garmin's curve reaches 2 h on this ride's 2:08 of clock time")
    }

    @Test
    fun countingStopsOnceWouldJoinEffortsAcrossThem() {
        val (time, power) = ride
        val stitched = curve(StreamAnalytics.perSecond(time, power))
        assertTrue(stitched.getValue(1200) > 190, "the old reading joined climbs from either side of a stop")
    }
}
