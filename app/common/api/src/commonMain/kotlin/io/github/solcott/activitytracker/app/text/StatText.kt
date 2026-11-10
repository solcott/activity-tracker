package io.github.solcott.activitytracker.app.text

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.data.sport.RateKind
import io.github.solcott.activitytracker.data.sport.SportCatalog
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.WeightConversions
import io.github.solcott.activitytracker.data.units.formatDecimal
import io.github.solcott.activitytracker.data.units.formatWhole
import kotlin.math.roundToLong

/** A stat as a UI shows it: what it is and its value. */
@Immutable data class LabeledText(val label: AppText, val value: AppText)

/**
 * How a stat reads. Inputs are the SI values records store; the text follows the athlete's [UnitSystem], which each UI passes from where it
 * keeps it. The one place these rules are written: the shared UI's `Format` and the native iOS UI both resolve what this returns. Numbers
 * are formatted here, by `formatDecimal` and `formatWhole`, so ui/number-format holds wherever the text is shown.
 */
object StatText {
    private const val DISTANCE_DECIMALS = 2
    private const val SPEED_DECIMALS = 1
    private const val WEIGHT_DECIMALS = 1
    private const val DEPTH_DECIMALS = 1
    private const val TEMPERATURE_DECIMALS = 1

    /** Below this, pace is noise from GPS jitter. */
    private const val MIN_PACE_DISTANCE_M = 10

    private const val SWIM_PACE_METERS = 100.0
    private const val SWIM_PACE_YARDS = 100.0

    private const val SECONDS_PER_HOUR = 3600
    private const val SECONDS_PER_MINUTE = 60

    /** What a stat with no value shows. */
    val missing: AppText = AppText.named("value_missing")

    fun distance(meters: Double?, units: UnitSystem): AppText {
        if (meters == null) return missing
        val value = formatDecimal(UnitConversions.toDistanceUnit(meters, units), DISTANCE_DECIMALS)
        return unit(units, "unit_distance_km", "unit_distance_mi", value)
    }

    /** A length of time as `m:ss`, or `h:mm:ss` from an hour up. Never grouped: a duration is not a quantity (ui/number-format). */
    fun duration(seconds: Long): String {
        val h = seconds / SECONDS_PER_HOUR
        val m = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val s = seconds % SECONDS_PER_MINUTE
        val mm = m.toString().padStart(2, '0')
        val ss = s.toString().padStart(2, '0')
        return if (h > 0) "$h:$mm:$ss" else "$m:$ss"
    }

    /** Minutes per kilometer, or per mile. */
    fun pace(meters: Double?, seconds: Long?, units: UnitSystem): AppText {
        if (meters == null || seconds == null || meters < MIN_PACE_DISTANCE_M) return missing
        val perUnit = duration((seconds / UnitConversions.toDistanceUnit(meters, units)).roundToLong())
        return unit(units, "unit_pace_per_km", "unit_pace_per_mi", perUnit)
    }

    /** km/h, or mph. */
    fun speed(mmPerSecond: Long?, units: UnitSystem): AppText {
        if (mmPerSecond == null) return missing
        val value = formatDecimal(UnitConversions.toSpeedUnit(mmPerSecond, units), SPEED_DECIMALS)
        return unit(units, "unit_speed_kmh", "unit_speed_mph", value)
    }

    /** Minutes per 100 meters, or per 100 yards. */
    fun swimPace(meters: Double?, seconds: Long?, units: UnitSystem): AppText {
        if (meters == null || seconds == null || meters < MIN_PACE_DISTANCE_M) return missing
        val metersPerUnit =
            when (units) {
                UnitSystem.Metric -> SWIM_PACE_METERS
                UnitSystem.Imperial -> SWIM_PACE_YARDS * UnitConversions.METERS_PER_YARD
            }
        val perUnit = duration((seconds / (meters / metersPerUnit)).roundToLong())
        return unit(units, "unit_pace_per_100m", "unit_pace_per_100yd", perUnit)
    }

    /** The sport's average rate (speed, pace or swim pace), or null for sports where distance means little, like yoga or tennis. */
    fun rate(sport: String, meters: Double?, seconds: Long?, units: UnitSystem): LabeledText? =
        when (SportCatalog.rate(sport)) {
            RateKind.Speed -> LabeledText(AppText.named("stat_speed"), speed(UnitConversions.averageSpeed(meters, seconds), units))
            RateKind.Pace -> LabeledText(AppText.named("stat_pace"), pace(meters, seconds, units))
            RateKind.SwimPace -> LabeledText(AppText.named("stat_pace"), swimPace(meters, seconds, units))
            RateKind.None -> null
        }

    /** Meters, or feet. */
    fun elevation(meters: Double?, units: UnitSystem): AppText {
        if (meters == null) return missing
        val value = formatWhole(UnitConversions.toElevationUnit(meters, units).roundToLong())
        return unit(units, "unit_elevation_m", "unit_elevation_ft", value)
    }

    /** A dive depth, or another short length like a jump, in meters, or feet, to one decimal. */
    fun depth(meters: Double?, units: UnitSystem): AppText {
        if (meters == null) return missing
        val value = formatDecimal(UnitConversions.toElevationUnit(meters, units), DEPTH_DECIMALS)
        return unit(units, "unit_elevation_m", "unit_elevation_ft", value)
    }

    /** A record temperature (tenths of °C), in °C or °F to one decimal, so a whole-degree sample reads exactly: 27 °C is 80.6 °F. */
    fun temperature(tenthsCelsius: Long?, units: UnitSystem): AppText {
        if (tenthsCelsius == null) return missing
        val value = formatDecimal(UnitConversions.toTemperatureUnit(tenthsCelsius, units), TEMPERATURE_DECIMALS)
        return unit(units, "unit_temperature_c", "unit_temperature_f", value)
    }

    /** A profile weight (grams) in kg or lb, to one decimal. */
    fun weight(grams: Long, units: UnitSystem): AppText {
        val value = formatDecimal(WeightConversions.toWeightUnit(grams, units), WEIGHT_DECIMALS)
        return unit(units, "unit_weight_kg", "unit_weight_lb", value)
    }

    private fun unit(units: UnitSystem, metric: String, imperial: String, value: String): AppText =
        AppText.named(
            when (units) {
                UnitSystem.Metric -> metric
                UnitSystem.Imperial -> imperial
            },
            AppText.written(value),
        )
}
