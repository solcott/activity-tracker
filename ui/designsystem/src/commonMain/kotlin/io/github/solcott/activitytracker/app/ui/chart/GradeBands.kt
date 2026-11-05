package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The colours an elevation profile is tinted by, one per grade band from flattest to steepest (green, yellow-green, yellow, orange, red),
 * and the hard-stop brushes that draw them along a chart. Darker shades on a light surface and lighter ones on a dark surface, so each band
 * stays distinct from the surface in both themes.
 */
object GradeBands {
    /** The five band colours for the current theme's surface. */
    @Composable
    @ReadOnlyComposable
    fun colors(): ImmutableList<Color> = if (MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE) ON_DARK else ON_LIGHT

    /**
     * Where each band starts along the chart, from 0 (its left edge) to 1 (its right): one stop for the first point's band, then one
     * halfway between each pair of points whose bands differ. [xFractions] says how far along the chart each point is, and [bands] its
     * band. A run of fewer than [minPoints] points in one band joins the run before it, so terrain hovering at a band's edge doesn't
     * flicker between two colours. Empty when there are no points.
     */
    fun stops(xFractions: List<Float>, bands: List<Int>, minPoints: Int = MIN_RUN_POINTS): List<Pair<Float, Int>> {
        require(xFractions.size == bands.size) { "one band per point" }
        if (bands.isEmpty()) return emptyList()
        // Runs of one band as (first point, band), short ones folded into the run before.
        val runs = mutableListOf(0 to bands.first())
        for (i in 1 until bands.size) if (bands[i] != bands[i - 1]) runs += i to bands[i]
        val kept = mutableListOf(runs.first())
        for (r in 1 until runs.size) {
            val length = (runs.getOrNull(r + 1)?.first ?: bands.size) - runs[r].first
            if (length >= minPoints && runs[r].second != kept.last().second) kept += runs[r]
        }
        return kept.mapIndexed { r, (first, band) -> (if (r == 0) 0f else (xFractions[first - 1] + xFractions[first]) / 2) to band }
    }

    /**
     * A horizontal brush across the chart in [colors] by [stops], each band's colour held to the next stop and switching sharply there, at
     * [alpha]. A chart with no stops gets the first colour.
     */
    fun brush(stops: List<Pair<Float, Int>>, colors: List<Color>, alpha: Float = 1f): Brush {
        fun color(band: Int) = colors[band.coerceIn(0, colors.lastIndex)].copy(alpha = alpha)
        if (stops.isEmpty()) return Brush.horizontalGradient(listOf(color(0), color(0)))
        // Each change repeats its position, ending one colour and starting the next exactly there.
        val colorStops = buildList {
            stops.forEachIndexed { i, (at, band) ->
                if (i > 0) add(at to color(stops[i - 1].second))
                add(at to color(band))
            }
            add(1f to color(stops.last().second))
        }
        @Suppress("SpreadOperator") // Compose takes stops only as varargs; one small array per chart.
        return Brush.horizontalGradient(*colorStops.toTypedArray())
    }

    private const val DARK_SURFACE = 0.5f
    private const val MIN_RUN_POINTS = 3

    internal val ON_LIGHT = persistentListOf(Color(0xFF43A047), Color(0xFF9E9D24), Color(0xFFF57F17), Color(0xFFEF6C00), Color(0xFFD32F2F))
    internal val ON_DARK = persistentListOf(Color(0xFF81C784), Color(0xFFD4E157), Color(0xFFFFD54F), Color(0xFFFFB74D), Color(0xFFE57373))
}
