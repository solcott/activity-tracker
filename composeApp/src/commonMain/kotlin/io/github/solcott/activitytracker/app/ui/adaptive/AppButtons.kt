package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/*
 * Buttons in three emphases. Material platforms use Material 3's filled, outlined and text buttons. iOS draws UIKit's: a filled
 * (borderedProminent) button, a tinted (bordered) one and a plain one, all dimming on press.
 */

/** The screen's main action: Save, Sign in, Follow. */
@Composable
expect fun AppButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit)

/** A secondary action beside or instead of an [AppButton]: Edit profile, Following, Pick a file. */
@Composable
expect fun AppSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
)

/** A low-emphasis action in running content: Retry, Cancel, Show likes. */
@Composable
expect fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
)

/** An icon-only action. On iOS it's tinted with the brand colour, like a bar button. */
@Composable
expect fun AppIconButton(
    icon: AppIcon,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    tint: Color = Color.Unspecified,
)
