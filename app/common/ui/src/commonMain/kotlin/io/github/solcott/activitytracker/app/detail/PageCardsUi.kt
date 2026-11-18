package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.GridTrackSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.detail_avg
import io.github.solcott.activitytracker.composeApp.resources.detail_max
import io.github.solcott.activitytracker.composeApp.resources.detail_min
import io.github.solcott.activitytracker.composeApp.resources.page_all_stats
import io.github.solcott.activitytracker.composeApp.resources.page_avg_max_heading
import io.github.solcott.activitytracker.composeApp.resources.page_training_load
import io.github.solcott.activitytracker.lexicon.app.MemberView
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * One metric's card, always open: its title, its headline figures, its chart with the average dashed and the elevation behind it, its other
 * figures as rows, then its zones, power curve, gears or table. [onSetThresholds] offers the owner the zones screen when they have none.
 */
@Composable
fun MetricCardUi(
    card: MetricCard,
    charts: ActivityCharts,
    modifier: Modifier = Modifier,
    missingZones: Boolean = false,
    onSetThresholds: (() -> Unit)? = null,
    /** On a combined activity, the members the card's data came from, named in its caption. */
    sources: ImmutableList<MemberView>? = null,
) {
    AppCard(modifier) {
        Column(Modifier.padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
            CardTitle(FigureTexts.cardTitle(card).string(), card.mark)
            sources?.let { ChartSourceCaption(it) }
            if (card.headline.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(ROW_GAP)) {
                    card.headline.forEach { FigureBlock(it, MaterialTheme.typography.titleLarge, Modifier.weight(1f)) }
                }
            }
            CardCharts(card, charts, missingZones, onSetThresholds)
            if (card.rows.isNotEmpty()) Rows(card.rows)
        }
    }
}

/** [rows], a gear item's opening its page with [onOpenGear] when given. */
@Composable
private fun Rows(rows: ImmutableList<Figure>, onOpenGear: ((String) -> Unit)? = null) {
    Column {
        rows.forEachIndexed { i, figure ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val gear = (figure.value as? FigureValue.GearItem)?.uri
            if (gear != null && onOpenGear != null) FigureRow(figure, Modifier.clickable { onOpenGear(gear) }.handCursor())
            else FigureRow(figure)
        }
    }
}

/** Training load (FTP, IF, TSS, training effect and HRV), for a viewer served it; nothing otherwise. */
@Composable
fun TrainingLoadCard(figures: ImmutableList<Figure>, modifier: Modifier = Modifier) {
    if (figures.isEmpty()) return
    AppCard(modifier) {
        Column(Modifier.padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardTitle(stringResource(Res.string.page_training_load), mark = null)
            Rows(figures)
        }
    }
}

/** Every figure, a group at a time under its name, in a grid of [columns]. A gear item's row opens its page with [onOpenGear]. */
@Composable
fun StatsList(
    groups: ImmutableList<StatsGroup>,
    modifier: Modifier = Modifier,
    columns: Int = 1,
    onOpenGear: ((String) -> Unit)? = null,
) {
    if (groups.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
        Text(
            stringResource(Res.string.page_all_stats),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        Grid(
            config = {
                repeat(columns) { column(1.fr) }
                gap(ITEM_GAP)
            },
            Modifier.fillMaxWidth(),
        ) {
            groups.forEach { StatsGroupCard(it, Modifier.fillMaxWidth(), onOpenGear) }
        }
    }
}

@Composable
private fun StatsGroupCard(group: StatsGroup, modifier: Modifier = Modifier, onOpenGear: ((String) -> Unit)? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            groupTitle(group.kind),
            Modifier.padding(horizontal = CARD_PADDING).semantics { heading() },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppCard { Column(Modifier.padding(horizontal = CARD_PADDING, vertical = 4.dp)) { Rows(group.rows, onOpenGear) } }
    }
}

/**
 * A row per metric with an average or maximum, and columns for each. A Min column joins them when a row has a minimum (temperature); the
 * other rows leave it empty, as their minimum is a stop rather than missing.
 */
@Composable
fun AvgMaxTable(rows: ImmutableList<AvgMaxRow>, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) return
    val showsMin = FigureTexts.showsMin(rows)
    AppCard(modifier) {
        Column(Modifier.padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardTitle(stringResource(Res.string.page_avg_max_heading), mark = null)
            val valueColumns = if (showsMin) 3 else 2
            // The metric's name takes the free width and wraps; each value column is as wide as its widest value, so every row lines up.
            Grid(
                config = {
                    column(1.fr)
                    repeat(valueColumns) { column(GridTrackSize.Auto) }
                    columnGap(12.dp)
                    rowGap(6.dp)
                },
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
            ) {
                // Three value columns share the width, so each narrows and the metric's name keeps room for "Temperature".
                val value =
                    Modifier.widthIn(min = if (showsMin) NARROW_TABLE_COLUMN else TABLE_COLUMN).gridItem(alignment = Alignment.TopEnd)
                val heading = MaterialTheme.typography.labelMedium
                Text("", style = heading)
                Text(stringResource(Res.string.detail_avg), value, style = heading, textAlign = TextAlign.End)
                if (showsMin) Text(stringResource(Res.string.detail_min), value, style = heading, textAlign = TextAlign.End)
                Text(stringResource(Res.string.detail_max), value, style = heading, textAlign = TextAlign.End)
                val missing = StatText.missing.string()
                val body = MaterialTheme.typography.bodyMedium
                rows.forEach { row ->
                    HorizontalDivider(Modifier.gridItem(columnSpan = valueColumns + 1), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(avgMaxLabel(row.metric), style = body)
                    Text(row.avg?.let { figureText(it) } ?: missing, value, style = body, textAlign = TextAlign.End)
                    if (showsMin) Text(row.min?.let { figureText(it) }.orEmpty(), value, style = body, textAlign = TextAlign.End)
                    Text(row.max?.let { figureText(it) } ?: missing, value, style = body, textAlign = TextAlign.End)
                }
            }
        }
    }
}

private val TABLE_COLUMN = 72.dp
private val NARROW_TABLE_COLUMN = 64.dp
