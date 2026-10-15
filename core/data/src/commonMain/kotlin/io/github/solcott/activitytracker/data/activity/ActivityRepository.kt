package io.github.solcott.activitytracker.data.activity

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.commentHideRkey
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalComments
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.feed.SentComment
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.CommentHideRecord
import io.github.solcott.activitytracker.lexicon.app.CommentRecord
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.LikeRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.ReplyRef
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import io.github.solcott.activitytracker.location.Gpx
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Writes the signed-in athlete's records. Everything goes through the outbox so writes survive offline periods and app restarts; the
 * AppView picks them up from the firehose, or for a non-public activity from the athlete's tier space, afterwards. An activity is queued to
 * the place its visibility says ([placeOf]), its streams to theirs.
 */
@SingleIn(SessionScope::class)
@Inject
@Suppress("LongParameterList", "TooManyFunctions") // Injected collaborators; every kind of write the athlete makes.
class ActivityRepository(
    db: AppDatabase,
    private val clients: SessionClients,
    private val outbox: OutboxProcessor,
    private val writer: OutboxWriter,
    private val streams: ActivityStreamsStore,
    private val zones: ActivityZonesStore,
    private val feed: FeedRepository,
    private val localLikes: LocalLikes,
    private val recentTypes: RecentActivityTypes,
    private val clock: Clock,
) {
    val pendingWrites: Flow<Int> = db.outbox().count(clients.did.value)

    private val likeMutex = Mutex()
    private val hideMutex = Mutex()

    /** Publishes [draft]. Its photos upload first, so one with photos needs a connection; one without waits in the outbox if offline. */
    suspend fun publish(draft: ActivityDraft): AtUri {
        clients.requireSpacesFor(draft.visibility)
        val uri = queue(draft, draft.photos.map { clients.upload(it, feed.heldPhotos) })
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
        val uris = zones.withTrainingReadOnce { drafts.map { queue(it, photos = emptyList()) } }
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
        val photos =
            draft.photos.take((ActivityMedia.MAX - current.media.orEmpty().size).coerceAtLeast(0)).map {
                clients.upload(it, feed.heldPhotos)
            }
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
                // Zones already stored stay as they are.
                draft.heartRateZones.takeIf { have?.heartRateZones == null && have?.powerZones == null },
                draft.powerZones.takeIf { have?.heartRateZones == null && have?.powerZones == null },
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
     * a stale UI snapshot, so rapid taps alternate instead of stacking likes. Athletes can't like their own activities.
     *
     * A like on a public activity goes in the repo, and one on a non-public activity in the athlete's interactions space
     * ([interactionPlaceOf]), which needs the `space:` scope to cover `feed.like` (a session from before the declaration listed it is
     * refused up front). Unliking can't tell where the like went (its URI is the same either way, and the activity may have changed
     * visibility since), so it deletes the rkey from both; a delete of what isn't there counts as done.
     */
    suspend fun toggleLike(activity: ActivityView) {
        if (activity.author.did == clients.did) return
        val place = clients.interactionPlaceOf(activity.visibilityOrPublic)
        likeMutex.withLock {
            val current = localLikes.current(activity)
            if (current == null) {
                val rkey = Tid.next(clock)
                val like = AtUri.of(clients.did, Nsid.FEED_LIKE, rkey)
                // Refused before anything changes, so a like is never shown that can't be written.
                clients.requireSpacesFor(activity.visibilityOrPublic, Nsid.FEED_LIKE)
                feed.updateCached(activity.uri) { it.copy(likeCount = it.likeCount + 1, viewer = it.viewer?.copy(like = like)) }
                writer.put(
                    Nsid.FEED_LIKE,
                    rkey,
                    LikeRecord.serializer(),
                    LikeRecord(StrongRef(activity.uri, activity.cid), clock.now()),
                    space = place,
                )
                localLikes.set(activity.uri, like)
            } else {
                feed.updateCached(activity.uri) {
                    it.copy(likeCount = (it.likeCount - 1).coerceAtLeast(0), viewer = it.viewer?.copy(like = null))
                }
                val rkey = requireNotNull(current.rkey)
                writer.delete(Nsid.FEED_LIKE, rkey)
                // Without the scope this session can't reach the space, and the PDS would refuse the delete and hold up the outbox; another
                // session's like there stays until one with it unlikes.
                if (clients.canWriteToSpaces(Nsid.FEED_LIKE))
                    writer.delete(Nsid.FEED_LIKE, rkey, clients.tiers.uri(AthleteSpace.INTERACTIONS))
                localLikes.set(activity.uri, null)
            }
        }
        outbox.drain()
    }

    /**
     * Comments on [activity], at any visibility, or with [replyTo] replies to one of its listed comments or replies. The comment is written
     * for the visibility the view shows, narrowed for a reply to [replyTo]'s audience ([commentAudience]), and placed by that audience: in
     * the repo for public, else in the athlete's interactions space ([interactionPlaceOf]), which needs the `space:` scope to cover
     * `feed.comment`. A non-public one records its audience, so the AppView never shows it wider, even if the activity is. A reply names
     * [replyTo] as its parent and [replyTo]'s thread's top-level comment as its root. Returns the comment's URI. It's listed after the
     * loaded comments, or at the end of its thread, and counted at once ([LocalComments.sent]) until the AppView lists it.
     */
    suspend fun comment(activity: ActivityView, text: String, replyTo: CommentView? = null): AtUri {
        val audience = commentAudience(activity.visibilityOrPublic, replyTo)
        // Refused before anything changes, so a comment is never counted that can't be written.
        clients.requireSpacesFor(audience, Nsid.FEED_COMMENT)
        val rkey = Tid.next(clock)
        val uri = AtUri.of(clients.did, Nsid.FEED_COMMENT, rkey)
        val createdAt = clock.now()
        val reply = replyTo?.let { ReplyRef(root = it.root ?: it.ref, parent = it.ref) }
        val record =
            CommentRecord(
                StrongRef(activity.uri, activity.cid),
                text.trim(),
                createdAt,
                audience = audience.takeIf { it != Visibility.PUBLIC },
                reply = reply,
            )
        val sent = SentComment(uri, ProfileViewBasic(clients.did, clients.handle), record.text, createdAt, root = reply?.root?.uri)
        feed.localComments.send(activity.uri, sent)
        undoingOnFailure({ feed.localComments.restore(uri, null) }) {
            writer.put(Nsid.FEED_COMMENT, rkey, CommentRecord.serializer(), record, space = clients.interactionPlaceOf(audience))
        }
        feed.updateCached(activity.uri) { it.copy(commentCount = it.commentCount + 1) }
        outbox.drain()
        return uri
    }

    /**
     * Deletes the viewer's own comment [uri] on [activity], listed as hidden ([hidden]) or not; someone else's is left alone. As with
     * unliking, its URI is the same wherever it went and the activity may have changed visibility since, so it's deleted from the repo and
     * the interactions space, the space only when the session can write `feed.comment` there; a delete of what isn't there counts as done.
     * It leaves the list and the count at once ([LocalComments]), the count only while the AppView counts it, until the AppView drops it.
     */
    suspend fun deleteComment(activity: ActivityView, uri: AtUri, hidden: Boolean = false) {
        if (uri.authority != clients.did.value) return
        val rkey = requireNotNull(uri.rkey)
        val previous = feed.localComments.change(activity.uri, uri, LocalComments.Want.Gone, listedHidden = hidden)
        undoingOnFailure({ feed.localComments.restore(uri, previous) }) {
            writer.delete(Nsid.FEED_COMMENT, rkey)
            if (clients.canWriteToSpaces(Nsid.FEED_COMMENT))
                writer.delete(Nsid.FEED_COMMENT, rkey, clients.tiers.uri(AthleteSpace.INTERACTIONS))
        }
        // A hidden comment was never counted.
        if (!hidden) feed.updateCached(activity.uri) { it.copy(commentCount = (it.commentCount - 1).coerceAtLeast(0)) }
        outbox.drain()
    }

    /**
     * Hides ([hidden]) or unhides someone else's [comment] on the athlete's own [activity]; nothing happens if it already is, as far as
     * this session knows. A hide is a `feed.commentHide` at the rkey derived from the comment ([commentHideRkey]), placed like a like
     * ([interactionPlaceOf]), so one on a non-public activity needs the `space:` scope to cover `feed.commentHide` and is refused up front
     * without it. As with unliking, the activity may have changed visibility since the hide, so unhiding deletes that rkey from the repo
     * and the interactions space, the space only when the session can write `feed.commentHide` there; a delete of what isn't there counts
     * as done. Either shows at once ([FeedRepository.localComments]), with the count, until the AppView catches up, and is taken back if it
     * can't be queued. Returns whether anything changed.
     */
    suspend fun setCommentHidden(activity: ActivityView, comment: CommentView, hidden: Boolean): Boolean {
        if (activity.author.did != clients.did || comment.author.did == clients.did) return false
        val visibility = activity.visibilityOrPublic
        val rkey = commentHideRkey(comment.uri)
        val local = feed.localComments
        val changed = hideMutex.withLock {
            if (local.isHidden(comment.uri, comment.hidden == true) == hidden) return@withLock false
            // Refused before anything changes, so a comment is never shown hidden that can't be.
            if (hidden) clients.requireSpacesFor(visibility, Nsid.FEED_COMMENT_HIDE)
            val previous =
                local.change(activity.uri, comment.uri, LocalComments.Want.Present(hidden), listedHidden = comment.hidden == true)
            undoingOnFailure({ local.restore(comment.uri, previous) }) {
                if (hidden) {
                    val record = CommentHideRecord(comment.uri, clock.now())
                    writer.put(
                        Nsid.FEED_COMMENT_HIDE,
                        rkey,
                        CommentHideRecord.serializer(),
                        record,
                        space = clients.interactionPlaceOf(visibility),
                    )
                } else {
                    writer.delete(Nsid.FEED_COMMENT_HIDE, rkey)
                    // Without the scope the PDS would refuse the space delete and hold up the outbox; a newer session's hide there stays.
                    if (clients.canWriteToSpaces(Nsid.FEED_COMMENT_HIDE))
                        writer.delete(Nsid.FEED_COMMENT_HIDE, rkey, clients.tiers.uri(AthleteSpace.INTERACTIONS))
                }
            }
            feed.updateCached(activity.uri) { it.copy(commentCount = (it.commentCount + if (hidden) -1 else 1).coerceAtLeast(0)) }
            true
        }
        if (changed) outbox.drain()
        return changed
    }

    /** Retries anything left in the outbox (e.g. on app start or reconnect). */
    suspend fun sync(): Int = outbox.drain()

    /**
     * Once a session, moves any zones left wider than they should be and drops copies an interrupted move left behind
     * ([ActivityZonesStore.sweepOnce]), then sends the moves. Best effort, and slow for an athlete with many activities, so callers run it
     * apart from anything the athlete waits on.
     */
    suspend fun repairZones() {
        zones.sweepOnce()
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

/** Runs [block], and [undo] if it throws (cancellation included), so a change shown before a write is queued is taken back with it. */
private inline fun <T> undoingOnFailure(undo: () -> Unit, block: () -> T): T {
    var done = false
    try {
        return block().also { done = true }
    } finally {
        if (!done) undo()
    }
}
