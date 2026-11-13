package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.chart.LineChartUi
import io.github.solcott.activitytracker.app.ui.chart.ZoneBar
import io.github.solcott.activitytracker.app.ui.chart.ZoneBars
import io.github.solcott.activitytracker.app.ui.string
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * [card]'s charts in full, as the native card draws them ([CardLines.of]): its lines, the power curve and the gears, each with its header
 * where it has one, then zones (or how to get them, [onSetThresholds], when the owner has none) after heart rate and power, and its table.
 * Elevation sits faintly behind the effort lines at every width, so effort reads against the climbs; a chart without an elevation series
 * draws none.
 */
@Composable
internal fun CardCharts(
    card: MetricCard,
    charts: ActivityCharts,
    missingZones: Boolean,
    onSetThresholds: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val units = LocalUnitSystem.current
    val lines = remember(card, charts, units) { CardLines.of(card, charts, units) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CHART_GAP)) {
        for (line in lines) CardLineChart(line)
        CardExtras(card, charts, missingZones, onSetThresholds)
    }
}

/** A chart of a card, under its title and detail when it has either. */
@Composable
private fun CardLineChart(line: CardLine) {
    Column(verticalArrangement = Arrangement.spacedBy(HEADER_GAP)) {
        if (line.title != null || line.detail != null) ChartHeader(line.title?.string(), line.detail?.string())
        LineChartUi(line.chart)
    }
}

/** What follows [card]'s charts: zones (or how to get them), or its table. */
@Composable
private fun CardExtras(card: MetricCard, charts: ActivityCharts, missingZones: Boolean, onSetThresholds: (() -> Unit)?) {
    when (card.section) {
        DetailSection.HeartRate,
        DetailSection.Power -> {
            val zones = card.zones
            if (zones != null) Zones(card.section, zones) else if (missingZones && onSetThresholds != null) MissingZones(onSetThresholds)
        }

        DetailSection.Laps -> LapTable(charts.laps, ski = charts.skiRuns != null)

        DetailSection.Legs -> LegTable(charts.legs)

        DetailSection.Lengths -> LengthTable(charts.lengths)

        DetailSection.Sets -> SetTable(charts.sets)

        else -> {}
    }
}

@Composable
private fun MissingZones(onSetThresholds: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            ZoneRows.missing.string(),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppTextButton(onClick = onSetThresholds) { Text(ZoneRows.setThresholds.string()) }
    }
}

/** A chart's [title], when the section's own doesn't name it, and its [detail] at the end. */
@Composable
private fun ChartHeader(title: String?, detail: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun Zones(section: DetailSection, zones: ImmutableList<ZoneTime>) {
    val bars =
        ZoneRows.of(zones, section).map { row ->
            ZoneBar(
                title = row.title.string(),
                range = row.range.string(),
                time = row.time.string(),
                percent = row.percent.string(),
                spoken = row.spoken.string(),
                fraction = row.fraction.toFloat(),
            )
        }
    Column(verticalArrangement = Arrangement.spacedBy(HEADER_GAP)) {
        ChartHeader(ZoneRows.heading(section).string(), null)
        ZoneBars(bars.toImmutableList())
    }
}

private val CHART_GAP = 24.dp
private val HEADER_GAP = 8.dp
