package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.ActivityInvitePairs
import io.github.solcott.activitytracker.appview.db.LeaderboardBrackets
import io.github.solcott.activitytracker.appview.db.SegmentCandidateRiders
import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.SegmentPrefs
import io.github.solcott.activitytracker.appview.db.SegmentTrackCells
import io.github.solcott.activitytracker.appview.db.SegmentTracks
import io.github.solcott.activitytracker.appview.db.matchingOn
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GroupPrefsRecord
import io.github.solcott.activitytracker.lexicon.app.LeaderboardAgeGroup
import io.github.solcott.activitytracker.lexicon.app.LeaderboardBracketsRecord
import io.github.solcott.activitytracker.lexicon.app.LeaderboardGender
import io.github.solcott.activitytracker.lexicon.app.LeaderboardWeightClass
import io.github.solcott.activitytracker.lexicon.app.SegmentPrefsRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackRecord
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.stringParam
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert

private const val MAX_REVISION_LENGTH = 32

/**
 * A put of a segment track, the segment matching setting, the grouping choice or the leaderboard brackets, from [author]'s `self` space.
 * The first three queue the tracks they make matchable; the matcher itself checks the settings
 * ([usableSegmentTracks][io.github.solcott.activitytracker.appview.db.usableSegmentTracks],
 * [groupableTracks][io.github.solcott.activitytracker.appview.db.groupableTracks]).
 */
@Suppress("LongParameterList") // The record, where it's from, and the queue.
internal fun JdbcTransaction.upsertSegments(collection: String, json: JsonObject, author: Did, uri: AtUri, jobs: SegmentJobs, now: Long) {
    if (collection == Nsid.ACTOR_GROUP_PREFS) {
        upsertGroupPrefs(AtJson.decodeFromJsonElement(GroupPrefsRecord.serializer(), json), author, uri, jobs, now)
    } else if (collection == Nsid.ACTOR_LEADERBOARD_BRACKETS) {
        upsertLeaderboardBrackets(AtJson.decodeFromJsonElement(LeaderboardBracketsRecord.serializer(), json), author, uri)
    } else if (collection == Nsid.FEED_SEGMENT_TRACK) {
        // Only the leaderboard exclusion changed when the points, their trimming and the group exclusion are the same: nothing to match
        // again. Leaving the group drops the activity's pairs at once; rejoining pairs it again once matched.
        val record = AtJson.decodeFromJsonElement(SegmentTrackRecord.serializer(), json)
        if (upsertSegmentTrack(record, author, uri, now)) jobs.enqueueTrack(uri.value, now)
        if (record.groupExcluded == true) dropPairsOfActivities(listOf(record.activity.value))
    } else {
        val record = AtJson.decodeFromJsonElement(SegmentPrefsRecord.serializer(), json)
        upsertSegmentPrefs(record, author, uri)
        // Tracks stored while matching was off are matched once it's on.
        if (record.matching) trackUrisOf(author).forEach { jobs.enqueueTrack(it, now) }
    }
}

/**
 * A delete of a segment track, with its cells, efforts, candidate riders and pairs, of the matching setting or the grouping choice, which
 * count as off, or of the leaderboard brackets, which takes the athlete off every filtered board.
 */
internal fun JdbcTransaction.deleteSegments(collection: String, uri: AtUri) {
    if (collection == Nsid.FEED_SEGMENT_TRACK) dropTracks(listOf(uri.value))
    else if (collection == Nsid.ACTOR_GROUP_PREFS) {
        if (uri.rkey == GroupPrefsRecord.RKEY) deleteGroupPrefs(Did(uri.authority))
    } else if (collection == Nsid.ACTOR_LEADERBOARD_BRACKETS) {
        if (uri.rkey == LeaderboardBracketsRecord.RKEY) LeaderboardBrackets.deleteWhere { did eq uri.authority }
    } else if (uri.rkey == SegmentPrefsRecord.RKEY) deleteSegmentPrefs(Did(uri.authority))
}

/**
 * A segment track from [author]'s `self` space. Kept only for an activity of their own under the same rkey, with a data blob; stored
 * whatever their setting says, since ops can arrive in any order, but read only while it's on
 * ([usableSegmentTracks][io.github.solcott.activitytracker.appview.db.usableSegmentTracks]). Returns whether its points may have changed:
 * false when only its leaderboard exclusion did, so it needn't be matched again. A changed group exclusion counts as a change.
 */
internal fun JdbcTransaction.upsertSegmentTrack(record: SegmentTrackRecord, author: Did, uri: AtUri, now: Long): Boolean {
    requireOwnActivityChild(record.activity, author, uri, "segment track")
    val cid = requireNotNull(record.data?.ref?.link) { "no data blob" }
    val revision = record.zonesRevision?.take(MAX_REVISION_LENGTH)
    val stored =
        SegmentTracks.select(SegmentTracks.dataCid, SegmentTracks.zonesRevision, SegmentTracks.groupExcluded)
            .where { SegmentTracks.uri eq uri.value }
            .firstOrNull()
            ?.let { Triple(it[SegmentTracks.dataCid], it[SegmentTracks.zonesRevision], it[SegmentTracks.groupExcluded]) }
    SegmentTracks.upsert {
        it[SegmentTracks.uri] = uri.value
        it[authorDid] = author.value
        it[activityUri] = record.activity.value
        it[dataCid] = cid
        it[zonesRevision] = revision
        it[indexedAt] = now
        it[leaderboardExcluded] = record.excludeFromLeaderboards == true
        it[groupExcluded] = record.groupExcluded == true
    }
    return stored != Triple(cid, revision, record.groupExcluded == true)
}

/**
 * [author]'s segment matching setting, from their `self` space. Turning it off drops everything segments derived from their segment tracks,
 * and the tracks too unless grouping still reads them.
 */
internal fun JdbcTransaction.upsertSegmentPrefs(record: SegmentPrefsRecord, author: Did, uri: AtUri) {
    require(uri.rkey == SegmentPrefsRecord.RKEY) { "segment prefs under another rkey" }
    SegmentPrefs.upsert {
        it[did] = author.value
        it[matching] = record.matching
        it[updatedAt] = record.updatedAt.toEpochMilliseconds()
        it[hideFromLeaderboards] = record.hideFromLeaderboards == true
    }
    if (!record.matching) dropSegmentMatching(author)
}

/**
 * [author]'s leaderboard brackets (segments/leaderboard-brackets), from their `self` space: kept only as brackets, with any value this
 * AppView doesn't know stored as absent.
 */
internal fun JdbcTransaction.upsertLeaderboardBrackets(record: LeaderboardBracketsRecord, author: Did, uri: AtUri) {
    require(uri.rkey == LeaderboardBracketsRecord.RKEY) { "leaderboard brackets under another rkey" }
    LeaderboardBrackets.upsert {
        it[did] = author.value
        it[ageGroup] = record.ageGroup?.takeIf { v -> v in LeaderboardAgeGroup.all }
        it[weightClass] = record.weightClass?.takeIf { v -> v in LeaderboardWeightClass.all }
        it[gender] = record.gender?.takeIf { v -> v in LeaderboardGender.all }
        it[updatedAt] = record.updatedAt.toEpochMilliseconds()
    }
}

/** [author]'s setting gone: no record means matching is off. */
internal fun JdbcTransaction.deleteSegmentPrefs(author: Did) {
    SegmentPrefs.deleteWhere { did eq author.value }
    dropSegmentMatching(author)
}

/** Whether [author]'s segment matching setting is on now. */
internal fun JdbcTransaction.segmentMatchingOn(author: Did): Boolean =
    !SegmentPrefs.select(SegmentPrefs.did).where { matchingOn(stringParam(author.value)) }.empty()

/**
 * Segment matching off for [author]: every effort matched from their tracks and their riders of staged candidates go (a candidate they made
 * ridden stays ridden), and the tracks with their cells too unless grouping still reads them.
 */
private fun JdbcTransaction.dropSegmentMatching(author: Did) {
    SegmentEfforts.deleteWhere { authorDid eq author.value }
    SegmentCandidateRiders.deleteWhere { authorDid eq author.value }
    if (!groupingOn(author)) dropTracks(trackUrisOf(author))
}

/**
 * Everything held from [author]'s segment tracks, as when their `self` space or account is gone: the tracks, their cells, every effort
 * matched from them, their riders of staged candidates and every pair of their activities.
 */
internal fun JdbcTransaction.dropSegmentTracks(author: Did) {
    SegmentEfforts.deleteWhere { authorDid eq author.value }
    SegmentCandidateRiders.deleteWhere { authorDid eq author.value }
    dropPairsOf(author)
    dropTracks(trackUrisOf(author))
}

/**
 * The segment tracks at [uris], with everything derived from them: cells, efforts, candidate riders and their activities' pairs. An invite
 * pair outlives its original's track, but is read as left only while a `group_excluded` track says so, so a left one goes with the track.
 */
internal fun JdbcTransaction.dropTracks(uris: List<String>) {
    if (uris.isEmpty()) return
    dropPairsOfActivities(
        SegmentTracks.select(SegmentTracks.activityUri).where { SegmentTracks.uri inList uris }.map { it[SegmentTracks.activityUri] }
    )
    SegmentTracks.select(SegmentTracks.activityUri)
        .where { (SegmentTracks.uri inList uris) and (SegmentTracks.groupExcluded eq true) }
        .map { it[SegmentTracks.activityUri] }
        .takeIf { it.isNotEmpty() }
        ?.let { left -> ActivityInvitePairs.deleteWhere { originalUri inList left } }
    SegmentEfforts.deleteWhere { trackUri inList uris }
    SegmentCandidateRiders.deleteWhere { trackUri inList uris }
    SegmentTrackCells.deleteWhere { trackUri inList uris }
    SegmentTracks.deleteWhere { SegmentTracks.uri inList uris }
}
