package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityEdit
import io.github.solcott.activitytracker.data.activity.UploadItem
import io.github.solcott.activitytracker.data.activity.UploadQueue
import io.github.solcott.activitytracker.data.activity.UploadQueueView
import io.github.solcott.activitytracker.data.activity.UploadSource
import io.github.solcott.activitytracker.data.activity.UploadState
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * The upload queue's log (data/upload-queue): one item per activity saved or edited, its state derived from the outbox, kept for a week.
 */
class UploadLogTest {
    private val pds = FakeAthletePds()
    private val start = Instant.parse("2026-10-14T09:00:00Z")

    /** A clock the test moves, for the week items are kept. */
    private class TestClock(var now: Instant = Clock.System.now()) : Clock {
        override fun now() = now

        fun advance(by: Duration) {
            now += by
        }
    }

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

    private fun FakeAthletePds.Session.uploads(clock: Clock = Clock.System) = UploadQueue(db, clients, rejected, clock)

    private suspend fun UploadQueue.view(): UploadQueueView = queue.first()

    private fun UploadQueueView.all(): List<UploadItem> =
        listOfNotNull(current) + own + imports.flatMap { it.live + it.finished } + finished

    private fun view(uri: AtUri, author: Did = pds.did) =
        ActivityView(
            uri = uri,
            cid = Cid("bafyrec"),
            author = ProfileViewBasic(author, Handle("alice.test")),
            record = ActivityRecord(Sport.RIDE, start, 3600, start, title = "Morning ride"),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Clock.System.now(),
        )

    @Test
    fun aSavedActivityWaitsThenUploads() = runTest {
        val s = pds.session()
        val uploads = s.uploads()
        pds.pdsUnreachable = true
        val uri = s.activities.publish(draft())

        val waiting = uploads.view().own.single()
        assertEquals(UploadState.Waiting, waiting.state)
        assertEquals(uri, waiting.uri)
        assertEquals("Morning ride", waiting.title)
        assertEquals(0, waiting.sent)
        assertTrue(waiting.total > 1, "an activity is one item however many writes it takes")

        pds.pdsUnreachable = false
        s.activities.sync()

        val done = uploads.view()
        assertEquals(emptyList(), done.own)
        assertEquals(UploadState.Uploaded, done.finished.single().state)
        assertEquals(done.finished.single().total, done.finished.single().sent)
    }

    @Test
    fun anActivityPartWayShowsItsWritesSent() = runTest {
        val s = pds.session()
        val uploads = s.uploads()
        pds.pdsUnreachable = true
        s.activities.publish(draft())
        pds.pdsUnreachable = false
        val first = s.db.outbox().pending(pds.did.value).first().id

        s.outbox.drain { it.id == first }

        val item = uploads.view().all().single()
        assertEquals(UploadState.Uploading, item.state)
        assertEquals(1, item.sent)
        assertTrue(item.total > item.sent)
    }

    @Test
    fun aRefusedActivityShowsAsRefused() = runTest {
        val s = pds.session()
        pds.failPutsOf = Nsid.FEED_ACTIVITY
        s.activities.publish(draft())

        val view = s.uploads().view()
        assertEquals(UploadState.Refused, view.all().single().state)
        assertEquals(emptyList(), view.refusedOther, "it's listed once, as its item")
    }

    @Test
    fun anEditWhileWaitingRenamesTheOneItem() = runTest {
        val s = pds.session()
        val uploads = s.uploads()
        pds.pdsUnreachable = true
        val uri = s.activities.publish(draft())

        s.manager.edit(view(uri), ActivityEdit(title = "Evening ride", description = "", diveSite = null, media = emptyList()))

        val item = uploads.view().all().single()
        assertEquals("Evening ride", item.title)
        assertEquals(false, item.isEdit, "still the new activity")
    }

    @Test
    fun anEditAfterUploadIsASecondItem() = runTest {
        val s = pds.session()
        val uploads = s.uploads()
        val uri = s.activities.publish(draft())

        s.manager.edit(view(uri), ActivityEdit(title = "Evening ride", description = "", diveSite = null, media = emptyList()))

        val finished = uploads.view().finished
        assertEquals(setOf("Morning ride" to false, "Evening ride" to true), finished.map { it.title to it.isEdit }.toSet())
    }

    @Test
    fun aLikeIsCountedNotListed() = runTest {
        val s = pds.session()
        val uploads = s.uploads()
        pds.pdsUnreachable = true
        val bobs = AtUri("at://did:plc:bob/${Nsid.FEED_ACTIVITY}/3kbob")
        s.activities.toggleLike(view(bobs, author = Did("did:plc:bob")))

        assertTrue(uploads.view().isEmpty)
        assertEquals(1, uploads.summary.first().other)
    }

    @Test
    fun deletingAWaitingActivityRemovesItsItem() = runTest {
        val s = pds.session()
        val uploads = s.uploads()
        pds.pdsUnreachable = true
        val uri = s.activities.publish(draft())

        s.manager.delete(view(uri), withRecordings = false)

        assertTrue(uploads.view().all().isEmpty())
        assertEquals(1, uploads.summary.first().other, "its delete counts with other changes")
    }

    @Test
    fun anImportIsOneGroupWithItsSkippedFiles() = runTest {
        val s = pds.session()
        val uploads = s.uploads()
        pds.pdsUnreachable = true
        s.activities.startImport("3kbatch", UploadSource.Export(listOf("Strava")))
        s.activities.publishBatch(flowOf(draft("One", "3kbatch"), draft("Two", "3kbatch")), drain = false)
        s.activities.skippedImport("3kbatch", "broken.fit", null, null)
        s.activities.finishImport("3kbatch", saved = 2, skipped = 1)

        val import = uploads.view().imports.single()
        assertEquals(UploadSource.Export(listOf("Strava")), import.source)
        assertEquals(2, import.saved)
        assertEquals(1, import.skipped)
        assertEquals(listOf("One", "Two"), import.live.map { it.title })
        assertEquals(listOf(UploadState.Skipped), import.finished.map { it.state })
        assertEquals(2, uploads.summary.first().imported)
        assertEquals(0, uploads.summary.first().own)
    }

    @Test
    fun finishedItemsStayAWeek() = runTest {
        val clock = TestClock()
        val s = pds.session()
        val uploads = s.uploads(clock)
        s.activities.startImport("3kbatch", UploadSource.Export(listOf("Garmin")))
        s.activities.publishBatch(flowOf(draft("One", "3kbatch")))
        clock.now = Clock.System.now()

        clock.advance(6.days)
        uploads.prune()
        assertEquals(1, uploads.view().imports.single().uploaded, "an import done overnight is still listed")

        clock.advance(2.days)
        uploads.prune()
        assertTrue(uploads.view().isEmpty, "a week later the item and its import are gone")
    }

    @Test
    fun clearDropsFinishedItemsAndKeepsLiveOnes() = runTest {
        val s = pds.session()
        val uploads = s.uploads()
        s.activities.publish(draft("Sent"))
        pds.pdsUnreachable = true
        s.activities.publish(draft("Waiting"))

        uploads.clearFinished()

        val view = uploads.view()
        assertEquals(listOf("Waiting"), view.own.map { it.title })
        assertEquals(emptyList(), view.finished)
    }

    @Test
    fun aLiveItemWhoseWritesLeftUncountedIsFinishedOnPrune() = runTest {
        val s = pds.session()
        val uploads = s.uploads()
        pds.pdsUnreachable = true
        val uri = s.activities.publish(draft())
        s.db.outbox().deleteGroupRows(pds.did.value, uri.rkey!!)

        uploads.prune()

        assertNull(uploads.view().own.firstOrNull())
        assertEquals(UploadState.Uploaded, uploads.view().finished.single().state)
    }
}
