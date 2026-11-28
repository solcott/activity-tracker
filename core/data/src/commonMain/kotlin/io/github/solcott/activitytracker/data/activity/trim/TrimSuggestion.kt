package io.github.solcott.activitytracker.data.activity.trim

import androidx.compose.runtime.Immutable

/**
 * A trim the app offers for what looks like junk at either end of a recording (feed/activity-trim): standing time before the athlete set
 * off, and at the end either standing time after they stopped or a stop followed by a short stretch too fast for the activity, such as the
 * drive home. Null parts cut nothing.
 */
@Immutable
data class TrimSuggestion(val span: KeptSpan, val start: StartCut?, val end: EndCut?) {
    /** Standing for [seconds] before setting off. */
    @Immutable data class StartCut(val seconds: Int)

    /** What the end cut takes. */
    @Immutable
    sealed interface EndCut {
        val seconds: Int

        /** Standing for [seconds] after the last stretch of moving. */
        @Immutable data class Standing(override val seconds: Int) : EndCut

        /** A stop, then [meters] of travel too fast for the activity; [seconds] in all. */
        @Immutable data class Travel(override val seconds: Int, val meters: Double) : EndCut
    }

    companion object {
        /** Standing for less than this at the start isn't worth cutting. */
        const val MIN_START_SECONDS = 60

        /** Standing for less than this at the end isn't worth cutting. */
        const val MIN_END_SECONDS = 120

        /** A stop at least this long can end the activity before a fast tail. */
        const val MIN_STOP_SECONDS = 180

        /** Moving counts as sustained when this much of [WINDOW_SECONDS] is moving. */
        private const val SUSTAINED_SECONDS = 30
        private const val WINDOW_SECONDS = 40

        /** A fast tail is at most this long and this share of the distance. */
        private const val MAX_TAIL_SECONDS = 900
        private const val MAX_TAIL_SHARE = 0.15

        /** A fast tail's median speed is at least this many times the activity's and at least [TRAVEL_SPEED_MPS]. */
        private const val TAIL_SPEED_FACTOR = 1.5
        private const val TRAVEL_SPEED_MPS = 25 / 3.6

        /** The suggestion for [input], or null when it has no speed or distance or nothing to cut. */
        @Suppress("CyclomaticComplexMethod", "ReturnCount") // Each way a recording has nothing to suggest.
        fun of(input: TrimInput): TrimSuggestion? {
            val s = input.series?.takeIf { it.size >= 2 && (it.distance != null || it.speed.any { v -> v != null }) } ?: return null
            val end = input.endSeconds
            fun sustainedFrom(t: Int) = (s.movingSeconds(t, t + WINDOW_SECONDS) ?: 0.0) >= SUSTAINED_SECONDS
            fun sustainedTo(t: Int) = (s.movingSeconds(t - WINDOW_SECONDS, t) ?: 0.0) >= SUSTAINED_SECONDS
            // The first moving step from the first sustained stretch, and the last moving step's end before the last one ends.
            val firstSustained = s.time.indexOfFirst(::sustainedFrom).takeIf { it >= 0 } ?: return null
            val setOff = (firstSustained until s.size - 1).firstOrNull { s.isMoving(it) == true }?.let { s.time[it] } ?: return null
            val lastSustained = s.time.indexOfLast(::sustainedTo).takeIf { it > 0 } ?: return null
            val lastMove = (lastSustained - 1 downTo 0).firstOrNull { s.isMoving(it) == true }?.let { s.time[it + 1] } ?: return null
            val startCut = setOff.takeIf { it >= MIN_START_SECONDS }?.let { StartCut(it) }
            val endCut: EndCut? = if (end - lastMove >= MIN_END_SECONDS) EndCut.Standing(end - lastMove) else fastTail(s, setOff, end)
            if (startCut == null && endCut == null) return null
            val span = KeptSpan(startCut?.seconds ?: 0, end - (endCut?.seconds ?: 0))
            if (span.seconds < KeptSpan.MIN_SECONDS) return null
            return TrimSuggestion(span, startCut, endCut)
        }

        /** The last long stop and the fast, short stretch after it, as one cut; null when the end doesn't look like that. */
        @Suppress("CyclomaticComplexMethod", "ReturnCount") // Each way an end doesn't look like a drive.
        private fun fastTail(s: TrimSeries, setOff: Int, end: Int): EndCut.Travel? {
            val distance = s.distance ?: return null
            val moving = (0 until s.size - 1).map { i -> s.isMoving(i) == true }
            // The last run of standing intervals at least MIN_STOP_SECONDS long.
            var stopStart = -1
            var stopEnd = -1
            var i = s.size - 2
            while (i >= 0) {
                if (!moving[i]) {
                    var j = i
                    while (j > 0 && !moving[j - 1]) j--
                    if (s.time[i + 1] - s.time[j] >= MIN_STOP_SECONDS) {
                        stopStart = j
                        stopEnd = i + 1
                        break
                    }
                    i = j - 1
                } else {
                    i--
                }
            }
            if (stopStart < 0 || s.time[stopStart] <= setOff) return null
            val tailSeconds = end - s.time[stopEnd]
            val tailMeters = distance.last() - distance[stopEnd]
            if (tailSeconds <= 0 || tailSeconds > MAX_TAIL_SECONDS || tailMeters > MAX_TAIL_SHARE * distance.last()) return null
            val before = (0 until stopStart).filter { moving[it] }.mapNotNull { s.speedOver(it) }
            val tail = (stopEnd until s.size - 1).filter { moving[it] }.mapNotNull { s.speedOver(it) }
            if (before.isEmpty() || tail.isEmpty()) return null
            if (median(tail) < maxOf(TAIL_SPEED_FACTOR * median(before), TRAVEL_SPEED_MPS)) return null
            return EndCut.Travel(seconds = end - s.time[stopStart], meters = tailMeters)
        }

        private fun median(values: List<Double>): Double = values.sorted()[values.size / 2]
    }
}
