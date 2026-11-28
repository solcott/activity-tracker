package io.github.solcott.activitytracker.app.trim

import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.activity.trim.TrimPreview
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatDecimal
import io.github.solcott.activitytracker.data.units.parseDecimal
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The exact value of one end of a trim (feed/activity-trim), as typed and stepped: a time into the activity by time, a distance in the
 * athlete's unit by distance, and a clock time for a manual entry, which has no samples to measure into.
 */
internal object TrimValues {
    /** The [start] or the end's value as the field shows it. */
    fun format(editor: TrimEditor, start: Boolean, units: UnitSystem): String {
        val seconds = if (start) editor.from else editor.to
        val meters = if (start) editor.fromMeters else editor.toMeters
        return when {
            editor.manual -> {
                val at = Instant.fromEpochSeconds(editor.startedAtEpochSeconds + seconds).toLocalDateTime(zone(editor)).time
                "${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}"
            }
            editor.byDistance && meters != null -> {
                formatDecimal(UnitConversions.toDistanceUnit(meters, units), DISTANCE_DECIMALS, separator = '.', grouping = null)
            }
            else -> StatText.duration(seconds.toLong())
        }
    }

    /** The second [text] puts the [start] or the end at, or null when it doesn't read as the field's value. */
    fun parse(text: String, editor: TrimEditor, preview: TrimPreview, start: Boolean, units: UnitSystem): Int? {
        val typed = text.trim()
        val distance = preview.distance
        return when {
            editor.manual -> clockSeconds(typed, editor)
            editor.byDistance && distance != null -> {
                val value = parseDecimal(typed)?.takeIf { it >= 0 } ?: return null
                val meters = UnitConversions.fromDistanceUnit(value, units)
                if (start) distance.startAt(meters) else distance.endAt(meters)
            }
            else -> durationSeconds(typed)
        }
    }

    /** The second one step [later] or earlier moves the [start] or the end to: 10 s by time, a tenth of a km or mi by distance. */
    fun step(editor: TrimEditor, preview: TrimPreview, start: Boolean, later: Boolean, units: UnitSystem): Int {
        val sign = if (later) 1 else -1
        val distance = preview.distance
        val meters = if (start) editor.fromMeters else editor.toMeters
        if (editor.byDistance && distance != null && meters != null) {
            val target = (meters + sign * TrimTexts.distanceStepMeters(units)).coerceIn(0.0, distance.total)
            return if (start) distance.startAt(target) else distance.endAt(target)
        }
        return (if (start) editor.from else editor.to) + sign * TrimTexts.TIME_STEP_SECONDS
    }

    /** `h:mm:ss`, `m:ss` or whole seconds as seconds. */
    @Suppress("ReturnCount") // Each way the text doesn't read as a time.
    private fun durationSeconds(text: String): Int? {
        val parts = text.split(':').map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
        if (parts.isEmpty() || parts.size > MAX_PARTS) return null
        if (parts.drop(1).any { it >= SIXTY }) return null
        return parts.fold(0L) { total, part -> total * SIXTY + part }.takeIf { it <= Int.MAX_VALUE }?.toInt()
    }

    /** `hh:mm` on the manual entry's clock, as seconds since its start (a time before the start is on the next day). */
    @Suppress("ReturnCount") // Each way the text doesn't read as a clock time.
    private fun clockSeconds(text: String, editor: TrimEditor): Int? {
        val parts = text.split(':').map { it.toIntOrNull() ?: return null }
        if (parts.size != 2) return null
        val (hour, minute) = parts
        if (hour !in 0 until HOURS || minute !in 0 until SIXTY) return null
        val startTime = Instant.fromEpochSeconds(editor.startedAtEpochSeconds).toLocalDateTime(zone(editor)).time
        val since = (hour * SIXTY + minute) * SIXTY - startTime.toSecondOfDay()
        return if (since < 0) since + DAY_SECONDS else since
    }

    private fun zone(editor: TrimEditor): TimeZone =
        editor.utcOffsetMinutes?.let { UtcOffset(seconds = it * SIXTY).asTimeZone() } ?: TimeZone.currentSystemDefault()

    private const val DISTANCE_DECIMALS = 2

    /** Hours, minutes and seconds. */
    private const val MAX_PARTS = 3
    private const val SIXTY = 60
    private const val HOURS = 24
    private const val DAY_SECONDS = 86_400
}
