package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import platform.UIKit.UIActivityIndicatorView
import platform.UIKit.UIActivityIndicatorViewStyleMedium

@Composable
actual fun AppProgressIndicator(modifier: Modifier, small: Boolean) {
    // In a button it takes the label's colour, as UIKit's buttons tint their activity indicator; on its own it's the system grey.
    ActivityIndicator(modifier, color = if (small) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * UIKit's medium spinner in [color], which iOS uses at every size, faded to [alpha] on the view itself (a `graphicsLayer` alpha doesn't
 * reach native views). It goes over the Compose canvas: spinners sit on Compose-drawn fills, like a pending Follow button, which an interop
 * view's cut-out below the canvas would erase.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ActivityIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    alpha: Float = 1f,
) {
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
