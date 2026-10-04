package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.JetstreamCursor
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.lexicon.Nsid
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory

/**
 * Tails Jetstream's `subscribeEvents` stream filtered to our collections and feeds [Indexer]. The cursor is the last event's `witnessedAt`
 * in unix µs, which Jetstream accepts as a timestamp cursor (portable across instances, unlike its seq). It is persisted periodically; on
 * reconnect we rewind a few seconds, relying on idempotent indexing.
 */
class JetstreamConsumer(
    private val http: HttpClient,
    private val url: String,
    private val db: Database,
    private val indexer: Indexer,
) {
    private val log = LoggerFactory.getLogger(JetstreamConsumer::class.java)

    suspend fun run() {
        var backoff = 1.seconds
        while (true) {
            runSuspendCatching { consume() }
                .onSuccess { backoff = 1.seconds }
                .onFailure { log.warn("Jetstream connection failed ({}); retrying in {}", it.message, backoff) }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(60.seconds)
        }
    }

    private suspend fun consume() {
        val cursor = loadCursor()?.let { it - REWIND_US }
        val query = buildString {
            JetstreamEvent.kinds.forEach { append("kinds=").append(it).append('&') }
            Nsid.indexedCollections.forEach { append("collections=").append(it).append('&') }
            if (cursor != null) append("cursor=").append(cursor)
        }
        log.info("Connecting to Jetstream {} (cursor={})", url, cursor)
        http.webSocket("$url?$query") {
            var processed = 0
            var lastUs = cursor ?: 0L
            for (frame in incoming) {
                val event = (frame as? Frame.Text)?.let { decode(it.readText()) } ?: continue
                indexer.handle(event)
                lastUs = event.witnessedAtUs
                if (++processed % CURSOR_SAVE_INTERVAL == 0) saveCursor(lastUs)
            }
            if (processed > 0) saveCursor(lastUs)
        }
    }

    /** Null for skipped message types and undecodable frames; a terminal error frame throws to force a reconnect. */
    private fun decode(text: String): JetstreamEvent? =
        try {
            // Touch the cursor timestamp so a malformed one is dropped here rather than failing the connection.
            JetstreamEvent.decodeFrame(text)?.also { it.witnessedAtUs }
        } catch (e: JetstreamEvent.StreamError) {
            throw e
        } catch (e: IllegalArgumentException) {
            log.debug("Undecodable Jetstream event: {}", e.message)
            null
        }

    private suspend fun loadCursor(): Long? = db.tx {
        JetstreamCursor.selectAll().where { JetstreamCursor.id eq 1 }.singleOrNull()?.get(JetstreamCursor.timeUs)
    }

    private suspend fun saveCursor(timeUs: Long) = db.tx {
        JetstreamCursor.upsert {
            it[id] = 1
            it[JetstreamCursor.timeUs] = timeUs
        }
    }

    private companion object {
        const val CURSOR_SAVE_INTERVAL = 100
        const val REWIND_US = 5_000_000L
    }
}
