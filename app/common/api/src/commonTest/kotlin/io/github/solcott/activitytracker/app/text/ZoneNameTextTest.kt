package io.github.solcott.activitytracker.app.text

import io.github.solcott.activitytracker.data.training.ZoneName
import kotlin.test.Test
import kotlin.test.assertEquals

/** A computed zone's name, the one choice the zones pages and an activity's time in zone share (dev/shared-display-text). */
class ZoneNameTextTest {
    @Test
    fun aZoneIsNamedByItsSnakeCasedName() {
        assertEquals(AppText.named("zone_name_aerobic"), ZoneNameText.of(ZoneName.Aerobic))
        assertEquals(AppText.named("zone_name_vo2_max"), ZoneNameText.of(ZoneName.Vo2Max))
        assertEquals(AppText.named("zone_name_sub_threshold"), ZoneNameText.of(ZoneName.SubThreshold))
    }

    @Test
    fun everyNameHasItsOwnString() {
        val names = ZoneName.entries.map { (ZoneNameText.of(it) as AppText.Named).name }
        assertEquals(ZoneName.entries.size, names.toSet().size)
    }
}
