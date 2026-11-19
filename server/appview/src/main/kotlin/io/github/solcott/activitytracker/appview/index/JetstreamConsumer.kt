package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.JetstreamCursor
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.ops.WorkerHandle
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.lexicon.Nsid
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.util.concurrent.atomic.AtomicLong
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
    private val url: String,
    private val db: Database,
    private val indexer: Indexer,
    /** Opens [url] with its query and calls the handler with each text message until the stream ends. */
    private val frames: suspend (url: String, onText: suspend (String) -> Unit) -> Unit,
) {
    /** Over Ktor websockets. */
    constructor(
        http: HttpClient,
        url: String,
        db: Database,
        indexer: Indexer,
    ) : this(
        url,
        db,
        indexer,
        { target, onText -> http.webSocket(target) { for (frame in incoming) if (frame is Frame.Text) onText(frame.readText()) } },
    )

    private val log = LoggerFactory.getLogger(JetstreamConsumer::class.java)

    /**
     * The `time_us` of the last event indexed, or 0 before the first: the Jetstream lag the metrics show is now minus this
     * (dev/worker-telemetry).
     */
    val lastEventUs = AtomicLong(0)

    /** Runs until cancelled, reporting each indexed event and each failed connection to [worker]. */
    suspend fun run(worker: WorkerHandle = WorkerHandle.None) {
        var backoff = 1.seconds
        while (true) {
            runSuspendCatching { consume(worker) }
                .onSuccess { backoff = 1.seconds }
                .onFailure {
                    worker.failure(it)
                    log.warn("Jetstream connection failed ({}); retrying in {}", it.message, backoff)
                }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(60.seconds)
        }
    }

    private suspend fun consume(worker: WorkerHandle) {
        val cursor = loadCursor()?.let { it - REWIND_US }
        val query = buildString {
            JetstreamEvent.kinds.forEach { append("kinds=").append(it).append('&') }
            Nsid.indexedCollections.forEach { append("collections=").append(it).append('&') }
            if (cursor != null) append("cursor=").append(cursor)
        }
        log.info("Connecting to Jetstream {} (cursor={})", url, cursor)
        var processed = 0
        var lastUs = cursor ?: 0L
        frames("$url?$query") { text ->
            val event = decode(text) ?: return@frames
            indexer.handle(event)
            lastUs = event.witnessedAtUs
            lastEventUs.set(lastUs)
            worker.success()
            if (++processed % CURSOR_SAVE_INTERVAL == 0) saveCursor(lastUs)
        }
        // A cancelled connection (shutdown, or a restart that rewinds the cursor) never gets here, so it can't overwrite a rewind.
        if (processed > 0) saveCursor(lastUs)
    }

    /** The stored cursor in unix µs, or null before the first save (admin/ingest-health). */
    suspend fun storedCursorUs(): Long? = loadCursor()

    /**
     * Stores [timeUs] as the cursor. Only while the consumer is stopped, as a `WorkerSupervisor.restart` step: a running connection would
     * save over it.
     */
    suspend fun storeCursor(timeUs: Long) = saveCursor(timeUs)

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

    companion object {
        private const val CURSOR_SAVE_INTERVAL = 100

        /** How far before the stored cursor each connection starts, replaying a little rather than missing anything. */
        const val REWIND_US = 5_000_000L
    }
}
