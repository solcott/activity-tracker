package io.github.solcott.activitytracker.data.activity

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.OutboxEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CommentRecord
import io.github.solcott.activitytracker.lexicon.app.KudosRecord
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import io.github.solcott.activitytracker.location.Gpx
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer

/**
 * Writes the signed-in athlete's records. Everything goes through the outbox so writes survive offline periods and app restarts; the
 * AppView picks them up from the firehose afterwards.
 */
@SingleIn(SessionScope::class)
@Inject
class ActivityRepository(
    private val db: AppDatabase,
    private val clients: SessionClients,
    private val outbox: OutboxProcessor,
    private val feed: FeedRepository,
    private val clock: Clock,
) {
    val pendingWrites: Flow<Int> = db.outbox().count(clients.did.value)

    private val kudosMutex = Mutex()

    /** Subject URI -> the viewer's kudos URI (null once removed), for every kudos toggled this session. */
    private val kudosState = mutableMapOf<String, AtUri?>()

    suspend fun publish(draft: ActivityDraft): AtUri {
        val rkey = Tid.next(clock)
        enqueue(
            collection = Nsid.FEED_ACTIVITY,
            rkey = rkey,
            serializer = ActivityRecord.serializer(),
            record = draft.toRecord(clock.now()),
            blob = draft.gpx?.let { PendingBlob(it, Gpx.MIME_TYPE, field = "track") },
        )
        return AtUri.of(clients.did, Nsid.FEED_ACTIVITY, rkey)
    }

    /**
     * Gives or removes the viewer's kudos. The decision is made against what this repository last wrote rather than [activity], which may
     * be a stale UI snapshot, so rapid taps alternate instead of stacking kudos. Athletes can't kudos their own activities.
     */
    suspend fun toggleKudos(activity: ActivityView) {
        if (activity.author.did == clients.did) return
        kudosMutex.withLock {
            val key = activity.uri.value
            val current = if (key in kudosState) kudosState[key] else activity.viewer?.kudos
            if (current == null) {
                val rkey = Tid.next(clock)
                val kudos = AtUri.of(clients.did, Nsid.FEED_KUDOS, rkey)
                feed.updateCached(activity.uri) { it.copy(kudosCount = it.kudosCount + 1, viewer = it.viewer?.copy(kudos = kudos)) }
                insertPut(
                    Nsid.FEED_KUDOS,
                    rkey,
                    KudosRecord.serializer(),
                    KudosRecord(StrongRef(activity.uri, activity.cid), clock.now()),
                )
                kudosState[key] = kudos
            } else {
                feed.updateCached(activity.uri) {
                    it.copy(kudosCount = (it.kudosCount - 1).coerceAtLeast(0), viewer = it.viewer?.copy(kudos = null))
                }
                db.outbox().insert(outboxEntity(OutboxProcessor.OP_DELETE, Nsid.FEED_KUDOS, requireNotNull(current.rkey), null))
                kudosState[key] = null
            }
        }
        outbox.drain()
    }

    suspend fun comment(activity: ActivityView, text: String) {
        feed.updateCached(activity.uri) { it.copy(commentCount = it.commentCount + 1) }
        enqueue(
            Nsid.FEED_COMMENT,
            Tid.next(clock),
            CommentRecord.serializer(),
            CommentRecord(StrongRef(activity.uri, activity.cid), text.trim(), clock.now()),
        )
    }

    /** Retries anything left in the outbox (e.g. on app start or reconnect). */
    suspend fun sync(): Int = outbox.drain()

    private suspend fun <T> enqueue(
        collection: String,
        rkey: String,
        serializer: KSerializer<T>,
        record: T,
        blob: PendingBlob? = null,
    ) {
        insertPut(collection, rkey, serializer, record, blob)
        outbox.drain()
    }

    private suspend fun <T> insertPut(
        collection: String,
        rkey: String,
        serializer: KSerializer<T>,
        record: T,
        blob: PendingBlob? = null,
    ) {
        val json = AtJson.encodeToString(serializer, record)
        db.outbox()
            .insert(
                outboxEntity(OutboxProcessor.OP_PUT, collection, rkey, json)
                    .copy(
                        blob = blob?.bytes,
                        blobMimeType = blob?.mimeType,
                        blobField = blob?.field,
                    )
            )
    }

    private fun outboxEntity(operation: String, collection: String, rkey: String, json: String?) =
        OutboxEntity(
            did = clients.did.value,
            operation = operation,
            collection = collection,
            rkey = rkey,
            recordJson = json,
            createdAt = clock.now().toEpochMilliseconds(),
        )
}

/** A blob uploaded before the record; its ref is written into the record's [field]. */
private data class PendingBlob(val bytes: ByteArray, val mimeType: String, val field: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PendingBlob

        if (!bytes.contentEquals(other.bytes)) return false
        if (mimeType != other.mimeType) return false
        if (field != other.field) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + field.hashCode()
        return result
    }
}
