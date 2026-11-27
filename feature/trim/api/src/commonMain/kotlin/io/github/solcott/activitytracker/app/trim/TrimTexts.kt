package io.github.solcott.activitytracker.app.trim

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.chart.ChartSpan
import io.github.solcott.activitytracker.app.chart.ChartTick
import io.github.solcott.activitytracker.app.chart.LineChart
import io.github.solcott.activitytracker.app.chart.LinePoint
import io.github.solcott.activitytracker.app.detail.CardLines
import io.github.solcott.activitytracker.app.detail.StreamKind
import io.github.solcott.activitytracker.app.detail.StreamUnits
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.DurationText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.activity.trim.TrimSuggestion
import io.github.solcott.activitytracker.data.activity.trim.TrimTotals
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds

/**
 * One of the trim screen's totals: its [label], its [value] over the kept span, and what it [was] before the trim (null when unchanged).
 */
@Immutable data class TrimTotalRow(val label: AppText, val value: AppText, val was: AppText?)

/** What the trim screen says that follows a rule (dev/shared-display-text), chosen here for the shared and the native screen. */
@Suppress("TooManyFunctions") // Everything the screen words, in one place for both UIs.
object TrimTexts {
    /** A step of the exact value by distance: a tenth of a kilometer, or of a mile, in meters. */
    fun distanceStepMeters(units: UnitSystem): Double = UnitConversions.fromDistanceUnit(DISTANCE_STEP, units)

    /** A step of the exact value by time, in seconds. */
    const val TIME_STEP_SECONDS = 10

    /** Where the [start] or the end lands: the time into the activity ("12:30 in"), or the distance from its start ("0.40 km"). */
    fun position(editor: TrimEditor, start: Boolean, units: UnitSystem): AppText {
        val seconds = if (start) editor.from else editor.to
        val meters = if (start) editor.fromMeters else editor.toMeters
        return if (editor.byDistance && meters != null) StatText.distance(meters, units)
        else AppText.named("trim_position_time", AppText.written(StatText.duration(seconds.toLong())))
    }

    /** The clock time the [start] or the end lands at, on the activity's own clock. */
    fun clock(editor: TrimEditor, start: Boolean): AppText =
        AppText.Time(editor.startedAtEpochSeconds + if (start) editor.from else editor.to, editor.utcOffsetMinutes)

    /** What a screen reader says for the [start] or the end handle's value: where it lands and at what clock time. */
    fun handleValue(editor: TrimEditor, start: Boolean, units: UnitSystem): AppText =
        AppText.named("trim_handle_value", position(editor, start, units), clock(editor, start))

    /** What the trim cuts at the [start] or the end, "Cuts 6:00 · 1.20 km"; "Nothing cut" when it keeps that end. */
    fun cut(editor: TrimEditor, start: Boolean, units: UnitSystem): AppText {
        val seconds = if (start) editor.cutStart else editor.cutEnd
        if (seconds <= 0) return AppText.named("trim_cut_none")
        val time = AppText.written(StatText.duration(seconds.toLong()))
        val meters = if (start) editor.cutStartMeters else editor.cutEndMeters
        return if (meters != null) AppText.named("trim_cut_distance", time, StatText.distance(meters, units))
        else AppText.named("trim_cut_time", time)
    }

    /**
     * The totals before and after: distance, moving time, elapsed time, and the elevation gain, else the average rate; a manual entry's
     * elapsed time and its distance, unchanged.
     */
    fun totals(editor: TrimEditor, units: UnitSystem): List<TrimTotalRow> {
        val before = editor.before
        val after = editor.after
        fun row(label: AppText, value: (TrimTotals) -> AppText): TrimTotalRow {
            val now = value(after)
            val then = value(before)
            return TrimTotalRow(label, now, AppText.named("trim_was", then).takeIf { now != then })
        }
        val distance = row(AppText.named("stat_distance")) { StatText.distance(it.distanceMeters, units) }
        val elapsed = row(AppText.named("page_elapsed_time")) { AppText.written(StatText.duration(it.elapsedSeconds)) }
        if (editor.manual) return listOf(elapsed, distance)
        val moving =
            row(AppText.named("stat_moving_time")) { t ->
                t.movingSeconds?.let { AppText.written(StatText.duration(it)) } ?: StatText.missing
            }
        val last =
            if (before.gainMeters != null) row(AppText.named("trim_total_gain")) { StatText.elevation(it.gainMeters, units) }
            else
                StatText.rate(editor.sport, before.distanceMeters, before.movingSeconds, units)?.let { rate ->
                    row(rate.label) { StatText.rate(editor.sport, it.distanceMeters, it.movingSeconds, units)?.value ?: StatText.missing }
                }
        return listOfNotNull(distance, moving, elapsed, last)
    }

    /** "Your effort on Ridgeline Climb will be removed", for each effort the kept span cuts into. */
    fun effortNotes(editor: TrimEditor): List<AppText> =
        editor.removedEfforts.map { AppText.named("trim_effort_removed", AppText.written(it.name)) }

    /** What [suggestion] cuts, in words: "Started 6 min early and drove 3.10 km after you stopped". */
    fun suggestion(suggestion: TrimSuggestion, units: UnitSystem): AppText? {
        val start = suggestion.start?.let { roughly(it.seconds) }
        return when (val end = suggestion.end) {
            null -> start?.let { AppText.named("trim_suggest_start", it) }
            is TrimSuggestion.EndCut.Standing -> {
                val stood = roughly(end.seconds)
                if (start != null) AppText.named("trim_suggest_start_stood", start, stood) else AppText.named("trim_suggest_stood", stood)
            }
            is TrimSuggestion.EndCut.Travel -> {
                val drove = StatText.distance(end.meters, units)
                if (start != null) AppText.named("trim_suggest_start_drove", start, drove) else AppText.named("trim_suggest_drove", drove)
            }
        }
    }

    /** [seconds] to the nearest minute in words, so 5:59 of standing reads as "6 min", not the "5 min" a floor would give. */
    private fun roughly(seconds: Int): AppText = DurationText.approximate(((seconds + HALF_MINUTE) / MINUTE * MINUTE).seconds)

    private const val MINUTE = 60
    private const val HALF_MINUTE = 30

    /** What a recording's or an import's offer says: the suggestion when there is one, else nothing more than its title. */
    fun offer(suggestion: TrimSuggestion?, applied: Boolean, units: UnitSystem): AppText? =
        when {
            applied -> AppText.named("trim_offer_applied")
            suggestion != null -> suggestion(suggestion, units)
            else -> null
        }

    /** The confirmation before a saved trim: how much each end loses, and that it can't be brought back. */
    fun confirm(editor: TrimEditor): AppText {
        val start = AppText.written(StatText.duration(editor.cutStart.toLong()))
        val end = AppText.written(StatText.duration(editor.cutEnd.toLong()))
        return when {
            editor.cutStart > 0 && editor.cutEnd > 0 -> AppText.named("trim_confirm_both", start, end)
            editor.cutStart > 0 -> AppText.named("trim_confirm_start", start)
            else -> AppText.named("trim_confirm_end", end)
        }
    }

    /** The exact value's label: a time into the activity, a distance in the athlete's unit, or for a manual entry a clock time. */
    fun exactLabel(editor: TrimEditor, units: UnitSystem): AppText =
        when {
            editor.manual -> AppText.named("trim_exact_clock")
            editor.byDistance && editor.offersDistance ->
                AppText.named(if (units == UnitSystem.Metric) "trim_exact_distance_km" else "trim_exact_distance_mi")
            else -> AppText.named("trim_exact_time")
        }

    /**
     * The profile the handles move along, on the activity's time, its x labels the time into it or, [TrimEditor.byDistance], the distance
     * there; the parts the trim cuts shaded. Null for a manual entry or a profile of fewer than two points.
     */
    @Suppress("ReturnCount") // A manual entry, or too few points to draw.
    fun profile(editor: TrimEditor, units: UnitSystem): LineChart? {
        val profile = editor.profile ?: return null
        val kind = if (profile.elevation) StreamKind.Elevation else StreamKind.Speed
        val points =
            profile.time.indices.mapNotNull { i ->
                profile.values[i]?.let { v ->
                    val value = if (profile.elevation) v else v * MM_PER_M
                    LinePoint(profile.time[i].toDouble(), StreamUnits.convert(kind, value, units))
                }
            }
        if (points.size < 2) return null
        val ticks =
            CardLines.timeTicks(editor.elapsed).map { tick ->
                val meters = profile.distance?.let { d -> d[nearest(profile.time, tick.value.toInt())] }
                if (editor.byDistance && meters != null) ChartTick(tick.value, StatText.distance(meters, units)) else tick
            }
        val readouts = points.map { AppText.written(StatText.duration(it.x.roundToLong())) }
        val chart =
            LineChart.of(
                points = points,
                xMin = 0.0,
                xMax = editor.elapsed.toDouble().coerceAtLeast(points.last().x),
                xTicks = ticks,
                yLabel = StreamUnits::axis,
                readouts = readouts,
                description = AppText.named(if (profile.elevation) "trim_profile_elevation" else "trim_profile_speed"),
            )
        return chart.withSpans(cutSpans(editor))
    }

    /** The cut parts as shaded spans of the profile. */
    fun cutSpans(editor: TrimEditor): List<ChartSpan> =
        listOfNotNull(
            ChartSpan(0.0, editor.from.toDouble(), rating = null, selected = true).takeIf { editor.from > 0 },
            ChartSpan(editor.to.toDouble(), editor.elapsed.toDouble(), rating = null, selected = true).takeIf {
                editor.to < editor.elapsed
            },
        )

    private fun nearest(times: List<Int>, t: Int): Int {
        var lo = 0
        var hi = times.lastIndex
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] < t) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private const val DISTANCE_STEP = 0.1
    private const val MM_PER_M = 1000.0
}
