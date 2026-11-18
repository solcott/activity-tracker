package io.github.solcott.activitytracker.data.units

private const val SECONDS_PER_HOUR = 3600
private const val SECONDS_PER_MINUTE = 60

/**
 * A length of time as `m:ss`, or `h:mm:ss` from an hour up: a duration, or a pace's time per unit. Never grouped: a duration is not a
 * quantity (ui/number-format).
 */
fun formatClock(seconds: Long): String {
    val h = seconds / SECONDS_PER_HOUR
    val m = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val s = seconds % SECONDS_PER_MINUTE
    val mm = m.toString().padStart(2, '0')
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:$mm:$ss" else "$m:$ss"
}
