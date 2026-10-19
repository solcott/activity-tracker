package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.chart_cadence
import io.github.solcott.activitytracker.composeApp.resources.chart_depth
import io.github.solcott.activitytracker.composeApp.resources.chart_elevation
import io.github.solcott.activitytracker.composeApp.resources.chart_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.chart_power
import io.github.solcott.activitytracker.composeApp.resources.chart_speed
import io.github.solcott.activitytracker.composeApp.resources.chart_temperature
import io.github.solcott.activitytracker.composeApp.resources.detail_section_cycling_dynamics
import io.github.solcott.activitytracker.composeApp.resources.detail_section_device
import io.github.solcott.activitytracker.composeApp.resources.detail_section_ebike
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
import org.jetbrains.compose.resources.stringResource

/** A card's title: its section's name. */
@Composable
internal fun sectionTitle(section: DetailSection): String =
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

/** Gaps shared by the page's cards, so every card lines up. */
internal val CARD_PADDING = 16.dp
internal val ITEM_GAP = 12.dp
