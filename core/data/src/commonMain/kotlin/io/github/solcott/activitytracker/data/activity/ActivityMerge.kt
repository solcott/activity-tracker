package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import kotlin.math.abs

/**
 * Merging another recording of an activity into the one the athlete already has, filling only its gaps: what the athlete wrote (title,
 * description) and what defines the activity (sport, start, length) never change, and nothing it already has is overwritten.
 */
object ActivityMerge {
    /** [existing] with each summary field it lacks taken from [other]. */
    fun fillGaps(existing: ActivityRecord, other: ActivityRecord): ActivityRecord =
        existing.copy(
            subSport = existing.subSport ?: other.subSport,
            movingTime = existing.movingTime ?: other.movingTime,
            distance = existing.distance ?: other.distance,
            elevationGain = existing.elevationGain ?: other.elevationGain,
            avgSpeed = existing.avgSpeed ?: other.avgSpeed,
            maxSpeed = existing.maxSpeed ?: other.maxSpeed,
            avgHeartRate = existing.avgHeartRate ?: other.avgHeartRate,
            maxHeartRate = existing.maxHeartRate ?: other.maxHeartRate,
            avgTemperature = existing.avgTemperature ?: other.avgTemperature,
            polyline = existing.polyline ?: other.polyline,
            device = existing.device ?: other.device,
            sensors = existing.sensors ?: other.sensors,
        )

    /**
     * [existing]'s channels, plus each one only [other] has, resampled onto [existing]'s times. [other]'s times are [offsetSeconds] after
     * [existing]'s start; a sample with nothing of [other]'s within [MAX_GAP_SECONDS] stays empty.
     */
    fun fillMissing(existing: ActivityStreamData, other: ActivityStreamData, offsetSeconds: Int): ActivityStreamData {
        val times = other.time.map { it + offsetSeconds }
        fun resample(channel: List<Int?>?): List<Int?>? = channel?.let { values ->
            existing.time.map { t -> nearest(times, t)?.let { values.getOrNull(it) } }
        }
        return existing.copy(
            distance = existing.distance ?: resample(other.distance),
            altitude = existing.altitude ?: resample(other.altitude),
            speed = existing.speed ?: resample(other.speed),
            heartRate = existing.heartRate ?: resample(other.heartRate),
            power = existing.power ?: resample(other.power),
            cadence = existing.cadence ?: resample(other.cadence),
            temperature = existing.temperature ?: resample(other.temperature),
            depth = existing.depth ?: resample(other.depth),
        )
    }

    /** Whether [fillMissing] would add any of [other]'s channels to [existing]: all of them when there's no [existing] to fill. */
    fun addsChannels(existing: ActivityStreamData?, other: ActivityStreamData): Boolean {
        if (existing == null) return true
        fun adds(have: List<Int?>?, extra: List<Int?>?) = have == null && extra != null
        return adds(existing.distance, other.distance) ||
            adds(existing.altitude, other.altitude) ||
            adds(existing.speed, other.speed) ||
            adds(existing.heartRate, other.heartRate) ||
            adds(existing.power, other.power) ||
            adds(existing.cadence, other.cadence) ||
            adds(existing.temperature, other.temperature) ||
            adds(existing.depth, other.depth)
    }

    /** [data] with its times moved [offsetSeconds] later, for an activity that starts that much earlier; samples before it start go. */
    fun shifted(data: ActivityStreamData, offsetSeconds: Int): ActivityStreamData {
        if (offsetSeconds == 0) return data
        val keep = data.time.indices.filter { data.time[it] + offsetSeconds >= 0 }
        fun <T> List<T>?.kept() = this?.let { values -> keep.map { values[it] } }
        return data.copy(
            time = keep.map { data.time[it] + offsetSeconds },
            distance = data.distance.kept(),
            altitude = data.altitude.kept(),
            speed = data.speed.kept(),
            heartRate = data.heartRate.kept(),
            power = data.power.kept(),
            cadence = data.cadence.kept(),
            temperature = data.temperature.kept(),
            depth = data.depth.kept(),
            gears = data.gears?.mapNotNull { gear -> gear.copy(time = gear.time + offsetSeconds).takeIf { it.time >= 0 } },
        )
    }

    /** The index in sorted [times] closest to [t], if within [MAX_GAP_SECONDS]. */
    private fun nearest(times: List<Int>, t: Int): Int? {
        if (times.isEmpty()) return null
        val found = times.binarySearch(t)
        val index =
            if (found >= 0) {
                found
            } else {
                val after = -found - 1
                listOf(after - 1, after).filter { it in times.indices }.minBy { abs(times[it] - t) }
            }
        return index.takeIf { abs(times[it] - t) <= MAX_GAP_SECONDS }
    }

    /** Dive computers sample every 1 to 10 seconds; beyond this there's no sample to borrow. */
    const val MAX_GAP_SECONDS = 30
}
