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
 * it over. Deleting from a space that doesn't exist, or a record that isn't there, is done already.
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

    /** Returns the number of writes still pending. */
    suspend fun drain(): Int = mutex.withLock {
        val pending = db.outbox().pending(clients.did.value)
        for ((index, item) in pending.withIndex()) {
            runSuspendCatching {
                apply(item)
                db.outbox().delete(item.id)
            }
                .onFailure { e ->
                    log.w(e) { "Outbox write ${item.id} failed; ${pending.size - index} pending" }
                    db.outbox().markFailed(item.id, e.message ?: e::class.simpleName.orEmpty())
                    return pending.size - index
                }
        }
        0
    }

    private suspend fun apply(item: OutboxEntity) {
        when (item.operation) {
            OP_PUT -> {
                var record = AtJson.parseToJsonElement(requireNotNull(item.recordJson)).jsonObject
                if (item.blob != null && item.blobField != null) {
                    val ref =
                        clients.pds.uploadBlob(
                            item.blob,
                            item.blobMimeType ?: "application/octet-stream",
                        )
                    record = JsonObject(record + (item.blobField to AtJson.encodeToJsonElement(BlobRef.serializer(), ref)))
                }
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

    companion object {
        const val OP_PUT = "put"
        const val OP_DELETE = "delete"
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
