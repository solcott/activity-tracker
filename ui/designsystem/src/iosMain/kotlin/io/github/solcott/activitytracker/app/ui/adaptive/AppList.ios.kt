package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A small grey heading over an [AppCard] (the inset grouped cell), with a hairline between rows that starts at their text, as in UIKit. */
@Composable
actual fun AppListSection(title: String?, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().padding(top = 16.dp)) {
        title?.let {
            Text(
                it,
                Modifier.padding(start = ROW_INSET, end = ROW_INSET, bottom = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        AppCard { SeparatedColumn(content) }
    }
}

/** Stacks its children top to bottom, full width, with a hairline above each one after the first. */
@Composable
private fun SeparatedColumn(content: @Composable () -> Unit) {
    val separator = MaterialTheme.colorScheme.outlineVariant
    // Where each child after the first starts, written while placing (only when it changes) and read while drawing.
    var tops by remember { mutableStateOf(emptyList<Int>()) }
    Layout(
        content,
        Modifier.fillMaxWidth().drawBehind {
            val inset = ROW_INSET.toPx()
            tops.forEach { y -> drawLine(separator, Offset(inset, y.toFloat()), Offset(size.width, y.toFloat()), HAIRLINE.toPx()) }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val width = placeables.maxOfOrNull { it.width }?.coerceAtLeast(constraints.minWidth) ?: constraints.minWidth
        val starts = placeables.runningFold(0) { y, placeable -> y + placeable.height }
        layout(width, starts.last()) {
            placeables.forEachIndexed { i, placeable -> placeable.place(0, starts[i]) }
            val next = starts.subList(1, placeables.size.coerceAtLeast(1))
            if (next != tops) tops = next
        }
    }
}

/** A UIKit cell: title at the start, the summary in secondary grey at the end, then a chevron, or [trailing] in its place. */
@Composable
actual fun AppListRow(
    title: String,
    modifier: Modifier,
    summary: String?,
    onClick: (() -> Unit)?,
    selected: Boolean,
    enabled: Boolean,
    trailing: (@Composable () -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onPrimary else colors.onSurface
    val secondary = if (selected) colors.onPrimary else colors.onSurfaceVariant
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ROW_HEIGHT)
            .semantics { this.selected = selected }
            .then(if (selected) Modifier.background(colors.primary) else Modifier)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(horizontal = ROW_INSET, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The title keeps its natural width (Row measures unweighted children first) and the summary takes what's left, ellipsized,
        // so a long value (a DID) can't squeeze the title to one letter per line. With no summary the title fills, pushing trailing aside.
        Text(
            title,
            if (summary == null) Modifier.weight(1f) else Modifier,
            color = content,
            style = MaterialTheme.typography.bodyLarge,
        )
        summary?.let {
            Text(
                it,
                Modifier.weight(1f),
                color = secondary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            trailing != null -> trailing()
            onClick != null -> PlatformIcon(AppIcon.Chevron, null, tint = secondary, size = CHEVRON_SIZE)
        }
    }
}

private val ROW_INSET = 16.dp
private val ROW_HEIGHT = 44.dp
private val HAIRLINE = 0.5.dp
private val CHEVRON_SIZE = 14.dp
private const val DISABLED_ALPHA = 0.38f
