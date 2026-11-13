package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier

/**
 * An on/off switch. Pass a null [onCheckedChange] when an enclosing row is `toggleable`, so the whole row is the target and screen readers
 * announce it once.
 */
@Composable
@NonRestartableComposable
fun AppSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
}
