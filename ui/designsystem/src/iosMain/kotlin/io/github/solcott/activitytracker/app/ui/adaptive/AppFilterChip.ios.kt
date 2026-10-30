package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.theme.LocalIosColors

@Composable
actual fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (selected) colors.primary.copy(alpha = SELECTED_ALPHA) else LocalIosColors.current.tertiaryFill
    val content = if (selected) colors.primary else colors.onSurface
    Row(
        modifier
            .defaultMinSize(minHeight = IOS_CHIP_HEIGHT)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(CircleShape)
            .background(container, CircleShape)
            .toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            leadingIcon?.invoke()
            ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color = content)) { label() }
            trailingIcon?.invoke()
        }
    }
}

private val IOS_CHIP_HEIGHT = 36.dp
private const val SELECTED_ALPHA = 0.15f
private const val DISABLED_ALPHA = 0.4f
