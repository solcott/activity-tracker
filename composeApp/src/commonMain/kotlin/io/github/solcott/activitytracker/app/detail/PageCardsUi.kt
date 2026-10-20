package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.chart_pace
import io.github.solcott.activitytracker.composeApp.resources.detail_avg
import io.github.solcott.activitytracker.composeApp.resources.detail_max
import io.github.solcott.activitytracker.composeApp.resources.detail_min
import io.github.solcott.activitytracker.composeApp.resources.page_all_stats
import io.github.solcott.activitytracker.composeApp.resources.page_avg_max_heading
import io.github.solcott.activitytracker.composeApp.resources.page_training_load
import io.github.solcott.activitytracker.composeApp.resources.value_missing
import io.github.solcott.activitytracker.lexicon.app.MemberView
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * One metric's card, always open: its title, its headline figures, its chart with the average dashed and the elevation behind it, its other
 * figures as rows, then its zones, power curve, gears or table. [onSetThresholds] offers the owner the zones screen when they have none.
 */
@Composable
internal fun MetricCardUi(
    card: MetricCard,
    charts: ActivityCharts,
    modifier: Modifier = Modifier,
    missingZones: Boolean = false,
    onSetThresholds: (() -> Unit)? = null,
    elevationBehind: Boolean = true,
    /** On a combined activity, the members the card's data came from, named in its caption. */
    sources: ImmutableList<MemberView>? = null,
) {
    AppCard(modifier) {
        Column(Modifier.padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
            CardTitle(if (card.pace != null) stringResource(Res.string.chart_pace) else sectionTitle(card.section), card.mark)
            sources?.let { ChartSourceCaption(it) }
            if (card.headline.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(ROW_GAP)) {
                    card.headline.forEach { FigureBlock(it, MaterialTheme.typography.titleLarge, Modifier.weight(1f)) }
                }
            }
            CardCharts(card, charts, missingZones, onSetThresholds, elevationBehind)
            if (card.rows.isNotEmpty()) Rows(card.rows)
        }
    }
}

@Composable
private fun Rows(rows: ImmutableList<Figure>) {
    Column {
        rows.forEachIndexed { i, figure ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FigureRow(figure)
        }
    }
}

/** Training load (FTP, IF, TSS, training effect and HRV), for a viewer served it; nothing otherwise. */
@Composable
internal fun TrainingLoadCard(figures: ImmutableList<Figure>, modifier: Modifier = Modifier) {
    if (figures.isEmpty()) return
    AppCard(modifier) {
        Column(Modifier.padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardTitle(stringResource(Res.string.page_training_load), mark = null)
            Rows(figures)
        }
    }
}

/** Every figure, a group at a time under its name, in [columns] side by side. */
@Composable
internal fun StatsList(groups: ImmutableList<StatsGroup>, modifier: Modifier = Modifier, columns: Int = 1) {
    if (groups.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
        Text(
            stringResource(Res.string.page_all_stats),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        groups.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
                row.forEach { StatsGroupCard(it, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StatsGroupCard(group: StatsGroup, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            groupTitle(group.kind),
            Modifier.padding(horizontal = CARD_PADDING).semantics { heading() },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppCard { Column(Modifier.padding(horizontal = CARD_PADDING, vertical = 4.dp)) { Rows(group.rows) } }
    }
}

/**
 * A row per metric with an average or maximum, and columns for each. A Min column joins them when a row has a minimum (temperature); the
 * other rows leave it empty, as their minimum is a stop rather than missing.
 */
@Composable
internal fun AvgMaxTable(rows: ImmutableList<AvgMaxRow>, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) return
    val showsMin = rows.any { it.min != null }
    AppCard(modifier) {
        Column(Modifier.padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardTitle(stringResource(Res.string.page_avg_max_heading), mark = null)
            AvgMaxLine(
                null,
                stringResource(Res.string.detail_avg),
                stringResource(Res.string.detail_min).takeIf { showsMin },
                stringResource(Res.string.detail_max),
                MaterialTheme.typography.labelMedium,
            )
            val missing = stringResource(Res.string.value_missing)
            rows.forEach { row ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AvgMaxLine(
                    avgMaxLabel(row.metric),
                    row.avg?.let { figureText(it) } ?: missing,
                    if (showsMin) row.min?.let { figureText(it) }.orEmpty() else null,
                    row.max?.let { figureText(it) } ?: missing,
                    MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** A table line; [min] is null when the table has no Min column. */
@Composable
private fun AvgMaxLine(label: String?, avg: String, min: String?, max: String, style: TextStyle) {
    // Three value columns share the width, so each narrows and the metric's name keeps room for "Temperature".
    val column = Modifier.widthIn(min = if (min != null) NARROW_TABLE_COLUMN else TABLE_COLUMN)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label.orEmpty(), Modifier.weight(1f), style = style)
        Text(avg, column, style = style, textAlign = TextAlign.End)
        if (min != null) Text(min, column, style = style, textAlign = TextAlign.End)
        Text(max, column, style = style, textAlign = TextAlign.End)
    }
}

private val TABLE_COLUMN = 72.dp
private val NARROW_TABLE_COLUMN = 64.dp
