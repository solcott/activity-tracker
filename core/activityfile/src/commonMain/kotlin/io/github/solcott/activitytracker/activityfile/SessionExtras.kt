package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.CycleCount
import kotlin.math.roundToInt
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** What [DecodedActivityMapper] takes from a file beyond its totals: step and stroke counts, and where its clock was. */
internal object SessionExtras {
    /** FIT times count seconds from 1989-12-31T00:00:00Z. */
    private const val FIT_EPOCH_SECONDS = 631_065_600L

    /** Every zone's UTC offset is a whole number of quarter hours. */
    private const val OFFSET_STEP_MINUTES = 15
    private const val SECONDS_PER_MINUTE = 60
    private const val MILLIS_PER_SECOND = 1000L
    private const val CENTI = 100.0

    /** The sessions' cycle count; on foot FIT counts strides, so it's doubled to steps. */
    fun cycles(sessions: List<DecodedSummary>, type: ActivityType): Int? {
        val total = sessions.mapNotNull { it.totalCycles }.takeIf { it.isNotEmpty() }?.sum()?.takeIf { it > 0 } ?: return null
        val count = if (CycleCount.unitOf(type.sport) == CycleCount.STEPS) total * 2 else total
        return count.takeIf { it <= Int.MAX_VALUE }?.toInt()
    }

    /** The first session's average stroke distance, in centimeters. */
    fun avgStrokeDistanceCentimeters(sessions: List<DecodedSummary>): Int? =
        sessions.firstNotNullOfOrNull { it.avgStrokeDistanceMeters?.takeIf { m -> m > 0 } }?.let { (it * CENTI).roundToInt() }

    /**
     * The device clock's lead on UTC, to the nearest 15 minutes (every zone's offset is a multiple): for a file whose times have no zone,
     * that of the zone its [DecodedActivity.localStart] was read in; for FIT, the activity message's local timestamp less its timestamp.
     * Null without either, or when the difference is no real offset.
     */
    fun utcOffsetMinutes(fit: DecodedActivity, startedAtMillis: Long): Int? {
        val seconds =
            fit.localStart?.let { it.toInstant(TimeZone.UTC).epochSeconds - startedAtMillis / MILLIS_PER_SECOND } ?: fitClockLead(fit)
        val minutes = seconds?.let { (it / (OFFSET_STEP_MINUTES * SECONDS_PER_MINUTE).toDouble()).roundToInt() * OFFSET_STEP_MINUTES }
        return minutes?.takeIf { it in ActivityRecord.MIN_UTC_OFFSET..ActivityRecord.MAX_UTC_OFFSET }
    }

    /** The FIT activity message's local timestamp less its timestamp, in seconds; null without both. */
    private fun fitClockLead(fit: DecodedActivity): Long? {
        val local = fit.activityLocalTimestampSeconds
        val utc = fit.activityTimestampMillis
        return if (local == null || utc == null) null else local + FIT_EPOCH_SECONDS - utc / MILLIS_PER_SECOND
    }
}
