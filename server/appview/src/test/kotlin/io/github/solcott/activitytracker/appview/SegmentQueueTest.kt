package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.SegmentJobFailures
import io.github.solcott.activitytracker.appview.db.SegmentMatchJobs
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.segments.Box
import io.github.solcott.activitytracker.appview.segments.KindSummary
import io.github.solcott.activitytracker.appview.segments.Requeued
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.appview.segments.SegmentQueue
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/** The admin site's reads and operator operations on the segment job queue (admin/job-queues). */
class SegmentQueueTest : SegmentTestBase() {
    private val now = Instant.parse("2026-11-18T12:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = now
        }
    private val nowMs = now.toEpochMilliseconds()
    private val queue = SegmentQueue(db, indexer.segmentJobs, clock)
    private val terrainQueue = SegmentQueue(db, SegmentJobs(terrain = true), clock)

    private data class Job(val kind: String, val subject: String, val dueAt: Long, val attempts: Int, val error: String?, val since: Long?)

    private fun queued(): List<Job> = runBlocking {
        db.tx {
            SegmentMatchJobs.selectAll().map {
                Job(
                    it[SegmentMatchJobs.kind],
                    it[SegmentMatchJobs.subjectUri],
                    it[SegmentMatchJobs.enqueuedAt],
                    it[SegmentMatchJobs.attempts],
                    it[SegmentMatchJobs.lastError],
                    it[SegmentMatchJobs.firstFailedAt],
                )
            }
        }
    }

    private fun failedSubjects(kind: String? = null): List<String> = runBlocking {
        db.tx {
            SegmentJobFailures.selectAll()
                .let { q -> kind?.let { q.where { SegmentJobFailures.kind eq it } } ?: q }
                .map { it[SegmentJobFailures.subjectUri] }
        }
    }

    private fun trackSubject(i: Int) = "at://t/" + i.toString().padStart(4, '0')

    private fun clearQueue() = runBlocking { db.tx { SegmentMatchJobs.deleteAll() } }

    private fun job(kind: String, subject: String, dueAt: Long, attempts: Int = 0, since: Long? = null) = runBlocking {
        db.tx {
            SegmentMatchJobs.insert {
                it[SegmentMatchJobs.kind] = kind
                it[subjectUri] = subject
                it[enqueuedAt] = dueAt
                it[SegmentMatchJobs.attempts] = attempts
                it[lastError] = since?.let { "IllegalStateException: down" }
                it[firstFailedAt] = since
            }
        }
    }

    private fun deadLetter(kind: String, subject: String, failedAt: Long) = runBlocking {
        db.tx {
            SegmentJobFailures.insert {
                it[SegmentJobFailures.kind] = kind
                it[subjectUri] = subject
                it[attempts] = 9
                it[lastError] = "IllegalStateException: down"
                it[firstFailedAt] = failedAt - 1.hours.inWholeMilliseconds
                it[SegmentJobFailures.failedAt] = failedAt
            }
        }
    }

    // Operations on one job

    @Test
    fun retryNowKeepsAttemptsAndErrorAndMakesItDue() {
        job(SegmentJobs.ELEVATION, "at://s", dueAt = nowMs + 1.hours.inWholeMilliseconds, attempts = 3, since = 5L)
        assertTrue(runBlocking { queue.retryNow(SegmentJobs.ELEVATION, "at://s") })
        assertEquals(listOf(Job(SegmentJobs.ELEVATION, "at://s", nowMs, 3, "IllegalStateException: down", 5L)), queued())
    }

    @Test
    fun retryNowOfAJobNoLongerRetryingChangesNothing() {
        job(SegmentJobs.TRACK, "at://fresh", dueAt = nowMs + 1_000)
        assertFalse(runBlocking { queue.retryNow(SegmentJobs.TRACK, "at://fresh") }, "never failed")
        assertFalse(runBlocking { queue.retryNow(SegmentJobs.TRACK, "at://gone") })
        assertEquals(nowMs + 1_000, queued().single().dueAt)
    }

    @Test
    fun requeueLeavesOneFreshJobAndNoFailedJob() {
        deadLetter(SegmentJobs.TRACK, "at://t", nowMs - 1_000)
        job(SegmentJobs.TRACK, "at://t", dueAt = nowMs + 9_000, attempts = 2, since = 1L)
        assertEquals(Requeued.REQUEUED, runBlocking { queue.requeue(SegmentJobs.TRACK, "at://t") })
        assertEquals(listOf(Job(SegmentJobs.TRACK, "at://t", nowMs, 0, null, null)), queued())
        assertEquals(emptyList(), failedSubjects())
        assertEquals(Requeued.NOT_FOUND, runBlocking { queue.requeue(SegmentJobs.TRACK, "at://t") })
    }

    @Test
    fun elevationIsNotRequeuedWithoutTerrainTiles() {
        deadLetter(SegmentJobs.ELEVATION, "at://s", nowMs)
        assertEquals(Requeued.NO_TERRAIN, runBlocking { queue.requeue(SegmentJobs.ELEVATION, "at://s") })
        assertNull(runBlocking { queue.requeueFailed(SegmentJobs.ELEVATION) })
        assertEquals(listOf("at://s"), failedSubjects())
        assertEquals(emptyList(), queued())
        assertEquals(Requeued.REQUEUED, runBlocking { terrainQueue.requeue(SegmentJobs.ELEVATION, "at://s") })
    }

    @Test
    fun requeueingAKindTakesTheOldestFiveHundred() {
        runBlocking {
            db.tx {
                repeat(700) { i ->
                    SegmentJobFailures.insert {
                        it[kind] = SegmentJobs.TRACK
                        it[subjectUri] = trackSubject(i)
                        it[attempts] = 9
                        it[lastError] = "IOException"
                        it[firstFailedAt] = i.toLong()
                        it[failedAt] = 1_000L + i
                    }
                }
            }
        }
        deadLetter(SegmentJobs.GROUP, "at://g", 0)
        val result = runBlocking { queue.requeueFailed(SegmentJobs.TRACK) }!!
        assertEquals(500 to 200L, result.requeued to result.remaining)
        assertEquals((0 until 500).map { trackSubject(it) }.toSet(), queued().map { it.subject }.toSet())
        assertTrue(queued().all { it.attempts == 0 && it.dueAt == nowMs && it.since == null })
        assertEquals((500 until 700).map { trackSubject(it) }.toSet(), failedSubjects(SegmentJobs.TRACK).toSet())
        assertEquals(listOf("at://g"), failedSubjects(SegmentJobs.GROUP), "other kinds untouched")
    }

    @Test
    fun dropRemovesOnlyARetryingJobOrAFailedJob() {
        job(SegmentJobs.SITE, "at://site", dueAt = nowMs, attempts = 1, since = 1L)
        job(SegmentJobs.SITE, "at://fresh", dueAt = nowMs)
        deadLetter(SegmentJobs.CANDIDATE, "c1", nowMs)
        assertTrue(runBlocking { queue.dropRetrying(SegmentJobs.SITE, "at://site") })
        assertFalse(runBlocking { queue.dropRetrying(SegmentJobs.SITE, "at://fresh") }, "not retrying")
        assertFalse(runBlocking { queue.dropRetrying(SegmentJobs.SITE, "at://missing") })
        assertTrue(runBlocking { queue.dropFailed(SegmentJobs.CANDIDATE, "c1") })
        assertFalse(runBlocking { queue.dropFailed(SegmentJobs.CANDIDATE, "c1") })
        assertEquals(listOf("at://fresh"), queued().map { it.subject })
        assertEquals(emptyList(), failedSubjects())
    }

    // Reads

    @Test
    fun summaryListsEveryKind() {
        val tenMinutesAgo = nowMs - 10.minutes.inWholeMilliseconds
        job(SegmentJobs.TRACK, "at://1", dueAt = tenMinutesAgo)
        job(SegmentJobs.TRACK, "at://2", dueAt = nowMs - 1_000, attempts = 1, since = 1L)
        job(SegmentJobs.TRACK, "at://3", dueAt = nowMs + 60_000)
        deadLetter(SegmentJobs.GROUP, "at://g", nowMs)
        val summary = runBlocking { queue.summary() }
        assertEquals(SegmentJobs.KINDS, summary.map { it.kind })
        assertEquals(KindSummary(SegmentJobs.TRACK, 3, 2, tenMinutesAgo, 1, 0), summary.first { it.kind == SegmentJobs.TRACK })
        assertEquals(KindSummary(SegmentJobs.GROUP, 0, 0, null, 0, 1), summary.first { it.kind == SegmentJobs.GROUP })
        assertEquals(KindSummary(SegmentJobs.SITE, 0, 0, null, 0, 0), summary.first { it.kind == SegmentJobs.SITE })
    }

    @Test
    fun emptyQueueIsAllZeros() {
        clearQueue()
        assertEquals(SegmentJobs.KINDS.map { KindSummary(it, 0, 0, null, 0, 0) }, runBlocking { queue.summary() })
    }

    @Test
    fun retryingJobsPageOldestFailureFirstByKind() {
        // Two share a failure time, so the keyset's tie-breakers are crossed.
        for ((i, since) in listOf(30L, 10L, 20L, 20L, 40L).withIndex()) job(SegmentJobs.TRACK, "at://t$i", nowMs, 1, since)
        job(SegmentJobs.SEGMENT, "at://s", nowMs, 1, 1L)
        job(SegmentJobs.TRACK, "at://fresh", nowMs)
        val seen = mutableListOf<String>()
        var cursor: String? = null
        var pages = 0
        do {
            val page = runBlocking { queue.retrying(SegmentJobs.TRACK, cursor, limit = 2) }
            seen += page.items.map { it.subject }
            cursor = page.cursor
            pages++
        } while (cursor != null)
        assertEquals(listOf("at://t1", "at://t2", "at://t3", "at://t0", "at://t4"), seen)
        assertEquals(3, pages)
        assertEquals(listOf("at://s", "at://t1"), runBlocking { queue.retrying(null, null, limit = 2) }.items.map { it.subject })
    }

    @Test
    fun failedJobsPageMostRecentFirst() {
        for (i in 0 until 5) deadLetter(SegmentJobs.TRACK, "at://t$i", nowMs - i * 1_000L)
        deadLetter(SegmentJobs.TRACK, "at://tie", nowMs - 2_000L)
        val first = runBlocking { queue.failed(SegmentJobs.TRACK, null, limit = 3) }
        val second = runBlocking { queue.failed(SegmentJobs.TRACK, first.cursor, limit = 3) }
        val all = first.items.map { it.subject } + second.items.map { it.subject }
        assertEquals(listOf("at://t0", "at://t1", "at://tie", "at://t2", "at://t3", "at://t4"), all)
        assertNull(second.cursor)
    }

    // Bulk requeues

    /** Alice's held ride track 3ka and dive track 3kd, bob's track 3kb with matching and grouping off; an empty queue. */
    private fun heldTracks() {
        matching(true)
        activity("3ka")
        track("3ka")
        activity("3kd", sport = "dive")
        track("3kd")
        activity("3kb", did = bob)
        track("3kb", did = bob)
        clearQueue()
    }

    @Test
    fun rematchQueuesEachHeldTrackOnce() {
        heldTracks()
        job(SegmentJobs.TRACK, trackUri("3kd"), dueAt = 7L)
        assertEquals(1L, runBlocking { queue.rematchPreview(null) })
        assertEquals(1, runBlocking { queue.rematch(null) })
        assertEquals(0L, runBlocking { queue.rematchPreview(null) })
        assertEquals(0, runBlocking { queue.rematch(null) }, "run twice, nothing more")
        assertEquals(mapOf(trackUri("3ka") to nowMs, trackUri("3kd") to 7L), queued().associate { it.subject to it.dueAt })
    }

    @Test
    fun rematchOfOneSport() {
        heldTracks()
        assertEquals(1L, runBlocking { queue.rematchPreview("dive") })
        assertEquals(1, runBlocking { queue.rematch("dive") })
        assertEquals(listOf(trackUri("3kd")), queued().map { it.subject })
        assertEquals(0, runBlocking { queue.rematch("swim") }, "an unknown sport queues nothing")
    }

    @Test
    fun tracksNotHeldAreNotRematched() {
        heldTracks()
        matching(false)
        clearQueue()
        assertEquals(0, runBlocking { queue.rematch(null) })
    }

    /** Segment 3kn near the origin (47° N, 8° E), 3kf far away at the equator, and an empty queue. */
    private fun twoSegments() {
        segment("3kn")
        val far = SegmentGeometry.encodePolyline(listOf(GeoPoint(0.0, 0.0), GeoPoint(0.005, 0.0)))
        commit(alice, Nsid.FEED_SEGMENT, "3kf", segmentJson(line = far))
        clearQueue()
    }

    @Test
    fun elevationForABoxQueuesTheSegmentsTouchingIt() {
        twoSegments()
        val box = Box(7.9, 46.9, 8.1, 47.1)
        assertEquals(1L, runBlocking { terrainQueue.elevationPreview(box, onlyMissing = false) })
        assertEquals(1, runBlocking { terrainQueue.recomputeElevation(box, onlyMissing = false) })
        assertEquals(listOf(SegmentJobs.ELEVATION to segmentUri("3kn")), jobs())
        assertEquals(1, runBlocking { terrainQueue.recomputeElevation(null, onlyMissing = false) }, "the rest, once")
    }

    @Test
    fun elevationOnlyForSegmentsMissingIt() {
        twoSegments()
        runBlocking {
            db.tx {
                Segments.update({ Segments.uri eq segmentUri("3kn") }) {
                    it[terrainGainM] = 10
                    it[terrainMaxGrade] = 50
                }
            }
        }
        assertEquals(1, runBlocking { terrainQueue.recomputeElevation(null, onlyMissing = true) })
        assertEquals(listOf(segmentUri("3kf")), jobs().map { it.second })
    }

    @Test
    fun elevationNeedsTerrainTiles() {
        twoSegments()
        assertNull(runBlocking { queue.recomputeElevation(null, onlyMissing = false) })
        assertNull(runBlocking { queue.elevationPreview(null, onlyMissing = false) })
        assertEquals(emptyList(), jobs())
    }

    @Test
    fun boxesAreChecked() {
        assertTrue(Box(-73.5, 42.7, -71.4, 45.0).isValid)
        assertFalse(Box(-71.4, 42.7, -73.5, 45.0).isValid, "west east of east")
        assertFalse(Box(-73.5, 42.7, -71.4, 91.0).isValid, "past 90")
        assertFalse(Box(-181.0, 0.0, 0.0, 1.0).isValid)
        assertNull(Box.parse("1,2,3"))
        assertEquals(Box(1.0, 2.0, 3.0, 4.0), Box.parse(" 1, 2,3 ,4"))
    }
}
