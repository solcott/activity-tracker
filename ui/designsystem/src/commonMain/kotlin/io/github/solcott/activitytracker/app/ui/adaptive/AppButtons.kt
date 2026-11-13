package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified

/*
 * Buttons in three emphases: Material 3's filled, outlined and text buttons.
 */

/** The screen's main action: Save, Sign in, Follow. */
@Composable
@NonRestartableComposable
fun AppButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(onClick, modifier, enabled = enabled, content = content)
}

/** A secondary action beside or instead of an [AppButton]: Edit profile, Following, Pick a file. */
@Composable
@NonRestartableComposable
fun AppSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(onClick, modifier, enabled = enabled, content = content)
}

/** A low-emphasis action in running content: Retry, Cancel, Show likes. */
@Composable
@NonRestartableComposable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(onClick, modifier, enabled = enabled, content = content)
}

/** An icon-only action. */
@Composable
fun AppIconButton(
    icon: AppIcon,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    tint: Color = Color.Unspecified,
) {
    IconButton(onClick, modifier, enabled = enabled) {
        PlatformIcon(icon, contentDescription, filled = filled, tint = if (tint.isSpecified) tint else LocalContentColor.current)
    }
}
