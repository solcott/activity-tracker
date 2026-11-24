package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon

/**
 * The like button, filled in the like colour once [liked]; [label] says what a tap does. On a card it's a full icon button; [compact] (a
 * comment's, beside its Reply text) draws the icon at that text's size on a full-size touch target, muted until liked, and only the icon
 * when not [enabled] (the viewer's own comment).
 */
@Composable
fun LikeButton(
    liked: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    if (!compact) {
        AppIconButton(
            AppIcon.Like,
            contentDescription = label,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            filled = liked,
            tint = if (liked) colors.tertiary else Color.Unspecified,
        )
        return
    }
    Box(
        modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .then(
                if (enabled)
                    Modifier.clickable(role = Role.Button, onClick = onClick).semantics {
                        contentDescription = label
                        selected = liked
                    }
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        PlatformIcon(
            AppIcon.Like,
            contentDescription = null,
            filled = liked,
            tint = if (liked) colors.tertiary else colors.onSurfaceVariant,
            size = 18.dp,
        )
    }
}
