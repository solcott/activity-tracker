package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.Follows
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.Visibility
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select

/**
 * Rows a [viewer] may see, by the tier in [visibility] and the athlete in [author], as the managing app admits readers to each tier space
 * ([SpaceAccess]): public to anyone, followers to effective followers, mutuals to effective follows both ways, and everything to the
 * athlete. An unknown tier admits only the athlete. A null viewer sees public rows only.
 */
internal fun visibleTo(
    viewer: Did?,
    visibility: Column<String> = Activities.visibility,
    author: Column<String> = Activities.authorDid,
): Op<Boolean> {
    val public = visibility eq Visibility.PUBLIC
    if (viewer == null) return public
    val v = viewer.value
    val follower = followExists((Follows.authorDid eq v) and (Follows.subjectDid eq author))
    val followedBack = followExists((Follows.authorDid eq author) and (Follows.subjectDid eq v))
    return public or
        (author eq v) or
        ((visibility eq Visibility.FOLLOWERS) and follower) or
        ((visibility eq Visibility.MUTUALS) and follower and followedBack)
}

/** An effective follow matching [pair], correlated to the outer query's author column. */
private fun followExists(pair: Op<Boolean>): Op<Boolean> = exists(Follows.select(Follows.uri).where { pair and effectiveFollow() })

/** Whether a row at [visibility] takes likes and comments: only public activities do. */
internal fun acceptsInteractions(visibility: String): Boolean = visibility == Visibility.PUBLIC
