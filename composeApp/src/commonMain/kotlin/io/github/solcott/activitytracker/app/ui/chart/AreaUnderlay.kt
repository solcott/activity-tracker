package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.multiplatform.cartesian.decoration.Decoration

/**
 * [points] filled in [color] from the chart's bottom edge (its top with [fromTop]) on a scale from [minY] to [maxY] of its own, under the
 * layers, held until the next point with [step], with no area across a gap ([SeriesPoint.gapBefore]). It draws straight onto the chart's
 * canvas: Vico draws each line through full-canvas software bitmaps, which on phones made a fill alone cost as much as the line in front of
 * it. The marker skips it, as decorations have no marker targets.
 */
internal class AreaUnderlay(
    private val points: List<SeriesPoint>,
    private val minY: Double,
    private val maxY: Double,
    color: Color,
    private val fromTop: Boolean = false,
    private val step: Boolean = false,
) : Decoration {
    private val paint = Paint().apply { this.color = color }
    private val path = Path()
    private var drawnIn: AreaFrame? = null

    override fun drawUnderLayers(context: CartesianDrawingContext) {
        with(context) {
            val frame =
                AreaFrame(
                    bounds = layerBounds,
                    start = if (isLtr) layerBounds.left else layerBounds.right,
                    direction = layoutDirectionMultiplier,
                    minX = ranges.minX,
                    xStep = ranges.xStep,
                    xSpacing = layerDimensions.xSpacing.toDouble(),
                    startPadding = layerDimensions.startPadding.toDouble(),
                    scroll = scroll.toDouble(),
                )
            // A static chart's frame changes only on a resize, so scrubbing repaints the same path.
            if (frame != drawnIn) {
                path.rewind()
                areaOutline(points, frame, minY..maxY, fromTop, step).forEachIndexed { i, at ->
                    if (i == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
                }
                path.close()
                drawnIn = frame
            }
            canvas.save()
            canvas.clipRect(layerBounds)
            canvas.drawPath(path, paint)
            canvas.restore()
        }
    }
}

/** Where a chart's layer sits and how Vico places x across it: [start] is the layer's leading edge, [direction] -1 right to left. */
internal data class AreaFrame(
    val bounds: Rect,
    val start: Float,
    val direction: Int,
    val minX: Double,
    val xStep: Double,
    val xSpacing: Double,
    val startPadding: Double,
    val scroll: Double,
) {
    fun x(value: Double): Float = start + direction * ((value - minX) / xStep * xSpacing + startPadding - scroll).toFloat()
}

/**
 * The outline of [points]' fill in [frame], [yRange] mapped onto its height and clamped to it: from the baseline (the bottom, or the top
 * with [fromTop]) under the first point, along the points, and back to the baseline under the last. With [step] each value holds until the
 * next point, across then up or down. Into a point after a gap it drops to the baseline, runs along it and rises to the point, so the fill
 * has no area across the gap where [GapConnector] breaks the line.
 */
internal fun areaOutline(
    points: List<SeriesPoint>,
    frame: AreaFrame,
    yRange: ClosedFloatingPointRange<Double>,
    fromTop: Boolean,
    step: Boolean,
): List<Offset> {
    if (points.isEmpty()) return emptyList()
    val bounds = frame.bounds
    val span = (yRange.endInclusive - yRange.start).takeIf { it > 0 } ?: 1.0
    fun y(value: Double) = (bounds.bottom - (value - yRange.start) / span * bounds.height).toFloat().coerceIn(bounds.top, bounds.bottom)
    val baseline = if (fromTop) bounds.top else bounds.bottom
    val outline = ArrayList<Offset>(points.size * 2 + 2)
    var previous = Offset(frame.x(points.first().x), y(points.first().y))
    outline += Offset(previous.x, baseline)
    outline += previous
    for (point in points.drop(1)) {
        val next = Offset(frame.x(point.x), y(point.y))
        when {
            point.gapBefore -> {
                outline += Offset(previous.x, baseline)
                outline += Offset(next.x, baseline)
            }
            step -> outline += Offset(next.x, previous.y)
        }
        outline += next
        previous = next
    }
    outline += Offset(previous.x, baseline)
    return outline
}
