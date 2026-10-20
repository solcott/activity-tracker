package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import io.github.solcott.activitytracker.app.ui.DiveFormat
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.bar
import io.github.solcott.activitytracker.app.ui.decoModelName
import io.github.solcott.activitytracker.app.ui.sensorLabel
import io.github.solcott.activitytracker.app.ui.waterName
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.chart_battery_used
import io.github.solcott.activitytracker.composeApp.resources.chart_cadence
import io.github.solcott.activitytracker.composeApp.resources.chart_ebike_battery
import io.github.solcott.activitytracker.composeApp.resources.chart_gear_value
import io.github.solcott.activitytracker.composeApp.resources.chart_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.chart_left_right
import io.github.solcott.activitytracker.composeApp.resources.chart_power
import io.github.solcott.activitytracker.composeApp.resources.chart_speed
import io.github.solcott.activitytracker.composeApp.resources.chart_temperature
import io.github.solcott.activitytracker.composeApp.resources.detail_device
import io.github.solcott.activitytracker.composeApp.resources.detail_max_grade
import io.github.solcott.activitytracker.composeApp.resources.detail_section_laps
import io.github.solcott.activitytracker.composeApp.resources.detail_section_legs
import io.github.solcott.activitytracker.composeApp.resources.detail_section_lengths
import io.github.solcott.activitytracker.composeApp.resources.detail_section_sets
import io.github.solcott.activitytracker.composeApp.resources.dive_gradient_factors
import io.github.solcott.activitytracker.composeApp.resources.page_aerobic_effect
import io.github.solcott.activitytracker.composeApp.resources.page_air_temperature
import io.github.solcott.activitytracker.composeApp.resources.page_anaerobic_effect
import io.github.solcott.activitytracker.composeApp.resources.page_average
import io.github.solcott.activitytracker.composeApp.resources.page_avg_pace
import io.github.solcott.activitytracker.composeApp.resources.page_avg_speed
import io.github.solcott.activitytracker.composeApp.resources.page_avg_stroke_distance
import io.github.solcott.activitytracker.composeApp.resources.page_avg_temperature
import io.github.solcott.activitytracker.composeApp.resources.page_best
import io.github.solcott.activitytracker.composeApp.resources.page_best_power
import io.github.solcott.activitytracker.composeApp.resources.page_desaturation_time
import io.github.solcott.activitytracker.composeApp.resources.page_dive_number
import io.github.solcott.activitytracker.composeApp.resources.page_elapsed_time
import io.github.solcott.activitytracker.composeApp.resources.page_elevation_gain
import io.github.solcott.activitytracker.composeApp.resources.page_end_n2
import io.github.solcott.activitytracker.composeApp.resources.page_front_shifts
import io.github.solcott.activitytracker.composeApp.resources.page_ftp
import io.github.solcott.activitytracker.composeApp.resources.page_group_cadence
import io.github.solcott.activitytracker.composeApp.resources.page_group_counts
import io.github.solcott.activitytracker.composeApp.resources.page_group_device
import io.github.solcott.activitytracker.composeApp.resources.page_group_distance_speed
import io.github.solcott.activitytracker.composeApp.resources.page_group_dive
import io.github.solcott.activitytracker.composeApp.resources.page_group_elevation
import io.github.solcott.activitytracker.composeApp.resources.page_group_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.page_group_power
import io.github.solcott.activitytracker.composeApp.resources.page_group_temperature
import io.github.solcott.activitytracker.composeApp.resources.page_group_timing
import io.github.solcott.activitytracker.composeApp.resources.page_group_work_energy
import io.github.solcott.activitytracker.composeApp.resources.page_highest
import io.github.solcott.activitytracker.composeApp.resources.page_highest_ppo2
import io.github.solcott.activitytracker.composeApp.resources.page_hrv
import io.github.solcott.activitytracker.composeApp.resources.page_intensity_factor
import io.github.solcott.activitytracker.composeApp.resources.page_lowest
import io.github.solcott.activitytracker.composeApp.resources.page_max_ascent_rate
import io.github.solcott.activitytracker.composeApp.resources.page_max_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.page_max_pace
import io.github.solcott.activitytracker.composeApp.resources.page_max_speed
import io.github.solcott.activitytracker.composeApp.resources.page_max_temperature
import io.github.solcott.activitytracker.composeApp.resources.page_maximum
import io.github.solcott.activitytracker.composeApp.resources.page_min_temperature
import io.github.solcott.activitytracker.composeApp.resources.page_minimum
import io.github.solcott.activitytracker.composeApp.resources.page_most_used_gear
import io.github.solcott.activitytracker.composeApp.resources.page_no_fly_time
import io.github.solcott.activitytracker.composeApp.resources.page_normalized_power
import io.github.solcott.activitytracker.composeApp.resources.page_otu
import io.github.solcott.activitytracker.composeApp.resources.page_pool_length
import io.github.solcott.activitytracker.composeApp.resources.page_rear_shifts
import io.github.solcott.activitytracker.composeApp.resources.page_reps
import io.github.solcott.activitytracker.composeApp.resources.page_sensor
import io.github.solcott.activitytracker.composeApp.resources.page_start_time
import io.github.solcott.activitytracker.composeApp.resources.page_tank_pressure
import io.github.solcott.activitytracker.composeApp.resources.page_tss
import io.github.solcott.activitytracker.composeApp.resources.page_water_type
import io.github.solcott.activitytracker.composeApp.resources.shifting_front_rear
import io.github.solcott.activitytracker.composeApp.resources.stat_air_used
import io.github.solcott.activitytracker.composeApp.resources.stat_altitude
import io.github.solcott.activitytracker.composeApp.resources.stat_avg_cadence
import io.github.solcott.activitytracker.composeApp.resources.stat_avg_depth
import io.github.solcott.activitytracker.composeApp.resources.stat_avg_power
import io.github.solcott.activitytracker.composeApp.resources.stat_bottom_time
import io.github.solcott.activitytracker.composeApp.resources.stat_calories
import io.github.solcott.activitytracker.composeApp.resources.stat_cns
import io.github.solcott.activitytracker.composeApp.resources.stat_cns_value
import io.github.solcott.activitytracker.composeApp.resources.stat_deco_model
import io.github.solcott.activitytracker.composeApp.resources.stat_deco_model_gf
import io.github.solcott.activitytracker.composeApp.resources.stat_descent
import io.github.solcott.activitytracker.composeApp.resources.stat_descent_time
import io.github.solcott.activitytracker.composeApp.resources.stat_distance
import io.github.solcott.activitytracker.composeApp.resources.stat_dive_time
import io.github.solcott.activitytracker.composeApp.resources.stat_flow
import io.github.solcott.activitytracker.composeApp.resources.stat_gas
import io.github.solcott.activitytracker.composeApp.resources.stat_gas_ccr
import io.github.solcott.activitytracker.composeApp.resources.stat_gradient_factors
import io.github.solcott.activitytracker.composeApp.resources.stat_grit
import io.github.solcott.activitytracker.composeApp.resources.stat_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.stat_jumps
import io.github.solcott.activitytracker.composeApp.resources.stat_max_cadence
import io.github.solcott.activitytracker.composeApp.resources.stat_max_depth
import io.github.solcott.activitytracker.composeApp.resources.stat_max_power
import io.github.solcott.activitytracker.composeApp.resources.stat_moving_time
import io.github.solcott.activitytracker.composeApp.resources.stat_pace
import io.github.solcott.activitytracker.composeApp.resources.stat_pedal_strokes
import io.github.solcott.activitytracker.composeApp.resources.stat_ppo2_critical
import io.github.solcott.activitytracker.composeApp.resources.stat_ppo2_deco
import io.github.solcott.activitytracker.composeApp.resources.stat_ppo2_limits
import io.github.solcott.activitytracker.composeApp.resources.stat_ppo2_warn
import io.github.solcott.activitytracker.composeApp.resources.stat_pressure_range
import io.github.solcott.activitytracker.composeApp.resources.stat_pushes
import io.github.solcott.activitytracker.composeApp.resources.stat_rmv
import io.github.solcott.activitytracker.composeApp.resources.stat_runs
import io.github.solcott.activitytracker.composeApp.resources.stat_sac
import io.github.solcott.activitytracker.composeApp.resources.stat_safety_stop
import io.github.solcott.activitytracker.composeApp.resources.stat_setpoint_high
import io.github.solcott.activitytracker.composeApp.resources.stat_setpoint_low
import io.github.solcott.activitytracker.composeApp.resources.stat_setpoints
import io.github.solcott.activitytracker.composeApp.resources.stat_shifts
import io.github.solcott.activitytracker.composeApp.resources.stat_steps
import io.github.solcott.activitytracker.composeApp.resources.stat_strokes
import io.github.solcott.activitytracker.composeApp.resources.stat_surface_interval
import io.github.solcott.activitytracker.composeApp.resources.stat_time
import io.github.solcott.activitytracker.composeApp.resources.stat_visibility
import io.github.solcott.activitytracker.composeApp.resources.stat_water_temp
import io.github.solcott.activitytracker.composeApp.resources.stat_work
import io.github.solcott.activitytracker.composeApp.resources.unit_bpm
import io.github.solcott.activitytracker.composeApp.resources.unit_kcal
import io.github.solcott.activitytracker.composeApp.resources.unit_kj
import io.github.solcott.activitytracker.composeApp.resources.unit_ms
import io.github.solcott.activitytracker.composeApp.resources.unit_percent
import io.github.solcott.activitytracker.composeApp.resources.unit_pool_m
import io.github.solcott.activitytracker.composeApp.resources.unit_pool_yd
import io.github.solcott.activitytracker.composeApp.resources.unit_pressure_bar
import io.github.solcott.activitytracker.composeApp.resources.unit_rate_ft_min
import io.github.solcott.activitytracker.composeApp.resources.unit_rate_m_min
import io.github.solcott.activitytracker.composeApp.resources.unit_rpm
import io.github.solcott.activitytracker.composeApp.resources.unit_watts
import io.github.solcott.activitytracker.composeApp.resources.value_missing
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatDecimal
import io.github.solcott.activitytracker.data.units.formatWhole
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A figure's label in full, as the hero, cards and stats list name it. */
@Composable
internal fun figureLabel(figure: Figure): String {
    val value = figure.value
    return when (figure.key) {
        FigureKey.BestPower -> stringResource(Res.string.page_best_power, figure.index ?: 0)
        FigureKey.TankPressure -> stringResource(Res.string.page_tank_pressure, figure.index ?: 1)
        FigureKey.StreamAverage -> stringResource(streamTitle((value as FigureValue.Stream).kind))
        FigureKey.LeftRight -> stringResource(streamTitle((value as FigureValue.LeftRight).kind))
        FigureKey.DecoModel ->
            stringResource(
                if ((value as? FigureValue.DecoModel)?.model == null) Res.string.stat_gradient_factors else Res.string.stat_deco_model
            )
        else -> stringResource(LABELS.getValue(figure.key))
    }
}

private val LABELS: Map<FigureKey, StringResource> =
    mapOf(
        FigureKey.Distance to Res.string.stat_distance,
        FigureKey.MovingTime to Res.string.stat_moving_time,
        FigureKey.ElapsedTime to Res.string.page_elapsed_time,
        FigureKey.Time to Res.string.stat_time,
        FigureKey.StartTime to Res.string.page_start_time,
        FigureKey.ElevationGain to Res.string.page_elevation_gain,
        FigureKey.Descent to Res.string.stat_descent,
        FigureKey.AvgSpeed to Res.string.page_avg_speed,
        FigureKey.MaxSpeed to Res.string.page_max_speed,
        FigureKey.AvgPace to Res.string.page_avg_pace,
        FigureKey.MaxPace to Res.string.page_max_pace,
        FigureKey.AvgHeartRate to Res.string.stat_heart_rate,
        FigureKey.MaxHeartRate to Res.string.page_max_heart_rate,
        FigureKey.AvgPower to Res.string.stat_avg_power,
        FigureKey.MaxPower to Res.string.stat_max_power,
        FigureKey.NormalizedPower to Res.string.page_normalized_power,
        FigureKey.Work to Res.string.stat_work,
        FigureKey.Calories to Res.string.stat_calories,
        FigureKey.AvgCadence to Res.string.stat_avg_cadence,
        FigureKey.MaxCadence to Res.string.stat_max_cadence,
        FigureKey.AvgTemperature to Res.string.page_avg_temperature,
        FigureKey.MinTemperature to Res.string.page_min_temperature,
        FigureKey.MaxTemperature to Res.string.page_max_temperature,
        FigureKey.PoolLength to Res.string.page_pool_length,
        FigureKey.AvgStrokeDistance to Res.string.page_avg_stroke_distance,
        FigureKey.Steps to Res.string.stat_steps,
        FigureKey.Strokes to Res.string.stat_strokes,
        FigureKey.PedalStrokes to Res.string.stat_pedal_strokes,
        FigureKey.Pushes to Res.string.stat_pushes,
        FigureKey.Shifts to Res.string.stat_shifts,
        FigureKey.Jumps to Res.string.stat_jumps,
        FigureKey.Grit to Res.string.stat_grit,
        FigureKey.Flow to Res.string.stat_flow,
        FigureKey.Runs to Res.string.stat_runs,
        FigureKey.Laps to Res.string.detail_section_laps,
        FigureKey.Legs to Res.string.detail_section_legs,
        FigureKey.Lengths to Res.string.detail_section_lengths,
        FigureKey.Sets to Res.string.detail_section_sets,
        FigureKey.Reps to Res.string.page_reps,
        FigureKey.MaxDepth to Res.string.stat_max_depth,
        FigureKey.AvgDepth to Res.string.stat_avg_depth,
        FigureKey.DiveTime to Res.string.stat_dive_time,
        FigureKey.WaterTemp to Res.string.stat_water_temp,
        FigureKey.BottomTime to Res.string.stat_bottom_time,
        FigureKey.Gas to Res.string.stat_gas,
        FigureKey.SurfaceInterval to Res.string.stat_surface_interval,
        FigureKey.DiveNumber to Res.string.page_dive_number,
        FigureKey.Cns to Res.string.stat_cns,
        FigureKey.Otu to Res.string.page_otu,
        FigureKey.Rmv to Res.string.stat_rmv,
        FigureKey.Sac to Res.string.stat_sac,
        FigureKey.AirUsed to Res.string.stat_air_used,
        FigureKey.EndN2 to Res.string.page_end_n2,
        FigureKey.HighestPpO2 to Res.string.page_highest_ppo2,
        FigureKey.MaxAscentRate to Res.string.page_max_ascent_rate,
        FigureKey.DescentTime to Res.string.stat_descent_time,
        FigureKey.WaterType to Res.string.page_water_type,
        FigureKey.Visibility to Res.string.stat_visibility,
        FigureKey.AirTemperature to Res.string.page_air_temperature,
        FigureKey.Altitude to Res.string.stat_altitude,
        FigureKey.NoFlyTime to Res.string.page_no_fly_time,
        FigureKey.DesaturationTime to Res.string.page_desaturation_time,
        FigureKey.PpO2Limits to Res.string.stat_ppo2_limits,
        FigureKey.Setpoints to Res.string.stat_setpoints,
        FigureKey.SafetyStop to Res.string.stat_safety_stop,
        FigureKey.Ftp to Res.string.page_ftp,
        FigureKey.IntensityFactor to Res.string.page_intensity_factor,
        FigureKey.TrainingStressScore to Res.string.page_tss,
        FigureKey.AerobicEffect to Res.string.page_aerobic_effect,
        FigureKey.AnaerobicEffect to Res.string.page_anaerobic_effect,
        FigureKey.Hrv to Res.string.page_hrv,
        FigureKey.Avg to Res.string.page_average,
        FigureKey.Min to Res.string.page_minimum,
        FigureKey.Max to Res.string.page_maximum,
        FigureKey.Best to Res.string.page_best,
        FigureKey.Highest to Res.string.page_highest,
        FigureKey.Lowest to Res.string.page_lowest,
        FigureKey.MaxGrade to Res.string.detail_max_grade,
        FigureKey.MostUsedGear to Res.string.page_most_used_gear,
        FigureKey.FrontShifts to Res.string.page_front_shifts,
        FigureKey.RearShifts to Res.string.page_rear_shifts,
        FigureKey.BatteryUsed to Res.string.chart_ebike_battery,
        FigureKey.Device to Res.string.detail_device,
        FigureKey.Sensor to Res.string.page_sensor,
    )

@Composable
internal fun groupTitle(kind: StatsGroupKind): String =
    stringResource(
        when (kind) {
            StatsGroupKind.Timing -> Res.string.page_group_timing
            StatsGroupKind.DistanceSpeed -> Res.string.page_group_distance_speed
            StatsGroupKind.HeartRate -> Res.string.page_group_heart_rate
            StatsGroupKind.Power -> Res.string.page_group_power
            StatsGroupKind.Cadence -> Res.string.page_group_cadence
            StatsGroupKind.Elevation -> Res.string.page_group_elevation
            StatsGroupKind.WorkEnergy -> Res.string.page_group_work_energy
            StatsGroupKind.Temperature -> Res.string.page_group_temperature
            StatsGroupKind.Counts -> Res.string.page_group_counts
            StatsGroupKind.Dive -> Res.string.page_group_dive
            StatsGroupKind.Device -> Res.string.page_group_device
        }
    )

@Composable
internal fun avgMaxLabel(metric: AvgMaxMetric): String =
    stringResource(
        when (metric) {
            AvgMaxMetric.HeartRate -> Res.string.chart_heart_rate
            AvgMaxMetric.Power -> Res.string.chart_power
            AvgMaxMetric.Speed -> Res.string.chart_speed
            AvgMaxMetric.Pace -> Res.string.stat_pace
            AvgMaxMetric.Cadence -> Res.string.chart_cadence
            AvgMaxMetric.Temperature -> Res.string.chart_temperature
        }
    )

/** A figure's value in the viewer's units, with its unit, in full. */
@Composable
@Suppress("CyclomaticComplexMethod") // One branch per kind of value.
internal fun figureText(value: FigureValue): String =
    when (value) {
        FigureValue.Missing -> stringResource(Res.string.value_missing)
        is FigureValue.Distance -> Format.distance(value.meters)
        is FigureValue.Duration -> Format.duration(value.seconds)
        is FigureValue.Elevation -> Format.elevation(value.meters)
        is FigureValue.Length -> Format.depth(value.meters)
        is FigureValue.Speed -> Format.speed(value.mmPerSecond)
        is FigureValue.Pace -> pace(value)
        is FigureValue.Temperature -> Format.temperature(value.tenthsCelsius)
        is FigureValue.Count -> formatWhole(value.value)
        is FigureValue.Decimal -> formatDecimal(value.value, value.decimals)
        is FigureValue.Quantity -> stringResource(unitOf(value.unit), formatWhole(value.value))
        is FigureValue.Stream -> streamUnit(value.kind).let { it.text(it.convert(value.value)) }
        is FigureValue.LeftRight -> {
            val unit = streamUnit(value.kind)
            val missing = stringResource(Res.string.value_missing)
            val side = { v: Double? -> v?.let { unit.text(unit.convert(it)) } ?: missing }
            stringResource(Res.string.chart_left_right, side(value.left), side(value.right))
        }
        is FigureValue.BatteryUsed ->
            stringResource(
                Res.string.chart_battery_used,
                stringResource(Res.string.unit_percent, formatDecimal(value.fromPercent, 0)),
                stringResource(Res.string.unit_percent, formatDecimal(value.toPercent, 0)),
            )
        is FigureValue.Gear -> stringResource(Res.string.chart_gear_value, value.rear)
        is FigureValue.Shifts -> stringResource(Res.string.shifting_front_rear, value.front, value.rear)
        is FigureValue.PoolLength ->
            stringResource(if (value.yards) Res.string.unit_pool_yd else Res.string.unit_pool_m, formatWhole(value.value))
        is FigureValue.StartTime -> startTime(value)
        is FigureValue.Text -> value.text
        is FigureValue.Sensor -> sensorLabel(value.kind, value.manufacturer, value.model)
        is FigureValue.Token -> waterName(value.token)?.let { stringResource(it) } ?: value.token
        is FigureValue.Gases -> {
            val names = value.mixes.map { DiveFormat.gasName(it.oxygen, it.helium) }.joinToString(" → ")
            if (value.closedCircuit) stringResource(Res.string.stat_gas_ccr, names) else names
        }
        is FigureValue.PercentRange -> stringResource(Res.string.stat_cns_value, value.start, value.end)
        is FigureValue.PressureRange ->
            stringResource(Res.string.stat_pressure_range, DiveFormat.pressure(value.startBar), DiveFormat.pressure(value.endBar))
        is FigureValue.Bar -> stringResource(Res.string.unit_pressure_bar, formatDecimal(value.value, 2))
        is FigureValue.GasRate -> DiveFormat.gasRate(value.litersPerMinute)
        is FigureValue.PressureRate -> DiveFormat.pressureRate(value.barPerMinute)
        is FigureValue.VerticalRate -> verticalRate(value.mmPerSecond)
        is FigureValue.Minutes -> DiveFormat.surfaceInterval(value.minutes)
        is FigureValue.DecoModel -> decoModel(value)
        is FigureValue.PpO2Limits ->
            listOfNotNull(
                    value.warn?.let { stringResource(Res.string.stat_ppo2_warn, bar(it)) },
                    value.critical?.let { stringResource(Res.string.stat_ppo2_critical, bar(it)) },
                    value.deco?.let { stringResource(Res.string.stat_ppo2_deco, bar(it)) },
                )
                .joinToString(" · ")
        is FigureValue.Setpoints ->
            listOfNotNull(
                    value.low?.let { stringResource(Res.string.stat_setpoint_low, bar(it)) },
                    value.high?.let { stringResource(Res.string.stat_setpoint_high, bar(it)) },
                )
                .joinToString(" · ")
    }

/**
 * [value] with its numbers in the value's own style and everything else (units, separators) at [unit]: "7.83" larger than "mi". A value
 * without a number, like a device's name, reads in the value's style throughout.
 */
internal fun withSmallUnits(value: String, unit: SpanStyle): AnnotatedString {
    val text = unbreakableUnit(value)
    val numbers = NUMBER.findAll(text).toList()
    if (numbers.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var at = 0
        for (match in numbers) {
            if (match.range.first > at) withStyle(unit) { append(text.substring(at, match.range.first)) }
            append(match.value)
            at = match.range.last + 1
        }
        if (at < text.length) withStyle(unit) { append(text.substring(at)) }
    }
}

/**
 * [value] that a narrow column may break only between its number and its unit ("1:59" over "/100 yd"), never inside the unit: every space
 * but the first after the number is non-breaking, and nothing breaks after a slash.
 */
internal fun unbreakableUnit(value: String): String {
    val first = NUMBER.find(value)?.let { value.indexOf(' ', it.range.last) } ?: -1
    return buildString {
        value.forEachIndexed { i, c ->
            when {
                c == ' ' && i != first -> append(NO_BREAK_SPACE)
                c == '/' -> append(c).append(WORD_JOINER)
                else -> append(c)
            }
        }
    }
}

private const val NO_BREAK_SPACE = '\u00A0'
private const val WORD_JOINER = '\u2060'
// Digits joined by decimal, grouping (including the no-break spaces some locales group with) and clock separators.
private val NUMBER = Regex("""[-−]?\d(?:[.,:\u00A0\u202F]?\d)*""")

private fun unitOf(unit: FigureUnit): StringResource =
    when (unit) {
        FigureUnit.Watts -> Res.string.unit_watts
        FigureUnit.Bpm -> Res.string.unit_bpm
        FigureUnit.Rpm -> Res.string.unit_rpm
        FigureUnit.Kilocalories -> Res.string.unit_kcal
        FigureUnit.Kilojoules -> Res.string.unit_kj
        FigureUnit.Milliseconds -> Res.string.unit_ms
    }

/** A pace from a speed: the time a kilometer or mile (or 100 m or yd swimming) takes at it. */
@Composable
private fun pace(value: FigureValue.Pace): String {
    if (value.mmPerSecond <= 0) return stringResource(Res.string.value_missing)
    // The distance covered in an hour at that speed, so the pace is rounded once, per the athlete's unit, as a pace chart rounds it.
    val meters = value.mmPerSecond / MM_PER_M * SECONDS_PER_HOUR
    return if (value.swim) Format.swimPace(meters, SECONDS_PER_HOUR) else Format.pace(meters, SECONDS_PER_HOUR)
}

@Composable
private fun verticalRate(mmPerSecond: Long): String {
    val units = LocalUnitSystem.current
    val perMinute = UnitConversions.toElevationUnit(mmPerSecond / MM_PER_M * SECONDS_PER_MINUTE, units)
    return stringResource(
        if (units == UnitSystem.Metric) Res.string.unit_rate_m_min else Res.string.unit_rate_ft_min,
        formatDecimal(perMinute, 0),
    )
}

/** The deco model, with ZHL-16C's gradient factors; the gradient factors alone for a dive that names no model. */
@Composable
private fun decoModel(value: FigureValue.DecoModel): String {
    val gradientFactors =
        value.gradientFactorLow?.let { low ->
            value.gradientFactorHigh?.let { high -> stringResource(Res.string.dive_gradient_factors, low, high) }
        }
    val model = value.model?.let { decoModelName(it) }
    return when {
        model != null && gradientFactors != null -> stringResource(Res.string.stat_deco_model_gf, model, gradientFactors)
        model != null -> model
        else -> gradientFactors.orEmpty()
    }
}

private const val SECONDS_PER_HOUR = 3600L
private const val MM_PER_M = 1000.0
private const val SECONDS_PER_MINUTE = 60
