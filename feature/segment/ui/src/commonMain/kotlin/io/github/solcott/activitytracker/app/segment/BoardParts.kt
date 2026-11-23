package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.ui.BoardChipRow
import io.github.solcott.activitytracker.app.ui.BoardChoiceChip
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem

/**
 * A segment board's choices as one row of chips under the bar (segments/segment-leaderboards, segments/segment-mayors): the date range, the
 * scope, the type when there are several to choose, then the age group, weight class and gender, each its own chip naming its choice and
 * marked while it narrows the board. What each offers and says is `SegmentBoardTexts`' and [fields]', and each choice goes to [fields]'
 * sink.
 */
@Composable
internal fun SegmentBoardChipRow(fields: BoardFilterFields) {
    val send = fields.eventSink
    BoardChipRow {
        BoardChoiceChip(fields.periodChoice, onSelect = { it?.let { period -> send(BoardFilterEvent.PeriodChanged(period)) } })
        BoardChoiceChip(fields.scopeChoice, onSelect = { it?.let { scope -> send(BoardFilterEvent.ScopeChanged(scope)) } })
        fields.typeChoice?.let { BoardChoiceChip(it, onSelect = { board -> board?.let { send(BoardFilterEvent.BoardChanged(it)) } }) }
        BoardChoiceChip(fields.ageGroupChoice, onSelect = { send(BoardFilterEvent.AgeGroupChanged(it)) })
        BoardChoiceChip(fields.weightClassChoice(LocalUnitSystem.current), onSelect = { send(BoardFilterEvent.WeightClassChanged(it)) })
        BoardChoiceChip(fields.genderChoice, onSelect = { send(BoardFilterEvent.GenderChanged(it)) })
    }
}
