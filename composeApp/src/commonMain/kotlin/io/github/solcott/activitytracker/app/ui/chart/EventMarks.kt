package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.runtime.Immutable
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.multiplatform.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.multiplatform.common.Position
import com.patrykandpatrick.vico.multiplatform.common.component.LineComponent
import com.patrykandpatrick.vico.multiplatform.common.component.TextComponent

/**
 * A vertical [line] across the chart at each of [marks], its text beside it at the top (or the bottom, with [atBottom], so a second set of
 * marks doesn't overprint the first), kept inside the chart.
 */
internal data class EventMarks(
    private val marks: List<EventMark>,
    private val line: LineComponent,
    private val style: TextComponent,
    private val atBottom: Boolean = false,
) : Decoration {
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
                    y = if (atBottom) layerBounds.bottom else layerBounds.top,
                    horizontalPosition = if (isLtr) Position.Horizontal.End else Position.Horizontal.Start,
                    verticalPosition = if (atBottom) Position.Vertical.Top else Position.Vertical.Bottom,
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
