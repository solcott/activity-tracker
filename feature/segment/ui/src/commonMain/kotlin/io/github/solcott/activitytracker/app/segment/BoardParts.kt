package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.ui.BoardChoiceChip
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem

/**
 * A segment board's type (segments/segment-leaderboards) and its age, weight and gender brackets (segments/leaderboard-brackets) as chips,
 * in the caller's flowing row: the type a menu of its options when it offers several, else its label, and nothing without one. What each
 * offers and says is `SegmentBoardTexts`' and [fields]', and each choice goes to [fields]' sink.
 */
@Suppress("MultipleEmitters") // Chips that flow in the caller's row, beside its own.
@Composable
internal fun SegmentBoardChips(fields: BoardFilterFields) {
    val send = fields.eventSink
    fields.typeChoice?.let { BoardChoiceChip(it, onSelect = { board -> board?.let { send(BoardFilterEvent.BoardChanged(it)) } }) }
    BoardChoiceChip(fields.ageGroupChoice, onSelect = { send(BoardFilterEvent.AgeGroupChanged(it)) })
    BoardChoiceChip(fields.weightClassChoice(LocalUnitSystem.current), onSelect = { send(BoardFilterEvent.WeightClassChanged(it)) })
    BoardChoiceChip(fields.genderChoice, onSelect = { send(BoardFilterEvent.GenderChanged(it)) })
}
