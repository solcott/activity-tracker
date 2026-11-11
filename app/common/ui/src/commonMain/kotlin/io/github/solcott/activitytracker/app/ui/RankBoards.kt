package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.boards.BoardChoice
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppFilterChip
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppSegmentedControl
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_your_place
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.uistate.PagedState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Pieces of a ranked board (segments' leaderboards and Mayors, dive sites' Regulars) shared by the features that show one.

/** A filter chip labelled [label] that opens a menu of [items]. */
@Composable
fun MenuChip(label: String, selected: Boolean, items: ImmutableList<AppMenuItem>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        AppFilterChip(
            selected = selected,
            onClick = { open = true },
            label = { Text(label) },
            trailingIcon = { PlatformIcon(AppIcon.ChevronDown, null, size = 16.dp) },
        )
        AppMenu(expanded = open, onDismiss = { open = false }, items = items)
    }
}

/**
 * A board's [choice] as a [MenuChip] of its options, selected while the choice narrows the board; its label alone when there is nothing to
 * choose (segments/segment-leaderboards).
 */
@Composable
fun BoardChoiceChip(choice: BoardChoice, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    if (!choice.offersMenu) {
        Text(choice.label.string(), modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
        return
    }
    MenuChip(
        label = choice.label.string(),
        selected = choice.narrows,
        items =
            choice.options.map { AppMenuItem(it.label.string(), checked = choice.isChosen(it)) { onSelect(it.value) } }.toImmutableList(),
        modifier = modifier,
    )
}

/**
 * One athlete's place on a board: [rank], as `RankBoardTexts.rank` writes it (in the accent colour when [accented]), avatar and name,
 * [detail] under it, and [value].
 */
@Composable
@Suppress("LongParameterList") // A board row's parts.
fun RankRow(
    rank: String,
    accented: Boolean,
    athlete: ProfileViewBasic,
    detail: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = athlete.shownName
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).handCursor().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rank,
            Modifier.width(36.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (accented) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
        )
        Avatar(athlete.avatar, name, 36.dp)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

/** A board's [choice] as a segmented control of its options, such as Everyone / Following. */
@Composable
fun BoardSegments(choice: BoardChoice, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    AppSegmentedControl(
        choice.options.map { it.label.string() }.toImmutableList(),
        selected = choice.options.indexOfFirst(choice::isChosen).coerceAtLeast(0),
        onSelect = { onSelect(choice.options[it].value) },
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A board's rows (ui/content-loading): [entries]' refresh status, each entry's [row] with a divider under it, the viewer's [own] place when
 * it isn't among them ([ownPlace]), then the next page's spinner or failure, or [emptyText] when the board lists no one.
 */
@Suppress("LongParameterList") // A paged board's state, its error and retry, the viewer's place and the pieces a board fills in.
fun <E : Any> LazyListScope.rankBoardRows(
    entries: PagedState<E, *>,
    own: E?,
    errorKind: UiError.Kind,
    onRetry: () -> Unit,
    emptyText: StringResource,
    key: (E) -> Any,
    row: @Composable (E) -> Unit,
) {
    pagedRows(entries, errorKind, onRetry, key, empty = null, row)
    ownPlace(own, row)
    pagedFooter(entries, errorKind, onRetry) {
        EmptyListText(stringResource(emptyText), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A board in brief on its place's page: [title], the [leader] and their [leaderValue], or [noneText] while it has none, and [viewerText],
 * the viewer's own count, when they're on it. Tapping it opens the board.
 */
@Composable
@Suppress("LongParameterList") // A summary card's parts.
fun LeaderCard(
    title: String,
    leader: ProfileViewBasic?,
    leaderValue: String,
    noneText: String,
    viewerText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier.fillMaxWidth().clickable(onClick = onClick).handCursor()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            if (leader == null) {
                Text(noneText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val name = leader.shownName
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(leader.avatar, name, 36.dp)
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(leaderValue, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }
            viewerText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** The viewer's own place ([own]'s [row]) under a heading, below a board's entries; nothing when they're among them or not on it. */
fun <E : Any> LazyListScope.ownPlace(own: E?, row: @Composable (E) -> Unit) {
    own ?: return
    item(key = "own-place") {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text(stringResource(Res.string.leaderboard_your_place), style = MaterialTheme.typography.titleSmall)
            row(own)
        }
    }
}
