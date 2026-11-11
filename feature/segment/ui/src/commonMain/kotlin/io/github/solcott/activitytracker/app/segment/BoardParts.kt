package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.boards.BoardChoice
import io.github.solcott.activitytracker.app.ui.BoardChoiceChip

/**
 * A segment board's type (segments/segment-leaderboards) and its age, weight and gender brackets (segments/leaderboard-brackets) as chips,
 * in the caller's flowing row: the type a menu of [type]'s options when it offers several, else its label, and nothing without one. What
 * each offers and says is `SegmentBoardTexts`'.
 */
@Suppress("MultipleEmitters", "LongParameterList") // Chips that flow in the caller's row, beside its own; one choice and sink each.
@Composable
internal fun SegmentBoardChips(
    type: BoardChoice?,
    ageGroup: BoardChoice,
    weightClass: BoardChoice,
    gender: BoardChoice,
    onType: (String) -> Unit,
    onAgeGroup: (String?) -> Unit,
    onWeightClass: (String?) -> Unit,
    onGender: (String?) -> Unit,
) {
    type?.let { BoardChoiceChip(it, onSelect = { board -> board?.let(onType) }) }
    BoardChoiceChip(ageGroup, onAgeGroup)
    BoardChoiceChip(weightClass, onWeightClass)
    BoardChoiceChip(gender, onGender)
}
