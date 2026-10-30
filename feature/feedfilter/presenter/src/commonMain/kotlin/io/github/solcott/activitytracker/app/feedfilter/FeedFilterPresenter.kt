package io.github.solcott.activitytracker.app.feedfilter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.subcircuit.SubCircuitInject
import com.slack.circuit.subcircuit.SubPresenter
import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.app.record.quickActivityTypes
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.data.sport.SportCatalog
import io.github.solcott.activitytracker.data.sport.SportCategory
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/** Owns the live [FeedFilter], seeded from [FeedFilterScreen.initial], and reports it to the host. Metro injects the screen. */
@SubCircuitInject(FeedFilterScreen::class, SessionScope::class)
@Inject
class FeedFilterPresenter(private val screen: FeedFilterScreen, private val recentTypes: RecentActivityTypes) :
    SubPresenter<FeedFilterOuterEvent, FeedFilterState> {
    @Composable
    override fun present(outerEventSink: (FeedFilterOuterEvent) -> Unit): FeedFilterState {
        var filter by rememberRetained { mutableStateOf(screen.initial) }
        var draft by rememberRetained { mutableStateOf<Set<ActivityType>?>(null) }
        var customRange by rememberRetained { mutableStateOf<CustomRangeState?>(null) }
        val quickTypes = quickActivityTypes(recentTypes)
        val typePicker = draft?.let { picked ->
            remember(picked, quickTypes) { TypePickerState(picked.toImmutableSet(), quickTypes, categories(picked)) }
        }

        // The whole upward channel: once when first shown, so the host always matches even if this state was lost and its wasn't, then
        // once per change.
        SideEffect(filter) { outerEventSink(FeedFilterOuterEvent.Changed(filter)) }

        return FeedFilterState(filter, typePicker, customRange) { event ->
            when (event) {
                FeedFilterEvent.OpenTypes -> draft = filter.types
                is FeedFilterEvent.ToggleType -> draft = draft?.toggle(event.type)
                is FeedFilterEvent.ToggleCategory -> draft = draft?.let { toggleCategory(it, event.category) }
                FeedFilterEvent.ApplyTypes -> {
                    // One snapshot, so the picker closes in the same frame the filter changes.
                    Snapshot.withMutableSnapshot {
                        draft?.let { filter = filter.copy(types = it.toImmutableSet()) }
                        draft = null
                    }
                }
                FeedFilterEvent.DismissTypes -> draft = null
                is FeedFilterEvent.SetRange -> filter = filter.copy(range = event.range)
                FeedFilterEvent.OpenCustomRange -> customRange = CustomRangeState(start = null)
                is FeedFilterEvent.PickRangeDate -> {
                    val start = customRange?.start
                    if (start == null) {
                        customRange = CustomRangeState(event.date)
                    } else {
                        Snapshot.withMutableSnapshot {
                            customRange = null
                            filter = filter.copy(range = DateRange.Custom(minOf(start, event.date), maxOf(start, event.date)))
                        }
                    }
                }
                FeedFilterEvent.DismissCustomRange -> customRange = null
                FeedFilterEvent.Clear -> filter = FeedFilter.Default
            }
        }
    }
}

private fun categories(picked: Set<ActivityType>): ImmutableList<CategorySelection> =
    SportCatalog.categories
        .map { (category, types) ->
            val covered = types.count(picked::covers)
            val selection =
                when (covered) {
                    0 -> Selection.None
                    types.size -> Selection.All
                    else -> Selection.Partial
                }
            CategorySelection(category, types.toImmutableList(), selection)
        }
        .toImmutableList()

/** Selects every type in [category], or deselects them all when it's already fully selected. */
private fun toggleCategory(picked: Set<ActivityType>, category: SportCategory): Set<ActivityType> {
    val types = SportCatalog.categories.first { it.category == category }.types
    return if (types.all(picked::covers)) {
        types.fold(picked) { acc, type -> if (acc.covers(type)) acc.deselect(type) else acc }
    } else {
        types.fold(picked) { acc, type -> acc.select(type) }
    }
}
