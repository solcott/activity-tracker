package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Comments
import io.github.solcott.activitytracker.appview.db.Likes
import io.github.solcott.activitytracker.appview.db.Posts
import io.github.solcott.activitytracker.appview.db.Reposts
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.PostViewerState
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select

// The like, comment, repost and quote counts post views carry (feed/posts, feed/reposts, feed/quote-posts), by the rules getLikes,
// getComments, getReposts and getQuotes list by.

/**
 * A page of posts' like, comment, repost and quote counts as one viewer is shown them, and that viewer's likes and reposts (null signed
 * out).
 */
@Suppress("LongParameterList") // One map per count, and the viewer's own.
internal class PostCounts(
    private val likes: Map<String, Long>,
    private val comments: Map<String, Long>,
    private val reposts: Map<String, Long>,
    private val quotes: Map<String, Long>,
    private val viewerLikes: Map<String, String>?,
    private val viewerReposts: Map<String, String>?,
) {
    fun likes(uri: String): Long = likes[uri] ?: 0

    fun comments(uri: String): Long = comments[uri] ?: 0

    fun reposts(uri: String): Long = reposts[uri] ?: 0

    fun quotes(uri: String): Long = quotes[uri] ?: 0

    fun viewer(uri: String): PostViewerState? = viewerLikes?.let {
        PostViewerState(like = it[uri]?.let(::AtUri), repost = viewerReposts?.get(uri)?.let(::AtUri))
    }
}

internal fun JdbcTransaction.postCounts(uris: List<String>, viewer: Did?): PostCounts =
    PostCounts(
        postLikeCounts(uris, viewer),
        postCommentCounts(uris, viewer),
        postRepostCounts(uris, viewer),
        postQuoteCounts(uris, viewer),
        viewer?.let { viewerLikes(it, uris) },
        viewer?.let { viewerReposts(it, uris) },
    )

/** Quotes of each of the posts [uris] that [viewer] is shown ([postRows]), the rule `getQuotes` lists by (feed/quote-posts). */
private fun JdbcTransaction.postQuoteCounts(uris: List<String>, viewer: Did?): Map<String, Long> {
    val count = Posts.uri.count()
    return Posts.select(Posts.quotedUri, count)
        .where { (Posts.quotedUri inList uris) and postRows(viewer) }
        .groupBy(Posts.quotedUri)
        .mapNotNull { row -> row[Posts.quotedUri]?.let { it to row[count] } }
        .toMap()
}

/**
 * Distinct reposters of each of the posts [uris] that [viewer] is shown ([repostsShownTo]), the rule `getReposts` lists by. Reposters are
 * counted, not records, as for likes.
 */
private fun JdbcTransaction.postRepostCounts(uris: List<String>, viewer: Did?): Map<String, Long> =
    admittedReposts()
        .select(Reposts.subjectUri, Reposts.authorDid)
        .where { (Reposts.subjectUri inList uris) and repostsShownTo(viewer) }
        .withDistinct()
        .toList()
        .groupBy({ it[Reposts.subjectUri] }) { it[Reposts.authorDid] }
        .mapValues { (_, reposters) -> reposters.size.toLong() }

/**
 * Distinct givers of the likes on each of the posts [uris] that [viewer] is shown ([likesShownTo]), the rule `getLikes` lists by. Repos may
 * hold duplicate like records, so givers are counted (the select is distinct per post and giver), not records.
 */
private fun JdbcTransaction.postLikeCounts(uris: List<String>, viewer: Did?): Map<String, Long> =
    admittedLikes(Subject.POST)
        .select(Likes.subjectUri, Likes.authorDid)
        .where { (Likes.subjectUri inList uris) and likesShownTo(viewer, Subject.POST) }
        .withDistinct()
        .toList()
        .groupBy({ it[Likes.subjectUri] }) { it[Likes.authorDid] }
        .mapValues { (_, givers) -> givers.size.toLong() }

/**
 * The comments and replies on each of the posts [uris] that [viewer] is shown and that the post's author hasn't hidden: what `getComments`
 * lists for them, less those marked hidden, so the count matches the pages.
 */
private fun JdbcTransaction.postCommentCounts(uris: List<String>, viewer: Did?): Map<String, Long> {
    val count = Comments.uri.count()
    return admittedComments(Subject.POST)
        .select(Comments.subjectUri, count)
        .where { (Comments.subjectUri inList uris) and commentsVisibleTo(viewer, Subject.POST) and not(hiddenByAthlete(Subject.POST)) }
        .groupBy(Comments.subjectUri)
        .associate { it[Comments.subjectUri] to it[count] }
}
