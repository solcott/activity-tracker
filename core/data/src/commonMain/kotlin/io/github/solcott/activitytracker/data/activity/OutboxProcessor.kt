package io.github.solcott.activitytracker.data.activity

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.oauth.OAuthException
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.OutboxEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.AthleteSpaces
import io.github.solcott.activitytracker.data.toDataError
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import io.github.solcott.activitytracker.lexicon.atproto.DeleteRecord
import io.github.solcott.activitytracker.lexicon.atproto.PutRecord
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.github.solcott.dataresult.DataError
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Applies queued writes to the user's PDS in order (data/outbox), the athlete's own before a batch import's ([sendOrder]). A failure that
 * may pass ([isTransient]: no connection, a server error, the session) stops the run, and the write is sent again on the next. Being asked
 * to slow down (429) pauses the account's outbox until the PDS says ([AccountPacing]), and isn't counted as an attempt; [OutboxPacer]
 * resumes it. Any other failure means the PDS refused the write: it's kept as rejected ([OutboxEntity.rejectedAt]) until the athlete
 * retries or discards it ([RejectedChanges]), and it holds back the later writes with its rkey (an activity's other records share its
 * rkey), while the rest go on. A write with a space goes to that tier space, which is created (and announced to the AppView) first if need
 * be; its blob is uploaded the same way as a repo write's, and the space record takes it over. Writes sharing a blob
 * ([OutboxEntity.blobKey]) upload it once. Deleting from a space that doesn't exist, or a record that isn't there, is done already. The one
 * write dropped rather than rejected is an original file (`feed.activitySource`) the PDS refuses as too large. Nothing is sent once the
 * athlete left Activity Tracker on another device ([SessionClients.checkStillHere], auth/leaving), checked before a session's first send
 * and again when a write finds a space gone.
 */
@SingleIn(SessionScope::class)
@Inject
class OutboxProcessor(
    private val db: AppDatabase,
    private val clients: SessionClients,
    logger: Logger,
    private val clock: Clock = Clock.System,
) {
    private val log = logger.withTag("OutboxProcessor")
    private val pacing
        get() = clients.pacing

    /**
     * Returns the number of writes not applied, rejected and held ones included, so zero means everything was sent. With [only], applies
     * just the writes it accepts, in order, so a stuck write it skips doesn't hold them up; the count is then of those. A write held back
     * by a rejected one is skipped whether or not [only] accepts the rejected one.
     */
    suspend fun drain(only: (OutboxEntity) -> Boolean = { true }): Int = run(only).notApplied

    /**
     * [drain], also saying whether the run stopped at a failure that may pass ([DrainRun.failed]). Writes go non-bulk first, each in the
     * order queued, except that a write for a record a bulk write is still queued for stays behind it ([sendOrder], over the whole queue).
     * Nothing is sent while the PDS's pause lasts ([AccountPacing.isPaused]), and no bulk write while the athlete paused imports or less
     * than a tenth of the PDS's stated limit is left: the rest is kept for the athlete's own writes, and bulk writes wait for the limit to
     * reset. With [ownOnly], only the athlete's own writes that go before every bulk write are sent ([ActivityRepository.syncOwn]).
     *
     * Each write is claimed before it's sent ([AccountPacing.claim]): one cancelled meanwhile is skipped, and the upload queue sees its
     * rkey as sending. A sent write's upload item counts it in the same transaction as it leaves the outbox (data/upload-queue).
     */
    suspend fun run(only: (OutboxEntity) -> Boolean = { true }, ownOnly: Boolean = false): DrainRun =
        pacing.lock.withLock {
            val did = clients.did.value
            if (pacing.importsPaused.value == null) pacing.pauseImports(db.keyValue().get(UploadLog.importsPausedKey(did)) != null)
            val all = db.outbox().pending(did)
            val held = all.filter { it.rejectedAt != null }.mapTo(mutableSetOf()) { it.rkey }
            val bulkRkeys = all.filter { it.bulk }.mapTo(mutableSetOf()) { it.rkey }
            val selected = sendOrder(all).filter { only(it) && (!ownOnly || (!it.bulk && it.rkey !in bulkRkeys)) }
            if (clients.mayNotSend(clock.now(), selected)) return@withLock DrainRun(selected.size)
            var notApplied = 0
            for ((index, item) in selected.withIndex()) {
                val waits = item.rkey !in held && (item.bulk || item.rkey in bulkRkeys) && !bulkMaySend()
                // Everything left waits behind a bulk write.
                if (waits) return@withLock DrainRun(notApplied + selected.size - index)
                val pending = selected.size - index
                when (if (item.rkey in held) Failure.Held else send(item, did, pending)) {
                    null,
                    Failure.Dropped -> Unit
                    Failure.Held -> notApplied++
                    Failure.Stop -> return@withLock DrainRun(notApplied + pending)
                    Failure.StopFailed -> return@withLock DrainRun(notApplied + pending, failed = true)
                    Failure.Rejected -> {
                        held += item.rkey
                        notApplied++
                    }
                }
                if (item.blobKey != null) db.outbox().deleteUnreferencedBlobs()
            }
            DrainRun(notApplied)
        }

    /**
     * Claims [item] and sends it: null once it's sent, or cancelled before it could be claimed, else what its failure means. While it's
     * sent, the upload queue sees its rkey as sending, and can't cancel it.
     */
    private suspend fun send(item: OutboxEntity, did: String, pending: Int): Failure? {
        val claimed = pacing.claim.withLock { db.outbox().exists(item.id).also { if (it) pacing.markSending(item.rkey) } }
        if (!claimed) return null
        val failure =
            try {
                runSuspendCatching {
                    apply(item)
                    db.uploads().sent(item.id, did, item.rkey, clock.now().toEpochMilliseconds())
                }
                    .exceptionOrNull()
            } finally {
                pacing.markSending(null)
            }
        if (failure == null && item.bulk) pacing.sentBulk(clock.now())
        return failure?.let { failed(item, it, pending) }
    }

    /**
     * Why a write wasn't sent, for the run: [Failure.Held] behind a rejected one, [Failure.Stop] at a pause, [Failure.StopFailed] at a
     * failure that may pass.
     */
    private enum class Failure {
        Held,
        Dropped,
        Stop,
        StopFailed,
        Rejected,
    }

    private suspend fun failed(item: OutboxEntity, e: Throwable, pending: Int): Failure {
        val error = e.message ?: e::class.simpleName.orEmpty()
        return when {
            e is AthleteSpaces.AccountLeftException -> {
                log.i { "Outbox stopped: the athlete left Activity Tracker on another device; $pending pending" }
                clients.markLeft()
                Failure.Stop
            }
            e is XrpcException && e.error == SpaceErrors.SPACE_NOT_FOUND && clients.checkStillHere(again = true) -> {
                log.i { "Outbox stopped: a space is gone because the athlete left Activity Tracker; $pending pending" }
                Failure.Stop
            }
            item.collection == Nsid.FEED_ACTIVITY_SOURCE && e.isBlobTooLarge() -> {
                // Only the original is lost: the activity just offers no download.
                log.w(e) { "Dropping outbox write ${item.id}: the PDS refused its original file as too large" }
                db.uploads().dropped(item.id, item.did, item.rkey, clock.now().toEpochMilliseconds())
                Failure.Dropped
            }
            e is XrpcException && e.status == HTTP_TOO_MANY_REQUESTS -> {
                // Not an attempt: the PDS said when to come back.
                // A time already past (a device clock ahead of the server's, or `Retry-After: 0`) still pauses a little, so the pacer
                // waits and tries again rather than taking the queue for idle.
                val now = clock.now()
                val until = maxOf(e.retryAfter ?: e.rateLimit?.resetAt ?: (now + RATE_LIMIT_FALLBACK), now + MIN_RATE_LIMIT_PAUSE)
                log.i { "Outbox paused by the PDS until $until; $pending pending" }
                pacing.pause(until)
                Failure.Stop
            }
            e.isTransient() -> {
                log.w(e) { "Outbox write ${item.id} failed; $pending pending" }
                db.outbox().markFailed(item.id, error)
                Failure.StopFailed
            }
            else -> {
                log.w(e) { "Outbox write ${item.id} rejected; the writes behind it with its rkey wait for the athlete" }
                db.outbox().markRejected(item.id, clock.now().toEpochMilliseconds(), error)
                Failure.Rejected
            }
        }
    }

    /**
     * Whether a bulk write may go now: imports not paused by the athlete, no bulk pause, and each limit the PDS stated has a tenth left or
     * has reset since. Otherwise bulk writes wait for the latest reset among the limits running low.
     */
    private fun bulkMaySend(): Boolean {
        if (pacing.importsPaused.value == true) return false
        val now = clock.now()
        if (!pacing.isBulkPaused(now)) {
            val low =
                pacing.rateLimits.value.values.filter { limit ->
                    val reset = limit.resetAt
                    reset != null && reset > now && limit.remaining * RESERVE_FRACTION < limit.limit
                }
            low.mapNotNull { it.resetAt }
                .maxOrNull()
                ?.let { until ->
                    val left = low.joinToString { "${it.remaining} of ${it.limit}" }
                    log.i { "Bulk writes wait until $until: $left left, kept for the athlete's own writes" }
                    pacing.pauseBulk(until)
                }
        }
        return !pacing.isBulkPaused(now)
    }

    private suspend fun apply(item: OutboxEntity) {
        when (item.operation) {
            OP_PUT -> {
                val record = AtJson.parseToJsonElement(requireNotNull(item.recordJson)).jsonObject
                when {
                    item.blobKey != null && item.blobField != null -> putSharingBlob(item, record, item.blobKey, item.blobField)
                    else -> {
                        // Read one write at a time: the queue is listed without its bytes (OutboxDao.pending).
                        val blob = item.blobField?.let { db.outbox().inlineBlob(item.id) }
                        if (blob != null && item.blobField != null) {
                            val ref = clients.pds.uploadBlob(blob, item.blobMimeType ?: "application/octet-stream")
                            put(item, record.withBlob(item.blobField, ref))
                        } else {
                            put(item, record)
                        }
                    }
                }
            }

            OP_DELETE -> {
                val space = item.space?.let(::SpaceUri)
                if (space != null) {
                    clients.deleteFromSpace(space, item.collection, item.rkey)
                } else {
                    clients.pds.procedure(
                        DeleteRecord.method,
                        DeleteRecord.Input(
                            repo = clients.did,
                            collection = item.collection,
                            rkey = item.rkey,
                        ),
                    )
                }
            }

            else -> {
                error("Unknown outbox operation ${item.operation}")
            }
        }
    }

    /**
     * Puts [item] naming its shared blob: the ref a write before it saved, else a fresh upload. A saved ref the PDS no longer has (every
     * record naming it was deleted, so it reclaimed the blob) is uploaded once more. The ref is saved once the put succeeds, when the PDS
     * keeps the blob for the record.
     */
    private suspend fun putSharingBlob(item: OutboxEntity, record: JsonObject, key: String, field: String) {
        val blob = checkNotNull(db.outbox().blob(key)) { "Outbox write ${item.id} names a missing blob" }
        val saved = blob.ref?.let { AtJson.decodeFromString(BlobRef.serializer(), it) }
        var ref = saved ?: clients.pds.uploadBlob(blob.bytes, blob.mimeType)
        try {
            put(item, record.withBlob(field, ref))
        } catch (e: XrpcException) {
            if (saved == null || e.error != BLOB_NOT_FOUND) throw e
            ref = clients.pds.uploadBlob(blob.bytes, blob.mimeType)
            put(item, record.withBlob(field, ref))
        }
        if (ref != saved) db.outbox().setBlobRef(key, AtJson.encodeToString(BlobRef.serializer(), ref))
    }

    private suspend fun put(item: OutboxEntity, record: JsonObject) {
        val space = item.space?.let(::SpaceUri)
        if (space != null) {
            clients.tiers.ensure(space.skey)
            clients.spaces.putRecord(space, item.collection, item.rkey, record)
        } else {
            clients.pds.procedure(
                PutRecord.method,
                PutRecord.Input(
                    repo = clients.did,
                    collection = item.collection,
                    rkey = item.rkey,
                    record = record,
                ),
            )
        }
    }

    private fun JsonObject.withBlob(field: String, ref: BlobRef) =
        JsonObject(this + (field to AtJson.encodeToJsonElement(BlobRef.serializer(), ref)))

    /**
     * Whether this failure may pass if the write is sent again: no connection, a server error, an expired or refreshing session, a timeout
     * or being asked to slow down. Anything else is the PDS refusing the write, or a write that can't be built (data/outbox).
     */
    private fun Throwable.isTransient(): Boolean =
        when (this) {
            is XrpcException -> status >= HTTP_SERVER_ERROR || status in RETRYABLE_STATUSES
            is OAuthException -> true
            else -> toDataError() == DataError.Network
        }

    /** The PDS's answer to an upload over its blob limit (`413 PayloadTooLarge`). */
    private fun Throwable.isBlobTooLarge() = this is XrpcException && (status == HTTP_PAYLOAD_TOO_LARGE || error == PAYLOAD_TOO_LARGE)

    companion object {
        const val OP_PUT = "put"
        const val OP_DELETE = "delete"

        private const val BLOB_NOT_FOUND = "BlobNotFound"
        private const val PAYLOAD_TOO_LARGE = "PayloadTooLarge"
        private const val HTTP_PAYLOAD_TOO_LARGE = 413
        private const val HTTP_SERVER_ERROR = 500

        /** An expired session (401), a timed-out request (408) and being asked to slow down (429, which also pauses the outbox). */
        private val RETRYABLE_STATUSES = setOf(401, 408, 429)
        private const val HTTP_TOO_MANY_REQUESTS = 429

        /** How long to wait after a 429 that says nothing of when to come back. */
        private val RATE_LIMIT_FALLBACK = 1.minutes

        /** The shortest pause a 429 sets, whatever it says. */
        private val MIN_RATE_LIMIT_PAUSE = 10.seconds

        /** Bulk writes stop once less than 1 / this of the PDS's limit is left. */
        private const val RESERVE_FRACTION = 10
    }
}

/** One [OutboxProcessor.run]: the writes [notApplied], and whether it stopped at a failure that may pass, other than a rate limit. */
data class DrainRun(val notApplied: Int, val failed: Boolean = false)

/**
 * [writes] in the order they're sent (data/outbox): the athlete's own before bulk ones, each in the order queued, except that a write for a
 * record a bulk write is queued for keeps its place among the bulk writes, so one record's writes never change order.
 */
internal fun sendOrder(writes: List<OutboxEntity>): List<OutboxEntity> {
    val bulkRkeys = writes.filter { it.bulk }.mapTo(mutableSetOf()) { it.rkey }
    val (own, rest) = writes.partition { !it.bulk && it.rkey !in bulkRkeys }
    return own + rest
}

/** Deletes [collection]/[rkey] from the athlete's [space]; a space or record that isn't there counts as deleted. */
internal suspend fun SessionClients.deleteFromSpace(space: SpaceUri, collection: String, rkey: String) {
    try {
        spaces.deleteRecord(space, collection, rkey)
    } catch (e: XrpcException) {
        if (e.error != SpaceErrors.SPACE_NOT_FOUND && e.error != SpaceErrors.RECORD_NOT_FOUND) throw e
    }
}

/**
 * Whether nothing may be sent now: the PDS's pause lasts, or the athlete left Activity Tracker on another device (auth/leaving), which is
 * asked only when there's something to send.
 */
private suspend fun SessionClients.mayNotSend(now: Instant, selected: List<OutboxEntity>): Boolean =
    pacing.isPaused(now) || (selected.isNotEmpty() && checkStillHere())
