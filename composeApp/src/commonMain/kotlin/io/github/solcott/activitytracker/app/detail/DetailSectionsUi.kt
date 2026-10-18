package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.sensorLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.chart_cadence
import io.github.solcott.activitytracker.composeApp.resources.chart_depth
import io.github.solcott.activitytracker.composeApp.resources.chart_elevation
import io.github.solcott.activitytracker.composeApp.resources.chart_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.chart_power
import io.github.solcott.activitytracker.composeApp.resources.chart_speed
import io.github.solcott.activitytracker.composeApp.resources.chart_temperature
import io.github.solcott.activitytracker.composeApp.resources.detail_collapse_all
import io.github.solcott.activitytracker.composeApp.resources.detail_device
import io.github.solcott.activitytracker.composeApp.resources.detail_device_unknown
import io.github.solcott.activitytracker.composeApp.resources.detail_expand_all
import io.github.solcott.activitytracker.composeApp.resources.detail_section_collapsed
import io.github.solcott.activitytracker.composeApp.resources.detail_section_cycling_dynamics
import io.github.solcott.activitytracker.composeApp.resources.detail_section_device
import io.github.solcott.activitytracker.composeApp.resources.detail_section_ebike
import io.github.solcott.activitytracker.composeApp.resources.detail_section_expanded
import io.github.solcott.activitytracker.composeApp.resources.detail_section_gears
import io.github.solcott.activitytracker.composeApp.resources.detail_section_laps
import io.github.solcott.activitytracker.composeApp.resources.detail_section_legs
import io.github.solcott.activitytracker.composeApp.resources.detail_section_lengths
import io.github.solcott.activitytracker.composeApp.resources.detail_section_oxygen
import io.github.solcott.activitytracker.composeApp.resources.detail_section_physiology
import io.github.solcott.activitytracker.composeApp.resources.detail_section_respiration
import io.github.solcott.activitytracker.composeApp.resources.detail_section_running_dynamics
import io.github.solcott.activitytracker.composeApp.resources.detail_section_sets
import io.github.solcott.activitytracker.composeApp.resources.detail_section_tank_pressure
import io.github.solcott.activitytracker.composeApp.resources.detail_sections_heading
import io.github.solcott.activitytracker.composeApp.resources.detail_sensor_count
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/** The heading over the sections, and one icon button to expand or collapse them all. */
@Composable
internal fun SectionsHeader(state: ActivityDetailState.Loaded, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.detail_sections_heading), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        AppIconButton(
            if (state.anyExpanded) AppIcon.CollapseAll else AppIcon.ExpandAll,
            stringResource(if (state.anyExpanded) Res.string.detail_collapse_all else Res.string.detail_expand_all),
            onClick = { state.eventSink(ActivityDetailEvent.ToggleAll) },
        )
    }
}

/** A card holding the overview, or a section: the same surface, so the screen reads as one stack. */
@Composable
internal fun DetailCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

/** A header that expands or collapses [section], then the section in full, or an overview of it. */
@Composable
internal fun SectionCard(section: DetailSection, state: ActivityDetailState.Loaded, modifier: Modifier = Modifier) {
    val expanded = section in state.expanded
    DetailCard(modifier) {
        Column {
            SectionHeader(section, expanded, onToggle = { state.eventSink(ActivityDetailEvent.ToggleSection(section)) })
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SectionContent(section, state, Modifier.padding(CARD_PADDING))
            } else {
                SectionOverview(section, state, Modifier.padding(start = CARD_PADDING, end = CARD_PADDING, bottom = CARD_PADDING))
            }
        }
    }
}

@Composable
private fun SectionHeader(
    section: DetailSection,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stateText = stringResource(if (expanded) Res.string.detail_section_expanded else Res.string.detail_section_collapsed)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = stateText }
            .padding(horizontal = CARD_PADDING, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlatformIcon(
            AppIcon.Chevron,
            contentDescription = null,
            modifier = Modifier.rotate(if (expanded) EXPANDED_ROTATION else 0f),
            tint = MaterialTheme.colorScheme.primary,
            size = 20.dp,
        )
        Text(sectionTitle(section), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun sectionTitle(section: DetailSection): String =
    stringResource(
        when (section) {
            DetailSection.HeartRate -> Res.string.chart_heart_rate
            DetailSection.Power -> Res.string.chart_power
            DetailSection.CyclingDynamics -> Res.string.detail_section_cycling_dynamics
            DetailSection.Speed -> Res.string.chart_speed
            DetailSection.Cadence -> Res.string.chart_cadence
            DetailSection.Elevation -> Res.string.chart_elevation
            DetailSection.Temperature -> Res.string.chart_temperature
            DetailSection.Physiology -> Res.string.detail_section_physiology
            DetailSection.Depth -> Res.string.chart_depth
            DetailSection.Oxygen -> Res.string.detail_section_oxygen
            DetailSection.TankPressure -> Res.string.detail_section_tank_pressure
            DetailSection.Respiration -> Res.string.detail_section_respiration
            DetailSection.RunningDynamics -> Res.string.detail_section_running_dynamics
            DetailSection.EBike -> Res.string.detail_section_ebike
            DetailSection.Gears -> Res.string.detail_section_gears
            DetailSection.Legs -> Res.string.detail_section_legs
            DetailSection.Lengths -> Res.string.detail_section_lengths
            DetailSection.Sets -> Res.string.detail_section_sets
            DetailSection.Laps -> Res.string.detail_section_laps
            DetailSection.Device -> Res.string.detail_section_device
        }
    )

@Composable
private fun SectionContent(section: DetailSection, state: ActivityDetailState.Loaded, modifier: Modifier = Modifier) {
    if (section == DetailSection.Device) DeviceDetails(state, modifier) else SectionCharts(section, state, modifier)
}

/** The recording device, then each sensor paired with it. */
@Composable
private fun DeviceDetails(state: ActivityDetailState.Loaded, modifier: Modifier = Modifier) {
    val record = state.activity.record
    val sensors = record.sensors.orEmpty().map { sensorLabel(it.kind, it.manufacturer, it.model) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
        LabelledValues(
            stringResource(Res.string.detail_device),
            persistentListOf(record.device ?: stringResource(Res.string.detail_device_unknown)),
        )
        if (sensors.isNotEmpty()) LabelledValues(stringResource(Res.string.detail_sensor_count), sensors.toImmutableList())
    }
}

@Composable
private fun LabelledValues(label: String, values: ImmutableList<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        values.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

/** Gaps shared by the overview and the sections, so every card lines up. */
internal val CARD_PADDING = 16.dp
internal val ITEM_GAP = 12.dp
private const val EXPANDED_ROTATION = 90f
