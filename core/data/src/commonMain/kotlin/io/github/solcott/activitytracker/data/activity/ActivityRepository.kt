package io.github.solcott.activitytracker.data.activity

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CommentRecord
import io.github.solcott.activitytracker.lexicon.app.LikeRecord
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import io.github.solcott.activitytracker.location.Gpx
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer

/**
 * Writes the signed-in athlete's records. Everything goes through the outbox so writes survive offline periods and app restarts; the
 * AppView picks them up from the firehose, or for a non-public activity from the athlete's tier space, afterwards. An activity is queued to
 * the place its visibility says ([placeOf]), its streams to theirs.
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
    private val localLikes: LocalLikes,
    private val recentTypes: RecentActivityTypes,
    private val clock: Clock,
) {
    val pendingWrites: Flow<Int> = db.outbox().count(clients.did.value)

    private val likeMutex = Mutex()

    /** Publishes [draft]. Its photos upload first, so one with photos needs a connection; one without waits in the outbox if offline. */
    suspend fun publish(draft: ActivityDraft): AtUri {
        clients.requireSpacesFor(draft.visibility)
        val uri = queue(draft, draft.photos.map { clients.upload(it) })
        outbox.drain()
        recentTypes.remember(draft.type)
        return uri
    }

    /**
     * Publishes [drafts] in order, like [publish] but draining the outbox once at the end: a dive logbook can hold hundreds. Every draft is
     * queued before any is sent, so a failed upload leaves them all in the outbox to retry.
     */
    suspend fun publishAll(drafts: List<ActivityDraft>): List<AtUri> {
        drafts.forEach { clients.requireSpacesFor(it.visibility) }
        val uris = drafts.map { queue(it, photos = emptyList()) }
        outbox.drain()
        drafts.map { it.type }.distinct().forEach { recentTypes.remember(it) }
        return uris
    }

    /**
     * Fills the gaps in the athlete's [existing] activity from [draft], another recording of it ([ActivityMerge]): its summary fields, its
     * track, and its streams' channels. Doesn't drain the outbox, so a batch can queue several before [publishAll] or [sync] sends them.
     *
     * An activity still in the outbox ([OwnActivity.cid] null) gets only its record merged: its streams aren't readable until the AppView
     * indexes them, and writing ours would replace them.
     */
    suspend fun mergeInto(existing: OwnActivity, draft: ActivityDraft) {
        val rkey = requireNotNull(existing.uri.rkey)
        val indexed = existing.cid != null
        val place = clients.placeOf(existing.visibility)
        val current = if (indexed) clients.ownActivityRecord(rkey, existing.visibility) else existing.record
        val photos = draft.photos.take((ActivityMedia.MAX - current.media.orEmpty().size).coerceAtLeast(0)).map { clients.upload(it) }
        // The athlete's own words on the import screen fill an empty description; a file's never do.
        val merged =
            ActivityMerge.fillGaps(current, draft.toRecord(clock.now(), photos)).let {
                if (it.description == null) it.copy(description = draft.description?.takeIf(String::isNotBlank)) else it
            }
        val track = draft.gpx?.takeIf { current.track == null }?.let { PendingBlob(it, Gpx.MIME_TYPE, field = "track") }
        writer.put(Nsid.FEED_ACTIVITY, rkey, ActivityRecord.serializer(), merged, track, space = place)
        val imported = draft.streams
        // The draft's stream times count from its own start.
        val offsetSeconds = (draft.startedAt - current.startedAt).inWholeSeconds.toInt()
        if (indexed && imported != null) mergeStreams(existing, draft, imported, offsetSeconds)
        feed.rewrites.set(existing.uri, merged)
        feed.updateCached(existing.uri) { feed.rewrites.rewritten(it, merged) }
    }

    private suspend fun mergeStreams(existing: OwnActivity, draft: ActivityDraft, imported: ActivityStreamData, offsetSeconds: Int) {
        val activity = existing.uri
        val have = feed.streams(activity)
        val data =
            have?.data?.let { ActivityMerge.fillMissing(it, imported, offsetSeconds) } ?: ActivityMerge.shifted(imported, offsetSeconds)
        if (data == have?.data) return
        streams.put(
            activity,
            ActivityStreams(
                data,
                have?.visibility ?: Visibility.capped(draft.streamsVisibility, existing.visibility),
                have?.heartRateZones ?: draft.heartRateZones,
                have?.powerZones ?: draft.powerZones,
            ),
        )
    }

    /** [photos] are [draft]'s, uploaded. */
    private suspend fun queue(draft: ActivityDraft, photos: List<ActivityMedia>): AtUri {
        val rkey = Tid.next(clock)
        val uri = AtUri.of(clients.did, Nsid.FEED_ACTIVITY, rkey)
        writer.put(
            collection = Nsid.FEED_ACTIVITY,
            rkey = rkey,
            serializer = ActivityRecord.serializer(),
            record = draft.toRecord(clock.now(), photos),
            blob = draft.gpx?.let { PendingBlob(it, Gpx.MIME_TYPE, field = "track") },
            space = clients.placeOf(draft.visibility),
        )
        draft.streams?.let {
            streams.put(uri, ActivityStreams(it, draft.publishedStreamsVisibility, draft.heartRateZones, draft.powerZones))
        }
        return uri
    }

    /**
     * Changes who may see the signed-in athlete's [activity]'s streams, never wider than the activity: moves the streams record to the
     * place for the new visibility, now, rather than through the outbox.
     */
    suspend fun setStreamsVisibility(activity: ActivityView, visibility: String) {
        val to = Visibility.capped(visibility, activity.visibilityOrPublic)
        clients.requireSpacesFor(to)
        val from = feed.streams(activity.uri)?.visibility ?: return
        streams.move(activity.uri, from, to)
    }

    /**
     * Gives or removes the viewer's like. The decision is made against what this repository last wrote rather than [activity], which may be
     * a stale UI snapshot, so rapid taps alternate instead of stacking likes. Athletes can't like their own activities, and nobody can like
     * a non-public one.
     */
    suspend fun toggleLike(activity: ActivityView) {
        if (activity.author.did == clients.did || !activity.acceptsInteractions) return
        likeMutex.withLock {
            val current = localLikes.current(activity)
            if (current == null) {
                val rkey = Tid.next(clock)
                val like = AtUri.of(clients.did, Nsid.FEED_LIKE, rkey)
                feed.updateCached(activity.uri) { it.copy(likeCount = it.likeCount + 1, viewer = it.viewer?.copy(like = like)) }
                writer.put(
                    Nsid.FEED_LIKE,
                    rkey,
                    LikeRecord.serializer(),
                    LikeRecord(StrongRef(activity.uri, activity.cid), clock.now()),
                )
                localLikes.set(activity.uri, like)
            } else {
                feed.updateCached(activity.uri) {
                    it.copy(likeCount = (it.likeCount - 1).coerceAtLeast(0), viewer = it.viewer?.copy(like = null))
                }
                writer.delete(Nsid.FEED_LIKE, requireNotNull(current.rkey))
                localLikes.set(activity.uri, null)
            }
        }
        outbox.drain()
    }

    /** Comments on [activity]; nothing for a non-public one, which takes no comments. */
    suspend fun comment(activity: ActivityView, text: String) {
        if (!activity.acceptsInteractions) return
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

/**
 * The signed-in athlete's activity [rkey], stored at [visibility]'s place, as their PDS has it now rather than the AppView's copy, which
 * may lag a write.
 */
internal suspend fun SessionClients.ownActivityRecord(rkey: String, visibility: String = Visibility.PUBLIC): ActivityRecord {
    val value =
        readOwn(Nsid.FEED_ACTIVITY, rkey, placeOf(visibility))
            ?: throw XrpcException(NOT_FOUND_STATUS, "RecordNotFound", "No activity $rkey at $visibility")
    return AtJson.decodeFromJsonElement(ActivityRecord.serializer(), value)
}

private const val NOT_FOUND_STATUS = 400
