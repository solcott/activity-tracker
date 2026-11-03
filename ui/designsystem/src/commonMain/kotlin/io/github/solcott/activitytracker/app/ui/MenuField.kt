package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * A read-only field showing [shown], with a dropdown arrow, that runs [onClick] when tapped: for a field that opens a sheet, a dialog or a
 * menu. A read-only field doesn't report clicks, so a transparent layer on top takes them, announced as [role].
 */
@Composable
fun TapField(
    shown: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.Button,
    leadingIcon: (@Composable () -> Unit)? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
) {
    Box(modifier) {
        AppTextField(
            rememberReadOnlyTextFieldState(shown),
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = true,
            label = label,
            leadingIcon = leadingIcon,
            trailingIcon = { PlatformIcon(AppIcon.DropDown, null) },
            lineLimits = lineLimits,
        )
        Box(Modifier.matchParentSize().clickable(enabled = enabled, role = role, onClick = onClick))
    }
}

/**
 * A read-only field showing [shown] that opens a menu of [items] when tapped: a dropdown. Each item's `onClick` runs, and the menu closes.
 */
@Composable
fun MenuField(
    shown: String,
    label: String,
    items: ImmutableList<AppMenuItem>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        TapField(shown, label, onClick = { expanded = true }, enabled = enabled, role = Role.DropdownList)
        AppMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            items =
                remember(items) {
                    items
                        .map { item ->
                            item.copy(
                                onClick = {
                                    expanded = false
                                    item.onClick()
                                }
                            )
                        }
                        .toImmutableList()
                },
        )
    }
}
