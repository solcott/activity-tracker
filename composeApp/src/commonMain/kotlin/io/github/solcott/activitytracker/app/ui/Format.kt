package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.unit_distance_km
import io.github.solcott.activitytracker.composeApp.resources.unit_elevation_m
import io.github.solcott.activitytracker.composeApp.resources.unit_pace_per_km
import io.github.solcott.activitytracker.composeApp.resources.value_missing
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.compose.resources.stringResource

/** Display formatting for stats. Values with units read their templates from Compose resources, so those are composable. */
object Format {
    private const val METERS_PER_KM = 1000.0
    private const val HUNDREDTHS_PER_KM = 100

    /** Below this, pace is noise from GPS jitter. */
    private const val MIN_PACE_DISTANCE_M = 10

    @Composable
    fun distance(meters: Double?): String {
        if (meters == null) return stringResource(Res.string.value_missing)
        val hundredths = (meters / METERS_PER_KM * HUNDREDTHS_PER_KM).roundToLong()
        val fraction = (hundredths % HUNDREDTHS_PER_KM).toString().padStart(2, '0')
        return stringResource(Res.string.unit_distance_km, "${hundredths / HUNDREDTHS_PER_KM}.$fraction")
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

    /** Minutes per kilometer. */
    @Composable
    fun pace(meters: Double?, seconds: Long?): String {
        if (meters == null || seconds == null || meters < MIN_PACE_DISTANCE_M) return stringResource(Res.string.value_missing)
        val secPerKm = (seconds / (meters / METERS_PER_KM)).roundToLong()
        return stringResource(Res.string.unit_pace_per_km, duration(secPerKm))
    }

    @Composable
    fun elevation(meters: Double?): String =
        meters?.let { stringResource(Res.string.unit_elevation_m, it.roundToLong()) } ?: stringResource(Res.string.value_missing)
}
