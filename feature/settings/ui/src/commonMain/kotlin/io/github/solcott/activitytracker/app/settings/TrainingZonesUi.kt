package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.VisibilityPicker
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableScrollColumn
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.sportLabel
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_edit
import io.github.solcott.activitytracker.composeApp.resources.zones_add_sport
import io.github.solcott.activitytracker.composeApp.resources.zones_history
import io.github.solcott.activitytracker.composeApp.resources.zones_sports_help
import io.github.solcott.activitytracker.composeApp.resources.zones_title
import io.github.solcott.activitytracker.composeApp.resources.zones_visibility_heading
import io.github.solcott.activitytracker.composeApp.resources.zones_visibility_help
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.data.training.Zone
import io.github.solcott.activitytracker.data.training.ZoneKind
import io.github.solcott.activitytracker.data.training.ZoneModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

@CircuitInject(TrainingZonesScreen::class, SessionScope::class)
@Composable
fun TrainingZonesUi(state: TrainingZonesState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.zones_title),
                onBack = { state.eventSink(TrainingZonesEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val retry = { state.eventSink(TrainingZonesEvent.Retry) }
        ContentStateLayout(state.content, UiError.Kind.LoadProfile, retry, contentPadding = padding) { zones ->
            ReadableScrollColumn(padding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RefreshStatus(state.content, UiError.Kind.LoadProfile, retry)
                state.error?.let { ErrorText(it) }
                if (zones != null) Zones(state, zones)
            }
        }
    }
}

/** The visibility, the history, then the defaults and each sport, and adding a sport. */
@Composable
private fun Zones(state: TrainingZonesState, zones: TrainingZones, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (zones.hasZones) {
            VisibilitySection(state, zones.approvesFollowers)
            HorizontalDivider()
        }
        if (zones.hasHistory) {
            AppSecondaryButton(onClick = { state.eventSink(TrainingZonesEvent.History) }) { Text(stringResource(Res.string.zones_history)) }
            HorizontalDivider()
        }
        ZoneSummarySection(zones.defaults, onEdit = { state.eventSink(TrainingZonesEvent.EditDefaults) })
        zones.sports.forEach { summary ->
            HorizontalDivider()
            val sport = summary.sport ?: return@forEach
            ZoneSummarySection(summary, onEdit = { state.eventSink(TrainingZonesEvent.EditSport(sport)) })
        }
        HorizontalDivider()
        AddSport(zones.addableSports, onAdd = { state.eventSink(TrainingZonesEvent.EditSport(it)) })
    }
}

/** One set of zones: a heading with an Edit button, its thresholds, then each kind of zone it offers. */
@Composable
private fun ZoneSummarySection(summary: ZoneSummary, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(TrainingZonesTexts.heading(summary).string(), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            AppTextButton(onClick = onEdit) { Text(stringResource(Res.string.action_edit)) }
        }
        Text(TrainingZonesTexts.help(summary).string(), style = MaterialTheme.typography.bodySmall)
        Thresholds(summary)
        if (summary.offers(ZoneKind.Speed)) ZoneKindSection(summary, ZoneKind.Speed, summary.speedModel, summary.speedZones)
        ZoneKindSection(summary, ZoneKind.HeartRate, summary.heartRateModel, summary.heartRateZones)
        if (summary.offers(ZoneKind.Power)) ZoneKindSection(summary, ZoneKind.Power, summary.powerModel, summary.powerZones)
    }
}

/** One kind of zone in a set: its heading with the model beside it, then its zones. */
@Composable
private fun ZoneKindSection(summary: ZoneSummary, kind: ZoneKind, model: ZoneModel, zones: ImmutableList<Zone>?) {
    ZoneKindHeading(ZoneTexts.heading(kind).string(), ZoneTexts.model(model).string())
    ZoneList(model, zones) { ZoneTexts.range(it, kind, summary.paceUnit).string() }
}

/** The thresholds a set of zones uses; for a sport, one it doesn't set itself is marked as the default's. */
@Composable
private fun Thresholds(summary: ZoneSummary, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TrainingZonesTexts.thresholds(summary).forEach { ThresholdRow(it) }
    }
}

/** "Threshold heart rate · 168 bpm"; "Not set" without a value, and "(default)" after one taken from the defaults. */
@Composable
private fun ThresholdRow(line: ZoneThresholdLine, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(line.label.string(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            line.value.string(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (line.muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** "Heart rate zones" with the model they come from beside it. */
@Composable
private fun ZoneKindHeading(heading: String, model: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(heading, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(model, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A menu of the sports that could get their own zones. */
@Composable
private fun AddSport(addable: ImmutableList<String>, onAdd: (String) -> Unit, modifier: Modifier = Modifier) {
    if (addable.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.zones_sports_help), style = MaterialTheme.typography.bodySmall)
        var expanded by remember { mutableStateOf(false) }
        Box {
            AppSecondaryButton(onClick = { expanded = true }) { Text(stringResource(Res.string.zones_add_sport)) }
            val items =
                addable
                    .map { sport ->
                        AppMenuItem(sportLabel(sport)) {
                            expanded = false
                            onAdd(sport)
                        }
                    }
                    .toImmutableList()
            AppMenu(expanded = expanded, onDismiss = { expanded = false }, items = items)
        }
    }
}

/** Who can see every set of zones: one setting for the defaults and each sport, saved as soon as it's picked. */
@Composable
private fun VisibilitySection(state: TrainingZonesState, approvesFollowers: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.zones_visibility_heading), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.zones_visibility_help), style = MaterialTheme.typography.bodySmall)
        VisibilityPicker(
            state.visibility,
            { state.eventSink(TrainingZonesEvent.VisibilityChanged(it)) },
            approvesFollowers,
            Modifier.fillMaxWidth(),
            enabled = !state.savingVisibility,
            options = state.visibilityOptions,
        )
    }
}

@Composable internal fun paceUnitLabel(unit: PaceUnit): String = ZoneTexts.paceUnit(unit).string()
