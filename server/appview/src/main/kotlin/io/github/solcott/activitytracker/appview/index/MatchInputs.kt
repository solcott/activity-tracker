package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityMembers
import io.github.solcott.activitytracker.appview.db.SegmentCandidateRiders
import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.SegmentTracks
import io.github.solcott.activitytracker.appview.db.usableSegmentTracks
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select

// What an activity, details or streams change does to the activity's segment efforts (segments/segment-efforts).

/** What matching an activity depends on, read before a change to it and compared after. */
internal data class MatchInputs(val sport: String, val subSport: String?, val claims: Set<String>)

/** The activity the [collection] record at [uri] belongs to: it, or the activity sharing its rkey. */
internal fun effortActivity(collection: String, uri: AtUri): String? =
    uri.rkey?.takeIf { collection in ACTIVITY_PARTS }?.let { AtUri.of(Did(uri.authority), Nsid.FEED_ACTIVITY, it).value }

internal fun JdbcTransaction.matchInputs(activity: String): MatchInputs? {
    val row = Activities.select(Activities.sport, Activities.subSport).where { Activities.uri eq activity }.firstOrNull() ?: return null
    val claims =
        ActivityMembers.select(ActivityMembers.memberUri)
            .where { ActivityMembers.combinedUri eq activity }
            .map { it[ActivityMembers.memberUri] }
    return MatchInputs(row[Activities.sport], row[Activities.subSport], claims.toSet())
}

/**
 * After [activity] or one of its details or streams records changed, given its [before] inputs: a deleted activity loses its efforts and
 * its track's candidate riders at once; one with efforts or a usable segment track is queued to be matched again when its sport or
 * sub-sport changed or it's new, and else to have its efforts re-tiered. Members it gained or lost as a combined activity are matched
 * again, to drop or regain theirs.
 */
internal fun JdbcTransaction.activityChanged(activity: String, before: MatchInputs?, jobs: SegmentJobs, now: Long) {
    val after = matchInputs(activity)
    if (after == null) {
        SegmentEfforts.deleteWhere { activityUri eq activity }
        SegmentCandidateRiders.deleteWhere { trackUri eq trackOf(activity) }
        return
    }
    if (matchable(activity)) {
        if (before == null || before.sport != after.sport || before.subSport != after.subSport) jobs.enqueueTrack(trackOf(activity), now)
        else jobs.enqueueTiers(activity, now)
    }
    for (member in (before?.claims.orEmpty() symmetricDifference after.claims)) {
        if (matchable(member)) jobs.enqueueTrack(trackOf(member), now)
    }
}

/** Whether [activity] has efforts or a usable segment track, so a change to it may change them. */
private fun JdbcTransaction.matchable(activity: String): Boolean =
    !SegmentEfforts.select(SegmentEfforts.id).where { SegmentEfforts.activityUri eq activity }.limit(1).empty() ||
        !usableSegmentTracks().andWhere { SegmentTracks.activityUri eq activity }.limit(1).empty()

/** The segment track of [activity], under the same rkey in the athlete's `self` space. */
private fun trackOf(activity: String): String {
    val uri = AtUri(activity)
    return AtUri.of(Did(uri.authority), Nsid.FEED_SEGMENT_TRACK, requireNotNull(uri.rkey) { "an activity URI without an rkey" }).value
}

private infix fun <T> Set<T>.symmetricDifference(other: Set<T>): Set<T> = (this - other) + (other - this)

private val ACTIVITY_PARTS = setOf(Nsid.FEED_ACTIVITY, Nsid.FEED_ACTIVITY_DETAILS, Nsid.FEED_ACTIVITY_STREAMS)
