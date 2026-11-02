package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityGroupPairs
import io.github.solcott.activitytracker.appview.db.Follows
import io.github.solcott.activitytracker.appview.db.GROUPING_MODES
import io.github.solcott.activitytracker.appview.db.GroupPrefs
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.GroupMode
import io.github.solcott.activitytracker.lexicon.app.GroupPartner
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
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
 * viewer nor the partner blocks the other, and the viewer doesn't mute the partner.
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
                    notBlocked(viewer, theirAuthor) and
                    notMuted(viewer, theirAuthor)
            }
            .orderBy(ActivityGroupPairs.matchedAt to SortOrder.DESC)
            .map { Triple(it[mine], it[theirs] to it[theirAuthor], it[ActivityGroupPairs.matchedAt]) }
    val rows =
        side(ActivityGroupPairs.activityA, ActivityGroupPairs.activityB, ActivityGroupPairs.authorB, ActivityGroupPairs.visibilityB) +
            side(ActivityGroupPairs.activityB, ActivityGroupPairs.activityA, ActivityGroupPairs.authorA, ActivityGroupPairs.visibilityA)
    val authors = basicProfiles(rows.map { it.second.second }, avatars)
    return rows
        .sortedByDescending { it.third }
        .groupBy({ it.first }) { it.second }
        .mapValues { (_, partners) ->
            partners.take(MAX_PARTNERS).mapNotNull { (uri, author) -> authors[author]?.let { GroupPartner(it, AtUri(uri)) } }
        }
        .filterValues { it.isNotEmpty() }
}

/**
 * True while the pair row's two athletes may still be paired: both chose to be grouped, an effective follow either way unless both chose
 * Anyone, and no block between them.
 */
private fun pairRuleHolds(): Op<Boolean> {
    fun modeIn(author: Column<String>, modes: List<String>): Op<Boolean> =
        exists(GroupPrefs.select(GroupPrefs.did).where { (GroupPrefs.did eq author) and (GroupPrefs.mode inList modes) })
    val a = ActivityGroupPairs.authorA
    val b = ActivityGroupPairs.authorB
    val bothAnyone = modeIn(a, listOf(GroupMode.ANYONE)) and modeIn(b, listOf(GroupMode.ANYONE))
    val followed =
        exists(
            Follows.select(Follows.uri).where {
                (((Follows.authorDid eq a) and (Follows.subjectDid eq b)) or ((Follows.authorDid eq b) and (Follows.subjectDid eq a))) and
                    effectiveFollow()
            }
        )
    return modeIn(a, GROUPING_MODES) and modeIn(b, GROUPING_MODES) and (bothAnyone or followed) and not(blocked(a, b))
}
