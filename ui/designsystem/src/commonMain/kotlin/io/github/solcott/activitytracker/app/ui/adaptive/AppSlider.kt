package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * A slider with one thumb choosing a value within [valueRange], such as a dive site's radius. Material's on every platform, coloured by
 * `AppTheme`, like [AppRangeSlider]. [onValueChangeFinished] fires when a drag ends.
 *
 * Controlled: the thumb shows [value]. Material's state-taking slider only reports a drag through [onValueChange], so the state here
 * follows [value] rather than holding its own.
 */
@Composable
@NonRestartableComposable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val state = remember(valueRange) { SliderState(value, trackRange = valueRange) }
    state.value = value
    Slider(
        state = state,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
    )
}
