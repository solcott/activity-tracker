package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.TrainingLoad
import kotlin.math.abs

/**
 * Merging another recording of an activity into the one the athlete already has, filling only its gaps: what the athlete wrote (title,
 * description) and what defines the activity (sport, start, length) never change, and nothing it already has is overwritten.
 */
object ActivityMerge {
    /** [existing] with each summary field it lacks taken from [other]. */
    @Suppress("CyclomaticComplexMethod") // An elvis per field.
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
            elevationLoss = existing.elevationLoss ?: other.elevationLoss,
            avgCadence = existing.avgCadence ?: other.avgCadence,
            maxCadence = existing.maxCadence ?: other.maxCadence,
            avgPower = existing.avgPower ?: other.avgPower,
            maxPower = existing.maxPower ?: other.maxPower,
            normalizedPower = existing.normalizedPower ?: other.normalizedPower,
            work = existing.work ?: other.work,
            calories = existing.calories ?: other.calories,
            frontShifts = existing.frontShifts ?: other.frontShifts,
            rearShifts = existing.rearShifts ?: other.rearShifts,
            jumps = existing.jumps ?: other.jumps,
            grit = existing.grit ?: other.grit,
            flow = existing.flow ?: other.flow,
            poolLength = existing.poolLength ?: other.poolLength,
            cycles = existing.cycles ?: other.cycles,
            avgStrokeDistance = existing.avgStrokeDistance ?: other.avgStrokeDistance,
            utcOffset = existing.utcOffset ?: other.utcOffset,
            polyline = existing.polyline ?: other.polyline,
            // Breaks go with the polyline they index.
            polylineGaps = if (existing.polyline != null) existing.polylineGaps else other.polylineGaps,
            device = existing.device ?: other.device,
            sensors = existing.sensors ?: other.sensors,
            dive = DiveSummary.fillGaps(existing.dive, other.dive),
            media = (existing.media.orEmpty() + other.media.orEmpty()).take(ActivityMedia.MAX).ifEmpty { null },
        )

    /** [existing]'s training load with each field it lacks taken from [other]'s; null when neither has one. */
    fun fillGaps(existing: TrainingLoad?, other: TrainingLoad?): TrainingLoad? {
        if (existing == null || other == null) return existing ?: other
        return existing.copy(
            ftp = existing.ftp ?: other.ftp,
            intensityFactor = existing.intensityFactor ?: other.intensityFactor,
            trainingStressScore = existing.trainingStressScore ?: other.trainingStressScore,
            aerobicTrainingEffect = existing.aerobicTrainingEffect ?: other.aerobicTrainingEffect,
            anaerobicTrainingEffect = existing.anaerobicTrainingEffect ?: other.anaerobicTrainingEffect,
            hrvRmssd = existing.hrvRmssd ?: other.hrvRmssd,
        )
    }

    /**
     * [existing]'s channels, plus each one only [other] has, resampled onto [existing]'s times. [other]'s times are [offsetSeconds] after
     * [existing]'s start; a sample with nothing of [other]'s within [MAX_GAP_SECONDS] stays empty. Gas switches, dive alerts, laps,
     * lengths, sets and legs come over, shifted onto [existing]'s start, each when it has none. Tank pressure comes over only when it can
     * line up with the record's tanks: the dive has none of its own ([existingTanks] is 0; it takes [other]'s too) or as many as [other]
     * has ([otherTanks], else its series).
     */
    fun fillMissing(
        existing: ActivityStreamData,
        other: ActivityStreamData,
        offsetSeconds: Int,
        existingTanks: Int = 0,
        otherTanks: Int? = null,
    ): ActivityStreamData {
        val times = other.time.map { it + offsetSeconds }
        fun resample(channel: List<Int?>): List<Int?> = existing.time.map { t -> nearest(times, t)?.let { channel.getOrNull(it) } }
        val filled =
            ActivityStreamData.INT_CHANNELS.fold(existing) { data, channel ->
                if (channel.get(data) != null) data else channel.with(data, channel.get(other)?.let(::resample))
            }
        val shiftedOther = shifted(other, offsetSeconds)
        return filled.copy(
            gasSwitches = existing.gasSwitches ?: shiftedOther.gasSwitches,
            diveAlerts = existing.diveAlerts ?: shiftedOther.diveAlerts,
            laps = existing.laps ?: shiftedOther.laps,
            lengths = existing.lengths ?: shiftedOther.lengths,
            sets = existing.sets ?: shiftedOther.sets,
            legs = existing.legs ?: shiftedOther.legs,
            tankPressures =
                existing.tankPressures
                    ?: other.tankPressures
                        ?.takeIf { tanksLineUp(existingTanks, otherTanks ?: it.size) }
                        ?.map { it.copy(pressure = resample(it.pressure)) },
        )
    }

    /** Whether [fillMissing] would add any of [other]'s channels to [existing]: all of them when there's no [existing] to fill. */
    fun addsChannels(existing: ActivityStreamData?, other: ActivityStreamData, existingTanks: Int = 0, otherTanks: Int? = null): Boolean {
        if (existing == null) return true
        val lists =
            listOf<(ActivityStreamData) -> List<*>?>(
                { it.gasSwitches },
                { it.diveAlerts },
                { it.laps },
                { it.lengths },
                { it.sets },
                { it.legs },
            )
        return ActivityStreamData.INT_CHANNELS.any { it.get(existing) == null && it.get(other) != null } ||
            lists.any { it(existing) == null && it(other) != null } ||
            (existing.tankPressures == null && other.tankPressures?.let { tanksLineUp(existingTanks, otherTanks ?: it.size) } == true)
    }

    private fun tanksLineUp(existingTanks: Int, otherTanks: Int) = existingTanks == 0 || existingTanks == otherTanks

    /**
     * [data] with its times moved [offsetSeconds] later, for an activity that starts that much earlier; samples before it start go. The gas
     * breathed at the new start is a switch at 0, an alert before it goes, and a lap, length, set or leg that started before it starts
     * at 0.
     */
    fun shifted(data: ActivityStreamData, offsetSeconds: Int): ActivityStreamData {
        if (offsetSeconds == 0) return data
        val keep = data.time.indices.filter { data.time[it] + offsetSeconds >= 0 }
        fun <T> List<T>.kept() = keep.map { this[it] }
        val moved =
            data.copy(
                time = keep.map { data.time[it] + offsetSeconds },
                gears = data.gears?.mapNotNull { gear -> gear.copy(time = gear.time + offsetSeconds).takeIf { it.time >= 0 } },
                gasSwitches =
                    data.gasSwitches?.let { switches ->
                        val moved = switches.map { it.copy(time = it.time + offsetSeconds) }
                        val before = moved.lastOrNull { it.time <= 0 }?.copy(time = 0)
                        listOfNotNull(before) + moved.filter { it.time > 0 }
                    },
                diveAlerts = data.diveAlerts?.mapNotNull { alert -> alert.copy(time = alert.time + offsetSeconds).takeIf { it.time >= 0 } },
                laps = data.laps?.mapNotNull { shift(it.start, it.elapsed, offsetSeconds)?.let { start -> it.copy(start = start) } },
                lengths = data.lengths?.mapNotNull { shift(it.start, it.elapsed, offsetSeconds)?.let { start -> it.copy(start = start) } },
                sets = data.sets?.mapNotNull { shift(it.start, it.elapsed, offsetSeconds)?.let { start -> it.copy(start = start) } },
                legs = data.legs?.mapNotNull { shift(it.start, it.elapsed, offsetSeconds)?.let { start -> it.copy(start = start) } },
                tankPressures = data.tankPressures?.map { it.copy(pressure = it.pressure.kept()) },
            )
        return ActivityStreamData.INT_CHANNELS.fold(moved) { shifted, channel -> channel.with(shifted, channel.get(data)?.kept()) }
    }

    /** A span's start moved [offsetSeconds] later, at 0 if it began before the new start; null if it ended by then. */
    private fun shift(start: Int, elapsed: Int, offsetSeconds: Int): Int? {
        val moved = start + offsetSeconds
        return if (moved + elapsed <= 0) null else moved.coerceAtLeast(0)
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
