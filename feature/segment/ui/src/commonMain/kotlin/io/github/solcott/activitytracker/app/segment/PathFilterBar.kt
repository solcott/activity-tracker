package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.SearchField
import io.github.solcott.activitytracker.app.ui.TestTags
import io.github.solcott.activitytracker.app.ui.adaptive.AppFilterChip
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.string
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The Trail page's search and filters (segments/path-filters): the search field, then one row of chips scrolled sideways: the sport chips,
 * the difficulty, direction and length menus, and the sort menu. Each shows only when the state offers it.
 */
@Composable
internal fun PathFilterBar(state: PathState, modifier: Modifier = Modifier) {
    val sports = state.sports
    if (!state.searchable && sports.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.searchable) {
            SearchField(state.query, PathTexts.search.string(), Modifier.fillMaxWidth().testTag(TestTags.PATH_SEARCH))
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (sports.isNotEmpty()) {
                AppFilterChip(
                    selected = state.sport == null,
                    onClick = { state.eventSink(state.sportEvent(null)) },
                    label = { Text(PathTexts.sportChoice(null).string()) },
                )
                for (sport in sports) {
                    AppFilterChip(
                        selected = state.sport == sport,
                        onClick = { state.eventSink(state.sportEvent(sport)) },
                        label = { Text(PathTexts.sportChoice(sport).string()) },
                    )
                }
            }
            if (state.searchable) {
                if (sports.isNotEmpty()) VerticalDivider(Modifier.height(24.dp))
                val levels = state.levels
                if (levels.isNotEmpty()) {
                    MenuChip(
                        state.difficultyLabel.string(),
                        description = PathTexts.filterDescription(PathTexts.difficulty, state.difficultyLabel).string(),
                        selected = state.filter.levels.isNotEmpty(),
                        items =
                            levels
                                .map { option ->
                                    AppMenuItem(PathTexts.level(option).string(), checked = state.isChosen(option)) {
                                        state.eventSink(state.levelEvent(option))
                                    }
                                }
                                .toImmutableList(),
                    )
                }
                val ways = state.ways
                if (ways.isNotEmpty()) {
                    MenuChip(
                        state.directionLabel.string(),
                        description = PathTexts.filterDescription(PathTexts.direction, state.directionLabel).string(),
                        selected = state.filter.ways.isNotEmpty(),
                        items =
                            ways
                                .map { way ->
                                    AppMenuItem(PathTexts.way(way).string(), checked = state.isChosen(way)) {
                                        state.eventSink(state.wayEvent(way))
                                    }
                                }
                                .toImmutableList(),
                    )
                }
                val lengths = state.lengths
                if (lengths.isNotEmpty()) {
                    MenuChip(
                        state.lengthLabel.string(),
                        description = PathTexts.filterDescription(PathTexts.length, state.lengthLabel).string(),
                        selected = state.filter.lengths.isNotEmpty(),
                        items =
                            lengths
                                .map { option ->
                                    AppMenuItem(state.lengthText(option).string(), checked = state.isChosen(option)) {
                                        state.eventSink(state.lengthEvent(option))
                                    }
                                }
                                .toImmutableList(),
                    )
                }
                MenuChip(
                    PathTexts.sort(state.sortChoice).string(),
                    description = state.sortLabel.string(),
                    selected = false,
                    icon = AppIcon.Sort,
                    items =
                        state.sorts
                            .map { option ->
                                AppMenuItem(PathTexts.sort(option).string(), checked = state.isChosen(option)) {
                                    state.eventSink(state.sortEvent(option))
                                }
                            }
                            .toImmutableList(),
                )
            }
        }
    }
}

/** A chip naming a filter, or what it is set to, that opens a menu of its choices, filled while any is chosen; read as [description]. */
@Composable
private fun MenuChip(
    label: String,
    description: String,
    selected: Boolean,
    items: ImmutableList<AppMenuItem>,
    icon: AppIcon? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppFilterChip(
            selected = selected,
            onClick = { open = true },
            modifier = Modifier.semantics { contentDescription = description },
            label = { Text(label) },
            leadingIcon = icon?.let { { PlatformIcon(it, null) } },
            trailingIcon = { PlatformIcon(AppIcon.ChevronDown, null) },
        )
        AppMenu(expanded = open, onDismiss = { open = false }, items = items, modifier = Modifier.padding(0.dp))
    }
}
