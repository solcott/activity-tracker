package io.github.solcott.activitytracker.data.export

import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.GarminJavaFitDecoder
import io.github.solcott.activitytracker.data.FakeAthletePds
import io.github.solcott.activitytracker.data.activity.ExportFormat
import io.github.solcott.activitytracker.data.activity.PlaceRecords
import io.github.solcott.activitytracker.data.activity.StoredActivity
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** A data export's rebuilt FIT (auth/data-export): built from the activity as the PDS holds it, every tier merged, with no AppView. */
class RebuiltFitTest {
    private val pds = FakeAthletePds()
    private val start = Instant.parse("2026-10-14T07:00:00Z")
    private val uri = AtUri("at://${pds.did.value}/${Nsid.FEED_ACTIVITY}/3lrun")
    private val record =
        ActivityRecord(
            sport = Sport.RUN,
            startedAt = start,
            elapsedTime = 1800,
            createdAt = Clock.System.now(),
            title = "Lunch run",
            distance = 5000,
            utcOffset = 0,
        )

    // The first point sits where a privacy zone would trim it: the export keeps it, as recorded.
    private val track = (0..2).map { LocationSample(47.6 + it * 0.0001, -122.3, 50.0, null, 5.0, start + it.seconds) }

    @Test
    fun aRunSplitAcrossTiersRebuildsWithEveryTiersDataAndItsFullTrack() = runTest {
        val s = pds.session()
        val stored =
            StoredActivity(
                uri,
                mapOf(
                    Visibility.PUBLIC to
                        PlaceRecords(
                            activity = record,
                            streams = ActivityStreamData(time = listOf(0, 1, 2), speed = listOf(3000, 3100, 3200)),
                        ),
                    Visibility.SELF to
                        PlaceRecords(
                            details = ActivityDetailsRecord(activity = uri, createdAt = Clock.System.now(), avgHeartRate = 141),
                            streams = ActivityStreamData(time = listOf(0, 1, 2), heartRate = listOf(140, 141, 142)),
                        ),
                ),
            )

        val fit = assertNotNull(s.exporter.rebuiltFit(stored, track))
        assertEquals(ExportFormat.FIT.mimeType, fit.mimeType)
        assertEquals("Lunch run 2026-10-14.fit", fit.name)
        val read = ActivityFileReader(GarminJavaFitDecoder()).read(fit.bytes).single()
        assertEquals(listOf(140, 141, 142), read.streams?.heartRate)
        assertEquals(141, read.avgHeartRate)
        assertEquals(47.6, read.samples.first().latitude, 1e-6)
        assertEquals(3, read.samples.size)
    }

    @Test
    fun aManualEntryHasNoFile() = runTest {
        val s = pds.session()
        val stored = StoredActivity(uri, mapOf(Visibility.PUBLIC to PlaceRecords(activity = record)))
        assertNull(s.exporter.rebuiltFit(stored, track = null))
    }

    @Test
    fun noRecordAnywhereHasNoFile() = runTest {
        val s = pds.session()
        assertNull(s.exporter.rebuiltFit(StoredActivity(uri, emptyMap()), track))
    }
}
