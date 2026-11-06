package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Comments
import io.github.solcott.activitytracker.appview.db.Likes
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.PostViewerState
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select

// The like and comment counts post views carry (feed/posts), by the rules getLikes and getComments list by.

/** A page of posts' like and comment counts as one viewer is shown them, and that viewer's likes (null signed out). */
internal class PostCounts(
    private val likes: Map<String, Long>,
    private val comments: Map<String, Long>,
    private val viewerLikes: Map<String, String>?,
) {
    fun likes(uri: String): Long = likes[uri] ?: 0

    fun comments(uri: String): Long = comments[uri] ?: 0

    fun viewer(uri: String): PostViewerState? = viewerLikes?.let { PostViewerState(like = it[uri]?.let(::AtUri)) }
}

internal fun JdbcTransaction.postCounts(uris: List<String>, viewer: Did?): PostCounts =
    PostCounts(postLikeCounts(uris, viewer), postCommentCounts(uris, viewer), viewer?.let { viewerLikes(it, uris) })

/**
 * Distinct givers of the likes on each of the posts [uris] that [viewer] is shown ([likesShownTo]), the rule `getLikes` lists by. Repos may
 * hold duplicate like records, so givers are counted, not records.
 */
private fun JdbcTransaction.postLikeCounts(uris: List<String>, viewer: Did?): Map<String, Long> =
    admittedLikes(Subject.POST)
        .select(Likes.subjectUri, Likes.authorDid)
        .where { (Likes.subjectUri inList uris) and likesShownTo(viewer, Subject.POST) }
        .withDistinct()
        .toList()
        .groupBy({ it[Likes.subjectUri] }) { it[Likes.authorDid] }
        .mapValues { (_, givers) -> givers.toSet().size.toLong() }

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
