package io.github.solcott.activitytracker.appview.sync

import io.github.solcott.activitytracker.appview.api.PdsLookup
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.atproto.ListRecords
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory

/**
 * Indexes the `app.bsky.graph.block` records athletes already had before the AppView knew them, which Jetstream will never resend: each
 * actor whose `bsky_blocks_synced_at` is null (new ones, and everyone indexed before blocks existed) has their repo's blocks listed from
 * their PDS and indexed like Jetstream's, then is stamped. Later blocks arrive through Jetstream. A failing actor is retried after
 * [RETRY_AFTER], without holding up the others or indexing.
 */
class BskyBlockBackfill(
    private val db: Database,
    private val indexer: Indexer,
    private val http: HttpClient,
    private val pds: PdsLookup,
    private val clock: Clock = Clock.System,
) {
    private val log = LoggerFactory.getLogger(BskyBlockBackfill::class.java)
    private val failedAt = ConcurrentHashMap<String, Instant>()

    /** Runs until [scope] is cancelled, backfilling a batch of actors every [POLL_INTERVAL]. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                try {
                    runDue()
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    log.warn("Reading the actors due for a Bluesky block backfill failed; retrying: {}", e.message)
                }
                delay(POLL_INTERVAL)
            }
        }
    }

    /** Backfills every actor that is due, one at a time. Exposed for tests. */
    suspend fun runDue() {
        val now = clock.now()
        // Actors backing off are left out of the query, so they never crowd the others out of a batch.
        val backingOff = failedAt.filterValues { now - it < RETRY_AFTER }.keys
        val due = db.tx {
            Actors.select(Actors.did)
                .where { Actors.bskyBlocksSyncedAt.isNull() and (Actors.did notInList backingOff) }
                .limit(BATCH)
                .map { it[Actors.did] }
        }
        for (did in due) {
            try {
                backfill(Did(did))
                db.tx { Actors.update({ Actors.did eq did }) { it[bskyBlocksSyncedAt] = clock.now().toEpochMilliseconds() } }
                failedAt.remove(did)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                log.info("Backfilling {}'s Bluesky blocks failed; retrying later: {}", did, e.message)
                failedAt[did] = now
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
        const val BATCH = 50
        const val PAGE = 100
    }
}
