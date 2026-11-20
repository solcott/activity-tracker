package io.github.solcott.activitytracker.appview.labels

import io.github.solcott.activitytracker.appview.AppViewConfig
import io.github.solcott.activitytracker.appview.ops.WorkerHandle
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

/**
 * Tails one labeler's `com.atproto.label.subscribeLabels` and hands each frame's verified labels to [store], with the frame's `seq` as the
 * resume cursor. It reconnects with backoff from the stored cursor, so a restart or disconnect loses nothing; replays are harmless because
 * the newest `cts` wins. Each labeler runs its own subscriber, so one failing stream never stops another, or indexing.
 */
@Suppress("LongParameterList") // Its collaborators, each swappable in tests.
class LabelSubscriber(
    private val labeler: AppViewConfig.Labeler,
    private val store: LabelStore,
    private val verifier: LabelVerifier,
    /** The labeler's `#atproto_labeler` endpoint when [labeler] names none; null when it can't be resolved. */
    private val endpoint: suspend (did: String) -> String?,
    /** Opens [url], calls `onOpen` once it's open and the handler with each binary message until the stream ends. */
    private val frames: suspend (url: String, onOpen: () -> Unit, onFrame: suspend (ByteArray) -> Unit) -> Unit,
    private val maxBackoff: Duration = 60.seconds,
    private val clock: Clock = Clock.System,
) {
    private val log = LoggerFactory.getLogger(LabelSubscriber::class.java)

    /** Whether its stream is open now, for the metrics (dev/worker-telemetry). */
    val connected = AtomicBoolean(false)

    /**
     * When this process last advanced the labeler's stored cursor, or began subscribing if it hasn't yet. A quiet labeler ages it too, so
     * the metrics show it beside [connected].
     */
    val cursorAdvancedAt = AtomicReference(clock.now())

    /** The labeler this subscriber tails. */
    val did: String
        get() = labeler.did

    /** Over Ktor websockets. */
    constructor(
        http: HttpClient,
        labeler: AppViewConfig.Labeler,
        store: LabelStore,
        verifier: LabelVerifier,
        endpoint: suspend (did: String) -> String?,
    ) : this(
        labeler,
        store,
        verifier,
        endpoint,
        { url, onOpen, onFrame ->
            http.webSocket(url) {
                onOpen()
                for (frame in incoming) if (frame is Frame.Binary) onFrame(frame.readBytes())
            }
        },
    )

    /** Runs until cancelled, reporting each applied frame and each failed connection to [worker]. */
    suspend fun run(worker: WorkerHandle = WorkerHandle.None) {
        var backoff = 1.seconds
        while (true) {
            runSuspendCatching { consume(worker) }
                .onSuccess { backoff = 1.seconds }
                .onFailure {
                    worker.failure(it)
                    log.warn("Labeler {} stream failed ({}); retrying in {}", labeler.did, it.message, backoff)
                }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(maxBackoff)
        }
    }

    /** One connection: from the stored cursor until the stream ends or fails. */
    suspend fun consume(worker: WorkerHandle = WorkerHandle.None) {
        val base = labeler.url ?: endpoint(labeler.did) ?: error("No #atproto_labeler endpoint for ${labeler.did}")
        val cursor = store.cursor(labeler.did)
        val url = buildString {
            append(base.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://"))
            append("/xrpc/com.atproto.label.subscribeLabels")
            // With no cursor yet, from the start: a labeler streams only new labels to a subscriber without one, and earlier labels
            // must apply too.
            append("?cursor=").append(cursor ?: 0)
        }
        log.info("Subscribing to labeler {} at {} (cursor={})", labeler.did, base, cursor)
        try {
            frames(url, { connected.set(true) }) { bytes -> handle(bytes, worker) }
        } finally {
            connected.set(false)
        }
    }

    private suspend fun handle(bytes: ByteArray, worker: WorkerHandle) {
        val frame =
            try {
                LabelFrame.decode(bytes)
            } catch (e: IllegalArgumentException) {
                log.debug("Undecodable frame from labeler {}: {}", labeler.did, e.message)
                return
            }
        when (frame) {
            is LabelFrame.Labels -> {
                val verified =
                    frame.labels.filter { label ->
                        verifier.verify(label, labeler.did).also { ok -> if (!ok) log.warn("Dropping unverified label {}", label) }
                    }
                store.apply(labeler.did, frame.seq, verified)
                cursorAdvancedAt.set(clock.now())
                worker.success()
            }
            is LabelFrame.Info -> log.info("Labeler {} says {}: {}", labeler.did, frame.name, frame.message)
            is LabelFrame.Unknown -> Unit
        }
    }

    companion object {
        /** How often expired labels are swept. */
        val SWEEP_INTERVAL = 60.minutes
    }
}
