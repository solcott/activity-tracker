package io.github.solcott.activitytracker.data.gear

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ActivityDevice
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.GearAutoAssignRecord
import io.github.solcott.activitytracker.lexicon.app.GearMounts
import io.github.solcott.activitytracker.lexicon.app.SensorKey
import kotlin.time.Instant

/**
 * The gear an imported activity starts with, and the items its sensors pointed at that clash: two or more of one gear type ([conflicts], by
 * gear type, in the order they matched).
 */
data class ResolvedGear(val gear: List<AtUri>, val conflicts: Map<String, List<AtUri>> = emptyMap())

/** A device a file names that the athlete hasn't linked or ignored, with its [key]. */
data class UnlinkedDevice(val key: SensorKey, val device: ActivityDevice)

/**
 * Fills in an imported activity's gear from the athlete's `gear.autoAssign` (gear/gear-sensors): per gear type, the items its linked
 * sensors and recording device stand for, else the item its activity profile stands for, else the defaults for its type. A linked item
 * stands for the top-level item it was mounted under at the activity's start ([GearMounts.rootAt]). Retired and deleted items are skipped,
 * and collections never apply. Runs on the client only, from data already read.
 */
object GearAutoAssign {
    /**
     * The gear an activity of [type] started at [startedAt] begins with, whose file names [devices] and was recorded under [profile], from
     * [items] (the athlete's gear) under [record]. [did] is the athlete's.
     */
    @Suppress("LongParameterList") // Everything resolving reads, passed in so it stays pure.
    fun resolve(
        type: ActivityType,
        startedAt: Instant,
        devices: List<ActivityDevice>,
        profile: String?,
        items: List<GearItem>,
        record: GearAutoAssignRecord,
        did: Did,
    ): ResolvedGear {
        val all = items.associateBy { it.uri(did) }
        val active = all.filterValues { it.record.retiredAt == null }
        // A linked item mounted on a retired bike still stands for that bike only while it's in use.
        fun lifted(linked: AtUri): GearItem? =
            active[linked]?.let { active[GearMounts.rootAt(it.uri(did), startedAt) { uri -> all[uri]?.record?.mounts }] }

        val sensed =
            devices
                .mapNotNull { device -> SensorKey.of(device)?.let { key -> itemFor(key, record) } }
                .mapNotNull(::lifted)
                .distinctBy { it.rkey }
        val bySensors = sensed.groupBy { it.record.type }
        val profiled =
            profile
                ?.let { name -> record.profiles.orEmpty().firstOrNull { it.name == name } }
                ?.let { lifted(it.gear) }
                ?.takeIf { it.record.type !in bySensors }
        val filled = bySensors.keys + listOfNotNull(profiled?.record?.type)
        val defaults = GearDefaults.forType(record, type, items, did).mapNotNull { active[it] }.filter { it.record.type !in filled }
        val gear = (sensed + listOfNotNull(profiled) + defaults).distinctBy { it.rkey }.take(ActivityRecord.MAX_GEAR).map { it.uri(did) }
        val conflicts = bySensors.filterValues { it.size > 1 }.mapValues { (_, clash) -> clash.map { it.uri(did) } }
        return ResolvedGear(gear, conflicts)
    }

    /** The item [key] is linked to under [record], or null. */
    fun itemFor(key: SensorKey, record: GearAutoAssignRecord): AtUri? = record.sensors.orEmpty().firstOrNull { it.key.matches(key) }?.gear

    /** Whether [key] is linked or ignored under [record]. */
    fun isKnown(key: SensorKey, record: GearAutoAssignRecord): Boolean =
        itemFor(key, record) != null || record.ignored.orEmpty().any { it.matches(key) }

    /** Each of [devices] with a key that's neither linked nor ignored under [record], in order, once per key. */
    fun unlinked(devices: List<ActivityDevice>, record: GearAutoAssignRecord): List<UnlinkedDevice> =
        devices
            .mapNotNull { device -> SensorKey.of(device)?.let { UnlinkedDevice(it, device) } }
            .filterNot { isKnown(it.key, record) }
            .distinctBy { it.key.normalized() }
}
