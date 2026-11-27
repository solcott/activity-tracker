package io.github.solcott.activitytracker.data.activity.trim

import io.github.solcott.activitytracker.activityfile.PerSecond
import io.github.solcott.activitytracker.data.streams.StreamAnalytics
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import kotlin.math.roundToInt

/**
 * Averages and maxima over part of an activity's streams, read one value per second recorded (feed/stream-resampling) so irregular sampling
 * doesn't skew them. A figure is null when its channel has no samples there.
 */
@Suppress("LongParameterList") // One per figure the kept streams give.
internal class KeptStats(
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val avgPower: Int?,
    val maxPower: Int?,
    val normalizedPower: Int?,
    /** Joules. */
    val workJoules: Double?,
    val avgCadence: Int?,
    val maxCadence: Int?,
    /** Millimeters per second. */
    val maxSpeed: Int?,
    /** Tenths of a degree Celsius. */
    val avgTemperature: Int?,
) {
    companion object {
        /** [data]'s figures between [from] and [to] seconds (inclusive), or over all of it. */
        fun of(data: ActivityStreamData, from: Int = Int.MIN_VALUE, to: Int = Int.MAX_VALUE): KeptStats {
            val indices = data.time.indices.filter { data.time[it] in from..to }
            val time = indices.map { data.time[it] }
            fun perSecond(channel: List<Int?>?): List<Int>? =
                channel?.let { values -> StreamAnalytics.perSecond(time, indices.map { values.getOrNull(it) }) }?.takeIf { it.isNotEmpty() }
            fun peak(channel: List<Int?>?): Int? = channel?.let { values -> indices.mapNotNull { values.getOrNull(it) }.maxOrNull() }
            val heartRate = perSecond(data.heartRate)
            val power = perSecond(data.power)
            // Devices leave the zeros of coasting and standing out of average cadence.
            val cadence = perSecond(data.cadence)?.filter { it > 0 }?.takeIf { it.isNotEmpty() }
            return KeptStats(
                avgHeartRate = heartRate?.average()?.roundToInt(),
                maxHeartRate = peak(data.heartRate),
                avgPower = power?.average()?.roundToInt(),
                maxPower = peak(data.power),
                normalizedPower = power?.let(PerSecond::normalizedPower),
                workJoules = power?.sumOf { it.toDouble() },
                avgCadence = cadence?.average()?.roundToInt(),
                maxCadence = peak(data.cadence),
                maxSpeed = peak(data.speed),
                avgTemperature = perSecond(data.temperature)?.average()?.roundToInt(),
            )
        }
    }
}
