package io.github.solcott.activitytracker.data.gear

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.data.activity.requireSpacesFor
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GearAutoAssignRecord
import io.github.solcott.activitytracker.lexicon.app.GearSportDefault
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.time.Clock
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

    /** The gear a new activity of [type] starts with, from [items], the athlete's gear ([forType]). */
    suspend fun forType(type: ActivityType, items: List<GearItem>): List<AtUri> = forType(load(), type, items, clients.did)

    /** The activity types [item] is a default for, as the record lists them. */
    suspend fun typesFor(item: AtUri): List<ActivityType> =
        load().sports.orEmpty().filter { item in it.gear }.map { ActivityType(it.sport, it.subSport) }

    /**
     * Makes [item] a default for exactly [types], read afresh so a change on another device isn't lost, and returns the record saved.
     * Entries left with no gear are dropped. Throws if the PDS can't be reached, and refuses a session that can't write the record.
     */
    suspend fun setTypes(item: AtUri, types: Collection<ActivityType>): GearAutoAssignRecord = mutex.withLock {
        clients.requireSpacesFor(Visibility.SELF, Nsid.GEAR_AUTO_ASSIGN)
        val before = read() ?: current.value ?: empty()
        val wanted = types.toSet()
        val kept =
            before.sports.orEmpty().map { entry ->
                val type = ActivityType(entry.sport, entry.subSport)
                if (type in wanted) entry.copy(gear = (entry.gear - item + item).takeLast(ActivityRecord.MAX_GEAR))
                else entry.copy(gear = entry.gear - item)
            }
        val listed = kept.map { ActivityType(it.sport, it.subSport) }.toSet()
        val added = wanted.filter { it !in listed }.map { GearSportDefault(it.sport, listOf(item), it.subSport) }
        val record =
            GearAutoAssignRecord(
                updatedAt = clock.now(),
                sports = (kept + added).filter { it.gear.isNotEmpty() }.take(GearAutoAssignRecord.MAX_SPORTS).ifEmpty { null },
            )
        if (record.sports != before.sports) {
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
    private suspend fun read(): GearAutoAssignRecord? {
        if (!clients.canUseSpaces) return empty()
        val read = runSuspendCatching {
            clients.spaces.getRecord(clients.tiers.uri(AthleteSpace.SELF), clients.did, Nsid.GEAR_AUTO_ASSIGN, GearAutoAssignRecord.RKEY)
        }
            .getOrNull()
        return when (read) {
            is SpaceRead.Found ->
                runCatching { AtJson.decodeFromJsonElement(GearAutoAssignRecord.serializer(), read.value.value) }.getOrNull() ?: empty()
            SpaceRead.NotFound -> empty()
            SpaceRead.NoAccess,
            null -> null
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
