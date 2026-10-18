package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.chart.SeriesPoint
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.chart_cadence
import io.github.solcott.activitytracker.composeApp.resources.chart_cns
import io.github.solcott.activitytracker.composeApp.resources.chart_core_temperature
import io.github.solcott.activitytracker.composeApp.resources.chart_depth
import io.github.solcott.activitytracker.composeApp.resources.chart_ebike_assist_level
import io.github.solcott.activitytracker.composeApp.resources.chart_ebike_assist_mode
import io.github.solcott.activitytracker.composeApp.resources.chart_ebike_battery
import io.github.solcott.activitytracker.composeApp.resources.chart_elevation
import io.github.solcott.activitytracker.composeApp.resources.chart_form_power
import io.github.solcott.activitytracker.composeApp.resources.chart_grade
import io.github.solcott.activitytracker.composeApp.resources.chart_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.chart_left_right_balance
import io.github.solcott.activitytracker.composeApp.resources.chart_leg_spring_stiffness
import io.github.solcott.activitytracker.composeApp.resources.chart_marker
import io.github.solcott.activitytracker.composeApp.resources.chart_marker_elevation
import io.github.solcott.activitytracker.composeApp.resources.chart_pedal_smoothness
import io.github.solcott.activitytracker.composeApp.resources.chart_platform_center_offset
import io.github.solcott.activitytracker.composeApp.resources.chart_power
import io.github.solcott.activitytracker.composeApp.resources.chart_ppo2
import io.github.solcott.activitytracker.composeApp.resources.chart_respiration_rate
import io.github.solcott.activitytracker.composeApp.resources.chart_smo2
import io.github.solcott.activitytracker.composeApp.resources.chart_speed
import io.github.solcott.activitytracker.composeApp.resources.chart_stance_time
import io.github.solcott.activitytracker.composeApp.resources.chart_stance_time_balance
import io.github.solcott.activitytracker.composeApp.resources.chart_step_length
import io.github.solcott.activitytracker.composeApp.resources.chart_tank_pressure
import io.github.solcott.activitytracker.composeApp.resources.chart_temperature
import io.github.solcott.activitytracker.composeApp.resources.chart_torque_effectiveness
import io.github.solcott.activitytracker.composeApp.resources.chart_total_hemoglobin
import io.github.solcott.activitytracker.composeApp.resources.chart_vertical_oscillation
import io.github.solcott.activitytracker.composeApp.resources.chart_vertical_ratio
import io.github.solcott.activitytracker.composeApp.resources.unit_assist_mode
import io.github.solcott.activitytracker.composeApp.resources.unit_bpm
import io.github.solcott.activitytracker.composeApp.resources.unit_breaths
import io.github.solcott.activitytracker.composeApp.resources.unit_cm
import io.github.solcott.activitytracker.composeApp.resources.unit_elevation_ft
import io.github.solcott.activitytracker.composeApp.resources.unit_elevation_m
import io.github.solcott.activitytracker.composeApp.resources.unit_g_per_dl
import io.github.solcott.activitytracker.composeApp.resources.unit_in
import io.github.solcott.activitytracker.composeApp.resources.unit_kn_per_m
import io.github.solcott.activitytracker.composeApp.resources.unit_mm
import io.github.solcott.activitytracker.composeApp.resources.unit_ms
import io.github.solcott.activitytracker.composeApp.resources.unit_percent
import io.github.solcott.activitytracker.composeApp.resources.unit_pressure_bar
import io.github.solcott.activitytracker.composeApp.resources.unit_pressure_psi
import io.github.solcott.activitytracker.composeApp.resources.unit_rpm
import io.github.solcott.activitytracker.composeApp.resources.unit_speed_kmh
import io.github.solcott.activitytracker.composeApp.resources.unit_speed_mph
import io.github.solcott.activitytracker.composeApp.resources.unit_temperature_c
import io.github.solcott.activitytracker.composeApp.resources.unit_temperature_f
import io.github.solcott.activitytracker.composeApp.resources.unit_watts
import io.github.solcott.activitytracker.data.units.DiveConversions
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatDecimal
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** [series] in the athlete's units, ready to chart. */
@Composable
internal fun rememberPoints(series: LineSeries): ImmutableList<SeriesPoint> {
    val unit = streamUnit(series.kind)
    return remember(series, unit) { series.points.map { SeriesPoint(it.seconds.toDouble(), unit.convert(it.value)) }.toImmutableList() }
}

/** The marker's text: the time and [value], then the elevation there when the chart has it [behind]. */
@Composable
internal fun rememberMarkerLabel(behind: ImmutableList<SeriesPoint>?, value: (Double) -> String): (Double, Double) -> String {
    val marker = stringResource(Res.string.chart_marker, "%1", "%2")
    val withElevation = stringResource(Res.string.chart_marker_elevation, "%1", "%2", "%3")
    val elevation = streamUnit(StreamKind.Elevation)
    return { x, y ->
        val time = Format.duration(x.roundToLong())
        val height = behind?.let { nearestY(it, x) }
        if (height == null) marker.replace("%1", time).replace("%2", value(y))
        else withElevation.replace("%1", time).replace("%2", value(y)).replace("%3", elevation.text(height))
    }
}

/** The y of the point in [points] (sorted by x) closest to [x]. */
private fun nearestY(points: List<SeriesPoint>, x: Double): Double {
    val i = points.binarySearch { it.x.compareTo(x) }
    if (i >= 0) return points[i].y
    val after = -i - 1
    val candidates = listOfNotNull(points.getOrNull(after - 1), points.getOrNull(after))
    return candidates.minBy { abs(it.x - x) }.y
}

internal fun streamTitle(kind: StreamKind): StringResource =
    when (kind) {
        StreamKind.HeartRate -> Res.string.chart_heart_rate
        StreamKind.Power -> Res.string.chart_power
        StreamKind.Speed -> Res.string.chart_speed
        StreamKind.Cadence -> Res.string.chart_cadence
        StreamKind.Elevation -> Res.string.chart_elevation
        StreamKind.Grade -> Res.string.chart_grade
        StreamKind.Temperature -> Res.string.chart_temperature
        StreamKind.Depth -> Res.string.chart_depth
        StreamKind.RespirationRate -> Res.string.chart_respiration_rate
        StreamKind.VerticalOscillation -> Res.string.chart_vertical_oscillation
        StreamKind.StanceTime -> Res.string.chart_stance_time
        StreamKind.StepLength -> Res.string.chart_step_length
        StreamKind.VerticalRatio -> Res.string.chart_vertical_ratio
        StreamKind.StanceTimeBalance -> Res.string.chart_stance_time_balance
        StreamKind.LeftRightBalance -> Res.string.chart_left_right_balance
        StreamKind.PpO2 -> Res.string.chart_ppo2
        StreamKind.Cns -> Res.string.chart_cns
        StreamKind.TankPressure -> Res.string.chart_tank_pressure
        StreamKind.TorqueEffectiveness -> Res.string.chart_torque_effectiveness
        StreamKind.PedalSmoothness -> Res.string.chart_pedal_smoothness
        StreamKind.PlatformCentreOffset -> Res.string.chart_platform_center_offset
        StreamKind.CoreTemperature -> Res.string.chart_core_temperature
        StreamKind.SmO2 -> Res.string.chart_smo2
        StreamKind.TotalHemoglobin -> Res.string.chart_total_hemoglobin
        StreamKind.EbikeBattery -> Res.string.chart_ebike_battery
        StreamKind.EbikeAssistMode -> Res.string.chart_ebike_assist_mode
        StreamKind.EbikeAssistLevel -> Res.string.chart_ebike_assist_level
        StreamKind.FormPower -> Res.string.chart_form_power
        StreamKind.LegSpringStiffness -> Res.string.chart_leg_spring_stiffness
    }

/**
 * How a stream's values show: [convert] from the presenter's unit to the athlete's, then [text] with the unit or [axis] without decimals.
 */
internal class StreamUnit(val convert: (Double) -> Double, val text: (Double) -> String, val axis: (Double) -> String)

@Composable
internal fun streamUnit(kind: StreamKind): StreamUnit {
    val units = LocalUnitSystem.current
    val metric = units == UnitSystem.Metric
    val template =
        stringResource(
            when (kind) {
                StreamKind.HeartRate -> Res.string.unit_bpm
                StreamKind.Power,
                StreamKind.FormPower -> Res.string.unit_watts
                StreamKind.Cadence -> Res.string.unit_rpm
                StreamKind.Grade,
                StreamKind.VerticalRatio,
                StreamKind.StanceTimeBalance,
                StreamKind.LeftRightBalance,
                StreamKind.TorqueEffectiveness,
                StreamKind.PedalSmoothness,
                StreamKind.SmO2,
                StreamKind.EbikeBattery,
                StreamKind.EbikeAssistLevel,
                StreamKind.Cns -> Res.string.unit_percent
                StreamKind.Speed -> if (metric) Res.string.unit_speed_kmh else Res.string.unit_speed_mph
                StreamKind.Temperature,
                StreamKind.CoreTemperature -> if (metric) Res.string.unit_temperature_c else Res.string.unit_temperature_f
                StreamKind.Elevation,
                StreamKind.Depth,
                StreamKind.StepLength -> if (metric) Res.string.unit_elevation_m else Res.string.unit_elevation_ft
                StreamKind.RespirationRate -> Res.string.unit_breaths
                StreamKind.VerticalOscillation -> if (metric) Res.string.unit_cm else Res.string.unit_in
                StreamKind.StanceTime -> Res.string.unit_ms
                // ppO₂ reads in bar whatever the units: it's how divers and their computers give it.
                StreamKind.PpO2 -> Res.string.unit_pressure_bar
                StreamKind.TankPressure -> if (metric) Res.string.unit_pressure_bar else Res.string.unit_pressure_psi
                StreamKind.PlatformCentreOffset -> Res.string.unit_mm
                StreamKind.TotalHemoglobin -> Res.string.unit_g_per_dl
                StreamKind.EbikeAssistMode -> Res.string.unit_assist_mode
                StreamKind.LegSpringStiffness -> Res.string.unit_kn_per_m
            },
            "%s",
        )
    return remember(kind, units, template) {
        val convert: (Double) -> Double =
            when (kind) {
                StreamKind.Speed -> { mm ->
                    mm / MM_PER_M * SECONDS_PER_HOUR / UnitConversions.metersPerDistanceUnit(units)
                }
                StreamKind.Temperature,
                StreamKind.CoreTemperature -> { tenths ->
                    UnitConversions.toTemperatureUnit(tenths.roundToLong(), units)
                }
                StreamKind.Elevation,
                StreamKind.Depth,
                StreamKind.StepLength -> { m ->
                    UnitConversions.toElevationUnit(m, units)
                }
                StreamKind.VerticalOscillation -> { mm ->
                    if (metric) mm / MM_PER_CM else mm / MM_PER_INCH
                }
                StreamKind.TankPressure -> { bar ->
                    DiveConversions.toPressureUnit(bar, units)
                }
                else -> { v ->
                    v
                }
            }
        val decimals =
            when (kind) {
                StreamKind.Speed,
                StreamKind.Grade,
                StreamKind.RespirationRate,
                StreamKind.VerticalOscillation,
                StreamKind.VerticalRatio,
                StreamKind.StanceTimeBalance,
                StreamKind.CoreTemperature,
                StreamKind.SmO2,
                StreamKind.LegSpringStiffness -> 1
                StreamKind.StepLength,
                StreamKind.TotalHemoglobin,
                StreamKind.PpO2 -> 2
                else -> 0
            }
        StreamUnit(
            convert = convert,
            text = { template.replace("%s", formatDecimal(it, decimals)) },
            axis = { formatDecimal(it, 0) },
        )
    }
}

private const val MM_PER_M = 1000.0
private const val MM_PER_CM = 10.0
private const val MM_PER_INCH = 25.4
private const val SECONDS_PER_HOUR = 3600.0
