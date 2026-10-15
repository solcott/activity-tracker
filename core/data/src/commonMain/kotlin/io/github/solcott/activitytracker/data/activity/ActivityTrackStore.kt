package io.github.solcott.activitytracker.data.activity

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityTrackRecord
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.Gpx
import kotlin.time.Clock

/**
 * Where the signed-in athlete's full-resolution tracks are written. A track holds exact positions, so it's stored only in their
 * [AthleteSpace.SELF] space, whatever its activity's visibility, and never moves; only the athlete reads it.
 */
interface ActivityTrackStore {
    /** Queues [gpx] as the track of the activity at [activity]; callers queue the activity first. Doesn't drain the outbox. */
    suspend fun put(activity: AtUri, gpx: ByteArray)

    /** Whether [activity] has a track, stored or still queued. Reads the `self` space. */
    suspend fun has(activity: AtUri): Boolean

    /** Queues removing [activity]'s track, if it has one. Doesn't drain the outbox. */
    suspend fun delete(activity: AtUri)
}

/** Writes tracks as `feed.activityTrack` records under the activity's rkey, with the GPX as a blob, in the athlete's `self` space. */
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
@Inject
class PdsActivityTrackStore(
    private val db: AppDatabase,
    private val writer: OutboxWriter,
    private val clients: SessionClients,
    private val clock: Clock,
) : ActivityTrackStore {
    private val self
        get() = clients.tiers.uri(AthleteSpace.SELF)

    override suspend fun put(activity: AtUri, gpx: ByteArray) {
        writer.put(
            Nsid.FEED_ACTIVITY_TRACK,
            requireNotNull(activity.rkey),
            ActivityTrackRecord.serializer(),
            ActivityTrackRecord(activity = activity, createdAt = clock.now()),
            PendingBlob(gpx, Gpx.MIME_TYPE, field = ActivityTrackRecord.TRACK_FIELD),
            space = self,
        )
    }

    // The outbox's last write of it says what the PDS will hold once it's sent; with none queued, the PDS holds it now.
    override suspend fun has(activity: AtUri): Boolean {
        val rkey = requireNotNull(activity.rkey)
        val queued = db.outbox().pending(clients.did.value).lastOrNull { it.collection == Nsid.FEED_ACTIVITY_TRACK && it.rkey == rkey }
        return if (queued != null) queued.operation == OutboxProcessor.OP_PUT
        else clients.readOwn(Nsid.FEED_ACTIVITY_TRACK, rkey, self) != null
    }

    // A session that can't write tracks never wrote one (publishing refuses it), and the PDS would refuse the delete at the head of the
    // outbox.
    override suspend fun delete(activity: AtUri) {
        if (!clients.canWriteToSpaces(Nsid.FEED_ACTIVITY_TRACK)) return
        writer.delete(Nsid.FEED_ACTIVITY_TRACK, requireNotNull(activity.rkey), self)
    }
}

/**
 * Refuses a session that can't write tracks to the `self` space, before anything is queued, so a recording is never published without its
 * track, nor its track queued for the PDS to refuse at the head of the outbox.
 */
internal fun SessionClients.requireTrackSpace() = requireSpacesFor(Visibility.SELF, Nsid.FEED_ACTIVITY_TRACK)
