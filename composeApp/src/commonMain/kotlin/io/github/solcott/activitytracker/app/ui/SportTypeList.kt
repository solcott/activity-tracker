package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppFilterChip
import io.github.solcott.activitytracker.app.ui.adaptive.AppFilterChipIconSize
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.detail_section_collapsed
import io.github.solcott.activitytracker.composeApp.resources.detail_section_expanded
import io.github.solcott.activitytracker.composeApp.resources.type_picker_toggle_sport
import io.github.solcott.activitytracker.data.sport.SportCategory
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * The type pickers' list: a sticky header per category, then a row per type, with a sub-sport indented under its sport. A sport with
 * sub-sports under it collapses: they show only while its token is in [expanded] or [expandAll] is set, and a chevron before the row's
 * [trailing] content toggles it through [onToggleExpanded]. A sub-sport listed without its sport (Indoor Ride under Gym) always shows.
 *
 * A collapsed sport names the sub-sport [hiddenChoice] returns for it under its label, so a single choice never hides without a trace. A
 * multi-select picker marks a partly selected sport with its own tri-state checkbox in [trailing] instead, collapsed or not.
 * [onCategoryClick] makes the header clickable, and [categoryTrailing] draws at its end.
 */
@Suppress("LongParameterList") // Slots, as a composable takes them; detekt only exempts @Composable functions.
fun LazyListScope.sportTypeList(
    groups: ImmutableList<Pair<SportCategory, ImmutableList<ActivityType>>>,
    expanded: Set<String>,
    onToggleExpanded: (sport: String) -> Unit,
    onClick: (ActivityType) -> Unit,
    expandAll: Boolean = false,
    onCategoryClick: ((SportCategory) -> Unit)? = null,
    categoryTrailing: @Composable RowScope.(SportCategory) -> Unit = {},
    hiddenChoice: (sport: ActivityType) -> ActivityType? = { null },
    trailing: @Composable (ActivityType) -> Unit,
) {
    groups.forEach { (category, types) ->
        stickyHeader(key = category.name) { CategoryHeader(category, onCategoryClick, categoryTrailing) }
        val nested = { type: ActivityType -> type.subSport != null && ActivityType(type.sport) in types }
        val collapsible = types.filter(nested).mapTo(mutableSetOf()) { it.sport }
        val visible = types.filter { !nested(it) || expandAll || it.sport in expanded }
        items(visible, key = { "${category.name}/${it.sport}/${it.subSport}" }) { type ->
            val chevron = !expandAll && type.subSport == null && type.sport in collapsible
            val isExpanded = type.sport in expanded
            val hidden = if (chevron && !isExpanded) hiddenChoice(type) else null
            ListItem(
                headlineContent = { Text(activityTypeLabel(type)) },
                supportingContent = hidden?.let { { Text(activityTypeLabel(it)) } },
                leadingContent = { ActivityTypeIcon(type, Modifier.padding(start = if (nested(type)) 24.dp else 0.dp)) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (chevron) ExpandButton(type.sport, isExpanded, onToggle = { onToggleExpanded(type.sport) })
                        trailing(type)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onClick(type) }.handCursor(),
            )
        }
    }
}

/** The expanded sports' tokens for [sportTypeList]: none at first, so a picker opens with every sport collapsed. */
@Composable
fun rememberExpandedSports(): MutableState<Set<String>> =
    rememberSaveable(stateSaver = listSaver(save = { it.toList() }, restore = { it.toSet() })) { mutableStateOf(emptySet()) }

/** The pickers' "Quick" row: a chip per type, selected per [isSelected]. */
@Composable
fun QuickTypeChips(
    types: ImmutableList<ActivityType>,
    isSelected: (ActivityType) -> Boolean,
    onClick: (ActivityType) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        types.forEach { type ->
            AppFilterChip(
                selected = isSelected(type),
                onClick = { onClick(type) },
                label = { Text(activityTypeLabel(type)) },
                leadingIcon = { ActivityTypeIcon(type, Modifier.size(AppFilterChipIconSize)) },
            )
        }
    }
}

@Composable
private fun CategoryHeader(
    category: SportCategory,
    onClick: ((SportCategory) -> Unit)?,
    trailing: @Composable RowScope.(SportCategory) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(if (onClick != null) Modifier.clickable { onClick(category) }.handCursor() else Modifier)
            .padding(16.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            sportCategoryLabel(category),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        trailing(category)
    }
}

/** Shows or hides [sport]'s sub-sports. Separate from the row's own click, which selects. */
@Composable
private fun ExpandButton(sport: String, expanded: Boolean, onToggle: () -> Unit) {
    val stateText = stringResource(if (expanded) Res.string.detail_section_expanded else Res.string.detail_section_collapsed)
    AppIconButton(
        AppIcon.Chevron,
        contentDescription = stringResource(Res.string.type_picker_toggle_sport, sportLabel(sport)),
        onClick = onToggle,
        modifier = Modifier.rotate(if (expanded) EXPANDED_ROTATION else 0f).semantics { stateDescription = stateText },
    )
}

private const val EXPANDED_ROTATION = 90f
