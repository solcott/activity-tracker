package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * An indeterminate spinner: Material's ring, or a native `UIActivityIndicatorView` on iOS. [small] is for a button: it shrinks Material's,
 * and on iOS takes the button's content colour rather than the system grey.
 */
@Composable expect fun AppProgressIndicator(modifier: Modifier = Modifier, small: Boolean = false)
