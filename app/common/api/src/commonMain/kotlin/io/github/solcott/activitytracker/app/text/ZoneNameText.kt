package io.github.solcott.activitytracker.app.text

import io.github.solcott.activitytracker.data.training.ZoneName

/** What a computed zone is called ("Aerobic"), chosen once for the zones pages and an activity's time in zone (dev/shared-display-text). */
object ZoneNameText {
    fun of(name: ZoneName): AppText =
        AppText.named(
            when (name) {
                ZoneName.Recovery -> "zone_name_recovery"
                ZoneName.Aerobic -> "zone_name_aerobic"
                ZoneName.Tempo -> "zone_name_tempo"
                ZoneName.SubThreshold -> "zone_name_sub_threshold"
                ZoneName.SuperThreshold -> "zone_name_super_threshold"
                ZoneName.AerobicCapacity -> "zone_name_aerobic_capacity"
                ZoneName.AnaerobicCapacity -> "zone_name_anaerobic_capacity"
                ZoneName.ActiveRecovery -> "zone_name_active_recovery"
                ZoneName.Endurance -> "zone_name_endurance"
                ZoneName.Threshold -> "zone_name_threshold"
                ZoneName.Vo2Max -> "zone_name_vo2_max"
                ZoneName.Neuromuscular -> "zone_name_neuromuscular"
                ZoneName.VeryLight -> "zone_name_very_light"
                ZoneName.Light -> "zone_name_light"
                ZoneName.Moderate -> "zone_name_moderate"
                ZoneName.Hard -> "zone_name_hard"
                ZoneName.Maximum -> "zone_name_maximum"
            },
        )
}
