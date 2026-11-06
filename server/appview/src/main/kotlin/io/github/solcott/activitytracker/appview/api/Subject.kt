package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.Likes
import io.github.solcott.activitytracker.appview.db.Posts
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and

// What likes and comments are on, and the like side of the rules every interaction query shares (comments: `CommentQueries.kt`). One set of
// rules serves activities and posts (feed/posts): each query names its [Subject] and is judged by that subject's columns.

/**
 * What a like or comment names: an activity, or a post (feed/posts). Its columns are what the giver and audience checks judge against: the
 * subject's tier, its author (whose relationships admit viewers to a comment's audience, and whose hide counts), and its URI and CID
 * (labels on the subject).
 */
internal enum class Subject(
    val table: Table,
    val uri: Column<String>,
    val cid: Column<String>,
    val author: Column<String>,
    val visibility: Column<String>,
) {
    ACTIVITY(Activities, Activities.uri, Activities.cid, Activities.authorDid, Activities.visibility),
    POST(Posts, Posts.uri, Posts.cid, Posts.authorDid, Posts.visibility);

    /** Rows of the subject [author] — a column of the outer query, such as a like's giver — may see at the subject's current tier. */
    fun visibleTo(author: Column<String>): Op<Boolean> = visibleTo(author, visibility, this.author, uri, cid)

    companion object {
        /** The kind of subject [uri] names, by its collection; null for anything else, which has no likes or comments to serve. */
        fun of(uri: AtUri): Subject? =
            when (uri.collection) {
                Nsid.FEED_ACTIVITY -> ACTIVITY
                Nsid.FEED_POST -> POST
                else -> null
            }
    }
}

/**
 * Likes joined to the [subject] they name, for [likesShownTo]: a like is served only while its giver may see the subject at its current
 * tier, wherever the like is stored, so likes follow the subject when it's narrowed or widened.
 */
internal fun admittedLikes(subject: Subject) = Likes.join(subject.table, JoinType.INNER, Likes.subjectUri, subject.uri)

/**
 * The likes over [admittedLikes] that [viewer] is shown: the giver may see the [subject], and the giver is neither in a block with the
 * viewer nor hidden from them. Every like count and list uses it, so a count never includes a like the list leaves out.
 */
internal fun likesShownTo(viewer: Did?, subject: Subject): Op<Boolean> =
    subject.visibleTo(Likes.authorDid) and accountShownTo(viewer, Likes.authorDid)
