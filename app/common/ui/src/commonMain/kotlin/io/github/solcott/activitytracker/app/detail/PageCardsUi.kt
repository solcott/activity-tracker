package io.github.solcott.activitytracker.app.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.VisibilityBadge
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.app.ui.chart.LineChartUi
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.detail_avg
import io.github.solcott.activitytracker.composeApp.resources.detail_max
import io.github.solcott.activitytracker.composeApp.resources.detail_min
import io.github.solcott.activitytracker.composeApp.resources.page_all_stats
import io.github.solcott.activitytracker.composeApp.resources.page_avg_max_heading
import io.github.solcott.activitytracker.composeApp.resources.page_training_load
import io.github.solcott.activitytracker.lexicon.app.MemberView
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * One metric's card, open: its title, its headline figures, its chart with the average dashed and the elevation behind it, its other
 * figures as rows, then its zones, power curve, gears or table. [onSetThresholds] offers the owner the zones screen when they have none.
 */
@Composable
fun MetricCardUi(
    card: MetricCard,
    charts: ActivityCharts,
    modifier: Modifier = Modifier,
    missingZones: Boolean = false,
    onSetThresholds: (() -> Unit)? = null,
    /** On a combined activity, the members the card's data came from, named in its caption. */
    sources: ImmutableList<MemberView>? = null,
) {
    AppCard(modifier) {
        Column(Modifier.padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
            CardTitle(FigureTexts.cardTitle(card).string(), card.mark)
            CardBody(card, charts, missingZones, onSetThresholds, sources, shownHeadline = 0)
        }
    }
}

/**
 * A card on the activity page (feed/activity-page-layout), open or closed to its strip. Its first row stays as it opens and closes: its
 * title, its first headline figure with the second muted after it, and a chevron that turns, with its sparkline and zones as a thin bar
 * while closed, which fade. Opening grows the rest of it out from under that row (any more headline figures, its charts, zones, rows and
 * table), and closing folds it back up. With [onToggle] the row opens or closes it; a table, always open, has none, and shows its first
 * [maxRows], then [showAll] when it has more.
 */
@Composable
@Suppress("LongParameterList") // The card, and how the page it's on opens it and continues its table.
fun PageCardUi(
    card: MetricCard,
    charts: ActivityCharts,
    open: Boolean,
    onToggle: (() -> Unit)?,
    modifier: Modifier = Modifier,
    missingZones: Boolean = false,
    onSetThresholds: (() -> Unit)? = null,
    sources: ImmutableList<MemberView>? = null,
    maxRows: Int = Int.MAX_VALUE,
    showAll: AppText? = null,
    onShowAll: () -> Unit = {},
) {
    AppCard(modifier) {
        Column {
            CardHeader(card, charts, open, onToggle)
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(CARD_MOTION, expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(CARD_MOTION, shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Column(
                    Modifier.padding(start = CARD_PADDING, end = CARD_PADDING, bottom = CARD_PADDING),
                    verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
                ) {
                    CardBody(card, charts, missingZones, onSetThresholds, sources, shownHeadline = 2, maxRows = maxRows)
                    showAll?.let { AppTextButton(onClick = onShowAll) { Text(it.string()) } }
                }
            }
        }
    }
}

/** How fast a card's body grows and folds: quickly to start and settling without overshoot. */
private val CARD_MOTION = spring<IntSize>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/** A card's content under its title: the headline figures past the first [shownHeadline], charts, zones, rows and table. */
@Composable
@Suppress("LongParameterList") // The card, and what its page adds around its charts.
private fun CardBody(
    card: MetricCard,
    charts: ActivityCharts,
    missingZones: Boolean,
    onSetThresholds: (() -> Unit)?,
    sources: ImmutableList<MemberView>?,
    shownHeadline: Int,
    maxRows: Int = Int.MAX_VALUE,
) {
    sources?.let { ChartSourceCaption(it) }
    val headline = card.headline.drop(shownHeadline)
    if (headline.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(ROW_GAP)) {
            headline.forEach { FigureBlock(it, MaterialTheme.typography.titleLarge, Modifier.weight(1f)) }
        }
    }
    CardCharts(card, charts, missingZones, onSetThresholds, maxRows = maxRows)
    if (card.rows.isNotEmpty()) Rows(card.rows)
}

/** A page card's first row, the same open or closed but for its sparkline fading and its chevron turning ([PageCardUi]). */
@Composable
private fun CardHeader(card: MetricCard, charts: ActivityCharts, open: Boolean, onToggle: (() -> Unit)?) {
    val units = LocalUnitSystem.current
    val spark = remember(card, charts, units) { CardLines.spark(card, charts, units) }
    val sparkAlpha by animateFloatAsState(if (open) 0f else 1f)
    val turn by animateFloatAsState(if (open) HALF_TURN else 0f)
    val toggle =
        onToggle?.let {
            Modifier.clickable(onClickLabel = FigureTexts.cardToggle(open).string(), role = Role.Button, onClick = it).handCursor()
        } ?: Modifier
    Row(
        Modifier.fillMaxWidth().then(toggle).padding(horizontal = CARD_PADDING, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(FigureTexts.cardTitle(card).string(), style = MaterialTheme.typography.titleMedium)
                card.mark?.let { VisibilityBadge(it) }
            }
            StripFigures(card)
        }
        if (spark != null || card.zones != null) {
            // Kept in the row while it fades, so the title and figures never move.
            Column(
                Modifier.width(SPARK_WIDTH)
                    .graphicsLayer { alpha = sparkAlpha }
                    .then(if (open) Modifier.clearAndSetSemantics {} else Modifier),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                spark?.let { LineChartUi(it, height = SPARK_HEIGHT, interactive = false) }
                card.zones?.let { ZoneStrip(card.section, it) }
            }
        }
        if (onToggle != null) {
            PlatformIcon(
                AppIcon.ChevronDown,
                null,
                Modifier.graphicsLayer { rotationZ = turn },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The strip's figures: the first headline's value large with its label, the second's label and value muted after it. */
@Composable
private fun StripFigures(card: MetricCard) {
    val first = card.headline.firstOrNull() ?: return
    val second = card.headline.getOrNull(1)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        FigureValueText(first, MaterialTheme.typography.titleLarge)
        Text(figureLabel(first), style = MaterialTheme.typography.labelMedium, color = muted)
        second?.let {
            Text("${figureLabel(it)} ${figureText(it.value)}", style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1)
        }
    }
}

private const val HALF_TURN = 180f
private val SPARK_WIDTH = 104.dp
private val SPARK_HEIGHT = 28.dp

/** [rows], a gear item's opening its page with [onOpenGear] when given. */
@Composable
private fun Rows(rows: ImmutableList<Figure>, onOpenGear: ((String) -> Unit)? = null) {
    Column {
        rows.forEachIndexed { i, figure ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val gear = (figure.value as? FigureValue.GearItem)?.uri
            if (gear != null && onOpenGear != null) FigureRow(figure, Modifier.clickable { onOpenGear(gear) }.handCursor())
            else FigureRow(figure)
        }
    }
}

/** Training load (FTP, IF, TSS, training effect and HRV), for a viewer served it; nothing otherwise. */
@Composable
fun TrainingLoadCard(figures: ImmutableList<Figure>, modifier: Modifier = Modifier) {
    if (figures.isEmpty()) return
    AppCard(modifier) {
        Column(Modifier.padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardTitle(stringResource(Res.string.page_training_load), mark = null)
            Rows(figures)
        }
    }
}

/** Every figure, a group at a time under its name, in [columns] side by side. A gear item's row opens its page with [onOpenGear]. */
@Composable
fun StatsList(
    groups: ImmutableList<StatsGroup>,
    modifier: Modifier = Modifier,
    columns: Int = 1,
    onOpenGear: ((String) -> Unit)? = null,
) {
    if (groups.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
        Text(
            stringResource(Res.string.page_all_stats),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        groups.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
                row.forEach { StatsGroupCard(it, Modifier.weight(1f), onOpenGear) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StatsGroupCard(group: StatsGroup, modifier: Modifier = Modifier, onOpenGear: ((String) -> Unit)? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            groupTitle(group.kind),
            Modifier.padding(horizontal = CARD_PADDING).semantics { heading() },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppCard { Column(Modifier.padding(horizontal = CARD_PADDING, vertical = 4.dp)) { Rows(group.rows, onOpenGear) } }
    }
}

/**
 * A row per metric with an average or maximum, and columns for each. A Min column joins them when a row has a minimum (temperature); the
 * other rows leave it empty, as their minimum is a stop rather than missing.
 */
@Composable
fun AvgMaxTable(rows: ImmutableList<AvgMaxRow>, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) return
    val showsMin = FigureTexts.showsMin(rows)
    AppCard(modifier) {
        Column(Modifier.padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardTitle(stringResource(Res.string.page_avg_max_heading), mark = null)
            AvgMaxLine(
                null,
                stringResource(Res.string.detail_avg),
                stringResource(Res.string.detail_min).takeIf { showsMin },
                stringResource(Res.string.detail_max),
                MaterialTheme.typography.labelMedium,
            )
            val missing = StatText.missing.string()
            rows.forEach { row ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AvgMaxLine(
                    avgMaxLabel(row.metric),
                    row.avg?.let { figureText(it) } ?: missing,
                    if (showsMin) row.min?.let { figureText(it) }.orEmpty() else null,
                    row.max?.let { figureText(it) } ?: missing,
                    MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** A table line; [min] is null when the table has no Min column. */
@Composable
private fun AvgMaxLine(label: String?, avg: String, min: String?, max: String, style: TextStyle) {
    // Three value columns share the width, so each narrows and the metric's name keeps room for "Temperature".
    val column = Modifier.widthIn(min = if (min != null) NARROW_TABLE_COLUMN else TABLE_COLUMN)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label.orEmpty(), Modifier.weight(1f), style = style)
        Text(avg, column, style = style, textAlign = TextAlign.End)
        if (min != null) Text(min, column, style = style, textAlign = TextAlign.End)
        Text(max, column, style = style, textAlign = TextAlign.End)
    }
}

private val TABLE_COLUMN = 72.dp
private val NARROW_TABLE_COLUMN = 64.dp
