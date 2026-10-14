package io.github.solcott.activitytracker.data.activity

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.AthleteSpaces
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamsRecord
import io.github.solcott.activitytracker.lexicon.app.ZoneSnapshot
import kotlin.time.Clock
import kotlinx.serialization.json.jsonObject

/** An activity's streams and who may see them, ready to write. */
data class ActivityStreams(
    val data: ActivityStreamData,
    /** A [io.github.solcott.activitytracker.lexicon.app.Visibility] value. */
    val visibility: String,
    val heartRateZones: ZoneSnapshot? = null,
    val powerZones: ZoneSnapshot? = null,
)

/**
 * Where the signed-in athlete's activity streams are written. Reads always go through the AppView (`feed.getActivityStreams`). Like an
 * activity, a streams record's visibility is where it's stored: public in the repo, the others in the athlete's tier space for it.
 */
interface ActivityStreamsStore {
    /**
     * Queues [streams] for the activity at [activity], at the place for their visibility; callers queue the activity first. Doesn't drain
     * the outbox.
     */
    suspend fun put(activity: AtUri, streams: ActivityStreams)

    /**
     * Moves [activity]'s streams from [from]'s place to [to]'s, now rather than through the outbox, so a failure shows: the new place is
     * written before the old one is deleted. Nothing happens if it has no streams.
     */
    suspend fun move(activity: AtUri, from: String, to: String)

    /** Queues removing [activity]'s streams from wherever they are, if it has any. Doesn't drain the outbox. */
    suspend fun delete(activity: AtUri)
}

/**
 * Writes streams as `feed.activityStreams` records under the activity's rkey, with the data as a JSON blob, in the athlete's repo or tier
 * space for their visibility. The record keeps its `visibility` field too, which repo records from before spaces rely on.
 */
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
            space = clients.placeOf(streams.visibility),
        )
    }

    // The blob moves with the record: the new place takes it over before the old one lets it go.
    override suspend fun move(activity: AtUri, from: String, to: String) {
        clients.moveOwn(Nsid.FEED_ACTIVITY_STREAMS, requireNotNull(activity.rkey), clients.placeOf(from), clients.placeOf(to)) { json ->
            val record = AtJson.decodeFromJsonElement(ActivityStreamsRecord.serializer(), json).copy(visibility = to)
            AtJson.encodeToJsonElement(ActivityStreamsRecord.serializer(), record).jsonObject
        }
    }

    // Deleting a record (or from a space) that isn't there is a no-op, so every place can be cleared without checking where they are.
    // A session without the space: scope never wrote to a space.
    override suspend fun delete(activity: AtUri) {
        val rkey = requireNotNull(activity.rkey)
        writer.delete(Nsid.FEED_ACTIVITY_STREAMS, rkey)
        if (clients.canUseSpaces) AthleteSpaces.TIERS.forEach { writer.delete(Nsid.FEED_ACTIVITY_STREAMS, rkey, clients.tiers.uri(it)) }
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
