package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.DecodedActivity
import io.github.solcott.activitytracker.activityfile.DecodedSample
import io.github.solcott.activitytracker.activityfile.DecodedSource
import io.github.solcott.activitytracker.activityfile.DecodedSummary
import io.github.solcott.activitytracker.app.record.ImportActivityEvent
import io.github.solcott.activitytracker.app.record.ImportActivityPresenter
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.jsonPrimitive

/** An imported file's notes (a UDDF dive's) seed the description, for the athlete to keep, edit or clear. */
class ImportActivityNotesTest {
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
    fun theFilesNotesPrefillTheDescriptionForTheAthleteToEdit() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen())
        presenterTestOf({
            ImportActivityPresenter(
                ImportActivityScreen(),
                navigator,
                repos.activities,
                repos.manager,
                ActivityFileReader { run.copy(notes = "Turtle at the wall") },
                repos.typeChoices,
                repos.profiles,
                repos.feed,
                repos.defaults,
                repos.units,
                repos.suggestions,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("Dive.fit", bytes))
            val read = awaitUntil { it.details != null }
            val details = assertNotNull(read.details)
            assertEquals("Turtle at the wall", details.description.text.toString())

            details.description.setTextAndPlaceCursorAtEnd("Turtle at the north wall")
            read.eventSink(ImportActivityEvent.Save(defaultTitle = "Dive"))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("Turtle at the north wall", fixture.putRecords.first().getValue("description").jsonPrimitive.content)
    }

    @Test
    fun clearedNotesAreNotPublished() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen())
        presenterTestOf({
            ImportActivityPresenter(
                ImportActivityScreen(),
                navigator,
                repos.activities,
                repos.manager,
                ActivityFileReader { run.copy(notes = "Turtle at the wall") },
                repos.typeChoices,
                repos.profiles,
                repos.feed,
                repos.defaults,
                repos.units,
                repos.suggestions,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("Dive.fit", bytes))
            val read = awaitUntil { it.details != null }
            assertNotNull(read.details).description.clearText()
            read.eventSink(ImportActivityEvent.Save(defaultTitle = "Dive"))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse("description" in fixture.putRecords.first())
    }
}
