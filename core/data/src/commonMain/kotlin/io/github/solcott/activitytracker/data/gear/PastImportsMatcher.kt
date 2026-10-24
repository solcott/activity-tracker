package io.github.solcott.activitytracker.data.gear

import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.data.activity.ActivityDevicesStore
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.ActivityPlaces
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.GearMounts
import io.github.solcott.activitytracker.lexicon.app.SensorKey

/** An earlier import to add [gear] to: the linked item, or the top-level item it was mounted under at [activity]'s start. */
data class PastImport(val activity: AtUri, val gear: AtUri)

/**
 * Applies a new sensor link to the athlete's earlier imports (gear/gear-sensors): every activity whose stored devices name the sensor and
 * whose gear lists no item of the type it stands for then. Runs only when the athlete asks, reading their devices records from `self` and
 * each activity from where it's stored, so it never waits for the AppView.
 */
@Inject
class PastImportsMatcher(
    private val devices: ActivityDevicesStore,
    private val places: ActivityPlaces,
    private val gear: GearRepository,
    private val feed: FeedRepository,
    private val manager: ActivityManager,
) {
    /**
     * The earlier imports naming [key] that [item] (or what it was mounted under at each one's start) would be added to, oldest record
     * first, leaving out [except] (the import just saved) and activities that already list an item of that type. Throws when the devices
     * can't be listed.
     */
    suspend fun find(key: SensorKey, item: AtUri, except: Set<AtUri> = emptySet()): List<PastImport> {
        val items = gear.items()
        val records = devices.list().filter { record -> record.devices.any { SensorKey.of(it)?.matches(key) == true } }
        return records
            .map { it.activity }
            .distinct()
            .filter { it !in except }
            .mapNotNull { activity -> places.read(activity).full?.let { target(activity, it, item, items) } }
    }

    /**
     * Adds each import's gear, one activity at a time, reporting how many are done to [onProgress]. Each is read afresh first and skipped
     * if it now lists an item of that type, so a pass that was interrupted can simply be run again. Returns how many were changed; throws
     * on the first that can't be saved, leaving those before it changed.
     */
    suspend fun apply(imports: List<PastImport>, onProgress: (Int) -> Unit = {}): Int {
        val items = gear.items()
        var changed = 0
        imports.forEachIndexed { index, import ->
            val full = places.read(import.activity).full
            val target = full?.let { target(import.activity, it, import.gear, items, lifted = true) }
            if (full != null && target != null) {
                val view = feed.activity(import.activity)
                val streams = feed.streams(import.activity)
                val visibility = view.visibilityOrPublic
                val choice = VisibilityChoice(visibility, DetailCategory.currentTiers(visibility, view, streams))
                manager.setVisibility(view, choice, gear = (full.gear.orEmpty() + target.gear).take(ActivityRecord.MAX_GEAR))
                changed++
            }
            onProgress(index + 1)
        }
        return changed
    }

    /**
     * What [activity] (stored as [record]) would gain: [item] lifted through its mounts at the start (unless [lifted] already), when it's
     * in use and the activity lists nothing of its type and has room.
     */
    private fun target(activity: AtUri, record: ActivityRecord, item: AtUri, items: List<GearItem>, lifted: Boolean = false): PastImport? {
        val did = gear.did
        val all = items.associateBy { it.uri(did) }
        val root = if (lifted) item else GearMounts.rootAt(item, record.startedAt) { all[it]?.record?.mounts }
        val type = all[root]?.takeIf { it.record.retiredAt == null }?.record?.type
        val listed = record.gear.orEmpty()
        val hasRoom = listed.size < ActivityRecord.MAX_GEAR && listed.none { all[it]?.record?.type == type }
        return if (type != null && hasRoom) PastImport(activity, root) else null
    }
}
