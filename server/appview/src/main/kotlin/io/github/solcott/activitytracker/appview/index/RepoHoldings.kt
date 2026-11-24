package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityDetails
import io.github.solcott.activitytracker.appview.db.ActivityStreams
import io.github.solcott.activitytracker.appview.db.ActivityZones
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.Blocks
import io.github.solcott.activitytracker.appview.db.CommentHides
import io.github.solcott.activitytracker.appview.db.Comments
import io.github.solcott.activitytracker.appview.db.DiveSites
import io.github.solcott.activitytracker.appview.db.FollowApprovals
import io.github.solcott.activitytracker.appview.db.Follows
import io.github.solcott.activitytracker.appview.db.GearItems
import io.github.solcott.activitytracker.appview.db.Likes
import io.github.solcott.activitytracker.appview.db.Origins
import io.github.solcott.activitytracker.appview.db.Paths
import io.github.solcott.activitytracker.appview.db.Posts
import io.github.solcott.activitytracker.appview.db.RecordShadows
import io.github.solcott.activitytracker.appview.db.Reposts
import io.github.solcott.activitytracker.appview.db.SegmentNameProposals
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.TrailDetailSuggestions
import io.github.solcott.activitytracker.appview.db.TrailReports
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ProfileRecord
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select

// What the AppView holds indexed from an account's repo (as opposed to its spaces), by collection: what a reindex (admin/repo-reindex)
// compares the repo with, deleting the rest and skipping records indexed at the CID the repo still has. The repo-side counterpart of
// SPACE_RKEYS (SpaceRkeys.kt).

/**
 * The [collection] records indexed from [author]'s repo: rkey → the CID they were indexed at, null where the index keeps none. Fails for a
 * collection with no entry in [REPO_HOLDINGS], so one added to `Nsid.indexedCollections` can't go unlisted (and so never be removed).
 */
internal fun JdbcTransaction.repoHoldings(author: Did, collection: String): Map<String, String?> {
    val holdings = checkNotNull(REPO_HOLDINGS[collection]) { "$collection has no way to list the records indexed from a repo" }
    return holdings(author, collection)
}

/** How many [collection] records are indexed from [author]'s repo: [repoHoldings]' size, for the admin site's account page. */
internal fun JdbcTransaction.repoHoldingCount(author: Did, collection: String): Int = repoHoldings(author, collection).size

/** The collections [repoHoldings] can list. */
internal val repoHoldingCollections: Set<String>
    get() = REPO_HOLDINGS.keys

private typealias HoldingsOf = JdbcTransaction.(author: Did, collection: String) -> Map<String, String?>

/**
 * Rows of a table keyed by record URI: [author]'s [collection] URIs, from the repo when the table keeps an [origin], with their [cid] when
 * it keeps one.
 */
private fun byUri(uri: Column<String>, cid: Column<String>? = null, origin: Column<String>? = null): HoldingsOf = { author, collection ->
    val prefix = "at://${author.value}/$collection/"
    val columns = listOfNotNull(uri, cid)
    uri.table
        .select(columns)
        .where { (uri like "${likeEscaped(prefix)}%") and (origin?.let { it eq Origins.REPO } ?: Op.TRUE) }
        .mapNotNull { row -> rkeyUnder(row[uri], prefix)?.let { it to cid?.let { c -> row[c] } } }
        .toMap()
}

/** [byUri], plus the repo's copies waiting in [RecordShadows] while a space's copy is the row. */
private fun placed(uri: Column<String>, cid: Column<String>, origin: Column<String>): HoldingsOf {
    val held = byUri(uri, cid, origin)
    return { author, collection ->
        val prefix = "at://${author.value}/$collection/"
        val shadows =
            RecordShadows.select(RecordShadows.uri, RecordShadows.cid)
                .where {
                    (RecordShadows.origin eq Origins.REPO) and
                        (RecordShadows.collection eq collection) and
                        (RecordShadows.uri like "${likeEscaped(prefix)}%")
                }
                .mapNotNull { row -> rkeyUnder(row[RecordShadows.uri], prefix)?.let { it to row[RecordShadows.cid] } }
        held(author, collection) + shadows
    }
}

/** The profile, a singleton at `self` whose fields live on the account's `actors` row (no CID kept). */
private val profile: HoldingsOf = { author, _ ->
    if (Actors.select(Actors.did).where { Actors.did eq author.value }.empty()) emptyMap() else mapOf(ProfileRecord.RKEY to null)
}

/** The rkey of [uri] when it is directly under [prefix] (`at://did/collection/`); null for anything else a LIKE matched. */
private fun rkeyUnder(uri: String, prefix: String): String? =
    uri.removePrefix(prefix).takeIf { uri.startsWith(prefix) && it.isNotEmpty() && '/' !in it && AtUri(uri).rkey == it }

/** [value] with LIKE's wildcards escaped (H2 and PostgreSQL both default to `\` as the escape). */
private fun likeEscaped(value: String) = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

/** One entry per collection in `Nsid.indexedCollections`. */
private val REPO_HOLDINGS: Map<String, HoldingsOf> =
    mapOf(
        Nsid.ACTOR_PROFILE to profile,
        Nsid.FEED_ACTIVITY to placed(Activities.uri, Activities.cid, Activities.origin),
        Nsid.FEED_ACTIVITY_ZONES to placed(ActivityZones.uri, ActivityZones.cid, ActivityZones.origin),
        Nsid.FEED_POST to placed(Posts.uri, Posts.cid, Posts.origin),
        Nsid.GEAR_ITEM to placed(GearItems.uri, GearItems.cid, GearItems.origin),
        // Each origin's copy is a row of its own, so never shadowed.
        Nsid.FEED_ACTIVITY_DETAILS to byUri(ActivityDetails.uri, ActivityDetails.cid, ActivityDetails.origin),
        Nsid.FEED_ACTIVITY_STREAMS to byUri(ActivityStreams.uri, ActivityStreams.cid, ActivityStreams.origin),
        Nsid.FEED_LIKE to byUri(Likes.uri, origin = Likes.origin),
        Nsid.FEED_COMMENT to byUri(Comments.uri, Comments.cid, Comments.origin),
        Nsid.FEED_COMMENT_HIDE to byUri(CommentHides.uri, origin = CommentHides.origin),
        Nsid.FEED_REPOST to byUri(Reposts.uri, origin = Reposts.origin),
        // Public records indexed from repos only.
        Nsid.FEED_SEGMENT to byUri(Segments.uri, Segments.cid),
        Nsid.FEED_SEGMENT_NAME_PROPOSAL to byUri(SegmentNameProposals.uri, SegmentNameProposals.cid),
        Nsid.FEED_TRAIL_DETAIL_SUGGESTION to byUri(TrailDetailSuggestions.uri, TrailDetailSuggestions.cid),
        Nsid.FEED_TRAIL_REPORT to byUri(TrailReports.uri, TrailReports.cid),
        Nsid.FEED_DIVE_SITE to byUri(DiveSites.uri, DiveSites.cid),
        Nsid.FEED_PATH to byUri(Paths.uri, Paths.cid),
        Nsid.GRAPH_FOLLOW to byUri(Follows.uri),
        Nsid.GRAPH_FOLLOW_APPROVAL to byUri(FollowApprovals.uri),
        // The app's blocks come from interactions spaces, Bluesky's from the repo; their URIs' collections tell them apart.
        Nsid.BSKY_GRAPH_BLOCK to byUri(Blocks.uri, origin = Blocks.origin),
    )
