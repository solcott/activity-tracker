package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem

/** What Privacy zones says that follows a rule, chosen here for the shared and the native UI (dev/shared-display-text). */
object PrivacyZonesTexts {
    /** A radius or hidden distance in the athlete's [units], as stats read; 0, nothing hidden, is "Off". */
    fun distance(meters: Int, units: UnitSystem): AppText =
        if (meters == 0) AppText.named("privacy_zones_off") else StatText.distance(meters.toDouble(), units)

    /** "Add a zone at my location", or "Finding your location…" while it does. */
    fun addHere(locating: Boolean): AppText = AppText.named(if (locating) "privacy_zones_locating" else "privacy_zones_add_here")
}
