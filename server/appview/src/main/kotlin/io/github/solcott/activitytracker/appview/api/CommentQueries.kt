package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.CommentHides
import io.github.solcott.activitytracker.appview.db.Comments
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.GetComments
import io.github.solcott.activitytracker.lexicon.app.Paging
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.ThreadView
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select

// Who sees each comment and reply, and `getComments`' pages of threads ([Views.comments]). Every comment query, count or list, goes
// through [admittedComments] and [commentsListedFor] or [commentsVisibleTo], so the count matches the pages.

/**
 * Comments joined to the activity they name, for [commentsVisibleTo]. Like likes, a comment is served by the activity's current visibility
 * wherever it's stored, but never wider than the audience it was written for.
 */
internal fun admittedComments() =
    Comments.join(Activities, JoinType.INNER, Comments.subjectUri, Activities.uri)
        .join(
            Roots,
            JoinType.LEFT,
            Comments.threadUri,
            Roots[Comments.uri],
            additionalConstraint = { Roots[Comments.subjectUri] eq Comments.subjectUri },
        )

/** The top-level comment of each comment's thread, in [admittedComments], when it's indexed on the same activity. */
private val Roots = Comments.alias("roots")

/**
 * Comments on activities [viewer] may already see (the caller filters the activities) that they may see too: while the commenter may still
 * see the activity at its current visibility, and the comment's audience admits the viewer, judged against the activity's athlete. Tiers
 * nest, so a comment is shown at the narrower of the activity's visibility and its audience.
 */
internal fun commentsVisibleTo(viewer: Did?): Op<Boolean> =
    visibleTo(Comments.authorDid) and
        visibleTo(viewer, visibility = Comments.audience, author = Activities.authorDid) and
        rootAdmits(viewer)

/**
 * A reply is also capped by its thread's top-level comment's audience while that's indexed on the activity: clients cap a reply by its
 * parent, so an honest chain only narrows, and a reply from a client that doesn't still never reaches past the root's audience. Only the
 * root, not every ancestor, is checked. A top-level comment joins itself, which adds nothing.
 */
private fun rootAdmits(viewer: Did?): Op<Boolean> =
    Roots[Comments.uri].isNull() or visibleTo(viewer, visibility = Roots[Comments.audience], author = Activities.authorDid)

/**
 * The activity's athlete hid the comment (`feed.commentHide`), from their repo or their interactions space. Checked here rather than at
 * index time, so a hide indexed before its comment counts, and a hide by anyone else changes nothing.
 */
internal fun hiddenByAthlete(): Op<Boolean> =
    exists(
        CommentHides.select(CommentHides.uri).where {
            (CommentHides.commentUri eq Comments.uri) and (CommentHides.authorDid eq Activities.authorDid)
        }
    )

/**
 * The comments listed for [viewer]: [commentsVisibleTo], and a hidden one ([hiddenByAthlete]) only for the activity's athlete and its
 * commenter. It's never counted ([hydrate] leaves it out for everyone), so the count matches the listed comments that aren't hidden.
 */
internal fun commentsListedFor(viewer: Did?): Op<Boolean> {
    val shown = commentsVisibleTo(viewer)
    if (viewer == null) return shown and not(hiddenByAthlete())
    return shown and (not(hiddenByAthlete()) or (Activities.authorDid eq viewer.value) or (Comments.authorDid eq viewer.value))
}

/**
 * Views of comment [rows] on an activity of [athlete]'s, in order. An author with no profile row shows as `handle.invalid` rather than the
 * comment being left out, so what's listed always matches what [commentsVisibleTo] counts. Only the athlete and each commenter are listed
 * hidden comments ([commentsListedFor]), so any listed one that's hidden is theirs to see marked.
 */
internal fun JdbcTransaction.commentViews(rows: List<ResultRow>, athlete: String, avatars: AvatarUrls): List<CommentView> {
    if (rows.isEmpty()) return emptyList()
    val authors = basicProfiles(rows.map { it[Comments.authorDid] }, avatars)
    val hidden =
        CommentHides.select(CommentHides.commentUri)
            .where { (CommentHides.commentUri inList rows.map { it[Comments.uri] }) and (CommentHides.authorDid eq athlete) }
            .map { it[CommentHides.commentUri] }
            .toSet()
    return rows.map { c ->
        CommentView(
            uri = AtUri(c[Comments.uri]),
            cid = Cid(c[Comments.cid]),
            author = authors[c[Comments.authorDid]] ?: ProfileViewBasic(Did(c[Comments.authorDid]), Handle(UNKNOWN_HANDLE)),
            text = c[Comments.text],
            createdAt = Instant.fromEpochMilliseconds(c[Comments.createdAt]),
            hidden = true.takeIf { c[Comments.uri] in hidden },
            audience = c[Comments.audience].takeIf { it != Visibility.PUBLIC },
            root = c[Comments.threadCid]?.let { StrongRef(AtUri(c[Comments.threadUri]), Cid(it)) },
            parent = c[Comments.parentUri]?.let { uri -> c[Comments.parentCid]?.let { StrongRef(AtUri(uri), Cid(it)) } },
        )
    }
}

/** atproto's handle for an account whose handle can't be resolved. */
private const val UNKNOWN_HANDLE = "handle.invalid"

/** A comment row's place in a keyset on (created_at, uri). */
internal fun commentCursor(row: ResultRow): String = "${row[Comments.createdAt]}::${row[Comments.uri]}"

/** Replies listed with each thread in a page of threads; the rest come a page at a time with `root`. */
private const val FIRST_REPLIES = 3

/**
 * Where a comment places its thread: a top-level comment by its own `createdAt`, and a reply by the later of its `createdAt` and when it
 * was first indexed. Clients set `createdAt`, so a reply backdated (or from a skewed clock) can't move its thread earlier, and a reply
 * indexed after a page was read never lowers its thread's place below that page's cursor.
 */
private val threadPlace =
    Case()
        .When(Comments.uri eq Comments.threadUri, Comments.createdAt)
        .Else(CustomFunction("GREATEST", LongColumnType(), Comments.createdAt, Comments.indexedAt))

/**
 * A page of threads: the listed rows grouped by thread, ordered by each thread's earliest [threadPlace], a keyset with `HAVING` on
 * (MIN(place), thread_uri), cursor `"<minPlace>::<threadUri>"`. That's the root's, unless it isn't listed: then the thread, a placeholder,
 * is placed by its earliest listed reply, revealing nothing of the root's time. Each thread comes with its [FIRST_REPLIES] oldest listed
 * replies, read with one limited query per thread on `comments_thread_idx`, and its count of listed replies, so a page's cost follows its
 * size rather than its largest thread.
 */
internal fun JdbcTransaction.threadPage(
    params: GetComments.Params,
    avatars: AvatarUrls,
    listed: Op<Boolean>,
    athlete: String,
): GetComments.Output {
    val pageSize = Paging.clamp(params.limit)
    val first = threadPlace.min()
    val after =
        params.cursor?.let(::parseCursor)?.let { (sortValue, thread) ->
            (first greaterEq sortValue) and ((first greater sortValue) or ((first eq sortValue) and (Comments.threadUri greater thread)))
        } ?: Op.TRUE
    val threads =
        admittedComments()
            .select(Comments.threadUri, first)
            .where { listed }
            .groupBy(Comments.threadUri)
            .having { after }
            .orderBy(first to SortOrder.ASC, Comments.threadUri to SortOrder.ASC)
            .limit(pageSize)
            .toList()
    val next = threads.takeIf { it.size == pageSize }?.last()?.let { "${it[first]}::${it[Comments.threadUri]}" }
    val threadUris = threads.map { it[Comments.threadUri] }
    if (threadUris.isEmpty()) return GetComments.Output(params.uri, emptyList(), next, emptyList())
    val isRoot = Comments.uri eq Comments.threadUri
    val rootRows = listedRows(listed and (Comments.uri inList threadUris) and isRoot)
    val replyCount = Comments.uri.count()
    val replyCounts =
        admittedComments()
            .select(Comments.threadUri, replyCount)
            .where { listed and (Comments.threadUri inList threadUris) and not(isRoot) }
            .groupBy(Comments.threadUri)
            .associate { it[Comments.threadUri] to it[replyCount].toInt() }
    val firstReplyRows =
        replyCounts.keys.associateWith { thread ->
            listedRows(listed and (Comments.threadUri eq thread) and not(isRoot), limit = FIRST_REPLIES)
        }
    val views = commentViews(rootRows + firstReplyRows.values.flatten(), athlete, avatars).associateBy { it.uri.value }
    return GetComments.Output(
        uri = params.uri,
        comments = rootRows.mapNotNull { views[it[Comments.uri]] },
        cursor = next,
        threads =
            threadUris.map { thread ->
                val replies = firstReplyRows[thread].orEmpty()
                val count = replyCounts[thread] ?: 0
                ThreadView(
                    uri = AtUri(thread),
                    root = views[thread],
                    replies = replies.mapNotNull { views[it[Comments.uri]] },
                    replyCount = count,
                    repliesCursor = replies.lastOrNull()?.takeIf { count > FIRST_REPLIES }?.let(::commentCursor),
                )
            },
    )
}

/** The comment rows matching [where], oldest first, the first [limit] of them if given. */
internal fun listedRows(where: Op<Boolean>, limit: Int? = null): List<ResultRow> =
    admittedComments()
        .select(Comments.columns)
        .where { where }
        .orderBy(Comments.createdAt to SortOrder.ASC, Comments.uri to SortOrder.ASC)
        .let { if (limit != null) it.limit(limit) else it }
        .toList()
