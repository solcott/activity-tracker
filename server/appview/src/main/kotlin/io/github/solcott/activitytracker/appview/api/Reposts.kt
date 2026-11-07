package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Posts
import io.github.solcott.activitytracker.appview.db.Reposts
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.ReasonRepost
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

// Which reposts of posts (feed/reposts) a viewer is shown: the like rule for posts (`Subject.kt`), plus the post's own rules, so a repost
// never widens who sees a post. Timelines add their author set and mutes on top, and place each post once (feed/timeline-order).

/** Reposts joined to the post they name, for [repostsShownTo]; a repost of a post that isn't indexed has nothing to join. */
internal fun admittedReposts() = Reposts.join(Posts, JoinType.INNER, Reposts.subjectUri, Posts.uri)

/**
 * The reposts over [admittedReposts] that [viewer] is shown: the viewer may see the post ([postRows]), the reposter may see it at its
 * current tier (which also leaves out a reposter in a block with its author), and the reposter is neither in a block with the viewer nor
 * hidden from them. Every repost count and list uses it, so a count never includes a reposter the list leaves out. Mutes don't apply.
 */
internal fun repostsShownTo(viewer: Did?): Op<Boolean> =
    postRows(viewer) and Subject.POST.visibleTo(Reposts.authorDid) and accountShownTo(viewer, Reposts.authorDid)

/**
 * A repost by [reposter] (a column of `reposts` or an alias of it) of the post in the outer query's `posts` row that [viewer]'s timeline
 * takes: the reposter may see the post and is shown to the viewer, as in [repostsShownTo], is the viewer or in [followed], and isn't muted.
 */
private fun timelineReposter(viewer: Did, followed: Query, reposter: Column<String>): Op<Boolean> =
    Subject.POST.visibleTo(reposter) and
        accountShownTo(viewer, reposter) and
        ((reposter eq viewer.value) or (reposter inSubQuery followed)) and
        notMuted(viewer, reposter)

/**
 * The reposts in [viewer]'s timeline (feed/reposts): those [repostsShownTo] them by the viewer or an athlete in [followed], neither of whom
 * nor the post's author the viewer mutes.
 */
internal fun timelineReposts(viewer: Did, followed: Query): Op<Boolean> =
    postRows(viewer) and notMuted(viewer, Posts.authorDid) and timelineReposter(viewer, followed, Reposts.authorDid)

/**
 * Whether [viewer]'s timeline holds a repost of the outer query's post placed after ([sort], then [uri] when given): such a post is placed
 * at that repost instead (feed/timeline-order).
 */
private fun laterTimelineRepost(viewer: Did, followed: Query, sort: Column<Long>, uri: Column<String>?): Op<Boolean> {
    val later = Reposts.alias("later_reposts")
    val after =
        if (uri == null) later[Reposts.sortAt] greater sort
        else (later[Reposts.sortAt] greater sort) or ((later[Reposts.sortAt] eq sort) and (later[Reposts.uri] greater uri))
    return exists(
        later.select(later[Reposts.uri]).where {
            (later[Reposts.subjectUri] eq Posts.uri) and timelineReposter(viewer, followed, later[Reposts.authorDid]) and after
        }
    )
}

/** Over a timeline's own post rows: leaves out a post its timeline places at a later repost instead. */
internal fun notRepostedLater(viewer: Did, followed: Query): Op<Boolean> = not(laterTimelineRepost(viewer, followed, Posts.sortAt, null))

/**
 * A page of [viewer]'s timeline reposts before [cursor], newest first, each the latest of its post there and later than the post itself
 * would be placed (a tie goes to the post), so a post comes at most once. Rows hold both the repost's and the post's columns; their key is
 * the repost's URI. [authors] says which posts the timeline holds as themselves.
 */
internal fun JdbcTransaction.timelineRepostPage(
    viewer: Did,
    followed: Query,
    authors: Op<Boolean>,
    limit: Int,
    cursor: String?,
): List<ResultRow> =
    admittedReposts()
        .selectAll()
        .where {
            timelineReposts(viewer, followed) and
                keysetBefore(Reposts.sortAt, Reposts.uri, cursor) and
                not(laterTimelineRepost(viewer, followed, Reposts.sortAt, Reposts.uri)) and
                not(authors and (Posts.sortAt greaterEq Reposts.sortAt))
        }
        .orderBy(Reposts.sortAt to SortOrder.DESC, Reposts.uri to SortOrder.DESC)
        .limit(limit)
        .toList()

/** Why each of the repost [rows] is in [viewer]'s timeline, by repost URI: its reposter, and how many others there reposted the post. */
internal fun JdbcTransaction.repostReasons(
    rows: List<ResultRow>,
    viewer: Did,
    followed: Query,
    profiles: Map<String, ProfileViewBasic>,
): Map<String, ReasonRepost> {
    if (rows.isEmpty()) return emptyMap()
    val reposters =
        admittedReposts()
            .select(Reposts.subjectUri, Reposts.authorDid)
            .where { (Reposts.subjectUri inList rows.map { it[Reposts.subjectUri] }) and timelineReposts(viewer, followed) }
            .withDistinct()
            .toList()
            .groupBy({ it[Reposts.subjectUri] }) { it[Reposts.authorDid] }
    return rows
        .mapNotNull { row ->
            val by = profiles[row[Reposts.authorDid]] ?: return@mapNotNull null
            val others = reposters[row[Reposts.subjectUri]].orEmpty().count { it != row[Reposts.authorDid] }.toLong()
            row[Reposts.uri] to ReasonRepost(by, Instant.fromEpochMilliseconds(row[Reposts.sortAt]), others)
        }
        .toMap()
}

/** [viewer]'s reposts of each of the posts [uris], by post URI. */
internal fun JdbcTransaction.viewerReposts(viewer: Did, uris: List<String>): Map<String, String> =
    Reposts.select(Reposts.uri, Reposts.subjectUri)
        .where { (Reposts.authorDid eq viewer.value) and (Reposts.subjectUri inList uris) }
        .associate { it[Reposts.subjectUri] to it[Reposts.uri] }
