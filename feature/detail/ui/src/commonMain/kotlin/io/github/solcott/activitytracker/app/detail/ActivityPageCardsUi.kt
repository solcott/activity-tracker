package io.github.solcott.activitytracker.app.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppListRow
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.string
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * The section chips, shown under the bar once the mosaic has scrolled out of view. A chip scrolls to its item, opening the strip it names;
 * the chip of the section at the top shows as selected.
 */
@Composable
internal fun StickyJumps(
    state: ActivityDetailPage,
    listState: LazyListState,
    overview: Int,
    positionOf: (PageJump) -> JumpPosition?,
    modifier: Modifier = Modifier,
) {
    val jumps = ActivityPageTexts.jumps(state).mapNotNull { jump -> positionOf(jump)?.let { jump to it } }
    val shown by remember(overview) { derivedStateOf { listState.firstVisibleItemIndex > overview } }
    if (!shown || jumps.size < 2) return
    val scope = rememberCoroutineScope()
    val top = JumpPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset + 1)
    val selected = jumps.indexOfLast { (_, at) -> at <= top }
    ReadableList(rememberLazyListState(), modifier.fillMaxWidth()) { padding ->
        SectionChips(
            jumps.map { it.first.label }.toImmutableList(),
            selected = selected,
            onSelect = { i ->
                val (jump, at) = jumps[i]
                val card = jump.card
                if (card != null && state.page.cards.firstOrNull { it.section == card }?.let(state::isOpen) == false) {
                    state.eventSink(ActivityDetailEvent.ToggleCard(card))
                }
                scope.launch { listState.animateScrollToItem(at.index, at.offset) }
            },
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(padding).padding(vertical = 8.dp),
        )
    }
}

/** Where a chip scrolls to: its item, and how far into the item (a card's place in the card grid). */
internal data class JumpPosition(val index: Int, val offset: Int = 0) : Comparable<JumpPosition> {
    override fun compareTo(other: JumpPosition): Int = compareValuesBy(this, other, { it.index }, { it.offset })
}

/** All data's title in the side pane, with the close button that brings the page's summary back. */
@Composable
internal fun PaneDataHeader(state: ActivityDetailPage) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIconButton(AppIcon.Close, ActivityDataTexts.close.string(), onClick = { state.eventSink(ActivityDetailEvent.CloseAllData) })
        Text(ActivityDataTexts.title.string(), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
    }
}

/** The page's last row, opening All data: in the side pane on a wide window ([inPane]), else as its own page. */
@Composable
internal fun AllDataRow(state: ActivityDetailPage, inPane: Boolean) {
    AppCard {
        AppListRow(
            ActivityDataTexts.rowTitle.string(),
            summary = ActivityDataTexts.rowSummary(state.data).string(),
            onClick = { state.eventSink(ActivityDetailEvent.OpenAllData(null, inPane)) },
            trailing = { PlatformIcon(AppIcon.Chevron, null) },
        )
    }
}

/** One row of the page's cards: an open card or a table alone, or [strips] side by side, the first after the heading over them. */
@Immutable
internal class CardRow(
    val key: String,
    val cards: ImmutableList<MetricCard>,
    val strips: Boolean,
    val heading: Boolean,
    val inPane: Boolean,
)

/**
 * The page's cards in the sport's order, as rows: an open card or a table takes a row of its own, and strips go [columns] to a row, "More
 * charts" over the first row of them when an open card comes before it.
 */
internal fun cardRows(state: ActivityDetailPage, columns: Int, inPane: Boolean): ImmutableList<CardRow> {
    if (state.page.charts == null) return persistentListOf()
    val rows = mutableListOf<CardRow>()
    var pending = mutableListOf<MetricCard>()
    var sawOpen = false
    var headed = false
    fun flush() {
        if (pending.isEmpty()) return
        val heading = sawOpen && !headed
        headed = headed || heading
        rows += CardRow(PageJump.card(pending.first().section), pending.toImmutableList(), strips = true, heading = heading, inPane)
        pending = mutableListOf()
    }
    for (card in state.page.cards) {
        if (state.isOpen(card)) {
            flush()
            sawOpen = true
            rows += CardRow(PageJump.card(card.section), persistentListOf(card), strips = false, heading = false, inPane)
        } else {
            pending += card
            if (pending.size == columns) flush()
        }
    }
    flush()
    return rows.toImmutableList()
}

/** Where each card's row starts in a [CardGrid], by its jump key ([PageJump.card]), in pixels from the grid's top, for the chips. */
@Stable
internal class CardTops {
    private val tops = mutableMapOf<String, Int>()

    fun of(key: String): Int? = tops[key]

    internal fun set(key: String, top: Int) {
        tops[key] = top
    }
}

/**
 * The page's cards as [rows] in one layout, each card keyed by its section so it keeps its state when it changes rows
 * (feed/activity-page-layout): opening or closing one grows or folds its body under its first row, which stays put, and the cards after it
 * follow its height. A card whose place or width changes (a strip opening to a row of its own, its neighbours widening to fill theirs)
 * moves and resizes there rather than jumping. "More charts" fades in over the first strips after an open card.
 */
@Composable
internal fun CardGrid(state: ActivityDetailPage, rows: ImmutableList<CardRow>, tops: CardTops, modifier: Modifier = Modifier) {
    val charts = state.page.charts ?: return
    val scope = rememberCoroutineScope()
    val motions = remember { mutableMapOf<DetailSection, CellMotion>() }
    Layout(
        content = {
            rows.forEach { row ->
                if (row.heading) {
                    key(MORE_CHARTS) {
                        AnimatedVisibility(visibleState = remember { MutableTransitionState(false) }.apply { targetState = true }) {
                            Text(
                                ActivityPageTexts.moreCharts.string(),
                                Modifier.padding(top = 4.dp).semantics { heading() },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
                row.cards.forEach { card -> key(card.section) { GridCard(state, card, charts, open = !row.strips, row.inPane) } }
            }
        },
        modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = ITEM_GAP.roundToPx()
        val placed = mutableListOf<Placed>()
        var next = 0
        var y = 0
        motions.keys.retainAll(rows.flatMap { row -> row.cards.map { it.section } }.toSet())
        rows.forEach { row ->
            if (row.heading) {
                val heading = measurables[next++].measure(Constraints.fixedWidth(width))
                placed += Placed(heading, 0, y)
                if (heading.height > 0) y += heading.height + gap
            }
            val count = row.cards.size
            val cell = (width - gap * (count - 1)) / count
            var x = 0
            var height = 0
            row.cards.forEachIndexed { i, card ->
                // The last cell takes what rounding left, so a row is exactly as wide as the grid.
                val w = if (i == count - 1) width - x else cell
                val motion = motions.getOrPut(card.section) { CellMotion() }
                motion.follow(row.key, x, y, w, scope)
                val placeable = measurables[next++].measure(Constraints.fixedWidth(motion.width()))
                placed += Placed(placeable, x + motion.dx(), y + motion.dy())
                tops.set(PageJump.card(card.section), y)
                height = maxOf(height, placeable.height)
                x += w + gap
            }
            y += height + gap
        }
        layout(width, (y - gap).coerceAtLeast(0)) { placed.forEach { it.placeable.place(it.x, it.y) } }
    }
}

private class Placed(val placeable: Placeable, val x: Int, val y: Int)

/**
 * How one card of a [CardGrid] moves to a new place: its width eases to its cell's, and it slides from where it was shown as offsets from
 * its new place, which ease to none. It follows its row's top while it stays in the row, since that moves as cards above it grow.
 */
private class CellMotion {
    private val width = Animatable(0f)
    private val dx = Animatable(0f)
    private val dy = Animatable(0f)
    private var row: String? = null
    private var x = 0
    private var y = 0
    private var w = 0

    fun follow(row: String, x: Int, y: Int, w: Int, scope: CoroutineScope) {
        val first = this.row == null
        val moved = !first && (row != this.row || x != this.x)
        val resized = !first && w != this.w
        if (moved) {
            val fromX = this.x + dx.value
            val fromY = this.y + dy.value
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                dx.snapTo(fromX - x)
                dx.animateTo(0f, CELL_MOTION)
            }
            if (row != this.row) {
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    dy.snapTo(fromY - y)
                    dy.animateTo(0f, CELL_MOTION)
                }
            }
        }
        when {
            first -> scope.launch(start = CoroutineStart.UNDISPATCHED) { width.snapTo(w.toFloat()) }
            resized -> scope.launch(start = CoroutineStart.UNDISPATCHED) { width.animateTo(w.toFloat(), CELL_MOTION) }
        }
        this.row = row
        this.x = x
        this.y = y
        this.w = w
    }

    fun width(): Int = width.value.roundToInt().coerceAtLeast(0)

    fun dx(): Int = dx.value.roundToInt()

    fun dy(): Int = dy.value.roundToInt()
}

/** How fast a card moves and resizes in the grid: as its body grows and folds, quickly to start and settling without overshoot. */
private val CELL_MOTION = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/** One card of the grid, open or closed to its strip. */
@Composable
private fun GridCard(state: ActivityDetailPage, card: MetricCard, charts: ActivityCharts, open: Boolean, inPane: Boolean) {
    val table = card.section.isTable
    PageCardUi(
        card,
        charts,
        open = open,
        onToggle = if (table) null else ({ state.eventSink(ActivityDetailEvent.ToggleCard(card.section)) }),
        missingZones = state.chartsMissingZones,
        onSetThresholds = { state.eventSink(ActivityDetailEvent.OpenTrainingZones) },
        sources = state.chartSources.firstOrNull { it.section == card.section }?.members,
        maxRows = if (table) TABLE_PREVIEW_ROWS else Int.MAX_VALUE,
        showAll = if (table) ActivityDataTexts.showAll(card.section, charts) else null,
        onShowAll = { state.eventSink(ActivityDetailEvent.OpenAllData(DataSection.of(card.section), inPane)) },
    )
}

private const val MORE_CHARTS = "moreCharts"

/** The cards' item in a page's list. */
internal const val CARDS_KEY = "cards"

/** The keys of a one-column page's items, in order, as its list lays them out. */
internal fun pageKeys(state: ActivityDetailPage, rows: List<CardRow>, medium: Boolean, offersData: Boolean): List<String> = buildList {
    add(PageJump.OVERVIEW)
    add("header")
    if (!medium) add("hero")
    add("social")
    if (state.showsGroup) add("group")
    add("trainingLoad")
    if (rows.isNotEmpty()) add(CARDS_KEY)
    if (state.segments.isNotEmpty()) add(PageJump.SEGMENTS)
    if (state.lifts.isNotEmpty()) add("lifts")
    if (offersData) add(PageJump.ALL_DATA)
}
