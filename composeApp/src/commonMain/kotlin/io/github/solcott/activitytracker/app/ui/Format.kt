package io.github.solcott.activitytracker.app.ui

import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object Format {
    fun distance(meters: Double?): String {
        if (meters == null) return "–"
        val hundredths = (meters / 10).roundToLong()
        return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')} km"
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
        if (meters == null || seconds == null || meters < 10) return "–"
        val secPerKm = (seconds / (meters / 1000)).roundToLong()
        return "${secPerKm / 60}:${(secPerKm % 60).toString().padStart(2, '0')} /km"
    }

    fun elevation(meters: Double?): String = meters?.let { "${it.roundToLong()} m" } ?: "–"
}
