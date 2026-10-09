package io.github.solcott.activitytracker.data.activity

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamsRecord
import io.github.solcott.activitytracker.lexicon.app.ZoneSnapshot
import io.github.solcott.activitytracker.lexicon.atproto.GetRecord
import kotlin.time.Clock

/** An activity's streams and who may see them, ready to write. */
data class ActivityStreams(
    val data: ActivityStreamData,
    /** A [io.github.solcott.activitytracker.lexicon.app.Visibility] value. */
    val visibility: String,
    val heartRateZones: ZoneSnapshot? = null,
    val powerZones: ZoneSnapshot? = null,
)

/**
 * Where the signed-in athlete's activity streams are written. Reads always go through the AppView (`feed.getActivityStreams`), so only this
 * binding changes when streams move.
 *
 * TODO(spaces): streams are public records on the athlete's PDS for now. Before going live, bind a store that writes them to a private
 *   atproto Space instead (see "Move activity streams to a Space" in BACKLOG.md).
 */
interface ActivityStreamsStore {
    /** Queues [streams] for the activity at [activity]; callers queue the activity first. Doesn't drain the outbox. */
    suspend fun put(activity: AtUri, streams: ActivityStreams)

    /** Changes who may see [activity]'s streams. Doesn't drain the outbox. */
    suspend fun setVisibility(activity: AtUri, visibility: String)

    /** Queues removing [activity]'s streams, if it has any. Doesn't drain the outbox. */
    suspend fun delete(activity: AtUri)
}

/** Writes streams as `feed.activityStreams` records under the activity's rkey, with the data as a JSON blob. */
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
@Inject
class PdsActivityStreamsStore(private val writer: OutboxWriter, private val clients: SessionClients, private val clock: Clock) :
    ActivityStreamsStore {
    override suspend fun put(activity: AtUri, streams: ActivityStreams) {
        val bytes = encode(streams.data)
        val record =
            ActivityStreamsRecord(
                activity = activity,
                visibility = streams.visibility,
                channels = streams.data.channels,
                createdAt = clock.now(),
                heartRateZones = streams.heartRateZones,
                powerZones = streams.powerZones,
            )
        writer.put(
            Nsid.FEED_ACTIVITY_STREAMS,
            requireNotNull(activity.rkey),
            ActivityStreamsRecord.serializer(),
            record,
            PendingBlob(bytes, ActivityStreamsRecord.MIME_TYPE, field = "data"),
        )
    }

    override suspend fun setVisibility(activity: AtUri, visibility: String) {
        val current =
            clients.pds
                .query(GetRecord.method, GetRecord.Params(clients.did.value, Nsid.FEED_ACTIVITY_STREAMS, requireNotNull(activity.rkey)))
                .value
        val record = AtJson.decodeFromJsonElement(ActivityStreamsRecord.serializer(), current)
        writer.put(
            Nsid.FEED_ACTIVITY_STREAMS,
            requireNotNull(activity.rkey),
            ActivityStreamsRecord.serializer(),
            record.copy(visibility = visibility),
        )
    }

    // Deleting a record that isn't there is a no-op on the PDS, so an activity without streams needs no check first.
    override suspend fun delete(activity: AtUri) {
        writer.delete(Nsid.FEED_ACTIVITY_STREAMS, requireNotNull(activity.rkey))
    }

    companion object {
        /** Leaves room under the lexicon's limit. */
        private const val MAX_BYTES = ActivityStreamsRecord.MAX_DATA_BYTES * 9 / 10

        /** [data] as JSON, every other sample dropped until it fits the blob limit (only multi-day activities get there). */
        fun encode(data: ActivityStreamData): ByteArray {
            var current = data
            while (true) {
                val bytes = AtJson.encodeToString(ActivityStreamData.serializer(), current).encodeToByteArray()
                if (bytes.size <= MAX_BYTES || current.time.size < 2) return bytes
                current = current.halved()
            }
        }

        private fun ActivityStreamData.halved(): ActivityStreamData {
            fun <T> List<T>?.half() = this?.filterIndexed { i, _ -> i % 2 == 0 }
            return copy(
                time = time.filterIndexed { i, _ -> i % 2 == 0 },
                distance = distance.half(),
                altitude = altitude.half(),
                speed = speed.half(),
                heartRate = heartRate.half(),
                power = power.half(),
                cadence = cadence.half(),
                temperature = temperature.half(),
                depth = depth.half(),
            )
        }
    }
}
