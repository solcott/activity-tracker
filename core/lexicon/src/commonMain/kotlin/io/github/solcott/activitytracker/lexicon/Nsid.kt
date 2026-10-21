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
    /**
     * A record kept only in an athlete's `self` tier space ([SPACE_ATHLETE]): the visibilities new activities start with. Never indexed.
     */
    const val ACTOR_ACTIVITY_DEFAULTS = "$NAMESPACE.actor.activityDefaults"
    /** A record kept only in an athlete's `self` tier space ([SPACE_ATHLETE]): the places hidden from their routes. Never indexed. */
    const val ACTOR_PRIVACY_ZONES = "$NAMESPACE.actor.privacyZones"
    /**
     * A record kept only in an athlete's `self` tier space ([SPACE_ATHLETE]): which [ACTOR_PRIVACY_ZONES] save their polylines were trimmed
     * by. Never indexed.
     */
    const val ACTOR_ROUTE_TRIM = "$NAMESPACE.actor.routeTrim"
    const val ACTOR_GET_PROFILE = "$NAMESPACE.actor.getProfile"
    const val ACTOR_SEARCH_ACTORS = "$NAMESPACE.actor.searchActors"

    const val FEED_ACTIVITY = "$NAMESPACE.feed.activity"
    const val FEED_LIKE = "$NAMESPACE.feed.like"
    const val FEED_COMMENT = "$NAMESPACE.feed.comment"
    /** The athlete hid a comment on their own activity; keyed by the comment (`commentHideRkey` in `core/atproto`). */
    const val FEED_COMMENT_HIDE = "$NAMESPACE.feed.commentHide"
    const val FEED_GET_TIMELINE = "$NAMESPACE.feed.getTimeline"
    const val FEED_GET_AUTHOR_ACTIVITIES = "$NAMESPACE.feed.getAuthorActivities"
    const val FEED_GET_AUTHOR_ACTIVITIES_BY_START = "$NAMESPACE.feed.getAuthorActivitiesByStart"
    const val FEED_GET_ACTIVITY = "$NAMESPACE.feed.getActivity"
    const val FEED_GET_LIKES = "$NAMESPACE.feed.getLikes"
    const val FEED_GET_COMMENTS = "$NAMESPACE.feed.getComments"
    const val FEED_ACTIVITY_STREAMS = "$NAMESPACE.feed.activityStreams"
    /**
     * An activity's summary fields in a detail category narrower than the activity, kept only in the athlete's tier space for that
     * category's visibility ([SPACE_ATHLETE]).
     */
    const val FEED_ACTIVITY_DETAILS = "$NAMESPACE.feed.activityDetails"
    /** An activity's zone snapshots, apart from its streams because they can be narrower (the training's visibility). */
    const val FEED_ACTIVITY_ZONES = "$NAMESPACE.feed.activityZones"
    /** An activity's full-resolution track, kept only in the athlete's `self` tier space ([SPACE_ATHLETE]) and never indexed. */
    const val FEED_ACTIVITY_TRACK = "$NAMESPACE.feed.activityTrack"
    /** The file an activity was imported from, kept only in the athlete's `self` tier space ([SPACE_ATHLETE]) and never indexed. */
    const val FEED_ACTIVITY_SOURCE = "$NAMESPACE.feed.activitySource"
    /**
     * An imported activity's devices and their serial numbers, kept only in the athlete's `self` tier space ([SPACE_ATHLETE]) and never
     * indexed.
     */
    const val FEED_ACTIVITY_DEVICES = "$NAMESPACE.feed.activityDevices"
    /** Not a record or a query: the views' definitions, named in a union member's `$type`. */
    const val FEED_DEFS = "$NAMESPACE.feed.defs"
    const val FEED_GET_ACTIVITY_STREAMS = "$NAMESPACE.feed.getActivityStreams"
    const val MODERATION_GET_SUBJECT = "$NAMESPACE.moderation.getSubject"
    const val MODERATION_GET_ROLE = "$NAMESPACE.moderation.getRole"

    const val GRAPH_FOLLOW = "$NAMESPACE.graph.follow"
    const val GRAPH_GET_FOLLOWERS = "$NAMESPACE.graph.getFollowers"
    const val GRAPH_GET_FOLLOWS = "$NAMESPACE.graph.getFollows"
    /** An athlete's public approval of one follower, keyed by the follower's DID. */
    const val GRAPH_FOLLOW_APPROVAL = "$NAMESPACE.graph.followApproval"
    /** A record kept only in an athlete's `self` tier space ([SPACE_ATHLETE]), so it never reaches the firehose and isn't indexed. */
    const val GRAPH_FOLLOW_DECLINE = "$NAMESPACE.graph.followDecline"
    const val GRAPH_GET_FOLLOW_REQUESTS = "$NAMESPACE.graph.getFollowRequests"
    /** An athlete's block of one account, kept only in their `interactions` space ([SPACE_ATHLETE]) and keyed by the subject's DID. */
    const val GRAPH_BLOCK = "$NAMESPACE.graph.block"
    /** An athlete's mute of one account, kept only in their `interactions` space ([SPACE_ATHLETE]) and keyed by the subject's DID. */
    const val GRAPH_MUTE = "$NAMESPACE.graph.mute"
    const val GRAPH_GET_BLOCKS = "$NAMESPACE.graph.getBlocks"
    const val GRAPH_GET_MUTES = "$NAMESPACE.graph.getMutes"

    /**
     * Bluesky's public block record. The AppView honors an athlete's Bluesky blocks as blocks here too; clients never write `app.bsky.*`
     * records.
     */
    const val BSKY_GRAPH_BLOCK = "app.bsky.graph.block"

    /**
     * The space type holding an athlete's permissioned data (`lexicons/.../space/athlete.json`). Not a record collection, so it's never
     * indexed; the local PDS resolves its declaration from the lexicon authority account (`docker/scripts/publish-lexicons.sh`).
     */
    const val SPACE_ATHLETE = "$NAMESPACE.space.athlete"
    /** Procedure: an athlete tells the AppView one of their [SPACE_ATHLETE] spaces exists, so it syncs it. */
    const val SPACE_NOTIFY_SPACE = "$NAMESPACE.space.notifySpace"

    /** Record collections the AppView indexes (used as the Jetstream `wantedCollections` filter). */
    val indexedCollections =
        listOf(
            ACTOR_PROFILE,
            FEED_ACTIVITY,
            FEED_ACTIVITY_DETAILS,
            FEED_ACTIVITY_STREAMS,
            FEED_ACTIVITY_ZONES,
            FEED_LIKE,
            FEED_COMMENT,
            FEED_COMMENT_HIDE,
            GRAPH_FOLLOW,
            GRAPH_FOLLOW_APPROVAL,
            BSKY_GRAPH_BLOCK,
        )
}
