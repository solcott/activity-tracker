package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList

/**
 * [chart] as a step line, since a threshold holds until it changes, filled beneath and running on to today at the right edge. [labels] are
 * the points' values as shown (one per point); without them it's a sparkline with no dots, for the collapsed overview.
 */
@Composable
internal fun ThresholdStepChart(
    chart: ThresholdChart,
    description: String,
    modifier: Modifier = Modifier,
    labels: ImmutableList<String>? = null,
    height: Dp = 150.dp,
) {
    val line = MaterialTheme.colorScheme.primary
    val fill = line.copy(alpha = FILL_ALPHA)
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = GRID_ALPHA)
    val hollow = MaterialTheme.colorScheme.surfaceContainerLowest
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
    val currentLabel = MaterialTheme.colorScheme.onSurface
    val pastLabel = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        val detailed = labels != null
        // Room above the highest point for its label, and inset from the sides so edge dots aren't clipped.
        val top = if (detailed) size.height * DETAILED_TOP else size.height * SPARK_INSET
        val bottom = size.height * (1 - if (detailed) DETAILED_BOTTOM else SPARK_INSET)
        val inset = if (detailed) 10.dp.toPx() else 0f
        fun at(point: ChartPoint) = Offset(inset + point.x * (size.width - 2 * inset), bottom - point.y * (bottom - top))
        val offsets = chart.points.map(::at)

        if (detailed) {
            GRID_LINES.forEach { drawLine(grid, Offset(0f, size.height * it), Offset(size.width, size.height * it), 1.dp.toPx()) }
        }
        val path =
            Path().apply {
                moveTo(offsets.first().x, offsets.first().y)
                offsets.zipWithNext().forEach { (previous, next) ->
                    lineTo(next.x, previous.y)
                    lineTo(next.x, next.y)
                }
                lineTo(size.width, offsets.last().y)
            }
        val area =
            Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(offsets.first().x, size.height)
                close()
            }
        drawPath(area, fill)
        drawPath(path, line, style = Stroke(width = (if (detailed) 2.25f else 1.5f).dp.toPx(), join = StrokeJoin.Round))
        if (labels != null) {
            chart.points.forEachIndexed { i, point ->
                drawDot(offsets[i], point.current, line, hollow)
                drawLabel(measurer, labels[i], offsets[i], labelStyle.copy(color = if (point.current) currentLabel else pastLabel))
            }
        }
    }
}

private fun DrawScope.drawDot(center: Offset, current: Boolean, line: Color, hollow: Color) {
    val radius = (if (current) 7f else 5f).dp.toPx()
    drawCircle(if (current) line else hollow, radius, center)
    drawCircle(line, radius, center, style = Stroke(2.dp.toPx()))
}

/** [text] centred above the dot at [center], kept inside the chart. */
private fun DrawScope.drawLabel(measurer: TextMeasurer, text: String, center: Offset, style: TextStyle) {
    val layout = measurer.measure(text, style)
    val x = (center.x - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
    val y = (center.y - 9.dp.toPx() - layout.size.height).coerceAtLeast(0f)
    drawText(layout, topLeft = Offset(x, y))
}

private const val FILL_ALPHA = 0.14f
private const val GRID_ALPHA = 0.05f
private val GRID_LINES = listOf(0.25f, 0.5f, 0.75f)
private const val DETAILED_TOP = 0.22f
private const val DETAILED_BOTTOM = 0.16f
private const val SPARK_INSET = 0.12f
