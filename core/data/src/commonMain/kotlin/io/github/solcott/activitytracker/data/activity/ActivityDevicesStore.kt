package io.github.solcott.activitytracker.data.activity

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.activityfile.ImportedDevices
import io.github.solcott.activitytracker.activityfile.toActivityDevice
import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityDevice
import io.github.solcott.activitytracker.lexicon.app.ActivityDevicesRecord
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import kotlin.time.Clock

/**
 * Where the signed-in athlete's imported activities' devices are kept (`feed.activityDevices`). They hold serial numbers, so they're stored
 * only in the athlete's [AthleteSpace.SELF] space, whatever the activity's visibility, and never move; only the athlete reads them.
 *
 * Unlike an original file, devices are optional: a session that can't write them to `self` (signed in before they were kept) skips them
 * rather than refusing the import.
 */
interface ActivityDevicesStore {
    /** Queues [devices] as the activity at [activity]'s; callers queue the activity first. Doesn't drain the outbox. */
    suspend fun put(activity: AtUri, devices: ImportedDevices)

    /** Whether [activity] has devices, stored or still queued. Reads the `self` space when nothing is queued. */
    suspend fun has(activity: AtUri): Boolean

    /** Queues deleting [activity]'s devices, if any. */
    suspend fun delete(activity: AtUri)

    /**
     * Every devices record the athlete has stored, read from the `self` space a page at a time; records that don't decode are left out.
     * Empty for a session that can't use them. Throws when the space can't be read, so a partial list is never taken as complete.
     */
    suspend fun list(): List<ActivityDevicesRecord>
}

/** Writes devices as `feed.activityDevices` records under the activity's rkey in the athlete's `self` space. */
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
@Inject
class PdsActivityDevicesStore(
    private val db: AppDatabase,
    private val writer: OutboxWriter,
    private val clients: SessionClients,
    private val clock: Clock,
) : ActivityDevicesStore {
    private val self
        get() = clients.tiers.uri(AthleteSpace.SELF)

    private val canWrite
        get() = clients.canWriteToSpaces(Nsid.FEED_ACTIVITY_DEVICES)

    override suspend fun put(activity: AtUri, devices: ImportedDevices) {
        if (!canWrite || devices.devices.isEmpty()) return
        writer.put(
            Nsid.FEED_ACTIVITY_DEVICES,
            requireNotNull(activity.rkey),
            ActivityDevicesRecord.serializer(),
            ActivityDevicesRecord(
                activity = activity,
                devices = devices.devices.take(ActivityDevicesRecord.MAX_DEVICES).map { it.toActivityDevice() },
                createdAt = clock.now(),
                activityProfile = devices.activityProfile?.take(ActivityDevice.MAX_NAME),
            ),
            space = self,
        )
    }

    // The outbox's last write of it says what the PDS will hold once it's sent; with none queued, the PDS holds it now.
    override suspend fun has(activity: AtUri): Boolean {
        val rkey = requireNotNull(activity.rkey)
        val queued = db.outbox().pending(clients.did.value).lastOrNull { it.collection == Nsid.FEED_ACTIVITY_DEVICES && it.rkey == rkey }
        return when {
            queued != null -> queued.operation == OutboxProcessor.OP_PUT
            !canWrite -> false
            else -> clients.readOwn(Nsid.FEED_ACTIVITY_DEVICES, rkey, self) != null
        }
    }

    override suspend fun list(): List<ActivityDevicesRecord> {
        if (!canWrite) return emptyList()
        return when (val read = clients.spaces.listAllRecords(self, clients.did, Nsid.FEED_ACTIVITY_DEVICES, LIST_PAGE)) {
            is SpaceRead.Found ->
                read.value.mapNotNull { record ->
                    record.value?.let { runCatching { AtJson.decodeFromJsonElement(ActivityDevicesRecord.serializer(), it) }.getOrNull() }
                }
            SpaceRead.NotFound -> emptyList()
            SpaceRead.NoAccess -> error("The self space refused its own athlete")
        }
    }

    // A session that can't write devices never wrote any, and the PDS would refuse the delete at the head of the outbox.
    override suspend fun delete(activity: AtUri) {
        if (!canWrite) return
        writer.delete(Nsid.FEED_ACTIVITY_DEVICES, requireNotNull(activity.rkey), self)
    }
}

private const val LIST_PAGE = 100
