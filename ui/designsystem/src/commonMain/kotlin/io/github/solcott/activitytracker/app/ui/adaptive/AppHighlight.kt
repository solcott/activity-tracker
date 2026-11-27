package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one look of every background that answers hover, press or selection, other than buttons, the segmented control and navigation
 * indicators, which stay pills: a rounded rectangle ([shape], 12dp on touch screens and 8dp with a pointer) with its content [inset] 12dp
 * from its sides and 8dp from its top and bottom. A row draws it with [highlight], its own click, toggle or select modifier, then
 * [highlightInset].
 */
object AppHighlight {
    /** The highlight's corners: the theme's medium shape, so they tighten with a pointer as the rest of the UI does. */
    val shape: Shape
        @Composable @ReadOnlyComposable get() = MaterialTheme.shapes.medium

    /** Between the highlight's sides and its content. */
    val Horizontal: Dp = 12.dp

    /** Between the highlight's top and bottom and its content. */
    val Vertical: Dp = 8.dp

    /** Between a line of text's highlight and the text above and below it, for a link inside running content. */
    val CompactVertical: Dp = 4.dp

    /** How far a [HighlightPlacement.Gutter] highlight stays inside its row's edges. */
    val Gutter: Dp = 4.dp

    /** The content's padding inside the highlight. */
    val inset: PaddingValues = PaddingValues(horizontal = Horizontal, vertical = Vertical)
}

/** Where a highlight's edges sit against the space its row is given. */
enum class HighlightPlacement {
    /** On the row's edges: the content steps in by the inset. */
    Inside,

    /**
     * Past the row's edges by the inset, so the content stays where the row starts and lines up with a heading or text beside it. For a row
     * in a padded column or card.
     */
    Bleed,

    /**
     * [AppHighlight.Gutter] inside the row's edges, so the content sits [AppHighlight.Gutter] plus the inset (16dp) in. For a row that
     * spans a list's full width.
     */
    Gutter,
}

/**
 * Draws [AppHighlight]'s background: placed by [placement], clipped to its shape so the press and hover layer of the click modifier that
 * follows stays inside it, and filled while [selected] (Dusk Slate Container, as a selected chip) or [hovered] (a row whose line on a map
 * is under the pointer). Follow it with the row's click, toggle or select modifier, then [highlightInset].
 */
@Composable
fun Modifier.highlight(
    selected: Boolean = false,
    hovered: Boolean = false,
    placement: HighlightPlacement = HighlightPlacement.Inside,
): Modifier {
    val colors = MaterialTheme.colorScheme
    val placed =
        when (placement) {
            HighlightPlacement.Inside -> this
            HighlightPlacement.Bleed -> this.outdent(AppHighlight.Horizontal)
            HighlightPlacement.Gutter -> this.padding(horizontal = AppHighlight.Gutter)
        }
    val clipped = placed.clip(AppHighlight.shape)
    return when {
        selected -> clipped.background(colors.secondaryContainer)
        hovered -> clipped.background(colors.surfaceContainerHigh)
        else -> clipped
    }
}

/**
 * Pads a [highlight]ed row's content by [AppHighlight.inset] and, while it's [clickable], shows the hand cursor over it. It's at least the
 * minimum touch size tall unless [compact], which pads a line of text inside running content by [AppHighlight.CompactVertical] instead, so
 * it keeps its line. A row that opens nothing in some states keeps the same padding with [clickable] false, so it doesn't move.
 */
@Composable
fun Modifier.highlightInset(compact: Boolean = false, clickable: Boolean = true): Modifier {
    val pointer = if (clickable) handCursor() else this
    return if (compact) {
        pointer.padding(horizontal = AppHighlight.Horizontal, vertical = AppHighlight.CompactVertical)
    } else {
        pointer.heightIn(min = LocalMinimumInteractiveComponentSize.current).padding(AppHighlight.inset)
    }
}

/**
 * Widens an element by [by] on each side and draws it that far past the space its parent gives it, so its content stays where it would be
 * without it. Its parent lays it out at its own size.
 */
fun Modifier.outdent(by: Dp): Modifier = layout { measurable, constraints ->
    val extra = 2 * by.roundToPx()
    val wide =
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
        )
    val placeable = measurable.measure(wide)
    layout((placeable.width - extra).coerceAtLeast(0), placeable.height) { placeable.place(-extra / 2, 0) }
}
