package io.github.solcott.activitytracker.data.chart

import androidx.compose.runtime.Immutable

/** Where a band starts along a chart: [at], from 0 (its left edge) to 1 (its right), and the [band] from there. */
@Immutable
data class BandStop(val at: Float, val band: Int) {
    companion object {
        /**
         * Where each band starts along a chart, from 0 to 1: one stop for the first point's band, then one halfway between each pair of
         * points whose bands differ. [xFractions] says how far along the chart each point is, and [bands] its band. A run of fewer than
         * [minPoints] points in one band joins the run before it, so terrain hovering at a band's edge doesn't flicker between two colours.
         * Empty when there are no points.
         */
        fun runs(xFractions: List<Float>, bands: List<Int>, minPoints: Int = MIN_RUN_POINTS): List<BandStop> {
            require(xFractions.size == bands.size) { "one band per point" }
            if (bands.isEmpty()) return emptyList()
            // Runs of one band as (first point, band), short ones folded into the run before.
            val firsts = mutableListOf(0)
            for (i in 1 until bands.size) if (bands[i] != bands[i - 1]) firsts += i
            val kept = mutableListOf(firsts.first())
            for (r in 1 until firsts.size) {
                val length = (firsts.getOrNull(r + 1) ?: bands.size) - firsts[r]
                if (length >= minPoints && bands[firsts[r]] != bands[kept.last()]) kept += firsts[r]
            }
            return kept.mapIndexed { r, first ->
                BandStop(if (r == 0) 0f else (xFractions[first - 1] + xFractions[first]) / 2, bands[first])
            }
        }

        private const val MIN_RUN_POINTS = 3
    }
}
