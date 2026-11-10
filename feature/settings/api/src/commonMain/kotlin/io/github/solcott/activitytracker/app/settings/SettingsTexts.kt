package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatWhole
import kotlinx.collections.immutable.persistentListOf

/**
 * What Settings' list says that follows a rule, chosen here for the shared and the native UI (dev/shared-display-text). A row's summary is
 * null until its page's setting has been read: it is left out, never guessed (ui/settings-layout).
 */
object SettingsTexts {
    /** The Activity defaults row: who new activities are shown to, a `Visibility` value. */
    fun defaultVisibility(visibility: String?): AppText? = visibility?.let(LabelText::visibility)

    /** The Privacy zones row: how many zones the athlete keeps, and that route ends are hidden when they are. */
    fun privacyZones(count: Int?, hidesRouteEnds: Boolean): AppText? {
        if (count == null) return null
        val zones =
            if (count == 0) AppText.named("settings_privacy_zones_none")
            else AppText.Plural("settings_privacy_zones_count", count, persistentListOf(AppText.written(formatWhole(count))))
        return if (hidesRouteEnds) AppText.named("settings_privacy_zones_ends", zones) else zones
    }

    /** The Segments & groups row: whether segment matching is on. */
    fun segmentMatching(on: Boolean?): AppText? = on?.let { AppText.named(if (it) "settings_segments_on" else "settings_segments_off") }

    /** The line under the units choice: what the chosen system measures in, or that the choice is being saved. */
    fun unitsNote(units: UnitSystem, saving: Boolean): AppText =
        AppText.named(
            when {
                saving -> "settings_saving"
                units == UnitSystem.Metric -> "settings_units_metric_example"
                else -> "settings_units_imperial_example"
            },
        )
}
