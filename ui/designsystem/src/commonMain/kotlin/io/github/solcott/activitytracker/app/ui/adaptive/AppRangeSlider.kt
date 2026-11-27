package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RangeSlider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription

/**
 * A slider with two thumbs choosing a range within [valueRange], such as a stretch along a route. Material's on every platform, coloured by
 * `AppTheme`: a thumb drags, a tap moves the nearer thumb, and a focused thumb steps with the arrow keys. [onValueChangeFinished] fires
 * when a drag ends. With [labels], each thumb reads to a screen reader by its own name and value, and its adjust action moves only that
 * thumb.
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
) {
    if (labels == null) {
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
        // Semantics only: no pointer input, so touches reach the slider beneath.
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
}
