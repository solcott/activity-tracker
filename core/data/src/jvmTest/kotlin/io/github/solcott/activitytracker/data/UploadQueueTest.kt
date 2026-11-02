package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.atproto.xrpc.RateLimit
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ImportProgress
import io.github.solcott.activitytracker.data.activity.UploadLog
import io.github.solcott.activitytracker.data.activity.UploadQueue
import io.github.solcott.activitytracker.data.activity.UploadSource
import io.github.solcott.activitytracker.data.activity.timeLeft
import io.github.solcott.activitytracker.data.db.BulkLeft
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.atproto.UploadBlob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

/** The feed's upload row and the time left for imports (data/upload-queue, data/outbox). */
class UploadQueueTest {
    private val pds = FakeAthletePds()
    private val start = Instant.parse("2026-10-14T09:00:00Z")
    private val now = Instant.parse("2026-11-01T12:00:00Z")

    private fun draft(title: String, importBatch: String? = null) =
        ActivityDraft(
            type = ActivityType(Sport.RIDE),
            title = title,
            description = null,
            startedAt = start,
            elapsed = 60.minutes,
            streams = ActivityStreamData(time = (0..3600 step 10).toList(), heartRate = (0..3600 step 10).map { 140 }),
            importBatch = importBatch,
        )

    private fun FakeAthletePds.Session.uploads() = UploadQueue(db, clients, rejected, Clock.System)

    private fun bobs(n: Int) =
        ActivityView(
            uri = AtUri("at://did:plc:bob/${Nsid.FEED_ACTIVITY}/3kbob$n"),
            cid = Cid("bafyrec"),
            author = ProfileViewBasic(Did("did:plc:bob"), Handle("bob.test")),
            record = ActivityRecord(Sport.RIDE, start, 3600, start),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Clock.System.now(),
        )

    @Test
    fun theRowCountsActivitiesNotWrites() = runTest {
        val s = pds.session()
        pds.pdsUnreachable = true
        s.activities.startImport("3kbatch", UploadSource.Export(listOf("Strava")))
        s.activities.publishBatch(flowOf(draft("One", "3kbatch"), draft("Two", "3kbatch")), drain = false)
        s.activities.publish(draft("Recorded run"))
        repeat(3) { s.activities.toggleLike(bobs(it)) }

        val summary = s.uploads().summary.first()
        assertEquals(1, summary.own)
        assertEquals(2, summary.imported)
        assertEquals(3, summary.other)
        assertEquals(0, summary.refused)
    }

    @Test
    fun refusedActivitiesAreCountedApart() = runTest {
        val s = pds.session()
        pds.failPutsOf = Nsid.FEED_ACTIVITY
        s.activities.publish(draft("Refused"))

        val summary = s.uploads().summary.first()
        assertEquals(0, summary.own, "not uploading any more")
        assertEquals(1, summary.refused)
        assertEquals(false, summary.uploading)
    }

    @Test
    fun bothPausesAreTold() = runTest {
        val s = pds.session()
        pds.pdsUnreachable = true
        s.activities.publish(draft("Waiting"))
        s.uploads().pauseImports()
        val until = Clock.System.now() + 1.hours
        s.clients.pacing.pause(until)

        val summary = s.uploads().summary.first()
        assertTrue(summary.importsPaused)
        assertEquals(until, summary.serverPausedUntil)
    }

    @Test
    fun anHourlyLimitGivesTheTimeLeft() {
        // 50,000 writes at 2 points each against 5,000 points an hour, 90% of it for imports: over 22 hours.
        val left =
            timeLeft(BulkLeft(writes = 50_000, blobs = 0), listOf(RateLimit(5000, 5000, now + 1.hours, "5000;w=3600")), emptyList(), now)
        assertNotNull(left)
        assertTrue(left > 22.hours && left < 23.hours, "$left")
    }

    @Test
    fun aBlobLimitCountsOnlyBlobs() {
        val limit = RateLimit(1000, 1000, now + 1.days, "1000;w=86400", method = UploadBlob.NSID)
        val left = timeLeft(BulkLeft(writes = 4_000, blobs = 2_700), listOf(limit), emptyList(), now)
        assertNotNull(left)
        assertTrue(left > 2.days, "2,700 blobs at 900 a day take three days: $left")
    }

    @Test
    fun thePaceSoFarGivesTheTimeLeft() {
        val sent = (0 until 60).map { now - 15.minutes + (it * 15).seconds }
        val left = timeLeft(BulkLeft(writes = 400, blobs = 0), emptyList(), sent, now)
        assertNotNull(left)
        assertTrue(left > 90.minutes && left < 110.minutes, "60 writes in 15 minutes, 400 left: $left")
    }

    @Test
    fun nothingToGoByGivesNoTimeLeft() {
        assertNull(timeLeft(BulkLeft(writes = 400, blobs = 0), emptyList(), emptyList(), now))
        assertNull(timeLeft(BulkLeft(writes = 0, blobs = 0), listOf(RateLimit(5000, 5000, now, "5000;w=3600")), emptyList(), now))
    }

    @Test
    fun noTimeLeftWhileTheAthletePausedImports() = runTest {
        val s = pds.session()
        pds.pdsUnreachable = true
        s.activities.publishBatch(flowOf(draft("One", "3kbatch")), drain = false)
        s.clients.pacing.heard(RateLimit(5000, 5000, Clock.System.now() + 1.hours, "5000;w=3600"))
        val uploads = s.uploads()
        assertNotNull(uploads.estimate.first())

        uploads.pauseImports()

        assertNull(uploads.estimate.first())
    }

    @Test
    fun importProgressCountsUploadedOfSavedUntilDoneOrPaused() = runTest {
        val s = pds.session()
        val uploads = s.uploads()
        pds.pdsUnreachable = true
        s.activities.startImport("3kbatch", UploadSource.Files("a.fit", 2))
        s.activities.publishBatch(flowOf(draft("One", "3kbatch"), draft("Two", "3kbatch")), drain = false)
        s.activities.finishImport("3kbatch", saved = 2, skipped = 0)
        assertEquals(ImportProgress(uploaded = 0, saved = 2), uploads.importProgress.first())
        assertTrue(uploads.importsActive.first())

        uploads.pauseImports()
        assertNull(uploads.importProgress.first(), "paused imports show no progress")
        assertEquals(false, uploads.importsActive.first(), "and closing the app needn't ask")

        uploads.resumeImports()
        pds.pdsUnreachable = false
        s.activities.sync()
        assertNull(uploads.importProgress.first(), "done")
    }

    @Test
    fun resumingFinishesWhenItsScreenCloses() = runTest {
        val s = pds.session()
        val uploads = s.uploads()
        uploads.pauseImports()

        val resume = launch(start = CoroutineStart.UNDISPATCHED) { uploads.resumeImports() }
        resume.cancel()
        resume.join()

        assertEquals(false, s.clients.pacing.importsPaused.value)
        assertNull(s.db.keyValue().get(UploadLog.importsPausedKey(pds.did.value)), "and stays resumed on the next start")
    }

    @Test
    fun importProgressCountsWhatIsSavedWhileTheSaveRuns() = runTest {
        val s = pds.session()
        val uploads = s.uploads()
        pds.pdsUnreachable = true
        // Saved this far, its total not yet recorded: the save is still running, or stopped short.
        s.activities.startImport("3kbatch", UploadSource.Files("a.fit", 5))
        s.activities.publishBatch(flowOf(draft("One", "3kbatch"), draft("Two", "3kbatch")), drain = false)

        assertEquals(ImportProgress(uploaded = 0, saved = 2), uploads.importProgress.first())
        assertEquals(2, uploads.queue.first().imports.single().saved)
    }
}
