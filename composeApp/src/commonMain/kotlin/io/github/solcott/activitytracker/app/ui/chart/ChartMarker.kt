package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.Interaction
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.multiplatform.common.Fill
import com.patrykandpatrick.vico.multiplatform.common.Insets
import com.patrykandpatrick.vico.multiplatform.common.Point
import com.patrykandpatrick.vico.multiplatform.common.component.rememberLineComponent
import com.patrykandpatrick.vico.multiplatform.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.multiplatform.common.component.rememberTextComponent
import kotlin.math.abs

/**
 * A readout of the point nearest the pointer: a dot on the line, a guideline down to the axis, and [text] for its x and y in a bubble above
 * it. Show it with [rememberScrubController].
 */
@Composable
internal fun rememberValueMarker(text: (x: Double, y: Double) -> String): CartesianMarker {
    val currentText by rememberUpdatedState(text)
    val colors = MaterialTheme.colorScheme
    val label =
        rememberTextComponent(
            style = MaterialTheme.typography.labelMedium.copy(color = colors.inverseOnSurface),
            padding = remember { Insets(horizontal = 8.dp, vertical = 4.dp) },
            background = rememberShapeComponent(Fill(colors.inverseSurface), RoundedCornerShape(6.dp)),
        )
    val dot = rememberShapeComponent(Fill(colors.primary), CircleShape, strokeFill = Fill(colors.surface), strokeThickness = 2.dp)
    val guideline = rememberLineComponent(Fill(colors.onSurfaceVariant.copy(alpha = GUIDELINE_ALPHA)), thickness = 1.dp)
    val formatter = remember {
        DefaultCartesianMarker.ValueFormatter { _, targets ->
            val target = targets.firstOrNull() as? LineCartesianLayerMarkerTarget
            target?.points?.firstOrNull()?.let { currentText(target.x, it.entry.y) }.orEmpty()
        }
    }
    return rememberDefaultCartesianMarker(
        label = label,
        valueFormatter = formatter,
        labelPosition = DefaultCartesianMarker.LabelPosition.AroundPoint,
        indicator = remember(dot) { { _ -> dot } },
        indicatorSize = 10.dp,
        guideline = guideline,
    )
}

/**
 * Shows the marker while the mouse hovers over the chart, and where a finger taps or drags across it; it stays put after a tap or drag so
 * the value can be read. A drag that starts mostly vertical is the page scrolling, so it hides the marker instead.
 */
@Composable
internal fun rememberScrubController(): CartesianMarkerController {
    val slop = LocalViewConfiguration.current.touchSlop
    return remember(slop) { ScrubController(slop / 2) }
}

private class ScrubController(private val slop: Float) : CartesianMarkerController {
    private var pressedAt: Point? = null
    private var scrolling = false

    override val acceptsLongPress = false

    override fun shouldAcceptInteraction(interaction: Interaction, targets: List<CartesianMarker.Target>) =
        interaction !is Interaction.Zoom && interaction !is Interaction.LongPress

    override fun shouldShowMarker(interaction: Interaction, targets: List<CartesianMarker.Target>): Boolean =
        when (interaction) {
            is Interaction.Press -> {
                pressedAt = interaction.point
                scrolling = false
                true
            }
            is Interaction.Move -> {
                // The list scrolls the chart along with the finger once past the touch slop, so only the first few pixels tell.
                pressedAt?.let { start ->
                    val dx = abs(interaction.point.x - start.x)
                    val dy = abs(interaction.point.y - start.y)
                    if (!scrolling && dy > slop && dy > dx) scrolling = true
                }
                !scrolling
            }
            is Interaction.Release -> {
                pressedAt = null
                !scrolling
            }
            is Interaction.Tap,
            is Interaction.Enter -> !scrolling
            is Interaction.Exit -> interaction.isInBounds
            else -> false
        }
}

private const val GUIDELINE_ALPHA = 0.5f
