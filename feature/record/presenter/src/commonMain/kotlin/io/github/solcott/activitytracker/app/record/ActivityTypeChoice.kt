package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import kotlinx.collections.immutable.ImmutableList

/**
 * The activity type chosen on a form ([ActivityTypeChoices.produce]): the athlete picks it from [field], or the form sets it ([select]).
 */
@Stable
class ActivityTypeChoice
internal constructor(private val chosen: MutableState<ActivityType>, private val quickTypes: ImmutableList<ActivityType>) {
    val selected: ActivityType
        get() = chosen.value

    /** What the picker shows. */
    val field: ActivityTypeField
        get() = ActivityTypeField(selected, quickTypes, ::onEvent)

    fun select(type: ActivityType) {
        chosen.value = type
    }

    fun onEvent(event: ActivityTypeEvent) {
        when (event) {
            is ActivityTypeEvent.Select -> select(event.type)
        }
    }
}

/**
 * Produces a form's [ActivityTypeChoice]: the type, saved across process death, and the quick row of the athlete's recent types. Injected
 * into the presenters of the forms that pick a type.
 */
@Inject
class ActivityTypeChoices(private val recentTypes: RecentActivityTypes) {
    /** Starts at [initial]. [outdoorOnly] leaves types that can't use GPS out of the quick row. */
    @Composable
    fun produce(initial: ActivityType, outdoorOnly: Boolean = false): ActivityTypeChoice {
        val chosen = rememberSaveable(stateSaver = ActivityTypeSaver) { mutableStateOf(initial) }
        val quickTypes = quickActivityTypes(recentTypes, outdoorOnly)
        return remember(chosen, quickTypes) { ActivityTypeChoice(chosen, quickTypes) }
    }
}
