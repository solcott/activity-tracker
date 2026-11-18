package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.material3.RangeSlider
import androidx.compose.material3.RangeSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * A slider with two thumbs choosing a range within [valueRange], such as a stretch along a route. Material's on every platform, coloured by
 * `AppTheme`. [onValueChangeFinished] fires when a drag ends.
 *
 * Controlled, like [AppSlider]: the thumbs show [value], and a drag only reaches [onValueChange].
 */
@Composable
@NonRestartableComposable
fun AppRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val state = remember(valueRange) { RangeSliderState(value.start, value.endInclusive, trackRange = valueRange) }
    state.startValue = value.start
    state.endValue = value.endInclusive
    RangeSlider(
        state = state,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
    )
}
