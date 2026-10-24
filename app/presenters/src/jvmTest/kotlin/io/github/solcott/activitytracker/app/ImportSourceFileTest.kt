package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.DecodedActivity
import io.github.solcott.activitytracker.activityfile.DecodedSummary
import io.github.solcott.activitytracker.app.record.ImportActivityEvent
import io.github.solcott.activitytracker.app.record.ImportActivityPresenter
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** A single imported file kept as its activity's original (`feed/activity-source-file`), or not when it's too large. */
class ImportSourceFileTest {
    private val fixture = SessionTestFixture()
    private val start = Instant.parse("2026-09-20T07:00:00Z")

    private val run =
        DecodedActivity(
            fileId = null,
            sessions =
                listOf(
                    DecodedSummary(
                        "running",
                        null,
                        start.toEpochMilliseconds(),
                        1800.0,
                        1750.0,
                        5000.0,
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
            records = emptyList(),
        )

    /** Just a FIT header: the reader picks the decoder by the ".FIT" signature at byte 8, and the fake decoder ignores the rest. */
    private val bytes = ByteString(14, 16, 0, 0, 0, 0, 0, 0, '.'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'T'.code.toByte())

    @Test
    fun theFileIsKeptAsTheActivitysSourceInTheSelfSpace() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen())
        presenterTestOf({
            ImportActivityPresenter(
                ImportActivityScreen(),
                navigator,
                repos.activities,
                repos.manager,
                ActivityFileReader { run },
                repos.typeChoices,
                repos.gearChoices,
                repos.newDeviceChoices,
                repos.profiles,
                repos.feed,
                repos.defaults,
                repos.units,
                repos.suggestions,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("Morning_Run.FIT", bytes))
            val read = awaitUntil { it.preview != null }
            assertFalse(read.originalTooLarge)
            read.eventSink(ImportActivityEvent.Save(defaultTitle = "Morning Run"))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }

        val (tier, source) =
            fixture.spacePuts.single { (_, put) -> put.getValue("\$type").jsonPrimitive.content == Nsid.FEED_ACTIVITY_SOURCE }
        assertEquals(Visibility.SELF, tier, "only the athlete reads it, whatever the activity's visibility")
        assertEquals("Morning_Run.FIT", source.getValue("fileName").jsonPrimitive.content)
        assertEquals("fit", source.getValue("format").jsonPrimitive.content)
        assertEquals(bytes.size.toLong(), source.getValue("size").jsonPrimitive.long)
        assertTrue("file" in source, "the file is uploaded as a blob")
        assertNull(source["entry"], "a single file names no entry")
        assertTrue(
            fixture.putRecords.none { it.getValue("\$type").jsonPrimitive.content == Nsid.FEED_ACTIVITY_SOURCE },
            "never in the repo",
        )
    }

    @Test
    fun aFileTooLargeToKeepSaysSoAndImportsWithoutIt() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen())
        // Random bytes don't compress, so this stays over the 10 MB a kept file may take.
        val huge = ByteString(bytes.toByteArray() + Random(3).nextBytes(10_500_000))
        presenterTestOf({
            ImportActivityPresenter(
                ImportActivityScreen(),
                navigator,
                repos.activities,
                repos.manager,
                ActivityFileReader { run },
                repos.typeChoices,
                repos.gearChoices,
                repos.newDeviceChoices,
                repos.profiles,
                repos.feed,
                repos.defaults,
                repos.units,
                repos.suggestions,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("Huge.fit", huge))
            val read = awaitUntil { it.preview != null }
            assertTrue(read.originalTooLarge)
            read.eventSink(ImportActivityEvent.Save(defaultTitle = "Morning Run"))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(Nsid.FEED_ACTIVITY, fixture.putRecords.first().getValue("\$type").jsonPrimitive.content, "the activity still saves")
        assertTrue(fixture.spacePuts.none { (_, put) -> put.getValue("\$type").jsonPrimitive.content == Nsid.FEED_ACTIVITY_SOURCE })
    }
}
