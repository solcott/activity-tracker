package io.github.solcott.activitytracker.app.chart

import io.github.solcott.activitytracker.app.segment.SegmentSamples
import io.github.solcott.activitytracker.app.segment.chart
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatWhole
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Line charts as the native design gallery shows them: one in the accent colour with a gap in its samples, and a segment's profile. */
object LineChartSamples {
    /** Heart rate over half an hour, untinted, with no samples for two minutes in the middle. */
    val plain: LineChart
        get() {
            val points =
                (0..60).map { i ->
                    val seconds = i * SAMPLE_SECONDS
                    LinePoint(seconds, 120 + 25 * sin(seconds / 600 * PI), gapBefore = i == GAP_AT)
                }
            fun bpm(value: Double) = AppText.named("unit_bpm", AppText.written(formatWhole(value.roundToInt())))
            return LineChart.of(
                points = points,
                xMin = 0.0,
                xMax = points.last().x,
                xTicks = listOf(0, 10, 20, 30).map { ChartTick(it * 60.0, AppText.written("$it:00")) },
                yLabel = ::bpm,
                readouts = points.map { bpm(it.y) },
                description = AppText.written("Heart rate"),
            )
        }

    /** The climbing segment's profile in metric units, tinted by grade. */
    val profile: LineChart
        get() = checkNotNull(SegmentSamples.elevation.profile).chart(UnitSystem.Metric)

    private const val SAMPLE_SECONDS = 30.0
    private const val GAP_AT = 31
}
