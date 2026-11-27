package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier

/** Size for an [AppFilterChip]'s icons. */
val AppFilterChipIconSize = FilterChipDefaults.IconSize

/** A toggleable chip: a filter or a choice among a few, as Material 3's filter chip, in [AppHighlight]'s shape. */
@Composable
@NonRestartableComposable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    FilterChip(
        selected,
        onClick,
        label,
        modifier.handCursor(),
        enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = AppHighlight.shape,
    )
}
