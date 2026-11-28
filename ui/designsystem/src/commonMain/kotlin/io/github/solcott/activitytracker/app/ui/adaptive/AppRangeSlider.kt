package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RangeSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * A slider with two thumbs choosing a range within [valueRange], such as a stretch along a route. Material's on every platform, coloured by
 * `AppTheme`: a thumb drags, a tap moves the nearer thumb, and a focused thumb steps with the arrow keys. [onValueChangeFinished] fires
 * when a drag ends. With [labels], each thumb reads to a screen reader by its own name and value, and its adjust action moves only that
 * thumb.
 *
 * With [precision], a drag slows as the pointer moves below the track (feed/activity-trim): [precision] gives the share of full speed for
 * the distance below the track's centre, and [onPrecisionChange] hears each new share while it changes and null when the drag ends. A drag
 * that starts on neither thumb and goes mostly up or down is left to the page, so it still scrolls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    labels: RangeSliderLabels? = null,
    precision: ((below: Dp) -> Float)? = null,
    onPrecisionChange: (Float?) -> Unit = {},
) {
    if (labels == null && precision == null) {
        RangeSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = modifier,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
        )
        return
    }
    Box(modifier) {
        RangeSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            // Material's thumbs read as "Range start" with a percentage; the two nodes below say what they are instead.
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
        )
        if (labels != null) RangeSliderSemantics(value, onValueChange, valueRange, enabled, onValueChangeFinished, labels)
        if (precision != null) {
            val drag =
                PrecisionDrag(
                    rememberUpdatedState(value),
                    valueRange,
                    rememberUpdatedState(onValueChange),
                    rememberUpdatedState(onValueChangeFinished),
                    rememberUpdatedState(precision),
                    rememberUpdatedState(onPrecisionChange),
                )
            // Over the slider, so Material's own drag never sees the pointer: this one takes the drag, or leaves it to the page.
            Box(Modifier.matchParentSize().pointerInput(enabled, valueRange) { if (enabled) with(drag) { run() } })
        }
    }
}

/** Each thumb as its own adjustable node, named by [labels]. Semantics only: no pointer input, so touches reach the slider beneath. */
@Composable
private fun BoxScope.RangeSliderSemantics(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChangeFinished: (() -> Unit)?,
    labels: RangeSliderLabels,
) {
    Box(
        Modifier.matchParentSize().semantics {
            contentDescription = labels.start
            stateDescription = labels.startValue
            progressBarRangeInfo = ProgressBarRangeInfo(value.start, valueRange.start..value.endInclusive)
            if (!enabled) disabled()
            setProgress { target ->
                onValueChange(target.coerceIn(valueRange.start, value.endInclusive)..value.endInclusive)
                onValueChangeFinished?.invoke()
                true
            }
        },
    )
    Box(
        Modifier.matchParentSize().semantics {
            contentDescription = labels.end
            stateDescription = labels.endValue
            progressBarRangeInfo = ProgressBarRangeInfo(value.endInclusive, value.start..valueRange.endInclusive)
            if (!enabled) disabled()
            setProgress { target ->
                onValueChange(value.start..target.coerceIn(value.start, valueRange.endInclusive))
                onValueChangeFinished?.invoke()
                true
            }
        },
    )
}

/**
 * The slider's own drag when it slows below the track: the nearer thumb at the press, moved by the pointer's sideways travel times the
 * share [precision] gives for how far below the track it is. Each new share starts from where the thumb is, so it never jumps. A press that
 * lifts without moving moves the nearer thumb there, as Material's does.
 */
private class PrecisionDrag(
    private val value: State<ClosedFloatingPointRange<Float>>,
    private val valueRange: ClosedFloatingPointRange<Float>,
    private val onValueChange: State<(ClosedFloatingPointRange<Float>) -> Unit>,
    private val onFinished: State<(() -> Unit)?>,
    private val precision: State<(Dp) -> Float>,
    private val onPrecision: State<(Float?) -> Unit>,
) {
    private val span: Float
        get() = valueRange.endInclusive - valueRange.start

    // One gesture: choosing to take it, then following it.
    @Suppress("CyclomaticComplexMethod", "LongMethod", "LoopWithTooManyJumpStatements")
    suspend fun PointerInputScope.run() = awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val width = size.width.toFloat().coerceAtLeast(1f)
        val centre = size.height / 2f
        fun x(v: Float) = (v - valueRange.start) / span.coerceAtLeast(Float.MIN_VALUE) * width
        val current = value.value
        val start =
            abs(down.position.x - x(current.start)) <= abs(down.position.x - x(current.endInclusive)) &&
                // Both thumbs at one place: the one that can move that way is chosen once the drag says which way.
                !(current.start == current.endInclusive &&
                    current.endInclusive < valueRange.endInclusive &&
                    down.position.x > x(current.start))
        val onThumb = abs(down.position.x - x(if (start) current.start else current.endInclusive)) <= THUMB_REACH.toPx()
        var taken = false
        var anchorValue = 0f
        var anchorX = 0f
        var factor = 1f
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (!taken && !change.isConsumed) {
                    set(start, valueRange.start + (change.position.x / width).coerceIn(0f, 1f) * span)
                    onFinished.value?.invoke()
                }
                break
            }
            val moved = change.position - down.position
            if (!taken) {
                if (change.isConsumed) break
                if (moved.getDistance() < viewConfiguration.touchSlop) continue
                if (!onThumb && abs(moved.y) > abs(moved.x)) break
                taken = true
                anchorValue = if (start) value.value.start else value.value.endInclusive
                anchorX = change.position.x
            }
            val below = ((change.position.y - centre).coerceAtLeast(0f)).toDp()
            val now = precision.value(below)
            if (now != factor) {
                factor = now
                anchorValue = if (start) value.value.start else value.value.endInclusive
                anchorX = change.position.x
                onPrecision.value(now)
            }
            set(start, anchorValue + (change.position.x - anchorX) / width * span * factor)
            change.consume()
        }
        if (taken) {
            onPrecision.value(null)
            onFinished.value?.invoke()
        }
    }

    private fun set(start: Boolean, to: Float) {
        val current = value.value
        onValueChange.value(
            if (start) to.coerceIn(valueRange.start, current.endInclusive)..current.endInclusive
            else current.start..to.coerceIn(current.start, valueRange.endInclusive),
        )
    }
}

/** How near a thumb a press counts as on it, so a drag from there is the slider's whichever way it goes. */
private val THUMB_REACH = 24.dp
