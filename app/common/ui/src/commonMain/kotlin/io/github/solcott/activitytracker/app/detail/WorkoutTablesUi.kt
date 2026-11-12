package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.string
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** A pool swim's lengths ([StructureTableTexts.lengths]). */
@Composable internal fun LengthTable(lengths: ImmutableList<LengthRow>) = StructureTable(StructureTableTexts.lengths(lengths))

/** A workout's sets ([StructureTableTexts.sets]). */
@Composable internal fun SetTable(sets: ImmutableList<SetRow>) = StructureTable(StructureTableTexts.sets(sets, LocalUnitSystem.current))

/** A multisport activity's legs ([StructureTableTexts.legs]), the sport's name given a wider first column. */
@Composable
internal fun LegTable(legs: ImmutableList<LegRow>) =
    StructureTable(StructureTableTexts.legs(legs, LocalUnitSystem.current), firstWidth = ROW_NAME_WIDTH)

/**
 * A header row, then each of [table]'s rows, each read by a screen reader as one sentence; its wide column (a name) gets twice the width.
 */
@Composable
private fun StructureTable(table: DetailTable, firstWidth: Dp = ROW_NUMBER_WIDTH) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TableCells(
            table.headers.map { it.string() }.toImmutableList(),
            header = true,
            firstWidth = firstWidth,
            wideColumn = table.wideColumn,
        )
        for (row in table.rows) {
            val spoken = row.spoken.string()
            TableCells(
                row.cells.map { it.string() }.toImmutableList(),
                header = false,
                Modifier.clearAndSetSemantics { contentDescription = spoken },
                firstWidth,
                table.wideColumn,
            )
        }
    }
}

/** A token as words: "single_arm_cable_row" is "Single arm cable row", "warmUp" is "Warm up". */
internal fun readableToken(token: String): String = LapTableTexts.readableToken(token)

private val ROW_NUMBER_WIDTH = 56.dp
