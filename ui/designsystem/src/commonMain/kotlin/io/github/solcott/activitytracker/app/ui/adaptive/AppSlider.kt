package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier

/**
 * A slider with one thumb choosing a value within [valueRange], such as a dive site's radius. Material's on every platform, coloured by
 * `AppTheme`, like [AppRangeSlider]. [onValueChangeFinished] fires when a drag ends.
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
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
    )
}
