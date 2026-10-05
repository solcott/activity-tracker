package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.stat_pace
import io.github.solcott.activitytracker.composeApp.resources.stat_speed
import io.github.solcott.activitytracker.composeApp.resources.unit_distance_km
import io.github.solcott.activitytracker.composeApp.resources.unit_distance_mi
import io.github.solcott.activitytracker.composeApp.resources.unit_elevation_ft
import io.github.solcott.activitytracker.composeApp.resources.unit_elevation_m
import io.github.solcott.activitytracker.composeApp.resources.unit_pace_per_km
import io.github.solcott.activitytracker.composeApp.resources.unit_pace_per_mi
import io.github.solcott.activitytracker.composeApp.resources.unit_speed_kmh
import io.github.solcott.activitytracker.composeApp.resources.unit_speed_mph
import io.github.solcott.activitytracker.composeApp.resources.unit_temperature_c
import io.github.solcott.activitytracker.composeApp.resources.unit_temperature_f
import io.github.solcott.activitytracker.composeApp.resources.unit_weight_kg
import io.github.solcott.activitytracker.composeApp.resources.unit_weight_lb
import io.github.solcott.activitytracker.composeApp.resources.value_missing
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.WeightConversions
import io.github.solcott.activitytracker.data.units.formatDecimal
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.compose.resources.stringResource

/** The signed-in athlete's unit preference; `App` provides it from `UnitsRepository`. */
val LocalUnitSystem = staticCompositionLocalOf { UnitSystem.Metric }

/**
 * Display formatting for stats. Inputs are the SI values records store; output follows [LocalUnitSystem]. Values with units read their
 * templates from Compose resources, so those are composable.
 */
object Format {
    private const val DISTANCE_DECIMALS = 2
    private const val SPEED_DECIMALS = 1
    private const val WEIGHT_DECIMALS = 1

    /** Below this, pace is noise from GPS jitter. */
    private const val MIN_PACE_DISTANCE_M = 10

    @Composable
    fun distance(meters: Double?): String {
        if (meters == null) return stringResource(Res.string.value_missing)
        val units = LocalUnitSystem.current
        val value = formatDecimal(UnitConversions.toDistanceUnit(meters, units), DISTANCE_DECIMALS)
        return when (units) {
            UnitSystem.Metric -> stringResource(Res.string.unit_distance_km, value)
            UnitSystem.Imperial -> stringResource(Res.string.unit_distance_mi, value)
        }
    }

    fun duration(duration: Duration): String {
        val total = duration.inWholeSeconds
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        val mm = m.toString().padStart(2, '0')
        val ss = s.toString().padStart(2, '0')
        return if (h > 0) "$h:$mm:$ss" else "$m:$ss"
    }

    fun duration(seconds: Long): String = duration(seconds.seconds)

    /** Minutes per kilometer, or per mile. */
    @Composable
    fun pace(meters: Double?, seconds: Long?): String {
        if (meters == null || seconds == null || meters < MIN_PACE_DISTANCE_M) return stringResource(Res.string.value_missing)
        val units = LocalUnitSystem.current
        val perUnit = duration((seconds / UnitConversions.toDistanceUnit(meters, units)).roundToLong())
        return when (units) {
            UnitSystem.Metric -> stringResource(Res.string.unit_pace_per_km, perUnit)
            UnitSystem.Imperial -> stringResource(Res.string.unit_pace_per_mi, perUnit)
        }
    }

    /** km/h, or mph. */
    @Composable
    fun speed(mmPerSecond: Long?): String {
        if (mmPerSecond == null) return stringResource(Res.string.value_missing)
        val units = LocalUnitSystem.current
        val value = formatDecimal(UnitConversions.toSpeedUnit(mmPerSecond, units), SPEED_DECIMALS)
        return when (units) {
            UnitSystem.Metric -> stringResource(Res.string.unit_speed_kmh, value)
            UnitSystem.Imperial -> stringResource(Res.string.unit_speed_mph, value)
        }
    }

    /** Rides read as speed, everything else as pace. */
    @Composable
    fun rateStat(sport: String, meters: Double?, seconds: Long?): Stat =
        if (sport == Sport.RIDE) {
            Stat(stringResource(Res.string.stat_speed), speed(UnitConversions.averageSpeed(meters, seconds)))
        } else {
            Stat(stringResource(Res.string.stat_pace), pace(meters, seconds))
        }

    /** Meters, or feet. */
    @Composable
    fun elevation(meters: Double?): String {
        if (meters == null) return stringResource(Res.string.value_missing)
        val units = LocalUnitSystem.current
        val value = UnitConversions.toElevationUnit(meters, units).roundToLong()
        return when (units) {
            UnitSystem.Metric -> stringResource(Res.string.unit_elevation_m, value)
            UnitSystem.Imperial -> stringResource(Res.string.unit_elevation_ft, value)
        }
    }

    /** A record temperature (tenths of °C), in whole °C or °F. */
    @Composable
    fun temperature(tenthsCelsius: Long?): String {
        if (tenthsCelsius == null) return stringResource(Res.string.value_missing)
        val units = LocalUnitSystem.current
        val value = UnitConversions.toTemperatureUnit(tenthsCelsius, units).roundToLong()
        return when (units) {
            UnitSystem.Metric -> stringResource(Res.string.unit_temperature_c, value)
            UnitSystem.Imperial -> stringResource(Res.string.unit_temperature_f, value)
        }
    }

    /** A profile weight (grams) in kg or lb, to one decimal. */
    @Composable
    fun weight(grams: Long): String {
        val units = LocalUnitSystem.current
        val value = formatDecimal(WeightConversions.toWeightUnit(grams, units), WEIGHT_DECIMALS)
        return when (units) {
            UnitSystem.Metric -> stringResource(Res.string.unit_weight_kg, value)
            UnitSystem.Imperial -> stringResource(Res.string.unit_weight_lb, value)
        }
    }
}
