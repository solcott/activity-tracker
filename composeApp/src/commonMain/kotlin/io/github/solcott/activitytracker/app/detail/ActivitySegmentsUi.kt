package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.segment.EffortRow
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.detail_section_segments
import io.github.solcott.activitytracker.lexicon.app.EffortView
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

/** The activity's segment efforts in a card after the laps, each opening its segment; nothing when it has none the viewer may see. */
internal fun LazyListScope.segments(state: ActivityDetailPage) {
    if (state.segments.isEmpty()) return
    item(key = "segments") {
        SegmentEffortsCard(
            state.segments,
            onOpen = { state.eventSink(ActivityDetailEvent.SegmentOpened(it.segment.uri, it.eBike == true)) },
        )
    }
}

/**
 * [groups] of an activity's efforts (segments/segment-efforts) in a card, set apart by dividers: each effort indented under the one it's
 * nested in, joined to it by guide lines, with a bar marking its span within its group when the group has more than one. Tapping an effort
 * calls [onOpen].
 */
@Composable
fun SegmentEffortsCard(groups: ImmutableList<SegmentEffortGroup>, onOpen: (EffortView) -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Column(Modifier.padding(CARD_PADDING)) {
            Text(stringResource(Res.string.detail_section_segments), style = MaterialTheme.typography.titleMedium)
            groups.forEachIndexed { i, group ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                // Every bar in a group starts past its deepest indent, so the bars share one axis and their spans line up.
                val barIndent = INDENT * group.rows.maxOf { it.depth }
                group.rows.forEach { row -> SegmentEffortRowItem(row, barIndent, onOpen) }
            }
        }
    }
}

/** How far each depth indents, with its guide line down the middle. */
private val INDENT = 20.dp

/** [EffortRow]'s padding above its name. */
private val ROW_TOP = 10.dp

@Composable
private fun SegmentEffortRowItem(row: SegmentEffortRow, barIndent: Dp, onOpen: (EffortView) -> Unit) {
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    // Where a nested row's connector meets it: the middle of its name's first line.
    val connectorY = with(LocalDensity.current) { ROW_TOP.toPx() + MaterialTheme.typography.bodyLarge.lineHeight.toPx() / 2 }
    Column(Modifier.fillMaxWidth().drawBehind { drawGuides(row, guideColor, connectorY) }) {
        EffortRow(
            row.effort,
            row.label,
            onClick = { onOpen(row.effort) },
            modifier = Modifier.padding(start = INDENT * row.depth),
            showStatus = true,
        )
        val start = row.spanStart
        val end = row.spanEnd
        if (start != null && end != null) SpanBar(start, end, Modifier.padding(start = barIndent, bottom = 8.dp))
    }
}

/** A thin track with the stretch from [start] to [end] (fractions of its width) filled. */
@Composable
private fun SpanBar(start: Float, end: Float, modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clearAndSetSemantics {}
            .drawBehind {
                val left = if (layoutDirection == LayoutDirection.Rtl) size.width * (1 - end) else size.width * start
                val width = (size.width * (end - start)).coerceAtLeast(size.height)
                drawRoundRect(fill, Offset(left, 0f), Size(width, size.height), CornerRadius(size.height / 2))
            }
    )
}

/**
 * [row]'s guide lines in its indent: a continuing ancestor's as a full-height line, and its own as a line from the top that turns to meet
 * its name, ending there when it's the last nested under its parent.
 */
private fun DrawScope.drawGuides(row: SegmentEffortRow, color: Color, meet: Float) {
    if (row.depth == 0) return
    val stroke = 1.dp.toPx()
    val indent = INDENT.toPx()
    val rtl = layoutDirection == LayoutDirection.Rtl
    fun x(px: Float) = if (rtl) size.width - px else px
    row.guides.forEachIndexed { i, continues ->
        if (continues) drawLine(color, Offset(x(indent * i + indent / 2), 0f), Offset(x(indent * i + indent / 2), size.height), stroke)
    }
    val own = indent * (row.depth - 1) + indent / 2
    drawLine(color, Offset(x(own), 0f), Offset(x(own), if (row.lastChild) meet else size.height), stroke)
    drawLine(color, Offset(x(own), meet), Offset(x(indent * row.depth - 2.dp.toPx()), meet), stroke)
}
