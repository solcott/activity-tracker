package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.SegmentPrefs
import io.github.solcott.activitytracker.appview.db.SegmentTracks
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.SegmentPrefsRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackRecord
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.upsert

private const val MAX_REVISION_LENGTH = 32

/** A put of a segment track or the segment matching setting, from [author]'s `self` space. */
internal fun JdbcTransaction.upsertSegments(collection: String, json: JsonObject, author: Did, uri: AtUri, now: Long) {
    if (collection == Nsid.FEED_SEGMENT_TRACK) {
        upsertSegmentTrack(AtJson.decodeFromJsonElement(SegmentTrackRecord.serializer(), json), author, uri, now)
    } else {
        upsertSegmentPrefs(AtJson.decodeFromJsonElement(SegmentPrefsRecord.serializer(), json), author, uri)
    }
}

/** A delete of a segment track, or of the setting, which counts as matching off. */
internal fun JdbcTransaction.deleteSegments(collection: String, uri: AtUri) {
    if (collection == Nsid.FEED_SEGMENT_TRACK) SegmentTracks.deleteWhere { SegmentTracks.uri eq uri.value }
    else if (uri.rkey == SegmentPrefsRecord.RKEY) deleteSegmentPrefs(Did(uri.authority))
}

/**
 * A segment track from [author]'s `self` space. Kept only for an activity of their own under the same rkey, with a data blob; stored
 * whatever their setting says, since ops can arrive in any order, but read only while it's on
 * ([usableSegmentTracks][io.github.solcott.activitytracker.appview.db.usableSegmentTracks]).
 */
internal fun JdbcTransaction.upsertSegmentTrack(record: SegmentTrackRecord, author: Did, uri: AtUri, now: Long) {
    require(record.activity.authority == author.value && record.activity.collection == Nsid.FEED_ACTIVITY) { "another's activity" }
    require(record.activity.rkey == uri.rkey) { "a segment track under another rkey" }
    val cid = requireNotNull(record.data?.ref?.link) { "no data blob" }
    SegmentTracks.upsert {
        it[SegmentTracks.uri] = uri.value
        it[authorDid] = author.value
        it[activityUri] = record.activity.value
        it[dataCid] = cid
        it[zonesRevision] = record.zonesRevision?.take(MAX_REVISION_LENGTH)
        it[indexedAt] = now
    }
}

/** [author]'s segment matching setting, from their `self` space. Turning it off drops everything held from their segment tracks. */
internal fun JdbcTransaction.upsertSegmentPrefs(record: SegmentPrefsRecord, author: Did, uri: AtUri) {
    require(uri.rkey == SegmentPrefsRecord.RKEY) { "segment prefs under another rkey" }
    SegmentPrefs.upsert {
        it[did] = author.value
        it[matching] = record.matching
        it[updatedAt] = record.updatedAt.toEpochMilliseconds()
    }
    if (!record.matching) dropSegmentTracks(author)
}

/** [author]'s setting gone: no record means matching is off, so their segment tracks go too. */
internal fun JdbcTransaction.deleteSegmentPrefs(author: Did) {
    SegmentPrefs.deleteWhere { did eq author.value }
    dropSegmentTracks(author)
}

/** Everything held from [author]'s segment tracks. */
internal fun JdbcTransaction.dropSegmentTracks(author: Did) {
    SegmentTracks.deleteWhere { authorDid eq author.value }
}
