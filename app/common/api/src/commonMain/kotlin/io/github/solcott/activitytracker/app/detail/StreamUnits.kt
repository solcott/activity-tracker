package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.DiveConversions
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatDecimal
import kotlin.math.roundToLong

/**
 * How a stream's values read (dev/shared-display-text): its title, its value in the viewer's units ([convert]) and that value with its unit
 * ([text]), or a pace's. The one place these rules are written: the shared charts' `streamUnit` and the native page both ask them.
 */
@Suppress("TooManyFunctions") // A stream's title, unit, decimals, conversion and text, and a pace's.
object StreamUnits {
    /** A chart's name for [kind]'s line: "Heart rate", "Cadence". */
    fun title(kind: StreamKind): AppText = AppText.named(titleName(kind))

    /** The string name of [kind]'s title. */
    @Suppress("CyclomaticComplexMethod") // One name per stream.
    fun titleName(kind: StreamKind): String =
        when (kind) {
            StreamKind.HeartRate -> "chart_heart_rate"
            StreamKind.Power -> "chart_power"
            StreamKind.Speed -> "chart_speed"
            StreamKind.Cadence -> "chart_cadence"
            StreamKind.Elevation -> "chart_elevation"
            StreamKind.Grade -> "chart_grade"
            StreamKind.Temperature -> "chart_temperature"
            StreamKind.Depth -> "chart_depth"
            StreamKind.RespirationRate -> "chart_respiration_rate"
            StreamKind.VerticalOscillation -> "chart_vertical_oscillation"
            StreamKind.StanceTime -> "chart_stance_time"
            StreamKind.StepLength -> "chart_step_length"
            StreamKind.VerticalRatio -> "chart_vertical_ratio"
            StreamKind.StanceTimeBalance -> "chart_stance_time_balance"
            StreamKind.LeftRightBalance -> "chart_left_right_balance"
            StreamKind.PpO2 -> "chart_ppo2"
            StreamKind.Cns -> "chart_cns"
            StreamKind.TankPressure -> "chart_tank_pressure"
            StreamKind.TorqueEffectiveness -> "chart_torque_effectiveness"
            StreamKind.PedalSmoothness -> "chart_pedal_smoothness"
            StreamKind.PlatformCentreOffset -> "chart_platform_center_offset"
            StreamKind.CoreTemperature -> "chart_core_temperature"
            StreamKind.SmO2 -> "chart_smo2"
            StreamKind.TotalHemoglobin -> "chart_total_hemoglobin"
            StreamKind.EbikeBattery -> "chart_ebike_battery"
            StreamKind.EbikeAssistMode -> "chart_ebike_assist_mode"
            StreamKind.EbikeAssistLevel -> "chart_ebike_assist_level"
            StreamKind.FormPower -> "chart_form_power"
            StreamKind.LegSpringStiffness -> "chart_leg_spring_stiffness"
        }

    /** The name of the string [kind]'s value reads in, with the number in its placeholder: "%1$s bpm", "%1$s km/h". */
    @Suppress("CyclomaticComplexMethod") // One unit per stream.
    fun unitName(kind: StreamKind, units: UnitSystem): String {
        val metric = units == UnitSystem.Metric
        return when (kind) {
            StreamKind.HeartRate -> "unit_bpm"
            StreamKind.Power,
            StreamKind.FormPower -> "unit_watts"
            StreamKind.Cadence -> "unit_rpm"
            StreamKind.Grade,
            StreamKind.VerticalRatio,
            StreamKind.StanceTimeBalance,
            StreamKind.LeftRightBalance,
            StreamKind.TorqueEffectiveness,
            StreamKind.PedalSmoothness,
            StreamKind.SmO2,
            StreamKind.EbikeBattery,
            StreamKind.EbikeAssistLevel,
            StreamKind.Cns -> "unit_percent"
            StreamKind.Speed -> if (metric) "unit_speed_kmh" else "unit_speed_mph"
            StreamKind.Temperature,
            StreamKind.CoreTemperature -> if (metric) "unit_temperature_c" else "unit_temperature_f"
            StreamKind.Elevation,
            StreamKind.Depth,
            StreamKind.StepLength -> if (metric) "unit_elevation_m" else "unit_elevation_ft"
            StreamKind.RespirationRate -> "unit_breaths"
            StreamKind.VerticalOscillation -> if (metric) "unit_cm" else "unit_in"
            StreamKind.StanceTime -> "unit_ms"
            // ppO₂ reads in bar whatever the units: it's how divers and their computers give it.
            StreamKind.PpO2 -> "unit_pressure_bar"
            StreamKind.TankPressure -> if (metric) "unit_pressure_bar" else "unit_pressure_psi"
            StreamKind.PlatformCentreOffset -> "unit_mm"
            StreamKind.TotalHemoglobin -> "unit_g_per_dl"
            StreamKind.EbikeAssistMode -> "unit_assist_mode"
            StreamKind.LegSpringStiffness -> "unit_kn_per_m"
        }
    }

    /** How many decimals [kind]'s value reads with. */
    fun decimals(kind: StreamKind): Int =
        when (kind) {
            StreamKind.Speed,
            StreamKind.Temperature,
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

    /** [value], in the unit [LineSeries] keeps [kind] in, in the viewer's units. */
    fun convert(kind: StreamKind, value: Double, units: UnitSystem): Double =
        when (kind) {
            StreamKind.Speed -> value / MM_PER_M * SECONDS_PER_HOUR / UnitConversions.metersPerDistanceUnit(units)
            StreamKind.Temperature,
            StreamKind.CoreTemperature -> UnitConversions.toTemperatureUnit(value.roundToLong(), units)
            StreamKind.Elevation,
            StreamKind.Depth,
            StreamKind.StepLength -> UnitConversions.toElevationUnit(value, units)
            StreamKind.VerticalOscillation -> if (units == UnitSystem.Metric) value / MM_PER_CM else value / MM_PER_INCH
            StreamKind.TankPressure -> DiveConversions.toPressureUnit(value, units)
            else -> value
        }

    /** [converted], already in the viewer's units ([convert]), with [kind]'s unit. */
    fun text(kind: StreamKind, converted: Double, units: UnitSystem): AppText =
        AppText.named(unitName(kind, units), AppText.written(formatDecimal(converted, decimals(kind))))

    /** [value] as [LineSeries] keeps it, converted and with its unit. */
    fun valueText(kind: StreamKind, value: Double, units: UnitSystem): AppText = text(kind, convert(kind, value, units), units)

    /** An axis label: [converted] without decimals and without its unit. */
    fun axis(converted: Double): AppText = AppText.written(formatDecimal(converted, 0))

    /** The name of the string a pace per [kind] reads in: "%1$s /km", "%1$s /100 yd". */
    fun paceUnitName(kind: PaceKind, units: UnitSystem): String {
        val metric = units == UnitSystem.Metric
        return when (kind) {
            PaceKind.Distance -> if (metric) "unit_pace_per_km" else "unit_pace_per_mi"
            PaceKind.Swim -> if (metric) "unit_pace_per_100m" else "unit_pace_per_100yd"
        }
    }

    /**
     * A speed (mm/s) as pace: the seconds a kilometer or mile takes, or 100 m or yd swimming. A standstill's pace is infinite
     * ([Double.MAX_VALUE]), so a chart caps it.
     */
    fun paceSeconds(kind: PaceKind, mmPerSecond: Double, units: UnitSystem): Double {
        val meters =
            when (kind) {
                PaceKind.Distance -> UnitConversions.metersPerDistanceUnit(units)
                PaceKind.Swim -> if (units == UnitSystem.Metric) SWIM_PACE_LENGTH else SWIM_PACE_LENGTH * UnitConversions.METERS_PER_YARD
            }
        return if (mmPerSecond > 0) meters * MM_PER_M / mmPerSecond else Double.MAX_VALUE
    }

    /** [seconds] per kilometer, mile or 100, with its unit: "5:12 /km". */
    fun paceText(kind: PaceKind, seconds: Double, units: UnitSystem): AppText =
        AppText.named(paceUnitName(kind, units), AppText.written(StatText.duration(seconds.roundToLong())))

    /** A pace axis label: [seconds] as a time, without its unit. */
    fun paceAxis(seconds: Double): AppText = AppText.written(StatText.duration(seconds.roundToLong()))

    private const val MM_PER_M = 1000.0
    private const val MM_PER_CM = 10.0
    private const val MM_PER_INCH = 25.4
    private const val SECONDS_PER_HOUR = 3600.0
    private const val SWIM_PACE_LENGTH = 100.0
}
