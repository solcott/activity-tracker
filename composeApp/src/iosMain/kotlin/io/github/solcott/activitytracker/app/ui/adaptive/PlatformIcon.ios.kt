package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.mohamedrejeb.calf.cupertino.icons.CupertinoIcon

@Composable
actual fun PlatformIcon(icon: AppIcon, contentDescription: String?, modifier: Modifier, filled: Boolean, tint: Color, size: Dp) {
    CupertinoIcon(
        if (filled) icon.filledCupertino else icon.cupertino,
        modifier,
        size = size,
        tint = tint,
        contentDescription = contentDescription,
    )
}
