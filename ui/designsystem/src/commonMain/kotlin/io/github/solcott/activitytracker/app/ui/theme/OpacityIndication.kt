package io.github.solcott.activitytracker.app.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/** iOS press feedback: the pressed control dims, instead of Material's ripple. */
internal object OpacityIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = OpacityIndicationNode(interactionSource)

    override fun hashCode(): Int = -1

    override fun equals(other: Any?): Boolean = other === this
}

private class OpacityIndicationNode(private val interactionSource: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private val alpha = Animatable(1f)
    private val paint = Paint()

    override fun onAttach() {
        coroutineScope.launch {
            val presses = mutableSetOf<PressInteraction.Press>()
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses += interaction
                    is PressInteraction.Release -> presses -= interaction.press
                    is PressInteraction.Cancel -> presses -= interaction.press
                }
                val pressed = presses.isNotEmpty()
                launch {
                    alpha.animateTo(if (pressed) PRESSED_ALPHA else 1f, tween(if (pressed) 0 else RELEASE_MILLIS)) { invalidateDraw() }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val value = alpha.value
        if (value >= 1f) {
            drawContent()
            return
        }
        drawIntoCanvas { canvas ->
            paint.alpha = value
            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
            drawContent()
            canvas.restore()
        }
    }
}

/** UIKit's highlighted alpha for plain buttons. */
private const val PRESSED_ALPHA = 0.35f
private const val RELEASE_MILLIS = 200
