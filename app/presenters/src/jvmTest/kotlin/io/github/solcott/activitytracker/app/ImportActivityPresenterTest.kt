package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.activityfile.ActivityFileException
import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.DecodedActivity
import io.github.solcott.activitytracker.activityfile.DecodedAthleteSettings
import io.github.solcott.activitytracker.activityfile.DecodedDive
import io.github.solcott.activitytracker.activityfile.DecodedGearChange
import io.github.solcott.activitytracker.activityfile.DecodedJump
import io.github.solcott.activitytracker.activityfile.DecodedSample
import io.github.solcott.activitytracker.activityfile.DecodedSensor
import io.github.solcott.activitytracker.activityfile.DecodedSource
import io.github.solcott.activitytracker.activityfile.DecodedSummary
import io.github.solcott.activitytracker.activityfile.DiveGas
import io.github.solcott.activitytracker.activityfile.DivePosition
import io.github.solcott.activitytracker.activityfile.DiveTank
import io.github.solcott.activitytracker.activityfile.FitDecoder
import io.github.solcott.activitytracker.activityfile.SensorBattery
import io.github.solcott.activitytracker.activityfile.SensorKind
import io.github.solcott.activitytracker.activityfile.TimeZones
import io.github.solcott.activitytracker.app.edit.DetailsEvent
import io.github.solcott.activitytracker.app.edit.PickedPhoto
import io.github.solcott.activitytracker.app.record.CadencePreview
import io.github.solcott.activitytracker.app.record.DefaultTitle
import io.github.solcott.activitytracker.app.record.DepthPreviewPoint
import io.github.solcott.activitytracker.app.record.DiveGasPreview
import io.github.solcott.activitytracker.app.record.DiveSitePreview
import io.github.solcott.activitytracker.app.record.DiveTankPreview
import io.github.solcott.activitytracker.app.record.GearTimePreview
import io.github.solcott.activitytracker.app.record.ImportActivityEvent
import io.github.solcott.activitytracker.app.record.ImportActivityPresenter
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.app.record.MtbPreview
import io.github.solcott.activitytracker.app.record.PartOfDay
import io.github.solcott.activitytracker.app.record.PowerPreview
import io.github.solcott.activitytracker.app.record.SensorPreview
import io.github.solcott.activitytracker.app.record.TimeZoneOption
import io.github.solcott.activitytracker.app.visibility.VisibilityEvent
import io.github.solcott.activitytracker.data.training.ThresholdChange
import io.github.solcott.activitytracker.data.training.ZoneModels
import io.github.solcott.activitytracker.data.training.Zones
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.HeartRateZoneModel
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.StreamChannel
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class ImportActivityPresenterTest {
    private val fixture = SessionTestFixture()
    private val start = Instant.parse("2026-09-20T07:00:00Z")

    private val run =
        DecodedActivity(
            fileId = DecodedSource("activity", "garmin", "fenix7", start.toEpochMilliseconds()),
            sessions =
                listOf(
                    DecodedSummary(
                        "running",
                        null,
                        start.toEpochMilliseconds(),
                        1800.0,
                        1750.0,
                        5000.0,
                        30.0,
                        null,
                        4.0,
                        152,
                        171,
                        null,
                        null,
                        null,
                    )
                ),
            laps = emptyList(),
            records =
                (0L..2L).map { i ->
                    DecodedSample(
                        (start + (i * 60).seconds).toEpochMilliseconds(),
                        47.6 + i * 0.001,
                        -122.3,
                        null,
                        150,
                        null,
                        null,
                        null,
                        null,
                        null,
                    )
                },
        )

    /** Just a FIT header: the reader picks the decoder by the ".FIT" signature at byte 8, and the fake decoder ignores the rest. */
    private val bytes = ByteString(14, 16, 0, 0, 0, 0, 0, 0, '.'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'T'.code.toByte())

    @Test
    fun readsAFitFileAndPublishesIt() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader { run },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            val initial = awaitItem()
            assertNull(initial.preview)

            initial.eventSink(ImportActivityEvent.FilePicked("Morning_Run.FIT", bytes))
            val read = awaitUntil { it.preview != null }
            val preview = assertNotNull(read.preview)
            assertEquals("Morning_Run.FIT", preview.fileName)
            assertEquals(5000.0, preview.distanceMeters)
            assertEquals(152, preview.avgHeartRate)
            assertEquals("Garmin Fenix 7", preview.device)
            assertNotNull(preview.polyline)
            assertNull(preview.dive)
            assertNull(preview.clock, "a FIT file's times are UTC")
            assertEquals(ActivityType(Sport.RUN), read.type)

            read.title.setTextAndPlaceCursorAtEnd("Lunch loop")
            read.eventSink(ImportActivityEvent.Save(defaultTitle = "Morning Run"))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }

        val (record, streams) = fixture.putRecords
        assertEquals(Nsid.FEED_ACTIVITY_STREAMS, streams.getValue("\$type").jsonPrimitive.content)
        assertEquals("Lunch loop", record.getValue("title").jsonPrimitive.content)
        assertEquals(5000L, record.getValue("distance").jsonPrimitive.long)
        assertEquals(1750L, record.getValue("movingTime").jsonPrimitive.long)
        assertEquals(152, record.getValue("avgHeartRate").jsonPrimitive.int)
        assertEquals("Garmin Fenix 7", record.getValue("device").jsonPrimitive.content)
        assertFalse("track" in record, "the full track is a record of its own")
        val (tier, track) =
            fixture.spacePuts.single { (_, put) -> put.getValue("\$type").jsonPrimitive.content == Nsid.FEED_ACTIVITY_TRACK }
        assertEquals(Visibility.SELF, tier, "only the athlete reads it, whatever the activity's visibility")
        assertTrue("track" in track, "the GPS track is uploaded as a GPX blob")
    }

    @Test
    fun aDescriptionAndPhotosPublishWithTheActivity() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader { run },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("Morning_Run.FIT", bytes))
            val read = awaitUntil { it.details != null }
            val details = assertNotNull(read.details)
            assertNull(details.diveSite, "a run has no dive site")
            details.description.setTextAndPlaceCursorAtEnd("Easy loop")
            details.eventSink(DetailsEvent.PhotosPicked(persistentListOf(PickedPhoto(ByteString(1, 2), "image/webp", 4, 3))))
            awaitUntil { it.details?.media?.size == 1 }.eventSink(ImportActivityEvent.Save(defaultTitle = "Morning Run"))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }

        val record = fixture.putRecords.first()
        assertEquals("Easy loop", record.getValue("description").jsonPrimitive.content)
        val photo = record.getValue("media").jsonArray.single().jsonObject
        assertEquals("app.activitytracker.feed.activity#image", photo.getValue("\$type").jsonPrimitive.content)
        assertEquals("4", photo.getValue("aspectRatio").jsonObject.getValue("width").jsonPrimitive.content)
    }

    private val withSettings = run.copy(athlete = DecodedAthleteSettings(maxHeartRate = 188, functionalThresholdPower = 240))

    @Test
    fun deviceSettingsUpdateChosenThresholdsOnSave() = runTest {
        // Max HR was set before this run started; FTP isn't chosen.
        fixture.privateDetails =
            """{"details":{"training":{"maxHeartRate":190,"functionalThresholdPower":231,"autoUpdate":["maxHeartRate"],""" +
                """"setAt":{"maxHeartRate":"2020-01-01T00:00:00Z"}}},"visibility":{"training":"self"}}"""
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader { withSettings },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("Morning_Run.FIT", bytes))
            val read = awaitUntil { it.preview != null && it.thresholdChanges.isNotEmpty() }
            assertEquals(listOf(ThresholdChange(Threshold.MAX_HEART_RATE, 190, 188)), read.thresholdChanges)
            read.eventSink(ImportActivityEvent.Save(defaultTitle = "Morning Run"))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        val training = fixture.privateDetailsPuts.single().getValue("details").jsonObject.getValue("training").jsonObject
        assertEquals(188L, training.getValue("maxHeartRate").jsonPrimitive.long)
        assertEquals(231L, training.getValue("functionalThresholdPower").jsonPrimitive.long)
        assertEquals(start.toString(), training.getValue("setAt").jsonObject["maxHeartRate"]?.jsonPrimitive?.content)
    }

    @Test
    fun chartsKeepTheChosenVisibilityAndTheZonesAsTheDeviceHadThem() = runTest {
        fixture.privateDetails =
            """{"details":{"training":{"maxHeartRate":190,"thresholdHeartRate":170,"autoUpdate":["maxHeartRate"],""" +
                """"heartRateZoneModel":"percentMaxHeartRate","setAt":{"maxHeartRate":"2020-01-01T00:00:00Z"}}},""" +
                """"visibility":{"training":"self"}}"""
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader { withSettings },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("Morning_Run.FIT", bytes))
            // Threshold changes show once the athlete's training has loaded.
            val read = awaitUntil { it.preview != null && it.thresholdChanges.isNotEmpty() }
            assertTrue(read.preview!!.hasStreams)
            read.visibility.eventSink(VisibilityEvent.CategoryChanged(DetailCategory.HEART_RATE, Visibility.MUTUALS))
            val mutuals = awaitUntil { it.visibility.tierOf(DetailCategory.HEART_RATE) == Visibility.MUTUALS }
            mutuals.eventSink(ImportActivityEvent.Save(defaultTitle = "Morning Run"))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        // The heart rate lives in the mutuals space, not the repo.
        assertTrue(fixture.putRecords.none { StreamChannel.HEART_RATE in it["channels"].toString() })
        val (tier, streams) = fixture.spacePuts.single { it.second.getValue("\$type").jsonPrimitive.content == Nsid.FEED_ACTIVITY_STREAMS }
        assertEquals(Visibility.MUTUALS, tier)
        assertTrue(StreamChannel.HEART_RATE in streams.getValue("channels").toString())
        assertFalse("heartRateZones" in streams, "zones are a record of their own")
        // Zones go to the narrower of the charts (Mutuals) and the training (Only me).
        val (zonesTier, zonesRecord) =
            fixture.spacePuts.single { it.second.getValue("\$type").jsonPrimitive.content == Nsid.FEED_ACTIVITY_ZONES }
        assertEquals(Visibility.SELF, zonesTier)
        val zones = zonesRecord.getValue("heartRateZones").jsonObject
        assertEquals(HeartRateZoneModel.PERCENT_MAX, zones.getValue("model").jsonPrimitive.content)
        // Zones from the device's max heart rate (188), not the saved 190.
        val expected = Zones.compute(ZoneModels.heartRate(HeartRateZoneModel.PERCENT_MAX), TrainingDetails(maxHeartRate = 188))!!
        assertEquals(expected.map { it.low }, zones.getValue("lows").jsonArray.map { it.jsonPrimitive.int })
    }

    @Test
    fun deviceSettingsChangeNothingUnlessChosen() = runTest {
        fixture.privateDetails = """{"details":{"training":{"maxHeartRate":190}},"visibility":{"training":"self"}}"""
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader { withSettings },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("Morning_Run.FIT", bytes))
            val read = awaitUntil { it.preview != null }
            assertTrue(read.thresholdChanges.isEmpty())
            read.eventSink(ImportActivityEvent.Save(defaultTitle = "Morning Run"))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(fixture.privateDetailsPuts.isEmpty())
    }

    /** A Shearwater-style dive log, two hours at 10 s intervals: more points than the profile draws. */
    private fun diveLog(): DecodedActivity {
        val records =
            (0L..720L).map { i ->
                DecodedSample(
                    (start + (i * 10).seconds).toEpochMilliseconds(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    18.0,
                    if (i == 720L) 0.0 else 30.0,
                )
            }
        return DecodedActivity(
            fileId = DecodedSource(null, null, "Shearwater Perdix", null),
            sessions =
                listOf(
                    DecodedSummary(
                        "diving",
                        "multi_gas_diving",
                        start.toEpochMilliseconds(),
                        7200.0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                    )
                ),
            laps = emptyList(),
            records = records,
            dive =
                DecodedDive(
                    31.0,
                    42,
                    90,
                    30,
                    70,
                    2,
                    25,
                    listOf(DiveGas(21, 35), DiveGas(50, 0)),
                    closedCircuit = false,
                    bottomTimeSeconds = 2400.4,
                    oxygenToxicityOtu = 40,
                    sacBarPerMinute = 1.5,
                    tanks = listOf(DiveTank(210.0, 60.0, null)),
                    exit = DivePosition(13.87, -61.08),
                ),
        )
    }

    @Test
    fun aDiveShowsItsDepthsAndGases() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        val dive = diveLog()
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader { dive },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("dive.fit", bytes))
            val read = awaitUntil { it.preview != null }
            assertEquals(ActivityType(Sport.DIVE, SubSport.MULTI_GAS), read.type)
            val preview = assertNotNull(read.preview?.dive)
            assertEquals(31.0, preview.maxDepthMeters)
            assertEquals(listOf(DiveGasPreview(21, 35), DiveGasPreview(50, 0)), preview.gases)
            assertEquals(42, preview.diveNumber)
            assertEquals(2400L, preview.bottomTimeSeconds)
            assertEquals(40, preview.oxygenToxicityOtu)
            assertEquals(1.5, preview.sacBarPerMinute)
            assertEquals(listOf(DiveTankPreview(210.0, 60.0)), preview.tanks)
            assertEquals(DiveSitePreview(13.87, -61.08), preview.site, "the exit, with no entry")
            assertTrue(preview.profile.size <= 300, "thinned to ${preview.profile.size}")
            assertEquals(DepthPreviewPoint(7200, 0.0), preview.profile.last(), "still ends at the surface")
            assertNull(read.preview?.polyline)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** An MTB ride from an Edge: the session's power totals, two rear shifts, a jump and its sensors. */
    private fun mtbRide(): DecodedActivity {
        val millis = { sec: Long -> (start + sec.seconds).toEpochMilliseconds() }
        return DecodedActivity(
            fileId = DecodedSource("activity", "garmin", "edge840", millis(0)),
            sessions =
                listOf(
                    DecodedSummary(
                        "cycling",
                        "mountain",
                        millis(0),
                        600.0,
                        600.0,
                        3000.0,
                        80.0,
                        null,
                        null,
                        140,
                        170,
                        avgPowerWatts = 180,
                        avgCadence = 75,
                        avgTemperatureCelsius = null,
                        maxPowerWatts = 640,
                        normalizedPowerWatts = 205,
                        maxCadence = 115,
                        totalDescentMeters = 82.0,
                        totalWorkJoules = 108_400.0,
                        thresholdPowerWatts = 250,
                        intensityFactor = 0.82,
                        trainingStressScore = 11.2,
                        rearShiftCount = 2,
                        jumpCount = 1,
                        totalGrit = 9.5,
                        avgFlow = 3.1,
                    )
                ),
            laps = emptyList(),
            records = (0L..10L).map { i -> DecodedSample(millis(i * 60), null, null, null, null, 80, 180, null, null, null) },
            sensors =
                listOf(
                    DecodedSensor(0, "local", null, "garmin", "edge840", null, null),
                    DecodedSensor(1, "antplus", "bikePower", "sram", "1052", "low", null),
                    DecodedSensor(2, "bluetooth_low_energy", "heartRate", "garmin", "hrm200", "good", 80),
                ),
            gearChanges = listOf(DecodedGearChange(millis(0), true, 1, 32, 3, 42), DecodedGearChange(millis(120), true, 1, 32, 4, 36)),
            jumps = listOf(DecodedJump(0.6, 4.0)),
        )
    }

    @Test
    fun aRideShowsItsPowerShiftingTrailAndSensors() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        val ride = mtbRide()
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader { ride },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("ride.fit", bytes))
            val preview = assertNotNull(awaitUntil { it.preview != null }.preview)
            assertEquals("Garmin Edge 840", preview.device)
            assertEquals(82.0, preview.elevationLossMeters)
            assertEquals(CadencePreview(75, 115), preview.cadence)
            assertEquals(PowerPreview(180, 640, 205, 108L, 250, 0.82, 11.2), preview.power)
            val shifting = assertNotNull(preview.shifting)
            assertEquals(2, shifting.rearShifts)
            assertEquals(listOf(GearTimePreview(3, 120), GearTimePreview(4, 480)), shifting.gears)
            assertEquals(MtbPreview(1, 0.6, 4.0, 9.5, 3.1), preview.mtb)
            assertEquals(
                listOf(
                    SensorPreview(SensorKind.Power, "SRAM", null, SensorBattery.Low, null),
                    SensorPreview(SensorKind.HeartRate, "Garmin", "HRM 200", SensorBattery.Good, 80),
                ),
                preview.sensors,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRunHasNoPedallingCadence() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        val stepping = run.copy(records = run.records.map { it.copy(cadence = 85) })
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader { stepping },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("run.fit", bytes))
            val preview = assertNotNull(awaitUntil { it.preview != null }.preview)
            assertNull(preview.cadence, "a run's cadence is strides, not revolutions")
            assertNull(preview.power)
            assertNull(preview.shifting)
            assertTrue(preview.sensors.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aZonelessClockIsPlacedInThePickedZone() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        // As a dive computer logs it: 7 AM local, which the parser read as if this device were on UTC.
        val dive =
            run.copy(
                fileId = DecodedSource(null, null, "Shearwater Petrel", null),
                sessions = run.sessions.map { it.copy(sport = "diving", subSport = "single_gas_diving") },
                records = emptyList(),
                localStart = LocalDateTime(2026, 9, 20, 7, 0),
            )
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader { dive },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("Petrel.xml", bytes))
            val read = awaitUntil { it.preview != null }
            val clock = assertNotNull(read.preview?.clock)
            assertEquals("2026-09-20 07:00", clock.localStart)
            assertEquals(TimeZones.device().id, clock.zone.id, "starts in this device's zone")
            assertTrue(TimeZoneOption("America/New_York", "UTC-04:00") in clock.zones, "daylight saving on the day")

            read.eventSink(ImportActivityEvent.TimeZoneChanged("America/Cancun"))
            val placed = awaitUntil { it.preview?.clock?.zone?.id == "America/Cancun" }
            assertEquals(TimeZoneOption("America/Cancun", "UTC-05:00"), placed.preview?.clock?.zone)
            assertEquals(DefaultTitle(PartOfDay.Morning, placed.type), placed.defaultTitle, "7 AM where the dive was")

            placed.eventSink(ImportActivityEvent.TimeZoneChanged("Asia/Tokyo"))
            val tokyo = awaitUntil { it.preview?.clock?.zone?.id == "Asia/Tokyo" }
            assertEquals(PartOfDay.Morning, tokyo.defaultTitle?.partOfDay, "the clock read 7 AM in any zone")

            tokyo.eventSink(ImportActivityEvent.TimeZoneChanged("America/Cancun"))
            awaitUntil { it.preview?.clock?.zone?.id == "America/Cancun" }.eventSink(ImportActivityEvent.Save("Morning Dive"))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }

        val record = fixture.putRecords.first()
        assertEquals("2026-09-20T12:00:00Z", record.getValue("startedAt").jsonPrimitive.content)
        assertEquals("Morning Dive", record.getValue("title").jsonPrimitive.content, "a blank title saves the default")
    }

    @Test
    fun theFilesOwnTitleFillsTheField() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        var file = run.copy(title = "Lake loop")
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader { file },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("Lake.gpx", bytes))
            val titled = awaitUntil { it.preview != null }
            assertEquals("Lake loop", titled.title.text.toString())
            assertNull(titled.defaultTitle, "the file named it")

            // A file without a title clears the last one's, so it can't be saved under the wrong name...
            file = run
            titled.eventSink(ImportActivityEvent.FilePicked("Run.fit", bytes))
            val untitled = awaitUntil { it.preview?.fileName == "Run.fit" }
            assertEquals("", untitled.title.text.toString())
            assertEquals(ActivityType(Sport.RUN), untitled.defaultTitle?.type)

            // ...but keeps what the athlete typed.
            untitled.title.setTextAndPlaceCursorAtEnd("Tempo")
            untitled.eventSink(ImportActivityEvent.FilePicked("Again.fit", bytes))
            val again = awaitUntil { it.preview?.fileName == "Again.fit" }
            assertEquals("Tempo", again.title.text.toString())

            again.eventSink(ImportActivityEvent.TypeChanged(ActivityType(Sport.RIDE, SubSport.MOUNTAIN)))
            assertEquals(ActivityType(Sport.RIDE, SubSport.MOUNTAIN), awaitUntil { it.type.sport == Sport.RIDE }.defaultTitle?.type)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anUnreadableFileShowsAnError() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        val decoder = FitDecoder { throw ActivityFileException("FIT file is corrupt or truncated") }
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader(decoder),
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("broken.fit", bytes))
            val failed = awaitUntil { it.error != null && !it.reading }
            assertEquals(UiError(UiError.Kind.ReadActivityFile, "FIT file is corrupt or truncated"), failed.error)
            assertNull(failed.preview)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(fixture.putRecords.isEmpty())
    }

    @Test
    fun aFailedPickDropsThePreviousFile() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        var fail = false
        val decoder = FitDecoder { if (fail) throw ActivityFileException("FIT file is corrupt or truncated") else run }
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader(decoder),
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("good.fit", bytes))
            val read = awaitUntil { it.preview != null }

            fail = true
            read.eventSink(ImportActivityEvent.FilePicked("broken.fit", bytes))
            val failed = awaitUntil { it.error != null && !it.reading }
            assertNull(failed.preview, "Save must not publish the earlier file")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aGpxFileIsReadByItsContentWhateverItsName() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        val gpx =
            """<gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1"><trk><type>cycling</type><trkseg>
                <trkpt lat="47.6" lon="-122.3"><time>2026-09-20T07:00:00Z</time></trkpt>
                <trkpt lat="47.61" lon="-122.3"><time>2026-09-20T07:05:00Z</time></trkpt>
            </trkseg></trk></gpx>"""
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader { error("not FIT") },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("export.dat", ByteString(gpx.encodeToByteArray())))
            val read = awaitUntil { it.preview != null }
            assertEquals(ActivityType(Sport.RIDE), read.type)
            assertEquals(1112.0, read.preview!!.distanceMeters!!, 1.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun otherFilesAreNotSupported() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader { error("not called") },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("notes.txt", ByteString("just some notes".encodeToByteArray())))
            assertEquals(UiError(UiError.Kind.UnsupportedActivityFile, null), awaitUntil { it.error != null }.error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
