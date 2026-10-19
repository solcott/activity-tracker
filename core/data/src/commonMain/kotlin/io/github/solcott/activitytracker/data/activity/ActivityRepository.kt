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
import io.github.solcott.activitytracker.lexicon.app.ActivityDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.CommentHideRecord
import io.github.solcott.activitytracker.lexicon.app.CommentRecord
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.LikeRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.ReplyRef
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.effectiveRecord
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
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
) {
    val pendingWrites: Flow<Int> = db.outbox().count(clients.did.value)

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
        if (privacy.hasStale) routes.trimStale()
        recentTypes.remember(draft.type)
        return uri
    }

    /**
     * Publishes [drafts] in order, like [publish] but draining the outbox once at the end: a dive logbook can hold hundreds. Every draft is
     * queued before any is sent, so a failed upload leaves them all in the outbox to retry.
     */
    suspend fun publishAll(drafts: List<ActivityDraft>): List<AtUri> {
        drafts.forEach { clients.requireSpacesFor(it.visibility) }
        clients.requireDetailsSpaceFor(drafts)
        requireSelfSpaceFor(drafts)
        val uris = privacy.withReadOnce {
            zones.withTrainingReadOnce { drafts.map { queue(it, photos = emptyList(), trim = privacy.forPublish()) } }
        }
        outbox.drain()
        drafts.map { it.type }.distinct().forEach { recentTypes.remember(it) }
        return uris
    }

    /**
     * Fills the gaps in the athlete's [existing] activity from [draft], another recording of it ([ActivityMerge]): its summary fields, its
     * track, its original file, and its streams' channels. Doesn't drain the outbox, so a batch can queue several before [publishAll] or
     * [sync] sends them.
     *
     * An activity still in the outbox ([OwnActivity.cid] null) gets its record and track merged but not its streams: those aren't readable
     * until the AppView indexes them, and writing ours would replace them.
     */
    suspend fun mergeInto(existing: OwnActivity, draft: ActivityDraft) {
        requireSelfSpaceFor(listOf(draft))
        val rkey = requireNotNull(existing.uri.rkey)
        val indexed = existing.cid != null
        val place = clients.placeOf(existing.visibility)
        // Read from the PDS, every place, so the hidden fields fill gaps too and stay hidden.
        val stored = if (indexed) places.read(existing.uri) else null
        val current = wholeRecord(existing, stored)
        val photos =
            draft.photos.take((ActivityMedia.MAX - current.media.orEmpty().size).coerceAtLeast(0)).map {
                clients.upload(it, feed.heldPhotos)
            }
        val trim = if (current.polyline == null && draft.route.size >= 2) privacy.forPublish() else null
        // The athlete's own words on the import screen fill an empty description; a file's never do.
        val merged =
            ActivityMerge.fillGaps(current, draft.toRecord(clock.now(), photos, trim?.trim?.polylineOf(draft.route))).let {
                if (it.description == null) it.copy(description = draft.description?.takeIf(String::isNotBlank)) else it
            }
        // Categories keep where they're stored; one the activity had no data in takes the draft's, capped at the activity.
        val (summaryNow, channelsNow) = stored?.narrowestTiers() ?: (emptyMap<String, String>() to emptyMap())
        val fallback = DetailCategory.tiers(existing.visibility, draft.tiers)
        val layout =
            ActivityLayout.split(
                merged,
                null,
                existing.uri,
                existing.visibility,
                DetailCategory.tiers(existing.visibility, fallback + summaryNow),
            )
        queueRecords(rkey, layout, stored, place)
        trim?.let { privacy.published(existing.uri, existing.visibility, draft.route, it) }
        fillSelfRecords(existing.uri, draft)
        val imported = draft.streams
        // The draft's stream times count from its own start.
        val offsetSeconds = (draft.startedAt - current.startedAt).inWholeSeconds.toInt()
        if (stored != null && imported != null) {
            val tiers = DetailCategory.tiers(existing.visibility, fallback + channelsNow)
            // A record that had no tanks takes the draft's (fillGaps), so its tank pressure series line up with them.
            mergeStreams(existing, stored, tiers, draft, imported, offsetSeconds, existingTanks = current.dive?.tanks?.size ?: 0)
        }
        feed.rewrites.set(existing.uri, layout.record)
        feed.updateCached(existing.uri) { feed.rewrites.rewritten(it, layout.record) }
    }

    /** [existing]'s whole record: its last queued put, else as [stored] (or as the view had it), with every details record over it. */
    private suspend fun wholeRecord(existing: OwnActivity, stored: StoredActivity?): ActivityRecord {
        val record =
            db.queuedActivityRecord(clients, requireNotNull(existing.uri.rkey))
                ?: stored?.places?.get(existing.visibility)?.activity
                ?: existing.record
        return effectiveRecord(record, Visibility.all.mapNotNull { stored?.places?.get(it)?.details })
    }

    /** Queues [layout]'s details records that differ from [stored]'s, narrowest first, then its activity record at [place]. */
    private suspend fun queueRecords(rkey: String, layout: ActivityLayout, stored: StoredActivity?, place: SpaceUri?) {
        if (layout.details.isNotEmpty()) clients.requireSpacesFor(Visibility.SELF, Nsid.FEED_ACTIVITY_DETAILS)
        for (tier in layout.tiersNarrowestFirst) {
            val details = layout.details[tier]?.takeIf { it != stored?.places?.get(tier)?.details } ?: continue
            writer.put(Nsid.FEED_ACTIVITY_DETAILS, rkey, ActivityDetailsRecord.serializer(), details, space = clients.placeOf(tier))
        }
        writer.put(Nsid.FEED_ACTIVITY, rkey, ActivityRecord.serializer(), layout.record, space = place)
    }

    /**
     * [existing]'s streams, as [stored], with the channels [imported] has and they lack, each category at [tiers]. An activity from before
     * categories keeps its core channels where its one streams record is, so a merge never makes them more visible.
     */
    @Suppress("LongParameterList") // The activity as stored, and the other recording.
    private suspend fun mergeStreams(
        existing: OwnActivity,
        stored: StoredActivity,
        tiers: Map<String, String>,
        draft: ActivityDraft,
        imported: ActivityStreamData,
        offsetSeconds: Int,
        existingTanks: Int,
    ) {
        val activity = existing.uri
        val have = stored.data
        // The zones record's state comes from the AppView: only the athlete's own session reads it there.
        val served = feed.streams(activity)
        val otherTanks = draft.dive?.tanks?.size ?: 0
        val data =
            have?.let { ActivityMerge.fillMissing(it, imported, offsetSeconds, existingTanks, otherTanks) }
                ?: ActivityMerge.shifted(imported, offsetSeconds)
        // A zones record already stored stays as it is, unless its training load gains what it lacks (an HRV, say): then it's rewritten
        // with its own zones and the fuller load. Without one, the draft's zones and load are stored.
        val storedZones = !served.hasNoZonesRecord()
        val filled = ActivityMerge.fillGaps(served?.trainingLoad, draft.trainingLoad).takeIf { storedZones && it != served?.trainingLoad }
        if (data == have && filled == null) return
        streams.put(
            activity,
            ActivityStreams(
                data,
                existing.visibility,
                tiers,
                if (!storedZones) draft.heartRateZones else served?.heartRateZones?.takeIf { filled != null },
                if (!storedZones) draft.powerZones else served?.powerZones?.takeIf { filled != null },
                if (!storedZones) draft.trainingLoad else filled,
                core = stored.coreTier ?: existing.visibility,
            ),
        )
    }

    /** Refuses a session that can't write the full tracks or original files of [drafts] to the `self` space, before anything is queued. */
    private fun requireSelfSpaceFor(drafts: List<ActivityDraft>) {
        if (drafts.any { it.gpx != null }) clients.requireTrackSpace()
        if (drafts.any { it.source != null }) clients.requireSourceSpace()
    }

    /**
     * Gives [activity] [draft]'s track, original file and devices where it has none, stored or queued. Their place doesn't depend on the
     * activity being sent, so unlike its streams an unsent activity's are checked too.
     */
    private suspend fun fillSelfRecords(activity: AtUri, draft: ActivityDraft) {
        draft.gpx?.let { if (!tracks.has(activity)) tracks.put(activity, it) }
        draft.source?.let { if (sources.info(activity) == null) sources.put(activity, it) }
        draft.devices?.let { if (!devices.has(activity)) devices.put(activity, it) }
    }

    /**
     * [photos] are [draft]'s, uploaded; its route is trimmed by [trim] for the record's polyline. Split by its categories
     * ([ActivityLayout]): its details records are queued narrowest first, then the activity record, then its streams, narrowest first.
     */
    private suspend fun queue(draft: ActivityDraft, photos: List<ActivityMedia>, trim: PublishTrim): AtUri {
        val rkey = Tid.next(clock)
        val uri = AtUri.of(clients.did, Nsid.FEED_ACTIVITY, rkey)
        val layout =
            ActivityLayout.split(
                draft.toRecord(clock.now(), photos, trim.trim.polylineOf(draft.route)),
                null,
                uri,
                draft.visibility,
                draft.categories,
            )
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
        privacy.published(uri, draft.visibility, draft.route, trim)
        draft.gpx?.let { tracks.put(uri, it) }
        draft.streams?.let {
            streams.put(
                uri,
                ActivityStreams(it, draft.visibility, draft.categories, draft.heartRateZones, draft.powerZones, draft.trainingLoad),
            )
        }
        draft.source?.let { sources.put(uri, it) }
        draft.devices?.let { devices.put(uri, it) }
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

    /**
     * Retries anything left in the outbox (e.g. on app start or reconnect), first trimming again any route this session published without
     * reading the zones ([RouteTrimmer.trimStale]).
     */
    suspend fun sync(): Int {
        routes.trimStale()
        return outbox.drain()
    }

    /**
     * Once the athlete's zones change, on any device, re-trims every activity's polyline from its track ([RouteTrimmer.sweep]) and sends
     * the rewrites. Best effort and slow for an athlete with many activities, so callers run it apart from anything the athlete waits on.
     */
    suspend fun repairRoutes() {
        routes.sweep()
    }

    /** The signed-in athlete's own [activity]'s full route, from its track in `self`; null when it has none or it can't be read now. */
    suspend fun ownRoute(activity: AtUri): List<LocationSample>? = tracks.read(activity)

    /**
     * Adds a privacy zone of [HIDE_ROUTE_END_METERS] at [route]'s first point ([start]) or its last, offset as every new zone is. Its
     * activities are trimmed again by [repairRoutesOnSave]. Throws as [PrivacyZones.add] does.
     */
    suspend fun hideRouteEnd(route: List<LocationSample>, start: Boolean) {
        val point = if (start) route.first() else route.last()
        privacy.add(point.latitude, point.longitude, HIDE_ROUTE_END_METERS)
    }

    /**
     * Re-trims after every save of the zones this session ([PrivacyZones.saved]), a newer save replacing a sweep still running, for as long
     * as it's collected: the session, so leaving the settings screen doesn't stop a sweep.
     */
    suspend fun repairRoutesOnSave() {
        privacy.saved.filterNotNull().collectLatest { routes.sweep() }
    }

    /**
     * Once a session, finishes any split activity an interrupted save left stored in two places ([ActivityPlaces.repairOnce], never
     * widening), moves any zones left wider than they should be and drops copies an interrupted move left behind
     * ([ActivityZonesStore.sweepOnce]), then sends the moves. Best effort, and slow for an athlete with many activities, so callers run it
     * apart from anything the athlete waits on.
     */
    suspend fun repairZones() {
        places.repairOnce()
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

/**
 * The signed-in athlete's activity [rkey] as the outbox will leave it: its last queued put, if one is queued. Rewrites start from it rather
 * than the PDS's copy, so an edit never undoes a queued rewrite (a re-trimmed polyline). Null when nothing is queued for it.
 */
internal suspend fun AppDatabase.queuedActivityRecord(clients: SessionClients, rkey: String): ActivityRecord? =
    outbox()
        .pending(clients.did.value)
        .lastOrNull { it.collection == Nsid.FEED_ACTIVITY && it.rkey == rkey }
        ?.takeIf { it.operation == OutboxProcessor.OP_PUT }
        ?.recordJson
        ?.let { AtJson.decodeFromString(ActivityRecord.serializer(), it) }

private const val NOT_FOUND_STATUS = 400

/** The radius of a zone added from an activity's start or end; the athlete can change it in Settings. */
const val HIDE_ROUTE_END_METERS = 400

/** Runs [block], and [undo] if it throws (cancellation included), so a change shown before a write is queued is taken back with it. */
private inline fun <T> undoingOnFailure(undo: () -> Unit, block: () -> T): T {
    var done = false
    try {
        return block().also { done = true }
    } finally {
        if (!done) undo()
    }
}

/**
 * Refuses a session whose grant doesn't cover `feed.activityDetails` when any of [drafts] has a category narrower than its activity, before
 * anything is queued: the PDS would refuse the details record at the head of the outbox. With every category at the activity's visibility,
 * nothing goes in a details record and such a session can still publish.
 */
internal fun SessionClients.requireDetailsSpaceFor(drafts: List<ActivityDraft>) {
    if (drafts.any { d -> d.tiers.values.any { it != Visibility.known(d.visibility) } })
        requireSpacesFor(Visibility.SELF, Nsid.FEED_ACTIVITY_DETAILS)
}

/** No zones record is stored for these streams (or there are none): a merge may add the other recording's. */
private fun GetActivityStreams.Output?.hasNoZonesRecord() =
    this == null || (heartRateZones == null && powerZones == null && trainingLoad == null)
