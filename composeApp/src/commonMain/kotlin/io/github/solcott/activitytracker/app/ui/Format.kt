package io.github.solcott.activitytracker.app.ui

import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object Format {
    private const val METERS_PER_KM = 1000.0
    private const val HUNDREDTHS_PER_KM = 100

    /** Below this, pace is noise from GPS jitter. */
    private const val MIN_PACE_DISTANCE_M = 10

    fun distance(meters: Double?): String {
        if (meters == null) return "–"
        val hundredths = (meters / METERS_PER_KM * HUNDREDTHS_PER_KM).roundToLong()
        val fraction = (hundredths % HUNDREDTHS_PER_KM).toString().padStart(2, '0')
        return "${hundredths / HUNDREDTHS_PER_KM}.$fraction km"
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
    fun pace(meters: Double?, seconds: Long?): String {
        if (meters == null || seconds == null || meters < MIN_PACE_DISTANCE_M) return "–"
        val secPerKm = (seconds / (meters / METERS_PER_KM)).roundToLong()
        return "${duration(secPerKm)} /km"
    }

    fun elevation(meters: Double?): String = meters?.let { "${it.roundToLong()} m" } ?: "–"
}
