package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.adaptive.edgeFades
import io.github.solcott.activitytracker.app.ui.string
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Each lap's time, distance, speed and effort, a row per lap, read by a screen reader as one sentence each, with its intensity when the
 * laps record one. A [ski] day's laps are its runs, numbered, with their descent, and its lift rides. Shows the first [maxRows].
 */
@Composable
internal fun LapTable(
    laps: ImmutableList<LapRow>,
    ski: Boolean = false,
    cadence: StreamKind = StreamKind.Cadence,
    maxRows: Int = Int.MAX_VALUE,
) {
    val table = LapTableTexts.of(laps, ski, LocalUnitSystem.current, cadence)
    val firstWidth = if (ski) RUN_NAME_WIDTH else LAP_NUMBER_WIDTH
    val wideColumn = if (table.wideFirst) 0 else -1
    TableFrame(table.headers.size, firstWidth, wideColumn) {
        TableCells(table.headers.map { it.string() }.toImmutableList(), header = true, firstWidth = firstWidth, wideColumn = wideColumn)
        table.rows.take(maxRows).forEach { row ->
            val spoken = row.spoken.string()
            TableCells(
                row.cells.map { it.string() }.toImmutableList(),
                header = false,
                Modifier.clearAndSetSemantics { contentDescription = spoken },
                firstWidth = firstWidth,
                wideColumn = wideColumn,
            )
        }
    }
}

/**
 * A table's rows, as wide as their space, or, when [columns] would be narrower than [MIN_CELL_WIDTH] each, at that width and scrolled
 * sideways, so a cell keeps its number on one line rather than breaking a word; an edge with more beyond it fades out, so the cut-off
 * column reads as more to scroll to.
 */
@Composable
internal fun TableFrame(columns: Int, firstWidth: Dp, wideColumn: Int, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val shares = (columns - 1) + if (wideColumn > 0) WIDE_SHARE - 1 else 0
        val first = if (wideColumn == 0) MIN_CELL_WIDTH * WIDE_SHARE else firstWidth
        val needed = first + MIN_CELL_WIDTH * shares + CELL_GAP * (columns - 1)
        val scroll = rememberScrollState()
        val rows =
            if (maxWidth >= needed) Modifier.fillMaxWidth()
            else Modifier.edgeFades(scroll, EDGE_FADE).horizontalScroll(scroll).width(needed)
        Column(rows, verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

/**
 * A row of a table: its first cell [firstWidth] wide (a number or short name), the rest sharing the width, the [wideColumn] (a name)
 * [WIDE_SHARE] times as much, each wrapping to a second line. A [wideColumn] of 0 makes the first cell that wide share instead of
 * [firstWidth].
 */
@Composable
internal fun TableCells(
    cells: ImmutableList<String>,
    header: Boolean,
    modifier: Modifier = Modifier,
    firstWidth: Dp = LAP_NUMBER_WIDTH,
    wideColumn: Int = -1,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
        cells.forEachIndexed { i, cell ->
            Text(
                cell,
                if (i == 0 && wideColumn != 0) Modifier.width(firstWidth)
                else Modifier.weight(if (i == wideColumn) WIDE_SHARE.toFloat() else 1f),
                style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
        }
    }
}

internal val LAP_NUMBER_WIDTH = 32.dp
internal val ROW_NAME_WIDTH = 88.dp
private val RUN_NAME_WIDTH = 56.dp
private val MIN_CELL_WIDTH = 64.dp
private val CELL_GAP = 8.dp

/** How many shares of the width a table's name column gets, so a lift's or exercise's name fits one line. */
private const val WIDE_SHARE = 3

/** How far a sideways table fades at an edge with more beyond it. */
private val EDGE_FADE = 32.dp
