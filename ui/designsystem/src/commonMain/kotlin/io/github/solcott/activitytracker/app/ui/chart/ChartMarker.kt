package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.multiplatform.cartesian.decoration.Decoration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** A decoration that draws nothing and keeps where its chart's layer was last drawn, for the chart's [ChartReadout]. */
@Stable
class ChartFrame : Decoration {
    var frame: AreaFrame? = null
        private set

    override fun drawOverLayers(context: CartesianDrawingContext) {
        frame = context.areaFrame()
    }
}

/**
 * A readout of the point of [points] nearest the pointer, which [StaticChart] draws over its chart: a guideline down the chart, a dot on
 * the line and on each of [others] with a point there, and [text] for the point's x and y in a bubble above it. Points are as plotted, on
 * the start axis's [yRange], and [frame] must be one of the chart's decorations.
 */
@Immutable
class ChartReadout(
    val frame: ChartFrame,
    val points: ImmutableList<SeriesPoint>,
    val yRange: ClosedFloatingPointRange<Double>,
    val others: ImmutableList<ImmutableList<SeriesPoint>> = persistentListOf(),
    val text: (x: Double, y: Double) -> String,
)

/**
 * [readout] in a layer of its own over its chart, shown as [ScrubGesture] decides. Moving it redraws only this layer: Vico redraws the
 * whole chart, through full-canvas software bitmaps, each time its own marker moves.
 */
@Composable
internal fun ReadoutOverlay(readout: ChartReadout, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(readout)
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelMedium.copy(color = colors.inverseOnSurface)
    val measurer = rememberTextMeasurer()
    val slop = LocalViewConfiguration.current.touchSlop
    val gesture = remember(slop) { ScrubGesture(slop / 2) }
    // The pointer's x as a value, NaN while hidden. Only drawing reads it, so a move redraws this layer and nothing else.
    val pointerX = remember { mutableDoubleStateOf(Double.NaN) }
    Spacer(
        modifier
            .graphicsLayer {}
            .pointerInput(gesture) {
                awaitPointerEventScope {
                    while (true) {
                        val pointerEvent = awaitPointerEvent()
                        val change = pointerEvent.changes.first()
                        val event = scrubEvent(pointerEvent.type, change.position, pointerEvent.changes.size, size) ?: continue
                        val shows = gesture.shows(event)
                        val frame = current.frame.frame
                        pointerX.doubleValue = if (shows && frame != null) frame.valueAt(change.position.x) else Double.NaN
                    }
                }
            }
            .drawBehind {
                val x = pointerX.doubleValue
                val frame = current.frame.frame
                if (x.isNaN() || frame == null || current.points.isEmpty()) return@drawBehind
                val point = current.points[nearestIndex(current.points, x)]
                val dots = readoutDots(point, current.others, frame, current.yRange)
                val bounds = frame.bounds
                val lineX = dots.first().x
                drawLine(
                    colors.onSurfaceVariant.copy(alpha = GUIDELINE_ALPHA),
                    Offset(lineX, bounds.top),
                    Offset(lineX, bounds.bottom),
                    strokeWidth = 1.dp.toPx(),
                )
                val radius = DOT_SIZE.toPx() / 2
                dots.forEach { dot ->
                    drawCircle(colors.surface, radius, dot)
                    drawCircle(colors.primary, radius - DOT_RING.toPx(), dot)
                }
                val padX = LABEL_PADDING_X.toPx()
                val padY = LABEL_PADDING_Y.toPx()
                val text =
                    measurer.measure(
                        current.text(point.x, point.y),
                        style,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        constraints = Constraints(maxWidth = (bounds.width - 2 * padX).toInt().coerceAtLeast(0)),
                    )
                val labelSize = Size(text.size.width + 2 * padX, text.size.height + 2 * padY)
                val topLeft = labelTopLeft(dots, labelSize, bounds)
                drawRoundRect(colors.inverseSurface, topLeft, labelSize, CornerRadius(LABEL_CORNER.toPx()))
                drawText(text, topLeft = topLeft + Offset(padX, padY))
            }
    )
}

/** [type] as [ScrubGesture] reads it, or null for events it ignores. A second finger ends the gesture, as it does for Vico's marker. */
private fun scrubEvent(type: PointerEventType, at: Offset, pointers: Int, size: IntSize): ScrubEvent? =
    when (type) {
        PointerEventType.Press -> if (pointers == 1) ScrubEvent.Press(at) else ScrubEvent.Release
        PointerEventType.Release -> ScrubEvent.Release
        PointerEventType.Move -> ScrubEvent.Move(at)
        PointerEventType.Enter -> ScrubEvent.Enter
        PointerEventType.Exit -> ScrubEvent.Exit(at.x in 0f..size.width.toFloat() && at.y in 0f..size.height.toFloat())
        else -> null
    }

private const val GUIDELINE_ALPHA = 0.5f
private val DOT_SIZE = 10.dp
private val DOT_RING = 2.dp
private val LABEL_PADDING_X = 8.dp
private val LABEL_PADDING_Y = 4.dp
private val LABEL_CORNER = 6.dp
