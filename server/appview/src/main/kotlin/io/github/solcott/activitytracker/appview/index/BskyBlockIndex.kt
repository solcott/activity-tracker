package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.BskyBlockRecord
import kotlin.time.Clock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.slf4j.LoggerFactory

/**
 * Athletes' `app.bsky.graph.block` records, as blocks here. Jetstream sends every Bluesky block on the network, so one is kept only when
 * its author already uses this app (has an [Actors] row), and never makes them one: unlike [Indexer]'s other records, no actor is created
 * for it. Deletes go through [Indexer] by URI, which is a no-op for one dropped here.
 */
class BskyBlockIndex(private val db: Database, private val clock: Clock = Clock.System) {
    private val log = LoggerFactory.getLogger(BskyBlockIndex::class.java)

    /** [author]'s block at [rkey] in their repo, as the backfill reads it from their PDS. */
    suspend fun put(author: Did, rkey: String, json: JsonObject) = put(author, AtUri.of(author, Nsid.BSKY_GRAPH_BLOCK, rkey), json)

    /** [author]'s block at [uri], from Jetstream or the backfill. A malformed one is skipped: repos are untrusted input. */
    suspend fun put(author: Did, uri: AtUri, json: JsonObject) {
        try {
            val record = AtJson.decodeFromJsonElement(BskyBlockRecord.serializer(), json)
            db.tx {
                if (Actors.select(Actors.did).where { Actors.did eq author.value }.empty()) return@tx
                val now = clock.now().toEpochMilliseconds()
                upsertBlock(uri, Placement.REPO, author, record.subject, record.createdAt.toEpochMilliseconds(), now)
            }
        } catch (e: SerializationException) {
            // Before IllegalArgumentException, which SerializationException extends.
            log.debug("Skipping undecodable Bluesky block {}: {}", uri, e.message)
        } catch (e: IllegalArgumentException) {
            log.debug("Skipping invalid Bluesky block {}: {}", uri, e.message)
        }
    }
}
