package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/** What a pointer did over a chart, as [ScrubGesture] reads it. */
internal sealed interface ScrubEvent {
    data class Press(val at: Offset) : ScrubEvent

    data class Move(val at: Offset) : ScrubEvent

    data object Release : ScrubEvent

    data object Enter : ScrubEvent

    /** The pointer left, or was reported leaving while still [inBounds], as a lifted finger can be. */
    data class Exit(val inBounds: Boolean) : ScrubEvent
}

/**
 * Whether a chart shows its readout: while the mouse hovers over the chart, and where a finger taps or drags across it, staying put after a
 * tap or drag so the value can be read. A drag that starts mostly vertical, past [slop] pixels, is the page scrolling, so it hides the
 * readout instead.
 */
internal class ScrubGesture(private val slop: Float) {
    private var pressedAt: Offset? = null
    private var scrolling = false

    /** Whether the readout shows after [event], at the pointer. */
    fun shows(event: ScrubEvent): Boolean =
        when (event) {
            is ScrubEvent.Press -> {
                pressedAt = event.at
                scrolling = false
                true
            }
            is ScrubEvent.Move -> {
                // The list scrolls the chart along with the finger once past the touch slop, so only the first few pixels tell.
                pressedAt?.let { start ->
                    val dx = abs(event.at.x - start.x)
                    val dy = abs(event.at.y - start.y)
                    if (!scrolling && dy > slop && dy > dx) scrolling = true
                }
                !scrolling
            }
            ScrubEvent.Release -> {
                pressedAt = null
                !scrolling
            }
            ScrubEvent.Enter -> !scrolling
            is ScrubEvent.Exit -> event.inBounds
        }
}
