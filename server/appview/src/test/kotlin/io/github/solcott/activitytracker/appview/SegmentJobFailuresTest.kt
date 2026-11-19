package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.SpaceBlobs
import io.github.solcott.activitytracker.appview.db.SegmentJobFailures
import io.github.solcott.activitytracker.appview.db.SegmentMatchJobs
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.appview.ops.RecordingWorker
import io.github.solcott.activitytracker.appview.ops.RecordingWorker.Companion.reportsOf
import io.github.solcott.activitytracker.appview.segments.JobOutcome
import io.github.solcott.activitytracker.appview.segments.Requeued
import io.github.solcott.activitytracker.appview.segments.SegmentJobOutcomes
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.appview.segments.SegmentMatcher
import io.github.solcott.activitytracker.appview.segments.SegmentQueue
import io.github.solcott.activitytracker.atproto.xrpc.XrpcBytes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/**
 * Segment jobs that give up are kept, with their last error, until a later success, their account's deletion or 30 days
 * (dev/worker-telemetry).
 */
class SegmentJobFailuresTest : SegmentTestBase() {
    private data class Failure(val kind: String, val subject: String, val attempts: Int, val error: String, val firstFailedAt: Long)

    private fun failures(): List<Failure> = runBlocking {
        db.tx {
            SegmentJobFailures.selectAll().map {
                Failure(
                    it[SegmentJobFailures.kind],
                    it[SegmentJobFailures.subjectUri],
                    it[SegmentJobFailures.attempts],
                    it[SegmentJobFailures.lastError],
                    it[SegmentJobFailures.firstFailedAt],
                )
            }
        }
    }

    /** The queued jobs' last errors and the start of their run of failures. */
    private fun queuedErrors(): List<Pair<String?, Long?>> = runBlocking {
        db.tx { SegmentMatchJobs.selectAll().map { it[SegmentMatchJobs.lastError] to it[SegmentMatchJobs.firstFailedAt] } }
    }

    /** A track job for alice's ride, failed [attempts] times since [since]: one more failure gives up after 7. */
    private fun failingTrackJob(attempts: Int = 7, since: Long = 1_000L) {
        matching(true)
        segment("3ks")
        match()
        activity("3ka")
        track("3ka")
        runBlocking {
            db.tx {
                SegmentMatchJobs.update({ SegmentMatchJobs.kind eq SegmentJobs.TRACK }) {
                    it[SegmentMatchJobs.attempts] = attempts
                    it[firstFailedAt] = since
                    it[lastError] = "IllegalStateException: earlier"
                }
            }
        }
        blobsDown = true
    }

    @Test
    fun aRetryingJobRecordsItsError() {
        matching(true)
        segment("3ks")
        match()
        activity("3ka")
        track("3ka")
        blobsDown = true
        match()
        val (error, since) = queuedErrors().single()
        assertEquals("IllegalStateException: the PDS is down", error)
        assertNotNull(since)
        assertEquals(emptyList(), failures())
    }

    @Test
    fun aJobThatGivesUpIsKeptAsAFailedJob() {
        failingTrackJob()
        match()
        assertEquals(emptyList(), jobs(), "no longer queued")
        assertEquals(
            listOf(Failure(SegmentJobs.TRACK, trackUri("3ka"), 8, "IllegalStateException: the PDS is down", 1_000L)),
            failures(),
        )
    }

    @Test
    fun aLaterSuccessOfTheSameJobClearsIt() {
        failingTrackJob()
        match()
        assertEquals(1, failures().size)

        blobsDown = false
        // Enqueued again, as a replay or a migration's rematch does.
        runBlocking { db.tx { indexer.segmentJobs.enqueueTrack(trackUri("3ka"), Clock.System.now().toEpochMilliseconds()) } }
        match()
        assertEquals(emptyList(), failures())
        assertEquals(1, efforts().size)
        assertEquals(listOf<Pair<String?, Long?>>(), queuedErrors())
    }

    @Test
    fun aJobEnqueuedAgainWhileItRanStaysQueued() {
        failingTrackJob()
        val reenqueuing =
            SegmentMatcher(
                db,
                indexer.segmentJobs,
                SpaceBlobs { _, _, _ ->
                    // The track is put again mid-fetch: a later enqueue of the same job.
                    db.tx { indexer.segmentJobs.enqueueTrack(trackUri("3ka"), Clock.System.now().toEpochMilliseconds() + 1) }
                    error("the PDS is down")
                },
            )
        runBlocking { reenqueuing.runDue() }
        assertEquals(listOf(SegmentJobs.TRACK to trackUri("3ka")), jobs())
        assertEquals(emptyList(), failures())
        assertEquals(listOf<Pair<String?, Long?>>(null to null), queuedErrors(), "enqueued afresh, with no earlier failures")
    }

    @Test
    fun aRequeuedFailedJobRunsAgainAndClearsIt() {
        failingTrackJob()
        match()
        assertEquals(1, failures().size)

        blobsDown = false
        val queue = SegmentQueue(db, indexer.segmentJobs)
        assertEquals(Requeued.REQUEUED, runBlocking { queue.requeue(SegmentJobs.TRACK, trackUri("3ka")) })
        assertEquals(emptyList(), failures(), "removed as soon as it's requeued")
        assertEquals(listOf<Pair<String?, Long?>>(null to null), queuedErrors(), "queued afresh")
        match()
        assertEquals(emptyList(), jobs())
        assertEquals(emptyList(), failures())
        assertEquals(1, efforts().size)
    }

    @Test
    fun aFailedJobsErrorDoesNotQuoteTheTrack() {
        failingTrackJob()
        val secret = "TRACK-SECRET-91c2"
        val decoding =
            SegmentMatcher(
                db,
                indexer.segmentJobs,
                SpaceBlobs { _, _, _ ->
                    // A decoder failure quoting the blob, wrapped as a lower layer might.
                    val bytes = """{"pieces":"$secret"}"""
                    try {
                        AtJson.decodeFromString(SegmentTrackData.serializer(), bytes)
                    } catch (e: SerializationException) {
                        throw IllegalStateException(e.message, e)
                    }
                    error("unreachable")
                },
            )
        runBlocking { decoding.runDue() }
        val failure = failures().single()
        assertEquals("IllegalStateException", failure.error)
        assertTrue(secret !in failure.error)
    }

    @Test
    fun anAccountsDeletionRemovesItsFailedJobs() {
        failingTrackJob()
        match()
        assertEquals(1, failures().size)
        runBlocking {
            indexer.handle(
                JetstreamEvent.Account(
                    did = alice,
                    time = "2026-10-15T12:00:00.000000Z",
                    account = JetstreamEvent.Account.Body(alice, active = false, status = "deleted"),
                ),
            )
        }
        assertEquals(emptyList(), failures())
    }

    @Test
    fun failedJobsOlderThanThirtyDaysArePruned() {
        val now = Clock.System.now()
        runBlocking {
            db.tx {
                for ((subject, at) in listOf("old" to now - 31.days, "recent" to now - 29.days)) {
                    SegmentJobFailures.insert {
                        it[kind] = SegmentJobs.SEGMENT
                        it[subjectUri] = subject
                        it[attempts] = 8
                        it[lastError] = "IOException"
                        it[firstFailedAt] = at.toEpochMilliseconds()
                        it[failedAt] = at.toEpochMilliseconds()
                    }
                }
            }
        }
        assertEquals(1, runBlocking { matcher.pruneFailedJobs() })
        assertEquals(listOf("recent"), failures().map { it.subject })
    }

    @Test
    fun eachAttemptsOutcomeIsCountedByKind() {
        failingTrackJob(attempts = 0)
        val counted = mutableListOf<Pair<String, JobOutcome>>()
        var down = true
        val counting =
            SegmentMatcher(
                db,
                indexer.segmentJobs,
                SpaceBlobs { _, _, cid ->
                    check(!down) { "the PDS is down" }
                    blobs[cid]?.let { XrpcBytes(it, "application/json") }
                },
                outcomes = SegmentJobOutcomes { kind, outcome -> counted += kind to outcome },
            )
        runBlocking { counting.runDue() }
        assertEquals(listOf(SegmentJobs.TRACK to JobOutcome.RETRY), counted)
        down = false
        runBlocking { db.tx { SegmentMatchJobs.update { it[enqueuedAt] = 0 } } }
        runBlocking { counting.runDue() }
        assertTrue(SegmentJobs.TRACK to JobOutcome.DONE in counted, "$counted")
    }

    @Test
    fun eachMatcherRoundIsReportedToItsWorker() = runBlocking {
        // A job that fails is the job's failure, retried on its own; the round itself succeeded.
        failingTrackJob(attempts = 0)
        assertEquals(RecordingWorker.OK, reportsOf { matcher.run(it) }.first())
    }
}
