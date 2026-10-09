package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.Stat
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.chart_gear_value
import io.github.solcott.activitytracker.composeApp.resources.detail_avg
import io.github.solcott.activitytracker.composeApp.resources.detail_best_power
import io.github.solcott.activitytracker.composeApp.resources.detail_device
import io.github.solcott.activitytracker.composeApp.resources.detail_device_unknown
import io.github.solcott.activitytracker.composeApp.resources.detail_gain
import io.github.solcott.activitytracker.composeApp.resources.detail_highest
import io.github.solcott.activitytracker.composeApp.resources.detail_max
import io.github.solcott.activitytracker.composeApp.resources.detail_max_grade
import io.github.solcott.activitytracker.composeApp.resources.detail_most_used_gear
import io.github.solcott.activitytracker.composeApp.resources.detail_sensor_count
import io.github.solcott.activitytracker.composeApp.resources.detail_shifts
import io.github.solcott.activitytracker.composeApp.resources.stat_avg_depth
import io.github.solcott.activitytracker.composeApp.resources.stat_max_depth
import io.github.solcott.activitytracker.composeApp.resources.stat_normalized_power
import io.github.solcott.activitytracker.composeApp.resources.unit_duration_minutes
import io.github.solcott.activitytracker.composeApp.resources.unit_watts
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/** What a collapsed section shows: its headline figures as tiles. */
@Composable
internal fun SectionOverview(section: DetailSection, state: ActivityDetailState.Loaded, modifier: Modifier = Modifier) {
    when (section) {
        DetailSection.Device -> {
            val record = state.activity.record
            // A device's name runs long, so its tile takes what the sensor count leaves, and wraps rather than cut it off.
            Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                StatTile(
                    Stat(stringResource(Res.string.detail_device), record.device ?: stringResource(Res.string.detail_device_unknown)),
                    Modifier.weight(1f).fillMaxHeight(),
                    wrap = true,
                )
                StatTile(Stat(stringResource(Res.string.detail_sensor_count), "${record.sensors.orEmpty().size}"), Modifier.fillMaxHeight())
            }
        }

        else -> StatTiles(chartOverview(section, state), modifier)
    }
}

/** Labelled figures as tiles, as many to a row as fit at [minWidth] each, and at least two. */
@Composable
internal fun StatTiles(stats: ImmutableList<Stat>, modifier: Modifier = Modifier, minWidth: Dp = TILE_WIDTH) {
    if (stats.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = (maxWidth / minWidth).toInt().coerceAtLeast(2)
        Column(verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
            stats.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                    row.forEach { StatTile(it, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun StatTile(stat: Stat, modifier: Modifier = Modifier, wrap: Boolean = false) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            stat.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stat.value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = if (wrap) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A collapsed chart section's headline figures: average and maximum, or what matters more for the section. */
@Composable
internal fun chartOverview(section: DetailSection, state: ActivityDetailState.Loaded): ImmutableList<Stat> {
    val charts = state.charts ?: return persistentListOf()
    val lines = charts.lines.associateBy { it.kind }
    val avg = stringResource(Res.string.detail_avg)
    val max = stringResource(Res.string.detail_max)
    val stats =
        when (section) {
            DetailSection.Power -> {
                val best = charts.powerCurve.lastOrNull { it.seconds <= BEST_POWER_SECONDS && it.seconds >= SECONDS_PER_MINUTE }
                averageAndMax(lines[StreamKind.Power], avg, max).withNormalized(charts.normalizedPower) +
                    listOfNotNull(
                        best?.let {
                            Stat(
                                stringResource(
                                    Res.string.detail_best_power,
                                    stringResource(Res.string.unit_duration_minutes, it.seconds / SECONDS_PER_MINUTE),
                                ),
                                stringResource(Res.string.unit_watts, it.watts),
                            )
                        }
                    )
            }

            DetailSection.Elevation -> {
                val height = streamUnit(StreamKind.Elevation)
                val grade = streamUnit(StreamKind.Grade)
                listOfNotNull(
                    state.activity.record.elevationGain?.let {
                        Stat(stringResource(Res.string.detail_gain), Format.elevation(it.toDouble()))
                    },
                    lines[StreamKind.Elevation]?.max?.let {
                        Stat(stringResource(Res.string.detail_highest), height.text(height.convert(it)))
                    },
                    lines[StreamKind.Grade]?.max?.let { Stat(stringResource(Res.string.detail_max_grade), grade.text(it)) },
                )
            }

            DetailSection.Depth -> {
                averageAndMax(lines[StreamKind.Depth], stringResource(Res.string.stat_avg_depth), stringResource(Res.string.stat_max_depth))
                    .reversed()
            }

            DetailSection.Gears -> gearOverview(charts.gears)

            else -> {
                val kind = StreamKind.entries.firstOrNull { it.name == section.name }
                averageAndMax(kind?.let { lines[it] }, avg, max)
            }
        }
    return stats.toImmutableList()
}

@Composable
private fun averageAndMax(series: LineSeries?, avgLabel: String, maxLabel: String): List<Stat> {
    series ?: return emptyList()
    val unit = streamUnit(series.kind)
    return listOfNotNull(
        series.average?.let { Stat(avgLabel, unit.text(unit.convert(it))) },
        series.max?.let { Stat(maxLabel, unit.text(unit.convert(it))) },
    )
}

/** Normalized power after the average, where it reads as the harder-weighted average. */
@Composable
private fun List<Stat>.withNormalized(watts: Int?): List<Stat> {
    watts ?: return this
    val normalized = Stat(stringResource(Res.string.stat_normalized_power), stringResource(Res.string.unit_watts, watts))
    return take(1) + normalized + drop(1)
}

/** How often the rider shifted, and the rear gear they spent longest in. */
@Composable
private fun gearOverview(gears: ImmutableList<GearPoint>): List<Stat> {
    val shifts = gears.zipWithNext().count { (a, b) -> a.front != b.front || a.rear != b.rear }
    // Each gear holds until the next shift; the last one's end isn't known, so it doesn't count.
    val mostUsed =
        gears
            .zipWithNext()
            .filter { it.first.rear != null }
            .groupBy { it.first.rear }
            .maxByOrNull { (_, spans) -> spans.sumOf { (a, b) -> b.seconds - a.seconds } }
            ?.key
    return listOfNotNull(
        Stat(stringResource(Res.string.detail_shifts), "$shifts"),
        mostUsed?.let { Stat(stringResource(Res.string.detail_most_used_gear), stringResource(Res.string.chart_gear_value, it)) },
    )
}

private val TILE_GAP = 8.dp
private val TILE_WIDTH = 96.dp
private const val BEST_POWER_SECONDS = 1200
private const val SECONDS_PER_MINUTE = 60
