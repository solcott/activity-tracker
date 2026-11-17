package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform

/**
 * A row of a list that shares a selection with a map of lines (segments/segment-browse, segments/path-page): its [name], then its [details]
 * line. It's [marked] while selected or under the mouse pointer (here or on its line), and a mouse over it reports its [key] to [onHover],
 * so the map highlights its line. A tap on a touch screen selects it, or opens it once [selected] ([onTap]); a click with a pointer opens
 * it ([onOpen]).
 */
@Composable
internal fun SelectableRow(
    key: String,
    name: AppText,
    details: AppText,
    selected: Boolean,
    marked: Boolean,
    hovered: String?,
    onHover: (String?) -> Unit,
    onTap: () -> Unit,
    onOpen: () -> Unit,
) {
    val touch = LocalUiPlatform.current.isMobile
    val currentOnHover by rememberUpdatedState(onHover)
    val currentHovered by rememberUpdatedState(hovered)
    Column(
        Modifier.fillMaxWidth()
            .then(if (marked) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .pointerInput(key) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.firstOrNull()?.type != PointerType.Mouse) continue
                        when (event.type) {
                            PointerEventType.Enter -> currentOnHover(key)
                            // Another row or the map may have taken the hover since.
                            PointerEventType.Exit -> if (currentHovered == key) currentOnHover(null)
                            else -> Unit
                        }
                    }
                }
            }
            .semantics { this.selected = selected }
            .clickable(onClickLabel = SegmentBrowseTexts.rowAction(selected, touch).string()) { if (touch) onTap() else onOpen() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(name.string(), style = MaterialTheme.typography.titleMedium)
        Text(details.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
