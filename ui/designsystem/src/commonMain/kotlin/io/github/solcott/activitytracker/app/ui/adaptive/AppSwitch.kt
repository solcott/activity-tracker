package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier
import com.mohamedrejeb.calf.ui.toggle.AdaptiveSwitch

/**
 * An on/off switch: a native `UISwitch` on iOS, Material's elsewhere. Pass a null [onCheckedChange] when an enclosing row is `toggleable`,
 * so the whole row is the target and screen readers announce it once.
 */
@Composable
@NonRestartableComposable
fun AppSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    AdaptiveSwitch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
}
