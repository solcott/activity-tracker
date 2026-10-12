package io.github.solcott.activitytracker.app.feedfilter

import androidx.compose.runtime.Immutable
import com.slack.circuit.subcircuit.SubCircuitOuterEvent
import com.slack.circuit.subcircuit.SubCircuitUiState
import com.slack.circuit.subcircuit.SubScreen
import io.github.solcott.activitytracker.data.sport.SportCategory
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.datetime.LocalDate

/** When the activities in a feed started, in the athlete's local calendar days. A preset is resolved against today when it's queried. */
@Immutable
sealed interface DateRange {
    /** The last [days] local days, today included. */
    @Immutable data class LastDays(val days: Int) : DateRange

    /** January 1st of this year through today. */
    @Immutable data object ThisYear : DateRange

    /** [start] through [endInclusive], both included. */
    @Immutable data class Custom(val start: LocalDate, val endInclusive: LocalDate) : DateRange

    companion object {
        val Presets: ImmutableList<DateRange> = persistentListOf(LastDays(7), LastDays(30), ThisYear)
    }
}

/**
 * What a list of activities is narrowed to. [types] empty means every type; a type with no sub-sport matches all of that sport's
 * sub-sports. Whose activities they are (the feed's scope) is the host's choice, not a filter.
 */
@Immutable
data class FeedFilter(val types: ImmutableSet<ActivityType> = persistentSetOf(), val range: DateRange? = null) {
    /** Whether a type or date filter narrows the list. */
    val isNarrowed: Boolean
        get() = types.isNotEmpty() || range != null

    companion object {
        val Default = FeedFilter()
    }
}

/**
 * The feed filter control, embeddable anywhere a list of activities can be filtered. It owns the live filter and reports it as
 * [FeedFilterOuterEvent.Changed] when it's first shown and on every change after that; [initial] only seeds it, so the host keeps this
 * screen stable rather than rebuilding it per change.
 */
@Immutable data class FeedFilterScreen(val initial: FeedFilter = FeedFilter.Default) : SubScreen<FeedFilterOuterEvent>

sealed interface FeedFilterOuterEvent : SubCircuitOuterEvent {
    data class Changed(val filter: FeedFilter) : FeedFilterOuterEvent
}

@Immutable
data class FeedFilterState(
    val filter: FeedFilter,
    /** The open type picker, or null when it's closed. */
    val typePicker: TypePickerState?,
    /** The custom range being picked, or null when no date picker is open. */
    val customRange: CustomRangeState?,
    val eventSink: (FeedFilterEvent) -> Unit,
) : SubCircuitUiState

/** The multi-select type picker. [draft] changes the filter only on [FeedFilterEvent.ApplyTypes]. */
@Immutable
data class TypePickerState(
    val draft: ImmutableSet<ActivityType>,
    /** The athlete's recent types, then the common ones. */
    val quickTypes: ImmutableList<ActivityType>,
    val categories: ImmutableList<CategorySelection>,
)

/** One catalog category in the picker, with how much of it [TypePickerState.draft] covers. */
@Immutable data class CategorySelection(val category: SportCategory, val types: ImmutableList<ActivityType>, val selection: Selection)

enum class Selection {
    None,
    Partial,
    All,
}

/** Picking a custom range takes two dates: [start] is null while the first is being picked. */
@Immutable data class CustomRangeState(val start: LocalDate?)

sealed interface FeedFilterEvent {
    data object OpenTypes : FeedFilterEvent

    data class ToggleType(val type: ActivityType) : FeedFilterEvent

    data class ToggleCategory(val category: SportCategory) : FeedFilterEvent

    data object ApplyTypes : FeedFilterEvent

    data object DismissTypes : FeedFilterEvent

    /** A preset, or null for any date. */
    data class SetRange(val range: DateRange?) : FeedFilterEvent

    data object OpenCustomRange : FeedFilterEvent

    /** The next date of a custom range: its start, then its end (either order; the range is sorted). */
    data class PickRangeDate(val date: LocalDate) : FeedFilterEvent

    data object DismissCustomRange : FeedFilterEvent

    /** Drops the type and date filters. */
    data object Clear : FeedFilterEvent
}
