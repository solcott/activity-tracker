package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
actual fun AppButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, content: @Composable RowScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    IosButton(onClick, enabled, container = colors.primary, content = colors.onPrimary, modifier = modifier, body = content)
}

@Composable
actual fun AppSecondaryButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, content: @Composable RowScope.() -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    IosButton(onClick, enabled, container = primary.copy(alpha = TINTED_ALPHA), content = primary, modifier = modifier, body = content)
}

@Composable
actual fun AppTextButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .defaultMinSize(minHeight = IOS_MIN_TOUCH)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) { content() }
        }
    }
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
    Box(
        modifier
            .size(IOS_MIN_TOUCH)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val color = if (tint.isSpecified) tint else MaterialTheme.colorScheme.primary
        PlatformIcon(icon, contentDescription, filled = filled, tint = color, size = 22.dp)
    }
}

@Composable
private fun IosButton(
    onClick: () -> Unit,
    enabled: Boolean,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    body: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(IOS_BUTTON_RADIUS)
    Row(
        modifier
            .defaultMinSize(minHeight = IOS_MIN_TOUCH)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(shape)
            .background(container, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(IosButtonPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(color = content)) { body() }
        }
    }
}

private val IOS_MIN_TOUCH = 44.dp
private val IOS_BUTTON_RADIUS = 12.dp
private val IosButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)

/** A tinted (bordered) button's fill: the tint at low opacity. */
private const val TINTED_ALPHA = 0.15f
private const val DISABLED_ALPHA = 0.4f
