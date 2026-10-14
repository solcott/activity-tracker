package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.Comments
import io.github.solcott.activitytracker.appview.db.Follows
import io.github.solcott.activitytracker.appview.db.Kudos
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
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.FeedFilterParams
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivitiesByStart
import io.github.solcott.activitytracker.lexicon.app.GetKudos
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.Paging
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewerState
import io.github.solcott.activitytracker.lexicon.app.RecordingView
import io.ktor.http.HttpStatusCode
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.Column
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
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Read side: turns indexed rows into lexicon views. */
class Views(private val db: Database, private val avatars: AvatarUrls, private val mediaUrls: MediaUrls) {
    suspend fun timeline(viewer: Did, params: GetTimeline.Params): GetTimeline.Output = db.tx {
        val authors =
            Follows.select(Follows.subjectDid).where { Follows.authorDid eq viewer.value }.map { it[Follows.subjectDid] } + viewer.value
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

    suspend fun activity(viewer: Did?, params: GetActivity.Params): GetActivity.Output = db.tx {
        val row =
            Activities.selectAll().where { Activities.uri eq params.uri.value }.singleOrNull()
                ?: throw XrpcError(HttpStatusCode.BadRequest.value, "NotFound", "Activity not found")
        val comments =
            Comments.selectAll()
                .where { Comments.subjectUri eq params.uri.value }
                .orderBy(Comments.createdAt to SortOrder.ASC)
                .limit(HttpStatusCode.InternalServerError.value)
                .toList()
        val authors = basicProfiles(comments.map { it[Comments.authorDid] }, avatars)
        GetActivity.Output(
            activity = hydrate(listOf(row), viewer).single(),
            comments =
                comments.mapNotNull { c ->
                    CommentView(
                        uri = AtUri(c[Comments.uri]),
                        cid = Cid(c[Comments.cid]),
                        author = authors[c[Comments.authorDid]] ?: return@mapNotNull null,
                        text = c[Comments.text],
                        createdAt = Instant.fromEpochMilliseconds(c[Comments.createdAt]),
                    )
                },
        )
    }

    /**
     * Athletes who gave kudos on an activity, each once (a repo may hold duplicate kudos records), most recent kudos first. Keyset
     * pagination over authors on (latest indexed_at, author_did) descending; cursor is `"<indexedAt>::<authorDid>"`.
     */
    suspend fun kudos(params: GetKudos.Params): GetKudos.Output = db.tx {
        val uri = params.uri.value
        if (Activities.select(Activities.uri).where { Activities.uri eq uri }.empty()) {
            throw XrpcError(HttpStatusCode.BadRequest.value, "NotFound", "Activity not found")
        }
        val pageSize = Paging.clamp(params.limit)
        val latest = Kudos.indexedAt.max()
        val after =
            params.cursor?.let(::parseCursor)?.let { (sortValue, did) ->
                (latest less sortValue) or ((latest eq sortValue) and (Kudos.authorDid less did))
            } ?: Op.TRUE
        val rows =
            Kudos.select(Kudos.authorDid, latest)
                .where { Kudos.subjectUri eq uri }
                .groupBy(Kudos.authorDid)
                .having { after }
                .orderBy(latest to SortOrder.DESC, Kudos.authorDid to SortOrder.DESC)
                .limit(pageSize)
                .toList()
        val next = rows.takeIf { it.size == pageSize }?.last()?.let { "${it[latest]}::${it[Kudos.authorDid]}" }
        val profiles = basicProfiles(rows.map { it[Kudos.authorDid] }, avatars)
        GetKudos.Output(params.uri, rows.mapNotNull { profiles[it[Kudos.authorDid]] }, next)
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
            activitiesCount = Activities.selectAll().where { Activities.authorDid eq did }.count(),
            followersCount = Follows.selectAll().where { Follows.subjectDid eq did }.count(),
            followsCount = Follows.selectAll().where { Follows.authorDid eq did }.count(),
            viewer =
                viewer?.let { v ->
                    ProfileViewerState(
                        following =
                            Follows.select(Follows.uri)
                                .where { (Follows.authorDid eq v.value) and (Follows.subjectDid eq did) }
                                .firstOrNull()
                                ?.let { AtUri(it[Follows.uri]) }
                    )
                },
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
                .where { filter and shownInFeeds() and keysetBefore(Activities.sortAt, Activities.uri, cursor) }
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
        val kudosCounts = countBySubject(Kudos, Kudos.subjectUri, Kudos.authorDid, uris, distinct = true)
        val commentCounts = countBySubject(Comments, Comments.subjectUri, Comments.uri, uris)
        val viewerKudos =
            viewer
                ?.let { v ->
                    Kudos.select(Kudos.uri, Kudos.subjectUri)
                        .where { (Kudos.authorDid eq v.value) and (Kudos.subjectUri inList uris) }
                        .associate { it[Kudos.subjectUri] to it[Kudos.uri] }
                }
                .orEmpty()

        val recordings = recordingsOf(rows.filter { it[Activities.recordingOf] == null }.map { it[Activities.uri] })

        return rows.mapNotNull { row ->
            val uri = row[Activities.uri]
            val record = AtJson.decodeFromString(ActivityRecord.serializer(), row[Activities.record])
            ActivityView(
                uri = AtUri(uri),
                cid = Cid(row[Activities.cid]),
                author = authors[row[Activities.authorDid]] ?: return@mapNotNull null,
                record = record,
                kudosCount = kudosCounts[uri] ?: 0,
                commentCount = commentCounts[uri] ?: 0,
                indexedAt = Instant.fromEpochMilliseconds(row[Activities.indexedAt]),
                viewer = viewer?.let { ActivityViewerState(kudos = viewerKudos[uri]?.let(::AtUri)) },
                recordings = recordings[uri],
                media = mediaViews(mediaUrls, row[Activities.authorDid], record.media),
            )
        }
    }

    /** Each of [primaries]' other recordings, oldest first. */
    private fun JdbcTransaction.recordingsOf(primaries: List<String>): Map<String, List<RecordingView>> =
        if (primaries.isEmpty()) {
            emptyMap()
        } else {
            Activities.select(Activities.uri, Activities.recordingOf, Activities.startedAt, Activities.record)
                .where { Activities.recordingOf inList primaries }
                .orderBy(Activities.startedAt to SortOrder.ASC, Activities.uri to SortOrder.ASC)
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
     * Leaves out another recording of an activity that's indexed and is an activity in its own right: the feed shows that one, listing its
     * recordings. A recording whose activity is gone, or is itself a recording, shows on its own.
     */
    private fun shownInFeeds(): Op<Boolean> {
        val recorded = Activities.alias("recorded")
        return Activities.recordingOf.isNull() or
            NotExists(
                recorded.select(recorded[Activities.uri]).where {
                    (recorded[Activities.uri] eq Activities.recordingOf) and recorded[Activities.recordingOf].isNull()
                }
            )
    }

    private fun countBySubject(
        table: org.jetbrains.exposed.v1.core.Table,
        subject: Column<String>,
        key: Column<String>,
        uris: List<String>,
        distinct: Boolean = false,
    ): Map<String, Long> {
        // Distinct keys let kudos count athletes rather than records, since repos may hold duplicates.
        val count = if (distinct) key.countDistinct() else key.count()
        return table.select(subject, count).where { subject inList uris }.groupBy(subject).associate { it[subject] to it[count] }
    }
}

class XrpcError(val status: Int, val error: String, override val message: String, override val cause: Throwable? = null) :
    Exception(message, cause)

/** A recording's device as `#recordingView` allows it: the record's own is untrusted. */
private const val MAX_DEVICE_LENGTH = 256

/** The indexer kept only photos it can serve (see `Indexer.boundedPhotos`). */
private fun mediaViews(urls: MediaUrls, author: String, media: List<ActivityMedia>?): List<ActivityMediaView>? =
    media
        ?.filterIsInstance<ActivityMedia.Image>()
        ?.map { ActivityMediaView.Image(fullsize = urls.url(author, it.image.ref.link), alt = it.alt, aspectRatio = it.aspectRatio) }
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
