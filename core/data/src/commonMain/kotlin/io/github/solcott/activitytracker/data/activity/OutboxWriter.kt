package io.github.solcott.activitytracker.data.activity

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.OutboxBlobEntity
import io.github.solcott.activitytracker.data.db.OutboxEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer

/**
 * Queues writes in the outbox, to the repo or (with a [SpaceUri]) one of the athlete's tier spaces; [OutboxProcessor] applies them in
 * order.
 */
@SingleIn(SessionScope::class)
@Inject
class OutboxWriter(private val db: AppDatabase, private val clients: SessionClients, private val clock: Clock) {
    @Suppress("LongParameterList") // The record, and where it goes.
    suspend fun <T> put(
        collection: String,
        rkey: String,
        serializer: KSerializer<T>,
        record: T,
        blob: PendingBlob? = null,
        space: SpaceUri? = null,
    ) {
        val json = AtJson.encodeToString(serializer, record)
        val row = entity(OutboxProcessor.OP_PUT, collection, rkey, json, space)
        val key = blob?.key
        if (key != null) {
            db.outbox()
                .insert(
                    row.copy(blobKey = key, blobMimeType = blob.mimeType, blobField = blob.field),
                    OutboxBlobEntity(key = key, did = clients.did.value, bytes = blob.bytes, mimeType = blob.mimeType),
                )
        } else {
            db.outbox().insert(row.copy(blob = blob?.bytes, blobMimeType = blob?.mimeType, blobField = blob?.field))
        }
    }

    suspend fun delete(collection: String, rkey: String, space: SpaceUri? = null) {
        db.outbox().insert(entity(OutboxProcessor.OP_DELETE, collection, rkey, null, space))
    }

    private suspend fun entity(operation: String, collection: String, rkey: String, json: String?, space: SpaceUri?) =
        OutboxEntity(
            did = clients.did.value,
            operation = operation,
            collection = collection,
            rkey = rkey,
            recordJson = json,
            createdAt = clock.now().toEpochMilliseconds(),
            space = space?.value,
            bulk = currentCoroutineContext()[BulkWrites] != null,
        )
}

/**
 * Marks every write queued in its scope as bulk (data/outbox): a batch import's, sent after the athlete's own writes and only while the
 * PDS's limit has room to spare. [ActivityRepository.publishBatch] runs under it, and so does anything a batch save queues within it,
 * combines included.
 */
object BulkWrites : CoroutineContext.Element, CoroutineContext.Key<BulkWrites> {
    override val key: CoroutineContext.Key<*>
        get() = this

    suspend fun <T> run(block: suspend () -> T): T = withContext(BulkWrites) { block() }
}

/**
 * A blob uploaded before the record; its ref is written into the record's [field]. Writes sharing a [key] share one stored copy, uploaded
 * once (a logbook's original file, named by each of its dives' sources); without one the bytes are stored on the write.
 */
class PendingBlob(val bytes: ByteArray, val mimeType: String, val field: String, val key: String? = null)
