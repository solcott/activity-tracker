package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.appview.db.SegmentJobFailures
import io.github.solcott.activitytracker.appview.db.SegmentMatchJobs
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import kotlin.math.roundToInt
import kotlin.time.Clock
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.longLiteral
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

/** One kind's line of the queue summary: queued, due now, the oldest due job's due time, retrying, and gave up. */
data class KindSummary(val kind: String, val queued: Long, val due: Long, val oldestDueAt: Long?, val retrying: Long, val failed: Long)

/** A queued job that has failed at least once. Its error is `ErrorSummary` text, never record content. */
data class RetryingJob(
    val kind: String,
    val subject: String,
    val attempts: Int,
    val lastError: String?,
    val firstFailedAt: Long,
    val dueAt: Long,
)

/** A job that gave up after its retries. */
data class FailedJob(
    val kind: String,
    val subject: String,
    val attempts: Int,
    val lastError: String,
    val firstFailedAt: Long,
    val failedAt: Long,
)

/** A page of [items] and the cursor to the next, null at the end. */
data class QueuePage<T>(val items: List<T>, val cursor: String?)

/** A box of degrees, west to east and south to north, not crossing the antimeridian. */
data class Box(val west: Double, val south: Double, val east: Double, val north: Double) {
    /** Whether it's a box [SegmentQueue.recomputeElevation] takes: in range, with each side before the other. */
    val isValid: Boolean
        get() =
            listOf(west, south, east, north).all { it.isFinite() } &&
                west >= -MAX_LNG &&
                west < east &&
                east <= MAX_LNG &&
                south >= -MAX_LAT &&
                south < north &&
                north <= MAX_LAT

    /** Segments whose box touches this one. */
    internal fun touched(): Op<Boolean> =
        (Segments.maxLatE7 greaterEq e7(south)) and
            (Segments.minLatE7 lessEq e7(north)) and
            (Segments.maxLngE7 greaterEq e7(west)) and
            (Segments.minLngE7 lessEq e7(east))

    override fun toString() = "$west,$south,$east,$north"

    companion object {
        private const val MAX_LAT = 90.0
        private const val MAX_LNG = 180.0
        private const val E7 = 1e7
        private const val SIDES = 4

        private fun e7(degrees: Double) = (degrees * E7).roundToInt()

        /** `west,south,east,north`, or null when it isn't four numbers. */
        fun parse(text: String): Box? {
            val parts = text.split(',')
            val degrees = parts.mapNotNull { it.trim().toDoubleOrNull() }
            if (parts.size != SIDES || degrees.size != SIDES) return null
            val (west, south, east) = degrees
            return Box(west, south, east, degrees.last())
        }
    }
}

/** How a requeue of one failed job went. */
enum class Requeued {
    REQUEUED,
    NOT_FOUND,
    NO_TERRAIN,
}

/** A kind's failed jobs requeued, and how many of that kind remain. */
data class RequeuedKind(val requeued: Int, val remaining: Long)

/**
 * The admin site's view and control of the segment job queue (admin/job-queues): reads of [SegmentMatchJobs] and [SegmentJobFailures], and
 * operator operations, each one transaction through [jobs], so every write wakes the matcher. Shows jobs by kind and subject only.
 */
@Suppress("TooManyFunctions") // One per read and operator operation the queues page offers.
class SegmentQueue(private val db: Database, private val jobs: SegmentJobs, private val clock: Clock = Clock.System) {
    /** Whether terrain tiles are configured: without them, `elevation` work isn't queued. */
    val terrain: Boolean
        get() = jobs.terrain

    private fun now() = clock.now().toEpochMilliseconds()

    /** Every kind's queue, in [SegmentJobs.KINDS] order; kinds with no jobs as zeros. */
    suspend fun summary(): List<KindSummary> = db.tx {
        val now = now()
        val due = SegmentMatchJobs.enqueuedAt lessEq now
        val queued = SegmentMatchJobs.kind.count()
        val dueCount = Case().When(due, intLiteral(1)).Else(intLiteral(0)).sum()
        val retrying = Case().When(SegmentMatchJobs.firstFailedAt.isNotNull(), intLiteral(1)).Else(intLiteral(0)).sum()
        // Not due counts as never, so the minimum is the oldest due job's; no FILTER clause, which H2 lacks.
        val oldestDue = Case().When(due, SegmentMatchJobs.enqueuedAt).Else(longLiteral(NOT_DUE)).min()
        val queues =
            SegmentMatchJobs.select(SegmentMatchJobs.kind, queued, dueCount, retrying, oldestDue).groupBy(SegmentMatchJobs.kind).associate {
                it[SegmentMatchJobs.kind] to
                    KindSummary(
                        it[SegmentMatchJobs.kind],
                        queued = it[queued],
                        due = it[dueCount]?.toLong() ?: 0,
                        oldestDueAt = it[oldestDue]?.takeIf { at -> at != NOT_DUE },
                        retrying = it[retrying]?.toLong() ?: 0,
                        failed = 0,
                    )
            }
        val failedCount = SegmentJobFailures.kind.count()
        val failed =
            SegmentJobFailures.select(SegmentJobFailures.kind, failedCount).groupBy(SegmentJobFailures.kind).associate {
                it[SegmentJobFailures.kind] to it[failedCount]
            }
        (SegmentJobs.KINDS + (queues.keys + failed.keys - SegmentJobs.KINDS.toSet()).sorted()).map { kind ->
            (queues[kind] ?: KindSummary(kind, 0, 0, null, 0, 0)).copy(failed = failed[kind] ?: 0)
        }
    }

    /** Retrying jobs, of [kind] when given, oldest failure first, after the [cursor] an earlier page returned. */
    suspend fun retrying(kind: String?, cursor: String?, limit: Int = PAGE_SIZE): QueuePage<RetryingJob> = db.tx {
        var where: Op<Boolean> = SegmentMatchJobs.firstFailedAt.isNotNull()
        kind?.let { where = where and (SegmentMatchJobs.kind eq it) }
        Cursor.parse(cursor)?.let {
            where = where and it.after(SegmentMatchJobs.firstFailedAt, SegmentMatchJobs.kind, SegmentMatchJobs.subjectUri)
        }
        val rows =
            SegmentMatchJobs.selectAll()
                .where(where)
                .orderBy(
                    SegmentMatchJobs.firstFailedAt to SortOrder.ASC,
                    SegmentMatchJobs.kind to SortOrder.ASC,
                    SegmentMatchJobs.subjectUri to SortOrder.ASC,
                )
                .limit(limit + 1)
                .map {
                    RetryingJob(
                        it[SegmentMatchJobs.kind],
                        it[SegmentMatchJobs.subjectUri],
                        it[SegmentMatchJobs.attempts],
                        it[SegmentMatchJobs.lastError],
                        checkNotNull(it[SegmentMatchJobs.firstFailedAt]),
                        it[SegmentMatchJobs.enqueuedAt],
                    )
                }
        pageOf(rows, limit) { Cursor(it.firstFailedAt, it.kind, it.subject) }
    }

    /** Failed jobs, of [kind] when given, most recent first, after the [cursor] an earlier page returned. */
    suspend fun failed(kind: String?, cursor: String?, limit: Int = PAGE_SIZE): QueuePage<FailedJob> = db.tx {
        var where: Op<Boolean> = Op.TRUE
        kind?.let { where = where and (SegmentJobFailures.kind eq it) }
        Cursor.parse(cursor)?.let {
            where = where and it.before(SegmentJobFailures.failedAt, SegmentJobFailures.kind, SegmentJobFailures.subjectUri)
        }
        val rows =
            SegmentJobFailures.selectAll()
                .where(where)
                .orderBy(
                    SegmentJobFailures.failedAt to SortOrder.DESC,
                    SegmentJobFailures.kind to SortOrder.DESC,
                    SegmentJobFailures.subjectUri to SortOrder.DESC,
                )
                .limit(limit + 1)
                .map {
                    FailedJob(
                        it[SegmentJobFailures.kind],
                        it[SegmentJobFailures.subjectUri],
                        it[SegmentJobFailures.attempts],
                        it[SegmentJobFailures.lastError],
                        it[SegmentJobFailures.firstFailedAt],
                        it[SegmentJobFailures.failedAt],
                    )
                }
        pageOf(rows, limit) { Cursor(it.failedAt, it.kind, it.subject) }
    }

    /** Makes the retrying job due now; false when it's no longer retrying. */
    suspend fun retryNow(kind: String, subject: String): Boolean = db.tx { jobs.retryNow(kind, subject, now()) }

    /** Queues the failed job afresh and removes it as failed; a terrain one (`elevation`, `path-elevation`) only with terrain tiles. */
    suspend fun requeue(kind: String, subject: String): Requeued = db.tx {
        when {
            kind in SegmentJobs.TERRAIN_KINDS && !terrain -> Requeued.NO_TERRAIN
            jobs.requeue(kind, subject, now()) -> Requeued.REQUEUED
            else -> Requeued.NOT_FOUND
        }
    }

    /** Requeues the oldest [SegmentJobs.REQUEUE_LIMIT] failed jobs of [kind]; null for a terrain kind without terrain tiles. */
    suspend fun requeueFailed(kind: String): RequeuedKind? = db.tx {
        if (kind in SegmentJobs.TERRAIN_KINDS && !terrain) return@tx null
        val requeued = jobs.requeueFailed(kind, now())
        RequeuedKind(requeued, SegmentJobFailures.selectAll().where { SegmentJobFailures.kind eq kind }.count())
    }

    /** Removes the retrying job from the queue without running it; false when it isn't retrying. */
    suspend fun dropRetrying(kind: String, subject: String): Boolean = db.tx {
        SegmentMatchJobs.deleteWhere {
            (SegmentMatchJobs.kind eq kind) and (subjectUri eq subject) and SegmentMatchJobs.firstFailedAt.isNotNull()
        } > 0
    }

    /** Removes the failed job; false when there's none. */
    suspend fun dropFailed(kind: String, subject: String): Boolean = db.tx {
        SegmentJobFailures.deleteWhere { (SegmentJobFailures.kind eq kind) and (subjectUri eq subject) } > 0
    }

    /** Queues a `track` job for every held segment track not already queued, of [sport]'s activities when given; how many. */
    suspend fun rematch(sport: String?): Int = db.tx { jobs.enqueueTracks(now(), sport) }

    /** How many jobs [rematch] would queue now. */
    suspend fun rematchPreview(sport: String?): Long = db.tx {
        val tracks = jobs.unqueuedTracks(sport)
        tracks.source.selectAll().where(tracks.where).count()
    }

    /**
     * Queues an `elevation` job for every segment not already queued, or those touching [box], only those missing terrain elevation or
     * maximum grade when [onlyMissing]; how many, or null without terrain tiles.
     */
    suspend fun recomputeElevation(box: Box?, onlyMissing: Boolean): Int? =
        if (!terrain) null else db.tx { jobs.enqueueElevations(elevationScope(box, onlyMissing), now()) }

    /** How many jobs [recomputeElevation] would queue now; null without terrain tiles. */
    suspend fun elevationPreview(box: Box?, onlyMissing: Boolean): Long? =
        if (!terrain) null else db.tx { Segments.selectAll().where(jobs.unqueuedElevations(elevationScope(box, onlyMissing))).count() }

    private fun elevationScope(box: Box?, onlyMissing: Boolean): Op<Boolean> {
        var where: Op<Boolean> = box?.touched() ?: Op.TRUE
        if (onlyMissing) where = where and (Segments.terrainGainM.isNull() or Segments.terrainMaxGrade.isNull())
        return where
    }

    private fun <T> pageOf(rows: List<T>, limit: Int, cursorOf: (T) -> Cursor): QueuePage<T> {
        val items = rows.take(limit)
        return QueuePage(items, items.lastOrNull()?.takeIf { rows.size > limit }?.let { cursorOf(it).toString() })
    }

    /** A keyset position: a time, then kind and subject. Written `<time>:<kind>:<subject>`; the subject may hold colons. */
    private data class Cursor(val at: Long, val kind: String, val subject: String) {
        override fun toString() = "$at:$kind:$subject"

        /** Rows after this position in ascending order of [at], [kind], [subject]. */
        fun after(at: Column<Long?>, kind: Column<String>, subject: Column<String>): Op<Boolean> =
            (at greater this.at) or
                ((at eq this.at) and ((kind greater this.kind) or ((kind eq this.kind) and (subject greater this.subject))))

        /** Rows after this position in descending order of [at], [kind], [subject]. */
        fun before(at: Column<Long>, kind: Column<String>, subject: Column<String>): Op<Boolean> =
            (at less this.at) or ((at eq this.at) and ((kind less this.kind) or ((kind eq this.kind) and (subject less this.subject))))

        companion object {
            fun parse(text: String?): Cursor? {
                val parts = text?.split(':', limit = 3)?.takeIf { it.size == 3 } ?: return null
                return parts[0].toLongOrNull()?.let { Cursor(it, parts[1], parts[2]) }
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 50
        private const val NOT_DUE = Long.MAX_VALUE
    }
}
