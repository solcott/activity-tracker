package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

@Composable
actual fun PlatformIcon(icon: AppIcon, contentDescription: String?, modifier: Modifier, filled: Boolean, tint: Color, size: Dp) {
    Icon(if (filled) icon.filledMaterial else icon.material, contentDescription, modifier.size(size), tint = tint)
}
