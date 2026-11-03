package io.github.solcott.activitytracker.app.segment

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
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.ui.Avatar
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.adaptive.AppFilterChip
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.app.ui.sportLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_age_all
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_age_over
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_age_range
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_age_under
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_board_ebike
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_gender_all
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_gender_female
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_gender_male
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_gender_non_binary
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_weight_all
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_weight_over_kg
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_weight_over_lb
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_weight_range_kg
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_weight_range_lb
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_weight_under_kg
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_weight_under_lb
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_your_place
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.WeightConversions
import io.github.solcott.activitytracker.lexicon.app.LeaderboardAgeGroup
import io.github.solcott.activitytracker.lexicon.app.LeaderboardGender
import io.github.solcott.activitytracker.lexicon.app.LeaderboardWeightClass
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentBoard
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import org.jetbrains.compose.resources.stringResource

/** A filter chip labelled [label] that opens a menu of [items]. */
@Composable
internal fun MenuChip(label: String, selected: Boolean, items: ImmutableList<AppMenuItem>) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppFilterChip(
            selected = selected,
            onClick = { open = true },
            label = { Text(label) },
            trailingIcon = { PlatformIcon(AppIcon.ChevronDown, null, size = 16.dp) },
        )
        AppMenu(expanded = open, onDismiss = { open = false }, items = items)
    }
}

/** A [MenuChip] choosing one of [labels]' keys, always selected since one always applies; [selected]'s label, or itself when unlisted. */
@Composable
internal fun ChoiceChip(selected: String, labels: ImmutableMap<String, String>, onSelect: (String) -> Unit) {
    MenuChip(
        label = labels[selected] ?: selected,
        selected = true,
        items = labels.map { (value, label) -> AppMenuItem(label, checked = value == selected) { onSelect(value) } }.toImmutableList(),
    )
}

/**
 * The board's type (segments/segment-leaderboards): a chip with a menu of [boards] when there are two or more, else [board] as a plain
 * label; nothing when the segment has no type to show.
 */
@Composable
internal fun BoardTypeChip(board: String?, boards: ImmutableList<String>, onSelect: (String) -> Unit) {
    board ?: return
    if (boards.size < 2) {
        Text(boardLabel(board), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 8.dp))
        return
    }
    ChoiceChip(board, boards.associateWith { boardLabel(it) }.toImmutableMap(), onSelect)
}

/** A board type's label: its sport's name, or "E-bike" for [SegmentBoard.E_BIKE]. */
@Composable
private fun boardLabel(board: String): String =
    if (board == SegmentBoard.E_BIKE) stringResource(Res.string.leaderboard_board_ebike) else sportLabel(board)

/** The age, weight and gender [BracketChip]s (segments/leaderboard-brackets). */
@Suppress("MultipleEmitters") // Three chips that flow in the caller's row, beside its own.
@Composable
internal fun BracketChips(
    ageGroup: String?,
    weightClass: String?,
    gender: String?,
    onAgeGroup: (String?) -> Unit,
    onWeightClass: (String?) -> Unit,
    onGender: (String?) -> Unit,
) {
    BracketChip(
        all = stringResource(Res.string.leaderboard_age_all),
        selected = ageGroup,
        labels = LeaderboardAgeGroup.all.associateWith { ageLabel(it) }.toImmutableMap(),
        onSelect = onAgeGroup,
    )
    BracketChip(
        all = stringResource(Res.string.leaderboard_weight_all),
        selected = weightClass,
        labels = LeaderboardWeightClass.all.associateWith { weightLabel(it) }.toImmutableMap(),
        onSelect = onWeightClass,
    )
    BracketChip(
        all = stringResource(Res.string.leaderboard_gender_all),
        selected = gender,
        labels =
            persistentMapOf(
                LeaderboardGender.FEMALE to stringResource(Res.string.leaderboard_gender_female),
                LeaderboardGender.MALE to stringResource(Res.string.leaderboard_gender_male),
                LeaderboardGender.NON_BINARY to stringResource(Res.string.leaderboard_gender_non_binary),
            ),
        onSelect = onGender,
    )
}

/**
 * A chip choosing one bracket (segments/leaderboard-brackets) from a menu: [all] when [selected] is null, else its label from [labels],
 * which also lists the menu's choices in order after [all].
 */
@Composable
internal fun BracketChip(all: String, selected: String?, labels: ImmutableMap<String, String>, onSelect: (String?) -> Unit) {
    MenuChip(
        label = selected?.let { labels[it] } ?: all,
        selected = selected != null,
        items =
            (listOf(AppMenuItem(all, checked = selected == null) { onSelect(null) }) +
                    labels.map { (value, label) -> AppMenuItem(label, checked = value == selected) { onSelect(value) } })
                .toImmutableList(),
    )
}

/** An age group's label: `0-19` is "19 and under", `75+` "75 and over". */
@Composable
internal fun ageLabel(group: String): String {
    val (from, to) = boundsOf(group)
    return when {
        to == null -> stringResource(Res.string.leaderboard_age_over, from)
        from == 0 -> stringResource(Res.string.leaderboard_age_under, to)
        else -> stringResource(Res.string.leaderboard_age_range, from, to)
    }
}

/**
 * A weight class's label in the viewer's units. A class is whole kilograms, so in pounds each bound is its kilogram bound converted and
 * rounded, and a class ends a pound before the next one starts.
 */
@Composable
internal fun weightLabel(weightClass: String): String {
    val (from, to) = boundsOf(weightClass)
    val imperial = LocalUnitSystem.current == UnitSystem.Imperial
    fun bound(kg: Int): Int = if (imperial) (kg * WeightConversions.GRAMS_PER_KG / WeightConversions.GRAMS_PER_POUND).roundToInt() else kg
    return when {
        to == null ->
            stringResource(if (imperial) Res.string.leaderboard_weight_over_lb else Res.string.leaderboard_weight_over_kg, bound(from))
        from == 0 ->
            stringResource(if (imperial) Res.string.leaderboard_weight_under_lb else Res.string.leaderboard_weight_under_kg, bound(to + 1))
        else ->
            stringResource(
                if (imperial) Res.string.leaderboard_weight_range_lb else Res.string.leaderboard_weight_range_kg,
                bound(from),
                bound(to + 1) - 1,
            )
    }
}

/** A bracket token's bounds: `55-64` is 55 to 64, `75+` is 75 with no upper bound. */
private fun boundsOf(token: String): Pair<Int, Int?> =
    if (token.endsWith("+")) token.dropLast(1).toInt() to null else token.split('-').let { (from, to) -> from.toInt() to to.toInt() }

/** One athlete's place on a board: [rank] (in the accent colour when [accented]), avatar and name, [detail] under it, and [value]. */
@Composable
internal fun RankRow(rank: Int, accented: Boolean, athlete: ProfileViewBasic, detail: String, value: String, onClick: () -> Unit) {
    val name = athlete.shownName
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).handCursor().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rank.toString(),
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

/** The viewer's own place ([own]'s [row]) under a heading, below a board's entries; nothing when they're among them or not on it. */
internal fun <E : Any> LazyListScope.ownPlace(own: E?, row: @Composable (E) -> Unit) {
    own ?: return
    item(key = "own-place") {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text(stringResource(Res.string.leaderboard_your_place), style = MaterialTheme.typography.titleSmall)
            row(own)
        }
    }
}
