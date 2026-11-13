package io.github.solcott.activitytracker.app.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.chart.LineChart
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.chart.LineChartUi
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.zones_history
import io.github.solcott.activitytracker.composeApp.resources.zones_history_current
import io.github.solcott.activitytracker.composeApp.resources.zones_history_empty
import io.github.solcott.activitytracker.composeApp.resources.zones_history_help
import io.github.solcott.activitytracker.composeApp.resources.zones_history_remove
import io.github.solcott.activitytracker.composeApp.resources.zones_history_today
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

@CircuitInject(ThresholdHistoryScreen::class, SessionScope::class)
@Composable
fun ThresholdHistoryUi(state: ThresholdHistoryState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.zones_history),
                onBack = { state.eventSink(ThresholdHistoryEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val retry = { state.eventSink(ThresholdHistoryEvent.Retry) }
        ContentStateLayout(state.content, UiError.Kind.LoadProfile, retry, contentPadding = padding) { groups ->
            val listState = rememberLazyListState()
            ReadableList(listState, Modifier.fillMaxSize(), padding) { listPadding ->
                LazyColumn(state = listState, contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { RefreshStatus(state.content, UiError.Kind.LoadProfile, retry, Modifier.padding(top = 16.dp)) }
                    state.error?.let { error -> item { ErrorText(error, Modifier.padding(top = 16.dp)) } }
                    if (groups.isEmpty()) {
                        item { Text(stringResource(Res.string.zones_history_empty), Modifier.padding(top = 16.dp)) }
                    } else {
                        item { Intro(state) }
                        items(groups, key = { "group/${it.sport}" }) { group -> GroupCard(group, state, Modifier.animateContentSize()) }
                        item { Spacer(Modifier.heightIn(min = 16.dp)) }
                    }
                }
            }
        }
    }
}

/** What the screen holds, and one icon button to expand or collapse every group. */
@Composable
private fun Intro(state: ThresholdHistoryState, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(Res.string.zones_history_help),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppIconButton(
            if (state.anyExpanded) AppIcon.CollapseAll else AppIcon.ExpandAll,
            ThresholdHistoryTexts.toggleAll(state).string(),
            onClick = { state.eventSink(ThresholdHistoryEvent.ToggleAll) },
        )
    }
}

/** The defaults or one sport: a header that expands or collapses it, then each threshold in full, or an overview tile for each. */
@Composable
private fun GroupCard(group: ThresholdHistoryGroup, state: ThresholdHistoryState, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            GroupHeader(group, onToggle = { state.eventSink(ThresholdHistoryEvent.ToggleGroup(group.sport)) })
            if (group.expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                group.thresholds.forEachIndexed { i, section ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA))
                    ThresholdBlock(section, state)
                }
            } else {
                Overview(group, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp))
            }
        }
    }
}

@Composable
private fun GroupHeader(group: ThresholdHistoryGroup, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val stateText = ThresholdHistoryTexts.expansion(group).string()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = stateText }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlatformIcon(
            AppIcon.Chevron,
            contentDescription = null,
            modifier = Modifier.rotate(if (group.expanded) EXPANDED_ROTATION else 0f),
            tint = MaterialTheme.colorScheme.primary,
            size = 20.dp,
        )
        Column(Modifier.weight(1f)) {
            Text(ThresholdHistoryTexts.heading(group).string(), style = MaterialTheme.typography.titleMedium)
            Text(
                ThresholdHistoryTexts.help(group).string(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            ThresholdHistoryTexts.summary(group).string(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A tile per threshold: its value now, a sparkline of its progress when it has one, and how it moved. */
@Composable
private fun Overview(group: ThresholdHistoryGroup, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = (maxWidth / OVERVIEW_TILE_WIDTH).toInt().coerceAtLeast(2)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            group.thresholds.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { OverviewTile(it, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun OverviewTile(section: ThresholdHistorySection, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            thresholdLabel(section.threshold),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            ThresholdHistoryTexts.current(section).string(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        ThresholdHistoryTexts.chart(section, detailed = false)?.let { LineChartUi(it, height = SPARKLINE_HEIGHT, interactive = false) }
        ChangeText(section, MaterialTheme.typography.labelSmall)
    }
}

/** One threshold in full: its value now and how it moved, a chart when it has two dated values, then its values newest first. */
@Composable
private fun ThresholdBlock(section: ThresholdHistorySection, state: ThresholdHistoryState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(thresholdLabel(section.threshold), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    ThresholdHistoryTexts.current(section).string(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                ChangeText(section, MaterialTheme.typography.labelMedium)
            }
        }
        section.chart?.let { placed -> ThresholdHistoryTexts.chart(section, detailed = true)?.let { ChartPanel(section, placed, it) } }
        Column {
            section.shownRows.forEach { row ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA))
                HistoryRow(section, row, state)
            }
        }
        if (section.canShowAll) {
            AppTextButton(onClick = { state.eventSink(ThresholdHistoryEvent.ToggleShowAll(section.threshold, section.sport)) }) {
                Text(ThresholdHistoryTexts.showAll(section).string())
            }
        }
    }
}

/** The chart, with its lowest and highest values on its axis, and its first date, any undated values and today beneath. */
@Composable
private fun ChartPanel(section: ThresholdHistorySection, placed: ThresholdChart, chart: LineChart, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LineChartUi(chart, height = CHART_HEIGHT)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(ThresholdHistoryTexts.chartStart(placed).string(), style = MaterialTheme.typography.labelSmall, color = muted)
            Text(
                ThresholdHistoryTexts.undated(section)?.string().orEmpty(),
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                textAlign = TextAlign.Center,
            )
            Text(stringResource(Res.string.zones_history_today), style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}

/** A value with its date and source, marked when current, and a menu to remove it. */
@Composable
private fun HistoryRow(
    section: ThresholdHistorySection,
    row: ThresholdHistoryRow,
    state: ThresholdHistoryState,
    modifier: Modifier = Modifier,
) {
    val value = thresholdValue(section, row.entry.value)
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(value, Modifier.width(VALUE_WIDTH), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Column(Modifier.weight(1f)) {
            Text(ThresholdHistoryTexts.date(row).string(), style = MaterialTheme.typography.bodySmall)
            Text(
                sourceLabel(row),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (row.current) {
            Text(
                stringResource(Res.string.zones_history_current),
                Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        if (state.isRemoving(row)) {
            Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) { AppProgressIndicator(small = true) }
        } else {
            RemoveButton(row, ThresholdHistoryTexts.removeLabel(section, row).string(), state)
        }
    }
}

@Composable
private fun RemoveButton(
    row: ThresholdHistoryRow,
    description: String,
    state: ThresholdHistoryState,
    modifier: Modifier = Modifier,
) {
    var confirming by remember { mutableStateOf(false) }
    Box(modifier) {
        AppIconButton(
            AppIcon.Delete,
            description,
            onClick = { confirming = true },
            enabled = state.canRemove,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val remove = stringResource(Res.string.zones_history_remove)
        AppMenu(
            expanded = confirming,
            onDismiss = { confirming = false },
            items =
                persistentListOf(
                    AppMenuItem(remove, AppIcon.Delete, destructive = true) {
                        confirming = false
                        state.eventSink(ThresholdHistoryEvent.Remove(row.entry))
                    },
                ),
        )
    }
}

/** "+18 W since Mar 2", "12 s/mi faster since Jun 1", or "Only value"; a threshold with values but nothing to compare shows nothing. */
@Composable
private fun ChangeText(section: ThresholdHistorySection, style: TextStyle, modifier: Modifier = Modifier) {
    val text = changeText(section) ?: return
    val color =
        if (ThresholdHistoryTexts.changed(section)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(text, modifier, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private const val EXPANDED_ROTATION = 90f
private const val DIVIDER_ALPHA = 0.5f
private val OVERVIEW_TILE_WIDTH = 165.dp
private val VALUE_WIDTH = 92.dp
private val CHART_HEIGHT = 150.dp
private val SPARKLINE_HEIGHT = 26.dp
