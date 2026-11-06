package io.github.solcott.activitytracker.data.feed

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.activity.HeldPhotos
import io.github.solcott.activitytracker.data.activity.OwnActivity
import io.github.solcott.activitytracker.data.activity.pendingActivity
import io.github.solcott.activitytracker.data.cachedOutcomes
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.FeedItemEntity
import io.github.solcott.activitytracker.data.db.KeyValueEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.outcomeFlow
import io.github.solcott.activitytracker.data.outcomeOf
import io.github.solcott.activitytracker.data.ownBlobUrl
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivitiesByStart
import io.github.solcott.activitytracker.lexicon.app.GetAuthorPosts
import io.github.solcott.activitytracker.lexicon.app.GetComments
import io.github.solcott.activitytracker.lexicon.app.GetImportBatch
import io.github.solcott.activitytracker.lexicon.app.GetLikes
import io.github.solcott.activitytracker.lexicon.app.GetPost
import io.github.solcott.activitytracker.lexicon.app.GetProfile
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.ImportCardView
import io.github.solcott.activitytracker.lexicon.app.Paging
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.PostViewerState
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.ThreadView
import io.github.solcott.activitytracker.lexicon.app.TimelineItem
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.effectiveRecord
import io.github.solcott.activitytracker.location.TrackMath
import io.github.solcott.dataresult.Outcome
import io.github.solcott.uistate.Page
import io.github.solcott.uistate.pageOf
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/** AppView reads, cached in Room so feeds render offline and instantly on launch. */
@SingleIn(SessionScope::class)
@Inject
@Suppress("TooManyFunctions") // Every read of the feed, with the athlete's own changes laid over it.
class FeedRepository(
    private val db: AppDatabase,
    private val clients: SessionClients,
    private val localLikes: LocalLikes,
    logger: Logger,
    /** Posts the athlete shared, changed or took back this session (feed/posts), laid over the timeline and their profile. */
    val localPosts: LocalPosts = LocalPosts(),
) {
    private val log = logger.withTag("FeedRepository")

    /** Photos this device uploaded this session, shown until the AppView serves them. */
    val heldPhotos = HeldPhotos()

    /** Records the athlete rewrote this session, shown over the AppView's until it catches up: write one with [updateCached] too. */
    val rewrites = LocalRewrites(heldPhotos) { cid -> ownBlobUrl(clients.pdsUrl, clients.did, cid) }

    /** Streams the athlete rebuilt this session, shown over the AppView's until it catches up ([streams]). */
    val localStreams = LocalStreams()

    /** Activities the athlete deleted and recordings they split off this session, left out until the AppView catches up. */
    val deletes = LocalDeletes()

    /** Comments the viewer sent, deleted, hid or unhid this session, shown so until the AppView catches up. */
    val localComments = LocalComments()

    /** Accounts the viewer blocked, muted or let back this session, laid over every read until the AppView catches up. */
    val localBlocks = LocalBlocks()

    /** The viewer's like toggles this session ([LocalLikes]), for a screen that lays them over posts it has already loaded. */
    val likeOverrides: StateFlow<Map<String, AtUri?>>
        get() = localLikes.overrides

    /** The cached timeline's activities, without its import cards ([timelineItems]). */
    fun timeline(filter: ActivityFilter = ActivityFilter.None): Flow<List<ActivityView>> =
        timelineItems(filter).map { items -> items.mapNotNull { (it as? TimelineItem.Activity)?.view } }

    /**
     * The cached timeline: activities and, unfiltered, import cards (feed/import-cards) and posts (feed/posts), in the AppView's order,
     * with the athlete's own new posts first until it lists them.
     */
    fun timelineItems(filter: ActivityFilter = ActivityFilter.None): Flow<List<TimelineItem>> =
        combine(db.feed().observe(filter.cacheKey(TIMELINE)), localBlocks.entries, localPosts.changes, localLikes.overrides) {
            items,
            blocks,
            posts,
            likes ->
            val decoded = items.mapNotNull { item ->
                runCatching {
                    when {
                        item.uri.startsWith(CARD_KEY) ->
                            TimelineItem.ImportCard(AtJson.decodeFromString(ImportCardView.serializer(), item.json))
                        item.uri.startsWith(POST_KEY) ->
                            TimelineItem.Post(AtJson.decodeFromString(PostView.serializer(), item.json).withLikeOverrides(likes))
                        else -> TimelineItem.Activity(AtJson.decodeFromString(ActivityView.serializer(), item.json))
                    }
                }
                    .onFailure { log.w(it) { "Skipping undecodable cached timeline item ${item.uri}" } }
                    .getOrNull()
            }
            localPosts.applyToTimeline(decoded, includeNew = filter == ActivityFilter.None, at = posts).filter { item ->
                when (item) {
                    is TimelineItem.Activity -> localBlocks.filter(listOf(item.view), timeline = true, blocks).isNotEmpty()
                    is TimelineItem.ImportCard -> !localBlocks.hidesOnTimeline(item.card.author.did, blocks)
                    is TimelineItem.Post -> !localBlocks.hidesOnTimeline(item.post.author.did, blocks)
                    is TimelineItem.Unknown -> false
                }
            }
        }

    /**
     * A page of [actor]'s posts the viewer may see after [cursor] (feed/posts), for their profile, with this session's post changes laid
     * over it: on the athlete's own first page, their new posts first until listed. Blocks made this session hide them; mutes don't.
     */
    suspend fun authorPostsPage(actor: Did, cursor: String?): Outcome<Page<PostView, Unit>> = outcomeOf {
        val output = clients.appView.query(GetAuthorPosts.method, GetAuthorPosts.Params(actor.value, cursor = cursor))
        val own = actor == clients.did
        val shown =
            localPosts
                .applyTo(output.posts, includeNew = own && cursor == null)
                .filterNot { localBlocks.entries.value.blocks[it.author.did]?.on == true }
                .map(localLikes::applyTo)
        pageOf(shown, output.cursor)
    }

    fun authorActivities(did: String, filter: ActivityFilter = ActivityFilter.None): Flow<List<ActivityView>> =
        observe(filter.cacheKey(authorKey(did)), timeline = false)

    /**
     * [timeline] and its refresh as one stream ([cachedOutcomes]): each collection refreshes once, after [beforeRefresh], whose failure
     * fails the refresh.
     */
    fun timelineOutcomes(
        filter: ActivityFilter = ActivityFilter.None,
        beforeRefresh: suspend () -> Unit = {},
    ): Flow<Outcome<List<ActivityView>>> =
        cachedOutcomes(timeline(filter), List<ActivityView>::isEmpty) {
            beforeRefresh()
            refreshTimeline(filter)
        }

    /** [timelineItems] and its refresh as one stream, as [timelineOutcomes]. */
    fun timelineItemOutcomes(
        filter: ActivityFilter = ActivityFilter.None,
        beforeRefresh: suspend () -> Unit = {},
    ): Flow<Outcome<List<TimelineItem>>> =
        cachedOutcomes(timelineItems(filter), List<TimelineItem>::isEmpty) {
            beforeRefresh()
            refreshTimeline(filter)
        }

    /** [view] with this session's changes laid over it, as cached feeds show it: for a paged list after a like toggle, say. */
    fun withLocalChanges(view: ActivityView): ActivityView = shownInFeed(view)

    /** A page of [actor]'s activities from import [batch] after [cursor] (feed/import-cards), for a paged list. */
    suspend fun importBatchPage(actor: String, batch: String, cursor: String?): Outcome<Page<ActivityView, Unit>> = outcomeOf {
        val output = clients.appView.query(GetImportBatch.method, GetImportBatch.Params(actor, batch, cursor = cursor))
        pageOf(deletes.filter(output.activities).map(shownInFeed), output.cursor)
    }

    /** [authorActivities] and its refresh as one stream, as [timelineOutcomes]. */
    fun authorOutcomes(
        did: String,
        filter: ActivityFilter = ActivityFilter.None,
        beforeRefresh: suspend () -> Unit = {},
    ): Flow<Outcome<List<ActivityView>>> =
        cachedOutcomes(authorActivities(did, filter), List<ActivityView>::isEmpty) {
            beforeRefresh()
            refreshAuthor(did, filter)
        }

    suspend fun refreshTimeline(filter: ActivityFilter = ActivityFilter.None) {
        val params = GetTimeline.Params(type = filter.typeTokens().ifEmpty { null }, since = filter.since, until = filter.until)
        val output = clients.appView.query(GetTimeline.method, params)
        val key = filter.cacheKey(TIMELINE)
        // Activities deleted or combined this session are left out, and recordings split off are dropped from the rest.
        val kept = deletes.filter(output.activities).associateBy { it.uri }
        val items =
            output.feed.mapNotNull { item ->
                when (item) {
                    is TimelineItem.Activity -> kept[item.view.uri]?.let { TimelineItem.Activity(shownInFeed(it)) }
                    is TimelineItem.ImportCard -> item
                    is TimelineItem.Post -> item
                    is TimelineItem.Unknown -> null
                }
            }
        db.feed().replace(key, items.toTimelineFeedItems(key))
        if (filter != ActivityFilter.None) db.trackFilteredFeed(key)
    }

    suspend fun refreshAuthor(actor: String, filter: ActivityFilter = ActivityFilter.None) {
        val params =
            GetAuthorActivities.Params(actor, type = filter.typeTokens().ifEmpty { null }, since = filter.since, until = filter.until)
        val output = clients.appView.query(GetAuthorActivities.method, params)
        val did = output.activities.firstOrNull()?.author?.did?.value ?: actor
        val key = filter.cacheKey(authorKey(did))
        db.feed().replace(key, deletes.filter(output.activities).toFeedItems(key, shownInFeed))
        if (filter != ActivityFilter.None) db.trackFilteredFeed(key)
    }

    /** [activity] as a stream ([outcomeFlow]): loading, then the activity or why it couldn't be read. Each collection reads it again. */
    fun activityOutcome(uri: AtUri): Flow<Outcome<ActivityView>> = outcomeFlow { activity(uri) }

    /**
     * [uri] as the viewer left it, its comment count included. When the viewer changed comments on it this session, the AppView is asked,
     * alongside, which of those it lists ([LocalComments.lookup]), so the count is moved only by the changes it hasn't caught up with,
     * wherever on the activity's pages they are.
     */
    suspend fun activity(uri: AtUri): ActivityView = coroutineScope {
        val pending = localComments.pending(uri)
        val lookup =
            if (pending.isEmpty()) null
            else async { runSuspendCatching { clients.appView.query(GetComments.method, GetComments.Params(uri, uris = pending)) } }
        // A block or mute the AppView already serves no longer moves the count: ask about those not known to be settled yet.
        val settling = localBlocks.unsettled().map { did -> async { settleBlock(did) } }
        val view = clients.appView.query(GetActivity.method, GetActivity.Params(uri)).activity
        settling.forEach { it.await() }
        // Without an answer, each change still counts as not caught up.
        val answer =
            lookup?.await()?.onFailure { log.w(it) { "Couldn't look up the viewer's comment changes on $uri" } }?.getOrNull()?.comments
        val lookedUp = answer?.let { localComments.lookup(uri, pending, it) }
        heldPhotos.dropServed(view)
        val activity = localLikes.applyTo(deletes.applyTo(rewrites.applyTo(view)))
        val delta = (lookedUp?.delta ?: localComments.delta(uri)) + localBlocks.commentDelta(uri, localComments::isHidden)
        val counted = activity.copy(commentCount = (activity.commentCount + delta).coerceAtLeast(0))
        updateCached(uri) { counted }
        // Reported last, with nothing suspending before the caller has the view, so a screen showing the activity loaded before and
        // [LocalComments.delta] now never shows the one moved by the other's catch-up.
        lookedUp?.report()
        counted
    }

    /**
     * A page of [activity]'s comment threads from the AppView, oldest first, after [cursor] (the first page when null), as it serves them;
     * with [root], a page of that thread's replies instead, after [cursor]. Everything it lists, top-level comments and replies alike, is
     * reported to [localComments]; lay [LocalComments.applyTo] over the threads or replies to show them as the viewer left them.
     */
    suspend fun comments(activity: AtUri, cursor: String? = null, root: AtUri? = null): GetComments.Output =
        clients.appView.query(GetComments.method, GetComments.Params(activity, cursor = cursor, root = root)).also { page ->
            val listed = page.comments + page.threads.orEmpty().flatMap { listOfNotNull(it.root) + it.replies }
            localComments.report(listed)
            localBlocks.report(activity, listed)
        }

    /** A page of [activity]'s comment threads after [cursor] ([comments]), for a paged list. */
    suspend fun commentsPage(activity: AtUri, cursor: String?): Outcome<Page<ThreadView, Unit>> = outcomeOf {
        comments(activity, cursor).let { pageOf(it.threads.orEmpty(), it.cursor) }
    }

    /** [post] as a stream ([outcomeFlow]): loading, then the post or why it couldn't be read. Each collection reads it again. */
    fun postOutcome(uri: AtUri): Flow<Outcome<PostView>> = outcomeFlow { post(uri) }

    /**
     * The post [uri] (feed/post-page) as the viewer left it: their like toggles and post changes, and their comment changes moved into its
     * count as [activity] does, asking the AppView alongside which of those it lists.
     */
    suspend fun post(uri: AtUri): PostView = coroutineScope {
        val pending = localComments.pending(uri)
        val lookup =
            if (pending.isEmpty()) null
            else async { runSuspendCatching { clients.appView.query(GetComments.method, GetComments.Params(uri, uris = pending)) } }
        val settling = localBlocks.unsettled().map { did -> async { settleBlock(did) } }
        val view = clients.appView.query(GetPost.method, GetPost.Params(uri)).post
        settling.forEach { it.await() }
        val answer =
            lookup?.await()?.onFailure { log.w(it) { "Couldn't look up the viewer's comment changes on $uri" } }?.getOrNull()?.comments
        val lookedUp = answer?.let { localComments.lookup(uri, pending, it) }
        val post = localLikes.applyTo(localPosts.applyTo(listOf(view), includeNew = false).firstOrNull() ?: view)
        val delta = (lookedUp?.delta ?: localComments.delta(uri)) + localBlocks.commentDelta(uri, localComments::isHidden)
        val counted = post.copy(commentCount = (post.commentCount + delta).coerceAtLeast(0))
        updateCachedPost(uri) { counted }
        lookedUp?.report()
        counted
    }

    /** A page of thread [root]'s replies on [activity] after [cursor] ([comments]), for a paged list. */
    suspend fun repliesPage(activity: AtUri, root: AtUri, cursor: String?): Outcome<Page<CommentView, Unit>> = outcomeOf {
        comments(activity, cursor, root).let { pageOf(it.comments, it.cursor) }
    }

    /**
     * Asks the AppView whether it serves the viewer's block or mute of [did] yet ([LocalBlocks.applyTo] settles it if so). An athlete it
     * answers as unknown blocks the viewer, so the AppView already leaves them out: that settles it too.
     */
    private suspend fun settleBlock(did: Did) {
        runSuspendCatching { clients.appView.query(GetProfile.method, GetProfile.Params(did.value)) }
            .onSuccess { localBlocks.applyTo(it) }
            .onFailure { e ->
                if (e is XrpcException && e.error == PROFILE_NOT_FOUND) localBlocks.settleAll(did)
                else log.w(e) { "Couldn't ask whether the AppView serves the viewer's block of $did" }
            }
    }

    /**
     * [uri]'s streams, or null when it has none or the viewer may not see them. Always from the AppView, never the author's PDS, so it's
     * the same wherever they're stored (the repo, or a tier space; see `ActivityStreamsStore`), with any the athlete rebuilt this session
     * laid over them until it serves those ([localStreams]).
     */
    suspend fun streams(uri: AtUri): GetActivityStreams.Output? {
        val served =
            try {
                clients.appView.query(GetActivityStreams.method, GetActivityStreams.Params(uri))
            } catch (e: XrpcException) {
                if (e.error == NOT_FOUND) null else throw e
            }
        return localStreams.applyTo(uri, served)
    }

    /**
     * The first [Paging.MAX_LIMIT] athletes who liked [uri], straight from the AppView, so it lags the viewer's own toggles (see
     * [LocalLikes]).
     */
    /** [uri]'s likers, without accounts the viewer blocked this session ([localBlocks]). */
    suspend fun likes(uri: AtUri): List<ProfileViewBasic> =
        clients.appView.query(GetLikes.method, GetLikes.Params(uri, limit = Paging.MAX_LIMIT)).likes.filterNot {
            localBlocks.blocked(it.did) == true
        }

    /**
     * The signed-in athlete's own activities that started in [since, until), oldest first, optionally of one [sport]: every page the
     * AppView has, plus those only this device knows of yet ([localOwnActivitiesInRange]). An import compares its activities against these
     * to find duplicates.
     */
    suspend fun ownActivitiesStartedBetween(since: Instant, until: Instant, sport: String? = null): List<OwnActivity> =
        ownActivitiesInRange(since, until, sport).activities

    /**
     * [ownActivitiesStartedBetween], reading at most [maxPages] pages, and whether the AppView had more ([OwnActivityRange.truncated]).
     * Throws when the AppView can't be read; [localOwnActivitiesInRange] still can.
     */
    suspend fun ownActivitiesInRange(
        since: Instant,
        until: Instant,
        sport: String? = null,
        maxPages: Int = MAX_RANGE_PAGES,
    ): OwnActivityRange {
        val indexed = mutableListOf<OwnActivity>()
        var cursor: String? = null
        var pages = 0
        do {
            val page =
                clients.appView.query(
                    GetAuthorActivitiesByStart.method,
                    GetAuthorActivitiesByStart.Params(clients.did.value, since, until, sport, Paging.MAX_LIMIT, cursor),
                )
            indexed += page.activities.map { it.toOwnActivity() }
            cursor = page.cursor
        } while (cursor != null && ++pages < maxPages)
        return OwnActivityRange(mergeOwn(indexed, localOwnActivitiesInRange(since, until, sport)), truncated = cursor != null)
    }

    /**
     * The signed-in athlete's own activities that started in [since, until), oldest first, optionally of one [sport], as this device knows
     * them without the network: still in the outbox, in the cached feeds, or logged in the upload queue (kept 7 days), one per URI in that
     * order of precedence. Never throws: a source that can't be read adds nothing.
     */
    suspend fun localOwnActivitiesInRange(since: Instant, until: Instant, sport: String? = null): List<OwnActivity> {
        val did = clients.did
        val pending = localRead("outbox") { db.outbox().pending(did.value).mapNotNull { it.pendingActivity(did) } }
        val cached =
            localRead("feed cache") {
                val prefix = "at://${did.value}/${Nsid.FEED_ACTIVITY}/"
                db.feed()
                    .byUriPrefix(prefix)
                    .mapNotNull { item -> runCatching { AtJson.decodeFromString(ActivityView.serializer(), item.json) }.getOrNull() }
                    // Deleted this session: no longer in the log. A member combined meanwhile still is, so it stays.
                    .filter { it.author.did == did && it.uri !in deletes }
                    .map { it.toOwnActivity() }
            }
        val logged =
            localRead("upload log") {
                db.uploads().loggedRecords(did.value, since.toEpochMilliseconds(), until.toEpochMilliseconds()).mapNotNull { row ->
                    runCatching { AtJson.decodeFromString(ActivityRecord.serializer(), row.recordJson) }
                        .getOrNull()
                        // Its visibility isn't logged, and with no CID it can't be combined into yet: Only me, the narrowest, if ever read.
                        ?.let { OwnActivity(AtUri.of(did, Nsid.FEED_ACTIVITY, row.rkey), cid = null, it, Visibility.SELF) }
                }
            }
        return mergeOwn(pending, cached, logged).filter { (_, _, record) ->
            record.startedAt >= since && record.startedAt < until && (sport == null || record.sport == sport)
        }
    }

    /**
     * Where the signed-in athlete's latest activity with a map started, as the AppView serves its line (privacy zones already cut out), in
     * degrees: from the cached feeds, else the first page of their activities from the AppView. Null when none has a line or neither can be
     * read. Segment browse opens there when it has no location (segments/segment-browse).
     */
    suspend fun latestOwnStart(): Pair<Double, Double>? {
        val did = clients.did
        fun startOf(views: List<ActivityView>): Pair<Double, Double>? =
            views
                .filter { it.author.did == did && it.uri !in deletes }
                .map { it.effectiveRecord() }
                .sortedByDescending { it.startedAt }
                .firstNotNullOfOrNull { record -> record.polyline?.let(TrackMath::decodePolylineOrNull)?.firstOrNull() }
        val cached = runSuspendCatching {
            db.feed().byUriPrefix("at://${did.value}/${Nsid.FEED_ACTIVITY}/").mapNotNull { item ->
                runCatching { AtJson.decodeFromString(ActivityView.serializer(), item.json) }.getOrNull()
            }
        }
            .getOrDefault(emptyList())
        return startOf(cached)
            ?: runSuspendCatching { clients.appView.query(GetAuthorActivities.method, GetAuthorActivities.Params(did.value)).activities }
                .onFailure { e -> log.w(e) { "Couldn't read the latest activities for a start" } }
                .getOrNull()
                ?.let(::startOf)
    }

    private suspend fun localRead(source: String, read: suspend () -> List<OwnActivity>): List<OwnActivity> = runSuspendCatching {
        read()
    }
        .onFailure { e -> log.w(e) { "Couldn't read own activities from the $source" } }
        .getOrDefault(emptyList())

    /**
     * The signed-in athlete's own activities as the AppView serves them, that started in [since, until), oldest first, every page it has,
     * without those deleted or combined into another this session. Members of a combined activity come too, naming it (`combinedInto`).
     */
    suspend fun ownViewsStartedBetween(since: Instant, until: Instant): List<ActivityView> {
        val views = mutableListOf<ActivityView>()
        var cursor: String? = null
        var pages = 0
        do {
            val page =
                clients.appView.query(
                    GetAuthorActivitiesByStart.method,
                    GetAuthorActivitiesByStart.Params(clients.did.value, since, until, null, Paging.MAX_LIMIT, cursor),
                )
            views += page.activities
            cursor = page.cursor
        } while (cursor != null && ++pages < MAX_RANGE_PAGES)
        return deletes.filter(views)
    }

    /**
     * Replaces every cached copy of [uri] with what the AppView serves now, or drops them when it serves nothing or can't be reached: after
     * the athlete discarded a change to it that was never sent (data/outbox).
     */
    suspend fun reloadCached(uri: AtUri) {
        if (db.feed().byUri(uri.value).isEmpty()) return
        val served = runSuspendCatching { clients.appView.query(GetActivity.method, GetActivity.Params(uri)).activity }.getOrNull()
        if (served == null) db.feed().deleteByUri(uri.value) else updateCached(uri) { served }
    }

    /**
     * Optimistically moves every cached copy of [subject]'s like count by [likes] and comment count by [comments], and sets the viewer's
     * [like] when given: an activity's ([updateCached]) or a post's ([updateCachedPost]), by [subject]'s collection.
     */
    suspend fun updateCachedCounts(subject: AtUri, likes: Long = 0, comments: Long = 0, like: CachedLike = CachedLike.Keep) {
        if (subject.collection == Nsid.FEED_POST) {
            updateCachedPost(subject) {
                it.copy(
                    likeCount = (it.likeCount + likes).coerceAtLeast(0),
                    commentCount = (it.commentCount + comments).coerceAtLeast(0),
                    viewer = if (like is CachedLike.Set) (it.viewer ?: PostViewerState()).copy(like = like.uri) else it.viewer,
                )
            }
        } else {
            updateCached(subject) {
                it.copy(
                    likeCount = (it.likeCount + likes).coerceAtLeast(0),
                    commentCount = (it.commentCount + comments).coerceAtLeast(0),
                    viewer = if (like is CachedLike.Set) it.viewer?.copy(like = like.uri) else it.viewer,
                )
            }
        }
    }

    /** Optimistically rewrites every cached copy of the post [uri] (feed/posts), in the timeline and wherever else it's cached. */
    suspend fun updateCachedPost(uri: AtUri, transform: (PostView) -> PostView) {
        val items = db.feed().byUri("$POST_KEY${uri.value}")
        if (items.isEmpty()) return
        db.feed()
            .upsert(
                items.map { item ->
                    val view = transform(AtJson.decodeFromString(PostView.serializer(), item.json))
                    item.copy(json = AtJson.encodeToString(PostView.serializer(), view))
                }
            )
    }

    /** Optimistically rewrites every cached copy of [uri]. */
    suspend fun updateCached(uri: AtUri, transform: (ActivityView) -> ActivityView) {
        val items = db.feed().byUri(uri.value)
        if (items.isEmpty()) return
        db.feed()
            .upsert(
                items.map { item ->
                    val view = transform(AtJson.decodeFromString(ActivityView.serializer(), item.json))
                    item.copy(json = AtJson.encodeToString(ActivityView.serializer(), view))
                }
            )
    }

    // AppView reads lag the viewer's own like toggles until Jetstream catches up, so local ones win. Photos they serve needn't be held.
    private val shownInFeed: (ActivityView) -> ActivityView = {
        heldPhotos.dropServed(it)
        localLikes.applyTo(rewrites.applyTo(it))
    }

    /** The cached feed [key], without the accounts the viewer blocked this session, and on the [timeline] muted ([localBlocks]). */
    private fun observe(key: String, timeline: Boolean): Flow<List<ActivityView>> =
        combine(db.feed().observe(key), localBlocks.entries) { items, blocks ->
            val views = items.mapNotNull { item ->
                runCatching { AtJson.decodeFromString(ActivityView.serializer(), item.json) }
                    .onFailure { log.w(it) { "Skipping undecodable cached activity ${item.uri}" } }
                    .getOrNull()
            }
            localBlocks.filter(views, timeline, blocks)
        }

    private companion object {
        const val NOT_FOUND = "NotFound"
        const val PROFILE_NOT_FOUND = "ProfileNotFound"
        const val TIMELINE = "timeline"

        fun authorKey(did: String) = "author:$did"
    }
}

/** Filtered feed cache keys, most recently refreshed first. */
private const val FILTERED_FEEDS = "feed.filteredKeys"
private const val MAX_FILTERED_FEEDS = 10
private val recentKeys = ListSerializer(String.serializer())

private fun ActivityView.toOwnActivity() = OwnActivity(uri, cid, record, visibilityOrPublic, combinedInto)

/** [sources]' activities oldest first, one per URI: the first source to list one wins. */
internal fun mergeOwn(vararg sources: List<OwnActivity>): List<OwnActivity> {
    val byUri = LinkedHashMap<AtUri, OwnActivity>()
    sources.forEach { source -> source.forEach { byUri.getOrPut(it.uri) { it } } }
    return byUri.values.sortedBy { it.record.startedAt }
}

/**
 * Makes [key] the most recently refreshed filtered feed and drops the cached pages of those beyond the [MAX_FILTERED_FEEDS] most recent, so
 * trying filters doesn't grow the cache without bound. Unfiltered feeds are never tracked, so never evicted.
 */
private suspend fun AppDatabase.trackFilteredFeed(key: String) {
    val recent = keyValue().get(FILTERED_FEEDS)?.let { AtJson.decodeFromString(recentKeys, it) }.orEmpty()
    val updated = listOf(key) + (recent - key)
    updated.drop(MAX_FILTERED_FEEDS).forEach { feed().clear(it) }
    keyValue().put(KeyValueEntity(FILTERED_FEEDS, AtJson.encodeToString(recentKeys, updated.take(MAX_FILTERED_FEEDS))))
}

/** Where a cached import card is kept in its feed: never an activity URI, so activity updates and deletes never touch it. */
private const val CARD_KEY = "importCard:"

/** Where a cached post is kept in its feed (feed/posts): its URI behind a prefix, so activity updates and deletes never touch it. */
private const val POST_KEY = "post:"

/** [this] as the cached items of timeline [key], in order. */
private fun List<TimelineItem>.toTimelineFeedItems(key: String) = mapIndexedNotNull { index, item ->
    when (item) {
        is TimelineItem.Activity ->
            FeedItemEntity(key, item.view.uri.value, index, AtJson.encodeToString(ActivityView.serializer(), item.view))
        is TimelineItem.ImportCard ->
            FeedItemEntity(
                key,
                "$CARD_KEY${item.card.author.did.value}/${item.card.batch}",
                index,
                AtJson.encodeToString(ImportCardView.serializer(), item.card),
            )
        is TimelineItem.Post ->
            FeedItemEntity(key, "$POST_KEY${item.post.uri.value}", index, AtJson.encodeToString(PostView.serializer(), item.post))
        is TimelineItem.Unknown -> null
    }
}

/** [this] as the cached items of feed [key], in order, each as [shown] makes it. */
private fun List<ActivityView>.toFeedItems(key: String, shown: (ActivityView) -> ActivityView) = mapIndexed { index, view ->
    FeedItemEntity(key, view.uri.value, index, AtJson.encodeToString(ActivityView.serializer(), shown(view)))
}

/** 5,000 activities: far more than one search window holds, and a bound on a misbehaving cursor. */
internal const val MAX_RANGE_PAGES = 50

/** What [FeedRepository.updateCachedCounts] does with the viewer's like on a cached copy: keep it, or [Set] it (null removes it). */
sealed interface CachedLike {
    data object Keep : CachedLike

    data class Set(val uri: AtUri?) : CachedLike
}
