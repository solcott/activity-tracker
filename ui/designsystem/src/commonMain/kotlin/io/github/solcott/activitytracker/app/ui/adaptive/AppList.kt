package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
        title?.let { AppListSectionTitle(it, Modifier.padding(horizontal = 16.dp)) }
        content()
    }
}

/**
 * An [AppListSection]'s coloured heading on its own, for a list that lays its sections out as items (a month of a batch). It pads only
 * above and below; [modifier] insets it where the list's rows are inset.
 */
@Composable
fun AppListSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(top = 16.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall,
    )
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
    Row(
        modifier
            .fillMaxWidth()
            .highlight(selected = selected, placement = HighlightPlacement.Gutter)
            .semantics { this.selected = selected }
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .highlightInset(clickable = onClick != null && enabled)
            .heightIn(min = ROW_MIN_HEIGHT)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.onSurface, style = MaterialTheme.typography.bodyLarge)
            summary?.let { Text(it, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
        }
        trailing?.invoke()
    }
}

/** A row's content height at least: with the inset, Material's 56dp one-line list item, so a row with a switch is no taller. */
private val ROW_MIN_HEIGHT = 40.dp

private const val DISABLED_ALPHA = 0.38f
