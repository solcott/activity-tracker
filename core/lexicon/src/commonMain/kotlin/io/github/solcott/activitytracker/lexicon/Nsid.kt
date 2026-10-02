package io.github.solcott.activitytracker.lexicon

/**
 * Every NSID this app defines. [NAMESPACE] is a placeholder: rename it here (and the `lexicons/` directory + `id` fields) before any real
 * records are written to a public PDS.
 */
object Nsid {
    const val NAMESPACE = "app.activitytracker"

    const val ACTOR_PROFILE = "$NAMESPACE.actor.profile"
    const val ACTOR_GET_PROFILE = "$NAMESPACE.actor.getProfile"

    const val FEED_ACTIVITY = "$NAMESPACE.feed.activity"
    const val FEED_KUDOS = "$NAMESPACE.feed.kudos"
    const val FEED_COMMENT = "$NAMESPACE.feed.comment"
    const val FEED_GET_TIMELINE = "$NAMESPACE.feed.getTimeline"
    const val FEED_GET_AUTHOR_ACTIVITIES = "$NAMESPACE.feed.getAuthorActivities"
    const val FEED_GET_ACTIVITY = "$NAMESPACE.feed.getActivity"

    const val GRAPH_FOLLOW = "$NAMESPACE.graph.follow"

    /** Record collections the AppView indexes (used as the Jetstream `wantedCollections` filter). */
    val indexedCollections = listOf(ACTOR_PROFILE, FEED_ACTIVITY, FEED_KUDOS, FEED_COMMENT, GRAPH_FOLLOW)
}
