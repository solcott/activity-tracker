package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.runtime.Immutable

/** One choice in an [AppMenu]. [checked] marks the current value in a picker menu. */
@Immutable
data class AppMenuItem(
    val label: String,
    val icon: AppIcon? = null,
    val checked: Boolean = false,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)
