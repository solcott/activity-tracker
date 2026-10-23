package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppSwitch
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_retry
import io.github.solcott.activitytracker.composeApp.resources.chart_source_from
import io.github.solcott.activitytracker.composeApp.resources.chart_source_then
import io.github.solcott.activitytracker.composeApp.resources.data_kind_cadence
import io.github.solcott.activitytracker.composeApp.resources.data_kind_distance_speed
import io.github.solcott.activitytracker.composeApp.resources.data_kind_dive
import io.github.solcott.activitytracker.composeApp.resources.data_kind_elevation
import io.github.solcott.activitytracker.composeApp.resources.data_kind_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.data_kind_laps
import io.github.solcott.activitytracker.composeApp.resources.data_kind_map
import io.github.solcott.activitytracker.composeApp.resources.data_kind_power
import io.github.solcott.activitytracker.composeApp.resources.data_kind_running_dynamics
import io.github.solcott.activitytracker.composeApp.resources.data_kind_temperature
import io.github.solcott.activitytracker.composeApp.resources.data_source_comes_from
import io.github.solcott.activitytracker.composeApp.resources.data_source_include
import io.github.solcott.activitytracker.composeApp.resources.data_source_left_out
import io.github.solcott.activitytracker.composeApp.resources.data_source_started
import io.github.solcott.activitytracker.composeApp.resources.data_sources
import io.github.solcott.activitytracker.data.activity.DataKind
import io.github.solcott.activitytracker.lexicon.app.MemberView
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * Where each kind of the owner's combined activity's data comes from (`feed/combined-sources`): one row per kind, switched off when it's
 * left out, with a menu of the members that recorded it when more than one did. Shown in the owner's combination editor.
 */
@Composable
internal fun DataSourcesSection(sources: DataSources, eventSink: (ActivityDetailEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.data_sources), style = MaterialTheme.typography.labelLarge)
        if (sources.isLoading) AppProgressIndicator(small = true)
        sources.error?.let { error ->
            Text(errorText(error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            AppTextButton(onClick = { eventSink(ActivityDetailEvent.RetrySources) }) { Text(stringResource(Res.string.action_retry)) }
        }
        sources.kinds.forEach { row -> SourceRow(row, saving = sources.saving, eventSink) }
    }
}

@Composable
private fun SourceRow(row: DataSourceRow, saving: DataKind?, eventSink: (ActivityDetailEvent) -> Unit) {
    val label = dataKindLabel(row.kind)
    val idle = saving == null
    val chosen = row.chosen
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        when {
            !row.included ->
                Text(
                    stringResource(Res.string.data_source_left_out),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            chosen != null && row.offered.isNotEmpty() -> SourceMenu(row, label, chosen, enabled = idle, eventSink)
            chosen != null ->
                Text(
                    stringResource(Res.string.chart_source_from, sourceName(chosen)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        if (saving == row.kind) AppProgressIndicator(small = true)
        val include = stringResource(Res.string.data_source_include, label)
        AppSwitch(
            checked = row.included,
            onCheckedChange = { eventSink(ActivityDetailEvent.SetIncluded(row.kind, it)) },
            modifier = Modifier.semantics { contentDescription = include },
            enabled = idle,
        )
    }
}

/** The member [row]'s kind comes from, as a compact trigger opening a menu of the members that recorded it. */
@Composable
private fun SourceMenu(
    row: DataSourceRow,
    label: String,
    chosen: MemberView,
    enabled: Boolean,
    eventSink: (ActivityDetailEvent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val shown = sourceName(chosen)
    val description = stringResource(Res.string.data_source_comes_from, label, shown)
    Box {
        AppTextButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = description },
            enabled = enabled,
        ) {
            Text(shown, style = MaterialTheme.typography.bodyMedium)
            PlatformIcon(AppIcon.DropDown, null)
        }
        val items =
            row.offered
                .map { member ->
                    AppMenuItem(sourceName(member), checked = member.uri == chosen.uri) {
                        if (member.uri != chosen.uri) eventSink(ActivityDetailEvent.ChooseSource(row.kind, member.uri))
                    }
                }
                .toImmutableList()
        AppMenu(expanded = expanded, onDismiss = { expanded = false }, items = items)
    }
}

/** On the owner's page, a member's device, or else when it started: the owner always sees their own start. */
@Composable
private fun sourceName(member: MemberView): String =
    member.device
        ?: stringResource(
            Res.string.data_source_started,
            member.startedAt.toLocalDateTime(LocalTimeZone.current).format(timeFormat()),
        )

@Composable
internal fun dataKindLabel(kind: DataKind): String =
    stringResource(
        when (kind) {
            DataKind.Map -> Res.string.data_kind_map
            DataKind.DistanceSpeed -> Res.string.data_kind_distance_speed
            DataKind.Elevation -> Res.string.data_kind_elevation
            DataKind.HeartRate -> Res.string.data_kind_heart_rate
            DataKind.Power -> Res.string.data_kind_power
            DataKind.Cadence -> Res.string.data_kind_cadence
            DataKind.Temperature -> Res.string.data_kind_temperature
            DataKind.RunningDynamics -> Res.string.data_kind_running_dynamics
            DataKind.Laps -> Res.string.data_kind_laps
            DataKind.Dive -> Res.string.data_kind_dive
        }
    )

/**
 * A card's caption on a combined activity: the devices its data came from, in time order ("From Edge 840", "From Forerunner, then Phone").
 * Each member is named by device, or by sport for a file that named none, since its start may be hidden from the viewer.
 */
@Composable
internal fun ChartSourceCaption(members: ImmutableList<MemberView>, modifier: Modifier = Modifier) {
    if (members.isEmpty()) return
    val names = members.map { memberName(it) }
    val joined = names.reduce { acc, name -> stringResource(Res.string.chart_source_then, acc, name) }
    Text(
        stringResource(Res.string.chart_source_from, joined),
        modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
