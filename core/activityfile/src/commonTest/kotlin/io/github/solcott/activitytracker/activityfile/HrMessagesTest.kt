package io.github.solcott.activitytracker.activityfile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class HrMessagesTest {
    private val startMillis = 1_790_000_000_000L
    private val start = startMillis / 1000.0

    private fun record(seconds: Int, heartRate: Int? = null) =
        DecodedSample(startMillis + seconds * 1000L, null, null, null, heartRate, null, null, null, null, null)

    private fun anchor(event: Double, bpm: Int) = DecodedHrMessage(startMillis, eventTimestamps = listOf(event), filteredBpm = listOf(bpm))

    private fun deltas(vararg readings: Pair<Double, Int>) =
        DecodedHrMessage(null, eventTimestamps = readings.map { it.first }, filteredBpm = readings.map { it.second })

    @Test
    fun anchorsEventTimestampsToTheMessagesTime() {
        val readings = HrMessages.expand(listOf(anchor(1000.0, 120), deltas(1000.25 to 121, 1000.5 to 122)))

        assertEquals(listOf(start, start + 0.25, start + 0.5), readings.map { it.seconds })
        assertEquals(listOf(120, 121, 122), readings.map { it.bpm })
    }

    @Test
    fun unwrapsTheEventTimestampsRollover() {
        val nearEnd = FitHeart.EVENT_TIMESTAMP_ROLLOVER - 0.25
        val readings = HrMessages.expand(listOf(anchor(nearEnd, 120), deltas(0.0 to 121)))

        assertEquals(listOf(start, start + 0.25), readings.map { it.seconds })
    }

    @Test
    fun dropsReadingsBeforeTheFirstAnchorOrRunningBackwards() {
        val readings = HrMessages.expand(listOf(deltas(5.0 to 99), anchor(1000.0, 120), deltas(999.0 to 98, 1000.25 to 121)))

        assertEquals(listOf(120, 121), readings.map { it.bpm })
    }

    @Test
    fun fillsAGapWithTheEarlierReadingForUpToFiveSeconds() {
        val short = HrMessages.expand(listOf(anchor(1000.0, 120), deltas(1001.0 to 130)))
        assertEquals(listOf(120, 120, 120, 120, 130), short.map { it.bpm }, "three fills a quarter second apart, then the reading")
        assertEquals(start + 0.75, short[3].seconds)

        val long = HrMessages.expand(listOf(anchor(1000.0, 120), deltas(1060.0 to 130)))
        assertEquals(22, long.size, "the anchor, 20 fills (5 s) and the reading")
        assertEquals(start + 5.0, long[20].seconds)
    }

    @Test
    fun eachRecordWithoutHeartRateAveragesTheReadingsInItsInterval() {
        val messages = listOf(anchor(1000.0, 100), deltas(1004.0 to 110, 1008.0 to 120, 1010.0 to 130))
        val merged = HrMessages.merge(messages, listOf(record(0), record(5), record(10)))

        assertEquals(100, merged[0].heartRate, "the first record takes the second before it")
        // (0, 5]: fifteen fills of 100 to 3.75 s, 110 at 4 s and four fills of it: 2050 / 20.
        assertEquals(103, merged[1].heartRate)
        // (5, 10]: eleven fills of 110, 120 at 8 s and seven fills of it, 130 at 10 s: 2300 / 20.
        assertEquals(115, merged[2].heartRate)
    }

    @Test
    fun aRecordKeepsItsOwnHeartRate() {
        val merged = HrMessages.merge(listOf(anchor(1000.0, 100), deltas(1005.0 to 180)), listOf(record(0), record(5, heartRate = 150)))

        assertEquals(100, merged[0].heartRate)
        assertEquals(150, merged[1].heartRate)
    }

    @Test
    fun recordsWithEveryHeartRateAreReturnedAsTheyAre() {
        val records = listOf(record(0, 140), record(5, 141))
        assertSame(records, HrMessages.merge(listOf(anchor(1000.0, 100)), records))
    }

    @Test
    fun aRecordWithNoReadingsInItsIntervalStaysWithout() {
        val merged = HrMessages.merge(listOf(anchor(1000.0, 100)), listOf(record(0), record(60)))

        assertEquals(100, merged[0].heartRate)
        assertNull(merged[1].heartRate)
    }
}
