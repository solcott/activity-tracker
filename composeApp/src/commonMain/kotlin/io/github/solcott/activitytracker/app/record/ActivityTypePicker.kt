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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.activityTypeLabel
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
            OutlinedTextField(
                value = activityTypeLabel(selected),
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                label = { Text(stringResource(Res.string.sport_picker_label)) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
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
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val choose: (ActivityType) -> Unit = { type ->
        onSelect(type)
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    val offered = { type: ActivityType -> !(outdoorOnly && SportCatalog.isIndoor(type)) }
    // Labels are resources, so search filters here rather than in the presenter. Tokens match too ("mtb" doesn't, "mountain" does).
    val labels = SportCatalog.categories.flatMap { it.types }.distinct().associateWith { activityTypeLabel(it) }
    val needle = query.trim()
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(Res.string.sport_picker_search)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val quick = quickTypes.filter(offered)
            if (needle.isEmpty() && quick.isNotEmpty()) {
                Text(stringResource(Res.string.sport_picker_quick), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.forEach { type ->
                        FilterChip(selected = type == selected, onClick = { choose(type) }, label = { Text(activityTypeLabel(type)) })
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
                        headlineContent = {
                            Text(labels[type] ?: activityTypeLabel(type), Modifier.padding(start = if (nested) 24.dp else 0.dp))
                        },
                        trailingContent = if (type == selected) ({ Icon(Icons.Default.Check, null) }) else null,
                        modifier = Modifier.clickable { choose(type) },
                    )
                }
            }
        }
    }
}
