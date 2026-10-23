package io.github.solcott.activitytracker.app.feedfilter

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.calf.ui.datepicker.DateBounds
import com.slack.circuit.subcircuit.SubCircuitInject
import com.slack.circuit.subcircuit.SubUi
import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.app.ui.LocalClock
import io.github.solcott.activitytracker.app.ui.QuickTypeChips
import io.github.solcott.activitytracker.app.ui.activityTypeLabel
import io.github.solcott.activitytracker.app.ui.activityTypeMatcher
import io.github.solcott.activitytracker.app.ui.adaptive.AppBottomSheet
import io.github.solcott.activitytracker.app.ui.adaptive.AppDatePickerDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppFilterChip
import io.github.solcott.activitytracker.app.ui.adaptive.AppFilterChipIconSize
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppSheetHeader
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTriStateCheckbox
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppSheetState
import io.github.solcott.activitytracker.app.ui.rememberExpandedSports
import io.github.solcott.activitytracker.app.ui.sportTypeList
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_all_types
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_any_time
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_apply
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_clear
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_custom
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_custom_range
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_last_days
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_this_year
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_types_more
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_types_title
import io.github.solcott.activitytracker.composeApp.resources.sport_picker_no_matches
import io.github.solcott.activitytracker.composeApp.resources.sport_picker_quick
import io.github.solcott.activitytracker.composeApp.resources.sport_picker_search
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One row, scrolling sideways when it doesn't fit: a chip each for types and dates, and clear while either narrows the list. It insets
 * itself by the screen margin inside the scroll, so chips scroll to the edge rather than clipping short of it.
 */
@Composable
fun FeedFilterUi(state: FeedFilterState, modifier: Modifier = Modifier) {
    val filter = state.filter
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppFilterChip(
            selected = filter.types.isNotEmpty(),
            onClick = { state.eventSink(FeedFilterEvent.OpenTypes) },
            label = { Text(typesLabel(filter.types)) },
            trailingIcon = { PlatformIcon(AppIcon.DropDown, null, size = AppFilterChipIconSize) },
        )
        DateChip(filter.range, state.eventSink)
        if (filter.isNarrowed) {
            AppTextButton(onClick = { state.eventSink(FeedFilterEvent.Clear) }) { Text(stringResource(Res.string.feed_filter_clear)) }
        }
    }
    state.typePicker?.let { TypePickerSheet(it, state.eventSink) }
    state.customRange?.let { CustomRangeDialog(it, state.eventSink) }
}

/** Registers [FeedFilterUi]. A class rather than the annotated function: that factory crashes Kotlin/Wasm (see compose-screens.md). */
@SubCircuitInject(FeedFilterScreen::class, SessionScope::class)
@Inject
class FeedFilterSubUi : SubUi<FeedFilterState> {
    @Composable override fun Content(state: FeedFilterState, modifier: Modifier) = FeedFilterUi(state, modifier)
}

@Composable
private fun typesLabel(types: ImmutableSet<ActivityType>): String {
    val sorted = types.sortedBy { it.sport + it.subSport.orEmpty() }
    return when (sorted.size) {
        0 -> stringResource(Res.string.feed_filter_all_types)
        1 -> activityTypeLabel(sorted.single())
        else ->
            pluralStringResource(Res.plurals.feed_filter_types_more, sorted.size - 1, activityTypeLabel(sorted.first()), sorted.size - 1)
    }
}

@Composable
private fun rangeLabel(range: DateRange?): String =
    when (range) {
        null -> stringResource(Res.string.feed_filter_any_time)
        is DateRange.LastDays -> pluralStringResource(Res.plurals.feed_filter_last_days, range.days, range.days)
        DateRange.ThisYear -> stringResource(Res.string.feed_filter_this_year)
        is DateRange.Custom -> stringResource(Res.string.feed_filter_custom_range, range.start.toString(), range.endInclusive.toString())
    }

@Composable
private fun DateChip(range: DateRange?, eventSink: (FeedFilterEvent) -> Unit) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val choices = listOf<DateRange?>(null) + DateRange.Presets
    val items =
        choices.map { choice ->
            AppMenuItem(rangeLabel(choice), checked = choice == range) {
                menuOpen = false
                eventSink(FeedFilterEvent.SetRange(choice))
            }
        } +
            AppMenuItem(stringResource(Res.string.feed_filter_custom), checked = range is DateRange.Custom) {
                menuOpen = false
                eventSink(FeedFilterEvent.OpenCustomRange)
            }
    Box {
        AppFilterChip(
            selected = range != null,
            onClick = { menuOpen = true },
            label = { Text(rangeLabel(range)) },
            trailingIcon = { PlatformIcon(AppIcon.DropDown, null, size = AppFilterChipIconSize) },
        )
        AppMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, items = items.toImmutableList())
    }
}

/** The first pick is the range's first day, the second its last; neither can be in the future, and the last can't precede the first. */
@Composable
private fun CustomRangeDialog(range: CustomRangeState, eventSink: (FeedFilterEvent) -> Unit) {
    val clock = LocalClock.current
    val today = remember { clock.now().toEpochMilliseconds() }
    val start = range.start
    val bounds = remember(start, today) { DateBounds(minDateMillis = start?.toPickerMillis(), maxDateMillis = today) }
    // Keyed so the second pick opens a fresh dialog rather than reusing the first's selection.
    key(start) {
        AppDatePickerDialog(
            initialSelectedDateMillis = start?.toPickerMillis(),
            selectableDates = bounds,
            onConfirm = { millis ->
                if (millis == null) eventSink(FeedFilterEvent.DismissCustomRange)
                else eventSink(FeedFilterEvent.PickRangeDate(LocalDate.fromEpochDays(millis.floorDiv(MILLIS_PER_DAY).toInt())))
            },
            onDismiss = { eventSink(FeedFilterEvent.DismissCustomRange) },
        )
    }
}

@Composable
private fun TypePickerSheet(picker: TypePickerState, eventSink: (FeedFilterEvent) -> Unit) {
    val sheetState = rememberAppSheetState()
    AppBottomSheet(onDismiss = { eventSink(FeedFilterEvent.DismissTypes) }, sheetState = sheetState) {
        AppSheetHeader(
            title = stringResource(Res.string.feed_filter_types_title),
            cancelLabel = stringResource(Res.string.action_cancel),
            onCancel = { eventSink(FeedFilterEvent.DismissTypes) },
            confirmLabel = stringResource(Res.string.feed_filter_apply),
            onConfirm = { eventSink(FeedFilterEvent.ApplyTypes) },
        )
        // Search is UI-only, so the query resets each time the sheet opens.
        val query = rememberTextFieldState()
        val needle = query.text.trim().toString()
        val searching = needle.isNotEmpty()
        val matches = activityTypeMatcher(needle)
        val groups =
            picker.categories
                .map { it.category to it.types.filter(matches).toImmutableList() }
                .filter { it.second.isNotEmpty() }
                .toImmutableList()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextField(
                query,
                placeholder = stringResource(Res.string.sport_picker_search),
                leadingIcon = { PlatformIcon(AppIcon.Search, null) },
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!searching) {
                Text(stringResource(Res.string.sport_picker_quick), style = MaterialTheme.typography.labelLarge)
                QuickTypeChips(
                    picker.quickTypes,
                    isSelected = { picker.draft.covers(it) },
                    onClick = { eventSink(FeedFilterEvent.ToggleType(it)) },
                )
            }
            if (groups.isEmpty()) Text(stringResource(Res.string.sport_picker_no_matches), style = MaterialTheme.typography.bodyMedium)
        }
        var expanded by rememberExpandedSports()
        // Full height, so the sheet doesn't jump as the search narrows the list.
        LazyColumn(Modifier.fillMaxSize()) {
            sportTypeList(
                groups,
                expanded = expanded,
                onToggleExpanded = { sport -> expanded = if (sport in expanded) expanded - sport else expanded + sport },
                onClick = { eventSink(FeedFilterEvent.ToggleType(it)) },
                // A search shows every match, so none hides behind a collapsed sport.
                expandAll = searching,
                // While searching, a category would select types the search hides, so its header is plain.
                onCategoryClick = if (searching) null else { category -> eventSink(FeedFilterEvent.ToggleCategory(category)) },
                categoryTrailing = { category ->
                    if (!searching) {
                        val state =
                            when (picker.categories.first { it.category == category }.selection) {
                                Selection.All -> ToggleableState.On
                                Selection.Partial -> ToggleableState.Indeterminate
                                Selection.None -> ToggleableState.Off
                            }
                        AppTriStateCheckbox(state, onClick = null)
                    }
                },
            ) { type ->
                // A sport with only some of its sub-sports selected shows the mixed mark, as a category does.
                val state =
                    when {
                        picker.draft.covers(type) -> ToggleableState.On
                        type.subSport == null && picker.draft.any { it.sport == type.sport } -> ToggleableState.Indeterminate
                        else -> ToggleableState.Off
                    }
                AppTriStateCheckbox(state, onClick = null)
            }
        }
    }
}

/** Whether [type] is selected, directly or through its bare sport (as the presenter's selection rules count it). */
private fun Set<ActivityType>.covers(type: ActivityType): Boolean =
    type in this || (type.subSport != null && ActivityType(type.sport) in this)

private const val MILLIS_PER_DAY = 86_400_000L

/** UTC midnight of this date, as the date picker takes it. */
private fun LocalDate.toPickerMillis(): Long = toEpochDays() * MILLIS_PER_DAY
