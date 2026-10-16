package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.activityfile.DiveGas
import io.github.solcott.activitytracker.activityfile.DiveTank
import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.activityfile.ImportedDive
import io.github.solcott.activitytracker.activityfile.ImportedSensor
import io.github.solcott.activitytracker.activityfile.SensorBattery
import io.github.solcott.activitytracker.activityfile.SensorKind
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveGas
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveTank
import io.github.solcott.activitytracker.lexicon.app.ActivitySensor
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.SensorType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ActivityDraftTest {
    private val start = Instant.parse("2026-09-20T07:00:00Z")

    private fun imported(samples: List<LocationSample>) =
        ImportedActivity(
            type = ActivityType(Sport.RUN),
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
        val draft =
            ActivityDraft.fromImport(
                imported(listOf(sample(0), sample(30), sample(60))),
                type = ActivityType(Sport.RIDE, SubSport.GRAVEL),
                title = "Imported",
            )
        val record = draft.toRecord(createdAt = start + 1.minutes)

        assertEquals(Sport.RIDE, record.sport, "the athlete's choice wins over the file's")
        assertEquals(SubSport.GRAVEL, record.subSport)
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
        assertNull(record.polyline, "the repository adds the trimmed polyline")
        assertEquals(3, draft.route.size)
        assertTrue("<trkpt" in draft.gpx!!.decodeToString())
    }

    @Test
    fun indoorImportHasNoTrack() {
        val draft = ActivityDraft.fromImport(imported(emptyList()), type = ActivityType(Sport.RIDE, SubSport.INDOOR), title = null)
        assertTrue(draft.route.isEmpty())
        assertNull(draft.gpx)
    }

    @Test
    fun sensorsArePublishedWithoutTheirBattery() {
        val ride =
            imported(emptyList())
                .copy(
                    sensors =
                        listOf(
                            ImportedSensor(SensorKind.HeartRate, "Garmin", "HRM 200", SensorBattery.Good, 80),
                            ImportedSensor(SensorKind.Power, "SRAM", null, SensorBattery.Low, null),
                        )
                )
        val record = ActivityDraft.fromImport(ride, type = ActivityType(Sport.RIDE), title = null).toRecord(createdAt = start)
        assertEquals(
            listOf(ActivitySensor(SensorType.HEART_RATE, "Garmin", "HRM 200"), ActivitySensor(SensorType.POWER, "SRAM", null)),
            record.sensors,
        )
        assertNull(ActivityDraft.fromImport(imported(emptyList()), ActivityType(Sport.RUN), null).toRecord(start).sensors)
    }

    @Test
    fun importedDiveBecomesTheRecordsSummaryInLexiconUnits() {
        val dive =
            ImportedDive(
                maxDepthMeters = 30.124,
                avgDepthMeters = 14.5,
                minWaterTempCelsius = 7.86,
                diveNumber = 42,
                surfaceIntervalMinutes = 75,
                gradientFactorLow = 30,
                gradientFactorHigh = 70,
                startCns = 3,
                endCns = 18,
                gases = listOf(DiveGas(32, 0), DiveGas(100, 0)),
                closedCircuit = false,
                profile = emptyList(),
                bottomTimeSeconds = 1520.4,
                sacBarPerMinute = 1.234,
                rmvLitersPerMinute = 14.8,
                tanks = listOf(DiveTank(startPressureBar = 210.0, endPressureBar = 62.5, volumeUsedLiters = null)),
            )
        val record = ActivityDraft.fromImport(imported(emptyList()).copy(dive = dive), ActivityType(Sport.DIVE), null).toRecord(start)
        assertEquals(
            ActivityDive(
                maxDepth = 3012,
                avgDepth = 1450,
                minTemperature = 79,
                bottomTime = 1520,
                surfaceInterval = 4500,
                diveNumber = 42,
                gases = listOf(ActivityDiveGas(32, 0), ActivityDiveGas(100, 0)),
                gradientFactorLow = 30,
                gradientFactorHigh = 70,
                startCns = 3,
                endCns = 18,
                sac = 1234,
                rmv = 14800,
                tanks = listOf(ActivityDiveTank(startPressure = 210_000, endPressure = 62_500)),
            ),
            record.dive,
        )
    }
}
