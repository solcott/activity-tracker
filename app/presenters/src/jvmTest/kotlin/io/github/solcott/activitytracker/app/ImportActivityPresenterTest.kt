package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.activityfile.ActivityFileException
import io.github.solcott.activitytracker.activityfile.FitActivity
import io.github.solcott.activitytracker.activityfile.FitDecoder
import io.github.solcott.activitytracker.activityfile.FitFileId
import io.github.solcott.activitytracker.activityfile.FitRecord
import io.github.solcott.activitytracker.activityfile.FitSummary
import io.github.solcott.activitytracker.app.record.ImportActivityEvent
import io.github.solcott.activitytracker.app.record.ImportActivityPresenter
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class ImportActivityPresenterTest {
    private val fixture = SessionTestFixture()
    private val start = Instant.parse("2026-09-20T07:00:00Z")

    private val run =
        FitActivity(
            fileId = FitFileId("activity", "garmin", "fenix7", start.toEpochMilliseconds()),
            sessions =
                listOf(
                    FitSummary(
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
                    FitRecord(
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

    private val bytes = ByteString(1, 2, 3)

    @Test
    fun readsAFitFileAndPublishesIt() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        presenterTestOf({ ImportActivityPresenter(navigator, repos.activities, FitDecoder { run }) }) {
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
            assertEquals(Sport.RUN, read.sport)

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
    fun anUnreadableFileShowsAnError() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        val decoder = FitDecoder { throw ActivityFileException("FIT file is corrupt or truncated") }
        presenterTestOf({ ImportActivityPresenter(navigator, repos.activities, decoder) }) {
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
        presenterTestOf({ ImportActivityPresenter(navigator, repos.activities, decoder) }) {
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
    fun otherFormatsAreNotSupportedYet() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        presenterTestOf({ ImportActivityPresenter(navigator, repos.activities, FitDecoder { error("not called") }) }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("ride.gpx", bytes))
            assertEquals(UiError(UiError.Kind.UnsupportedActivityFile, null), awaitItem().error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
