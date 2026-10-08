package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.GearChange
import io.github.solcott.activitytracker.lexicon.app.StreamChannel
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class StreamBuilderTest {
    private val start = Instant.parse("2026-09-20T07:00:00Z")

    @Suppress("LongParameterList")
    private fun sample(
        sec: Long,
        lat: Double? = null,
        hr: Int? = null,
        power: Int? = null,
        cadence: Int? = null,
        speed: Double? = null,
        distance: Double? = null,
        alt: Double? = null,
    ) = DecodedSample((start + sec.seconds).toEpochMilliseconds(), lat, lat?.let { -122.3 }, alt, hr, cadence, power, speed, distance, null)

    @Test
    fun keepsIndoorChannelsInLexiconUnits() {
        val records =
            listOf(sample(0, hr = 120, power = 200, cadence = 90, speed = 8.5, distance = 0.0), sample(2, hr = 125, distance = 17.0))
        val data = assertNotNull(StreamBuilder.fromRecords(records, start.toEpochMilliseconds(), emptyList()))
        assertEquals(listOf(0, 2), data.time)
        assertEquals(listOf(120, 125), data.heartRate)
        assertEquals(listOf(200, null), data.power)
        assertEquals(listOf(8500, null), data.speed)
        assertEquals(listOf(0, 1700), data.distance)
        assertNull(data.altitude)
        assertEquals(
            listOf(StreamChannel.DISTANCE, StreamChannel.SPEED, StreamChannel.HEART_RATE, StreamChannel.POWER, StreamChannel.CADENCE),
            data.channels,
        )
    }

    @Test
    fun derivesDistanceAndSpeedFromPositionsWhenTheFileLacksThem() {
        // 1e-4 degrees of latitude is about 11.1 m.
        val records = (0L..4L).map { sample(it, lat = 47.6 + it * 1e-4, alt = 100.0) }
        val data = assertNotNull(StreamBuilder.fromRecords(records, start.toEpochMilliseconds(), emptyList()))
        assertEquals(4448, data.distance!!.last()!!, 5)
        assertEquals(11119, data.speed!![2]!!, 20)
        assertEquals(10000, data.altitude!!.first())
    }

    @Test
    fun keepsGearChangesRelativeToTheStart() {
        val changes = listOf(DecodedGearChange((start + 30.seconds).toEpochMilliseconds(), rear = true, 2, null, 5, null))
        val data = assertNotNull(StreamBuilder.fromRecords(listOf(sample(0, hr = 100)), start.toEpochMilliseconds(), changes))
        assertEquals(listOf(GearChange(30, 2, 5)), data.gears)
    }

    @Test
    fun nullWithNothingToChart() {
        assertNull(StreamBuilder.fromRecords(listOf(sample(0)), start.toEpochMilliseconds(), emptyList()))
    }

    @Test
    fun recordingKeepsDistanceAltitudeAndSpeed() {
        val samples = (0L..2L).map { LocationSample(47.6 + it * 1e-4, -122.3, 50.0, 11.0, 5.0, start + it.seconds) }
        val data = assertNotNull(StreamBuilder.fromLocations(samples, start))
        assertEquals(listOf(0, 1, 2), data.time)
        assertEquals(listOf(5000, 5000, 5000), data.altitude)
        assertEquals(listOf(11000, 11000, 11000), data.speed)
        assertEquals(2224, data.distance!!.last()!!, 2)
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) =
        kotlin.test.assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "expected $expected ± $tolerance, was $actual")
}
