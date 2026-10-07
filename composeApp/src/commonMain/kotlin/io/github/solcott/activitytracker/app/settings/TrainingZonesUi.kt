package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import io.github.solcott.activitytracker.app.ui.VisibilityPicker
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ScrollbarBox
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.sportLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_edit
import io.github.solcott.activitytracker.composeApp.resources.zones_add_sport
import io.github.solcott.activitytracker.composeApp.resources.zones_heart_rate_heading
import io.github.solcott.activitytracker.composeApp.resources.zones_overview_default
import io.github.solcott.activitytracker.composeApp.resources.zones_overview_default_help
import io.github.solcott.activitytracker.composeApp.resources.zones_overview_from_default
import io.github.solcott.activitytracker.composeApp.resources.zones_overview_heading
import io.github.solcott.activitytracker.composeApp.resources.zones_overview_sport_help
import io.github.solcott.activitytracker.composeApp.resources.zones_pace_unit_100m
import io.github.solcott.activitytracker.composeApp.resources.zones_pace_unit_100yd
import io.github.solcott.activitytracker.composeApp.resources.zones_pace_unit_km
import io.github.solcott.activitytracker.composeApp.resources.zones_pace_unit_mi
import io.github.solcott.activitytracker.composeApp.resources.zones_power_heading
import io.github.solcott.activitytracker.composeApp.resources.zones_speed_heading
import io.github.solcott.activitytracker.composeApp.resources.zones_sports_help
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_max_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_resting_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_threshold_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_threshold_pace
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_threshold_power
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_unset
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_value_bpm
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_value_pace
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_value_watts
import io.github.solcott.activitytracker.composeApp.resources.zones_title
import io.github.solcott.activitytracker.composeApp.resources.zones_unit_bpm
import io.github.solcott.activitytracker.composeApp.resources.zones_unit_watts
import io.github.solcott.activitytracker.composeApp.resources.zones_visibility_heading
import io.github.solcott.activitytracker.composeApp.resources.zones_visibility_help
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.training.PaceText
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.data.training.ZoneKind
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.StringResource
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
        val scroll = rememberScrollState()
        ScrollbarBox(scroll, contentPadding = padding) {
            Column(
                Modifier.verticalScroll(scroll).padding(padding).readableWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
                val defaults = state.defaults
                if (defaults == null) {
                    if (state.error == null) AppProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                } else {
                    if (state.hasZones) {
                        VisibilitySection(state)
                        HorizontalDivider()
                    }
                    ZoneSummarySection(defaults, onEdit = { state.eventSink(TrainingZonesEvent.EditDefaults) })
                    state.sports.forEach { summary ->
                        HorizontalDivider()
                        val sport = summary.sport ?: return@forEach
                        ZoneSummarySection(summary, onEdit = { state.eventSink(TrainingZonesEvent.EditSport(sport)) })
                    }
                    HorizontalDivider()
                    AddSport(state)
                }
            }
        }
    }
}

/** One set of zones: a heading with an Edit button, its thresholds, then each kind of zone it offers. */
@Composable
private fun ZoneSummarySection(summary: ZoneSummary, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val sport = summary.sport
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (sport == null) stringResource(Res.string.zones_overview_default)
                else stringResource(Res.string.zones_overview_heading, sportLabel(sport)),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            AppTextButton(onClick = onEdit) { Text(stringResource(Res.string.action_edit)) }
        }
        Text(
            if (sport == null) stringResource(Res.string.zones_overview_default_help)
            else stringResource(Res.string.zones_overview_sport_help, sportLabel(sport)),
            style = MaterialTheme.typography.bodySmall,
        )
        Thresholds(summary)
        val unit = paceUnitLabel(summary.paceUnit)
        if (ZoneKind.Speed in summary.kinds) {
            ZoneKindHeading(stringResource(Res.string.zones_speed_heading), zoneModelLabel(summary.speedModel))
            ZoneList(summary.speedModel, summary.speedZones) { paceRange(it, summary.paceUnit, unit) }
        }
        ZoneKindHeading(stringResource(Res.string.zones_heart_rate_heading), zoneModelLabel(summary.heartRateModel))
        ZoneList(summary.heartRateModel, summary.heartRateZones) { numberRange(it, stringResource(Res.string.zones_unit_bpm)) }
        if (ZoneKind.Power in summary.kinds) {
            ZoneKindHeading(stringResource(Res.string.zones_power_heading), zoneModelLabel(summary.powerModel))
            ZoneList(summary.powerModel, summary.powerZones) { numberRange(it, stringResource(Res.string.zones_unit_watts)) }
        }
    }
}

/** The thresholds a set of zones uses; for a sport, one it doesn't set itself is marked as the default's. */
@Composable
private fun Thresholds(summary: ZoneSummary, modifier: Modifier = Modifier) {
    val training = summary.training
    val own = summary.override
    val isSport = summary.sport != null
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (ZoneKind.Speed in summary.kinds) {
            val pace =
                training.thresholdSpeed?.let {
                    stringResource(
                        Res.string.zones_summary_value_pace,
                        PaceText.format(it, summary.paceUnit),
                        paceUnitLabel(summary.paceUnit),
                    )
                }
            ThresholdRow(Res.string.zones_summary_threshold_pace, pace, inherited = false)
        }
        ThresholdRow(
            Res.string.zones_summary_threshold_heart_rate,
            training.thresholdHeartRate?.let { stringResource(Res.string.zones_summary_value_bpm, it.toString()) },
            inherited = isSport && own?.thresholdHeartRate == null,
        )
        ThresholdRow(
            Res.string.zones_summary_max_heart_rate,
            training.maxHeartRate?.let { stringResource(Res.string.zones_summary_value_bpm, it.toString()) },
            inherited = isSport && own?.maxHeartRate == null,
        )
        if (!isSport) {
            ThresholdRow(
                Res.string.zones_summary_resting_heart_rate,
                training.restingHeartRate?.let { stringResource(Res.string.zones_summary_value_bpm, it.toString()) },
                inherited = false,
            )
        }
        if (ZoneKind.Power in summary.kinds) {
            ThresholdRow(
                Res.string.zones_summary_threshold_power,
                training.functionalThresholdPower?.let { stringResource(Res.string.zones_summary_value_watts, it.toString()) },
                inherited = isSport && own?.functionalThresholdPower == null,
            )
        }
    }
}

/** "Threshold heart rate · 168 bpm"; "Not set" without a value, and "(default)" after one taken from the defaults. */
@Composable
private fun ThresholdRow(label: StringResource, value: String?, inherited: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        val shown =
            when {
                value == null -> stringResource(Res.string.zones_summary_unset)
                inherited -> stringResource(Res.string.zones_overview_from_default, value)
                else -> value
            }
        Text(
            shown,
            style = MaterialTheme.typography.bodyMedium,
            color = if (value == null || inherited) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
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
private fun AddSport(state: TrainingZonesState, modifier: Modifier = Modifier) {
    if (state.addableSports.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.zones_sports_help), style = MaterialTheme.typography.bodySmall)
        var expanded by remember { mutableStateOf(false) }
        Box {
            AppSecondaryButton(onClick = { expanded = true }) { Text(stringResource(Res.string.zones_add_sport)) }
            val items =
                state.addableSports
                    .map { sport ->
                        AppMenuItem(sportLabel(sport)) {
                            expanded = false
                            state.eventSink(TrainingZonesEvent.EditSport(sport))
                        }
                    }
                    .toImmutableList()
            AppMenu(expanded = expanded, onDismiss = { expanded = false }, items = items)
        }
    }
}

/** Who can see every set of zones: one setting for the defaults and each sport, saved as soon as it's picked. */
@Composable
private fun VisibilitySection(state: TrainingZonesState, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.zones_visibility_heading), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.zones_visibility_help), style = MaterialTheme.typography.bodySmall)
        VisibilityPicker(
            state.visibility,
            { state.eventSink(TrainingZonesEvent.VisibilityChanged(it)) },
            Modifier.fillMaxWidth(),
            enabled = !state.savingVisibility,
        )
    }
}

@Composable
internal fun paceUnitLabel(unit: PaceUnit): String =
    stringResource(
        when (unit) {
            PaceUnit.PerKilometer -> Res.string.zones_pace_unit_km
            PaceUnit.PerMile -> Res.string.zones_pace_unit_mi
            PaceUnit.Per100Meters -> Res.string.zones_pace_unit_100m
            PaceUnit.Per100Yards -> Res.string.zones_pace_unit_100yd
        }
    )
