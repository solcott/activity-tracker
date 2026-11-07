package io.github.solcott.activitytracker.lexicon

/**
 * The [Nsid.SPACE_ATHLETE] space type's declaration, mirrored from `lexicons/app/activitytracker/space/athlete.json`. The PDS fills a
 * session's `space:` grant with these collections when it signs the athlete in, so a session signed in before one was added can't write it
 * (auth/account-page). `AthleteSpaceDeclarationTest` checks this list against the JSON.
 */
object AthleteSpaceDeclaration {
    /** Every record collection the space type declares, in the declaration's order. */
    val collections: List<String> =
        listOf(
            Nsid.ACTOR_PRIVATE_DETAILS,
            Nsid.GRAPH_FOLLOW_DECLINE,
            Nsid.FEED_ACTIVITY_STREAMS,
            Nsid.FEED_ACTIVITY_DETAILS,
            Nsid.FEED_ACTIVITY_ZONES,
            Nsid.FEED_ACTIVITY_TRACK,
            Nsid.FEED_ACTIVITY_SOURCE,
            Nsid.FEED_ACTIVITY_DEVICES,
            Nsid.FEED_ACTIVITY,
            Nsid.ACTOR_ACTIVITY_DEFAULTS,
            Nsid.ACTOR_PRIVACY_ZONES,
            Nsid.ACTOR_ROUTE_TRIM,
            Nsid.ACTOR_SEGMENT_PREFS,
            Nsid.ACTOR_LEADERBOARD_BRACKETS,
            Nsid.ACTOR_GROUP_PREFS,
            Nsid.FEED_SEGMENT_TRACK,
            Nsid.FEED_SEGMENT_STAR,
            Nsid.FEED_ACTIVITY_INVITE,
            Nsid.FEED_ACTIVITY_INVITE_DECLINE,
            Nsid.GEAR_ITEM,
            Nsid.GEAR_AUTO_ASSIGN,
            Nsid.GEAR_COLLECTION,
            Nsid.FEED_LIKE,
            Nsid.FEED_COMMENT,
            Nsid.FEED_COMMENT_HIDE,
            Nsid.GRAPH_BLOCK,
            Nsid.GRAPH_MUTE,
            Nsid.FEED_SEGMENT_NAME_VOTE,
            Nsid.FEED_POST,
            Nsid.FEED_REPOST,
        )
}
