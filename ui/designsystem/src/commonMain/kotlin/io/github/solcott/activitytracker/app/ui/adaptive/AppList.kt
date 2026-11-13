package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
 * control (a switch). [selected] marks the row whose page shows beside the list.
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
    val colors = MaterialTheme.colorScheme
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = if (selected) colors.secondaryContainer else Color.Transparent),
        modifier =
            modifier
                .clip(RoundedCornerShape(ROW_CORNER))
                .semantics { this.selected = selected }
                .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick).handCursor() else Modifier)
                .alpha(if (enabled) 1f else DISABLED_ALPHA),
    )
}

private val ROW_CORNER = 12.dp

private const val DISABLED_ALPHA = 0.38f
