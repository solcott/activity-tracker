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
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ActivityViewerState
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.GetKudos
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.Paging
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewerState
import io.ktor.http.HttpStatusCode
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.countDistinct
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Read side: turns indexed rows into lexicon views. */
class Views(private val db: Database, private val avatars: AvatarUrls) {
    suspend fun timeline(viewer: Did, params: GetTimeline.Params): GetTimeline.Output = db.tx {
        val authors =
            Follows.select(Follows.subjectDid).where { Follows.authorDid eq viewer.value }.map { it[Follows.subjectDid] } + viewer.value
        val (views, cursor) = activityPage(Activities.authorDid inList authors, params.limit, params.cursor, viewer)
        GetTimeline.Output(views, cursor)
    }

    suspend fun authorActivities(
        viewer: Did?,
        params: GetAuthorActivities.Params,
    ): GetAuthorActivities.Output = db.tx {
        val did =
            resolveActor(params.actor) ?: throw XrpcError(HttpStatusCode.BadRequest.value, "ActorNotFound", "Unknown actor ${params.actor}")
        val (views, cursor) = activityPage(Activities.authorDid eq did, params.limit, params.cursor, viewer)
        GetAuthorActivities.Output(views, cursor)
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
            details = visibleDetails(row, viewer),
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
                .where { filter and keysetBefore(Activities.sortAt, Activities.uri, cursor) }
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

        return rows.mapNotNull { row ->
            val uri = row[Activities.uri]
            ActivityView(
                uri = AtUri(uri),
                cid = Cid(row[Activities.cid]),
                author = authors[row[Activities.authorDid]] ?: return@mapNotNull null,
                record = AtJson.decodeFromString(ActivityRecord.serializer(), row[Activities.record]),
                kudosCount = kudosCounts[uri] ?: 0,
                commentCount = commentCounts[uri] ?: 0,
                indexedAt = Instant.fromEpochMilliseconds(row[Activities.indexedAt]),
                viewer = viewer?.let { ActivityViewerState(kudos = viewerKudos[uri]?.let(::AtUri)) },
            )
        }
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
