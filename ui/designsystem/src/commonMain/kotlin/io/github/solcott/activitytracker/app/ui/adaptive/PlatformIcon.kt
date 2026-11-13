package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** [icon] as its Material icon. [filled] picks the selected form. */
@Composable
fun PlatformIcon(
    icon: AppIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    Icon(if (filled) icon.filledMaterial else icon.material, contentDescription, modifier.size(size), tint = tint)
}
