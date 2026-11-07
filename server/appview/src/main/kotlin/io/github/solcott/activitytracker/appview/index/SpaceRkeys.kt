package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityDetails
import io.github.solcott.activitytracker.appview.db.ActivityInvites
import io.github.solcott.activitytracker.appview.db.ActivityStreams
import io.github.solcott.activitytracker.appview.db.ActivityZones
import io.github.solcott.activitytracker.appview.db.Blocks
import io.github.solcott.activitytracker.appview.db.CommentHides
import io.github.solcott.activitytracker.appview.db.Comments
import io.github.solcott.activitytracker.appview.db.GearItems
import io.github.solcott.activitytracker.appview.db.GroupPrefs
import io.github.solcott.activitytracker.appview.db.LeaderboardBrackets
import io.github.solcott.activitytracker.appview.db.Likes
import io.github.solcott.activitytracker.appview.db.Mutes
import io.github.solcott.activitytracker.appview.db.Posts
import io.github.solcott.activitytracker.appview.db.RecordShadows
import io.github.solcott.activitytracker.appview.db.Reposts
import io.github.solcott.activitytracker.appview.db.SegmentNameVotes
import io.github.solcott.activitytracker.appview.db.SegmentPrefs
import io.github.solcott.activitytracker.appview.db.SegmentTracks
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GroupPrefsRecord
import io.github.solcott.activitytracker.lexicon.app.LeaderboardBracketsRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentPrefsRecord
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select

// What each collection a space is synced for (Indexer.spaceCollections) holds indexed from it, by rkey: what a backfill compares with the
// listing (deleting the rest) and what dropping the space deletes.

/**
 * The rkeys of the [collection] records indexed from [space]. Fails for a collection with no entry in [SPACE_RKEYS], so one added to
 * `spaceCollections` can't silently go unlisted (and so never be dropped by a backfill).
 */
internal fun JdbcTransaction.spaceRkeys(space: SpaceUri, collection: String): Set<String> {
    val rkeys = checkNotNull(SPACE_RKEYS[collection]) { "$collection has no way to list the records indexed from a space" }
    return rkeys(space).toSet()
}

/** The collections [spaceRkeys] can list. */
internal val spaceRkeyCollections: Set<String>
    get() = SPACE_RKEYS.keys

private typealias RkeysOf = JdbcTransaction.(SpaceUri) -> List<String>

/** The rkeys of the rows of a table held by origin: [uri]'s, where [origin] is the space's. */
private fun byOrigin(uri: Column<String>, origin: Column<String>): RkeysOf = { space ->
    uri.table.select(uri).where { origin eq space.value }.map { it[uri] }.rkeys()
}

/** [byOrigin], plus the space's copies of [collection] waiting in [RecordShadows] ([place]). */
private fun placed(uri: Column<String>, origin: Column<String>, collection: String): RkeysOf {
    val held = byOrigin(uri, origin)
    return { space ->
        held(space) +
            RecordShadows.select(RecordShadows.uri)
                .where { (RecordShadows.origin eq space.value) and (RecordShadows.collection eq collection) }
                .map { it[RecordShadows.uri] }
                .rkeys()
    }
}

/** The rkeys of a table whose rows come only from their author's `self` or `interactions` space: [uri]'s, where [author] is its owner. */
private fun byAuthor(uri: Column<String>, author: Column<String>): RkeysOf = { space ->
    uri.table.select(uri).where { author eq space.authority.value }.map { it[uri] }.rkeys()
}

/** A singleton record at [rkey] in `self`, whose table holds one row per athlete in [did]. */
private fun singleton(did: Column<String>, rkey: String): RkeysOf = { space ->
    if (did.table.select(did).where { did eq space.authority.value }.empty()) emptyList() else listOf(rkey)
}

/** [FEED_ACTIVITY_INVITE][Nsid.FEED_ACTIVITY_INVITE]s, from `self` only, keyed by inviter and rkey. */
private fun invites(): RkeysOf = { space ->
    ActivityInvites.select(ActivityInvites.rkey)
        .where { ActivityInvites.inviterDid eq space.authority.value }
        .map { it[ActivityInvites.rkey] }
}

private fun List<String>.rkeys() = mapNotNull { AtUri(it).rkey }

/**
 * One entry per collection any space is synced for. Likes, comments, comment hides, blocks and mutes are never moved, so never shadowed.
 */
private val SPACE_RKEYS: Map<String, RkeysOf> =
    mapOf(
        Nsid.FEED_ACTIVITY to placed(Activities.uri, Activities.origin, Nsid.FEED_ACTIVITY),
        Nsid.FEED_ACTIVITY_ZONES to placed(ActivityZones.uri, ActivityZones.origin, Nsid.FEED_ACTIVITY_ZONES),
        Nsid.GEAR_ITEM to placed(GearItems.uri, GearItems.origin, Nsid.GEAR_ITEM),
        Nsid.FEED_POST to placed(Posts.uri, Posts.origin, Nsid.FEED_POST),
        // Each tier's copy is a row of its own, so never shadowed.
        Nsid.FEED_ACTIVITY_DETAILS to byOrigin(ActivityDetails.uri, ActivityDetails.origin),
        Nsid.FEED_ACTIVITY_STREAMS to byOrigin(ActivityStreams.uri, ActivityStreams.origin),
        Nsid.FEED_LIKE to byOrigin(Likes.uri, Likes.origin),
        Nsid.FEED_COMMENT to byOrigin(Comments.uri, Comments.origin),
        Nsid.FEED_REPOST to byOrigin(Reposts.uri, Reposts.origin),
        Nsid.FEED_COMMENT_HIDE to byOrigin(CommentHides.uri, CommentHides.origin),
        Nsid.GRAPH_BLOCK to byOrigin(Blocks.uri, Blocks.origin),
        Nsid.GRAPH_MUTE to byOrigin(Mutes.uri, Mutes.origin),
        // From the interactions space only.
        Nsid.FEED_SEGMENT_NAME_VOTE to byAuthor(SegmentNameVotes.uri, SegmentNameVotes.voterDid),
        // From the self space only.
        Nsid.FEED_SEGMENT_TRACK to byAuthor(SegmentTracks.uri, SegmentTracks.authorDid),
        Nsid.ACTOR_SEGMENT_PREFS to singleton(SegmentPrefs.did, SegmentPrefsRecord.RKEY),
        Nsid.ACTOR_GROUP_PREFS to singleton(GroupPrefs.did, GroupPrefsRecord.RKEY),
        Nsid.ACTOR_LEADERBOARD_BRACKETS to singleton(LeaderboardBrackets.did, LeaderboardBracketsRecord.RKEY),
        Nsid.FEED_ACTIVITY_INVITE to invites(),
    )
