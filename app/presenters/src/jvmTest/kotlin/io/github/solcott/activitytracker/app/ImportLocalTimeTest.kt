package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.DecodedActivity
import io.github.solcott.activitytracker.activityfile.DecodedSource
import io.github.solcott.activitytracker.activityfile.DecodedSummary
import io.github.solcott.activitytracker.app.record.ImportActivityEvent
import io.github.solcott.activitytracker.app.record.ImportActivityPresenter
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.app.record.PartOfDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString

/** The import screen reads a FIT file's local times in the zone its clock was in, not this device's. */
class ImportLocalTimeTest {
    private val fixture = SessionTestFixture()
    private val start = Instant.parse("2026-09-20T07:00:00Z")
    private val bytes = ByteString(14, 16, 0, 0, 0, 0, 0, 0, '.'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'T'.code.toByte())

    @Test
    fun theDefaultTitleReadsTheStartInTheFilesOwnZone() = runTest {
        val repos = fixture.repos()
        // Started at 07:00 UTC on a watch whose clock was at UTC+1: 8 AM there, whatever this device's zone.
        val end = start + 1800.seconds
        val run =
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
                activityTimestampMillis = end.toEpochMilliseconds(),
                activityLocalTimestampSeconds = end.epochSeconds - FIT_EPOCH_SECONDS + 3600,
            )
        presenterTestOf({
            ImportActivityPresenter(
                ImportActivityScreen(),
                FakeNavigator(ImportActivityScreen()),
                repos.activities,
                repos.manager,
                ActivityFileReader { run },
                repos.typeChoices,
                repos.profiles,
                repos.feed,
                repos.defaults,
                repos.units,
                repos.suggestions,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("Run.fit", bytes))
            assertEquals(PartOfDay.Morning, awaitUntil { it.preview != null }.defaultTitle?.partOfDay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        /** FIT times count seconds from 1989-12-31T00:00:00Z. */
        const val FIT_EPOCH_SECONDS = 631_065_600L
    }
}
