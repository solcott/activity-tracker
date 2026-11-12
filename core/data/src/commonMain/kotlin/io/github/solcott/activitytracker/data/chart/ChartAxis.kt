package io.github.solcott.activitytracker.data.chart

import androidx.compose.runtime.Immutable
import kotlin.math.ceil
import kotlin.math.floor

/**
 * A chart's y range from [min] to [max] on whole multiples of [step], a round 1, 2 or 5 times a power of ten (at least 1, since labels are
 * whole numbers), with at most [MAX_GAPS] gaps between gridlines. Every UI draws the axis it is given, so the gridlines and their labels
 * are the same everywhere.
 */
@Immutable
data class ChartAxis(val min: Double, val max: Double, val step: Double) {
    /** Each gridline's value, from [min] to [max]. */
    val ticks: List<Double>
        get() {
            if (step <= 0) return listOf(min)
            val count = ((max - min) / step + TICK_ROUNDING).toInt()
            return (0..count).map { min + it * step }
        }

    companion object {
        /**
         * A little headroom over [values] (more when [labelled] points carry labels above them), and a floor at zero for values that start
         * there, like power or speed; [inverted] for a chart plotted below zero. [timeValues] (a pace) step by whole seconds or minutes and
         * have no floor. [values] must not be empty.
         */
        fun of(values: List<Double>, labelled: Boolean = false, inverted: Boolean = false, timeValues: Boolean = false): ChartAxis {
            val low = values.min()
            val high = values.max()
            val pad = ((high - low) * Y_PADDING).coerceAtLeast(1.0)
            val top = if (labelled) (high - low) * LABELLED_Y_PADDING else pad
            val axis =
                if (timeValues) aroundTime(low - pad, high + top) else around(if (low >= 0 && low - pad < 0) 0.0 else low - pad, high + top)
            return if (inverted) ChartAxis(-axis.max, -axis.min, axis.step) else axis
        }

        /** The smallest round axis holding [low] to [high]. */
        fun around(low: Double, high: Double): ChartAxis {
            var magnitude = 1.0
            while (true) {
                for (multiple in NICE_STEPS) {
                    val step = multiple * magnitude
                    val min = floor(low / step) * step
                    val max = ceil(high / step) * step
                    if ((max - min) / step <= MAX_GAPS) return ChartAxis(min, max.coerceAtLeast(min + step), step)
                }
                magnitude *= DECADE
            }
        }

        /** Like [around], on a step a clock reads easily: 5, 10, 15 or 30 seconds, or 1, 2, 5, 10, 15 or 30 minutes. */
        fun aroundTime(low: Double, high: Double): ChartAxis {
            for (step in TIME_VALUE_STEPS) {
                val min = floor(low / step) * step
                val max = ceil(high / step) * step
                if ((max - min) / step <= MAX_GAPS) return ChartAxis(min, max.coerceAtLeast(min + step), step)
            }
            return around(low, high)
        }

        /** The most gaps between gridlines. */
        const val MAX_GAPS = 4

        private val NICE_STEPS = listOf(1.0, 2.0, 5.0)
        private val TIME_VALUE_STEPS = listOf(5.0, 10.0, 15.0, 30.0, 60.0, 120.0, 300.0, 600.0, 900.0, 1800.0)
        private const val DECADE = 10
        private const val Y_PADDING = 0.08
        private const val LABELLED_Y_PADDING = 0.25
        private const val TICK_ROUNDING = 1e-9
    }
}
