package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.datetime.TimeZone

/** The signed-in athlete's unit preference; `App` provides it from `UnitsRepository`. */
val LocalUnitSystem = staticCompositionLocalOf { UnitSystem.Metric }

/** The current time for UIs that show or bound by it (ages, date pickers). The system clock, unless a preview or test fixes it. */
val LocalClock = staticCompositionLocalOf<Clock> { Clock.System }

/** The viewer's time zone for UIs that show local times. The system's, unless a preview or test sets it. */
val LocalTimeZone = staticCompositionLocalOf { TimeZone.currentSystemDefault() }

/**
 * Display formatting for stats. Inputs are the SI values records store; output follows [LocalUnitSystem]. How each reads is
 * `:app-common-api`'s [StatText], shared with the native iOS UI; these read the athlete's units and the string, so they are composable.
 */
object Format {
    @Composable fun distance(meters: Double?): String = StatText.distance(meters, LocalUnitSystem.current).string()

    fun duration(duration: Duration): String = StatText.duration(duration.inWholeSeconds)

    fun duration(seconds: Long): String = StatText.duration(seconds)

    /** Minutes per kilometer, or per mile. */
    @Composable fun pace(meters: Double?, seconds: Long?): String = StatText.pace(meters, seconds, LocalUnitSystem.current).string()

    /** km/h, or mph. */
    @Composable fun speed(mmPerSecond: Long?): String = StatText.speed(mmPerSecond, LocalUnitSystem.current).string()

    /** Minutes per 100 meters, or per 100 yards. */
    @Composable fun swimPace(meters: Double?, seconds: Long?): String = StatText.swimPace(meters, seconds, LocalUnitSystem.current).string()

    /** The sport's average rate (speed, pace or swim pace), or null for sports where distance means little, like yoga or tennis. */
    @Composable
    fun rateStat(sport: String, meters: Double?, seconds: Long?): Stat? =
        StatText.rate(sport, meters, seconds, LocalUnitSystem.current)?.let { Stat(it.label.string(), it.value.string()) }

    /** Meters, or feet. */
    @Composable fun elevation(meters: Double?): String = StatText.elevation(meters, LocalUnitSystem.current).string()

    /** A dive depth, or another short length like a jump, in meters, or feet, to one decimal. */
    @Composable fun depth(meters: Double?): String = StatText.depth(meters, LocalUnitSystem.current).string()

    /** A record temperature (tenths of °C), in °C or °F to one decimal, so a whole-degree sample reads exactly: 27 °C is 80.6 °F. */
    @Composable fun temperature(tenthsCelsius: Long?): String = StatText.temperature(tenthsCelsius, LocalUnitSystem.current).string()

    /** A profile weight (grams) in kg or lb, to one decimal. */
    @Composable fun weight(grams: Long): String = StatText.weight(grams, LocalUnitSystem.current).string()
}
