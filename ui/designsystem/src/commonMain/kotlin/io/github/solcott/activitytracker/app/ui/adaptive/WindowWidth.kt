package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How wide a screen's window is, the one thing that picks its layout: a phone and a narrow desktop or web window are both [Compact], so a
 * screen lays out by width the same way on every platform. Platform look stays in the `ui/adaptive` wrappers.
 */
enum class WindowWidth {
    /** Under 600dp: one column. */
    Compact,
    /** 600dp to under 840dp: two columns. */
    Medium,
    /** 840dp and wider: a side pane beside the content. */
    Expanded;

    companion object {
        val MEDIUM_MIN: Dp = 600.dp
        val EXPANDED_MIN: Dp = 840.dp

        fun of(width: Dp): WindowWidth =
            when {
                width < MEDIUM_MIN -> Compact
                width < EXPANDED_MIN -> Medium
                else -> Expanded
            }
    }
}

/** Measures the width it's given, at a screen's root, and lays [content] out for its [WindowWidth]. */
@Composable
fun WithWindowWidth(modifier: Modifier = Modifier, content: @Composable (WindowWidth) -> Unit) {
    BoxWithConstraints(modifier) { content(WindowWidth.of(maxWidth)) }
}
