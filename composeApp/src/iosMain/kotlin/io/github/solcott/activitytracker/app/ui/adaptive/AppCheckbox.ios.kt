package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIImageSymbolConfiguration
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode

@Composable
actual fun AppCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier, enabled: Boolean) {
    val toggle =
        if (onCheckedChange == null) Modifier
        else Modifier.toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
    CheckmarkSymbol(if (checked) ToggleableState.On else ToggleableState.Off, enabled, modifier.then(toggle))
}

@Composable
actual fun AppTriStateCheckbox(state: ToggleableState, onClick: (() -> Unit)?, modifier: Modifier, enabled: Boolean) {
    val toggle =
        if (onClick == null) Modifier else Modifier.triStateToggleable(state, enabled = enabled, role = Role.Checkbox, onClick = onClick)
    CheckmarkSymbol(state, enabled, modifier.then(toggle))
}

/**
 * The SF Symbol iOS lists mark a picked row with, in a native `UIImageView`. It takes no touches and has no accessibility element of its
 * own: the enclosing row, or the caller's toggle modifier, handles both. Under [LocalPinnedOverContent] it goes over the Compose canvas,
 * because Compose doesn't restack a pinned view above rows that scroll in later, so their marks would show through its cut-out.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CheckmarkSymbol(state: ToggleableState, enabled: Boolean, modifier: Modifier = Modifier) {
    val symbol =
        when (state) {
            ToggleableState.On -> "checkmark.circle.fill"
            ToggleableState.Indeterminate -> "minus.circle.fill"
            ToggleableState.Off -> "circle"
        }
    val tint =
        when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
            state != ToggleableState.Off -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        }
    UIKitView(
        factory = {
            UIImageView().apply {
                contentMode = UIViewContentMode.UIViewContentModeScaleAspectFit
                userInteractionEnabled = false
            }
        },
        update = {
            it.image =
                UIImage.systemImageNamed(
                    symbol,
                    withConfiguration = UIImageSymbolConfiguration.configurationWithPointSize(SYMBOL_POINT_SIZE),
                )
            it.tintColor = tint.toUIColor()
        },
        modifier = modifier.size(24.dp),
        properties =
            UIKitInteropProperties(
                interactionMode = null,
                isNativeAccessibilityEnabled = false,
                placedAsOverlay = LocalPinnedOverContent.current,
            ),
    )
}

private fun Color.toUIColor(): UIColor =
    UIColor.colorWithRed(red.toDouble(), green = green.toDouble(), blue = blue.toDouble(), alpha = alpha.toDouble())

private const val DISABLED_ALPHA = 0.38f
private const val SYMBOL_POINT_SIZE = 22.0
