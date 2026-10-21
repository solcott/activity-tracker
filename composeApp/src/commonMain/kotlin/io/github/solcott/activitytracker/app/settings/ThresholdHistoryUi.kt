package io.github.solcott.activitytracker.app.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
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
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.sportLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.zones_history
import io.github.solcott.activitytracker.composeApp.resources.zones_history_collapse_all
import io.github.solcott.activitytracker.composeApp.resources.zones_history_collapsed
import io.github.solcott.activitytracker.composeApp.resources.zones_history_current
import io.github.solcott.activitytracker.composeApp.resources.zones_history_date_unknown
import io.github.solcott.activitytracker.composeApp.resources.zones_history_default
import io.github.solcott.activitytracker.composeApp.resources.zones_history_default_help
import io.github.solcott.activitytracker.composeApp.resources.zones_history_empty
import io.github.solcott.activitytracker.composeApp.resources.zones_history_expand_all
import io.github.solcott.activitytracker.composeApp.resources.zones_history_expanded
import io.github.solcott.activitytracker.composeApp.resources.zones_history_help
import io.github.solcott.activitytracker.composeApp.resources.zones_history_only_value
import io.github.solcott.activitytracker.composeApp.resources.zones_history_remove
import io.github.solcott.activitytracker.composeApp.resources.zones_history_remove_description
import io.github.solcott.activitytracker.composeApp.resources.zones_history_show_all
import io.github.solcott.activitytracker.composeApp.resources.zones_history_show_fewer
import io.github.solcott.activitytracker.composeApp.resources.zones_history_sport_help
import io.github.solcott.activitytracker.composeApp.resources.zones_history_summary
import io.github.solcott.activitytracker.composeApp.resources.zones_history_thresholds
import io.github.solcott.activitytracker.composeApp.resources.zones_history_today
import io.github.solcott.activitytracker.composeApp.resources.zones_history_undated
import io.github.solcott.activitytracker.composeApp.resources.zones_history_values
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.pluralStringResource
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
                    state.error?.let { error ->
                        item { Text(errorText(error), Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.error) }
                    }
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
            stringResource(if (state.anyExpanded) Res.string.zones_history_collapse_all else Res.string.zones_history_expand_all),
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
    val sport = group.sport
    val stateText = stringResource(if (group.expanded) Res.string.zones_history_expanded else Res.string.zones_history_collapsed)
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
            Text(
                if (sport == null) stringResource(Res.string.zones_history_default) else sportLabel(sport),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (sport == null) stringResource(Res.string.zones_history_default_help)
                else stringResource(Res.string.zones_history_sport_help, sportLabel(sport)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(
                Res.string.zones_history_summary,
                pluralStringResource(Res.plurals.zones_history_thresholds, group.thresholds.size, group.thresholds.size),
                pluralStringResource(Res.plurals.zones_history_values, group.valueCount, group.valueCount),
            ),
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
            thresholdValue(section.threshold, section.newest.entry.value, section.paceUnit),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        section.chart?.let { ThresholdStepChart(it, chartDescription(section, it), height = 26.dp) }
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
                    thresholdValue(section.threshold, section.newest.entry.value, section.paceUnit),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                ChangeText(section, MaterialTheme.typography.labelMedium)
            }
        }
        section.chart?.let { ChartPanel(section, it) }
        Column {
            section.shownRows.forEach { row ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA))
                HistoryRow(section, row, state)
            }
        }
        if (section.canShowAll) {
            AppTextButton(onClick = { state.eventSink(ThresholdHistoryEvent.ToggleShowAll(section.threshold, section.sport)) }) {
                Text(
                    if (section.showingAll) stringResource(Res.string.zones_history_show_fewer)
                    else stringResource(Res.string.zones_history_show_all, section.rows.size)
                )
            }
        }
    }
}

/** The chart with its highest and lowest values beside it, and its first date and today beneath. */
@Composable
private fun ChartPanel(section: ThresholdHistorySection, chart: ThresholdChart, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val labels = chart.points.map { pointLabel(section, it.value) }.toImmutableList()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                Modifier.width(AXIS_WIDTH).heightIn(min = CHART_HEIGHT).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(pointLabel(section, chart.highest), style = MaterialTheme.typography.labelSmall, color = muted)
                Spacer(Modifier.weight(1f))
                Text(pointLabel(section, chart.lowest), style = MaterialTheme.typography.labelSmall, color = muted)
            }
            ThresholdStepChart(
                chart,
                chartDescription(section, chart),
                Modifier.weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA), RoundedCornerShape(10.dp)),
                labels = labels,
                height = CHART_HEIGHT,
            )
        }
        Row(Modifier.padding(start = AXIS_WIDTH + 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(longDate(chart.start), style = MaterialTheme.typography.labelSmall, color = muted)
            Text(
                if (section.undatedCount > 0)
                    pluralStringResource(Res.plurals.zones_history_undated, section.undatedCount, section.undatedCount)
                else "",
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
    val value = thresholdValue(section.threshold, row.entry.value, section.paceUnit)
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(value, Modifier.width(VALUE_WIDTH), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Column(Modifier.weight(1f)) {
            Text(
                row.date?.let { longDate(it) } ?: stringResource(Res.string.zones_history_date_unknown),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                sourceLabel(row.entry),
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
        if (state.removing == row.entry) {
            Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) { AppProgressIndicator(small = true) }
        } else {
            RemoveButton(row, value, thresholdLabel(section.threshold), state)
        }
    }
}

@Composable
private fun RemoveButton(
    row: ThresholdHistoryRow,
    value: String,
    threshold: String,
    state: ThresholdHistoryState,
    modifier: Modifier = Modifier,
) {
    var confirming by remember { mutableStateOf(false) }
    Box(modifier) {
        AppIconButton(
            AppIcon.Delete,
            stringResource(Res.string.zones_history_remove_description, value, threshold),
            onClick = { confirming = true },
            enabled = state.removing == null,
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
                    }
                ),
        )
    }
}

/** "+18 W since Mar 2", "12 s/mi faster since Jun 1", or "Only value"; a threshold with values but nothing to compare shows nothing. */
@Composable
private fun ChangeText(section: ThresholdHistorySection, style: TextStyle, modifier: Modifier = Modifier) {
    val change = section.change
    val text =
        when {
            section.rows.size == 1 -> stringResource(Res.string.zones_history_only_value)
            change == null -> return
            else -> changeText(section, change)
        }
    val color = if (change == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    Text(text, modifier, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private const val EXPANDED_ROTATION = 90f
private const val DIVIDER_ALPHA = 0.5f
private val OVERVIEW_TILE_WIDTH = 165.dp
private val AXIS_WIDTH = 40.dp
private val VALUE_WIDTH = 92.dp
private val CHART_HEIGHT = 150.dp
