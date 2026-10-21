package io.github.solcott.activitytracker.data.segments

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.PendingBlob
import io.github.solcott.activitytracker.data.activity.listOwn
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackData
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackRecord
import kotlin.time.Clock

/**
 * Where the signed-in athlete's segment tracks are written: the copies of their tracks trimmed by their privacy zones that the AppView may
 * match segments against. Like full tracks ([ActivityTrackStore][io.github.solcott.activitytracker.data.activity.ActivityTrackStore]) they
 * live only in the athlete's [AthleteSpace.SELF] space under their activity's rkey, whatever its visibility.
 */
interface SegmentTrackStore {
    /** Queues [data], trimmed by the zones saved at [zonesRevision], as the segment track of the activity at [activity]. Doesn't drain. */
    suspend fun put(activity: AtUri, data: SegmentTrackData, zonesRevision: String?)

    /** Queues removing [activity]'s segment track, if it has one. Doesn't drain. */
    suspend fun delete(activity: AtUri)

    /** The segment tracks stored now, by rkey, each with the zones revision it was trimmed by. Throws when `self` can't be read. */
    suspend fun list(): Map<String, String?>
}

/** Writes segment tracks as `feed.segmentTrack` records with a [SegmentTrackData] JSON blob, in the athlete's `self` space. */
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
@Inject
class PdsSegmentTrackStore(private val writer: OutboxWriter, private val clients: SessionClients, private val clock: Clock) :
    SegmentTrackStore {
    private val self
        get() = clients.tiers.uri(AthleteSpace.SELF)

    override suspend fun put(activity: AtUri, data: SegmentTrackData, zonesRevision: String?) {
        writer.put(
            Nsid.FEED_SEGMENT_TRACK,
            requireNotNull(activity.rkey),
            SegmentTrackRecord.serializer(),
            SegmentTrackRecord(activity = activity, createdAt = clock.now(), zonesRevision = zonesRevision),
            PendingBlob(
                AtJson.encodeToString(SegmentTrackData.serializer(), data).encodeToByteArray(),
                SegmentTrackRecord.MIME_TYPE,
                field = SegmentTrackRecord.DATA_FIELD,
            ),
            space = self,
        )
    }

    // A session that can't write segment tracks never wrote one, and the PDS would refuse the delete at the head of the outbox.
    override suspend fun delete(activity: AtUri) {
        if (!clients.canWriteToSpaces(Nsid.FEED_SEGMENT_TRACK)) return
        writer.delete(Nsid.FEED_SEGMENT_TRACK, requireNotNull(activity.rkey), self)
    }

    override suspend fun list(): Map<String, String?> =
        clients.listOwn(Nsid.FEED_SEGMENT_TRACK, self).mapValues { (_, value) ->
            runCatching { AtJson.decodeFromJsonElement(SegmentTrackRecord.serializer(), value).zonesRevision }.getOrNull()
        }
}
