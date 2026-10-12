package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.app.ui.theme.UiPlatform

/** Size for an [AppFilterChip]'s icons. */
val AppFilterChipIconSize = FilterChipDefaults.IconSize

/**
 * A toggleable chip: a filter or a choice among a few. Material platforms get Material 3's filter chip. iOS has no chip, so it gets a
 * capsule: a translucent grey fill with plain text, or tinted with the brand colour while [selected].
 */
@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    if (LocalUiPlatform.current == UiPlatform.Ios) {
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
    } else {
        FilterChip(selected, onClick, label, modifier, enabled, leadingIcon = leadingIcon, trailingIcon = trailingIcon)
    }
}

private val IOS_CHIP_HEIGHT = 36.dp
private const val SELECTED_ALPHA = 0.15f
private const val DISABLED_ALPHA = 0.4f
