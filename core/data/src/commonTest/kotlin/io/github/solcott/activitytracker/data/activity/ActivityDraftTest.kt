package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ActivityDraftTest {
    private val start = Instant.parse("2026-09-20T07:00:00Z")

    private fun imported(samples: List<LocationSample>) =
        ImportedActivity(
            sport = "run",
            startedAt = start,
            elapsed = 30.minutes,
            movingSeconds = 1700,
            distanceMeters = 5012.4,
            elevationGainMeters = 41.6,
            maxSpeedMps = 4.25,
            avgHeartRate = 151,
            maxHeartRate = 178,
            avgTemperatureCelsius = 12.34,
            device = "Garmin fenix7",
            samples = samples,
        )

    private fun sample(sec: Long) = LocationSample(47.6 + sec * 1e-4, -122.3, 50.0, null, null, start + sec.seconds)

    @Test
    fun importBecomesARecordInLexiconUnits() {
        val draft = ActivityDraft.fromImport(imported(listOf(sample(0), sample(30), sample(60))), sport = "ride", title = "Imported")
        val record = draft.toRecord(createdAt = start + 1.minutes)

        assertEquals("ride", record.sport, "the athlete's choice wins over the file's")
        assertEquals("Imported", record.title)
        assertEquals(start, record.startedAt)
        assertEquals(1800L, record.elapsedTime)
        assertEquals(1700L, record.movingTime)
        assertEquals(5012L, record.distance)
        assertEquals(42L, record.elevationGain)
        assertEquals(2948L, record.avgSpeed) // 5012.4 m / 1700 s = 2.9485 m/s
        assertEquals(4250L, record.maxSpeed)
        assertEquals(151, record.avgHeartRate)
        assertEquals(178, record.maxHeartRate)
        assertEquals(123L, record.avgTemperature)
        assertEquals("Garmin fenix7", record.device)
        assertNotNull(record.polyline)
        assertTrue("<trkpt" in draft.gpx!!.decodeToString())
    }

    @Test
    fun indoorImportHasNoTrack() {
        val draft = ActivityDraft.fromImport(imported(emptyList()), sport = "ride", title = null)
        assertNull(draft.polyline)
        assertNull(draft.gpx)
    }
}
