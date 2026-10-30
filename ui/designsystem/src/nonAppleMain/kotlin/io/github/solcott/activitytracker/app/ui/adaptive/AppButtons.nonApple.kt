package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified

@Composable
actual fun AppButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, content: @Composable RowScope.() -> Unit) {
    Button(onClick, modifier, enabled = enabled, content = content)
}

@Composable
actual fun AppSecondaryButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick, modifier, enabled = enabled, content = content)
}

@Composable
actual fun AppTextButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, content: @Composable RowScope.() -> Unit) {
    TextButton(onClick, modifier, enabled = enabled, content = content)
}

@Composable
actual fun AppIconButton(
    icon: AppIcon,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    filled: Boolean,
    tint: Color,
) {
    IconButton(onClick, modifier, enabled = enabled) {
        PlatformIcon(icon, contentDescription, filled = filled, tint = if (tint.isSpecified) tint else LocalContentColor.current)
    }
}
