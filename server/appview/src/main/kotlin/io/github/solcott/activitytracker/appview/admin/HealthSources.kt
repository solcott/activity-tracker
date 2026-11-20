package io.github.solcott.activitytracker.appview.admin

import io.github.solcott.activitytracker.appview.StartedWorkers
import io.github.solcott.activitytracker.appview.db.LabelCursors
import io.github.solcott.activitytracker.appview.db.SpaceSync
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.ops.WorkerSupervisor
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * What the health page reads (admin/ingest-health): the workers' status in memory, the started Jetstream consumer and label subscribers,
 * and a few small queries of [db] that select identifiers, counts, times and recorded error summaries only.
 */
@Suppress("LongParameterList") // What the page reads, each swappable in tests.
class HealthSources(
    val supervisor: WorkerSupervisor,
    val workers: StartedWorkers,
    private val db: Database,
    /** How far back a Jetstream rewind may reach (`JETSTREAM_REWIND_MAX`). */
    val rewindMax: Duration,
    /** Whether the space syncer runs (it needs the signing key); without it the page says syncing is off. */
    val syncing: Boolean,
    val sizes: DatabaseSizes = DatabaseSizes.Unavailable,
    val clock: Clock = Clock.System,
) {
    /** How many spaces are synced, failing, reported missing by their host, and due now. */
    data class SpaceCounts(val synced: Long, val failing: Long, val notFound: Long, val due: Long)

    /** One failing space, by identifier: no record of it, no token or hash. */
    data class FailingSpace(
        val space: String,
        val authorityDid: String,
        val failures: Int,
        val lastError: String?,
        val lastErrorAt: Instant?,
        val nextAttemptAt: Instant,
        val syncedAt: Instant?,
    )

    /** Every count in one pass over `space_sync`. */
    suspend fun spaceCounts(): SpaceCounts = db.tx {
        val now = clock.now().toEpochMilliseconds()
        fun countOf(condition: Op<Boolean>) = Case().When(condition, intLiteral(1)).Else(intLiteral(0)).sum()
        val total = SpaceSync.space.count()
        val failing = countOf(SpaceSync.failures greater 0)
        val notFound = countOf(SpaceSync.notFoundSince.isNotNull())
        val due = countOf(SpaceSync.nextAttemptAt lessEq now)
        val row = SpaceSync.select(total, failing, notFound, due).single()
        SpaceCounts(
            synced = row[total],
            failing = row[failing]?.toLong() ?: 0,
            notFound = row[notFound]?.toLong() ?: 0,
            due = row[due]?.toLong() ?: 0,
        )
    }

    /** The [limit] spaces with the most consecutive failures, the most recently failed first among equals. */
    suspend fun failingSpaces(limit: Int = FAILING_SPACES): List<FailingSpace> = db.tx {
        SpaceSync.select(
                SpaceSync.space,
                SpaceSync.authorityDid,
                SpaceSync.failures,
                SpaceSync.lastError,
                SpaceSync.lastErrorAt,
                SpaceSync.nextAttemptAt,
                SpaceSync.syncedAt,
            )
            .where { SpaceSync.failures greater 0 }
            .orderBy(
                SpaceSync.failures to SortOrder.DESC,
                SpaceSync.lastErrorAt to SortOrder.DESC_NULLS_LAST,
                SpaceSync.space to SortOrder.ASC,
            )
            .limit(limit)
            .map {
                FailingSpace(
                    space = it[SpaceSync.space],
                    authorityDid = it[SpaceSync.authorityDid],
                    failures = it[SpaceSync.failures],
                    lastError = it[SpaceSync.lastError],
                    lastErrorAt = it[SpaceSync.lastErrorAt]?.let(Instant::fromEpochMilliseconds),
                    nextAttemptAt = Instant.fromEpochMilliseconds(it[SpaceSync.nextAttemptAt]),
                    syncedAt = it[SpaceSync.syncedAt]?.let(Instant::fromEpochMilliseconds),
                )
            }
    }

    /** The labeler [did]'s stored cursor `seq`, or null before its first. */
    suspend fun labelCursor(did: String): Long? = db.tx {
        LabelCursors.selectAll().where { LabelCursors.src eq did }.singleOrNull()?.get(LabelCursors.seq)
    }

    companion object {
        const val FAILING_SPACES = 50
    }
}
