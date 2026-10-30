package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Size for an [AppFilterChip]'s icons. */
val AppFilterChipIconSize = FilterChipDefaults.IconSize

/**
 * A toggleable chip: a filter or a choice among a few. Material platforms get Material 3's filter chip. iOS has no chip, so it gets a
 * capsule: a translucent grey fill with plain text, or tinted with the brand colour while [selected].
 */
@Composable
expect fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
)
