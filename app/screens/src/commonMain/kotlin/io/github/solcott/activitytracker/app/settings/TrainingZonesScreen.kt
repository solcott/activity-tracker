package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.training.EffectiveTraining
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.data.training.Zone
import io.github.solcott.activitytracker.data.training.ZoneKind
import io.github.solcott.activitytracker.data.training.ZoneModel
import io.github.solcott.activitytracker.lexicon.app.SportTraining
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

/**
 * An overview of the signed-in athlete's training zones: the defaults, then each sport with its own. Each opens its editor ([ZonesScreen],
 * [SportZonesScreen]), and another sport can be added.
 */
@CircuitSerializable(SessionScope::class) data object TrainingZonesScreen : Screen

@Immutable
data class TrainingZonesState(
    /** The athlete's zones, loaded (ui/content-loading); null until they first load. */
    val content: ContentState<TrainingZones?> = ContentState(null),
    /** A `Visibility` value for all the thresholds and zones together, set here rather than in each editor. */
    val visibility: String = Visibility.SELF,
    val savingVisibility: Boolean = false,
    /** Why saving the visibility failed; loading failures are [content]'s. */
    val error: UiError? = null,
    val eventSink: (TrainingZonesEvent) -> Unit = {},
) : CircuitUiState

/** The athlete's zones as they apply. */
@Immutable
data class TrainingZones(
    /** The defaults. */
    val defaults: ZoneSummary,
    /** The sports with their own zones, in saved order. */
    val sports: ImmutableList<ZoneSummary>,
    /** `Sport` values that could have their own zones and don't yet. */
    val addableSports: ImmutableList<String>,
    /** Whether any threshold or zone model is saved: visibility only sticks to saved details. */
    val hasZones: Boolean,
    /** Whether any threshold has a value, so there's history to show. */
    val hasHistory: Boolean,
    /** The athlete approves followers, so Followers means only the ones they approved. */
    val approvesFollowers: Boolean = false,
)

/**
 * One set of zones as it applies: the defaults when [sport] is null, else that sport's. [override] is what the sport sets itself, so a
 * threshold it leaves out can be shown as the default's.
 */
@Immutable
data class ZoneSummary(
    val sport: String?,
    val kinds: ImmutableSet<ZoneKind>,
    val training: EffectiveTraining,
    val override: SportTraining?,
    val paceUnit: PaceUnit,
    val heartRateModel: ZoneModel,
    val heartRateZones: ImmutableList<Zone>?,
    val powerModel: ZoneModel,
    val powerZones: ImmutableList<Zone>?,
    val speedModel: ZoneModel,
    val speedZones: ImmutableList<Zone>?,
)

sealed interface TrainingZonesEvent {
    data object Back : TrainingZonesEvent

    data object EditDefaults : TrainingZonesEvent

    /** Show every value the thresholds have had ([ThresholdHistoryScreen]). */
    data object History : TrainingZonesEvent

    /** Saves [visibility], a `Visibility` value, for all the thresholds and zones. */
    data class VisibilityChanged(val visibility: String) : TrainingZonesEvent

    /** Loads the zones again after they failed to. */
    data object Retry : TrainingZonesEvent

    /** Edit [sport]'s zones, or add them when it has none. */
    data class EditSport(val sport: String) : TrainingZonesEvent
}
