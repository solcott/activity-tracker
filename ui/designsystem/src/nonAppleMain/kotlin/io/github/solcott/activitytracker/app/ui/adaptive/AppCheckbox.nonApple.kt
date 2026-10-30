package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.material3.Checkbox
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState

@Composable
actual fun AppCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier, enabled: Boolean) {
    Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
}

@Composable
actual fun AppTriStateCheckbox(state: ToggleableState, onClick: (() -> Unit)?, modifier: Modifier, enabled: Boolean) {
    TriStateCheckbox(state = state, onClick = onClick, modifier = modifier, enabled = enabled)
}
