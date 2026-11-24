package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Comments
import io.github.solcott.activitytracker.lexicon.Did
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or

// The audience rule a comment is read by, shared by who is shown it ([commentsVisibleTo]) and whose likes on it count
// ([commentLikeCounts]).

/**
 * The comment's audience admits [reader], judged against the [subject]'s author, and so does its thread root's ([rootAdmits]). A reply is
 * also capped by its thread's top-level comment's audience while that's indexed on the subject: clients cap a reply by its parent, so an
 * honest chain only narrows, and a reply from a client that doesn't still never reaches past the root's audience. Only the root, not every
 * ancestor, is checked. A top-level comment joins itself, which adds nothing.
 */
internal fun commentAudienceAdmits(reader: Did?, subject: Subject): Op<Boolean> = rootAdmits {
    visibleTo(reader, visibility = it, author = subject.author, record = subject.uri, cid = subject.cid)
}

/** [commentAudienceAdmits] for a [reader] that varies per row, a column of the outer query such as a comment like's giver. */
internal fun commentAudienceAdmits(reader: Column<String>, subject: Subject): Op<Boolean> = rootAdmits {
    visibleTo(reader, visibility = it, author = subject.author, record = subject.uri, cid = subject.cid)
}

/** The comment's audience and, for a reply whose root is indexed on the same subject, the root's audience each pass [tier]. */
private fun rootAdmits(tier: (Column<String>) -> Op<Boolean>): Op<Boolean> =
    tier(Comments.audience) and (Roots[Comments.uri].isNull() or tier(Roots[Comments.audience]))
