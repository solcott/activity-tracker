package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import kotlinx.collections.immutable.ImmutableList

/**
 * A pop-up menu anchored to the enclosing box: Material's dropdown. Picking an item runs it and dismisses the menu. Each item's hover and
 * press fill is [AppHighlight]'s, inset from the menu's sides.
 */
@Composable
fun BoxScope.AppMenu(expanded: Boolean, onDismiss: () -> Unit, items: ImmutableList<AppMenuItem>, modifier: Modifier = Modifier) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = AppMenuShape,
    ) {
        items.forEach { item ->
            AppMenuEntry(
                item.label,
                onClick = {
                    item.onClick()
                    onDismiss()
                },
                color = if (item.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                leadingIcon = item.icon?.let { { PlatformIcon(it, null) } },
                trailingIcon = if (item.checked) ({ PlatformIcon(AppIcon.Check, null) }) else null,
            )
        }
    }
}

/** A pop-up menu's corners: rounder than its items' [AppHighlight] shape, so an item's fill nests inside them. */
val AppMenuShape: Shape
    @Composable @ReadOnlyComposable get() = MaterialTheme.shapes.large

/**
 * One item of a pop-up menu, as [AppMenu] draws each: [label] in [color], its hover and press fill [AppHighlight]'s, inset from the menu's
 * sides. For a menu [AppMenu] can't draw, like a field's suggestions.
 */
@Composable
fun AppMenuEntry(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    DropdownMenuItem(
        text = { Text(label, color = color) },
        onClick = onClick,
        modifier = modifier.padding(horizontal = AppHighlight.Gutter).clip(AppHighlight.shape).handCursor(),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        contentPadding = PaddingValues(horizontal = AppHighlight.Horizontal),
    )
}
