package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.ActivityZones
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityZonesRecord
import io.github.solcott.activitytracker.lexicon.app.ZoneSnapshot
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.upsert

// Indexing of `feed.activityZones` records, which the Indexer hands off here.

/**
 * Zones must sit under their own activity's rkey in the same repo. They're served at [placement]'s visibility: where they're stored is who
 * may see them, which the athlete keeps at the narrower of the charts' and the training's.
 */
@Suppress("LongParameterList") // The record, its raw JSON, and where it's from.
internal fun JdbcTransaction.upsertZones(
    record: ActivityZonesRecord,
    author: Did,
    uri: AtUri,
    cid: String,
    json: JsonObject,
    placement: Placement,
    now: Long,
) {
    val activity = record.activity
    require(activity.authority == author.value && activity.collection == Nsid.FEED_ACTIVITY && activity.rkey == uri.rkey) {
        "zones not under their activity's rkey"
    }
    ActivityZones.upsert {
        it[ActivityZones.uri] = uri.value
        it[ActivityZones.cid] = cid
        it[authorDid] = author.value
        it[activityUri] = activity.value
        it[visibility] = placement.visibility
        it[origin] = placement.origin
        it[heartRateZones] = boundedZones(record.heartRateZones)
        it[powerZones] = boundedZones(record.powerZones)
        it[ActivityZones.record] = AtJson.encodeToString(JsonObject.serializer(), json)
        it[indexedAt] = now
    }
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

/** Above any heart rate or power an athlete could hold. */
private const val MAX_ZONE_VALUE = 5_000
