package io.github.solcott.activitytracker.activityfile

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Heart rate a strap stored in FIT `hr` messages rather than in records (a swim's HRM-Pro or HRM-Swim uploads it afterwards), merged into
 * the records that lack it. A port of Garmin's own merge (the FIT SDKs' `HrMesgUtils`), done here so every platform merges alike and a
 * record's own heart rate is kept.
 */
object HrMessages {
    /** A gap between readings is filled with the earlier one every [GAP_STEP_SECONDS], for up to [GAP_MAX_SECONDS]. */
    private const val GAP_STEP_SECONDS = 0.25
    private const val GAP_MAX_SECONDS = 5.0
    private const val MILLIS_PER_SECOND = 1000.0

    /** A reading at [seconds] since the epoch. */
    internal data class Reading(val seconds: Double, val bpm: Int)

    /**
     * [records] (sorted, every one with a timestamp) with heart rate filled in from [messages] where they have none: each takes the rounded
     * average of the readings after the previous record's time, up to its own. The first record takes those in the second before it.
     */
    fun merge(messages: List<DecodedHrMessage>, records: List<DecodedSample>): List<DecodedSample> {
        val readings = if (records.any { it.heartRate == null }) expand(messages).sortedBy { it.seconds } else emptyList()
        if (readings.isEmpty()) return records
        var next = 0
        var previousEnd: Double? = null
        return records.map { record ->
            val end = (record.timestampMillis ?: return@map record) / MILLIS_PER_SECOND
            val start = previousEnd?.takeIf { it < end } ?: (end - 1)
            previousEnd = end
            while (next < readings.size && readings[next].seconds <= start) next++
            var sum = 0
            var count = 0
            var i = next
            while (i < readings.size && readings[i].seconds <= end) {
                sum += readings[i].bpm
                count++
                i++
            }
            next = i
            if (record.heartRate != null || count == 0) record else record.copy(heartRate = (sum.toDouble() / count).roundToInt())
        }
    }

    /**
     * Every reading in [messages], placed in time: a message with a timestamp anchors its one event timestamp there, and later event
     * timestamps count from that anchor, unwrapping their rollover. Readings before the first anchor, or that run backwards, are dropped.
     */
    internal fun expand(messages: List<DecodedHrMessage>): List<Reading> {
        val readings = mutableListOf<Reading>()
        var anchorSeconds: Double? = null
        var anchorEvent = 0.0
        for (message in messages) {
            val timestamp = message.timestampMillis
            val event = message.eventTimestamps.firstOrNull()
            if (timestamp != null && event != null) {
                anchorEvent = event
                anchorSeconds = timestamp / MILLIS_PER_SECOND + (message.fractionalSeconds ?: 0.0)
            }
            val anchor = anchorSeconds ?: continue
            message.eventTimestamps.zip(message.filteredBpm).forEach { (event, bpm) ->
                val unwrapped =
                    when {
                        event >= anchorEvent -> event
                        anchorEvent - event > FitHeart.EVENT_TIMESTAMP_ROLLOVER / 2 -> event + FitHeart.EVENT_TIMESTAMP_ROLLOVER
                        else -> return@forEach
                    }
                val reading = Reading(anchor + (unwrapped - anchorEvent), bpm)
                readings.lastOrNull()?.let { readings += gapFill(it, reading) }
                readings += reading
            }
        }
        return readings
    }

    /** The earlier reading repeated every [GAP_STEP_SECONDS] across the gap to [current], for up to [GAP_MAX_SECONDS]. */
    private fun gapFill(previous: Reading, current: Reading): List<Reading> {
        val steps = ceil((abs(current.seconds - previous.seconds) - GAP_STEP_SECONDS) / GAP_STEP_SECONDS).toInt()
        return (1..steps.coerceIn(0, (GAP_MAX_SECONDS / GAP_STEP_SECONDS).toInt())).map { step ->
            Reading(previous.seconds + step * GAP_STEP_SECONDS, previous.bpm)
        }
    }
}
