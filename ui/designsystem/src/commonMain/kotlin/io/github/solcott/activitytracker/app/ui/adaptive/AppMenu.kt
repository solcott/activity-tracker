package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList

/** A pop-up menu anchored to the enclosing box: Material's dropdown. Picking an item runs it and dismisses the menu. */
@Composable
fun BoxScope.AppMenu(expanded: Boolean, onDismiss: () -> Unit, items: ImmutableList<AppMenuItem>, modifier: Modifier = Modifier) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        items.forEach { item ->
            DropdownMenuItem(
                text = {
                    Text(item.label, color = if (item.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                },
                leadingIcon = item.icon?.let { { PlatformIcon(it, null) } },
                trailingIcon = if (item.checked) ({ PlatformIcon(AppIcon.Check, null) }) else null,
                onClick = {
                    item.onClick()
                    onDismiss()
                },
            )
        }
    }
}
