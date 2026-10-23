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
import io.github.solcott.activitytracker.composeApp.resources.unit_pace_per_100m
import io.github.solcott.activitytracker.composeApp.resources.unit_pace_per_100yd
import io.github.solcott.activitytracker.composeApp.resources.unit_pace_per_km
import io.github.solcott.activitytracker.composeApp.resources.unit_pace_per_mi
import io.github.solcott.activitytracker.composeApp.resources.unit_speed_kmh
import io.github.solcott.activitytracker.composeApp.resources.unit_speed_mph
import io.github.solcott.activitytracker.composeApp.resources.unit_temperature_c
import io.github.solcott.activitytracker.composeApp.resources.unit_temperature_f
import io.github.solcott.activitytracker.composeApp.resources.unit_weight_kg
import io.github.solcott.activitytracker.composeApp.resources.unit_weight_lb
import io.github.solcott.activitytracker.composeApp.resources.value_missing
import io.github.solcott.activitytracker.data.sport.RateKind
import io.github.solcott.activitytracker.data.sport.SportCatalog
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.WeightConversions
import io.github.solcott.activitytracker.data.units.formatDecimal
import io.github.solcott.activitytracker.data.units.formatWhole
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource

/** The signed-in athlete's unit preference; `App` provides it from `UnitsRepository`. */
val LocalUnitSystem = staticCompositionLocalOf { UnitSystem.Metric }

/** The current time for UIs that show or bound by it (ages, date pickers). The system clock, unless a preview or test fixes it. */
val LocalClock = staticCompositionLocalOf<Clock> { Clock.System }

/** The viewer's time zone for UIs that show local times. The system's, unless a preview or test sets it. */
val LocalTimeZone = staticCompositionLocalOf { TimeZone.currentSystemDefault() }

/**
 * Display formatting for stats. Inputs are the SI values records store; output follows [LocalUnitSystem]. Values with units read their
 * templates from Compose resources, so those are composable.
 */
object Format {
    private const val DISTANCE_DECIMALS = 2
    private const val SPEED_DECIMALS = 1
    private const val WEIGHT_DECIMALS = 1
    private const val DEPTH_DECIMALS = 1
    private const val TEMPERATURE_DECIMALS = 1

    /** Below this, pace is noise from GPS jitter. */
    private const val MIN_PACE_DISTANCE_M = 10

    private const val SWIM_PACE_METERS = 100.0
    private const val SWIM_PACE_YARDS = 100.0

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

    /** Minutes per 100 meters, or per 100 yards. */
    @Composable
    fun swimPace(meters: Double?, seconds: Long?): String {
        if (meters == null || seconds == null || meters < MIN_PACE_DISTANCE_M) return stringResource(Res.string.value_missing)
        val units = LocalUnitSystem.current
        val metersPerUnit =
            when (units) {
                UnitSystem.Metric -> SWIM_PACE_METERS
                UnitSystem.Imperial -> SWIM_PACE_YARDS * UnitConversions.METERS_PER_YARD
            }
        val perUnit = duration((seconds / (meters / metersPerUnit)).roundToLong())
        return when (units) {
            UnitSystem.Metric -> stringResource(Res.string.unit_pace_per_100m, perUnit)
            UnitSystem.Imperial -> stringResource(Res.string.unit_pace_per_100yd, perUnit)
        }
    }

    /** The sport's average rate (speed, pace or swim pace), or null for sports where distance means little, like yoga or tennis. */
    @Composable
    fun rateStat(sport: String, meters: Double?, seconds: Long?): Stat? =
        when (SportCatalog.rate(sport)) {
            RateKind.Speed -> Stat(stringResource(Res.string.stat_speed), speed(UnitConversions.averageSpeed(meters, seconds)))
            RateKind.Pace -> Stat(stringResource(Res.string.stat_pace), pace(meters, seconds))
            RateKind.SwimPace -> Stat(stringResource(Res.string.stat_pace), swimPace(meters, seconds))
            RateKind.None -> null
        }

    /** Meters, or feet. */
    @Composable
    fun elevation(meters: Double?): String {
        if (meters == null) return stringResource(Res.string.value_missing)
        val units = LocalUnitSystem.current
        val value = formatWhole(UnitConversions.toElevationUnit(meters, units).roundToLong())
        return when (units) {
            UnitSystem.Metric -> stringResource(Res.string.unit_elevation_m, value)
            UnitSystem.Imperial -> stringResource(Res.string.unit_elevation_ft, value)
        }
    }

    /** A dive depth, or another short length like a jump, in meters, or feet, to one decimal. */
    @Composable
    fun depth(meters: Double?): String {
        if (meters == null) return stringResource(Res.string.value_missing)
        val units = LocalUnitSystem.current
        val value = formatDecimal(UnitConversions.toElevationUnit(meters, units), DEPTH_DECIMALS)
        return when (units) {
            UnitSystem.Metric -> stringResource(Res.string.unit_elevation_m, value)
            UnitSystem.Imperial -> stringResource(Res.string.unit_elevation_ft, value)
        }
    }

    /** A record temperature (tenths of °C), in °C or °F to one decimal, so a whole-degree sample reads exactly: 27 °C is 80.6 °F. */
    @Composable
    fun temperature(tenthsCelsius: Long?): String {
        if (tenthsCelsius == null) return stringResource(Res.string.value_missing)
        val units = LocalUnitSystem.current
        val value = formatDecimal(UnitConversions.toTemperatureUnit(tenthsCelsius, units), TEMPERATURE_DECIMALS)
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
