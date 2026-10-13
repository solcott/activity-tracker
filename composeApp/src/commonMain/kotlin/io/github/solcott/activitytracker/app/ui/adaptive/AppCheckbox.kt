package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.calf.cupertino.icons.CupertinoIcon
import com.mohamedrejeb.calf.cupertino.icons.CupertinoIcons
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.app.ui.theme.UiPlatform

/**
 * A multi-select mark: Material's checkbox, or on iOS the filled checkmark circle its lists use to pick rows (UIKit has no checkbox). Pass
 * a null [onCheckedChange] when an enclosing row is `toggleable`, so the whole row is the target and screen readers announce it once.
 */
@Composable
fun AppCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (LocalUiPlatform.current == UiPlatform.Ios) {
        val toggle =
            if (onCheckedChange == null) Modifier
            else Modifier.toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
        CupertinoIcon(
            if (checked) CupertinoIcons.checkmarkCircleFill else CupertinoIcons.circle,
            modifier.then(toggle),
            size = 24.dp,
            tint =
                when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
                    checked -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline
                },
            contentDescription = null,
        )
    } else {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
    }
}

/**
 * A parent's mark over child checkboxes: [AppCheckbox] when all or none of them are on, and the mixed mark (a dash) for
 * [ToggleableState.Indeterminate], as Material's and Apple's guidelines draw a partly selected group. Pass a null [onClick] when an
 * enclosing row is clickable.
 */
@Composable
fun AppTriStateCheckbox(state: ToggleableState, onClick: (() -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (LocalUiPlatform.current == UiPlatform.Ios) {
        val toggle =
            if (onClick == null) Modifier
            else Modifier.triStateToggleable(state, enabled = enabled, role = Role.Checkbox, onClick = onClick)
        CupertinoIcon(
            when (state) {
                ToggleableState.On -> CupertinoIcons.checkmarkCircleFill
                ToggleableState.Indeterminate -> CupertinoIcons.minusCircleFill
                ToggleableState.Off -> CupertinoIcons.circle
            },
            modifier.then(toggle),
            size = 24.dp,
            tint =
                when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
                    state != ToggleableState.Off -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline
                },
            contentDescription = null,
        )
    } else {
        TriStateCheckbox(state = state, onClick = onClick, modifier = modifier, enabled = enabled)
    }
}

private const val DISABLED_ALPHA = 0.38f
