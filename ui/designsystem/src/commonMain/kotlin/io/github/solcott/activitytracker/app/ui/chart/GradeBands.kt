package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.solcott.activitytracker.data.chart.BandStop
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The colours an elevation profile is tinted by, one per grade band from flattest to steepest (green, yellow-green, yellow, orange, red),
 * and the hard-stop brushes that draw them along a chart at the stops [BandStop.runs] chose. Darker shades on a light surface and lighter
 * ones on a dark surface, so each band stays distinct from the surface in both themes.
 */
object GradeBands {
    /** The five band colours for the current theme's surface. */
    @Composable
    @ReadOnlyComposable
    fun colors(): ImmutableList<Color> = if (MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE) ON_DARK else ON_LIGHT

    /**
     * A horizontal brush across the chart in [colors] by [stops], each band's colour held to the next stop and switching sharply there, at
     * [alpha]. A chart with no stops gets the first colour.
     */
    fun brush(stops: List<BandStop>, colors: List<Color>, alpha: Float = 1f): Brush {
        fun color(band: Int) = colors[band.coerceIn(0, colors.lastIndex)].copy(alpha = alpha)
        if (stops.isEmpty()) return Brush.horizontalGradient(listOf(color(0), color(0)))
        // Each change repeats its position, ending one colour and starting the next exactly there.
        val colorStops = buildList {
            stops.forEachIndexed { i, (at, band) ->
                if (i > 0) add(at to color(stops[i - 1].band))
                add(at to color(band))
            }
            add(1f to color(stops.last().band))
        }
        @Suppress("SpreadOperator") // Compose takes stops only as varargs; one small array per chart.
        return Brush.horizontalGradient(*colorStops.toTypedArray())
    }

    private const val DARK_SURFACE = 0.5f

    internal val ON_LIGHT = persistentListOf(Color(0xFF43A047), Color(0xFF9E9D24), Color(0xFFF57F17), Color(0xFFEF6C00), Color(0xFFD32F2F))
    internal val ON_DARK = persistentListOf(Color(0xFF81C784), Color(0xFFD4E157), Color(0xFFFFD54F), Color(0xFFFFB74D), Color(0xFFE57373))
}
