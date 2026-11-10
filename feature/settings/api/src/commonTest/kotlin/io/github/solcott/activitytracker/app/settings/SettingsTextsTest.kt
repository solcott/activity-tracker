package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.collections.immutable.persistentListOf

/** What Settings' rows say about their pages (ui/settings-layout, "Rows summarize their page"), and the line under the units choice. */
class SettingsTextsTest {
    private fun zones(count: Int) = AppText.Plural("settings_privacy_zones_count", count, persistentListOf(AppText.written("$count")))

    @Test
    fun aSummaryNotReadYetIsLeftOut() {
        assertNull(SettingsTexts.defaultVisibility(null))
        assertNull(SettingsTexts.privacyZones(count = null, hidesRouteEnds = false))
        assertNull(SettingsTexts.privacyZones(count = null, hidesRouteEnds = true), "hidden ends alone say nothing about the zones")
        assertNull(SettingsTexts.segmentMatching(null))
    }

    @Test
    fun theDefaultVisibilityIsItsLabel() =
        assertEquals(AppText.named("visibility_followers"), SettingsTexts.defaultVisibility(Visibility.FOLLOWERS))

    @Test
    fun privacyZonesAreCounted() {
        assertEquals(AppText.named("settings_privacy_zones_none"), SettingsTexts.privacyZones(0, hidesRouteEnds = false))
        assertEquals(zones(1), SettingsTexts.privacyZones(1, hidesRouteEnds = false))
        assertEquals(zones(2), SettingsTexts.privacyZones(2, hidesRouteEnds = false))
    }

    @Test
    fun hiddenRouteEndsFollowTheCount() {
        assertEquals(AppText.named("settings_privacy_zones_ends", zones(2)), SettingsTexts.privacyZones(2, hidesRouteEnds = true))
        assertEquals(
            AppText.named("settings_privacy_zones_ends", AppText.named("settings_privacy_zones_none")),
            SettingsTexts.privacyZones(0, hidesRouteEnds = true),
        )
    }

    @Test
    fun segmentMatchingIsOnOrOff() {
        assertEquals(AppText.named("settings_segments_on"), SettingsTexts.segmentMatching(true))
        assertEquals(AppText.named("settings_segments_off"), SettingsTexts.segmentMatching(false))
    }

    @Test
    fun theUnitsNoteSaysWhatTheSystemMeasuresIn() {
        assertEquals(AppText.named("settings_units_metric_example"), SettingsTexts.unitsNote(UnitSystem.Metric, saving = false))
        assertEquals(AppText.named("settings_units_imperial_example"), SettingsTexts.unitsNote(UnitSystem.Imperial, saving = false))
    }

    @Test
    fun aSaveInProgressReplacesTheNote() =
        assertEquals(AppText.named("settings_saving"), SettingsTexts.unitsNote(UnitSystem.Imperial, saving = true))
}
