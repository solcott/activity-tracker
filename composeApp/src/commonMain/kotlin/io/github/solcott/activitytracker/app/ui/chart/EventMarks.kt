package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.runtime.Immutable
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.multiplatform.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.multiplatform.common.Position
import com.patrykandpatrick.vico.multiplatform.common.component.LineComponent
import com.patrykandpatrick.vico.multiplatform.common.component.TextComponent

/** A vertical [line] across the chart at each of [marks], its text at the top beside it, kept inside the chart. */
internal data class EventMarks(private val marks: List<EventMark>, private val line: LineComponent, private val style: TextComponent) :
    Decoration {
    override fun drawOverLayers(context: CartesianDrawingContext) {
        with(context) {
            val start = if (isLtr) layerBounds.left else layerBounds.right
            marks.forEach { mark ->
                val offset = (mark.x - ranges.minX) / ranges.xStep * layerDimensions.xSpacing + layerDimensions.startPadding - scroll
                val x = start + layoutDirectionMultiplier * offset.toFloat()
                line.drawVertical(this, x, layerBounds.top, layerBounds.bottom)
                val width = style.getWidth(this, mark.text)
                style.draw(
                    this,
                    mark.text,
                    // Beside the line, flipped to its other side near the end so it isn't cut off.
                    x = if (x + width + LABEL_GAP > canvasSize.width) x - width - LABEL_GAP else x + LABEL_GAP,
                    y = layerBounds.top,
                    horizontalPosition = if (isLtr) Position.Horizontal.End else Position.Horizontal.Start,
                    verticalPosition = Position.Vertical.Bottom,
                )
            }
        }
    }

    private companion object {
        const val LABEL_GAP = 4f
    }
}

/** [text] marked at [x], in data units. */
@Immutable internal data class EventMark(val x: Double, val text: String)
