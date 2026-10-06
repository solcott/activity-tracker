package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The widest a column of text and cards gets before it reads badly: a phone-like column, centred, on desktop and wide web windows. */
val ReadableMaxWidth = 720.dp

/** Caps a non-lazy column at [ReadableMaxWidth], centred. Put it inside any `verticalScroll`, so the whole width still scrolls. */
fun Modifier.readableWidth(max: Dp = ReadableMaxWidth): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = max)

/**
 * Hosts a full-width list whose items sit in a centred [ReadableMaxWidth] column. [content] gets the padding to use as its
 * `contentPadding`: [contentPadding] (a `Scaffold`'s, so the list starts below the bars but scrolls under them) plus the side margins, so
 * the margins still scroll the list. On desktop and web a scrollbar runs down the right edge, clear of the bars.
 */
@Composable
fun ReadableList(
    state: LazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    minSidePadding: Dp = 16.dp,
    content: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    BoxWithConstraints(modifier) {
        content(contentPadding + PaddingValues(horizontal = maxOf(minSidePadding, (maxWidth - ReadableMaxWidth) / 2)))
        AppVerticalScrollbar(state, Modifier.align(Alignment.CenterEnd).padding(contentPadding).fillMaxHeight())
    }
}

/**
 * A column scrolled by [state], with a scrollbar on desktop and web. Pad the scrolled column by [contentPadding] (a `Scaffold`'s) inside
 * its `verticalScroll`, so it scrolls under the bars; the scrollbar stays clear of them.
 */
@Composable
fun ScrollbarBox(
    state: ScrollState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        content()
        AppVerticalScrollbar(state, Modifier.align(Alignment.CenterEnd).padding(contentPadding).fillMaxHeight())
    }
}

/** A scrollbar for a lazy list on desktop and web. Phones have none: their lists show a transient indicator of their own, or none. */
@Composable expect fun AppVerticalScrollbar(state: LazyListState, modifier: Modifier = Modifier)

/** A scrollbar for a scrolling column on desktop and web; nothing on phones. */
@Composable expect fun AppVerticalScrollbar(state: ScrollState, modifier: Modifier = Modifier)

/** The hand cursor web pages show over links and buttons. Pointerless platforms ignore it. */
fun Modifier.handCursor(): Modifier = pointerHoverIcon(PointerIcon.Hand)
