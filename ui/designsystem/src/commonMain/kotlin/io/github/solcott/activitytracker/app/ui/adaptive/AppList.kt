package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A titled group of [AppListRow]s, like a section of Settings. It has a coloured heading over plain rows. [content] stacks top to bottom
 * and may also hold other controls (a segmented control), padded by the caller.
 */
@Composable
fun AppListSection(title: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        title?.let {
            Text(
                it,
                Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        content()
    }
}

/**
 * One row of a list: a [title] with an optional [summary] of its current value. With [onClick] it opens something. [trailing] holds a
 * control (a switch). [selected] marks the row whose page shows beside the list. Material's interactive list item draws the selected,
 * pressed and disabled states and carries the row's selected semantics.
 */
@Composable
fun AppListRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors =
        ListItemDefaults.colors(containerColor = Color.Transparent, selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer)
    val shapes = ListItemDefaults.shapes(shape = RoundedCornerShape(ROW_CORNER), selectedShape = RoundedCornerShape(ROW_CORNER))
    val supporting: (@Composable () -> Unit)? = summary?.let { { Text(it) } }
    if (onClick != null) {
        ListItem(
            selected = selected,
            onClick = onClick,
            modifier = modifier.handCursor(),
            enabled = enabled,
            trailingContent = trailing,
            supportingContent = supporting,
            shapes = shapes,
            colors = colors,
        ) {
            Text(title)
        }
    } else {
        ListItem(
            modifier = modifier,
            enabled = enabled,
            trailingContent = trailing,
            supportingContent = supporting,
            shapes = shapes,
            colors = colors,
        ) {
            Text(title)
        }
    }
}

private val ROW_CORNER = 12.dp
