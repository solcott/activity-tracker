package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.multiplatform.cartesian.decoration.Decoration

/**
 * [spans] as marks along the bottom of the chart's layer, each a bar in its colour on its casing, and each selected one shaded over the
 * whole height in [shade] under the layers, so the line stays on top. Drawn straight onto the chart's canvas, like [AreaUnderlay].
 */
internal class SpanMarks(private val spans: List<SeriesSpan>, shade: Color) : Decoration {
    private val shadePaint = Paint().apply { color = shade }
    private val paint = Paint()

    override fun drawUnderLayers(context: CartesianDrawingContext) {
        with(context) {
            val frame = areaFrame()
            canvas.save()
            canvas.clipRect(layerBounds)
            spans.filter { it.selected }.forEach { span -> canvas.drawRect(rectOf(span, frame, layerBounds.top), shadePaint) }
            canvas.restore()
        }
    }

    override fun drawOverLayers(context: CartesianDrawingContext) {
        with(context) {
            val frame = areaFrame()
            val height = STRIP_HEIGHT.pixels
            val edge = CASING.pixels
            val top = layerBounds.bottom - height
            canvas.save()
            canvas.clipRect(layerBounds)
            spans.forEach { span ->
                val bar = rectOf(span, frame, top)
                paint.color = span.casing
                canvas.drawRect(bar, paint)
                paint.color = span.color
                canvas.drawRect(Rect(bar.left + edge, bar.top + edge, bar.right - edge, bar.bottom - edge), paint)
            }
            canvas.restore()
        }
    }

    /** [span]'s x across the layer, from [top] to the layer's bottom, at least a hairline wide. */
    private fun CartesianDrawingContext.rectOf(span: SeriesSpan, frame: AreaFrame, top: Float): Rect {
        val a = frame.x(span.from)
        val b = frame.x(span.to)
        val left = minOf(a, b)
        val right = maxOf(maxOf(a, b), left + MIN_WIDTH.pixels)
        return Rect(left, top, right, layerBounds.bottom)
    }

    private companion object {
        val STRIP_HEIGHT = 6.dp
        val CASING = 1.dp
        val MIN_WIDTH = 2.dp
    }
}
