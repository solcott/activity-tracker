package io.github.solcott.activitytracker.data.segments

import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackData
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant

class SegmentTrackEncodingTest {
    private val start = Instant.parse("2026-10-15T07:31:12Z")

    private fun sample(lat: Double, lng: Double, seconds: Long, altitude: Double? = null) =
        LocationSample(lat, lng, altitude, speed = 4.0, accuracy = 3.0, time = Instant.fromEpochSeconds(start.epochSeconds + seconds))

    @Test
    fun offsetsAreFromTheFullTracksStart() {
        // The first piece begins 95 s in, where the track left a zone around the real start.
        val pieces = listOf(listOf(sample(51.5, -0.12, 95), sample(51.5001, -0.12, 100)), listOf(sample(51.6, -0.1, 700)))
        val data = segmentTrackData(pieces, origin = start)
        assertEquals(listOf(95L, 100L), data.pieces[0].t)
        assertEquals(listOf(700L), data.pieces[1].t)
        assertEquals(listOf(515_000_000, 515_001_000), data.pieces[0].lat)
        assertEquals(listOf(-1_200_000, -1_200_000), data.pieces[0].lng)
    }

    @Test
    fun noWallClockTimeInTheJson() {
        val data = segmentTrackData(listOf(listOf(sample(51.5, -0.12, 0), sample(51.5001, -0.12, 1))), origin = start)
        val json = AtJson.encodeToString(SegmentTrackData.serializer(), data)
        assertFalse(json.contains("2026"), json)
        assertFalse(json.contains(start.epochSeconds.toString()), json)
    }

    @Test
    fun altitudeInCentimetersOnlyWhenKnown() {
        val withAltitude = segmentTrackData(listOf(listOf(sample(0.0, 0.0, 0, 12.345), sample(0.0, 0.0001, 1))), origin = start)
        assertEquals(listOf(1235, null), withAltitude.pieces.single().alt)
        val without = segmentTrackData(listOf(listOf(sample(0.0, 0.0, 0))), origin = start)
        assertNull(without.pieces.single().alt)
    }
}
