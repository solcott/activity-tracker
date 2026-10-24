package io.github.solcott.activitytracker.data.gear

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.data.activity.requireSpacesFor
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.outcomeFlow
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GearAutoAssignRecord
import io.github.solcott.activitytracker.lexicon.app.GearProfileLink
import io.github.solcott.activitytracker.lexicon.app.GearSensorLink
import io.github.solcott.activitytracker.lexicon.app.GearSportDefault
import io.github.solcott.activitytracker.lexicon.app.SensorKey
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.dataresult.Outcome
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The gear each activity type starts with (gear/activity-gear): the `gear.autoAssign` record (rkey `self`) in the athlete's `self` space,
 * which the AppView never sees. Read once per session, and empty when it can't be read: defaults only fill in a new activity, so a failed
 * read never stops the athlete saving one. Saves are direct calls, like gear saves, and refuse a session whose grant predates the record
 * with the same "sign in again" error as private gear.
 */
@SingleIn(SessionScope::class)
@Inject
class GearDefaults(private val clients: SessionClients, private val clock: Clock) {
    private val mutex = Mutex()

    /** The record as last read or saved this session; null until it has been read. */
    val current: StateFlow<GearAutoAssignRecord?>
        field = MutableStateFlow<GearAutoAssignRecord?>(null)

    /** Whether this session can save defaults: not one signed in before its `space:` grant listed the record. */
    val canSave: Boolean
        get() = clients.canWriteToSpaces(Nsid.GEAR_AUTO_ASSIGN)

    /** The record, read once per session (again after a failed read); empty when there's none or it can't be read now. */
    suspend fun load(): GearAutoAssignRecord = mutex.withLock { current.value ?: read()?.also { current.value = it } ?: empty() }

    /**
     * The record read afresh, as a stream (`ui/content-loading`): loading, then the record (empty when there's none) or why it couldn't be
     * read. Keeps it as [current].
     */
    fun recordOutcome(): Flow<Outcome<GearAutoAssignRecord>> = outcomeFlow { mutex.withLock { readOrThrow().also { current.value = it } } }

    /** The gear a new activity of [type] starts with, from [items], the athlete's gear ([forType]). */
    suspend fun forType(type: ActivityType, items: List<GearItem>): List<AtUri> = forType(load(), type, items, clients.did)

    /** The activity types [item] is a default for, as the record lists them. */
    suspend fun typesFor(item: AtUri): List<ActivityType> =
        load().sports.orEmpty().filter { item in it.gear }.map { ActivityType(it.sport, it.subSport) }

    /**
     * Makes [item] a default for exactly [types], read afresh so a change on another device isn't lost, and returns the record saved.
     * Entries left with no gear are dropped. Throws if the PDS can't be reached, and refuses a session that can't write the record.
     */
    suspend fun setTypes(item: AtUri, types: Collection<ActivityType>): GearAutoAssignRecord = update { before ->
        val wanted = types.toSet()
        val kept =
            before.sports.orEmpty().map { entry ->
                val type = ActivityType(entry.sport, entry.subSport)
                if (type in wanted) entry.copy(gear = (entry.gear - item + item).takeLast(ActivityRecord.MAX_GEAR))
                else entry.copy(gear = entry.gear - item)
            }
        val listed = kept.map { ActivityType(it.sport, it.subSport) }.toSet()
        val added = wanted.filter { it !in listed }.map { GearSportDefault(it.sport, listOf(item), it.subSport) }
        before.copy(sports = (kept + added).filter { it.gear.isNotEmpty() }.take(GearAutoAssignRecord.MAX_SPORTS).ifEmpty { null })
    }

    /**
     * Applies [edit] to the sensor and profile links and the ignored devices in one write, read afresh like [setTypes], and returns the
     * record saved. A key is kept at most once across links and ignores: linking a key drops its ignore and any other link of it, and
     * ignoring it drops its link. Each list keeps its newest entries up to the lexicon's limit.
     */
    suspend fun editLinks(edit: GearLinkEdit): GearAutoAssignRecord = update(edit::applyTo)

    /** Reads the record afresh, writes [change]'s result when it differs, and keeps it as [current]. */
    private suspend fun update(change: (GearAutoAssignRecord) -> GearAutoAssignRecord): GearAutoAssignRecord = mutex.withLock {
        clients.requireSpacesFor(Visibility.SELF, Nsid.GEAR_AUTO_ASSIGN)
        val before = read() ?: current.value ?: empty()
        val changed = change(before)
        val record = changed.copy(updatedAt = clock.now())
        if (changed != before) {
            clients.spaces.putRecord(
                clients.tiers.ensure(AthleteSpace.SELF),
                Nsid.GEAR_AUTO_ASSIGN,
                GearAutoAssignRecord.RKEY,
                AtJson.encodeToJsonElement(GearAutoAssignRecord.serializer(), record),
            )
        }
        current.value = record
        record
    }

    private fun empty() = GearAutoAssignRecord(updatedAt = clock.now())

    /** The record; empty when there's none, null when it can't be read now (or this session can't read it). */
    private suspend fun read(): GearAutoAssignRecord? = runSuspendCatching { readOrThrow() }.getOrNull()

    /** The record; empty when there's none or this session can't use spaces. Throws when it can't be read, the space refusing included. */
    private suspend fun readOrThrow(): GearAutoAssignRecord {
        if (!clients.canUseSpaces) return empty()
        val read =
            clients.spaces.getRecord(clients.tiers.uri(AthleteSpace.SELF), clients.did, Nsid.GEAR_AUTO_ASSIGN, GearAutoAssignRecord.RKEY)
        return when (read) {
            is SpaceRead.Found ->
                runCatching { AtJson.decodeFromJsonElement(GearAutoAssignRecord.serializer(), read.value.value) }.getOrNull() ?: empty()
            SpaceRead.NotFound -> empty()
            SpaceRead.NoAccess -> error("The self space refused its own athlete")
        }
    }

    companion object {
        /**
         * The gear a new activity of [type] starts with under [record], from [items] (the athlete's gear; anything else is ignored, as are
         * retired items). For each gear type, the items of the entry for [type]'s sport and sub-sport win when it has any of that type,
         * else those of the entry for its sport alone. Sub-sport items come first, each entry's in its order, at most
         * [ActivityRecord.MAX_GEAR]. [did] is the athlete's.
         */
        fun forType(record: GearAutoAssignRecord, type: ActivityType, items: List<GearItem>, did: Did): List<AtUri> {
            val active = items.filter { it.record.retiredAt == null }.associateBy { it.uri(did) }
            fun itemsOf(entry: GearSportDefault?) = entry?.gear.orEmpty().mapNotNull { active[it] }
            val entries = record.sports.orEmpty().filter { it.sport == type.sport }
            val sport = itemsOf(entries.firstOrNull { it.subSport == null })
            val sub = if (type.subSport == null) emptyList() else itemsOf(entries.firstOrNull { it.subSport == type.subSport })
            val subTypes = sub.map { it.record.type }.toSet()
            return (sub + sport.filter { it.record.type !in subTypes })
                .distinctBy { it.rkey }
                .take(ActivityRecord.MAX_GEAR)
                .map { it.uri(did) }
        }
    }
}

/** One round of changes to the athlete's sensor and profile links ([GearDefaults.editLinks]). */
data class GearLinkEdit(
    val link: List<GearSensorLink> = emptyList(),
    val ignore: List<SensorKey> = emptyList(),
    val linkProfiles: List<GearProfileLink> = emptyList(),
    val unlink: List<SensorKey> = emptyList(),
    val unignore: List<SensorKey> = emptyList(),
    val unlinkProfiles: Set<String> = emptySet(),
) {
    /** Whether it changes nothing. */
    val isEmpty: Boolean
        get() = this == GearLinkEdit()

    /** [before] with this edit applied, as [GearDefaults.editLinks] saves it. */
    fun applyTo(before: GearAutoAssignRecord): GearAutoAssignRecord {
        fun SensorKey.isIn(keys: Collection<SensorKey>) = keys.any { it.matches(this) }
        val linked = link.map { it.key }
        val sensors = before.sensors.orEmpty().filterNot { it.key.isIn(linked + ignore + unlink) } + link.distinctBy { it.key.normalized() }
        val ignored = before.ignored.orEmpty().filterNot { it.isIn(linked + ignore + unignore) } + ignore
        val names = linkProfiles.map { it.name }.toSet() + unlinkProfiles
        val profiles = before.profiles.orEmpty().filterNot { it.name in names } + linkProfiles.distinctBy { it.name }
        return before.copy(
            sensors = sensors.takeLast(GearAutoAssignRecord.MAX_SENSORS).ifEmpty { null },
            ignored = ignored.distinctBy { it.normalized() }.takeLast(GearAutoAssignRecord.MAX_IGNORED).ifEmpty { null },
            profiles = profiles.takeLast(GearAutoAssignRecord.MAX_PROFILES).ifEmpty { null },
        )
    }
}
