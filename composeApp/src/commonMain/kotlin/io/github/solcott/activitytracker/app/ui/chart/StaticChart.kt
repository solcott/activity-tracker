package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianChart
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.multiplatform.cartesian.Zoom
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberVicoZoomState

/**
 * [chart] fitted to its full width and drawn once: no scrolling, zooming or animation. Charts are built on Vico from the pieces in this
 * package so they share the theme's colours. [readout] is drawn over the chart in a layer of its own, so scrubbing never redraws the chart;
 * give the chart no Vico marker.
 */
@Composable
internal fun StaticChart(
    chart: CartesianChart,
    model: CartesianChartModel,
    description: String,
    height: Dp,
    modifier: Modifier = Modifier,
    readout: ChartReadout? = null,
) {
    Box(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        CartesianChartHost(
            chart,
            model,
            Modifier.matchParentSize().graphicsLayer {},
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        )
        if (readout != null) ReadoutOverlay(readout, Modifier.matchParentSize())
    }
}

/**
 * The x step for a chart without an x axis: the whole range is one step. Vico's default is the GCD of the x gaps, which is tiny for
 * fractional or per-second x values.
 */
internal val WholeRangeXStep: (CartesianChartModel, Double, Double) -> Double = { _, minX, maxX -> (maxX - minX).takeIf { it > 0 } ?: 1.0 }

/** Holds each value until the next point: across, then up or down. */
internal val StepConnector = LineCartesianLayer.PointConnector { _, path, _, y1, x2, y2 ->
    path.lineTo(x2, y1)
    path.lineTo(x2, y2)
}

/** The theme's chart colours: [line] for the data, [area] beneath it, faint [grid] lines and the [baseline] a profile hangs from. */
@Immutable internal data class ChartColors(val line: Color, val area: Color, val grid: Color, val baseline: Color)

@Composable
internal fun chartColors(areaAlpha: Float): ChartColors {
    val line = MaterialTheme.colorScheme.primary
    return ChartColors(
        line = line,
        area = line.copy(alpha = areaAlpha),
        grid = MaterialTheme.colorScheme.onSurface.copy(alpha = GRID_ALPHA),
        baseline = MaterialTheme.colorScheme.outlineVariant,
    )
}

private const val GRID_ALPHA = 0.05f
