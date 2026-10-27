package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.ProfileDetailRules
import io.github.solcott.activitytracker.appview.db.ActivityStreamData as StreamDataCache
import io.github.solcott.activitytracker.appview.db.ActivityStreamFetchFailures
import io.github.solcott.activitytracker.appview.db.ActivityStreams
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamsRecord
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
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
    val replaced = streamBlobs { (ActivityStreams.uri eq uri.value) and (ActivityStreams.origin eq placement.origin) }
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
    dropOrphanedStreamData(replaced - blob)
}

/** The blob CIDs the streams rows matching [where] name: what to pass [dropOrphanedStreamData] once they're deleted or replaced. */
internal fun JdbcTransaction.streamBlobs(where: () -> Op<Boolean>): Set<String> =
    ActivityStreams.select(ActivityStreams.blobCid).where { where() }.mapTo(mutableSetOf()) { it[ActivityStreams.blobCid] }

/**
 * Cached stream data, and failed fetches of it, for those of [cids] that no streams record names any more (deleted, or replaced by a new
 * blob). Only the CIDs a change let go of are checked, so a write costs a lookup per CID rather than a pass over the whole cache.
 */
internal fun JdbcTransaction.dropOrphanedStreamData(cids: Collection<String>) {
    if (cids.isEmpty()) return
    val stillNamed =
        ActivityStreams.select(ActivityStreams.blobCid)
            .where { ActivityStreams.blobCid inList cids }
            .mapTo(mutableSetOf()) { it[ActivityStreams.blobCid] }
    val orphans = cids - stillNamed
    if (orphans.isEmpty()) return
    StreamDataCache.deleteWhere { StreamDataCache.blobCid inList orphans }
    ActivityStreamFetchFailures.deleteWhere { ActivityStreamFetchFailures.blobCid inList orphans }
}

// Records are untrusted, so text is clamped to the lexicon maxLength before it's stored.
private const val MAX_CHANNEL_LENGTH = 32
