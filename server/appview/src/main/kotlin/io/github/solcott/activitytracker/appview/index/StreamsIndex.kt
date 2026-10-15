package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.ProfileDetailRules
import io.github.solcott.activitytracker.appview.db.ActivityStreamData as StreamDataCache
import io.github.solcott.activitytracker.appview.db.ActivityStreams
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamsRecord
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.notInSubQuery
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert

// Indexing of `feed.activityStreams` records, which the Indexer hands off here.

/**
 * Streams must sit under their own activity's rkey in the same repo, and name their data blob. They're served at [placement]'s visibility:
 * where they're stored is who may see them. Zone fields a record still carries are ignored: zones are a record of their own
 * ([upsertZones]).
 */
@Suppress("LongParameterList") // The record, its raw JSON, and where it's from.
internal fun JdbcTransaction.upsertStreams(
    record: ActivityStreamsRecord,
    author: Did,
    uri: AtUri,
    cid: String,
    json: JsonObject,
    placement: Placement,
    now: Long,
) {
    val activity = record.activity
    require(activity.authority == author.value && activity.collection == Nsid.FEED_ACTIVITY && activity.rkey == uri.rkey) {
        "streams not under their activity's rkey"
    }
    val blob = requireNotNull(record.data?.ref?.link?.takeIf(ProfileDetailRules::isCid)) { "no data blob" }
    ActivityStreams.upsert {
        it[ActivityStreams.uri] = uri.value
        it[ActivityStreams.cid] = cid
        it[authorDid] = author.value
        it[activityUri] = activity.value
        it[ActivityStreams.visibility] = placement.visibility
        it[ActivityStreams.origin] = placement.origin
        it[ActivityStreams.record] = AtJson.encodeToString(JsonObject.serializer(), json)
        it[channels] =
            AtJson.encodeToString(
                ListSerializer(String.serializer()),
                record.channels.take(ActivityStreamsRecord.MAX_CHANNELS).map { c -> c.take(MAX_CHANNEL_LENGTH) },
            )
        it[blobCid] = blob
        it[indexedAt] = now
    }
    dropOrphanedStreamData()
}

/** Cached stream data no streams record names any more (deleted, or replaced by a new blob). */
internal fun JdbcTransaction.dropOrphanedStreamData() {
    StreamDataCache.deleteWhere { StreamDataCache.blobCid notInSubQuery ActivityStreams.select(ActivityStreams.blobCid) }
}

// Records are untrusted, so text is clamped to the lexicon maxLength before it's stored.
private const val MAX_CHANNEL_LENGTH = 32
