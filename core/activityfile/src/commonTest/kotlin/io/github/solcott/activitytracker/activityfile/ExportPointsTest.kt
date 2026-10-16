package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.GearChange
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ExportPointsTest {
    private val start = Instant.parse("2026-09-20T07:00:00Z")

    private fun location(at: Instant, lat: Double, alt: Double? = 12.0) = LocationSample(lat, -122.3, alt, 3.0, 5.0, at)

    @Test
    fun trackPointsTakeTheStreamSampleAtTheirSecond() {
        val track = (0..2).map { location(start + it.seconds, LATS[it]) }
        val streams =
            ActivityStreamData(
                time = listOf(0, 1, 2),
                distance = listOf(0, 1110, 2220),
                altitude = listOf(10050, null, 10100),
                speed = listOf(3000, 3100, null),
                heartRate = listOf(120, 121, 122),
                temperature = listOf(215, 215, 216),
                gears = listOf(GearChange(1, 2, 5)),
            )
        val points = ExportPoints.join(start, track, streams)
        assertEquals(3, points.size)
        assertEquals(listOf(120, 121, 122), points.map { it.heartRate })
        assertEquals(listOf(47.6, 47.6001, 47.6002), points.map { it.latitude })
        assertEquals(listOf(0.0, 11.1, 22.2), points.map { it.distanceMeters })
        // The stream's altitude where it has one, else GPS.
        assertEquals(listOf(100.5, 12.0, 101.0), points.map { it.altitudeMeters })
        assertEquals(listOf(3.0, 3.1, null), points.map { it.speedMps })
        assertEquals(21.5, points.first().temperatureCelsius)
    }

    @Test
    fun streamsWithoutATrackArePositionLessPoints() {
        val streams = ActivityStreamData(time = listOf(0, 1, 5), heartRate = listOf(100, 101, 105), power = listOf(200, 210, 250))
        val points = ExportPoints.join(start, null, streams)
        assertEquals(listOf(start, start + 1.seconds, start + 5.seconds), points.map { it.time })
        assertTrue(points.none { it.hasPosition })
        assertEquals(listOf(200, 210, 250), points.map { it.powerWatts })
    }

    @Test
    fun aTrackWithoutStreamsKeepsItsGpsAltitude() {
        val points = ExportPoints.join(start, listOf(location(start, 47.6), location(start + 1.seconds, 47.61, alt = null)), null)
        assertEquals(listOf(12.0, null), points.map { it.altitudeMeters })
        assertTrue(points.all { it.heartRate == null && it.hasPosition })
    }

    @Test
    fun twoPointsInOneSecondBothTakeItsSample() {
        val track = listOf(location(start, 47.6), location(start + 400.milliseconds, 47.6001), location(start + 1.seconds, 47.6002))
        val streams = ActivityStreamData(time = listOf(0, 1), heartRate = listOf(130, 131))
        val points = ExportPoints.join(start, track, streams)
        assertEquals(3, points.size)
        assertEquals(listOf(130, 130, 131), points.map { it.heartRate })
        assertEquals(start + 400.milliseconds, points[1].time)
    }

    @Test
    fun aSamplePastTheTracksEndIsItsOwnPoint() {
        val track = listOf(location(start, 47.6), location(start + 1.seconds, 47.6001))
        val streams = ActivityStreamData(time = listOf(0, 1, 2, 3), heartRate = listOf(140, 141, 142, 143))
        val points = ExportPoints.join(start, track, streams)
        assertEquals(4, points.size)
        assertEquals(listOf(140, 141, 142, 143), points.map { it.heartRate })
        assertNull(points[3].latitude)
        assertEquals(start + 3.seconds, points[3].time)
    }
}

/** Exact in decimal, unlike `47.6 + i * 1e-4`, so they survive a round trip through text. */
internal val LATS = listOf(47.6, 47.6001, 47.6002, 47.6003)
