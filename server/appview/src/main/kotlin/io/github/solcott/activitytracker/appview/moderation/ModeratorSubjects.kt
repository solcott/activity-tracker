package io.github.solcott.activitytracker.appview.moderation

import io.github.solcott.activitytracker.appview.api.AvatarUrls
import io.github.solcott.activitytracker.appview.api.ModerationAccessLog
import io.github.solcott.activitytracker.appview.api.Views
import io.github.solcott.activitytracker.appview.api.XrpcError
import io.github.solcott.activitytracker.appview.api.commentViews
import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.Comments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetModerationRole
import io.github.solcott.activitytracker.lexicon.app.GetSubject
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.ktor.http.HttpStatusCode
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * `moderation/private-subjects`: serves moderators a reported activity or comment as the AppView indexed it, at any visibility, read as its
 * athlete (so no block between them hides it) with photo links signed for the moderator. The caller must be on the labeler's team as a
 * moderator or admin ([roles]). A Public subject that shows nothing beyond what a public viewer sees needs nothing more; any other (a
 * non-public one, or a public activity with details or gear in a narrower tier) only while Ozone has an open report or review on it or its
 * author, and each answer is written to the audit [log]. If Ozone can't be asked, the request is refused.
 */
class ModeratorSubjects(
    private val db: Database,
    private val views: Views,
    private val avatars: AvatarUrls,
    private val ozone: OzoneClient?,
    private val log: ModerationAccessLog,
    clock: Clock = Clock.System,
) {
    private val roles = RoleCache(clock)

    /** [viewer]'s role on the team: `admin`, `moderator`, `triage` or `verifier`; null when not on it. */
    suspend fun role(viewer: Did): GetModerationRole.Output = GetModerationRole.Output(roles.role(viewer.value, requireOzone()))

    suspend fun subject(moderator: Did, uri: AtUri): GetSubject.Output {
        val ozone = requireModerator(moderator)
        val subject = load(uri, moderator) ?: throw notFound()
        if (subject.visibility != Visibility.PUBLIC || subject.holdsPrivate) {
            val report = openReview(ozone, uri.value, subject.author) ?: throw notFound()
            log.record(moderator.value, uri.value, report.toString())
        }
        return subject.output
    }

    /** [holdsPrivate]: [output] shows more than a public viewer sees (details or gear in a narrower tier), so it needs a review too. */
    private class Loaded(val author: String, val visibility: String, val output: GetSubject.Output, val holdsPrivate: Boolean = false)

    /**
     * The activity or comment at [uri], at any visibility, as its athlete sees it, with photo links for [moderator]; null if not indexed.
     */
    private suspend fun load(uri: AtUri, moderator: Did): Loaded? = db.tx {
        when (uri.collection) {
            Nsid.FEED_ACTIVITY -> {
                val row = Activities.selectAll().where { Activities.uri eq uri.value }.singleOrNull() ?: return@tx null
                val author = row[Activities.authorDid]
                val view = with(views) { hydrate(listOf(row), Did(author), mediaViewer = moderator) }.single()
                val holdsPrivate =
                    view.visibilityOrPublic == Visibility.PUBLIC &&
                        with(views) { hydrate(listOf(row), viewer = null) }
                            .single()
                            .let { it.details != view.details || it.gear != view.gear }
                Loaded(author, view.visibilityOrPublic, GetSubject.Output(view.visibilityOrPublic, activity = view), holdsPrivate)
            }
            Nsid.FEED_COMMENT -> {
                val row =
                    Comments.join(Activities, JoinType.INNER, Comments.subjectUri, Activities.uri)
                        .selectAll()
                        .where { Comments.uri eq uri.value }
                        .firstOrNull() ?: return@tx null
                val visibility = Visibility.narrower(row[Comments.audience], row[Activities.visibility])
                val view = commentViews(listOf(row), row[Activities.authorDid], avatars).single()
                Loaded(row[Comments.authorDid], visibility, GetSubject.Output(visibility, comment = view))
            }
            else -> null
        }
    }

    /** The id of an open report or review on [uri] or on [author]'s account, from Ozone; null when there's none. */
    private suspend fun openReview(ozone: OzoneClient, uri: String, author: String): Long? =
        (ozone.statuses(uri) + ozone.statuses(author)).firstOrNull { it.reviewState in OzoneClient.OPEN_REVIEW }?.id

    /** Ozone, once [moderator] is on its team as a moderator or admin. */
    private suspend fun requireModerator(moderator: Did): OzoneClient {
        val ozone = requireOzone()
        if (roles.role(moderator.value, ozone) !in GetModerationRole.MODERATOR_ROLES) {
            throw XrpcError(HttpStatusCode.Forbidden.value, "NotModerator", "Only the labeler's moderators and admins may open subjects")
        }
        return ozone
    }

    private fun requireOzone(): OzoneClient =
        ozone ?: throw XrpcError(HttpStatusCode.ServiceUnavailable.value, "LabelerUnavailable", "No labeler team is configured")

    private fun notFound() = XrpcError(HttpStatusCode.BadRequest.value, "NotFound", "No such subject under review")

    /** Team roles from `listMembers`, read at most every [ttl]. */
    private class RoleCache(private val clock: Clock, private val ttl: Duration = 5.minutes) {
        private val mutex = Mutex()
        private var roles: Map<String, String> = emptyMap()
        private var readAt: Instant? = null

        suspend fun role(did: String, ozone: OzoneClient): String? = mutex.withLock {
            val stale = readAt?.let { clock.now() - it >= ttl } ?: true
            if (stale) {
                roles = ozone.members().filter { it.disabled != true }.associate { it.did to it.role.substringAfter("#role").lowercase() }
                readAt = clock.now()
            }
            roles[did]
        }
    }
}
