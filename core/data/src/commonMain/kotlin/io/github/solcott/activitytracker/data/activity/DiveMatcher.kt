package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** What an imported dive repeats. */
sealed interface DuplicateOf {
    /** A dive already in the athlete's log. */
    data class Existing(val activity: OwnActivity) : DuplicateOf

    /** An earlier dive in the same file, by its index there. */
    data class InFile(val index: Int) : DuplicateOf
}

/** [hoursApart] is 0 for the same dive; otherwise it's a dive of the same length that many hours off, likely read in the wrong zone. */
data class DiveMatch(val of: DuplicateOf, val hoursApart: Int = 0)

/**
 * Finds the dives in an import that are already in the athlete's log, or appear twice in the file (two computers on one dive, say).
 *
 * Two dives are the same when their times overlap by at least half the shorter one: computers' clocks drift by minutes, not by half a dive.
 * A dive of the same length (within 10%) a whole number of hours off is likely the same one read in another zone, which a zone-less dive
 * log invites, so it matches too, flagged by [DiveMatch.hoursApart].
 */
object DiveMatcher {
    /** For each of [dives] (placed in the zone the athlete picked), what it duplicates, or null. Only dives are compared. */
    fun match(dives: List<ImportedActivity>, existing: List<OwnActivity>): List<DiveMatch?> {
        val logged =
            existing.filter { it.record.sport == Sport.DIVE }.map { it to Span(it.record.startedAt, it.record.elapsedTime.seconds) }
        val spans = dives.map { Span(it.startedAt, it.elapsed) }
        return dives.indices.map { i ->
            if (dives[i].type.sport != Sport.DIVE) return@map null
            val span = spans[i]
            logged.firstOrNull { (_, other) -> span.sameAs(other) }?.let { (activity) -> DiveMatch(DuplicateOf.Existing(activity)) }
                ?: logged.firstNotNullOfOrNull { (activity, other) ->
                    span.hoursFrom(other)?.let { DiveMatch(DuplicateOf.Existing(activity), it) }
                }
                ?: (0 until i)
                    .firstOrNull { j -> dives[j].type.sport == Sport.DIVE && span.sameAs(spans[j]) }
                    ?.let { DiveMatch(DuplicateOf.InFile(it)) }
        }
    }

    private class Span(val start: Instant, val length: Duration) {
        val end: Instant = start + length

        /** The two overlap by half the shorter; a zero-length one needs starts within a few minutes. */
        fun sameAs(other: Span): Boolean {
            val shorter = minOf(length, other.length)
            val overlap = minOf(end, other.end) - maxOf(start, other.start)
            return if (shorter <= Duration.ZERO) (start - other.start).absoluteValue <= CLOCK_DRIFT else overlap >= shorter / 2
        }

        /** The whole hours (signed, never 0) [other] is off by, when it's the same length; null otherwise. */
        fun hoursFrom(other: Span): Int? {
            val longer = maxOf(length, other.length)
            if (longer <= Duration.ZERO || (length - other.length).absoluteValue > longer * LENGTH_TOLERANCE) return null
            val offset = start - other.start
            val hours = (offset.inWholeMinutes.toDouble() / MINUTES_PER_HOUR).roundToInt()
            val off = (offset - hours.hours).absoluteValue
            return hours.takeIf { it != 0 && abs(it) <= MAX_ZONE_HOURS && off <= CLOCK_DRIFT }
        }
    }

    private val CLOCK_DRIFT = 3.minutes
    private const val LENGTH_TOLERANCE = 0.1
    private const val MINUTES_PER_HOUR = 60
    /** UTC-12 to UTC+14 apart, rounded: the most two zones differ by in practice. */
    private const val MAX_ZONE_HOURS = 14
}
