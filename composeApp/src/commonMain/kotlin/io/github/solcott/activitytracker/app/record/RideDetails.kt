package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.activityfile.SensorBattery
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.GearTimeChart
import io.github.solcott.activitytracker.app.ui.Stat
import io.github.solcott.activitytracker.app.ui.StatsRow
import io.github.solcott.activitytracker.app.ui.sensorLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.power_ftp
import io.github.solcott.activitytracker.composeApp.resources.power_intensity_factor
import io.github.solcott.activitytracker.composeApp.resources.power_tss
import io.github.solcott.activitytracker.composeApp.resources.power_work
import io.github.solcott.activitytracker.composeApp.resources.section_power
import io.github.solcott.activitytracker.composeApp.resources.section_sensors
import io.github.solcott.activitytracker.composeApp.resources.section_shifting
import io.github.solcott.activitytracker.composeApp.resources.section_trail
import io.github.solcott.activitytracker.composeApp.resources.sensor_battery_critical
import io.github.solcott.activitytracker.composeApp.resources.sensor_battery_low
import io.github.solcott.activitytracker.composeApp.resources.sensor_battery_percent
import io.github.solcott.activitytracker.composeApp.resources.shifting_front_rear
import io.github.solcott.activitytracker.composeApp.resources.shifting_gear_numbers
import io.github.solcott.activitytracker.composeApp.resources.shifting_shifts
import io.github.solcott.activitytracker.composeApp.resources.shifting_time_in_gear
import io.github.solcott.activitytracker.composeApp.resources.stat_avg_cadence
import io.github.solcott.activitytracker.composeApp.resources.stat_avg_power
import io.github.solcott.activitytracker.composeApp.resources.stat_descent
import io.github.solcott.activitytracker.composeApp.resources.stat_flow
import io.github.solcott.activitytracker.composeApp.resources.stat_grit
import io.github.solcott.activitytracker.composeApp.resources.stat_jumps
import io.github.solcott.activitytracker.composeApp.resources.stat_max_cadence
import io.github.solcott.activitytracker.composeApp.resources.stat_max_power
import io.github.solcott.activitytracker.composeApp.resources.stat_normalized_power
import io.github.solcott.activitytracker.composeApp.resources.trail_hang_time
import io.github.solcott.activitytracker.composeApp.resources.trail_longest_jump
import io.github.solcott.activitytracker.composeApp.resources.unit_rpm
import io.github.solcott.activitytracker.composeApp.resources.unit_seconds_decimal
import io.github.solcott.activitytracker.composeApp.resources.unit_watts
import io.github.solcott.activitytracker.composeApp.resources.value_missing
import io.github.solcott.activitytracker.data.units.formatDecimal
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What a bike computer adds to a ride: descent and cadence, power, shifting, trail dynamics and the paired sensors. Each only if recorded.
 */
@Composable
fun RideDetails(preview: ImportPreview, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ClimbAndCadence(preview)
        preview.power?.let { Power(it) }
        preview.shifting?.let { Shifting(it) }
        preview.mtb?.let { Trail(it) }
        if (preview.sensors.isNotEmpty()) Sensors(preview)
    }
}

@Composable
private fun ClimbAndCadence(preview: ImportPreview) {
    val cadence = preview.cadence
    val stats =
        listOfNotNull(
            preview.elevationLossMeters?.let { Stat(stringResource(Res.string.stat_descent), Format.elevation(it)) },
            cadence?.avg?.let { Stat(stringResource(Res.string.stat_avg_cadence), stringResource(Res.string.unit_rpm, it)) },
            cadence?.max?.let { Stat(stringResource(Res.string.stat_max_cadence), stringResource(Res.string.unit_rpm, it)) },
        )
    if (stats.isNotEmpty()) StatsRow(stats.toImmutableList())
}

@Composable
private fun Power(power: PowerPreview) {
    Section(stringResource(Res.string.section_power)) {
        StatsRow(
            persistentListOf(
                Stat(stringResource(Res.string.stat_avg_power), watts(power.avgWatts)),
                Stat(stringResource(Res.string.stat_normalized_power), watts(power.normalizedWatts)),
                Stat(stringResource(Res.string.stat_max_power), watts(power.maxWatts)),
            )
        )
        val more =
            listOfNotNull(
                power.workKilojoules?.let { stringResource(Res.string.power_work, it) },
                power.ftpWatts?.let { stringResource(Res.string.power_ftp, it) },
                power.intensityFactor?.let { stringResource(Res.string.power_intensity_factor, formatDecimal(it, 2)) },
                power.trainingStressScore?.let { stringResource(Res.string.power_tss, formatDecimal(it, 0)) },
            )
        if (more.isNotEmpty()) Text(more.joinToString(" · "))
    }
}

@Composable
private fun Shifting(shifting: ShiftingPreview) {
    Section(stringResource(Res.string.section_shifting)) {
        val front = shifting.frontShifts ?: 0
        val rear = shifting.rearShifts ?: 0
        val total = front + rear
        val shifts =
            if (front > 0 && rear > 0) stringResource(Res.string.shifting_front_rear, front, rear)
            else pluralStringResource(Res.plurals.shifting_shifts, total, total)
        Text(shifts)
        if (shifting.gears.isNotEmpty()) {
            GearTimeChart(shifting.gears, description = stringResource(Res.string.shifting_time_in_gear))
            Text(stringResource(Res.string.shifting_gear_numbers), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Trail(mtb: MtbPreview) {
    Section(stringResource(Res.string.section_trail)) {
        val stats =
            listOfNotNull(
                mtb.jumps?.let { Stat(stringResource(Res.string.stat_jumps), it.toString()) },
                mtb.grit?.let { Stat(stringResource(Res.string.stat_grit), formatDecimal(it, 1)) },
                mtb.flow?.let { Stat(stringResource(Res.string.stat_flow), formatDecimal(it, 1)) },
            )
        if (stats.isNotEmpty()) StatsRow(stats.toImmutableList())
        val jumps =
            listOfNotNull(
                mtb.longestJumpMeters?.let { stringResource(Res.string.trail_longest_jump, Format.depth(it)) },
                mtb.longestHangTimeSeconds?.let {
                    stringResource(Res.string.trail_hang_time, stringResource(Res.string.unit_seconds_decimal, formatDecimal(it, 2)))
                },
            )
        if (jumps.isNotEmpty()) Text(jumps.joinToString(" · "))
    }
}

@Composable
private fun Sensors(preview: ImportPreview) {
    Section(stringResource(Res.string.section_sensors)) {
        preview.sensors.forEach { sensor ->
            Row {
                val battery = sensor.batteryPercent?.let { stringResource(Res.string.sensor_battery_percent, it) }
                Text(listOfNotNull(sensorLabel(sensor.kind.token, sensor.manufacturer, sensor.model), battery).joinToString(" · "))
                batteryWarning(sensor.battery)?.let { Text(" · $it", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun watts(value: Int?): String =
    value?.let { stringResource(Res.string.unit_watts, it) } ?: stringResource(Res.string.value_missing)

@Composable
private fun batteryWarning(battery: SensorBattery?): String? =
    when (battery) {
        SensorBattery.Low -> stringResource(Res.string.sensor_battery_low)
        SensorBattery.Critical -> stringResource(Res.string.sensor_battery_critical)
        else -> null
    }
