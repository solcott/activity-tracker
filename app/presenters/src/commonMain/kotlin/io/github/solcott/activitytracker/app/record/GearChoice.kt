package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.slack.circuit.retained.rememberRetained
import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.gear.GearDefaults
import io.github.solcott.activitytracker.data.gear.GearItem
import io.github.solcott.activitytracker.data.gear.GearRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.GearAutoAssignRecord
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/**
 * The gear chosen on a form ([GearChoices.produce]). Until the athlete picks or removes an item ([touched]), it's the defaults for the
 * form's activity type, so changing the type changes them; after, it's what they picked, whatever the type.
 */
@Stable
class GearChoice
internal constructor(
    private val did: Did,
    private val type: () -> ActivityType,
    private val items: State<List<GearItem>>,
    private val rules: State<GearAutoAssignRecord?>,
    private val picked: MutableState<List<AtUri>?>,
) {
    /** Whether the athlete has picked or removed gear on this form, or it was [seed]ed. */
    val touched: Boolean
        get() = picked.value != null

    /** The gear to save with the activity, in the athlete's order. */
    val selected: List<AtUri>
        get() = picked.value ?: rules.value?.let { GearDefaults.forType(it, type(), items.value, did) }.orEmpty()

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
            return GearField(options.toImmutableList(), chosen.toImmutableList(), ::onEvent)
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
        picked.value =
            when (event) {
                is GearEvent.Toggle -> if (event.uri in now) now - event.uri else (now + event.uri).take(ActivityRecord.MAX_GEAR)
                GearEvent.Clear -> emptyList()
            }
    }
}

/**
 * Produces a form's [GearChoice] from the athlete's gear and their defaults per activity type, each read once. Injected into the presenters
 * of the forms that save an activity. Reading either can fail without stopping the form: it then offers no gear, or starts with none.
 */
@Inject
class GearChoices(private val gear: GearRepository, private val defaults: GearDefaults) {
    /** For an activity of [type], read as it changes. */
    @Composable
    fun produce(type: ActivityType): GearChoice {
        val currentType by rememberUpdatedState(type)
        val items = produceState(emptyList<GearItem>()) { value = runSuspendCatching { gear.items() }.getOrDefault(emptyList()) }
        val rules = produceState<GearAutoAssignRecord?>(null) { value = runSuspendCatching { defaults.load() }.getOrNull() }
        val picked = rememberRetained { mutableStateOf<List<AtUri>?>(null) }
        return remember(items, rules, picked) { GearChoice(gear.did, { currentType }, items, rules, picked) }
    }
}
