package io.github.solcott.activitytracker.data.activity

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.activityfile.SourceFile
import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivitySourceRecord
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.time.Clock

/**
 * Where the signed-in athlete's imported files are kept (`feed.activitySource`). A file can hold device serial numbers and exact positions,
 * so it's stored only in their [AthleteSpace.SELF] space, whatever its activity's visibility, and never moves; only the athlete reads it.
 */
interface ActivitySourceStore {
    /** Queues [source] as the original file of the activity at [activity]; callers queue the activity first. Doesn't drain the outbox. */
    suspend fun put(activity: AtUri, source: DraftSource)

    /** [activity]'s original file, stored or still queued, or null for none. Reads the `self` space when nothing is queued. */
    suspend fun info(activity: AtUri): SourceInfo?

    /** [activity]'s original file, byte for byte as imported. Throws when it can't be read. */
    suspend fun read(activity: AtUri): ByteArray

    /** Queues deleting [activity]'s original file, if any. */
    suspend fun delete(activity: AtUri)
}

/** An imported file kept with an activity ([ActivitySourceStore.info]): its name as picked, its format token, and whether it's unsent. */
data class SourceInfo(val fileName: String, val format: String, val queued: Boolean)

/** The file a draft was read from, and for a logbook which dive in it ([ActivitySourceRecord.entry]). */
data class DraftSource(val file: SourceFile, val entry: Int? = null)

/** Writes originals as `feed.activitySource` records under the activity's rkey, the gzipped file a blob, in the athlete's `self` space. */
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
@Inject
class PdsActivitySourceStore(
    private val db: AppDatabase,
    private val writer: OutboxWriter,
    private val clients: SessionClients,
    private val clock: Clock,
) : ActivitySourceStore {
    private val self
        get() = clients.tiers.uri(AthleteSpace.SELF)

    // Every draft from one file shares its key, so the outbox stores and uploads a logbook once however many dives it gives.
    override suspend fun put(activity: AtUri, source: DraftSource) {
        val file = source.file
        writer.put(
            Nsid.FEED_ACTIVITY_SOURCE,
            requireNotNull(activity.rkey),
            ActivitySourceRecord.serializer(),
            ActivitySourceRecord(
                activity = activity,
                format = file.format.token,
                fileName = file.fileName,
                size = file.size,
                compressed = file.compressed,
                createdAt = clock.now(),
                entry = source.entry,
            ),
            PendingBlob(file.stored, ActivitySourceRecord.MIME_TYPE, ActivitySourceRecord.FILE_FIELD, key = file.key),
            space = self,
        )
    }

    // The outbox's last write of it says what the PDS will hold once it's sent; with none queued, the PDS holds it now.
    override suspend fun info(activity: AtUri): SourceInfo? {
        val rkey = requireNotNull(activity.rkey)
        val queued = db.outbox().pending(clients.did.value).lastOrNull { it.collection == Nsid.FEED_ACTIVITY_SOURCE && it.rkey == rkey }
        return when {
            queued == null -> stored(rkey)?.info(queued = false)
            queued.operation != OutboxProcessor.OP_PUT -> null
            else -> AtJson.decodeFromString(ActivitySourceRecord.serializer(), requireNotNull(queued.recordJson)).info(queued = true)
        }
    }

    override suspend fun read(activity: AtUri): ByteArray {
        val record = checkNotNull(stored(requireNotNull(activity.rkey))) { "$activity has no original file" }
        val cid = Cid(checkNotNull(record.file?.ref?.link) { "$activity's original file names no blob" })
        return when (val blob = clients.spaces.getBlob(self, clients.did, cid)) {
            is SpaceRead.Found -> SourceFile.original(blob.value.bytes, record.compressed)
            SpaceRead.NotFound,
            SpaceRead.NoAccess -> error("$activity's original file can't be read: $blob")
        }
    }

    // A session that can't write originals never wrote one (publishing refuses it), and the PDS would refuse the delete at the head of the
    // outbox.
    override suspend fun delete(activity: AtUri) {
        if (!clients.canWriteToSpaces(Nsid.FEED_ACTIVITY_SOURCE)) return
        writer.delete(Nsid.FEED_ACTIVITY_SOURCE, requireNotNull(activity.rkey), self)
    }

    private suspend fun stored(rkey: String): ActivitySourceRecord? =
        clients.readOwn(Nsid.FEED_ACTIVITY_SOURCE, rkey, self)?.let { AtJson.decodeFromJsonElement(ActivitySourceRecord.serializer(), it) }

    private fun ActivitySourceRecord.info(queued: Boolean) = SourceInfo(fileName, format, queued)
}

/**
 * Refuses a session that can't write originals to the `self` space, before anything is queued, so an import is never published without its
 * file, nor its file queued for the PDS to refuse at the head of the outbox.
 */
internal fun SessionClients.requireSourceSpace() = requireSpacesFor(Visibility.SELF, Nsid.FEED_ACTIVITY_SOURCE)
