package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.ProfileDetailRules
import io.github.solcott.activitytracker.appview.db.ActivityStreamData as StreamDataCache
import io.github.solcott.activitytracker.appview.db.ActivityStreams
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamsRecord
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.ZoneSnapshot
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.v1.core.notInSubQuery
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert

// Indexing of `feed.activityStreams` records, which the Indexer hands off here.

/**
 * Streams must sit under their own activity's rkey in the same repo, and name their data blob. An unknown visibility is stored as is; views
 * treat it as [Visibility.SELF].
 */
internal fun JdbcTransaction.upsertStreams(record: ActivityStreamsRecord, author: Did, uri: AtUri, cid: String, now: Long) {
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
        it[visibility] = record.visibility.take(MAX_VISIBILITY_LENGTH)
        it[channels] =
            AtJson.encodeToString(
                ListSerializer(String.serializer()),
                record.channels.take(ActivityStreamsRecord.MAX_CHANNELS).map { c -> c.take(MAX_CHANNEL_LENGTH) },
            )
        it[heartRateZones] = boundedZones(record.heartRateZones)
        it[powerZones] = boundedZones(record.powerZones)
        it[blobCid] = blob
        it[indexedAt] = now
    }
    dropOrphanedStreamData()
}

/** Cached stream data no streams record names any more (deleted, or replaced by a new blob). */
internal fun JdbcTransaction.dropOrphanedStreamData() {
    StreamDataCache.deleteWhere { StreamDataCache.blobCid notInSubQuery ActivityStreams.select(ActivityStreams.blobCid) }
}

/** Zones as JSON, or null when they break the lexicon's limits. */
private fun boundedZones(zones: ZoneSnapshot?): String? =
    zones
        ?.takeIf { z ->
            z.model.length <= MAX_MODEL_LENGTH && z.lows.size in 1..ZoneSnapshot.MAX_ZONES && z.lows.all { it in 0..MAX_ZONE_VALUE }
        }
        ?.let { AtJson.encodeToString(ZoneSnapshot.serializer(), it) }

// Records are untrusted, so text is clamped to the lexicon maxLength before it's stored.
private const val MAX_MODEL_LENGTH = 64
private const val MAX_VISIBILITY_LENGTH = 16
private const val MAX_CHANNEL_LENGTH = 32

/** Above any heart rate or power an athlete could hold. */
private const val MAX_ZONE_VALUE = 5_000
