package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.sportLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.threshold_source_device
import io.github.solcott.activitytracker.composeApp.resources.threshold_source_device_named
import io.github.solcott.activitytracker.composeApp.resources.threshold_source_garmin
import io.github.solcott.activitytracker.composeApp.resources.threshold_source_intervals_icu
import io.github.solcott.activitytracker.composeApp.resources.threshold_source_manual
import io.github.solcott.activitytracker.composeApp.resources.threshold_source_strava
import io.github.solcott.activitytracker.composeApp.resources.threshold_source_trainer_road
import io.github.solcott.activitytracker.composeApp.resources.threshold_source_training_peaks
import io.github.solcott.activitytracker.composeApp.resources.threshold_source_unknown
import io.github.solcott.activitytracker.composeApp.resources.threshold_source_wahoo
import io.github.solcott.activitytracker.composeApp.resources.threshold_source_zwift
import io.github.solcott.activitytracker.composeApp.resources.zones_history
import io.github.solcott.activitytracker.composeApp.resources.zones_history_current
import io.github.solcott.activitytracker.composeApp.resources.zones_history_date_unknown
import io.github.solcott.activitytracker.composeApp.resources.zones_history_detail
import io.github.solcott.activitytracker.composeApp.resources.zones_history_empty
import io.github.solcott.activitytracker.composeApp.resources.zones_history_help
import io.github.solcott.activitytracker.composeApp.resources.zones_history_remove
import io.github.solcott.activitytracker.composeApp.resources.zones_history_remove_description
import io.github.solcott.activitytracker.composeApp.resources.zones_history_sport_heading
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_max_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_resting_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_threshold_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_threshold_pace
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_threshold_power
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_value_bpm
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_value_pace
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_value_watts
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.training.PaceText
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.ThresholdEntry
import io.github.solcott.activitytracker.lexicon.app.ThresholdSource
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
        val listState = rememberLazyListState()
        ReadableList(listState, Modifier.fillMaxSize(), padding) { listPadding ->
            LazyColumn(state = listState, contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.error?.let { error ->
                    item { Text(errorText(error), Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.error) }
                }
                val sections = state.sections
                when {
                    sections == null ->
                        if (state.error == null) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { AppProgressIndicator() }
                            }
                        }

                    sections.isEmpty() -> item { Text(stringResource(Res.string.zones_history_empty), Modifier.padding(top = 16.dp)) }

                    else -> {
                        item {
                            Text(
                                stringResource(Res.string.zones_history_help),
                                Modifier.padding(top = 16.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        sections.forEach { section ->
                            item(key = "${section.sport}/${section.threshold}") {
                                Text(sectionHeading(section), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
                            }
                            items(section.rows, key = { "${section.sport}/${section.threshold}/${it.entry.setAt}/${it.entry.value}" }) { row
                                ->
                                HistoryRow(section, row, state)
                                HorizontalDivider(Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Threshold power", or "Run · Threshold pace" for a sport's. */
@Composable
private fun sectionHeading(section: ThresholdHistorySection): String {
    val threshold = thresholdLabel(section.threshold)
    val sport = section.sport ?: return threshold
    return stringResource(Res.string.zones_history_sport_heading, sportLabel(sport), threshold)
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
    val heading = sectionHeading(section)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = MaterialTheme.typography.bodyLarge)
                if (row.current) {
                    Text(
                        stringResource(Res.string.zones_history_current),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                stringResource(
                    Res.string.zones_history_detail,
                    row.date ?: stringResource(Res.string.zones_history_date_unknown),
                    sourceLabel(row.entry),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.removing == row.entry) {
            AppProgressIndicator(small = true)
        } else {
            var confirming by remember { mutableStateOf(false) }
            Box {
                AppIconButton(
                    AppIcon.Delete,
                    stringResource(Res.string.zones_history_remove_description, value, heading),
                    onClick = { confirming = true },
                    enabled = state.removing == null,
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
    }
}

@Composable
private fun thresholdLabel(threshold: String): String =
    when (threshold) {
        Threshold.FUNCTIONAL_THRESHOLD_POWER -> stringResource(Res.string.zones_summary_threshold_power)
        Threshold.MAX_HEART_RATE -> stringResource(Res.string.zones_summary_max_heart_rate)
        Threshold.THRESHOLD_HEART_RATE -> stringResource(Res.string.zones_summary_threshold_heart_rate)
        Threshold.RESTING_HEART_RATE -> stringResource(Res.string.zones_summary_resting_heart_rate)
        Threshold.THRESHOLD_SPEED -> stringResource(Res.string.zones_summary_threshold_pace)
        else -> threshold
    }

@Composable
private fun thresholdValue(threshold: String, value: Long, paceUnit: PaceUnit): String =
    when (threshold) {
        Threshold.FUNCTIONAL_THRESHOLD_POWER -> stringResource(Res.string.zones_summary_value_watts, value.toString())
        Threshold.THRESHOLD_SPEED ->
            stringResource(Res.string.zones_summary_value_pace, PaceText.format(value, paceUnit), paceUnitLabel(paceUnit))
        Threshold.MAX_HEART_RATE,
        Threshold.THRESHOLD_HEART_RATE,
        Threshold.RESTING_HEART_RATE -> stringResource(Res.string.zones_summary_value_bpm, value.toString())
        else -> value.toString()
    }

/** Where [entry] came from; a source this client doesn't know shows as its token. */
@Composable
private fun sourceLabel(entry: ThresholdEntry): String {
    val device = entry.device
    return when (val source = entry.source) {
        null -> stringResource(Res.string.threshold_source_unknown)
        ThresholdSource.MANUAL -> stringResource(Res.string.threshold_source_manual)
        ThresholdSource.DEVICE ->
            if (device == null) stringResource(Res.string.threshold_source_device)
            else stringResource(Res.string.threshold_source_device_named, device)
        ThresholdSource.GARMIN -> stringResource(Res.string.threshold_source_garmin)
        ThresholdSource.TRAINER_ROAD -> stringResource(Res.string.threshold_source_trainer_road)
        ThresholdSource.TRAINING_PEAKS -> stringResource(Res.string.threshold_source_training_peaks)
        ThresholdSource.INTERVALS_ICU -> stringResource(Res.string.threshold_source_intervals_icu)
        ThresholdSource.ZWIFT -> stringResource(Res.string.threshold_source_zwift)
        ThresholdSource.WAHOO -> stringResource(Res.string.threshold_source_wahoo)
        ThresholdSource.STRAVA -> stringResource(Res.string.threshold_source_strava)
        else -> source
    }
}
