package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.lexicon.app.ThresholdEntry
import kotlinx.collections.immutable.ImmutableList

/** Every value the signed-in athlete's thresholds have had, where each came from, and a way to remove one. */
@CircuitSerializable(SessionScope::class) data object ThresholdHistoryScreen : Screen

@Immutable
data class ThresholdHistoryState(
    /** One per threshold with history, the defaults' first; null until loaded. */
    val sections: ImmutableList<ThresholdHistorySection>?,
    /** The entry being removed; one at a time, since each removal rewrites the whole history. */
    val removing: ThresholdEntry?,
    val error: UiError?,
    val eventSink: (ThresholdHistoryEvent) -> Unit,
) : CircuitUiState

/** One threshold's history: [threshold] is a `Threshold` value, of [sport]'s override, or the defaults' when [sport] is null. */
@Immutable
data class ThresholdHistorySection(
    val threshold: String,
    val sport: String?,
    /** How a threshold speed shows as a pace. */
    val paceUnit: PaceUnit,
    /** Newest first. */
    val rows: ImmutableList<ThresholdHistoryRow>,
)

@Immutable
data class ThresholdHistoryRow(
    val entry: ThresholdEntry,
    /** The local date it took effect, `YYYY-MM-DD`; null when unknown. */
    val date: String?,
    /** True when it's the threshold's value now. */
    val current: Boolean,
)

sealed interface ThresholdHistoryEvent {
    data object Back : ThresholdHistoryEvent

    /** Remove [entry]; when it's the current value, the threshold goes back to the one before it. */
    data class Remove(val entry: ThresholdEntry) : ThresholdHistoryEvent
}
