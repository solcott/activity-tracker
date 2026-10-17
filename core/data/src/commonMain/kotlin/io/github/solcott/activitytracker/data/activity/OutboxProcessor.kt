package io.github.solcott.activitytracker.data.activity

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.OutboxEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import io.github.solcott.activitytracker.lexicon.atproto.DeleteRecord
import io.github.solcott.activitytracker.lexicon.atproto.PutRecord
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Applies queued writes to the user's PDS in order, stopping at the first failure. A write with a space goes to that tier space, which is
 * created (and announced to the AppView) first if need be; its blob is uploaded the same way as a repo write's, and the space record takes
 * it over. Writes sharing a blob ([OutboxEntity.blobKey]) upload it once. Deleting from a space that doesn't exist, or a record that isn't
 * there, is done already. The one write dropped rather than retried is an original file (`feed.activitySource`) the PDS refuses as too
 * large.
 */
@SingleIn(SessionScope::class)
@Inject
class OutboxProcessor(
    private val db: AppDatabase,
    private val clients: SessionClients,
    logger: Logger,
) {
    private val log = logger.withTag("OutboxProcessor")
    private val mutex = Mutex()

    /**
     * Returns the number of writes still pending. With [only], applies just the writes it accepts, in order, so a stuck write it skips
     * doesn't hold them up; the count is then of those.
     */
    suspend fun drain(only: (OutboxEntity) -> Boolean = { true }): Int = mutex.withLock {
        val pending = db.outbox().pending(clients.did.value).filter(only)
        for ((index, item) in pending.withIndex()) {
            runSuspendCatching {
                apply(item)
                db.outbox().delete(item.id)
            }
                .onFailure { e ->
                    if (item.collection == Nsid.FEED_ACTIVITY_SOURCE && e.isBlobTooLarge()) {
                        // Only the original is lost: the activity just offers no download. Retrying would block every write behind it.
                        log.w(e) { "Dropping outbox write ${item.id}: the PDS refused its original file as too large" }
                        db.outbox().delete(item.id)
                    } else {
                        log.w(e) { "Outbox write ${item.id} failed; ${pending.size - index} pending" }
                        db.outbox().markFailed(item.id, e.message ?: e::class.simpleName.orEmpty())
                        return pending.size - index
                    }
                }
            if (item.blobKey != null) db.outbox().deleteUnreferencedBlobs()
        }
        0
    }

    private suspend fun apply(item: OutboxEntity) {
        when (item.operation) {
            OP_PUT -> {
                val record = AtJson.parseToJsonElement(requireNotNull(item.recordJson)).jsonObject
                when {
                    item.blobKey != null && item.blobField != null -> putSharingBlob(item, record, item.blobKey, item.blobField)
                    item.blob != null && item.blobField != null -> {
                        val ref = clients.pds.uploadBlob(item.blob, item.blobMimeType ?: "application/octet-stream")
                        put(item, record.withBlob(item.blobField, ref))
                    }
                    else -> put(item, record)
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

    /** The PDS's answer to an upload over its blob limit (`413 PayloadTooLarge`). */
    private fun Throwable.isBlobTooLarge() = this is XrpcException && (status == HTTP_PAYLOAD_TOO_LARGE || error == PAYLOAD_TOO_LARGE)

    companion object {
        const val OP_PUT = "put"
        const val OP_DELETE = "delete"

        private const val BLOB_NOT_FOUND = "BlobNotFound"
        private const val PAYLOAD_TOO_LARGE = "PayloadTooLarge"
        private const val HTTP_PAYLOAD_TOO_LARGE = 413
    }
}

/** Deletes [collection]/[rkey] from the athlete's [space]; a space or record that isn't there counts as deleted. */
internal suspend fun SessionClients.deleteFromSpace(space: SpaceUri, collection: String, rkey: String) {
    try {
        spaces.deleteRecord(space, collection, rkey)
    } catch (e: XrpcException) {
        if (e.error != SpaceErrors.SPACE_NOT_FOUND && e.error != SpaceErrors.RECORD_NOT_FOUND) throw e
    }
}
