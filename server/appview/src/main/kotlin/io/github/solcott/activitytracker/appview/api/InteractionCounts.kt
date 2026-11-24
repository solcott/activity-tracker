package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Comments
import io.github.solcott.activitytracker.appview.db.Likes
import io.github.solcott.activitytracker.lexicon.Did
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select

// The like and comment counts activity and post views carry, by the rules getLikes and getComments list by. Each takes the subjects to
// read as [countedAs]' keys and counts each toward its value: a combined activity takes its members' likes and comments (each post counts
// as itself).

/**
 * Distinct givers of the likes on each of [countedAs]' subjects that [viewer] is shown ([likesShownTo]), counted toward the subject each is
 * shown as. Repos may hold duplicate like records, and a giver may have liked a combined activity and its member too, so givers are
 * counted, not records.
 */
internal fun JdbcTransaction.likeCounts(subject: Subject, countedAs: Map<String, String>, viewer: Did?): Map<String, Long> =
    admittedLikes(subject)
        .select(Likes.subjectUri, Likes.authorDid)
        .where { (Likes.subjectUri inList countedAs.keys) and likesShownTo(viewer, subject) }
        .withDistinct()
        .toList()
        .groupBy({ countedAs.getValue(it[Likes.subjectUri]) }) { it[Likes.authorDid] }
        .mapValues { (_, givers) -> givers.toSet().size.toLong() }

/**
 * The comments and replies on each of [countedAs]' subjects that [viewer] is shown ([commentsVisibleTo]) and that the athlete hasn't hidden
 * ([hiddenByAthlete]), counted toward the subject each is shown as: what `getComments` lists, less those marked hidden, so the count
 * matches the pages.
 */
internal fun JdbcTransaction.commentCounts(subject: Subject, countedAs: Map<String, String>, viewer: Did?): Map<String, Long> {
    val count = Comments.uri.count()
    return admittedComments(subject)
        .select(Comments.subjectUri, count)
        .where { (Comments.subjectUri inList countedAs.keys) and commentsVisibleTo(viewer, subject) and not(hiddenByAthlete(subject)) }
        .groupBy(Comments.subjectUri)
        .toList()
        .groupBy({ countedAs.getValue(it[Comments.subjectUri]) }) { it[count] }
        .mapValues { (_, counts) -> counts.sum() }
}

/**
 * Distinct givers of the likes on each of the comments [uris] on [subject] that [viewer] is shown: a giver counts while they may see the
 * comment as [commentsVisibleTo] would let them (the subject at its current visibility, and the comment's audience and its thread root's
 * audience, judged against the subject's author), and isn't in a block with its commenter or hidden from the viewer ([accountShownTo]).
 * Repos may hold duplicate like records, so givers are counted, not records.
 */
internal fun JdbcTransaction.commentLikeCounts(subject: Subject, uris: List<String>, viewer: Did?): Map<String, Long> {
    val giver = Likes.authorDid
    val tier = { visibility: Column<String> ->
        visibleTo(giver, visibility = visibility, author = subject.author, record = subject.uri, cid = subject.cid)
    }
    return admittedComments(subject)
        .join(Likes, JoinType.INNER, Comments.uri, Likes.subjectUri)
        .select(Comments.uri, giver)
        .where {
            (Comments.uri inList uris) and
                subject.visibleTo(giver) and
                tier(Comments.audience) and
                (Roots[Comments.uri].isNull() or tier(Roots[Comments.audience])) and
                not(blocked(giver, Comments.authorDid)) and
                accountShownTo(viewer, giver)
        }
        .withDistinct()
        .toList()
        .groupBy({ it[Comments.uri] }) { it[giver] }
        .mapValues { (_, givers) -> givers.toSet().size.toLong() }
}
