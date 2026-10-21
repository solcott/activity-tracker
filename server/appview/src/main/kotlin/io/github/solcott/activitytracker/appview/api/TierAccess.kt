package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.Follows
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.Visibility
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.stringParam
import org.jetbrains.exposed.v1.jdbc.select

/**
 * Rows a [viewer] may see, by the tier in [visibility] and the athlete in [author], as the managing app admits readers to each tier space
 * ([SpaceAccess]): public to anyone, followers to effective followers, mutuals to effective follows both ways, and everything to the
 * athlete, except that two athletes in a block see nothing of each other. An unknown tier admits only the athlete. A null viewer sees
 * public rows only. Rows of an account that isn't served, or of a [record] taken down at [cid], are the athlete's alone ([served]); a query
 * over another table than `activities` names its own activity column as [record], with no [cid].
 */
internal fun visibleTo(
    viewer: Did?,
    visibility: Column<String> = Activities.visibility,
    author: Column<String> = Activities.authorDid,
    record: Column<String>? = Activities.uri,
    cid: Column<String>? = Activities.cid,
): Op<Boolean> =
    if (viewer == null) (visibility eq Visibility.PUBLIC) and served(null as Expression<String>?, author, record, cid)
    else admits(stringParam(viewer.value), visibility, author) and served(stringParam(viewer.value), author, record, cid)

/**
 * Rows the athlete in [viewer], a column of the outer query, may see: [visibleTo] for a viewer that varies per row, such as a like's giver.
 * Public rows admit everyone, so their viewers are never looked up.
 */
internal fun visibleTo(
    viewer: Column<String>,
    visibility: Column<String> = Activities.visibility,
    author: Column<String> = Activities.authorDid,
    record: Column<String>? = Activities.uri,
    cid: Column<String>? = Activities.cid,
): Op<Boolean> = admits(viewer, visibility, author) and served(viewer, author, record, cid)

/** A viewer in a block with the athlete ([blocked]) is admitted to nothing of theirs, Public included. */
private fun admits(viewer: Expression<String>, visibility: Column<String>, author: Column<String>): Op<Boolean> {
    val follower = followExists((Follows.authorDid eq viewer) and (Follows.subjectDid eq author))
    val followedBack = followExists((Follows.authorDid eq author) and (Follows.subjectDid eq viewer))
    return ((visibility eq Visibility.PUBLIC) or
        (author eq viewer) or
        ((visibility eq Visibility.FOLLOWERS) and follower) or
        ((visibility eq Visibility.MUTUALS) and follower and followedBack)) and not(blocked(viewer, author))
}

/** An effective follow matching [pair], correlated to the outer query's author column. */
private fun followExists(pair: Op<Boolean>): Op<Boolean> = exists(Follows.select(Follows.uri).where { pair and effectiveFollow() })
