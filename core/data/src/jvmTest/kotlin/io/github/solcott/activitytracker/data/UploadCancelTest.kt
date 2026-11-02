package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityEdit
import io.github.solcott.activitytracker.data.activity.UploadQueue
import io.github.solcott.activitytracker.data.activity.UploadSource
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

/**
 * Cancelling in the upload queue (data/upload-queue): a waiting activity's writes are dropped before any is sent, or the cancel is refused.
 */
class UploadCancelTest {
    private val pds = FakeAthletePds()
    private val start = Instant.parse("2026-10-14T09:00:00Z")

    private fun draft(title: String = "Morning ride", importBatch: String? = null) =
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

    private fun view(uri: AtUri, title: String = "Morning ride") =
        ActivityView(
            uri = uri,
            cid = Cid("bafyrec"),
            author = ProfileViewBasic(pds.did, Handle("alice.test")),
            record = ActivityRecord(Sport.RIDE, start, 3600, start, title = title),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Clock.System.now(),
        )

    private fun sentFor(uri: AtUri) = pds.writes.filter { it.endsWith("/${uri.rkey}") }

    @Test
    fun aCancelledNewActivityIsNeverSentAndLeavesTheLists() = runTest {
        val s = pds.session()
        pds.pdsUnreachable = true
        val uri = s.activities.publish(draft())
        s.cache(view(uri))

        assertTrue(s.uploads().cancel(uri.rkey!!))
        pds.pdsUnreachable = false
        s.activities.sync()

        assertEquals(emptyList(), sentFor(uri))
        assertTrue(s.uploads().queue.first().isEmpty)
        assertEquals(emptyList(), s.feed.timeline().first().map { it.uri }, "it no longer appears in the feed")
    }

    @Test
    fun aCancelledEditLeavesTheActivityAsLastUploaded() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft())
        // The edit reads the record, then can't send it.
        pds.onRequest = { path -> if (path.endsWith("putRecord")) throw IOException("Connection refused") }
        s.manager.edit(view(uri), ActivityEdit(title = "Evening ride", description = "", diveSite = null, media = emptyList()))

        assertTrue(s.uploads().cancel(uri.rkey!!))
        pds.onRequest = null
        s.activities.sync()

        val stored =
            AtJson.decodeFromJsonElement(
                ActivityRecord.serializer(),
                pds.stored.getValue(pds.repo).getValue("${Nsid.FEED_ACTIVITY}/${uri.rkey}"),
            )
        assertEquals("Morning ride", stored.title)
        assertEquals(listOf("Morning ride"), s.uploads().queue.first().finished.map { it.title })
    }

    @Test
    fun anActivityBeingSentCantBeCancelled() = runTest {
        val s = pds.session()
        pds.pdsUnreachable = true
        val uri = s.activities.publish(draft())
        pds.pdsUnreachable = false
        var refused: Boolean? = null
        pds.onRequest = { path -> if (path.endsWith("putRecord") && refused == null) refused = !s.uploads().cancel(uri.rkey!!) }

        s.activities.sync()

        assertEquals(true, refused)
        assertTrue(s.uploads().queue.first().finished.single().sent > 1, "every write of it was sent")
    }

    @Test
    fun anActivityWithAWriteSentCantBeCancelled() = runTest {
        val s = pds.session()
        pds.pdsUnreachable = true
        val uri = s.activities.publish(draft())
        pds.pdsUnreachable = false
        val first = s.db.outbox().pending(pds.did.value).first().id
        s.outbox.drain { it.id == first }

        assertFalse(s.uploads().cancel(uri.rkey!!))
        assertTrue(s.db.outbox().pending(pds.did.value, uri.rkey!!).isNotEmpty())
    }

    @Test
    fun aWriteCancelledDuringADrainIsSkipped() = runTest {
        val s = pds.session()
        pds.pdsUnreachable = true
        val first = s.activities.publish(draft("First"))
        val second = s.activities.publish(draft("Second"))
        pds.pdsUnreachable = false
        var cancelled = false
        pds.onRequest = { if (!cancelled) cancelled = s.uploads().cancel(second.rkey!!) }

        s.activities.sync()

        assertTrue(cancelled)
        assertTrue(sentFor(first).isNotEmpty())
        assertEquals(emptyList(), sentFor(second), "the drain listed it before the cancel, and skipped it")
    }

    @Test
    fun aRefusedActivityCanBeCancelled() = runTest {
        val s = pds.session()
        pds.failPutsOf = Nsid.FEED_ACTIVITY
        val uri = s.activities.publish(draft())

        assertTrue(s.uploads().cancel(uri.rkey!!))
        assertEquals(emptyList(), s.rejected.changes.first())
    }

    @Test
    fun cancellingTheRestOfAnImportKeepsWhatStarted() = runTest {
        val s = pds.session()
        val uploads = s.uploads()
        pds.pdsUnreachable = true
        s.activities.startImport("3kbatch", UploadSource.Export(listOf("Strava")))
        val uris =
            s.activities.publishBatch(flowOf(draft("One", "3kbatch"), draft("Two", "3kbatch"), draft("Three", "3kbatch")), drain = false)
        pds.pdsUnreachable = false
        val first = s.db.outbox().pending(pds.did.value, uris.first().rkey!!).first().id
        s.outbox.drain { it.id == first }

        assertEquals(2, uploads.cancelRest("3kbatch"))
        s.activities.sync()

        assertEquals(listOf("One"), uploads.queue.first().imports.single().finished.map { it.title })
        uris.drop(1).forEach { assertEquals(emptyList(), sentFor(it)) }
    }

    @Test
    fun aConfirmedCancelFinishesWhenItsScreenCloses() = runTest {
        val s = pds.session()
        pds.pdsUnreachable = true
        val uri = s.activities.publish(draft())
        val claim = s.clients.pacing.claim

        // Waiting for the activity being sent when the screen that asked closes.
        claim.lock()
        val cancel = launch { s.uploads().cancel(uri.rkey!!) }
        testScheduler.runCurrent()
        cancel.cancel()
        claim.unlock()
        cancel.join()

        assertTrue(s.uploads().queue.first().isEmpty)
        assertEquals(emptyList(), s.db.outbox().pending(pds.did.value, uri.rkey!!))
    }
}
