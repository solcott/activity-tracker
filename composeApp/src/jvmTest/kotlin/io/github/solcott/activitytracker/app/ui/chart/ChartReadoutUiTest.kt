package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.multiplatform.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.multiplatform.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.multiplatform.common.Fill
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalTestApi::class)
class ChartReadoutUiTest {
    private val points = (0..60).map { SeriesPoint(it.toDouble(), 40.0 + it % 10) }.toImmutableList()

    /** Counts the chart's draws: Vico draws its decorations each time it draws the chart. */
    private class DrawCounter : Decoration {
        var draws = 0

        override fun drawUnderLayers(context: CartesianDrawingContext) {
            draws++
        }
    }

    /** What the test reads back from a composed [Chart]. Each test composes its chart once, so nothing depends on skipping. */
    @Stable
    private class Scene {
        val counter = DrawCounter()
        var asked: Pair<Double, Double>? = null
        var bubble = Color.Unspecified
    }

    @Composable
    private fun Chart(scene: Scene) {
        scene.bubble = MaterialTheme.colorScheme.inverseSurface
        val frame = remember { ChartFrame() }
        val line = LineCartesianLayer.rememberLine(fill = remember { LineCartesianLayer.LineFill.single(Fill(Color.Blue)) })
        val chart =
            rememberCartesianChart(
                rememberLineCartesianLayer(
                    LineCartesianLayer.LineProvider.series(line),
                    rangeProvider = remember { CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = 100.0) },
                ),
                decorations = remember(frame) { listOf(scene.counter, frame) },
                getXStep = WholeRangeXStep,
            )
        val model = remember { CartesianChartModel(LineCartesianLayerModel.build { series(points.map { it.x }, points.map { it.y }) }) }
        val readout =
            remember(frame) {
                ChartReadout(frame, points, 0.0..100.0) { x, y ->
                    scene.asked = x to y
                    "${x.toInt()} s · ${y.toInt()}"
                }
            }
        StaticChart(chart, model, DESCRIPTION, 140.dp, readout = readout)
    }

    private fun SemanticsNodeInteraction.bubblePixels(bubble: Color): Int {
        val pixels = captureToImage().toPixelMap()
        var count = 0
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) if (pixels[x, y] == bubble) count++
        return count
    }

    @Test
    fun aSidewaysDragReadsOutWithoutRedrawingTheChart() = runComposeUiTest {
        val scene = Scene()
        setContent { AppPreview(width = null) { Chart(scene) } }
        val chart = onNodeWithContentDescription(DESCRIPTION)
        assertEquals(0, chart.bubblePixels(scene.bubble))
        val drawn = scene.counter.draws
        assertTrue(drawn > 0)

        chart.performTouchInput { down(Offset(width * 0.1f, centerY)) }
        assertTrue(chart.bubblePixels(scene.bubble) > 0)
        repeat(STEPS) {
            chart.performTouchInput { moveBy(Offset(width * 0.85f / STEPS, 0f)) }
            assertTrue(chart.bubblePixels(scene.bubble) > 0)
        }
        chart.performTouchInput { moveTo(Offset(width - 1f, centerY)) }
        chart.performTouchInput { up() }

        // The readout stays after the drag, on the last point, and the chart beneath was never drawn again.
        assertTrue(chart.bubblePixels(scene.bubble) > 0)
        assertEquals(60.0 to 40.0, scene.asked)
        assertEquals(drawn, scene.counter.draws)
    }

    @Test
    fun aVerticalDragHidesTheReadout() = runComposeUiTest {
        val scene = Scene()
        setContent { AppPreview(width = null) { Chart(scene) } }
        val chart = onNodeWithContentDescription(DESCRIPTION)
        chart.performTouchInput {
            down(Offset(centerX, height * 0.2f))
            moveBy(Offset(0f, height * 0.5f))
        }
        assertEquals(0, chart.bubblePixels(scene.bubble))
        chart.performTouchInput { up() }
        assertEquals(0, chart.bubblePixels(scene.bubble))
    }

    @Test
    fun hoveringReadsOutTheNearestPointUntilThePointerLeaves() = runComposeUiTest {
        val scene = Scene()
        setContent { AppPreview(width = null) { Chart(scene) } }
        val chart = onNodeWithContentDescription(DESCRIPTION)
        val drawn = scene.counter.draws
        chart.performMouseInput {
            enter(Offset(1f, centerY))
            moveTo(Offset(1f, centerY))
        }
        assertTrue(chart.bubblePixels(scene.bubble) > 0)
        assertEquals(0.0 to 40.0, scene.asked)
        chart.performMouseInput { exit(Offset(-10f, centerY)) }
        assertEquals(0, chart.bubblePixels(scene.bubble))
        assertEquals(drawn, scene.counter.draws)
    }

    private companion object {
        const val DESCRIPTION = "chart"
        const val STEPS = 12
    }
}
