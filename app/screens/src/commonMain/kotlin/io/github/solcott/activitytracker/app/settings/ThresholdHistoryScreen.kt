package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.lexicon.app.ThresholdEntry
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.hasLoaded
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Every value the signed-in athlete's thresholds have had, grouped by the defaults and each sport, then by threshold, with a chart of each
 * threshold's progress and a way to remove a value.
 */
@CircuitSerializable(SessionScope::class) data object ThresholdHistoryScreen : Screen

@Immutable
data class ThresholdHistoryState(
    /** The defaults first, then each sport with history, loaded (ui/content-loading); empty until they first load. */
    val content: ContentState<ImmutableList<ThresholdHistoryGroup>> = ContentState(persistentListOf()),
    /** The entry being removed; one at a time, since each removal rewrites the whole history. */
    val removing: ThresholdEntry? = null,
    /** Why removing a value failed; loading failures are [content]'s. */
    val error: UiError? = null,
    val eventSink: (ThresholdHistoryEvent) -> Unit = {},
) : CircuitUiState {
    /** [content]'s groups once loaded; null until then. */
    val groups: ImmutableList<ThresholdHistoryGroup>?
        get() = content.data.takeIf { content.hasLoaded }

    val anyExpanded: Boolean
        get() = content.data.any { it.expanded }
}

/** The defaults' thresholds when [sport] is null, else that sport's. Collapsed, it shows an overview of each threshold instead. */
@Immutable
data class ThresholdHistoryGroup(
    val sport: String?,
    val expanded: Boolean,
    val thresholds: ImmutableList<ThresholdHistorySection>,
) {
    val valueCount: Int
        get() = thresholds.sumOf { it.rows.size }
}

/** One threshold's history: [threshold] is a `Threshold` value, of [sport]'s override, or the defaults' when [sport] is null. */
@Immutable
data class ThresholdHistorySection(
    val threshold: String,
    val sport: String?,
    /** How a threshold speed shows as a pace. */
    val paceUnit: PaceUnit,
    /** Newest first. The first is the current value unless the threshold has been cleared or changed since. */
    val rows: ImmutableList<ThresholdHistoryRow>,
    /** Whether every row shows, rather than the newest few. */
    val showingAll: Boolean,
    /** The dated values oldest first, placed for a chart; null with fewer than two. */
    val chart: ThresholdChart?,
    /** Values with no date, which the chart leaves out. */
    val undatedCount: Int,
    /** How the threshold moved since its first dated value; null when there's nothing to compare. */
    val change: ThresholdTrend?,
) {
    val newest: ThresholdHistoryRow
        get() = rows.first()

    /** The rows to show: all of them, or the newest [COLLAPSED_ROWS]. */
    val shownRows: List<ThresholdHistoryRow>
        get() = if (showingAll) rows else rows.take(COLLAPSED_ROWS)

    val canShowAll: Boolean
        get() = rows.size > COLLAPSED_ROWS

    companion object {
        const val COLLAPSED_ROWS = 3
    }
}

@Immutable
data class ThresholdHistoryRow(
    val entry: ThresholdEntry,
    /** The local date it took effect; null when unknown. */
    val date: HistoryDate?,
    /** True when it's the threshold's value now. */
    val current: Boolean,
)

/** A calendar date, month 1–12. */
@Immutable data class HistoryDate(val year: Int, val month: Int, val day: Int)

/**
 * A threshold's dated values as a step chart running from the first to today: a threshold holds until it changes. Each point's
 * [ChartPoint.x] and [ChartPoint.y] are 0–1, left to right and bottom to top, so a higher value (more watts, a faster pace) is higher.
 */
@Immutable
data class ThresholdChart(
    val points: ImmutableList<ChartPoint>,
    val start: HistoryDate,
    val lowest: Long,
    val highest: Long,
)

@Immutable data class ChartPoint(val x: Float, val y: Float, val value: Long, val current: Boolean)

/** The threshold went from [from], its first dated value on [since], to [to]. */
@Immutable data class ThresholdTrend(val from: Long, val to: Long, val since: HistoryDate)

sealed interface ThresholdHistoryEvent {
    data object Back : ThresholdHistoryEvent

    /** Expand or collapse the defaults (null [sport]) or a sport. */
    data class ToggleGroup(val sport: String?) : ThresholdHistoryEvent

    /** Expand every group when none is expanded, else collapse them all. */
    data object ToggleAll : ThresholdHistoryEvent

    /** Show every value of a threshold, or only the newest few. */
    data class ToggleShowAll(val threshold: String, val sport: String?) : ThresholdHistoryEvent

    /** Remove [entry]; when it's the current value, the threshold goes back to the one before it. */
    data class Remove(val entry: ThresholdEntry) : ThresholdHistoryEvent

    /** Loads the history again after it failed to. */
    data object Retry : ThresholdHistoryEvent
}
