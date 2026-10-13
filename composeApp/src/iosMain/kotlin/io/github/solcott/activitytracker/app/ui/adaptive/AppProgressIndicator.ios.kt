package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import platform.UIKit.UIActivityIndicatorView
import platform.UIKit.UIActivityIndicatorViewStyleMedium

@Composable
actual fun AppProgressIndicator(modifier: Modifier, small: Boolean) {
    ActivityIndicator(modifier)
}

/**
 * UIKit's medium spinner, which iOS uses at every size, faded to [alpha] on the view itself (a `graphicsLayer` alpha doesn't reach native
 * views). It goes over the Compose canvas: spinners sit on Compose-drawn fills, like a pending Follow button, which an interop view's
 * cut-out below the canvas would erase.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ActivityIndicator(modifier: Modifier = Modifier, alpha: Float = 1f) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    UIKitView(
        factory = { UIActivityIndicatorView(UIActivityIndicatorViewStyleMedium).apply { startAnimating() } },
        update = {
            it.color = color.toUIColor()
            it.alpha = alpha.toDouble()
        },
        modifier = modifier.size(MEDIUM_SIZE),
        properties = UIKitInteropProperties(interactionMode = null, isNativeAccessibilityEnabled = true, placedAsOverlay = true),
    )
}

/** `UIActivityIndicatorView`'s medium style. */
private val MEDIUM_SIZE = 20.dp
