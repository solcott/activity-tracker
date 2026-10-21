package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.SegmentPrefs
import io.github.solcott.activitytracker.appview.db.SegmentTrackCells
import io.github.solcott.activitytracker.appview.db.SegmentTracks
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.SegmentPrefsRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackRecord
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert

private const val MAX_REVISION_LENGTH = 32

/**
 * A put of a segment track or the segment matching setting, from [author]'s `self` space. Each queues the tracks it makes matchable; the
 * matcher itself checks the setting ([usableSegmentTracks][io.github.solcott.activitytracker.appview.db.usableSegmentTracks]).
 */
@Suppress("LongParameterList") // The record, where it's from, and the queue.
internal fun JdbcTransaction.upsertSegments(collection: String, json: JsonObject, author: Did, uri: AtUri, jobs: SegmentJobs, now: Long) {
    if (collection == Nsid.FEED_SEGMENT_TRACK) {
        // Only the leaderboard exclusion changed when the points and their trimming are the same: nothing to match again.
        if (upsertSegmentTrack(AtJson.decodeFromJsonElement(SegmentTrackRecord.serializer(), json), author, uri, now)) {
            jobs.enqueueTrack(uri.value, now)
        }
    } else {
        val record = AtJson.decodeFromJsonElement(SegmentPrefsRecord.serializer(), json)
        upsertSegmentPrefs(record, author, uri)
        // Tracks stored while matching was off are matched once it's on.
        if (record.matching) trackUris(author).forEach { jobs.enqueueTrack(it, now) }
    }
}

/** A delete of a segment track, with its cells and efforts, or of the setting, which counts as matching off. */
internal fun JdbcTransaction.deleteSegments(collection: String, uri: AtUri) {
    if (collection == Nsid.FEED_SEGMENT_TRACK) dropTracks(listOf(uri.value))
    else if (uri.rkey == SegmentPrefsRecord.RKEY) deleteSegmentPrefs(Did(uri.authority))
}

/**
 * A segment track from [author]'s `self` space. Kept only for an activity of their own under the same rkey, with a data blob; stored
 * whatever their setting says, since ops can arrive in any order, but read only while it's on
 * ([usableSegmentTracks][io.github.solcott.activitytracker.appview.db.usableSegmentTracks]). Returns whether its points may have changed:
 * false when only its leaderboard exclusion did, so it needn't be matched again.
 */
internal fun JdbcTransaction.upsertSegmentTrack(record: SegmentTrackRecord, author: Did, uri: AtUri, now: Long): Boolean {
    require(record.activity.authority == author.value && record.activity.collection == Nsid.FEED_ACTIVITY) { "another's activity" }
    require(record.activity.rkey == uri.rkey) { "a segment track under another rkey" }
    val cid = requireNotNull(record.data?.ref?.link) { "no data blob" }
    val revision = record.zonesRevision?.take(MAX_REVISION_LENGTH)
    val stored =
        SegmentTracks.select(SegmentTracks.dataCid, SegmentTracks.zonesRevision)
            .where { SegmentTracks.uri eq uri.value }
            .firstOrNull()
            ?.let { it[SegmentTracks.dataCid] to it[SegmentTracks.zonesRevision] }
    SegmentTracks.upsert {
        it[SegmentTracks.uri] = uri.value
        it[authorDid] = author.value
        it[activityUri] = record.activity.value
        it[dataCid] = cid
        it[zonesRevision] = revision
        it[indexedAt] = now
        it[leaderboardExcluded] = record.excludeFromLeaderboards == true
    }
    return stored != (cid to revision)
}

/** [author]'s segment matching setting, from their `self` space. Turning it off drops everything held from their segment tracks. */
internal fun JdbcTransaction.upsertSegmentPrefs(record: SegmentPrefsRecord, author: Did, uri: AtUri) {
    require(uri.rkey == SegmentPrefsRecord.RKEY) { "segment prefs under another rkey" }
    SegmentPrefs.upsert {
        it[did] = author.value
        it[matching] = record.matching
        it[updatedAt] = record.updatedAt.toEpochMilliseconds()
        it[hideFromLeaderboards] = record.hideFromLeaderboards == true
    }
    if (!record.matching) dropSegmentTracks(author)
}

/** [author]'s setting gone: no record means matching is off, so their segment tracks go too. */
internal fun JdbcTransaction.deleteSegmentPrefs(author: Did) {
    SegmentPrefs.deleteWhere { did eq author.value }
    dropSegmentTracks(author)
}

/** Everything held from [author]'s segment tracks: the tracks, their cells and every effort matched from them. */
internal fun JdbcTransaction.dropSegmentTracks(author: Did) {
    SegmentEfforts.deleteWhere { authorDid eq author.value }
    dropTracks(trackUris(author))
}

private fun JdbcTransaction.trackUris(author: Did): List<String> =
    SegmentTracks.select(SegmentTracks.uri).where { SegmentTracks.authorDid eq author.value }.map { it[SegmentTracks.uri] }

private fun JdbcTransaction.dropTracks(uris: List<String>) {
    if (uris.isEmpty()) return
    SegmentEfforts.deleteWhere { trackUri inList uris }
    SegmentTrackCells.deleteWhere { trackUri inList uris }
    SegmentTracks.deleteWhere { SegmentTracks.uri inList uris }
}
