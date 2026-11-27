package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import io.github.solcott.activitytracker.app.ui.adaptive.HighlightPlacement
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableMaxWidth
import io.github.solcott.activitytracker.app.ui.adaptive.highlight
import io.github.solcott.activitytracker.app.ui.adaptive.highlightInset
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_your_place
import io.github.solcott.activitytracker.composeApp.resources.mayors_mayor
import io.github.solcott.activitytracker.composeApp.resources.rank_board_you
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
 * One athlete's place on a board: [rank], as `RankBoardTexts.rank` writes it (in the accent colour when [accented]; a crown for a [crowned]
 * Mayor), avatar and name, [detail] under it and [note] under that, and [value] at the end in figures of equal width, kept whole while the
 * name wraps. The viewer's [own] row is tinted, its name heavier, and it says "You" to accessibility services.
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
    own: Boolean = false,
    note: String? = null,
    crowned: Boolean = false,
) {
    val name = athlete.shownName
    val you = stringResource(Res.string.rank_board_you)
    Row(
        modifier
            .fillMaxWidth()
            .highlight(placement = HighlightPlacement.Bleed)
            // The athlete's own place: a "you" marker, not a selection, so it keeps its own tint inside the highlight's shape.
            .then(if (own) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = OWN_ROW_TINT)) else Modifier)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { if (own) stateDescription = you }
            .highlightInset(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (crowned) {
            CrownMedallion(Modifier.width(RANK_WIDTH))
        } else {
            Text(
                rank,
                Modifier.width(RANK_WIDTH),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (accented) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                // Centred, so every rank and the Mayor's crown share one centre line.
                textAlign = TextAlign.Center,
            )
        }
        Avatar(athlete.avatar, name, 36.dp)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (own) FontWeight.SemiBold else null)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(
            value,
            Modifier.testTag(RankRowValueTag),
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * The Mayor's mark in a [RankRow]'s rank cell (segments/segment-mayors): a crown in the accent's container colour, said as "Mayor", at the
 * cell's centre like a rank number.
 */
@Composable
private fun CrownMedallion(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(28.dp).background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape).testTag(RankRowCrownTag),
            contentAlignment = Alignment.Center,
        ) {
            PlatformIcon(
                AppIcon.Crown,
                stringResource(Res.string.mayors_mayor),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                size = 18.dp,
            )
        }
    }
}

/** The width of a [RankRow]'s rank cell, so names line up whatever the rank. */
private val RANK_WIDTH = 36.dp

/** The test tag of a crowned [RankRow]'s medallion. */
const val RankRowCrownTag = "rank-row-crown"

/**
 * How strongly the viewer's own row is washed in the brand colour: lighter than `primaryContainer`, the avatar placeholder's circle, so the
 * circle still shows on it, as iOS tints its row.
 */
private const val OWN_ROW_TINT = 0.08f

/** The test tag of a [RankRow]'s value. */
const val RankRowValueTag = "rank-row-value"

/**
 * A board's choices as one row of chips under the screen's bar (segments/segment-leaderboards), scrolling sideways when they don't fit and
 * never wrapping, so the board's first row always starts at the same place. Give it to [ListScreen]'s `header`.
 */
@Composable
fun BoardChipRow(modifier: Modifier = Modifier, chips: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(BoardChipRowTag),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = chips,
    )
}

/** The test tag of a [BoardChipRow]. */
const val BoardChipRowTag = "board-chip-row"

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
 * A board's rows (ui/content-loading): [entries]' refresh status, each entry's [row] with a divider under it, then the next page's spinner
 * or failure, or [emptyText] when the board lists no one. The viewer's own place, when it isn't among them, goes in an [OwnPlaceBar].
 */
@Suppress("LongParameterList") // A paged board's state, its error and retry and the pieces a board fills in.
fun <E : Any> LazyListScope.rankBoardRows(
    entries: PagedState<E, *>,
    errorKind: UiError.Kind,
    onRetry: () -> Unit,
    emptyText: StringResource,
    key: (E) -> Any,
    row: @Composable (E) -> Unit,
) {
    pagedRows(entries, errorKind, onRetry, key, empty = null, row)
    pagedFooter(entries, errorKind, onRetry) {
        EmptyListText(stringResource(emptyText), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A board in brief on its place's page: [title], the [leader] and their [leaderValue], or [noneText] while it has none (nothing when
 * [noneText] is null too, a board that hasn't loaded), and [viewerText], the viewer's own place, when they're on it. Tapping it opens the
 * board. [stacked] puts the value under the name, for a card that shares its row with another.
 */
@Composable
@Suppress("LongParameterList") // A summary card's parts.
fun LeaderCard(
    title: String,
    leader: ProfileViewBasic?,
    leaderValue: String,
    noneText: String?,
    viewerText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
) {
    AppCard(onClick, modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            if (leader == null) {
                noneText?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                val name = leader.shownName
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(leader.avatar, name, 36.dp)
                    if (stacked) {
                        Column(Modifier.weight(1f)) {
                            Text(leaderValue, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(leaderValue, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }
            }
            viewerText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/**
 * The viewer's own place, fixed under a board's list while it isn't among the rows loaded: "Your place" over [row], their row, on the next
 * surface step with a hairline above it and no shadow. Nothing when there is no [row].
 */
@Composable
fun OwnPlaceBar(row: (@Composable () -> Unit)?, modifier: Modifier = Modifier) {
    row ?: return
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).testTag(OwnPlaceBarTag)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            Modifier.align(Alignment.CenterHorizontally)
                .widthIn(max = ReadableMaxWidth)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
        ) {
            Text(
                stringResource(Res.string.leaderboard_your_place),
                Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row()
        }
    }
}

/** The test tag of an [OwnPlaceBar]. */
const val OwnPlaceBarTag = "own-place-bar"
