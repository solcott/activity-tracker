package io.github.solcott.activitytracker.location

import kotlin.math.roundToLong

/** Elevation gain and loss in whole meters, both at least 0. */
data class GainLoss(val gain: Long, val loss: Long)

/**
 * Elevation gain and loss over a series of heights (segments/segment-elevation), shared by terrain samples and track altitude so both count
 * climbing the same way. A height only moves the count once it's [ELEVATION_THRESHOLD_M] or more from the last height counted, so noise and
 * small undulations don't add up.
 */
object ElevationGain {
    /** How far a height must move from the last one counted, in meters, to count as climbing or descending. */
    const val ELEVATION_THRESHOLD_M = 3.0

    /** [heights]' gain and loss in meters, skipping null samples; null when fewer than two heights are known. */
    fun of(heights: List<Double?>, thresholdM: Double = ELEVATION_THRESHOLD_M): GainLoss? {
        val known = heights.filterNotNull()
        if (known.size < 2) return null
        // The last turning point counted, the direction since it (1 up, -1 down, 0 not yet [thresholdM] away), and the farthest height
        // reached in that direction: a climb or descent is counted to its extreme once the heights turn back by [thresholdM].
        var last = known.first()
        var direction = 0
        var extreme = last
        var gain = 0.0
        var loss = 0.0
        for (h in known) {
            when (direction) {
                0 ->
                    if (h - last >= thresholdM) {
                        direction = 1
                        extreme = h
                    } else if (last - h >= thresholdM) {
                        direction = -1
                        extreme = h
                    }
                1 ->
                    if (h > extreme) {
                        extreme = h
                    } else if (extreme - h >= thresholdM) {
                        gain += extreme - last
                        last = extreme
                        direction = -1
                        extreme = h
                    }
                else ->
                    if (h < extreme) {
                        extreme = h
                    } else if (h - extreme >= thresholdM) {
                        loss += last - extreme
                        last = extreme
                        direction = 1
                        extreme = h
                    }
            }
        }
        if (direction == 1) gain += extreme - last
        if (direction == -1) loss += last - extreme
        return GainLoss(gain.roundToLong(), loss.roundToLong())
    }
}
