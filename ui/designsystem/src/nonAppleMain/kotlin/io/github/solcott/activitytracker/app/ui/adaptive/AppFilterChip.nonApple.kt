package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
) {
    FilterChip(selected, onClick, label, modifier, enabled, leadingIcon = leadingIcon, trailingIcon = trailingIcon)
}
