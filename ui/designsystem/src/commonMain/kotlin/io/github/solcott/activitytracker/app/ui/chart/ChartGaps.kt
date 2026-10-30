package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.ui.graphics.Path
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.LineCartesianLayer.PointConnector
import kotlin.math.abs
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** The x of each of [points] that follows a gap ([SeriesPoint.gapBefore]), in order: where a chart breaks its line. */
internal fun gapsIn(points: List<SeriesPoint>): ImmutableList<Double> = points.filter { it.gapBefore }.map { it.x }.toImmutableList()

/**
 * Joins points as [base] does, except into a point at one of [gapsAt] (x values, in order), which follows a gap in the samples: there the
 * path moves, so the line breaks.
 */
internal class GapConnector(private val base: PointConnector, private val gapsAt: List<Double>) : PointConnector {
    override fun connect(context: CartesianDrawingContext, path: Path, x1: Float, y1: Float, x2: Float, y2: Float) {
        if (context.isGapAt(x2)) path.moveTo(x2, y2) else base.connect(context, path, x1, y1, x2, y2)
    }

    /** Whether the point drawn at [canvasX] is one of [gapsAt], placed as Vico places a line layer's points. */
    private fun CartesianDrawingContext.isGapAt(canvasX: Float): Boolean {
        val direction = if (isLtr) 1 else -1
        val start = (if (isLtr) layerBounds.left else layerBounds.right) + direction * layerDimensions.startPadding - scroll
        val spacing = layerDimensions.xSpacing.toDouble()
        if (spacing <= 0) return false
        val x = dataX(canvasX - start, direction, spacing, ranges.minX, ranges.xStep)
        return gapsAt.containsNear(x, tolerance = HALF_PIXEL * ranges.xStep / spacing)
    }

    private companion object {
        const val HALF_PIXEL = 0.5
    }
}

/**
 * The x value of a point drawn [fromStart] pixels from where the layer starts drawing: the inverse of Vico's `drawingStart + direction *
 * xSpacing * (x - minX) / xStep`.
 */
internal fun dataX(fromStart: Float, direction: Int, xSpacing: Double, minX: Double, xStep: Double): Double =
    minX + fromStart * direction / xSpacing * xStep

/** Whether sorted [this] holds a value within [tolerance] of [x]. */
internal fun List<Double>.containsNear(x: Double, tolerance: Double): Boolean {
    val found = binarySearch(x)
    if (found >= 0) return true
    val i = -(found + 1)
    return (i < size && abs(this[i] - x) <= tolerance) || (i > 0 && abs(this[i - 1] - x) <= tolerance)
}
