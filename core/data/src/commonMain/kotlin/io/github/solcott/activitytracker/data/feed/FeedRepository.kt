package io.github.solcott.activitytracker.data.feed

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.activity.HeldPhotos
import io.github.solcott.activitytracker.data.activity.OwnActivity
import io.github.solcott.activitytracker.data.activity.pendingActivity
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.FeedItemEntity
import io.github.solcott.activitytracker.data.db.KeyValueEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.ownBlobUrl
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivitiesByStart
import io.github.solcott.activitytracker.lexicon.app.GetComments
import io.github.solcott.activitytracker.lexicon.app.GetLikes
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.Paging
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
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
) {
    private val log = logger.withTag("FeedRepository")

    /** Photos this device uploaded this session, shown until the AppView serves them. */
    val heldPhotos = HeldPhotos()

    /** Records the athlete rewrote this session, shown over the AppView's until it catches up: write one with [updateCached] too. */
    val rewrites = LocalRewrites(heldPhotos) { cid -> ownBlobUrl(clients.pdsUrl, clients.did, cid) }

    /** Activities the athlete deleted and recordings they split off this session, left out until the AppView catches up. */
    val deletes = LocalDeletes()

    /** Comments the viewer sent, deleted, hid or unhid this session, shown so until the AppView catches up. */
    val localComments = LocalComments()

    fun timeline(filter: ActivityFilter = ActivityFilter.None): Flow<List<ActivityView>> = observe(filter.cacheKey(TIMELINE))

    fun authorActivities(did: String, filter: ActivityFilter = ActivityFilter.None): Flow<List<ActivityView>> =
        observe(filter.cacheKey(authorKey(did)))

    suspend fun refreshTimeline(filter: ActivityFilter = ActivityFilter.None) {
        val params = GetTimeline.Params(type = filter.typeTokens().ifEmpty { null }, since = filter.since, until = filter.until)
        val output = clients.appView.query(GetTimeline.method, params)
        val key = filter.cacheKey(TIMELINE)
        db.feed().replace(key, deletes.filter(output.activities).toFeedItems(key, shownInFeed))
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
        val view = clients.appView.query(GetActivity.method, GetActivity.Params(uri)).activity
        // Without an answer, each change still counts as not caught up.
        val answer =
            lookup?.await()?.onFailure { log.w(it) { "Couldn't look up the viewer's comment changes on $uri" } }?.getOrNull()?.comments
        val lookedUp = answer?.let { localComments.lookup(uri, pending, it) }
        heldPhotos.dropServed(view)
        val activity = localLikes.applyTo(deletes.applyTo(rewrites.applyTo(view)))
        val delta = lookedUp?.delta ?: localComments.delta(uri)
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
            localComments.report(page.comments + page.threads.orEmpty().flatMap { listOfNotNull(it.root) + it.replies })
        }

    /**
     * [uri]'s streams, or null when it has none or the viewer may not see them. Always from the AppView, never the author's PDS, so it's
     * the same wherever they're stored (the repo, or a tier space; see `ActivityStreamsStore`).
     */
    suspend fun streams(uri: AtUri): GetActivityStreams.Output? =
        try {
            clients.appView.query(GetActivityStreams.method, GetActivityStreams.Params(uri))
        } catch (e: XrpcException) {
            if (e.error == NOT_FOUND) null else throw e
        }

    /**
     * The first [Paging.MAX_LIMIT] athletes who liked [uri], straight from the AppView, so it lags the viewer's own toggles (see
     * [LocalLikes]).
     */
    suspend fun likes(uri: AtUri): List<ProfileViewBasic> =
        clients.appView.query(GetLikes.method, GetLikes.Params(uri, limit = Paging.MAX_LIMIT)).likes

    /**
     * The signed-in athlete's own activities that started in [since, until), oldest first, optionally of one [sport]: every page the
     * AppView has, plus any still in the outbox (published moments ago, or offline), which it hasn't seen yet. An import compares its
     * activities against these to find duplicates.
     */
    suspend fun ownActivitiesStartedBetween(since: Instant, until: Instant, sport: String? = null): List<OwnActivity> {
        val indexed = mutableListOf<OwnActivity>()
        var cursor: String? = null
        var pages = 0
        do {
            val page =
                clients.appView.query(
                    GetAuthorActivitiesByStart.method,
                    GetAuthorActivitiesByStart.Params(clients.did.value, since, until, sport, Paging.MAX_LIMIT, cursor),
                )
            indexed += page.activities.map { OwnActivity(it.uri, it.cid, it.record, it.visibilityOrPublic, it.combinedInto) }
            cursor = page.cursor
        } while (cursor != null && ++pages < MAX_RANGE_PAGES)
        val known = indexed.mapTo(mutableSetOf()) { it.uri }
        val pending =
            db.outbox()
                .pending(clients.did.value)
                .mapNotNull { it.pendingActivity(clients.did) }
                .filter { (uri, _, record) ->
                    uri !in known && record.startedAt >= since && record.startedAt < until && (sport == null || record.sport == sport)
                }
        return (indexed + pending).sortedBy { it.record.startedAt }
    }

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

    private fun observe(key: String): Flow<List<ActivityView>> =
        db.feed().observe(key).map { items ->
            items.mapNotNull { item ->
                runCatching { AtJson.decodeFromString(ActivityView.serializer(), item.json) }
                    .onFailure { log.w(it) { "Skipping undecodable cached activity ${item.uri}" } }
                    .getOrNull()
            }
        }

    private companion object {
        const val NOT_FOUND = "NotFound"
        const val TIMELINE = "timeline"

        /** 5,000 activities: far more than one import spans, and a bound on a misbehaving cursor. */
        const val MAX_RANGE_PAGES = 50

        fun authorKey(did: String) = "author:$did"
    }
}

/** Filtered feed cache keys, most recently refreshed first. */
private const val FILTERED_FEEDS = "feed.filteredKeys"
private const val MAX_FILTERED_FEEDS = 10
private val recentKeys = ListSerializer(String.serializer())

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

/** [this] as the cached items of feed [key], in order, each as [shown] makes it. */
private fun List<ActivityView>.toFeedItems(key: String, shown: (ActivityView) -> ActivityView) = mapIndexed { index, view ->
    FeedItemEntity(key, view.uri.value, index, AtJson.encodeToString(ActivityView.serializer(), shown(view)))
}
