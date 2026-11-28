package io.github.solcott.activitytracker.data.activity.trim

import androidx.compose.runtime.Immutable
import kotlin.math.roundToLong

/** An activity's totals as the trim screen compares them before and after (feed/activity-trim); null where the activity has none. */
@Immutable data class TrimTotals(val distanceMeters: Double?, val movingSeconds: Long?, val elapsedSeconds: Long, val gainMeters: Double?)

/**
 * What the trim screen draws for an activity while its handles move (feed/activity-trim): its samples for the profile, where a distance
 * lands, and the totals a kept span leaves, worked out on samples read once, so each move costs one pass and no rebuilt activity.
 */
class TrimPreview private constructor(val input: TrimInput, private val series: TrimSeries?) {
    /** Seconds from the start to the end of the recording, the handles' range. */
    val elapsed: Int = input.endSeconds

    /** Seconds from the start of each sample; empty for a manual entry. */
    val time: List<Int>
        get() = series?.time.orEmpty()

    /** Meters above sea level at each sample, or null when the activity has no altitude. */
    val altitude: List<Double?>?
        get() = series?.altitude?.takeIf { values -> values.any { it != null } }

    /** Meters per second at each sample (from distance where it has one), or null when nothing says how fast. */
    val speed: List<Double?>?
        get() {
            val s = series ?: return null
            if (s.size < 2 || (s.distance == null && s.speed.none { it != null })) return null
            return List(s.size) { i -> if (i == 0) s.speedOver(0) else s.speedOver(i - 1) }
        }

    /** Where distances land in time, or null when the activity can't be trimmed by distance. */
    val distance: TrimDistance? = TrimDistance.of(input)

    /** [before], the activity's totals, as [span] leaves them: [ActivityTrimmer]'s rules, without rebuilding the activity. */
    fun totals(before: TrimTotals, span: KeptSpan): TrimTotals {
        if (span.isWhole(elapsed)) return before
        val cut = TrimCut.of(input, span, series)
        return TrimTotals(
            distanceMeters = before.distanceMeters?.let { d -> cut.distance?.let { (d - it).coerceAtLeast(0.0) } ?: d },
            movingSeconds = before.movingSeconds?.let { cut.keptMoving(it.toDouble()).roundToLong() },
            elapsedSeconds = span.keptElapsed(input).toLong(),
            gainMeters = before.gainMeters?.let { (it - cut.gain).coerceAtLeast(0.0) },
        )
    }

    companion object {
        fun of(input: TrimInput): TrimPreview = TrimPreview(input, input.series)
    }
}
