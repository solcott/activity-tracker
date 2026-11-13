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
import com.slack.circuit.subcircuit.SubCircuitInject
import io.github.solcott.activitytracker.app.sport.SportTypeSearch
import io.github.solcott.activitytracker.app.ui.LocalClock
import io.github.solcott.activitytracker.app.ui.QuickTypeChips
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
import io.github.solcott.activitytracker.app.ui.adaptive.DateBounds
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppSheetState
import io.github.solcott.activitytracker.app.ui.pickedDate
import io.github.solcott.activitytracker.app.ui.rememberExpandedSports
import io.github.solcott.activitytracker.app.ui.sportTypeList
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.app.ui.toPickerMillis
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_apply
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_clear
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_custom
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_types_title
import io.github.solcott.activitytracker.composeApp.resources.sport_picker_no_matches
import io.github.solcott.activitytracker.composeApp.resources.sport_picker_quick
import io.github.solcott.activitytracker.composeApp.resources.sport_picker_search
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.textfield.rememberTextFieldState
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * One row, scrolling sideways when it doesn't fit: a chip each for types and dates, and clear while either narrows the list. It insets
 * itself by the screen margin inside the scroll, so chips scroll to the edge rather than clipping short of it.
 */
@SubCircuitInject(FeedFilterScreen::class, SessionScope::class)
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
            label = { Text(typesLabel(filter)) },
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

@Composable private fun typesLabel(filter: FeedFilter): String = FeedFilterTexts.types(filter).string()

@Composable private fun rangeLabel(range: DateRange?): String = FeedFilterTexts.range(range).string()

@Composable
private fun DateChip(range: DateRange?, eventSink: (FeedFilterEvent) -> Unit) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val choices = FeedFilterTexts.rangeChoices()
    val chosen = FeedFilterTexts.chosenRange(range)
    val items =
        choices.mapIndexed { index, choice ->
            AppMenuItem(rangeLabel(choice.range), checked = index == chosen) {
                menuOpen = false
                eventSink(FeedFilterEvent.SetRange(choice.range))
            }
        } +
            AppMenuItem(stringResource(Res.string.feed_filter_custom), checked = chosen == choices.size) {
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
                else eventSink(FeedFilterEvent.PickRangeDate(pickedDate(millis)))
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
        val searching = SportTypeSearch.searching(needle)
        val matches = activityTypeMatcher(needle)
        val groups = picker.groups(matches)
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
                    isSelected = { picker.selectionOf(it) == Selection.All },
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
                        AppTriStateCheckbox(picker.selectionOf(category).toggleable(), onClick = null)
                    }
                },
            ) { type ->
                // A sport with only some of its sub-sports selected shows the mixed mark, as a category does.
                AppTriStateCheckbox(picker.selectionOf(type).toggleable(), onClick = null)
            }
        }
    }
}

private fun Selection.toggleable(): ToggleableState =
    when (this) {
        Selection.All -> ToggleableState.On
        Selection.Partial -> ToggleableState.Indeterminate
        Selection.None -> ToggleableState.Off
    }
