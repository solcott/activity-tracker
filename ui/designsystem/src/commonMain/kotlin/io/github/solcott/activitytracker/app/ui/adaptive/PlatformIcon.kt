package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** [icon] in this platform's style: Material, or an SF Symbols-style glyph on iOS. [filled] picks the selected form. */
@Composable
expect fun PlatformIcon(
    icon: AppIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
)
