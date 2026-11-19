package io.github.solcott.activitytracker.appview.sync

import io.github.solcott.activitytracker.appview.api.PdsLookup
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.BskyBlockBackfillFailures
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.ops.ErrorSummary
import io.github.solcott.activitytracker.appview.ops.WorkerHandle
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.atproto.ListRecords
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory

/**
 * Indexes the `app.bsky.graph.block` records athletes already had before the AppView knew them, which Jetstream will never resend: each
 * actor whose `bsky_blocks_synced_at` is null (new ones, and everyone indexed before blocks existed) has their repo's blocks listed from
 * their PDS and indexed like Jetstream's, then is stamped. Later blocks arrive through Jetstream. A failing actor is retried after a pause
 * doubling from [RETRY_AFTER] to [MAX_RETRY_AFTER] with each failure, kept with its last error in `bsky_block_backfill_failures`, so a
 * restart keeps it (graph/blocks), without holding up the others or indexing.
 */
class BskyBlockBackfill(
    private val db: Database,
    private val indexer: Indexer,
    private val http: HttpClient,
    private val pds: PdsLookup,
    private val clock: Clock = Clock.System,
) {
    private val log = LoggerFactory.getLogger(BskyBlockBackfill::class.java)

    /** Runs until cancelled, backfilling a batch of actors every [POLL_INTERVAL] and reporting each round to [worker]. */
    suspend fun run(worker: WorkerHandle = WorkerHandle.None): Nothing {
        while (true) {
            try {
                runDue()
                worker.success()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                worker.failure(e)
                log.warn("Reading the actors due for a Bluesky block backfill failed; retrying: {}", e.message)
            }
            delay(POLL_INTERVAL)
        }
    }

    /** Backfills every actor that is due, one at a time. Exposed for tests. */
    suspend fun runDue() {
        val now = clock.now().toEpochMilliseconds()
        // Actors backing off are left out of the query, so they never crowd the others out of a batch.
        val due = db.tx {
            Actors.join(BskyBlockBackfillFailures, JoinType.LEFT, Actors.did, BskyBlockBackfillFailures.did)
                .select(Actors.did, BskyBlockBackfillFailures.failures)
                .where {
                    Actors.bskyBlocksSyncedAt.isNull() and
                        (BskyBlockBackfillFailures.nextAttemptAt.isNull() or (BskyBlockBackfillFailures.nextAttemptAt lessEq now))
                }
                .limit(BATCH)
                .map { it[Actors.did] to (it.getOrNull(BskyBlockBackfillFailures.failures) ?: 0) }
        }
        for ((did, failures) in due) {
            try {
                backfill(Did(did))
                db.tx {
                    Actors.update({ Actors.did eq did }) { it[bskyBlocksSyncedAt] = clock.now().toEpochMilliseconds() }
                    BskyBlockBackfillFailures.deleteWhere { BskyBlockBackfillFailures.did eq did }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                val pause = pauseAfter(failures + 1)
                log.info("Backfilling {}'s Bluesky blocks failed; retrying in {}: {}", did, pause, e.message)
                db.tx {
                    BskyBlockBackfillFailures.upsert {
                        it[BskyBlockBackfillFailures.did] = did
                        it[BskyBlockBackfillFailures.failures] = failures + 1
                        it[lastError] = ErrorSummary.of(e)
                        it[nextAttemptAt] = (clock.now() + pause).toEpochMilliseconds()
                    }
                }
            }
        }
    }

    private suspend fun backfill(did: Did) {
        val host = pds.pdsFor(did) ?: error("no PDS for $did")
        var cursor: String? = null
        do {
            val response =
                http.get("$host/xrpc/${ListRecords.method.nsid}") {
                    parameter("repo", did.value)
                    parameter("collection", Nsid.BSKY_GRAPH_BLOCK)
                    parameter("limit", PAGE)
                    cursor?.let { parameter("cursor", it) }
                }
            check(response.status == HttpStatusCode.OK) { "listRecords answered ${response.status}" }
            val page = AtJson.decodeFromString(ListRecords.Output.serializer(), response.bodyAsText())
            page.records.forEach { record -> record.uri.rkey?.let { indexer.bskyBlocks.put(did, it, record.value) } }
            cursor = page.cursor?.takeIf { page.records.isNotEmpty() }
        } while (cursor != null)
    }

    private companion object {
        val POLL_INTERVAL = 1.minutes
        val RETRY_AFTER = 10.minutes
        val MAX_RETRY_AFTER = 1.days
        const val BATCH = 50
        const val MAX_DOUBLINGS = 20

        /** The pause after an actor's [failures]th failure in a row: [RETRY_AFTER], doubling each time, at most [MAX_RETRY_AFTER]. */
        fun pauseAfter(failures: Int) = minOf(RETRY_AFTER * (1L shl minOf(failures - 1, MAX_DOUBLINGS)).toDouble(), MAX_RETRY_AFTER)

        const val PAGE = 100
    }
}
