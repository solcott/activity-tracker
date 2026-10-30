package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RangeSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier

/**
 * A slider with two thumbs choosing a range within [valueRange], such as a stretch along a route. Material's on every platform, coloured by
 * `AppTheme`: UIKit has no two-thumb slider for Calf to wrap. [onValueChangeFinished] fires when a drag ends.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    RangeSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
    )
}
