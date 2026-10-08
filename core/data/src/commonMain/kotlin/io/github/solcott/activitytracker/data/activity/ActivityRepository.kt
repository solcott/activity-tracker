package io.github.solcott.activitytracker.data.activity

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalKudos
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
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
@Suppress("LongParameterList") // Injected collaborators.
class ActivityRepository(
    db: AppDatabase,
    private val clients: SessionClients,
    private val outbox: OutboxProcessor,
    private val writer: OutboxWriter,
    private val streams: ActivityStreamsStore,
    private val feed: FeedRepository,
    private val localKudos: LocalKudos,
    private val recentTypes: RecentActivityTypes,
    private val clock: Clock,
) {
    val pendingWrites: Flow<Int> = db.outbox().count(clients.did.value)

    private val kudosMutex = Mutex()

    suspend fun publish(draft: ActivityDraft): AtUri {
        val rkey = Tid.next(clock)
        val uri = AtUri.of(clients.did, Nsid.FEED_ACTIVITY, rkey)
        writer.put(
            collection = Nsid.FEED_ACTIVITY,
            rkey = rkey,
            serializer = ActivityRecord.serializer(),
            record = draft.toRecord(clock.now()),
            blob = draft.gpx?.let { PendingBlob(it, Gpx.MIME_TYPE, field = "track") },
        )
        draft.streams?.let { streams.put(uri, ActivityStreams(it, draft.streamsVisibility, draft.heartRateZones, draft.powerZones)) }
        outbox.drain()
        recentTypes.remember(draft.type)
        return uri
    }

    /** Changes who may see the signed-in athlete's [activity]'s streams. */
    suspend fun setStreamsVisibility(activity: AtUri, visibility: String) {
        streams.setVisibility(activity, visibility)
        outbox.drain()
    }

    /**
     * Gives or removes the viewer's kudos. The decision is made against what this repository last wrote rather than [activity], which may
     * be a stale UI snapshot, so rapid taps alternate instead of stacking kudos. Athletes can't kudos their own activities.
     */
    suspend fun toggleKudos(activity: ActivityView) {
        if (activity.author.did == clients.did) return
        kudosMutex.withLock {
            val current = localKudos.current(activity)
            if (current == null) {
                val rkey = Tid.next(clock)
                val kudos = AtUri.of(clients.did, Nsid.FEED_KUDOS, rkey)
                feed.updateCached(activity.uri) { it.copy(kudosCount = it.kudosCount + 1, viewer = it.viewer?.copy(kudos = kudos)) }
                writer.put(
                    Nsid.FEED_KUDOS,
                    rkey,
                    KudosRecord.serializer(),
                    KudosRecord(StrongRef(activity.uri, activity.cid), clock.now()),
                )
                localKudos.set(activity.uri, kudos)
            } else {
                feed.updateCached(activity.uri) {
                    it.copy(kudosCount = (it.kudosCount - 1).coerceAtLeast(0), viewer = it.viewer?.copy(kudos = null))
                }
                writer.delete(Nsid.FEED_KUDOS, requireNotNull(current.rkey))
                localKudos.set(activity.uri, null)
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
        writer.put(collection, rkey, serializer, record, blob)
        outbox.drain()
    }
}
