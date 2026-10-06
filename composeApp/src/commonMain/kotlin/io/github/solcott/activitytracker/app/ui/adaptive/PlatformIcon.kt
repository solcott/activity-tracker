package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.calf.cupertino.icons.CupertinoIcon
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.app.ui.theme.UiPlatform

/** [icon] in this platform's style: Material, or an SF Symbols-style glyph on iOS. [filled] picks the selected form. */
@Composable
fun PlatformIcon(
    icon: AppIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    if (LocalUiPlatform.current == UiPlatform.Ios) {
        CupertinoIcon(
            if (filled) icon.filledCupertino else icon.cupertino,
            modifier,
            size = size,
            tint = tint,
            contentDescription = contentDescription,
        )
    } else {
        Icon(if (filled) icon.filledMaterial else icon.material, contentDescription, modifier.size(size), tint = tint)
    }
}
