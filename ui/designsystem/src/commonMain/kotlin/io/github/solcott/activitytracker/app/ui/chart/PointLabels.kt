package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.Axis
import com.patrykandpatrick.vico.multiplatform.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.multiplatform.common.Position
import com.patrykandpatrick.vico.multiplatform.common.component.TextComponent

/**
 * A label centred above each of [labels]' points, [gap] above it and kept inside the chart. Vico's own data labels shrink to the room
 * between neighbouring points and skip the first and last, which loses values on a sparse history.
 */
data class PointLabels(private val labels: List<PointLabel>, private val gap: Dp) : Decoration {
    override fun drawOverLayers(context: CartesianDrawingContext) {
        with(context) {
            val yRange = ranges.getYRange(Axis.Position.Vertical.Start)
            val start = if (isLtr) layerBounds.left else layerBounds.right
            labels.forEach { label ->
                val offset = (label.x - ranges.minX) / ranges.xStep * layerDimensions.xSpacing + layerDimensions.startPadding - scroll
                val x = start + layoutDirectionMultiplier * offset.toFloat()
                val y = layerBounds.bottom - ((label.y - yRange.minY) / yRange.length * layerBounds.height).toFloat()
                val width = label.style.getWidth(this, label.text)
                val height = label.style.getHeight(this, label.text)
                label.style.draw(
                    this,
                    label.text,
                    x = (x - width / 2).coerceIn(0f, (canvasSize.width - width).coerceAtLeast(0f)),
                    y = (y - gap.pixels - height).coerceAtLeast(0f),
                    // x is the label's left edge in either direction.
                    horizontalPosition = if (isLtr) Position.Horizontal.End else Position.Horizontal.Start,
                    verticalPosition = Position.Vertical.Bottom,
                )
            }
        }
    }
}

/** [text] for the point at [x], [y] in data units, drawn with [style]. */
@Immutable data class PointLabel(val x: Double, val y: Double, val text: String, val style: TextComponent)
