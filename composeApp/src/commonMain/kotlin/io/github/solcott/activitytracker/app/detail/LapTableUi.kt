package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.lap_ascent
import io.github.solcott.activitytracker.composeApp.resources.lap_cadence
import io.github.solcott.activitytracker.composeApp.resources.lap_distance
import io.github.solcott.activitytracker.composeApp.resources.lap_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.lap_number
import io.github.solcott.activitytracker.composeApp.resources.lap_power
import io.github.solcott.activitytracker.composeApp.resources.lap_row_description
import io.github.solcott.activitytracker.composeApp.resources.lap_speed
import io.github.solcott.activitytracker.composeApp.resources.lap_time
import io.github.solcott.activitytracker.composeApp.resources.unit_bpm
import io.github.solcott.activitytracker.composeApp.resources.unit_distance_km
import io.github.solcott.activitytracker.composeApp.resources.unit_distance_mi
import io.github.solcott.activitytracker.composeApp.resources.unit_elevation_ft
import io.github.solcott.activitytracker.composeApp.resources.unit_elevation_m
import io.github.solcott.activitytracker.composeApp.resources.unit_rpm
import io.github.solcott.activitytracker.composeApp.resources.unit_speed_kmh
import io.github.solcott.activitytracker.composeApp.resources.unit_speed_mph
import io.github.solcott.activitytracker.composeApp.resources.unit_watts
import io.github.solcott.activitytracker.composeApp.resources.value_missing
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatDecimal
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/** Each lap's time, distance, speed and effort, a row per lap, read by a screen reader as one sentence each. */
@Composable
internal fun LapTable(laps: ImmutableList<LapRow>) {
    val columns = LapColumns(laps)
    val headers = lapHeaders(columns)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LapCells(headers.toImmutableList(), header = true)
        for (lap in laps) {
            val cells = lapCells(lap, columns)
            val spoken =
                stringResource(
                    Res.string.lap_row_description,
                    lap.number,
                    headers.drop(1).zip(cells.drop(1)).joinToString(", ") { (h, v) -> "$h $v" },
                )
            LapCells(cells.toImmutableList(), header = false, Modifier.clearAndSetSemantics { contentDescription = spoken })
        }
    }
}

/** The optional columns any lap fills; cadence only when there's no power, which says more. */
private class LapColumns(laps: List<LapRow>) {
    val power = laps.any { it.avgPower != null }
    val heartRate = laps.any { it.avgHeartRate != null }
    val ascent = laps.any { it.ascent != null }
    val cadence = !power && laps.any { it.avgCadence != null }
}

@Composable
private fun lapHeaders(columns: LapColumns): List<String> =
    listOfNotNull(
        stringResource(Res.string.lap_number),
        stringResource(Res.string.lap_time),
        stringResource(Res.string.lap_distance),
        stringResource(Res.string.lap_speed),
        stringResource(Res.string.lap_heart_rate).takeIf { columns.heartRate },
        stringResource(Res.string.lap_power).takeIf { columns.power },
        stringResource(Res.string.lap_cadence).takeIf { columns.cadence },
        stringResource(Res.string.lap_ascent).takeIf { columns.ascent },
    )

@Composable
private fun lapCells(lap: LapRow, columns: LapColumns): List<String> {
    val metric = LocalUnitSystem.current == UnitSystem.Metric
    val distanceUnit = if (metric) Res.string.unit_distance_km else Res.string.unit_distance_mi
    val speedUnit = if (metric) Res.string.unit_speed_kmh else Res.string.unit_speed_mph
    val heightUnit = if (metric) Res.string.unit_elevation_m else Res.string.unit_elevation_ft
    return listOfNotNull(
        "${lap.number}",
        Format.duration((lap.movingSeconds ?: lap.elapsedSeconds).toLong()),
        cell(lap.distance) { stringResource(distanceUnit, formatDecimal(it, 2)) },
        cell(lap.speed) { stringResource(speedUnit, formatDecimal(it, 1)) },
        cell(lap.avgHeartRate, columns.heartRate) { stringResource(Res.string.unit_bpm, it) },
        cell(lap.avgPower, columns.power) { stringResource(Res.string.unit_watts, it) },
        cell(lap.avgCadence, columns.cadence) { stringResource(Res.string.unit_rpm, it) },
        cell(lap.ascent, columns.ascent) { stringResource(heightUnit, formatDecimal(it, 0)) },
    )
}

/** [value] as [text] puts it, a dash when the lap has none, or null when the column isn't [shown]. */
@Composable
private fun <T : Any> cell(value: T?, shown: Boolean = true, text: @Composable (T) -> String): String? =
    if (!shown) null else value?.let { text(it) } ?: stringResource(Res.string.value_missing)

@Composable
private fun LapCells(cells: ImmutableList<String>, header: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.forEachIndexed { i, cell ->
            Text(
                cell,
                // The lap number is narrow; the rest share the width.
                if (i == 0) Modifier.width(LAP_NUMBER_WIDTH) else Modifier.weight(1f),
                style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

private val LAP_NUMBER_WIDTH = 32.dp
