package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityGroupPairs
import io.github.solcott.activitytracker.appview.db.ActivityInvitePairs
import io.github.solcott.activitytracker.appview.db.ActivityInvites
import io.github.solcott.activitytracker.appview.db.Follows
import io.github.solcott.activitytracker.appview.db.GROUPING_MODES
import io.github.solcott.activitytracker.appview.db.GroupPrefs
import io.github.solcott.activitytracker.appview.db.groupingIn
import io.github.solcott.activitytracker.appview.db.leftGroup
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.GroupMode
import io.github.solcott.activitytracker.lexicon.app.GroupPartner
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select

// Serving pairs (feed/group-activities): each activity's partners a viewer may see, for activityView.group.

/** The most partners an activity's view lists. */
internal const val MAX_PARTNERS = 20

/**
 * The partners of each of [activities] that [viewer] may see, most recently paired first, at most [MAX_PARTNERS] each. A partner shows only
 * while the pair rule still holds ([pairRuleHolds]: follows, blocks and choices change without a rematch), the viewer may see the partner's
 * activity at its partner tier (the pair row's side: the narrowest of its visibility and its Start time and Map categories'), neither the
 * viewer nor the partner blocks the other, and the viewer doesn't mute the partner. Pairs from accepted invites (feed/activity-invites) are
 * listed the same way, under their own rule ([invitePairRuleHolds]) instead of the grouping choices, except that the pair's own two
 * athletes see each other whatever its tiers.
 */
internal fun JdbcTransaction.partnersOf(
    activities: Collection<String>,
    viewer: Did?,
    avatars: AvatarUrls,
): Map<String, List<GroupPartner>> {
    if (activities.isEmpty()) return emptyMap()
    val rule = pairRuleHolds()
    fun side(mine: Column<String>, theirs: Column<String>, theirAuthor: Column<String>, theirTier: Column<String>) =
        ActivityGroupPairs.join(Activities, JoinType.INNER, theirs, Activities.uri)
            .select(mine, theirs, theirAuthor, ActivityGroupPairs.matchedAt)
            .where {
                (mine inList activities) and
                    rule and
                    visibleTo(viewer, visibility = theirTier) and
                    accountShownTo(viewer, theirAuthor) and
                    notMuted(viewer, theirAuthor)
            }
            .orderBy(ActivityGroupPairs.matchedAt to SortOrder.DESC)
            .map { Triple(it[mine], it[theirs] to it[theirAuthor], it[ActivityGroupPairs.matchedAt]) }
    val inviteRule = invitePairRuleHolds()
    // The pair's two athletes always see each other, whatever its tiers, while the partner's activity is served: the inviter already sees
    // who joined, and adding granted the invitee the original.
    val member = viewer?.let { (ActivityInvitePairs.inviterDid eq it.value) or (ActivityInvitePairs.inviteeDid eq it.value) } ?: Op.FALSE
    fun inviteSide(mine: Column<String>, theirs: Column<String>, theirAuthor: Column<String>, theirTier: Column<String>) =
        ActivityInvitePairs.join(Activities, JoinType.INNER, theirs, Activities.uri)
            .select(mine, theirs, theirAuthor, ActivityInvitePairs.pairedAt)
            .where {
                (mine inList activities) and
                    inviteRule and
                    ((member and served(viewer, theirAuthor, theirs, Activities.cid)) or visibleTo(viewer, visibility = theirTier)) and
                    accountShownTo(viewer, theirAuthor) and
                    notMuted(viewer, theirAuthor)
            }
            .map { Triple(it[mine], it[theirs] to it[theirAuthor], it[ActivityInvitePairs.pairedAt]) }
    val rows =
        side(ActivityGroupPairs.activityA, ActivityGroupPairs.activityB, ActivityGroupPairs.authorB, ActivityGroupPairs.visibilityB) +
            side(ActivityGroupPairs.activityB, ActivityGroupPairs.activityA, ActivityGroupPairs.authorA, ActivityGroupPairs.visibilityA) +
            inviteSide(
                ActivityInvitePairs.originalUri,
                ActivityInvitePairs.copyUri,
                ActivityInvitePairs.inviteeDid,
                ActivityInvitePairs.copyVisibility,
            ) +
            inviteSide(
                ActivityInvitePairs.copyUri,
                ActivityInvitePairs.originalUri,
                ActivityInvitePairs.inviterDid,
                ActivityInvitePairs.originalVisibility,
            )
    val authors = basicProfiles(rows.map { it.second.second }, avatars)
    return rows
        .sortedByDescending { it.third }
        .distinctBy { it.first to it.second.first }
        .groupBy({ it.first }) { it.second }
        .mapValues { (_, partners) ->
            partners.take(MAX_PARTNERS).mapNotNull { (uri, author) -> authors[author]?.let { GroupPartner(it, AtUri(uri)) } }
        }
        .filterValues { it.isNotEmpty() }
}

/**
 * True while the pair row's two athletes may still be paired: both chose to be grouped, an effective follow either way unless both chose
 * Anyone, and no block between them. `GroupPairing.mayPair` is the same rule for the indexer, which has the two modes in hand; both build
 * on [groupingIn], [effectivelyFollows] and [blocked].
 */
private fun pairRuleHolds(): Op<Boolean> {
    fun modeIn(author: Column<String>, modes: List<String>): Op<Boolean> =
        exists(GroupPrefs.select(GroupPrefs.did).where { groupingIn(author, modes) })
    val a = ActivityGroupPairs.authorA
    val b = ActivityGroupPairs.authorB
    val bothAnyone = modeIn(a, listOf(GroupMode.ANYONE)) and modeIn(b, listOf(GroupMode.ANYONE))
    val followed = exists(Follows.select(Follows.uri).where { effectivelyFollows(a, b) or effectivelyFollows(b, a) })
    return modeIn(a, GROUPING_MODES) and modeIn(b, GROUPING_MODES) and (bothAnyone or followed) and not(blocked(a, b))
}

/**
 * True while an invite pair row still stands (feed/activity-invites): its invite is indexed, the copy still names the original in `copyOf`,
 * neither athlete blocks the other, and the original hasn't left its group. Follows and grouping choices don't matter.
 */
private fun invitePairRuleHolds(): Op<Boolean> {
    val p = ActivityInvitePairs
    val invite =
        exists(
            ActivityInvites.select(ActivityInvites.rkey).where {
                (ActivityInvites.inviterDid eq p.inviterDid) and (ActivityInvites.rkey eq p.inviteRkey)
            },
        )
    val copies = Activities.alias("copies")
    val namesOriginal =
        exists(
            copies.select(copies[Activities.uri]).where {
                (copies[Activities.uri] eq p.copyUri) and (copies[Activities.copyOf] eq p.originalUri)
            },
        )
    return invite and namesOriginal and not(leftGroup(p.originalUri)) and not(blocked(p.inviterDid, p.inviteeDid))
}
