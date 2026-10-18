package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.activityfile.DiveGas
import io.github.solcott.activitytracker.activityfile.DivePosition
import io.github.solcott.activitytracker.activityfile.DiveTank
import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.activityfile.ImportedDive
import io.github.solcott.activitytracker.activityfile.ImportedMtb
import io.github.solcott.activitytracker.activityfile.ImportedPower
import io.github.solcott.activitytracker.activityfile.ImportedSensor
import io.github.solcott.activitytracker.activityfile.ImportedShifting
import io.github.solcott.activitytracker.activityfile.SensorBattery
import io.github.solcott.activitytracker.activityfile.SensorKind
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveGas
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveTank
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivitySensor
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.DecoModel
import io.github.solcott.activitytracker.lexicon.app.Length
import io.github.solcott.activitytracker.lexicon.app.SensorType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.SwimStroke
import io.github.solcott.activitytracker.lexicon.app.TrainingLoad
import io.github.solcott.activitytracker.lexicon.app.WaterType
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.RecordingResult
import io.github.solcott.activitytracker.location.RecordingState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

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
    fun aPoolSwimsLengthsGoInTheStreamsAndItsPoolLengthOnTheRecord() {
        val streams = ActivityStreamData(time = listOf(0, 30), lengths = listOf(Length(0, 30, SwimStroke.FREESTYLE, 15)))
        val swim = imported(emptyList()).copy(poolLengthCentimeters = 2500, streams = streams)
        val draft = ActivityDraft.fromImport(swim, ActivityType(Sport.SWIM, SubSport.POOL), null)
        assertEquals(streams, draft.streams)
        assertEquals(2500, draft.toRecord(start).poolLength)
        assertNull(ActivityDraft.fromImport(imported(emptyList()), ActivityType(Sport.RUN), null).toRecord(start).poolLength)
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

    private val diveFields =
        ImportedDive(
            maxDepthMeters = 25.0,
            avgDepthMeters = null,
            minWaterTempCelsius = null,
            diveNumber = null,
            surfaceIntervalMinutes = null,
            gradientFactorLow = null,
            gradientFactorHigh = null,
            startCns = null,
            endCns = null,
            gases = listOf(DiveGas(32, 0)),
            closedCircuit = false,
            profile = emptyList(),
            tanks = listOf(DiveTank(200.0, 60.0, null, volumeLiters = 11.1, gas = DiveGas(32, 0), sensor = "t")),
            startN2 = 10,
            endN2 = 55,
            avgAscentRateMps = 0.15,
            maxAscentRateMps = 0.3,
            avgDescentRateMps = 0.2,
            maxDescentRateMps = 0.4,
            descentTimeSeconds = 300.4,
            waterType = WaterType.SALT,
            waterDensityKgPerM3 = 1025.0,
            airTemperatureCelsius = -2.5,
            visibilityMeters = 15.0,
            altitudeMeters = 450.0,
            noFlyTimeSeconds = 64_800.0,
            desaturationTimeSeconds = 72_000.0,
            highestPpO2Bar = 1.4,
        )

    @Test
    fun aDivesComputerSettingsPublishInMillibar() {
        val settings =
            diveFields.copy(
                decoModel = DecoModel.ZHL16C,
                ppO2WarnBar = 1.4,
                ppO2CriticalBar = 1.6,
                ppO2DecoBar = 1.6,
                lowSetpointBar = 0.7,
                highSetpointBar = 1.3,
                safetyStopSeconds = 180,
            )
        val dive =
            ActivityDraft.fromImport(imported(emptyList()).copy(dive = settings), ActivityType(Sport.DIVE), null).toRecord(start).dive!!
        assertEquals(DecoModel.ZHL16C, dive.decoModel)
        assertEquals(listOf(1400, 1600, 1600), listOf(dive.ppO2Warn, dive.ppO2Critical, dive.ppO2Deco))
        assertEquals(700 to 1300, dive.lowSetpoint to dive.highSetpoint)
        assertEquals(180, dive.safetyStopTime)
        val older =
            ActivityDraft.fromImport(imported(emptyList()).copy(dive = diveFields), ActivityType(Sport.DIVE), null).toRecord(start).dive!!
        assertEquals(null, older.decoModel ?: older.ppO2Warn ?: older.lowSetpoint ?: older.safetyStopTime, "none where the file had none")
    }

    @Test
    fun aDivesNewerSummaryAndTanksAreInLexiconUnits() {
        val dive =
            ActivityDraft.fromImport(imported(emptyList()).copy(dive = diveFields), ActivityType(Sport.DIVE), null).toRecord(start).dive!!
        assertEquals(10 to 55, dive.startN2 to dive.endN2)
        assertEquals(
            listOf(150L, 300L, 200L, 400L),
            listOf(dive.avgAscentRate, dive.maxAscentRate, dive.avgDescentRate, dive.maxDescentRate),
        )
        assertEquals(300L, dive.descentTime)
        assertEquals(WaterType.SALT to 1025, dive.waterType to dive.waterDensity)
        assertEquals(-25L, dive.airTemperature)
        assertEquals(1500L, dive.visibility, "centimeters")
        assertEquals(450L, dive.altitude)
        assertEquals(64_800L to 72_000L, dive.noFlyTime to dive.desaturationTime)
        assertEquals(1400, dive.highestPpO2)
        assertEquals(
            listOf(ActivityDiveTank(startPressure = 200_000, endPressure = 60_000, volume = 11_100, gas = ActivityDiveGas(32, 0))),
            dive.tanks,
            "milliliters, and no sensor id",
        )
    }

    @Test
    fun aRidesPowerShiftingAndTrailTotalsArePublishedAndItsTrainingLoadKeptApart() {
        val ride =
            imported(emptyList())
                .copy(
                    type = ActivityType(Sport.RIDE, SubSport.MOUNTAIN),
                    elevationLossMeters = 120.4,
                    avgCadence = 80,
                    maxCadence = 110,
                    power = ImportedPower(180, 650, 210, 300_000.0, 250, 0.84, 45.0),
                    shifting = ImportedShifting(2, 3, emptyList(), emptyList(), emptyList()),
                    mtb = ImportedMtb(jumps = 2, longestHangTimeSeconds = 0.9, longestJumpMeters = 8.2, grit = 12.5, flow = 2.54),
                    calories = 450,
                    aerobicTrainingEffect = 3.4,
                )
        val draft = ActivityDraft.fromImport(ride, ride.type, null)
        val record = draft.toRecord(start)
        assertEquals(120L, record.elevationLoss)
        assertEquals(80 to 110, record.avgCadence to record.maxCadence)
        assertEquals(listOf(180, 650, 210), listOf(record.avgPower, record.maxPower, record.normalizedPower))
        assertEquals(300L, record.work, "kilojoules")
        assertEquals(450, record.calories)
        assertEquals(2 to 3, record.frontShifts to record.rearShifts)
        assertEquals(Triple(2, 125, 25), Triple(record.jumps, record.grit, record.flow), "grit and flow in tenths")
        assertEquals(
            TrainingLoad(ftp = 250, intensityFactor = 84, trainingStressScore = 450, aerobicTrainingEffect = 34),
            draft.trainingLoad,
            "kept for the zones record",
        )
        val json = AtJson.encodeToString(ActivityRecord.serializer(), record)
        assertFalse("ftp" in json || "intensityFactor" in json || "trainingStressScore" in json || "TrainingEffect" in json, json)
    }

    @Test
    fun aRunWithoutPowerHasNoPowerFields() {
        val draft = ActivityDraft.fromImport(imported(emptyList()).copy(avgCadence = 172), ActivityType(Sport.RUN), null)
        val record = draft.toRecord(start)
        assertEquals(172, record.avgCadence)
        assertNull(record.avgPower)
        assertNull(record.normalizedPower)
        assertNull(draft.trainingLoad)
    }

    @Test
    fun hrvGoesWithTheTrainingLoadNeverOnTheRecordOrStreams() {
        val run = imported(emptyList()).copy(hrvRmssd = 42, streams = ActivityStreamData(time = listOf(0, 1), heartRate = listOf(150, 151)))
        val draft = ActivityDraft.fromImport(run, ActivityType(Sport.RUN), null)

        assertEquals(TrainingLoad(hrvRmssd = 42), draft.trainingLoad, "an HRV alone is a load to store")
        val record = AtJson.encodeToString(ActivityRecord.serializer(), draft.toRecord(start))
        assertFalse("hrv" in record.lowercase(), record)
        assertFalse("hrv" in AtJson.encodeToString(ActivityStreamData.serializer(), draft.streams!!).lowercase())
    }

    @Test
    fun countsAndTheUtcOffsetArePublishedOnTheRecord() {
        val row = imported(emptyList()).copy(cycles = 1850, avgStrokeDistanceCentimeters = 1020, utcOffsetMinutes = -300)
        val record = ActivityDraft.fromImport(row, ActivityType(Sport.ROW), null).toRecord(start)

        assertEquals(1850, record.cycles)
        assertEquals(1020, record.avgStrokeDistance)
        assertEquals(-300, record.utcOffset)
        val bare = ActivityDraft.fromImport(imported(emptyList()), ActivityType(Sport.RUN), null).toRecord(start)
        assertNull(bare.cycles)
        assertNull(bare.utcOffset, "a GPX or TCX import has none")
    }

    @Test
    fun aRecordingTakesTheDevicesOffsetAtItsStart() {
        val result = RecordingResult(RecordingState(startedAt = start), endedAt = start + 30.minutes)
        val draft = ActivityDraft.fromRecording(result, ActivityType(Sport.RUN), null, null, zone = TimeZone.of("Europe/Paris"))

        assertEquals(120, draft.toRecord(start).utcOffset, "Paris in September is on summer time")
    }

    @Test
    fun theDescriptionStartsFromTheFilesNotes() {
        val draft = ActivityDraft.fromImport(imported(emptyList()).copy(description = "Turtle at the wall"), ActivityType(Sport.DIVE), null)
        assertEquals("Turtle at the wall", draft.description)
        assertEquals("Mine", draft.withDetails("Mine", null, emptyList()).description, "the athlete's words replace it")
    }

    @Test
    fun aTracklessDiveKeepsItsSurfaceFixesInItsTrackOnly() {
        val dive = diveFields.copy(entry = DivePosition(13.86, -61.07), exit = DivePosition(13.87, -61.08))
        val draft = ActivityDraft.fromImport(imported(emptyList()).copy(dive = dive), ActivityType(Sport.DIVE), null)
        val gpx = draft.gpx!!.decodeToString()
        assertTrue("13.86" in gpx && "13.87" in gpx, gpx)
        assertTrue(draft.route.isEmpty(), "no route, so no polyline")
        assertNull(draft.toRecord(start).polyline)

        val logbook = diveFields.copy(entry = DivePosition(17.3157, -87.5346))
        assertEquals(
            1,
            Regex("<trkpt")
                .findAll(
                    ActivityDraft.fromImport(imported(emptyList()).copy(dive = logbook), ActivityType(Sport.DIVE), null)
                        .gpx!!
                        .decodeToString()
                )
                .count(),
        )

        val tracked = ActivityDraft.fromImport(imported(listOf(sample(0), sample(30))).copy(dive = dive), ActivityType(Sport.DIVE), null)
        assertEquals(2, tracked.route.size, "a dive with GPS records keeps them")
        assertTrue("13.86" !in tracked.gpx!!.decodeToString())
        assertNull(ActivityDraft.fromImport(imported(emptyList()).copy(dive = diveFields), ActivityType(Sport.DIVE), null).gpx)
    }
}
