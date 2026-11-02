package io.github.solcott.activitytracker.data.segments

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.PendingBlob
import io.github.solcott.activitytracker.data.activity.listOwn
import io.github.solcott.activitytracker.data.db.AppDatabase
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
    /**
     * Queues [data], trimmed by the zones saved at [zonesRevision], as the segment track of the activity at [activity], [excluded] from
     * leaderboards or not and [groupExcluded] (left its group, feed/group-activities) or not. Doesn't drain.
     */
    suspend fun put(
        activity: AtUri,
        data: SegmentTrackData,
        zonesRevision: String?,
        excluded: Boolean = false,
        groupExcluded: Boolean = false,
    )

    /** Queues removing [activity]'s segment track, if it has one. Doesn't drain. */
    suspend fun delete(activity: AtUri)

    /** The segment tracks stored now, by rkey. Throws when `self` can't be read. */
    suspend fun list(): Map<String, StoredSegmentTrack>

    /**
     * Whether [activity] is excluded from leaderboards, as its stored segment track says (segments/segment-leaderboards); null when it has
     * none, it can't be read now, or a write of it is still queued, so the choice can't be shown.
     */
    suspend fun excluded(activity: AtUri): Boolean?

    /**
     * Queues rewriting [activity]'s stored segment track [excluded] from leaderboards or not, with the same points (its blob is already on
     * the PDS). Throws when [excluded] would: there's no stored track to mark.
     */
    suspend fun setExcluded(activity: AtUri, excluded: Boolean)

    /**
     * Whether [activity] left its group (feed/group-activities), as its stored segment track says; null when it has none, it can't be read
     * now, or a write of it is still queued, so the choice can't be offered.
     */
    suspend fun groupExcluded(activity: AtUri): Boolean?

    /**
     * Queues rewriting [activity]'s stored segment track as having left its group or not, keeping its points and leaderboard exclusion.
     * Throws when [groupExcluded] would be null: there's no stored track to mark.
     */
    suspend fun setGroupExcluded(activity: AtUri, left: Boolean)
}

/**
 * A stored segment track as [SegmentTrackStore.list] gives it: the zones revision it was trimmed by, its leaderboard exclusion and whether
 * its activity left its group.
 */
data class StoredSegmentTrack(val zonesRevision: String?, val excluded: Boolean = false, val groupExcluded: Boolean = false)

/** Writes segment tracks as `feed.segmentTrack` records with a [SegmentTrackData] JSON blob, in the athlete's `self` space. */
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
@Inject
class PdsSegmentTrackStore(
    private val writer: OutboxWriter,
    private val clients: SessionClients,
    private val clock: Clock,
    private val db: AppDatabase,
) : SegmentTrackStore {
    private val self
        get() = clients.tiers.uri(AthleteSpace.SELF)

    override suspend fun put(activity: AtUri, data: SegmentTrackData, zonesRevision: String?, excluded: Boolean, groupExcluded: Boolean) {
        writer.put(
            Nsid.FEED_SEGMENT_TRACK,
            requireNotNull(activity.rkey),
            SegmentTrackRecord.serializer(),
            SegmentTrackRecord(
                activity = activity,
                createdAt = clock.now(),
                zonesRevision = zonesRevision,
                excludeFromLeaderboards = excluded.takeIf { it },
                groupExcluded = groupExcluded.takeIf { it },
            ),
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

    override suspend fun list(): Map<String, StoredSegmentTrack> =
        clients.listOwn(Nsid.FEED_SEGMENT_TRACK, self).mapValues { (_, value) ->
            runCatching { AtJson.decodeFromJsonElement(SegmentTrackRecord.serializer(), value) }
                .getOrNull()
                ?.let { StoredSegmentTrack(it.zonesRevision, it.excludeFromLeaderboards == true, it.groupExcluded == true) }
                ?: StoredSegmentTrack(null)
        }

    override suspend fun excluded(activity: AtUri): Boolean? = runSuspendCatching {
        stored(activity)
    }
        .getOrNull()
        ?.let { it.excludeFromLeaderboards == true }

    override suspend fun setExcluded(activity: AtUri, excluded: Boolean) {
        val record = checkNotNull(stored(activity)) { "No stored segment track to mark" }
        writer.put(
            Nsid.FEED_SEGMENT_TRACK,
            requireNotNull(activity.rkey),
            SegmentTrackRecord.serializer(),
            record.copy(excludeFromLeaderboards = excluded.takeIf { it }),
            space = self,
        )
    }

    override suspend fun groupExcluded(activity: AtUri): Boolean? = runSuspendCatching {
        latest(activity)
    }
        .getOrNull()
        ?.let { it.groupExcluded == true }

    override suspend fun setGroupExcluded(activity: AtUri, left: Boolean) {
        val record = checkNotNull(latest(activity)) { "No stored segment track to mark" }
        writer.put(
            Nsid.FEED_SEGMENT_TRACK,
            requireNotNull(activity.rkey),
            SegmentTrackRecord.serializer(),
            record.copy(groupExcluded = left.takeIf { it }),
            space = self,
        )
    }

    /**
     * [activity]'s segment track as it will be once the outbox drains: the last queued rewrite of a stored one (a mark, which uploads
     * nothing), else as stored; null when there's none, or a write that uploads its points is still queued.
     */
    private suspend fun latest(activity: AtUri): SegmentTrackRecord? {
        val rkey = requireNotNull(activity.rkey)
        val queued = db.outbox().pending(clients.did.value, rkey).filter { it.collection == Nsid.FEED_SEGMENT_TRACK }
        val last = queued.lastOrNull() ?: return stored(activity)
        val marksOnly = last.operation == OutboxProcessor.OP_PUT && queued.none { it.blobField != null || it.blobKey != null }
        return last.recordJson
            ?.takeIf { marksOnly }
            ?.let { runCatching { AtJson.decodeFromString(SegmentTrackRecord.serializer(), it) }.getOrNull() }
            ?.takeIf { it.data != null }
    }

    /** [activity]'s segment track as stored, with its blob; null when there's none, or a write of it is still queued. */
    private suspend fun stored(activity: AtUri): SegmentTrackRecord? {
        val rkey = requireNotNull(activity.rkey)
        if (db.outbox().pending(clients.did.value, rkey).any { it.collection == Nsid.FEED_SEGMENT_TRACK }) return null
        val read = clients.spaces.getRecord(self, clients.did, Nsid.FEED_SEGMENT_TRACK, rkey)
        return (read as? SpaceRead.Found)
            ?.let { runCatching { AtJson.decodeFromJsonElement(SegmentTrackRecord.serializer(), it.value.value) }.getOrNull() }
            ?.takeIf { it.data != null }
    }
}
