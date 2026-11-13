package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.material3.Checkbox
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState

/**
 * A multi-select mark: Material's checkbox. Pass a null [onCheckedChange] when an enclosing row is `toggleable`, so the whole row is the
 * target and screen readers announce it once.
 */
@Composable
@NonRestartableComposable
fun AppCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
}

/**
 * A parent's mark over child checkboxes: [AppCheckbox] when all or none of them are on, and the mixed mark (a dash) for
 * [ToggleableState.Indeterminate], as Material draws a partly selected group. Pass a null [onClick] when an enclosing row is clickable.
 */
@Composable
@NonRestartableComposable
fun AppTriStateCheckbox(state: ToggleableState, onClick: (() -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TriStateCheckbox(state = state, onClick = onClick, modifier = modifier, enabled = enabled)
}
