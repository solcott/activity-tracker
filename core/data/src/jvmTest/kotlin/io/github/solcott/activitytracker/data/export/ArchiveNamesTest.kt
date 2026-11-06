package io.github.solcott.activitytracker.data.export

import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ArchiveNamesTest {
    private fun record(title: String?, at: String = "2026-10-14T18:00:00Z", offset: Int? = 0) =
        ActivityRecord(
            sport = Sport.RUN,
            startedAt = Instant.parse(at),
            elapsedTime = 60,
            createdAt = Instant.parse(at),
            title = title,
            utcOffset = offset,
        )

    @Test
    fun rebuiltFilesAreTheDateAndTitle() {
        assertEquals("2026-10-14-Lunch run.fit", ArchiveNames().rebuilt(record("Lunch run"), "fit"))
        assertEquals("2026-10-14-Run.fit", ArchiveNames().rebuilt(record(null), "fit"))
    }

    @Test
    fun theDateIsLocalToTheActivity() {
        // 02:00 UTC is still the 13th at UTC−6.
        assertEquals("2026-10-13-Night run.fit", ArchiveNames().rebuilt(record("Night run", "2026-10-14T02:00:00Z", offset = -360), "fit"))
    }

    @Test
    fun originalsKeepTheirNameAfterTheDate() {
        assertEquals("2026-10-14-Morning_Ride.fit", ArchiveNames().original(record("Ride"), "Morning_Ride.fit"))
        assertEquals("2026-10-14-log.uddf.gz", ArchiveNames().original(record("Dive"), "log.uddf.gz"))
    }

    @Test
    fun clashingNamesGetNumbered() {
        val names = ArchiveNames()
        assertEquals("2026-10-14-Lunch run.fit", names.rebuilt(record("Lunch run"), "fit"))
        assertEquals("2026-10-14-Lunch run-2.fit", names.rebuilt(record("Lunch run"), "fit"))
        assertEquals("2026-10-14-LUNCH RUN-3.fit", names.rebuilt(record("LUNCH RUN"), "fit"))
    }

    @Test
    fun aResumedExportNeverReusesANameItGave() {
        val names = ArchiveNames(taken = listOf("2026-10-14-Lunch run.fit"))
        assertEquals("2026-10-14-Lunch run-2.fit", names.rebuilt(record("Lunch run"), "fit"))
    }

    @Test
    fun unsafeCharactersAreReplaced() {
        assertEquals("2026-10-14-Up_down_ a_b.fit", ArchiveNames().rebuilt(record("Up/down: a\\b"), "fit"))
        assertEquals("2026-10-14-x_y.gpx", ArchiveNames().original(record("x"), "x?y.gpx"))
    }
}
