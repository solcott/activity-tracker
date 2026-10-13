package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** An indeterminate spinner: Material's ring, or a native `UIActivityIndicatorView` on iOS. [small] sizes Material's for a button. */
@Composable expect fun AppProgressIndicator(modifier: Modifier = Modifier, small: Boolean = false)
