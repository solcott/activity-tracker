package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList

/**
 * A single choice among a few [labels]: Material's segmented buttons, or a native `UISegmentedControl` on iOS. [selected] is an index into
 * [labels], or -1 for none.
 */
@Composable
expect fun AppSegmentedControl(
    labels: ImmutableList<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
)
