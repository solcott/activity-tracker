package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mohamedrejeb.calf.ui.ExperimentalCalfUiApi
import com.mohamedrejeb.calf.ui.dropdown.AdaptiveDropDown
import com.mohamedrejeb.calf.ui.dropdown.AdaptiveDropDownItem
import com.mohamedrejeb.calf.ui.uikit.UIKitImage
import kotlinx.collections.immutable.ImmutableList

/**
 * A pop-up menu anchored to the enclosing box: a native `UIMenu` on iOS, Material's dropdown elsewhere. Picking an item runs it and
 * dismisses the menu.
 */
@OptIn(ExperimentalCalfUiApi::class)
@Composable
fun BoxScope.AppMenu(expanded: Boolean, onDismiss: () -> Unit, items: ImmutableList<AppMenuItem>, modifier: Modifier = Modifier) {
    AdaptiveDropDown(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
        iosItems =
            items.map { item ->
                AdaptiveDropDownItem(
                    title = item.label,
                    iosIcon = (if (item.checked) AppIcon.Check else item.icon)?.let { UIKitImage.SystemName(it.sfSymbol) },
                    isDestructive = item.destructive,
                    onClick = item.onClick,
                )
            },
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
