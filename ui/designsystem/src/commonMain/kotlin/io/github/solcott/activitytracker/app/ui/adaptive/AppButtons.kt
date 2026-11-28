package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import kotlinx.coroutines.delay

/*
 * Buttons in three emphases: Material 3's filled, outlined and text buttons.
 */

/** The screen's main action: Save, Sign in, Follow. */
@Composable
@NonRestartableComposable
fun AppButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(onClick, modifier, enabled = enabled, content = content)
}

/** A secondary action beside or instead of an [AppButton]: Edit profile, Following, Pick a file. */
@Composable
@NonRestartableComposable
fun AppSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(onClick, modifier, enabled = enabled, content = content)
}

/** A low-emphasis action in running content: Retry, Cancel, Show likes. */
@Composable
@NonRestartableComposable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(onClick, modifier, enabled = enabled, content = content)
}

/** An icon-only action. */
@Composable
fun AppIconButton(
    icon: AppIcon,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    tint: Color = Color.Unspecified,
) {
    IconButton(onClick, modifier, enabled = enabled) {
        PlatformIcon(icon, contentDescription, filled = filled, tint = if (tint.isSpecified) tint else LocalContentColor.current)
    }
}

/**
 * An icon-only action that repeats while held, such as a nudge: [onStep] runs on the press with 0, and while it stays pressed again after a
 * pause and then quickly, each time with how many steps came before. A click without a press (a screen reader's, a keyboard's) is one step.
 */
@Composable
fun AppRepeatIconButton(
    icon: AppIcon,
    contentDescription: String?,
    onStep: (repeat: Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val step by rememberUpdatedState(onStep)
    // Whether this press already stepped, so the click that ends it doesn't step again.
    val held = remember { mutableStateOf(false) }
    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) {
            held.value = false
            return@LaunchedEffect
        }
        held.value = true
        step(0)
        delay(REPEAT_AFTER_MS)
        var repeat = 1
        while (true) {
            step(repeat++)
            delay(REPEAT_EVERY_MS)
        }
    }
    IconButton(onClick = { if (!held.value) step(0) }, modifier, enabled = enabled, interactionSource = source) {
        PlatformIcon(icon, contentDescription, tint = LocalContentColor.current)
    }
}

private const val REPEAT_AFTER_MS = 350L
private const val REPEAT_EVERY_MS = 90L
