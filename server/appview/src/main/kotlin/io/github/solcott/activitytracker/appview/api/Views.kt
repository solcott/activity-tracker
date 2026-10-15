package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.Comments
import io.github.solcott.activitytracker.appview.db.Follows
import io.github.solcott.activitytracker.appview.db.Likes
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.ActivityMediaView
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ActivityViewerState
import io.github.solcott.activitytracker.lexicon.app.FeedFilterParams
import io.github.solcott.activitytracker.lexicon.app.FollowPolicy
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivitiesByStart
import io.github.solcott.activitytracker.lexicon.app.GetComments
import io.github.solcott.activitytracker.lexicon.app.GetLikes
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.Paging
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.RecordingView
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.ktor.http.HttpStatusCode
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.NotExists
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.countDistinct
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Read side: turns indexed rows into lexicon views. */
class Views(private val db: Database, private val avatars: AvatarUrls, private val mediaUrls: MediaUrls) {
    suspend fun timeline(viewer: Did, params: GetTimeline.Params): GetTimeline.Output = db.tx {
        val authors =
            Follows.select(Follows.subjectDid)
                .where { (Follows.authorDid eq viewer.value) and effectiveFollow() }
                .map { it[Follows.subjectDid] } + viewer.value
        val filter = feedFilter(params.type, params.since, params.until)
        val (views, cursor) = activityPage((Activities.authorDid inList authors) and filter, params.limit, params.cursor, viewer)
        GetTimeline.Output(views, cursor)
    }

    suspend fun authorActivities(
        viewer: Did?,
        params: GetAuthorActivities.Params,
    ): GetAuthorActivities.Output = db.tx {
        val did =
            resolveActor(params.actor) ?: throw XrpcError(HttpStatusCode.BadRequest.value, "ActorNotFound", "Unknown actor ${params.actor}")
        val filter = feedFilter(params.type, params.since, params.until)
        val (views, cursor) = activityPage((Activities.authorDid eq did) and filter, params.limit, params.cursor, viewer)
        GetAuthorActivities.Output(views, cursor)
    }

    suspend fun authorActivitiesByStart(
        viewer: Did?,
        params: GetAuthorActivitiesByStart.Params,
    ): GetAuthorActivitiesByStart.Output = db.tx {
        val did =
            resolveActor(params.actor) ?: throw XrpcError(HttpStatusCode.BadRequest.value, "ActorNotFound", "Unknown actor ${params.actor}")
        val since = params.since.toEpochMilliseconds()
        val until = params.until.toEpochMilliseconds()
        if (until <= since) throw XrpcError(HttpStatusCode.BadRequest.value, "InvalidRequest", "until must be after since")
        val sport = params.sport?.let { Activities.sport eq it } ?: Op.TRUE
        val pageSize = Paging.clamp(params.limit)
        val rows =
            Activities.selectAll()
                .where {
                    (Activities.authorDid eq did) and
                        visibleTo(viewer) and
                        (Activities.startedAt greaterEq since) and
                        (Activities.startedAt less until) and
                        sport and
                        keysetAfter(Activities.startedAt, Activities.uri, params.cursor)
                }
                .orderBy(Activities.startedAt to SortOrder.ASC, Activities.uri to SortOrder.ASC)
                .limit(pageSize)
                .toList()
        val next = rows.takeIf { it.size == pageSize }?.last()?.let { "${it[Activities.startedAt]}::${it[Activities.uri]}" }
        GetAuthorActivitiesByStart.Output(hydrate(rows, viewer), next)
    }

    /** The activity alone; its comments come from [comments]. `comments` is still written, empty, for clients from before that. */
    suspend fun activity(viewer: Did?, params: GetActivity.Params): GetActivity.Output = db.tx {
        // An activity the viewer may not see answers as if it didn't exist.
        val row =
            Activities.selectAll().where { (Activities.uri eq params.uri.value) and visibleTo(viewer) }.singleOrNull() ?: throw notFound()
        GetActivity.Output(hydrate(listOf(row), viewer).single())
    }

    /**
     * An activity's comments listed for the viewer ([commentsListedFor], the rule [hydrate] counts by), oldest first, in one of three
     * modes: a page of threads ([threadPage]), the `uris` asked for, or with `root` a page of that thread's replies. Comment keysets are on
     * (created_at, uri) ascending, cursor `"<createdAt>::<uri>"`, taken from the last row read rather than the last view built, since a
     * view is built for every row read.
     */
    suspend fun comments(viewer: Did?, params: GetComments.Params): GetComments.Output = db.tx {
        val uris = params.uris
        if (uris != null && uris.size > GetComments.MAX_URIS)
            invalidRequest("At most ${GetComments.MAX_URIS} uris may be given, not ${uris.size}")
        if (uris != null && params.root != null) invalidRequest("uris and root can't be given together")
        val athlete = requireVisibleActivity(params.uri.value, viewer)
        val listed = (Comments.subjectUri eq params.uri.value) and commentsListedFor(viewer)
        val root = params.root
        when {
            uris != null -> {
                val rows = listedRows(listed and (Comments.uri inList uris.map { it.value }))
                GetComments.Output(params.uri, commentViews(rows, athlete, avatars))
            }

            root != null -> {
                val pageSize = Paging.clamp(params.limit)
                val replies = (Comments.threadUri eq root.value) and (Comments.uri neq root.value)
                val rows = listedRows(listed and replies and keysetAfter(Comments.createdAt, Comments.uri, params.cursor), pageSize)
                val next = rows.takeIf { it.size == pageSize }?.last()?.let(::commentCursor)
                GetComments.Output(params.uri, commentViews(rows, athlete, avatars), next)
            }

            else -> threadPage(params, avatars, listed, athlete)
        }
    }

    /**
     * Athletes who liked an activity, each once (a repo may hold duplicate like records), most recent like first, counting only givers who
     * may see it now ([admittedLikes]). Keyset pagination over authors on (latest indexed_at, author_did) descending; cursor is
     * `"<indexedAt>::<authorDid>"`.
     */
    suspend fun likes(viewer: Did?, params: GetLikes.Params): GetLikes.Output = db.tx {
        val uri = params.uri.value
        // An activity the viewer may not see answers as if it didn't exist, revealing none of its likes.
        requireVisibleActivity(uri, viewer)
        val pageSize = Paging.clamp(params.limit)
        val latest = Likes.indexedAt.max()
        val after =
            params.cursor?.let(::parseCursor)?.let { (sortValue, did) ->
                (latest less sortValue) or ((latest eq sortValue) and (Likes.authorDid less did))
            } ?: Op.TRUE
        val rows =
            admittedLikes()
                .select(Likes.authorDid, latest)
                .where { (Likes.subjectUri eq uri) and visibleTo(Likes.authorDid) }
                .groupBy(Likes.authorDid)
                .having { after }
                .orderBy(latest to SortOrder.DESC, Likes.authorDid to SortOrder.DESC)
                .limit(pageSize)
                .toList()
        val next = rows.takeIf { it.size == pageSize }?.last()?.let { "${it[latest]}::${it[Likes.authorDid]}" }
        val profiles = basicProfiles(rows.map { it[Likes.authorDid] }, avatars)
        GetLikes.Output(params.uri, rows.mapNotNull { profiles[it[Likes.authorDid]] }, next)
    }

    suspend fun profile(viewer: Did?, actor: String): ProfileView = db.tx {
        val did = resolveActor(actor) ?: throw XrpcError(HttpStatusCode.BadRequest.value, "ProfileNotFound", "Unknown actor $actor")
        val row = Actors.selectAll().where { Actors.did eq did }.single()
        ProfileView(
            did = Did(row[Actors.did]),
            handle = Handle(row[Actors.handle]),
            displayName = row[Actors.displayName],
            description = row[Actors.description],
            avatar = avatars.url(did, row[Actors.avatarCid]),
            details = publicDetails(row),
            units = row[Actors.units],
            followPolicy = row[Actors.followPolicy] ?: FollowPolicy.OPEN,
            activitiesCount = Activities.selectAll().where { (Activities.authorDid eq did) and visibleTo(viewer) }.count(),
            followersCount = Follows.selectAll().where { (Follows.subjectDid eq did) and effectiveFollow() }.count(),
            followsCount = Follows.selectAll().where { (Follows.authorDid eq did) and effectiveFollow() }.count(),
            viewer = viewer?.let { v -> viewerState(v.value, did) },
        )
    }

    /** Keyset pagination on (sort_at, uri) descending; cursor is `"<sortAt>::<uri>"`. */
    private fun JdbcTransaction.activityPage(
        filter: Op<Boolean>,
        limit: Int?,
        cursor: String?,
        viewer: Did?,
    ): Pair<List<ActivityView>, String?> {
        val pageSize = Paging.clamp(limit)
        val rows =
            Activities.selectAll()
                .where { filter and visibleTo(viewer) and shownInFeeds() and keysetBefore(Activities.sortAt, Activities.uri, cursor) }
                .orderBy(Activities.sortAt to SortOrder.DESC, Activities.uri to SortOrder.DESC)
                .limit(pageSize)
                .toList()
        val next = rows.takeIf { it.size == pageSize }?.last()?.let { "${it[Activities.sortAt]}::${it[Activities.uri]}" }
        return hydrate(rows, viewer) to next
    }

    private fun JdbcTransaction.hydrate(rows: List<ResultRow>, viewer: Did?): List<ActivityView> {
        if (rows.isEmpty()) return emptyList()
        val uris = rows.map { it[Activities.uri] }
        val authors = basicProfiles(rows.map { it[Activities.authorDid] }, avatars)
        // Every row is one the viewer may see, so a like counts once its giver may see the activity too.
        // Distinct givers, not records, since repos may hold duplicates.
        val likeCount = Likes.authorDid.countDistinct()
        val likeCounts =
            admittedLikes()
                .select(Likes.subjectUri, likeCount)
                .where { (Likes.subjectUri inList uris) and visibleTo(Likes.authorDid) }
                .groupBy(Likes.subjectUri)
                .associate { it[Likes.subjectUri] to it[likeCount] }
        val commentCount = Comments.uri.count()
        val commentCounts =
            admittedComments()
                .select(Comments.subjectUri, commentCount)
                .where { (Comments.subjectUri inList uris) and commentsVisibleTo(viewer) and not(hiddenByAthlete()) }
                .groupBy(Comments.subjectUri)
                .associate { it[Comments.subjectUri] to it[commentCount] }
        // The viewer may see every row, so their own like is always admitted.
        val viewerLikes =
            viewer
                ?.let { v ->
                    Likes.select(Likes.uri, Likes.subjectUri)
                        .where { (Likes.authorDid eq v.value) and (Likes.subjectUri inList uris) }
                        .associate { it[Likes.subjectUri] to it[Likes.uri] }
                }
                .orEmpty()

        val recordings =
            recordingsOf(rows.filter { it[Activities.recordingOf] == null }.associate { it[Activities.uri] to it[Activities.visibility] })

        return rows.mapNotNull { row ->
            val uri = row[Activities.uri]
            val record = AtJson.decodeFromString(ActivityRecord.serializer(), row[Activities.record])
            val visibility = row[Activities.visibility]
            ActivityView(
                uri = AtUri(uri),
                cid = Cid(row[Activities.cid]),
                author = authors[row[Activities.authorDid]] ?: return@mapNotNull null,
                record = record,
                likeCount = likeCounts[uri] ?: 0,
                commentCount = commentCounts[uri] ?: 0,
                indexedAt = Instant.fromEpochMilliseconds(row[Activities.indexedAt]),
                visibility = visibility,
                viewer = viewer?.let { ActivityViewerState(like = viewerLikes[uri]?.let(::AtUri)) },
                recordings = recordings[uri],
                media = mediaViews(mediaUrls, row[Activities.authorDid], record.media, visibility, viewer),
            )
        }
    }

    /**
     * Each of [primaries]' other recordings, oldest first, given each primary's visibility: a recording counts only at its primary's
     * visibility, so it never shows anyone more than the primary does.
     */
    private fun JdbcTransaction.recordingsOf(primaries: Map<String, String>): Map<String, List<RecordingView>> =
        if (primaries.isEmpty()) {
            emptyMap()
        } else {
            Activities.select(Activities.uri, Activities.recordingOf, Activities.startedAt, Activities.record, Activities.visibility)
                .where { Activities.recordingOf inList primaries.keys }
                .orderBy(Activities.startedAt to SortOrder.ASC, Activities.uri to SortOrder.ASC)
                .filter { primaries[it[Activities.recordingOf]] == it[Activities.visibility] }
                .groupBy({ it[Activities.recordingOf].orEmpty() }) {
                    val record = AtJson.decodeFromString(ActivityRecord.serializer(), it[Activities.record])
                    RecordingView(
                        AtUri(it[Activities.uri]),
                        Instant.fromEpochMilliseconds(it[Activities.startedAt]),
                        record.device?.take(MAX_DEVICE_LENGTH),
                    )
                }
        }

    /**
     * Leaves out another recording of an activity that's indexed, is an activity in its own right and has the same visibility: the feed
     * shows that one, listing its recordings. A recording whose activity is gone, is itself a recording, or is at another visibility shows
     * on its own.
     */
    private fun shownInFeeds(): Op<Boolean> {
        val recorded = Activities.alias("recorded")
        return Activities.recordingOf.isNull() or
            NotExists(
                recorded.select(recorded[Activities.uri]).where {
                    (recorded[Activities.uri] eq Activities.recordingOf) and
                        recorded[Activities.recordingOf].isNull() and
                        (recorded[Activities.visibility] eq Activities.visibility)
                }
            )
    }
}

class XrpcError(val status: Int, val error: String, override val message: String, override val cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Likes joined to the activity they name, for [visibleTo] with the like's giver as viewer: a like is served only while its giver may see
 * the activity at its current visibility, wherever the like is stored. Likes follow the activity when it's narrowed or widened.
 */
private fun admittedLikes() = Likes.join(Activities, JoinType.INNER, Likes.subjectUri, Activities.uri)

/** A recording's device as `#recordingView` allows it: the record's own is untrusted. */
private const val MAX_DEVICE_LENGTH = 256

/**
 * The indexer kept only photos it can serve (see `Indexer.boundedPhotos`). A non-public activity's photos get URLs signed for [viewer];
 * with no viewer (or no way to sign) they're left out.
 */
private fun mediaViews(
    urls: MediaUrls,
    author: String,
    media: List<ActivityMedia>?,
    visibility: String,
    viewer: Did?,
): List<ActivityMediaView>? =
    media
        ?.filterIsInstance<ActivityMedia.Image>()
        ?.mapNotNull {
            val cid = it.image.ref.link
            val url = if (visibility == Visibility.PUBLIC) urls.url(author, cid) else viewer?.let { v -> urls.privateUrl(author, cid, v) }
            url?.let { u -> ActivityMediaView.Image(fullsize = u, alt = it.alt, aspectRatio = it.aspectRatio) }
        }
        ?.ifEmpty { null }

/**
 * The optional filters `getTimeline` and `getAuthorActivities` share: any of [types] (a sport matches every sub-sport, `sport:subSport`
 * only that pair), started in [since, until). Untrusted input, so it is capped and validated before any query.
 */
private fun feedFilter(types: List<String>?, since: Instant?, until: Instant?): Op<Boolean> {
    val tokens = types.orEmpty()
    if (tokens.size > FeedFilterParams.MAX_TYPES) invalidRequest("At most ${FeedFilterParams.MAX_TYPES} types")
    val typeOps = tokens.map { token ->
        val parts = token.split(FeedFilterParams.TYPE_SEPARATOR)
        if (token.length > FeedFilterParams.MAX_TYPE_LENGTH || parts.size > 2 || parts.any { it.isEmpty() }) {
            invalidRequest("Malformed type: expected 'sport' or 'sport:subSport'")
        }
        if (parts.size == 1) Activities.sport eq parts[0] else (Activities.sport eq parts[0]) and (Activities.subSport eq parts[1])
    }
    if (since != null && until != null && until <= since) invalidRequest("until must be after since")
    return listOfNotNull(
            typeOps.reduceOrNull { a, b -> a or b },
            since?.let { Activities.startedAt greaterEq it.toEpochMilliseconds() },
            until?.let { Activities.startedAt less it.toEpochMilliseconds() },
        )
        .fold(Op.TRUE) { acc: Op<Boolean>, op -> acc and op }
}

private fun invalidRequest(message: String): Nothing = throw XrpcError(HttpStatusCode.BadRequest.value, "InvalidRequest", message)

/** The athlete whose activity [uri] is; [notFound] when the viewer may not see it, as if it didn't exist. */
private fun JdbcTransaction.requireVisibleActivity(uri: String, viewer: Did?): String =
    Activities.select(Activities.authorDid)
        .where { (Activities.uri eq uri) and visibleTo(viewer) }
        .singleOrNull()
        ?.get(Activities.authorDid) ?: throw notFound()

private fun notFound() = XrpcError(HttpStatusCode.BadRequest.value, "NotFound", "Activity not found")
