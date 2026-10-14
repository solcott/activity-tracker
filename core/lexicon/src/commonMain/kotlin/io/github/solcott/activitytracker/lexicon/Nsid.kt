package io.github.solcott.activitytracker.lexicon

/**
 * Every NSID this app defines. [NAMESPACE] is a placeholder: rename it here (and the `lexicons/` directory + `id` fields) before any real
 * records are written to a public PDS.
 */
object Nsid {
    const val NAMESPACE = "app.activitytracker"

    const val ACTOR_PROFILE = "$NAMESPACE.actor.profile"
    /** A record kept only in an athlete's tier space ([SPACE_ATHLETE]), so it never reaches the firehose and isn't indexed. */
    const val ACTOR_PRIVATE_DETAILS = "$NAMESPACE.actor.privateDetails"
    const val ACTOR_GET_PROFILE = "$NAMESPACE.actor.getProfile"
    const val ACTOR_SEARCH_ACTORS = "$NAMESPACE.actor.searchActors"

    const val FEED_ACTIVITY = "$NAMESPACE.feed.activity"
    const val FEED_KUDOS = "$NAMESPACE.feed.kudos"
    const val FEED_COMMENT = "$NAMESPACE.feed.comment"
    const val FEED_GET_TIMELINE = "$NAMESPACE.feed.getTimeline"
    const val FEED_GET_AUTHOR_ACTIVITIES = "$NAMESPACE.feed.getAuthorActivities"
    const val FEED_GET_AUTHOR_ACTIVITIES_BY_START = "$NAMESPACE.feed.getAuthorActivitiesByStart"
    const val FEED_GET_ACTIVITY = "$NAMESPACE.feed.getActivity"
    const val FEED_GET_KUDOS = "$NAMESPACE.feed.getKudos"
    const val FEED_ACTIVITY_STREAMS = "$NAMESPACE.feed.activityStreams"
    /** Not a record or a query: the views' definitions, named in a union member's `$type`. */
    const val FEED_DEFS = "$NAMESPACE.feed.defs"
    const val FEED_GET_ACTIVITY_STREAMS = "$NAMESPACE.feed.getActivityStreams"

    const val GRAPH_FOLLOW = "$NAMESPACE.graph.follow"
    const val GRAPH_GET_FOLLOWERS = "$NAMESPACE.graph.getFollowers"
    const val GRAPH_GET_FOLLOWS = "$NAMESPACE.graph.getFollows"
    /** An athlete's public approval of one follower, keyed by the follower's DID. */
    const val GRAPH_FOLLOW_APPROVAL = "$NAMESPACE.graph.followApproval"
    /** A record kept only in an athlete's `self` tier space ([SPACE_ATHLETE]), so it never reaches the firehose and isn't indexed. */
    const val GRAPH_FOLLOW_DECLINE = "$NAMESPACE.graph.followDecline"
    const val GRAPH_GET_FOLLOW_REQUESTS = "$NAMESPACE.graph.getFollowRequests"

    /**
     * The space type holding an athlete's permissioned data (`lexicons/.../space/athlete.json`). Not a record collection, so it's never
     * indexed; the local PDS resolves its declaration from the lexicon authority account (`docker/scripts/publish-lexicons.sh`).
     */
    const val SPACE_ATHLETE = "$NAMESPACE.space.athlete"

    /** Record collections the AppView indexes (used as the Jetstream `wantedCollections` filter). */
    val indexedCollections =
        listOf(ACTOR_PROFILE, FEED_ACTIVITY, FEED_ACTIVITY_STREAMS, FEED_KUDOS, FEED_COMMENT, GRAPH_FOLLOW, GRAPH_FOLLOW_APPROVAL)
}
