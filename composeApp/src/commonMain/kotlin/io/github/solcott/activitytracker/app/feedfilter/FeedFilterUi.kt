package io.github.solcott.activitytracker.app.feedfilter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.calf.ui.datepicker.DateBounds
import com.slack.circuit.subcircuit.SubCircuitInject
import com.slack.circuit.subcircuit.SubUi
import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.app.ui.ActivityTypeIcon
import io.github.solcott.activitytracker.app.ui.activityTypeLabel
import io.github.solcott.activitytracker.app.ui.adaptive.AppBottomSheet
import io.github.solcott.activitytracker.app.ui.adaptive.AppCheckbox
import io.github.solcott.activitytracker.app.ui.adaptive.AppDatePickerDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppFilterChip
import io.github.solcott.activitytracker.app.ui.adaptive.AppFilterChipIconSize
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppSheetHeader
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppSheetState
import io.github.solcott.activitytracker.app.ui.sportCategoryLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_all_types
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_any_time
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_apply
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_category_some
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_clear
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_custom
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_custom_range
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_last_days
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_this_year
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_types_more
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_types_title
import io.github.solcott.activitytracker.composeApp.resources.sport_picker_quick
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import kotlin.time.Clock
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
    val today = remember { Clock.System.now().toEpochMilliseconds() }
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.sport_picker_quick), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                picker.quickTypes.forEach { type ->
                    AppFilterChip(
                        selected = picker.draft.covers(type),
                        onClick = { eventSink(FeedFilterEvent.ToggleType(type)) },
                        label = { Text(activityTypeLabel(type)) },
                        leadingIcon = { ActivityTypeIcon(type, Modifier.size(AppFilterChipIconSize)) },
                    )
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            picker.categories.forEach { group ->
                stickyHeader(key = group.category.name) { CategoryHeader(group, eventSink) }
                items(group.types, key = { "${group.category.name}/${it.sport}/${it.subSport}" }) { type ->
                    val nested = type.subSport != null && ActivityType(type.sport) in group.types
                    ListItem(
                        headlineContent = { Text(activityTypeLabel(type)) },
                        leadingContent = { ActivityTypeIcon(type, Modifier.padding(start = if (nested) 24.dp else 0.dp)) },
                        trailingContent = { AppCheckbox(checked = picker.draft.covers(type), onCheckedChange = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { eventSink(FeedFilterEvent.ToggleType(type)) }.handCursor(),
                    )
                }
            }
        }
    }
}

/** A category with a checkbox for all of it; "Some" marks a partly selected one. */
@Composable
private fun CategoryHeader(group: CategorySelection, eventSink: (FeedFilterEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable { eventSink(FeedFilterEvent.ToggleCategory(group.category)) }
            .handCursor()
            .padding(16.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            sportCategoryLabel(group.category),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (group.selection == Selection.Partial) {
            Text(stringResource(Res.string.feed_filter_category_some), style = MaterialTheme.typography.labelMedium)
        }
        AppCheckbox(checked = group.selection == Selection.All, onCheckedChange = null, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Whether [type] is selected, directly or through its bare sport (as the presenter's selection rules count it). */
private fun Set<ActivityType>.covers(type: ActivityType): Boolean =
    type in this || (type.subSport != null && ActivityType(type.sport) in this)

private const val MILLIS_PER_DAY = 86_400_000L

/** UTC midnight of this date, as the date picker takes it. */
private fun LocalDate.toPickerMillis(): Long = toEpochDays() * MILLIS_PER_DAY
