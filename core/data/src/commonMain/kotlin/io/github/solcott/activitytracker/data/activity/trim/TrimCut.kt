package io.github.solcott.activitytracker.data.activity.trim

import kotlin.math.min

/**
 * What a trim cuts from an activity's totals, measured on its streams or route. Totals that add up (distance, moving time, gain and loss)
 * become the original less the cut, so a short trim of a device's 45.12 km doesn't turn into a stream sum's 44.70 km.
 */
@Suppress("LongParameterList") // Each total a cut takes from, and what scales the rest.
internal class TrimCut
private constructor(
    /** Meters cut, or null when nothing says how far the athlete went. */
    val distance: Double?,
    /** Seconds of moving time cut, or null when nothing says when they moved. */
    val moving: Double?,
    /** Meters of elevation gain cut. */
    val gain: Double,
    /** Meters of elevation loss cut. */
    val loss: Double,
    /** A manual entry: no streams and no route, so only the times change. */
    val manual: Boolean,
    /** The kept span's share of the activity's elapsed time. */
    private val spanShare: Double,
    private val keptSeconds: Int,
) {
    /** Moving time left of [original] seconds. */
    fun keptMoving(original: Double): Double =
        when {
            manual -> min(original, keptSeconds.toDouble())
            moving != null -> (original - moving).coerceIn(0.0, keptSeconds.toDouble())
            else -> original * spanShare
        }

    /** The kept share of [original] moving time (else of elapsed time), for totals only a device can measure, which scale with it. */
    fun share(original: Double?): Double =
        if (original != null && original > 0) (keptMoving(original) / original).coerceIn(0.0, 1.0) else spanShare

    companion object {
        @Suppress("CyclomaticComplexMethod") // Each end and each total, cut or not.
        fun of(input: TrimInput, span: KeptSpan, series: TrimSeries? = input.series): TrimCut {
            val end = maxOf(input.elapsedSeconds, series?.time?.lastOrNull() ?: 0)
            val kept = span.keptElapsed(input)
            val cutsStart = span.from > 0
            val cutsEnd = span.to < end
            val distance =
                series?.distance?.let { d ->
                    val atStart = if (cutsStart) series.distanceAt(span.from) ?: 0.0 else 0.0
                    val atEnd = if (cutsEnd) d.last() - (series.distanceAt(span.to) ?: d.last()) else 0.0
                    atStart + atEnd
                }
            val moving = series?.let { s ->
                val start = if (cutsStart) s.movingSeconds(0, span.from) else 0.0
                val tail = if (cutsEnd) s.movingSeconds(span.to, end) else 0.0
                if (start == null || tail == null) null else start + tail
            }
            val startClimb = if (cutsStart) series?.gainLoss(0, span.from) else null
            val endClimb = if (cutsEnd) series?.gainLoss(span.to, end) else null
            return TrimCut(
                distance = distance,
                moving = moving,
                gain = ((startClimb?.gain ?: 0) + (endClimb?.gain ?: 0)).toDouble(),
                loss = ((startClimb?.loss ?: 0) + (endClimb?.loss ?: 0)).toDouble(),
                manual = input.streams == null && input.route.isEmpty(),
                spanShare = if (input.elapsedSeconds > 0) kept.toDouble() / input.elapsedSeconds else 1.0,
                keptSeconds = kept,
            )
        }
    }
}
