package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityGroupPairs
import io.github.solcott.activitytracker.appview.db.GroupPrefs
import io.github.solcott.activitytracker.appview.db.SegmentTracks
import io.github.solcott.activitytracker.appview.db.groupableTracks
import io.github.solcott.activitytracker.appview.db.groupingIn
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.GroupMode
import io.github.solcott.activitytracker.lexicon.app.GroupPrefsRecord
import kotlin.time.Duration.Companion.days
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.stringParam
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert

// Indexing for group activities (feed/group-activities): the grouping choice and the drops that keep pairs within the pair rule.

private const val MAX_MODE_LENGTH = 16

/**
 * [author]'s grouping choice, from their `self` space. Off (or a value this AppView doesn't know) drops their pairs, and their segment
 * tracks too when segment matching is also off; on queues their tracks to be matched, which pairs them.
 */
internal fun JdbcTransaction.upsertGroupPrefs(record: GroupPrefsRecord, author: Did, uri: AtUri, jobs: SegmentJobs, now: Long) {
    require(uri.rkey == GroupPrefsRecord.RKEY) { "group prefs under another rkey" }
    val wasOn = groupingOn(author)
    GroupPrefs.upsert {
        it[did] = author.value
        it[mode] = record.mode.take(MAX_MODE_LENGTH)
        it[updatedAt] = record.updatedAt.toEpochMilliseconds()
    }
    if (!GroupMode.isOn(record.mode)) {
        dropGrouping(author)
    } else if (!wasOn) {
        // Tracks stored while grouping was off are matched, and so paired, once it's on.
        trackUrisOf(author).forEach { jobs.enqueueTrack(it, now) }
    }
}

/** [author]'s grouping choice gone: no record means grouping is off. */
internal fun JdbcTransaction.deleteGroupPrefs(author: Did) {
    GroupPrefs.deleteWhere { did eq author.value }
    dropGrouping(author)
}

/** Whether [author]'s grouping choice is on now. */
internal fun JdbcTransaction.groupingOn(author: Did): Boolean =
    !GroupPrefs.select(GroupPrefs.did).where { groupingIn(stringParam(author.value)) }.empty()

/** Grouping off for [author]: their pairs go, and their segment tracks too unless segment matching still reads them. */
private fun JdbcTransaction.dropGrouping(author: Did) {
    dropPairsOf(author)
    if (!segmentMatchingOn(author)) dropTracks(trackUrisOf(author))
}

/** Every pair one of [author]'s activities is in. */
internal fun JdbcTransaction.dropPairsOf(author: Did) {
    ActivityGroupPairs.deleteWhere { (authorA eq author.value) or (authorB eq author.value) }
}

/** Every pair one of [activities] is in. */
internal fun JdbcTransaction.dropPairsOfActivities(activities: Collection<String>) {
    if (activities.isEmpty()) return
    ActivityGroupPairs.deleteWhere { (activityA inList activities) or (activityB inList activities) }
}

/** Every pair of an activity of [a] with one of [b]'s, as a block between them calls for. */
internal fun JdbcTransaction.dropPairsBetween(a: Did, b: Did) {
    ActivityGroupPairs.deleteWhere { ((authorA eq a.value) and (authorB eq b.value)) or ((authorA eq b.value) and (authorB eq a.value)) }
}

/**
 * A follow or approval between [a] and [b] may have connected them (feed/group-activities: Connected needs an effective follow), so while
 * both are grouping, [a]'s groupable tracks of the last [REGROUP_WINDOW] are paired again, which finds [b]'s too. An activity done together
 * before a follow then pairs once they follow.
 */
internal fun JdbcTransaction.regroupRecent(a: Did, b: Did, jobs: SegmentJobs, now: Long) {
    if (a == b || !groupingOn(a) || !groupingOn(b)) return
    groupableTracks()
        .adjustColumnSet { join(Activities, JoinType.INNER, SegmentTracks.activityUri, Activities.uri) }
        .andWhere { (SegmentTracks.authorDid eq a.value) and (Activities.startedAt greaterEq now - REGROUP_WINDOW.inWholeMilliseconds) }
        .map { it[SegmentTracks.uri] }
        .forEach { jobs.enqueueGroup(it, now) }
}

/** How far back a new follow pairs activities done together. */
private val REGROUP_WINDOW = 30.days

internal fun JdbcTransaction.trackUrisOf(author: Did): List<String> =
    SegmentTracks.select(SegmentTracks.uri).where { SegmentTracks.authorDid eq author.value }.map { it[SegmentTracks.uri] }
