package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.ActivityTypeIcon
import io.github.solcott.activitytracker.app.ui.activityTypeLabel
import io.github.solcott.activitytracker.app.ui.adaptive.AppBottomSheet
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppSheetState
import io.github.solcott.activitytracker.app.ui.rememberReadOnlyTextFieldState
import io.github.solcott.activitytracker.app.ui.sportCategoryLabel
import io.github.solcott.activitytracker.app.ui.sportLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.sport_picker_label
import io.github.solcott.activitytracker.composeApp.resources.sport_picker_no_matches
import io.github.solcott.activitytracker.composeApp.resources.sport_picker_quick
import io.github.solcott.activitytracker.composeApp.resources.sport_picker_search
import io.github.solcott.activitytracker.data.sport.SportCatalog
import io.github.solcott.activitytracker.data.sport.SportCategory
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The form's activity type: a field showing the chosen type, which opens a searchable sheet of every type, and chips for the chosen sport's
 * sub-sports. [outdoorOnly] leaves out types that can't use GPS, for recording.
 */
@Composable
fun ActivityTypePicker(
    selected: ActivityType,
    quickTypes: ImmutableList<ActivityType>,
    onSelect: (ActivityType) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outdoorOnly: Boolean = false,
) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            AppTextField(
                rememberReadOnlyTextFieldState(activityTypeLabel(selected)),
                readOnly = true,
                enabled = enabled,
                label = stringResource(Res.string.sport_picker_label),
                leadingIcon = { ActivityTypeIcon(selected) },
                trailingIcon = { PlatformIcon(AppIcon.DropDown, null) },
                modifier = Modifier.fillMaxWidth(),
            )
            // A read-only field doesn't report clicks, so a transparent layer on top opens the sheet.
            Box(Modifier.matchParentSize().clickable(enabled = enabled, role = Role.DropdownList) { sheetOpen = true })
        }
        SubSportChips(selected, onSelect, enabled, outdoorOnly)
    }
    if (sheetOpen) {
        ActivityTypeSheet(selected, quickTypes, outdoorOnly, onSelect = onSelect, onDismiss = { sheetOpen = false })
    }
}

/** The chosen sport's generic form and its sub-sports. Nothing when the sport has none. */
@Composable
private fun SubSportChips(selected: ActivityType, onSelect: (ActivityType) -> Unit, enabled: Boolean, outdoorOnly: Boolean) {
    val types =
        SportCatalog.subSports(selected.sport)
            .map { ActivityType(selected.sport, it.subSport) }
            .filterNot { outdoorOnly && SportCatalog.isIndoor(it) }
    if (types.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf(ActivityType(selected.sport)) + types).forEach { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onSelect(type) },
                enabled = enabled,
                label = { Text(if (type.subSport == null) sportLabel(type.sport) else activityTypeLabel(type)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityTypeSheet(
    selected: ActivityType,
    quickTypes: ImmutableList<ActivityType>,
    outdoorOnly: Boolean,
    onSelect: (ActivityType) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberAppSheetState()
    val scope = rememberCoroutineScope()
    val query = rememberTextFieldState()
    val choose: (ActivityType) -> Unit = { type ->
        onSelect(type)
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    val offered = { type: ActivityType -> !(outdoorOnly && SportCatalog.isIndoor(type)) }
    // Labels are resources, so search filters here rather than in the presenter. Tokens match too ("mtb" doesn't, "mountain" does).
    val labels = SportCatalog.categories.flatMap { it.types }.distinct().associateWith { activityTypeLabel(it) }
    val needle = query.text.trim().toString()
    val matches = { type: ActivityType ->
        needle.isEmpty() ||
            listOfNotNull(labels[type], labels[ActivityType(type.sport)], type.sport, type.subSport).any {
                it.contains(needle, ignoreCase = true)
            }
    }
    val groups: List<Pair<SportCategory, List<ActivityType>>> =
        SportCatalog.categories
            .map { group -> group.category to group.types.filter { offered(it) && matches(it) } }
            .filter { it.second.isNotEmpty() }

    AppBottomSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(
                query,
                placeholder = stringResource(Res.string.sport_picker_search),
                leadingIcon = { PlatformIcon(AppIcon.Search, null) },
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )
            val quick = quickTypes.filter(offered)
            if (needle.isEmpty() && quick.isNotEmpty()) {
                Text(stringResource(Res.string.sport_picker_quick), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.forEach { type ->
                        FilterChip(
                            selected = type == selected,
                            onClick = { choose(type) },
                            label = { Text(activityTypeLabel(type)) },
                            leadingIcon = { ActivityTypeIcon(type, Modifier.size(FilterChipDefaults.IconSize)) },
                        )
                    }
                }
            }
            if (groups.isEmpty()) Text(stringResource(Res.string.sport_picker_no_matches), style = MaterialTheme.typography.bodyMedium)
        }
        // Full height, so the sheet doesn't jump as the search narrows the list.
        LazyColumn(Modifier.fillMaxSize()) {
            groups.forEach { (category, types) ->
                stickyHeader(key = category.name) {
                    Text(
                        sportCategoryLabel(category),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp, 8.dp),
                    )
                }
                items(types, key = { "${category.name}/${it.sport}/${it.subSport}" }) { type ->
                    // A sub-sport sits under its sport, unless it's a shortcut listed without it (Indoor Ride under Gym).
                    val nested = type.subSport != null && ActivityType(type.sport) in types
                    ListItem(
                        headlineContent = { Text(labels[type] ?: activityTypeLabel(type)) },
                        leadingContent = { ActivityTypeIcon(type, Modifier.padding(start = if (nested) 24.dp else 0.dp)) },
                        trailingContent =
                            if (type == selected) ({ PlatformIcon(AppIcon.Check, null, tint = MaterialTheme.colorScheme.primary) })
                            else null,
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { choose(type) }.handCursor(),
                    )
                }
            }
        }
    }
}
