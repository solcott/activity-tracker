package io.github.solcott.activitytracker.data.activity

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.commentHideRkey
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.UploadItemEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.CachedLike
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.InteractionSubject
import io.github.solcott.activitytracker.data.feed.LocalComments
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.feed.SentComment
import io.github.solcott.activitytracker.data.feed.asSubject
import io.github.solcott.activitytracker.data.outcomeFlow
import io.github.solcott.activitytracker.data.segments.SegmentTrackSweeper
import io.github.solcott.activitytracker.data.segments.SegmentTracks
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.ActivityDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.CommentHideRecord
import io.github.solcott.activitytracker.lexicon.app.CommentRecord
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.LikeRecord
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.PostViewerState
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.ReplyRef
import io.github.solcott.activitytracker.lexicon.app.RepostRecord
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.dataresult.Outcome
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Writes the signed-in athlete's records. Everything goes through the outbox so writes survive offline periods and app restarts; the
 * AppView picks them up from the firehose, or for a non-public activity from the athlete's tier space, afterwards. An activity is queued to
 * the place its visibility says ([placeOf]), split by its detail categories ([ActivityLayout]): fields and channels of a narrower category
 * go to that tier's details and streams records. Its full track and imported file go to the athlete's `self` space ([ActivityTrackStore],
 * [ActivitySourceStore]).
 */
@SingleIn(SessionScope::class)
@Inject
@Suppress("LongParameterList", "TooManyFunctions") // Injected collaborators; every kind of write the athlete makes.
class ActivityRepository(
    private val db: AppDatabase,
    private val clients: SessionClients,
    private val outbox: OutboxProcessor,
    private val writer: OutboxWriter,
    private val streams: ActivityStreamsStore,
    private val tracks: ActivityTrackStore,
    private val sources: ActivitySourceStore,
    private val devices: ActivityDevicesStore,
    private val zones: ActivityZonesStore,
    private val places: ActivityPlaces,
    private val privacy: PrivacyZones,
    private val routes: RouteTrimmer,
    private val feed: FeedRepository,
    private val localLikes: LocalLikes,
    private val recentTypes: RecentActivityTypes,
    private val clock: Clock,
    private val segmentTracks: SegmentTracks,
    private val segmentSweeper: SegmentTrackSweeper,
) {
    /** Writes still to upload: not rejected, and not held back by a rejected write (data/outbox). */
    val pendingWrites: Flow<Int> = db.outbox().pendingCount(clients.did.value)

    private val likeMutex = Mutex()
    private val hideMutex = Mutex()

    /** Publishes [draft]. Its photos upload first, so one with photos needs a connection; one without waits in the outbox if offline. */
    suspend fun publish(draft: ActivityDraft): AtUri {
        clients.requireSpacesFor(draft.visibility)
        clients.requireDetailsSpaceFor(listOf(draft))
        requireSelfSpaceFor(listOf(draft))
        val photos = draft.photos.map { clients.upload(it, feed.heldPhotos) }
        val uri = queue(draft, photos, privacy.forPublish())
        outbox.drain()
        if (privacy.hasStale()) routes.trimStale()
        recentTypes.remember(draft.type)
        return uri
    }

    /**
     * Publishes [drafts] in order, like [publish] but draining the outbox once at the end: a dive logbook can hold hundreds. All are
     * checked before any is queued, and every draft is queued before any is sent, so a failed upload leaves them all in the outbox to
     * retry. They're the athlete's own writes, not bulk ones, unless the caller runs under [BulkWrites] (a batch import's combines).
     */
    suspend fun publishAll(drafts: List<ActivityDraft>): List<AtUri> {
        drafts.forEach { clients.requireSpacesFor(it.visibility) }
        clients.requireDetailsSpaceFor(drafts)
        requireSelfSpaceFor(drafts)
        val uris = queueEach(drafts.asFlow())
        outbox.drain()
        return uris
    }

    /**
     * Publishes [drafts] as they arrive, like [publishAll] with one read of the zones and training for them all, but queueing each before
     * the next is built, so a batch of thousands is never held at once. Each is checked as it comes: one this session can't write throws,
     * those before it staying queued. [onQueued] hears each draft's URI. Every write is bulk ([BulkWrites]), sent after the athlete's own.
     * The outbox drains after the last when [drain]; otherwise the session's [OutboxPacer] sends them, so saving a batch needn't wait for
     * its upload.
     */
    suspend fun publishBatch(
        drafts: Flow<ActivityDraft>,
        drain: Boolean = true,
        onQueued: suspend (ActivityDraft, AtUri) -> Unit = { _, _ -> },
    ): List<AtUri> {
        val uris = BulkWrites.run { queueEach(drafts, onQueued) }
        if (drain) outbox.drain()
        return uris
    }

    /**
     * Lists the import [batchId] from [source], of [total] selected activities, in the upload queue, as its save starts
     * (feed/batch-import).
     */
    suspend fun startImport(batchId: String, source: UploadSource, total: Int = 0) {
        UploadLog.batch(db, clients.did.value, batchId, source.encode(), total, clock.now())
    }

    /** Lists a file of the import [batchId] that couldn't be read again at save, by its [title] and [sport] if known. */
    suspend fun skippedImport(batchId: String, title: String?, sport: String?, startedAt: Instant?) {
        UploadLog.skipped(db, clients.did.value, batchId, title, sport, startedAt, clock.now())
    }

    /** The import [batchId] saved [saved] activities and skipped [skipped]. */
    suspend fun finishImport(batchId: String, saved: Int, skipped: Int) {
        UploadLog.counted(db, batchId, saved, skipped)
    }

    /** Queues [drafts] as they arrive, checking each, with one read of the zones and training for them all. */
    private suspend fun queueEach(
        drafts: Flow<ActivityDraft>,
        onQueued: suspend (ActivityDraft, AtUri) -> Unit = { _, _ -> },
    ): List<AtUri> {
        val uris = mutableListOf<AtUri>()
        val types = mutableSetOf<ActivityType>()
        try {
            privacy.withReadOnce {
                zones.withTrainingReadOnce {
                    drafts.collect { draft ->
                        clients.requireSpacesFor(draft.visibility)
                        clients.requireDetailsSpaceFor(listOf(draft))
                        requireSelfSpaceFor(listOf(draft))
                        val uri = queue(draft, photos = emptyList(), trim = privacy.forPublish())
                        uris += uri
                        types += draft.type
                        onQueued(draft, uri)
                    }
                }
            }
        } finally {
            types.forEach { recentTypes.remember(it) }
        }
        return uris
    }

    /** Refuses a session that can't write the full tracks or original files of [drafts] to the `self` space, before anything is queued. */
    private fun requireSelfSpaceFor(drafts: List<ActivityDraft>) {
        if (drafts.any { it.gpx != null }) clients.requireTrackSpace()
        if (drafts.any { it.source != null }) clients.requireSourceSpace()
    }

    /**
     * [photos] are [draft]'s, uploaded; its route is trimmed by [trim] for the record's polyline. Split by its categories
     * ([ActivityLayout]): its details records are queued narrowest first, then the activity record, then its streams, narrowest first.
     */
    private suspend fun queue(draft: ActivityDraft, photos: List<ActivityMedia>, trim: PublishTrim): AtUri {
        val rkey = draft.rkey ?: Tid.next(clock)
        val uri = AtUri.of(clients.did, Nsid.FEED_ACTIVITY, rkey)
        val layout =
            ActivityLayout.split(
                draft.toRecord(clock.now(), photos, trim.trim.polylineOf(draft.route)),
                null,
                uri,
                draft.visibility,
                draft.categories,
            )
        // Queued under the claim (data/upload-queue): a cancel lands before the item or after its last write, never between, and no write
        // of it is sent, so its item can't finish, before all are queued.
        clients.pacing.claim.withLock {
            // Logged before its first write, so the processor counts every write it sends (data/upload-queue).
            UploadLog.queued(db, clients.did.value, rkey, UploadItemEntity.KIND_NEW, layout.record, draft.importBatch, clock.now())
            for (tier in layout.tiersNarrowestFirst) {
                val details = layout.details[tier] ?: continue
                writer.put(Nsid.FEED_ACTIVITY_DETAILS, rkey, ActivityDetailsRecord.serializer(), details, space = clients.placeOf(tier))
            }
            writer.put(
                collection = Nsid.FEED_ACTIVITY,
                rkey = rkey,
                serializer = ActivityRecord.serializer(),
                record = layout.record,
                space = clients.placeOf(draft.visibility),
            )
            val mapTier = DetailCategory.tiers(draft.visibility, draft.categories).getValue(DetailCategory.MAP)
            val published = privacy.published(uri, draft.visibility, mapTier, draft.route, trim)
            draft.gpx?.let {
                tracks.put(uri, it)
                // A trackless dive's segment track is its surface fixes, its entry first: one is enough to visit a dive site.
                if (draft.route.isEmpty() && draft.surfaceFixes.isNotEmpty())
                    segmentTracks.putSurfaceFixes(uri, draft.surfaceFixes, published)
                else segmentTracks.put(uri, draft.route, published)
            }
            draft.streams?.let {
                streams.put(
                    uri,
                    ActivityStreams(it, draft.visibility, draft.categories, draft.heartRateZones, draft.powerZones, draft.trainingLoad),
                )
            }
            draft.source?.let { sources.put(uri, it) }
            draft.devices?.let { devices.put(uri, it) }
        }
        return uri
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
    suspend fun toggleLike(activity: ActivityView) = toggleLike(activity.asSubject())

    /** [toggleLike] on any [subject], an activity's or a post's (feed/posts), placed and shown the same way. */
    suspend fun toggleLike(subject: InteractionSubject) {
        if (subject.author == clients.did) return
        val place = clients.interactionPlaceOf(subject.visibility)
        likeMutex.withLock {
            val current = localLikes.current(subject)
            if (current == null) {
                val rkey = Tid.next(clock)
                val like = AtUri.of(clients.did, Nsid.FEED_LIKE, rkey)
                // Refused before anything changes, so a like is never shown that can't be written.
                clients.requireSpacesFor(subject.visibility, Nsid.FEED_LIKE)
                feed.updateCachedCounts(subject.uri, likes = 1, like = CachedLike.Set(like))
                writer.put(
                    Nsid.FEED_LIKE,
                    rkey,
                    LikeRecord.serializer(),
                    LikeRecord(StrongRef(subject.uri, subject.cid), clock.now()),
                    space = place,
                )
                localLikes.set(subject.uri, like)
            } else {
                feed.updateCachedCounts(subject.uri, likes = -1, like = CachedLike.Set(null))
                val rkey = requireNotNull(current.rkey)
                writer.delete(Nsid.FEED_LIKE, rkey)
                // Without the scope this session can't reach the space, and the PDS would refuse the delete and hold up the outbox; another
                // session's like there stays until one with it unlikes.
                if (clients.canWriteToSpaces(Nsid.FEED_LIKE))
                    writer.delete(Nsid.FEED_LIKE, rkey, clients.tiers.uri(AthleteSpace.INTERACTIONS))
                localLikes.set(subject.uri, null)
            }
        }
        outbox.drain()
    }

    /**
     * Reposts someone else's [post] (feed/reposts), or undoes the viewer's repost of it, deciding against what this repository last wrote
     * rather than [post], which may be a stale UI snapshot, as [toggleLike] does. A repost of a public post goes in the repo, and of any
     * other in the athlete's interactions space ([interactionPlaceOf]), refused up front without the `space:` scope covering `feed.repost`.
     * Undoing can't tell where the repost went, so it deletes the rkey from both places.
     */
    suspend fun toggleRepost(post: PostView) {
        if (post.author.did == clients.did) return
        val place = clients.interactionPlaceOf(post.visibilityOrPublic)
        val reposts = feed.localReposts
        likeMutex.withLock {
            val current = reposts.current(post)
            if (current == null) {
                val rkey = Tid.next(clock)
                val repost = AtUri.of(clients.did, Nsid.FEED_REPOST, rkey)
                // Refused before anything changes, so a repost is never shown that can't be written.
                clients.requireSpacesFor(post.visibilityOrPublic, Nsid.FEED_REPOST)
                feed.updateCachedPost(post.uri) { it.withRepost(repost) }
                writer.put(
                    Nsid.FEED_REPOST,
                    rkey,
                    RepostRecord.serializer(),
                    RepostRecord(StrongRef(post.uri, post.cid), clock.now()),
                    space = place,
                )
                reposts.set(post.uri, repost)
            } else {
                feed.updateCachedPost(post.uri) { it.withRepost(null) }
                val rkey = requireNotNull(current.rkey)
                writer.delete(Nsid.FEED_REPOST, rkey)
                // As for unliking: without the scope this session can't reach the space, so its copy stays until a session with it undoes.
                if (clients.canWriteToSpaces(Nsid.FEED_REPOST))
                    writer.delete(Nsid.FEED_REPOST, rkey, clients.tiers.uri(AthleteSpace.INTERACTIONS))
                reposts.set(post.uri, null)
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
    suspend fun comment(activity: ActivityView, text: String, replyTo: CommentView? = null): AtUri =
        comment(activity.asSubject(), text, replyTo)

    /** [comment] on any [subject], an activity's or a post's (feed/posts), its audience from the visibility its view showed. */
    suspend fun comment(subject: InteractionSubject, text: String, replyTo: CommentView? = null): AtUri {
        val audience = commentAudience(subject.visibility, replyTo)
        // Refused before anything changes, so a comment is never counted that can't be written.
        clients.requireSpacesFor(audience, Nsid.FEED_COMMENT)
        val rkey = Tid.next(clock)
        val uri = AtUri.of(clients.did, Nsid.FEED_COMMENT, rkey)
        val createdAt = clock.now()
        val reply = replyTo?.let { ReplyRef(root = it.root ?: it.ref, parent = it.ref) }
        val record =
            CommentRecord(
                StrongRef(subject.uri, subject.cid),
                text.trim(),
                createdAt,
                audience = audience.takeIf { it != Visibility.PUBLIC },
                reply = reply,
            )
        val sent = SentComment(uri, ProfileViewBasic(clients.did, clients.handle), record.text, createdAt, root = reply?.root?.uri)
        feed.localComments.send(subject.uri, sent)
        undoingOnFailure({ feed.localComments.restore(uri, null) }) {
            writer.put(Nsid.FEED_COMMENT, rkey, CommentRecord.serializer(), record, space = clients.interactionPlaceOf(audience))
        }
        feed.updateCachedCounts(subject.uri, comments = 1)
        outbox.drain()
        return uri
    }

    /**
     * Deletes the viewer's own comment [uri] on [activity], listed as hidden ([hidden]) or not; someone else's is left alone. As with
     * unliking, its URI is the same wherever it went and the activity may have changed visibility since, so it's deleted from the repo and
     * the interactions space, the space only when the session can write `feed.comment` there; a delete of what isn't there counts as done.
     * It leaves the list and the count at once ([LocalComments]), the count only while the AppView counts it, until the AppView drops it.
     */
    suspend fun deleteComment(activity: ActivityView, uri: AtUri, hidden: Boolean = false) =
        deleteComment(activity.asSubject(), uri, hidden)

    /** [deleteComment] on any [subject], an activity's or a post's (feed/posts). */
    suspend fun deleteComment(subject: InteractionSubject, uri: AtUri, hidden: Boolean = false) {
        if (uri.authority != clients.did.value) return
        val rkey = requireNotNull(uri.rkey)
        val previous = feed.localComments.change(subject.uri, uri, LocalComments.Want.Gone, listedHidden = hidden)
        undoingOnFailure({ feed.localComments.restore(uri, previous) }) {
            writer.delete(Nsid.FEED_COMMENT, rkey)
            if (clients.canWriteToSpaces(Nsid.FEED_COMMENT))
                writer.delete(Nsid.FEED_COMMENT, rkey, clients.tiers.uri(AthleteSpace.INTERACTIONS))
        }
        // A hidden comment was never counted.
        if (!hidden) feed.updateCachedCounts(subject.uri, comments = -1)
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
    suspend fun setCommentHidden(activity: ActivityView, comment: CommentView, hidden: Boolean): Boolean =
        setCommentHidden(activity.asSubject(), comment, hidden)

    /**
     * [setCommentHidden] on any [subject] of the viewer's own, an activity or a post (feed/posts): the hide goes where their like would.
     */
    suspend fun setCommentHidden(subject: InteractionSubject, comment: CommentView, hidden: Boolean): Boolean {
        if (subject.author != clients.did || comment.author.did == clients.did) return false
        val visibility = subject.visibility
        val rkey = commentHideRkey(comment.uri)
        val local = feed.localComments
        val changed = hideMutex.withLock {
            if (local.isHidden(comment.uri, comment.hidden == true) == hidden) return@withLock false
            // Refused before anything changes, so a comment is never shown hidden that can't be.
            if (hidden) clients.requireSpacesFor(visibility, Nsid.FEED_COMMENT_HIDE)
            val previous = local.change(subject.uri, comment.uri, LocalComments.Want.Present(hidden), listedHidden = comment.hidden == true)
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
            feed.updateCachedCounts(subject.uri, comments = if (hidden) -1 else 1)
            true
        }
        if (changed) outbox.drain()
        return changed
    }

    /**
     * Retries anything left in the outbox (e.g. on app start or reconnect), first trimming again any route this session published without
     * reading the zones ([RouteTrimmer.trimStale]).
     */
    suspend fun sync(): Int = syncRun().notApplied

    /**
     * [sync] for the athlete's own writes only: those that go before every bulk write ([OutboxProcessor.run] `ownOnly`), so the feed loads
     * without waiting for an import to upload (data/outbox). The pacer sends the rest.
     */
    suspend fun syncOwn(): Int {
        routes.trimStale()
        return outbox.run(ownOnly = true).notApplied
    }

    /** [sync], also saying whether the drain stopped at a failure that may pass ([DrainRun.failed]), for [OutboxPacer]. */
    suspend fun syncRun(): DrainRun {
        routes.trimStale()
        if (segmentTracks.takeStale()) segmentSweeper.sweep()
        return outbox.run()
    }

    /**
     * Once the athlete's zones change, on any device, re-trims every activity's polyline from its track ([RouteTrimmer.sweep]) and sends
     * the rewrites. Best effort and slow for an athlete with many activities, so callers run it apart from anything the athlete waits on.
     */
    suspend fun repairRoutes() {
        routes.sweep()
        segmentSweeper.sweep()
    }

    /** The signed-in athlete's own [activity]'s full route, from its track in `self`; null when it has none or it can't be read now. */
    suspend fun ownRoute(activity: AtUri): List<LocationSample>? = tracks.read(activity)

    /**
     * [ownRoute] as a screen loads it: loading, then the route, null when the activity has none, or why it couldn't be read, so a track
     * that can't be read now isn't taken for none.
     */
    fun ownRouteOutcome(activity: AtUri): Flow<Outcome<List<LocationSample>?>> = outcomeFlow { tracks.readOrThrow(activity) }

    /**
     * The athlete's privacy settings as last read or saved this session, read first when none has been, so the owner's map can mark where
     * the route others see starts and ends ([PrivacySettings.shownEnds]); null while they can't be read.
     */
    fun privacySettings(): Flow<PrivacySettings?> = flow {
        if (privacy.current.value == null) privacy.forPublish()
        emitAll(privacy.current)
    }

    /**
     * Adds a privacy zone of [HIDE_ROUTE_END_METERS] at [route]'s first point ([start]) or its last, offset as every new zone is. Its
     * activities are trimmed again by [repairRoutesOnSave]. Throws as [PrivacyZones.add] does.
     */
    suspend fun hideRouteEnd(route: List<LocationSample>, start: Boolean) {
        val point = if (start) route.first() else route.last()
        privacy.add(point.latitude, point.longitude, HIDE_ROUTE_END_METERS)
    }

    /**
     * Re-trims polylines and segment tracks after every save of the zones this session ([PrivacyZones.saved]), and writes or deletes
     * segment tracks after every save of the segment matching setting ([SegmentTrackSweeper.sweepOnSave]), a newer save replacing a sweep
     * still running, for as long as it's collected: the session, so leaving the settings screen doesn't stop a sweep.
     */
    suspend fun repairRoutesOnSave(): Unit = coroutineScope {
        launch {
            privacy.saved.filterNotNull().collectLatest {
                routes.sweep()
                segmentSweeper.sweep()
            }
        }
        launch { segmentSweeper.sweepOnSave() }
    }

    /**
     * Once a session, finishes any split activity an interrupted save left stored in two places ([ActivityPlaces.repairOnce], never
     * widening), moves any zones left wider than they should be and drops copies an interrupted move left behind
     * ([ActivityZonesStore.sweepOnce]), then sends the moves. Best effort, and slow for an athlete with many activities, so callers run it
     * apart from anything the athlete waits on. It sends no import's (bulk) writes, which are the pacer's: one stalled at the PDS would
     * hold the account's drain, and the feed's [syncOwn] behind it (data/outbox).
     */
    suspend fun repairZones() {
        places.repairOnce()
        zones.sweepOnce()
        outbox.drain { !it.bulk }
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

/**
 * The signed-in athlete's activity [rkey] as the outbox will leave it: its last queued put, if one is queued. Rewrites start from it rather
 * than the PDS's copy, so an edit never undoes a queued rewrite (a re-trimmed polyline). Null when nothing is queued for it.
 */
internal suspend fun AppDatabase.queuedActivityRecord(clients: SessionClients, rkey: String): ActivityRecord? =
    outbox()
        .pending(clients.did.value, rkey)
        .lastOrNull { it.collection == Nsid.FEED_ACTIVITY }
        ?.takeIf { it.operation == OutboxProcessor.OP_PUT }
        ?.recordJson
        ?.let { AtJson.decodeFromString(ActivityRecord.serializer(), it) }

private const val NOT_FOUND_STATUS = 400

/** The radius of a zone added from an activity's start or end; the athlete can change it in Settings. */
const val HIDE_ROUTE_END_METERS = 400

/** Runs [block], and [undo] if it throws (cancellation included), so a change shown before a write is queued is taken back with it. */
internal inline fun <T> undoingOnFailure(undo: () -> Unit, block: () -> T): T {
    var done = false
    try {
        return block().also { done = true }
    } finally {
        if (!done) undo()
    }
}

/**
 * Refuses a session whose grant doesn't cover `feed.activityDetails` when any of [drafts] has a category with data narrower than its
 * activity, before anything is queued: the PDS would refuse the details record at the head of the outbox. With every such category at the
 * activity's visibility (an unset Gear category of an activity with no gear included), nothing goes in a details record and such a session
 * can still publish.
 */
internal fun SessionClients.requireDetailsSpaceFor(drafts: List<ActivityDraft>) {
    if (drafts.any { d -> d.tiers.filterKeys { it in d.heldCategories() }.values.any { it != Visibility.known(d.visibility) } })
        requireSpacesFor(Visibility.SELF, Nsid.FEED_ACTIVITY_DETAILS)
}

/** This post with the viewer's repost set to [repost] (null to undo), its count moved by the difference. */
private fun PostView.withRepost(repost: AtUri?): PostView {
    val had = viewer?.repost != null
    val delta =
        when {
            repost != null && !had -> 1
            repost == null && had -> -1
            else -> 0
        }
    return copy(repostCount = (repostCount + delta).coerceAtLeast(0), viewer = (viewer ?: PostViewerState()).copy(repost = repost))
}
