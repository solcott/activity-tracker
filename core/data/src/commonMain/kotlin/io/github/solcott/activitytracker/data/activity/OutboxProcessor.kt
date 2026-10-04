package io.github.solcott.activitytracker.data.activity

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.OutboxEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import io.github.solcott.activitytracker.lexicon.atproto.DeleteRecord
import io.github.solcott.activitytracker.lexicon.atproto.PutRecord
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Applies queued repo writes to the user's PDS in order, stopping at the first failure. */
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

            OP_DELETE -> {
                clients.pds.procedure(
                    DeleteRecord.method,
                    DeleteRecord.Input(
                        repo = clients.did,
                        collection = item.collection,
                        rkey = item.rkey,
                    ),
                )
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
