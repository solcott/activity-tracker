package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.slack.circuit.retained.rememberRetained
import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.activityfile.toActivityDevice
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.gear.GearAutoAssign
import io.github.solcott.activitytracker.data.gear.GearCollection
import io.github.solcott.activitytracker.data.gear.GearCollections
import io.github.solcott.activitytracker.data.gear.GearDefaults
import io.github.solcott.activitytracker.data.gear.GearItem
import io.github.solcott.activitytracker.data.gear.GearRepository
import io.github.solcott.activitytracker.data.gear.ResolvedGear
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ActivityDevice
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.GearAutoAssignRecord
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/**
 * What an imported activity's file says about its gear (gear/gear-sensors): the [devices] it names, as `feed.activityDevices` stores them,
 * the activity profile it was recorded under, and when it started, for the mounts then.
 */
@Immutable
data class GearSource(val devices: List<ActivityDevice>, val startedAt: Instant, val profile: String? = null) {
    companion object {
        /** What [activity]'s file says: its devices as they'll be stored, its activity profile and its start. */
        fun of(activity: ImportedActivity): GearSource =
            GearSource(
                devices = activity.devices?.devices.orEmpty().map { it.toActivityDevice() },
                startedAt = activity.startedAt,
                profile = activity.devices?.activityProfile,
            )
    }
}

/**
 * The gear chosen on a form ([GearChoices.produce]). Until the athlete picks or removes an item ([touched]), it's what the form's activity
 * type starts with: for an import, the items its linked sensors and profile stand for, then the defaults ([GearAutoAssign.resolve], from
 * the first of its [sources]); else the defaults alone. Changing the type changes them; after a pick, it's what they picked, whatever the
 * type.
 */
@Stable
@Suppress("LongParameterList") // The form's states, each read as it changes.
class GearChoice
internal constructor(
    private val did: Did,
    private val type: () -> ActivityType,
    private val sources: () -> List<GearSource>,
    private val items: MutableState<List<GearItem>>,
    private val rules: MutableState<GearAutoAssignRecord?>,
    private val collections: State<List<GearCollection>>,
    private val picked: MutableState<List<AtUri>?>,
    private val notAdded: MutableState<Int>,
) {
    /** Whether the athlete has picked or removed gear on this form, or it was [seed]ed. */
    val touched: Boolean
        get() = picked.value != null

    /** The gear to save with the activity, in the athlete's order. */
    val selected: List<AtUri>
        get() = picked.value ?: resolved()?.gear.orEmpty()

    /** The athlete's gear, as read; empty until it is, or when it can't be. */
    val itemsNow: List<GearItem>
        get() = items.value

    /** The athlete's gear rules, as read or last saved; null until read, or when they can't be. */
    val rulesNow: GearAutoAssignRecord?
        get() = rules.value

    /** The athlete's collections, as read. */
    val collectionsNow: List<GearCollection>
        get() = collections.value

    /**
     * The gear types whose items clash in what the sources' sensors point at (two bikes), with the clashing items still [selected], from
     * every source: a logbook's dives share the gear.
     */
    val conflicts: List<GearConflict>
        get() {
            val record = rules.value ?: return emptyList()
            val chosen = selected.toSet()
            val names = items.value.associate { it.uri(did) to it.record.name }
            return sources()
                .flatMap {
                    GearAutoAssign.resolve(type(), it.startedAt, it.devices, it.profile, items.value, record, did).conflicts.entries
                }
                .groupBy({ it.key }, { it.value })
                .mapNotNull { (gearType, clashes) ->
                    val clashing = clashes.flatten().distinct().filter { it in chosen }
                    if (clashing.size < 2) null else GearConflict(gearType, clashing.mapNotNull { names[it] }.toImmutableList())
                }
        }

    /** After a save wrote new [record] rules and [added] items: the untouched gear resolves with them. */
    fun updated(record: GearAutoAssignRecord?, added: List<GearItem>) {
        if (record != null) rules.value = record
        items.value = items.value.filter { item -> added.none { it.rkey == item.rkey } } + added
    }

    private fun resolved(): ResolvedGear? =
        rules.value?.let { record ->
            val source = sources().firstOrNull()
            if (source == null) ResolvedGear(GearDefaults.forType(record, type(), items.value, did))
            else GearAutoAssign.resolve(type(), source.startedAt, source.devices, source.profile, items.value, record, did)
        }

    /** What the picker shows: active items, those for the activity's sport first then by name, and retired ones only when selected. */
    val field: GearField
        get() {
            val chosen = selected
            val sport = type().sport
            val options =
                items.value
                    .filter { it.record.retiredAt == null || it.uri(did) in chosen }
                    .sortedWith(compareBy<GearItem> { sport !in it.record.sports.orEmpty() }.thenBy { it.record.name.lowercase() })
                    .map {
                        GearOption(
                            uri = it.uri(did),
                            name = it.record.name,
                            type = it.record.type,
                            kind = it.record.kind,
                            visibility = it.visibility,
                            retired = it.record.retiredAt != null,
                        )
                    }
            val collectionOptions = collections.value.map { GearCollectionOption(it.rkey, it.record.name, it.items.size) }
            return GearField(
                options.toImmutableList(),
                chosen.toImmutableList(),
                collectionOptions.toImmutableList(),
                notAdded.value,
                conflicts.toImmutableList(),
                ::onEvent,
            )
        }

    /** [categories] (those the activity has data in), with Gear once the form lists gear: picked gear is data too. */
    fun offering(categories: Collection<String>): ImmutableSet<String> =
        (if (selected.isEmpty()) categories else categories + DetailCategory.GEAR).toImmutableSet()

    /** [selected], when it differs from [saved], the gear the activity lists now; null when it doesn't. */
    fun changedFrom(saved: List<AtUri>): List<AtUri>? = selected.takeIf { it != saved }

    /** Starts from [gear], the gear an existing activity lists, as if the athlete had picked it. */
    fun seed(gear: List<AtUri>) {
        picked.value = gear
    }

    fun onEvent(event: GearEvent) {
        val now = selected
        notAdded.value = 0
        picked.value =
            when (event) {
                is GearEvent.Toggle -> if (event.uri in now) now - event.uri else (now + event.uri).take(ActivityRecord.MAX_GEAR)
                GearEvent.Clear -> emptyList()
                is GearEvent.AddCollection -> {
                    val active = items.value.filter { it.record.retiredAt == null }.map { it.uri(did) }.toSet()
                    val members = collections.value.firstOrNull { it.rkey == event.rkey }?.items.orEmpty()
                    val adding = members.filter { it in active && it !in now }.distinct()
                    val added = adding.take((ActivityRecord.MAX_GEAR - now.size).coerceAtLeast(0))
                    notAdded.value = adding.size - added.size
                    now + added
                }
            }
    }
}

/**
 * Produces a form's [GearChoice] from the athlete's gear, their collections and their defaults per activity type, each read once. Injected
 * into the presenters of the forms that save an activity. Reading either can fail without stopping the form: it then offers no gear, or
 * starts with none.
 */
@Inject
class GearChoices(private val gear: GearRepository, private val defaults: GearDefaults, private val collections: GearCollections) {
    /** For an activity of [type], and for an import the [sources] its file gives, each read as it changes. */
    @Composable
    fun produce(type: ActivityType, sources: ImmutableList<GearSource> = persistentListOf()): GearChoice {
        val currentType by rememberUpdatedState(type)
        val currentSources by rememberUpdatedState(sources)
        val items = remember { mutableStateOf(emptyList<GearItem>()) }
        val rules = remember { mutableStateOf<GearAutoAssignRecord?>(null) }
        LaunchedEffect(Unit) { items.value = runSuspendCatching { gear.items() }.getOrDefault(emptyList()) }
        LaunchedEffect(Unit) { rules.value = runSuspendCatching { defaults.load() }.getOrNull() }
        // Once the items are read, so members of deleted items are left out.
        val groups =
            produceState(emptyList<GearCollection>(), items.value) {
                if (items.value.isNotEmpty()) value = runSuspendCatching { collections.all(items.value) }.getOrDefault(emptyList())
            }
        val picked = rememberRetained { mutableStateOf<List<AtUri>?>(null) }
        val notAdded = remember { mutableIntStateOf(0) }
        return remember(items, rules, groups, picked) {
            GearChoice(gear.did, { currentType }, { currentSources }, items, rules, groups, picked, notAdded)
        }
    }
}
