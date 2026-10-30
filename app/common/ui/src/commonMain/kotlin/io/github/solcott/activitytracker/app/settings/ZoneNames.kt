package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.zone_name_active_recovery
import io.github.solcott.activitytracker.composeApp.resources.zone_name_aerobic
import io.github.solcott.activitytracker.composeApp.resources.zone_name_aerobic_capacity
import io.github.solcott.activitytracker.composeApp.resources.zone_name_anaerobic_capacity
import io.github.solcott.activitytracker.composeApp.resources.zone_name_endurance
import io.github.solcott.activitytracker.composeApp.resources.zone_name_hard
import io.github.solcott.activitytracker.composeApp.resources.zone_name_light
import io.github.solcott.activitytracker.composeApp.resources.zone_name_maximum
import io.github.solcott.activitytracker.composeApp.resources.zone_name_moderate
import io.github.solcott.activitytracker.composeApp.resources.zone_name_neuromuscular
import io.github.solcott.activitytracker.composeApp.resources.zone_name_recovery
import io.github.solcott.activitytracker.composeApp.resources.zone_name_sub_threshold
import io.github.solcott.activitytracker.composeApp.resources.zone_name_super_threshold
import io.github.solcott.activitytracker.composeApp.resources.zone_name_tempo
import io.github.solcott.activitytracker.composeApp.resources.zone_name_threshold
import io.github.solcott.activitytracker.composeApp.resources.zone_name_very_light
import io.github.solcott.activitytracker.composeApp.resources.zone_name_vo2_max
import io.github.solcott.activitytracker.data.training.ZoneName
import io.github.solcott.activitytracker.lexicon.app.Threshold
import org.jetbrains.compose.resources.StringResource

fun zoneName(name: ZoneName): StringResource =
    when (name) {
        ZoneName.Recovery -> Res.string.zone_name_recovery
        ZoneName.Aerobic -> Res.string.zone_name_aerobic
        ZoneName.Tempo -> Res.string.zone_name_tempo
        ZoneName.SubThreshold -> Res.string.zone_name_sub_threshold
        ZoneName.SuperThreshold -> Res.string.zone_name_super_threshold
        ZoneName.AerobicCapacity -> Res.string.zone_name_aerobic_capacity
        ZoneName.AnaerobicCapacity -> Res.string.zone_name_anaerobic_capacity
        ZoneName.ActiveRecovery -> Res.string.zone_name_active_recovery
        ZoneName.Endurance -> Res.string.zone_name_endurance
        ZoneName.Threshold -> Res.string.zone_name_threshold
        ZoneName.Vo2Max -> Res.string.zone_name_vo2_max
        ZoneName.Neuromuscular -> Res.string.zone_name_neuromuscular
        ZoneName.VeryLight -> Res.string.zone_name_very_light
        ZoneName.Light -> Res.string.zone_name_light
        ZoneName.Moderate -> Res.string.zone_name_moderate
        ZoneName.Hard -> Res.string.zone_name_hard
        ZoneName.Maximum -> Res.string.zone_name_maximum
    }
