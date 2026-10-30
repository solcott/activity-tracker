package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.runtime.Immutable

/** One button in an [AppAlertDialog]. [destructive] marks one that deletes or can't be undone. */
@Immutable data class AppDialogAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)
