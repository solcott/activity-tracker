package io.github.solcott.activitytracker.lexicon

/**
 * Every `app.activitytracker` record collection (each `"type": "record"` lexicon under `lexicons/app/activitytracker/`), whether it lives
 * in repos, spaces or both. A data export walks these in the athlete's repo (auth/data-export); the space side walks
 * [AthleteSpaceDeclaration.collections]. `AppCollectionsTest` checks this list against the lexicon files, so a new record type can't be
 * left out of an export.
 */
object AppCollections {
    /** Sorted by NSID. */
    val records: List<String> =
        listOf(
                Nsid.ACTOR_ACTIVITY_DEFAULTS,
                Nsid.ACTOR_GROUP_PREFS,
                Nsid.ACTOR_LEADERBOARD_BRACKETS,
                Nsid.ACTOR_PRIVACY_ZONES,
                Nsid.ACTOR_PRIVATE_DETAILS,
                Nsid.ACTOR_PROFILE,
                Nsid.ACTOR_ROUTE_TRIM,
                Nsid.ACTOR_SEGMENT_PREFS,
                Nsid.FEED_ACTIVITY,
                Nsid.FEED_ACTIVITY_DETAILS,
                Nsid.FEED_ACTIVITY_DEVICES,
                Nsid.FEED_ACTIVITY_INVITE,
                Nsid.FEED_ACTIVITY_INVITE_DECLINE,
                Nsid.FEED_ACTIVITY_SOURCE,
                Nsid.FEED_ACTIVITY_STREAMS,
                Nsid.FEED_ACTIVITY_TRACK,
                Nsid.FEED_ACTIVITY_ZONES,
                Nsid.FEED_COMMENT,
                Nsid.FEED_COMMENT_HIDE,
                Nsid.FEED_DIVE_SITE,
                Nsid.FEED_LIKE,
                Nsid.FEED_POST,
                Nsid.FEED_REPOST,
                Nsid.FEED_SEGMENT,
                Nsid.FEED_SEGMENT_NAME_PROPOSAL,
                Nsid.FEED_SEGMENT_NAME_VOTE,
                Nsid.FEED_SEGMENT_STAR,
                Nsid.FEED_SEGMENT_TRACK,
                Nsid.GEAR_AUTO_ASSIGN,
                Nsid.GEAR_COLLECTION,
                Nsid.GEAR_ITEM,
                Nsid.GRAPH_BLOCK,
                Nsid.GRAPH_FOLLOW,
                Nsid.GRAPH_FOLLOW_APPROVAL,
                Nsid.GRAPH_FOLLOW_DECLINE,
                Nsid.GRAPH_MUTE,
            )
            .sorted()
}
