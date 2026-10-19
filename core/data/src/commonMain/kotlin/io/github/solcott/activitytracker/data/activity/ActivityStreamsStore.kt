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
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.TrainingLoad
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.ZoneSnapshot
import io.github.solcott.activitytracker.lexicon.app.mergeStreams
import kotlin.time.Clock

/**
 * An activity's streams and who may see each category of them, ready to write, with its zones (placed apart, by [ActivityZonesStore]). They
 * go in one streams record per tier holding any channel ([parts]).
 */
data class ActivityStreams(
    val data: ActivityStreamData,
    /** The activity's [Visibility]: where the core channels go, unless [core] says otherwise. */
    val activity: String,
    /** Each [DetailCategory]'s visibility, capped at [activity] ([DetailCategory.tiers]); a category left out is at [activity]. */
    val categories: Map<String, String> = emptyMap(),
    val heartRateZones: ZoneSnapshot? = null,
    val powerZones: ZoneSnapshot? = null,
    /** Kept with the zones, as it reveals thresholds too. */
    val trainingLoad: TrainingLoad? = null,
    /**
     * Where the core channels go, when not with the activity: an activity from before categories keeps them in its one streams record,
     * which a merge must not make more visible.
     */
    val core: String = activity,
) {
    val tiers: Map<String, String>
        get() = DetailCategory.tiers(activity, categories)

    /** One [ActivityStreamData] per tier holding any channel, by tier. */
    val parts: Map<String, ActivityStreamData>
        get() =
            if (core == activity) DetailCategory.streamsFor(data, activity, categories)
            else DetailCategory.streamsFor(data, core, DetailCategory.tiers(core, tiers))

    /** Where the zones go, before the training caps them: the narrower of the heart rate and power tiers ([ActivityZonesStore]). */
    val zonesTier: String
        get() = zonesTier(tiers)

    /** These streams as the AppView serves them to their owner once it has indexed what [ActivityStreamsStore.put] wrote for [activity]. */
    fun servedAs(activity: AtUri): GetActivityStreams.Output? {
        val parts = copy(data = PdsActivityStreamsStore.fitted(data)).parts
        val widestFirst = Visibility.all.mapNotNull { tier -> parts[tier]?.let { tier to it } }
        val merged = mergeStreams(widestFirst.map { it.second }) ?: return null
        return GetActivityStreams.Output(
            uri = activity,
            visibility = widestFirst.first().first,
            channels = merged.channels,
            data = merged,
            heartRateZones = heartRateZones,
            powerZones = powerZones,
            trainingLoad = trainingLoad,
            parts = widestFirst.map { (tier, part) -> GetActivityStreams.StreamsPart(tier, part.channels) },
        )
    }
}

/** The narrower of [tiers]' heart rate and power visibilities: what an activity's zones reveal, and so where they go at most. */
internal fun zonesTier(tiers: Map<String, String>): String =
    Visibility.narrower(tiers.getValue(DetailCategory.HEART_RATE), tiers.getValue(DetailCategory.POWER))

/**
 * Where the signed-in athlete's activity streams are written. Reads always go through the AppView (`feed.getActivityStreams`). Like an
 * activity, a streams record's visibility is where it's stored: public in the repo, the others in the athlete's tier space for it, one
 * record per tier holding any of its channels. Their zones are a record of their own, placed by the heart rate and power tiers and never
 * wider than the athlete's training ([ActivityZonesStore]).
 */
interface ActivityStreamsStore {
    /**
     * Queues [streams] for the activity at [activity], one record per tier, narrowest first, and their zones at theirs. Doesn't drain the
     * outbox.
     */
    suspend fun put(activity: AtUri, streams: ActivityStreams)

    /** Queues removing [activity]'s streams, details and zones from wherever they are, if it has any. Doesn't drain the outbox. */
    suspend fun delete(activity: AtUri)
}

/**
 * Writes streams as `feed.activityStreams` records under the activity's rkey, each with its tier's data as a JSON blob, in the athlete's
 * repo or tier space. Where each is stored is its only visibility: the record carries none of its own, and no zones.
 */
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
@Inject
class PdsActivityStreamsStore(
    private val writer: OutboxWriter,
    private val clients: SessionClients,
    private val clock: Clock,
    private val zones: ActivityZonesStore,
) : ActivityStreamsStore {
    // Halved once, before the split, so every tier's record lines up with the same times.
    override suspend fun put(activity: AtUri, streams: ActivityStreams) {
        val parts = streams.copy(data = fitted(streams.data)).parts
        for (tier in Visibility.all.reversed()) {
            val data = parts[tier] ?: continue
            val record = ActivityStreamsRecord(activity = activity, channels = data.channels, createdAt = clock.now())
            writer.put(
                Nsid.FEED_ACTIVITY_STREAMS,
                requireNotNull(activity.rkey),
                ActivityStreamsRecord.serializer(),
                record,
                PendingBlob(encode(data), ActivityStreamsRecord.MIME_TYPE, field = "data"),
                space = clients.placeOf(tier),
            )
        }
        zones.put(activity, streams.zonesTier, streams.heartRateZones, streams.powerZones, streams.trainingLoad)
    }

    // Deleting a record (or from a space) that isn't there is a no-op, so every place can be cleared without checking where they are.
    // A session without the space: scope never wrote to a space.
    override suspend fun delete(activity: AtUri) {
        val rkey = requireNotNull(activity.rkey)
        writer.delete(Nsid.FEED_ACTIVITY_STREAMS, rkey)
        if (clients.canUseSpaces) AthleteSpaces.TIERS.forEach { writer.delete(Nsid.FEED_ACTIVITY_STREAMS, rkey, clients.tiers.uri(it)) }
        // A session that can't write details never wrote any, and the PDS would refuse the delete at the head of the outbox.
        if (clients.canWriteToSpaces(Nsid.FEED_ACTIVITY_DETAILS))
            AthleteSpaces.TIERS.forEach { writer.delete(Nsid.FEED_ACTIVITY_DETAILS, rkey, clients.tiers.uri(it)) }
        zones.delete(activity)
    }

    companion object {
        /** Leaves room under the lexicon's limit. */
        private const val MAX_BYTES = ActivityStreamsRecord.MAX_DATA_BYTES * 9 / 10

        /** [data] as JSON, every other sample dropped until it fits the blob limit (only multi-day activities get there). */
        fun encode(data: ActivityStreamData): ByteArray =
            AtJson.encodeToString(ActivityStreamData.serializer(), fitted(data)).encodeToByteArray()

        /** [data] with every other sample dropped until its JSON fits the blob limit; any part of it then fits too. */
        fun fitted(data: ActivityStreamData): ActivityStreamData {
            var current = data
            while (true) {
                val bytes = AtJson.encodeToString(ActivityStreamData.serializer(), current).encodeToByteArray()
                if (bytes.size <= MAX_BYTES || current.time.size < 2) return current
                current = current.halved()
            }
        }

        private fun ActivityStreamData.halved(): ActivityStreamData {
            fun <T> List<T>.half() = filterIndexed { i, _ -> i % 2 == 0 }
            val halved = copy(time = time.half(), tankPressures = tankPressures?.map { it.copy(pressure = it.pressure.half()) })
            return ActivityStreamData.INT_CHANNELS.fold(halved) { data, channel -> channel.with(data, channel.get(this)?.half()) }
        }
    }
}
