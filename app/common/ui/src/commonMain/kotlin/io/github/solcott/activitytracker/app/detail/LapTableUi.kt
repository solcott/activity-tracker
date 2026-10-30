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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.composeApp.resources.lap_ascent
import io.github.solcott.activitytracker.composeApp.resources.lap_cadence
import io.github.solcott.activitytracker.composeApp.resources.lap_descent
import io.github.solcott.activitytracker.composeApp.resources.lap_distance
import io.github.solcott.activitytracker.composeApp.resources.lap_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.lap_lift
import io.github.solcott.activitytracker.composeApp.resources.lap_number
import io.github.solcott.activitytracker.composeApp.resources.lap_power
import io.github.solcott.activitytracker.composeApp.resources.lap_run
import io.github.solcott.activitytracker.composeApp.resources.lap_run_number
import io.github.solcott.activitytracker.composeApp.resources.lap_speed
import io.github.solcott.activitytracker.composeApp.resources.lap_time
import io.github.solcott.activitytracker.composeApp.resources.lap_type
import io.github.solcott.activitytracker.composeApp.resources.table_row_description
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
import io.github.solcott.activitytracker.data.units.formatWhole
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * Each lap's time, distance, speed and effort, a row per lap, read by a screen reader as one sentence each, with its intensity when the
 * laps record one. A [ski] day's laps are its runs, numbered, with their descent, and its lift rides.
 */
@Composable
internal fun LapTable(laps: ImmutableList<LapRow>, ski: Boolean = false) {
    val columns = LapColumns(laps, ski)
    val headers = lapHeaders(columns)
    val names = lapNames(laps, ski)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TableCells(headers.toImmutableList(), header = true, firstWidth = columns.firstWidth)
        laps.forEachIndexed { i, lap ->
            val cells = listOf(names[i]) + lapCells(lap, columns)
            val spoken =
                spokenRow(
                    if (ski) names[i] else "${headers.first()} ${lap.number}",
                    headers.drop(1).toImmutableList(),
                    cells.drop(1).toImmutableList(),
                )
            TableCells(
                cells.toImmutableList(),
                header = false,
                Modifier.clearAndSetSemantics { contentDescription = spoken },
                firstWidth = columns.firstWidth,
            )
        }
    }
}

/** Each lap's name for its first cell: its number, or on a ski day "Run n" or "Lift". */
@Composable
private fun lapNames(laps: ImmutableList<LapRow>, ski: Boolean): List<String> {
    if (!ski) return laps.map { "${it.number}" }
    val lift = stringResource(Res.string.lap_lift)
    val runNumber = stringResource(Res.string.lap_run_number, "%s")
    val runs = laps.runningFold(0) { n, lap -> if (lap.isRest) n else n + 1 }.drop(1)
    return laps.mapIndexed { i, lap -> if (lap.isRest) lift else runNumber.replace("%s", "${runs[i]}") }
}

/** A table row as a screen reader says it: its [name], then each header and value. */
@Composable
internal fun spokenRow(name: String, headers: ImmutableList<String>, cells: ImmutableList<String>): String =
    stringResource(Res.string.table_row_description, name, headers.zip(cells).joinToString(", ") { (h, v) -> "$h $v" })

/** The optional columns any lap fills; cadence only when there's no power, which says more. */
private class LapColumns(laps: List<LapRow>, val ski: Boolean) {
    val intensity = !ski && laps.any { it.intensity != null }
    val power = laps.any { it.avgPower != null }
    val heartRate = laps.any { it.avgHeartRate != null }
    val ascent = !ski && laps.any { it.ascent != null }
    val descent = ski && laps.any { it.descent != null }
    val cadence = !power && laps.any { it.avgCadence != null }
    val firstWidth = if (ski) RUN_NAME_WIDTH else LAP_NUMBER_WIDTH
}

@Composable
private fun lapHeaders(columns: LapColumns): List<String> =
    listOfNotNull(
        stringResource(if (columns.ski) Res.string.lap_run else Res.string.lap_number),
        stringResource(Res.string.lap_type).takeIf { columns.intensity },
        stringResource(Res.string.lap_time),
        stringResource(Res.string.lap_distance),
        stringResource(Res.string.lap_speed),
        stringResource(Res.string.lap_heart_rate).takeIf { columns.heartRate },
        stringResource(Res.string.lap_power).takeIf { columns.power },
        stringResource(Res.string.lap_cadence).takeIf { columns.cadence },
        stringResource(Res.string.lap_ascent).takeIf { columns.ascent },
        stringResource(Res.string.lap_descent).takeIf { columns.descent },
    )

@Composable
private fun lapCells(lap: LapRow, columns: LapColumns): List<String> {
    val metric = LocalUnitSystem.current == UnitSystem.Metric
    val distanceUnit = if (metric) Res.string.unit_distance_km else Res.string.unit_distance_mi
    val speedUnit = if (metric) Res.string.unit_speed_kmh else Res.string.unit_speed_mph
    val heightUnit = if (metric) Res.string.unit_elevation_m else Res.string.unit_elevation_ft
    return listOfNotNull(
        cell(lap.intensity, columns.intensity) { intensityLabel(it) },
        Format.duration((lap.movingSeconds ?: lap.elapsedSeconds).toLong()),
        cell(lap.distance) { stringResource(distanceUnit, formatDecimal(it, 2)) },
        cell(lap.speed) { stringResource(speedUnit, formatDecimal(it, 1)) },
        cell(lap.avgHeartRate, columns.heartRate) { stringResource(Res.string.unit_bpm, formatWhole(it)) },
        cell(lap.avgPower, columns.power) { stringResource(Res.string.unit_watts, formatWhole(it)) },
        cell(lap.avgCadence, columns.cadence) { stringResource(Res.string.unit_rpm, formatWhole(it)) },
        cell(lap.ascent, columns.ascent) { stringResource(heightUnit, formatDecimal(it, 0)) },
        // A lift ride has no descent worth showing.
        if (!columns.descent) null
        else
            lap.descent?.takeUnless { lap.isRest }?.let { stringResource(heightUnit, formatDecimal(it, 0)) }
                ?: stringResource(Res.string.value_missing),
    )
}

/** A lap's intensity ("Rest", "Warm-up"), by its token; an unknown one readably. */
@Composable
private fun intensityLabel(token: String): String =
    Res.allStringResources["lap_intensity_$token"]?.let { stringResource(it) } ?: readableToken(token)

/** [value] as [text] puts it, a dash when the row has none, or null when the column isn't [shown]. */
@Composable
internal fun <T : Any> cell(value: T?, shown: Boolean = true, text: @Composable (T) -> String): String? =
    if (!shown) null else value?.let { text(it) } ?: stringResource(Res.string.value_missing)

/**
 * A row of a table: its first cell [firstWidth] wide (a number or short name), the rest sharing the width, the [wideColumn] (a name) twice
 * as much, each wrapping to a second line.
 */
@Composable
internal fun TableCells(
    cells: ImmutableList<String>,
    header: Boolean,
    modifier: Modifier = Modifier,
    firstWidth: Dp = LAP_NUMBER_WIDTH,
    wideColumn: Int = -1,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.forEachIndexed { i, cell ->
            Text(
                cell,
                if (i == 0) Modifier.width(firstWidth) else Modifier.weight(if (i == wideColumn) 2f else 1f),
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
