package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.activityfile.ActivityFileException
import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.DecodedActivity
import io.github.solcott.activitytracker.activityfile.DecodedDive
import io.github.solcott.activitytracker.activityfile.DecodedSample
import io.github.solcott.activitytracker.activityfile.DecodedSource
import io.github.solcott.activitytracker.activityfile.DecodedSummary
import io.github.solcott.activitytracker.activityfile.DiveGas
import io.github.solcott.activitytracker.activityfile.FitDecoder
import io.github.solcott.activitytracker.activityfile.TimeZones
import io.github.solcott.activitytracker.app.record.DepthPreviewPoint
import io.github.solcott.activitytracker.app.record.DiveGasPreview
import io.github.solcott.activitytracker.app.record.ImportActivityEvent
import io.github.solcott.activitytracker.app.record.ImportActivityPresenter
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.app.record.TimeZoneOption
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.int
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
        presenterTestOf({ ImportActivityPresenter(navigator, repos.activities, ActivityFileReader { run }, repos.recentTypes) }) {
            val initial = awaitItem()
            assertNull(initial.preview)

            initial.eventSink(ImportActivityEvent.FilePicked("Morning_Run.FIT", bytes))
            val read = awaitUntil { it.preview != null }
            val preview = assertNotNull(read.preview)
            assertEquals("Morning_Run.FIT", preview.fileName)
            assertEquals(5000.0, preview.distanceMeters)
            assertEquals(152, preview.avgHeartRate)
            assertEquals("Garmin fenix7", preview.device)
            assertNotNull(preview.polyline)
            assertNull(preview.dive)
            assertNull(preview.clock, "a FIT file's times are UTC")
            assertEquals(ActivityType(Sport.RUN), read.type)

            read.eventSink(ImportActivityEvent.TitleChanged("Lunch loop"))
            awaitUntil { it.title == "Lunch loop" }.eventSink(ImportActivityEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }

        val record = fixture.putRecords.single()
        assertEquals("Lunch loop", record.getValue("title").jsonPrimitive.content)
        assertEquals(5000L, record.getValue("distance").jsonPrimitive.long)
        assertEquals(1750L, record.getValue("movingTime").jsonPrimitive.long)
        assertEquals(152, record.getValue("avgHeartRate").jsonPrimitive.int)
        assertEquals("Garmin fenix7", record.getValue("device").jsonPrimitive.content)
        assertTrue("track" in record, "the GPS track is uploaded as a GPX blob")
    }

    @Test
    fun aDiveShowsItsDepthsAndGases() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        // Two hours at 10 s intervals: more points than the profile draws.
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
        val dive =
            DecodedActivity(
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
                dive = DecodedDive(31.0, 42, 90, 30, 70, 2, 25, listOf(DiveGas(21, 35), DiveGas(50, 0)), closedCircuit = false),
            )
        presenterTestOf({ ImportActivityPresenter(navigator, repos.activities, ActivityFileReader { dive }, repos.recentTypes) }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("dive.fit", bytes))
            val read = awaitUntil { it.preview != null }
            assertEquals(ActivityType(Sport.DIVE, SubSport.MULTI_GAS), read.type)
            val preview = assertNotNull(read.preview?.dive)
            assertEquals(31.0, preview.maxDepthMeters)
            assertEquals(listOf(DiveGasPreview(21, 35), DiveGasPreview(50, 0)), preview.gases)
            assertEquals(42, preview.diveNumber)
            assertTrue(preview.profile.size <= 300, "thinned to ${preview.profile.size}")
            assertEquals(DepthPreviewPoint(7200, 0.0), preview.profile.last(), "still ends at the surface")
            assertNull(read.preview?.polyline)
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
        presenterTestOf({ ImportActivityPresenter(navigator, repos.activities, ActivityFileReader { dive }, repos.recentTypes) }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("Petrel.xml", bytes))
            val read = awaitUntil { it.preview != null }
            val clock = assertNotNull(read.preview?.clock)
            assertEquals("2026-09-20 07:00", clock.localStart)
            assertEquals(TimeZones.device().id, clock.zone.id, "starts in this device's zone")
            assertTrue(TimeZoneOption("America/New_York", "UTC-04:00") in clock.zones, "daylight saving on the day")

            read.eventSink(ImportActivityEvent.TimeZoneChanged("America/Cancun"))
            val placed = awaitUntil { it.preview?.clock?.zone?.id == "America/Cancun" }
            assertEquals(TimeZoneOption("America/Cancun", "UTC-05:00"), placed.preview?.clock?.zone)
            placed.eventSink(ImportActivityEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals("2026-09-20T12:00:00Z", fixture.putRecords.single().getValue("startedAt").jsonPrimitive.content)
    }

    @Test
    fun anUnreadableFileShowsAnError() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        val decoder = FitDecoder { throw ActivityFileException("FIT file is corrupt or truncated") }
        presenterTestOf({ ImportActivityPresenter(navigator, repos.activities, ActivityFileReader(decoder), repos.recentTypes) }) {
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
        presenterTestOf({ ImportActivityPresenter(navigator, repos.activities, ActivityFileReader(decoder), repos.recentTypes) }) {
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
            ImportActivityPresenter(navigator, repos.activities, ActivityFileReader { error("not FIT") }, repos.recentTypes)
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
            ImportActivityPresenter(navigator, repos.activities, ActivityFileReader { error("not called") }, repos.recentTypes)
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("notes.txt", ByteString("just some notes".encodeToByteArray())))
            assertEquals(UiError(UiError.Kind.UnsupportedActivityFile, null), awaitUntil { it.error != null }.error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
